package com.shopscale.seed;

import com.shopscale.catalog.Category;
import com.shopscale.catalog.CategoryRepository;
import com.shopscale.catalog.Product;
import com.shopscale.catalog.ProductRepository;
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

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void run(ApplicationArguments args) {
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

    private void seed() {
        Map<String, Category> categories = SeedCatalog.CATEGORIES.stream()
                .map(c -> categoryRepository.save(new Category(c.name(), c.slug(), c.description())))
                .collect(Collectors.toMap(Category::getSlug, Function.identity()));

        for (SeedCatalog.SeedProduct seed : SeedCatalog.PRODUCTS) {
            Product product = new Product();
            product.setSku(seed.sku());
            product.setName(seed.name());
            product.setBrand(seed.brand());
            product.setCategory(categories.get(seed.categorySlug()));
            product.setPrice(seed.priceValue());
            product.setCompareAtPrice(seed.compareAtValue());
            product.setDescription(seed.name() + " by " + seed.brand() + ".");
            productRepository.save(product);
        }
    }
}
