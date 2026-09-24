package co.gamestore.seed;

import co.gamestore.catalog.Category;
import co.gamestore.catalog.CategoryRepository;
import co.gamestore.catalog.Product;
import co.gamestore.catalog.ProductRepository;
import co.gamestore.inventory.InventoryService;
import co.gamestore.inventory.InventoryService.HistoricalSale;
import co.gamestore.orders.Order;
import co.gamestore.orders.OrderRepository;
import co.gamestore.orders.OrderStatus;
import co.gamestore.security.Role;
import co.gamestore.security.User;
import co.gamestore.security.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Loads demo data on an empty database: catalog, users and 60 days of sales history so the dashboard
 * and the AI reports have realistic numbers. When several API replicas start at the same time only one
 * wins the insert; the others hit the unique constraints and skip seeding.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    /** Current units per product: a mix of healthy, low and out-of-stock items so alerts have data. */
    private static final int[] STOCK_PATTERN = {120, 45, 8, 60, 3, 200, 75, 12, 35, 0, 90, 25, 150, 6, 40};
    /** Average units sold per day: best sellers, regular products and items that barely move. */
    private static final double[] DEMAND_PATTERN = {4.5, 1.2, 2.8, 0.6, 3.5, 0.1, 1.0, 0.0, 1.8, 0.3, 2.2, 0.8, 0.0, 5.0, 1.5};
    private static final int REORDER_POINT = 10;
    private static final int HISTORY_DAYS = 60;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd");

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void run(ApplicationArguments args) {
        seedUsers();
        if (productRepository.count() > 0) {
            log.info("Database already has data, skipping seed");
            return;
        }
        try {
            long start = System.currentTimeMillis();
            transactionTemplate.executeWithoutResult(status -> seedCatalogAndHistory());
            log.info("Seeded catalog and {} days of sales history in {} ms", HISTORY_DAYS,
                    System.currentTimeMillis() - start);
        } catch (DataIntegrityViolationException ex) {
            log.info("Another instance already seeded the database");
        }
    }

    /** Demo accounts, one per role. Documented in the README; never use these outside a demo. */
    private void seedUsers() {
        if (userRepository.count() > 0) {
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> {
                userRepository.save(new User("admin@gamestore.co", passwordEncoder.encode("Admin123!"),
                        "Ada Admin", Role.ADMIN));
                userRepository.save(new User("operator@gamestore.co", passwordEncoder.encode("Operator123!"),
                        "Oscar Operator", Role.OPERATOR));
                userRepository.save(new User("customer@gamestore.co", passwordEncoder.encode("Customer123!"),
                        "Carla Customer", Role.CUSTOMER));
            });
        } catch (DataIntegrityViolationException ex) {
            log.info("Another instance already seeded the users");
        }
    }

    private void seedCatalogAndHistory() {
        Map<String, Category> categories = SeedCatalog.CATEGORIES.stream()
                .map(c -> categoryRepository.save(new Category(c.name(), c.slug(), c.description())))
                .collect(Collectors.toMap(Category::getSlug, Function.identity()));

        List<Product> products = new ArrayList<>();
        for (SeedCatalog.SeedProduct seed : SeedCatalog.PRODUCTS) {
            Product product = new Product();
            product.setSku(seed.sku());
            product.setName(seed.name());
            product.setBrand(seed.brand());
            product.setCategory(categories.get(seed.categorySlug()));
            product.setPrice(seed.priceValue());
            product.setCompareAtPrice(seed.compareAtValue());
            product.setDescription(seed.name() + " by " + seed.brand() + ".");
            products.add(productRepository.save(product));
        }

        Instant today = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant since = today.minus(Duration.ofDays(HISTORY_DAYS + 1));
        Random random = new Random(42);
        List<List<HistoricalSale>> salesByProduct = new ArrayList<>();
        products.forEach(p -> salesByProduct.add(new ArrayList<>()));
        List<Order> orders = new ArrayList<>();

        double totalDemand = 0;
        for (int i = 0; i < products.size(); i++) {
            totalDemand += demand(i);
        }
        int sequence = 0;
        for (int day = HISTORY_DAYS; day >= 1; day--) {
            Instant date = today.minus(Duration.ofDays(day));
            boolean weekend = LocalDate.ofInstant(date, ZoneOffset.UTC).getDayOfWeek().getValue() >= 6;
            // Growing store: more orders in recent weeks, extra traffic on weekends.
            int ordersToday = 8 + (HISTORY_DAYS - day) / 6 + random.nextInt(6) + (weekend ? 5 : 0);
            for (int n = 0; n < ordersToday; n++) {
                Instant createdAt = date.plus(Duration.ofMinutes(480 + random.nextInt(840)));
                String number = "SS-" + LocalDate.ofInstant(createdAt, ZoneOffset.UTC).format(DAY)
                        + "-H" + String.format("%05d", ++sequence);
                Order order = new Order(number, "customer" + (1 + random.nextInt(60)) + "@example.com", createdAt, null);
                int lines = 1 + random.nextInt(3);
                for (int l = 0; l < lines; l++) {
                    int index = pickWeighted(random, totalDemand, products.size());
                    String sku = products.get(index).getSku();
                    if (order.getItems().stream().anyMatch(it -> it.getSku().equals(sku))) {
                        continue;
                    }
                    int qty = random.nextInt(10) == 0 ? 2 : 1;
                    order.addItem(products.get(index), qty);
                    salesByProduct.get(index).add(new HistoricalSale(createdAt, qty, number));
                }
                order.moveTo(OrderStatus.PAID, createdAt.plus(Duration.ofMinutes(3)));
                if (day > 2) {
                    order.moveTo(OrderStatus.SHIPPED, createdAt.plus(Duration.ofDays(1)));
                }
                if (day > 5) {
                    order.moveTo(OrderStatus.DELIVERED, createdAt.plus(Duration.ofDays(4)));
                }
                orders.add(order);
            }
        }

        for (int i = 0; i < products.size(); i++) {
            inventoryService.importHistory(products.get(i), STOCK_PATTERN[i % STOCK_PATTERN.length], REORDER_POINT,
                    since, salesByProduct.get(i));
        }
        orderRepository.saveAll(orders);
        log.info("Seeded {} products and {} historical orders", products.size(), orders.size());
    }

    private static double demand(int productIndex) {
        return DEMAND_PATTERN[productIndex % DEMAND_PATTERN.length];
    }

    private static int pickWeighted(Random random, double totalDemand, int size) {
        double r = random.nextDouble() * totalDemand;
        for (int i = 0; i < size; i++) {
            r -= demand(i);
            if (r <= 0) {
                return i;
            }
        }
        return size - 1;
    }
}
