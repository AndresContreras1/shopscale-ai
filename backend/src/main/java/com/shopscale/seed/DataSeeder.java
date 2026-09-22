package com.shopscale.seed;

import com.shopscale.catalog.Category;
import com.shopscale.catalog.CategoryRepository;
import com.shopscale.catalog.Product;
import com.shopscale.catalog.ProductRepository;
import com.shopscale.inventory.InventoryService;
import com.shopscale.security.Role;
import com.shopscale.security.User;
import com.shopscale.security.UserRepository;
import java.util.Map;
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
 * Loads demo data on an empty database. When several API replicas start at the same time only one
 * wins the insert; the others hit the unique constraints and simply skip seeding.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    /** Initial units per product: a mix of healthy, low and out-of-stock items so alerts have data. */
    private static final int[] STOCK_PATTERN = {120, 45, 8, 60, 3, 200, 75, 12, 35, 0, 90, 25, 150, 6, 40};
    private static final int REORDER_POINT = 10;

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
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
            transactionTemplate.executeWithoutResult(status -> seed());
            log.info("Seeded {} categories and {} products", SeedCatalog.CATEGORIES.size(), SeedCatalog.PRODUCTS.size());
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
                userRepository.save(new User("admin@shopscale.dev", passwordEncoder.encode("Admin123!"),
                        "Ada Admin", Role.ADMIN));
                userRepository.save(new User("operator@shopscale.dev", passwordEncoder.encode("Operator123!"),
                        "Oscar Operator", Role.OPERATOR));
                userRepository.save(new User("customer@shopscale.dev", passwordEncoder.encode("Customer123!"),
                        "Carla Customer", Role.CUSTOMER));
            });
        } catch (DataIntegrityViolationException ex) {
            log.info("Another instance already seeded the users");
        }
    }

    private void seed() {
        Map<String, Category> categories = SeedCatalog.CATEGORIES.stream()
                .map(c -> categoryRepository.save(new Category(c.name(), c.slug(), c.description())))
                .collect(Collectors.toMap(Category::getSlug, Function.identity()));

        for (int i = 0; i < SeedCatalog.PRODUCTS.size(); i++) {
            SeedCatalog.SeedProduct seed = SeedCatalog.PRODUCTS.get(i);
            Product product = new Product();
            product.setSku(seed.sku());
            product.setName(seed.name());
            product.setBrand(seed.brand());
            product.setCategory(categories.get(seed.categorySlug()));
            product.setPrice(seed.priceValue());
            product.setCompareAtPrice(seed.compareAtValue());
            product.setDescription(seed.name() + " by " + seed.brand() + ".");
            productRepository.save(product);
            inventoryService.initialize(product, STOCK_PATTERN[i % STOCK_PATTERN.length], REORDER_POINT);
        }
    }
}
