package co.gamestore.catalog.importer;

import co.gamestore.audit.AuditService;
import co.gamestore.catalog.Category;
import co.gamestore.catalog.CategoryRepository;
import co.gamestore.catalog.Product;
import co.gamestore.catalog.ProductRepository;
import co.gamestore.common.CurrentActor;
import co.gamestore.common.NotFoundException;
import co.gamestore.inventory.InventoryService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bulk upsert of products from a CSV file (supplier feed, marketplace export).
 * The request only validates the file and returns a job id (HTTP 202); rows are processed in the
 * background in small batches, each in its own transaction, so a large file never blocks a request
 * thread or holds one huge database transaction.
 *
 * <p>CSV header: {@code sku,name,brand,category_slug,price,stock}
 */
@Service
public class ProductImportService {

    private static final Logger log = LoggerFactory.getLogger(ProductImportService.class);
    private static final int BATCH_SIZE = 200;
    private static final int MAX_ROWS = 20_000;
    private static final int MAX_ERRORS = 30;
    private static final Pattern SKU = Pattern.compile("^[A-Z0-9-]{3,40}$");

    private final ImportJobRepository jobRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryService inventoryService;
    private final AuditService auditService;
    private final TransactionTemplate tx;
    private final TaskExecutor executor;

    public ProductImportService(ImportJobRepository jobRepository, ProductRepository productRepository,
                                CategoryRepository categoryRepository, InventoryService inventoryService,
                                AuditService auditService, PlatformTransactionManager transactionManager,
                                @Qualifier("importExecutor") TaskExecutor executor) {
        this.jobRepository = jobRepository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.inventoryService = inventoryService;
        this.auditService = auditService;
        this.tx = new TransactionTemplate(transactionManager);
        this.executor = executor;
    }

    public ImportJob start(MultipartFile file) {
        List<String[]> rows = parse(file);
        String actor = CurrentActor.name();
        ImportJob job = jobRepository.save(new ImportJob(UUID.randomUUID().toString(), file.getOriginalFilename(),
                rows.size(), actor));
        auditService.record(actor, "PRODUCT_IMPORT_STARTED", "ImportJob", job.getId(), rows.size() + " rows");
        executor.execute(() -> process(job.getId(), rows));
        return job;
    }

    public ImportJob status(String id) {
        return jobRepository.findById(id).orElseThrow(() -> new NotFoundException("Import job", id));
    }

    void process(String jobId, List<String[]> rows) {
        ImportJob job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus(ImportJob.Status.RUNNING);
        jobRepository.save(job);
        List<String> errors = new ArrayList<>();
        try {
            Map<String, Category> categories = categoryRepository.findAll().stream()
                    .collect(Collectors.toMap(Category::getSlug, Function.identity()));
            for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
                List<String[]> batch = rows.subList(start, Math.min(start + BATCH_SIZE, rows.size()));
                int firstLine = start + 2;
                int[] result = tx.execute(status -> processBatch(batch, firstLine, categories, errors));
                job.setCreated(job.getCreated() + result[0]);
                job.setUpdated(job.getUpdated() + result[1]);
                job.setFailed(job.getFailed() + result[2]);
                job.setProcessedRows(Math.min(start + BATCH_SIZE, rows.size()));
                job.setErrors(String.join("\n", errors));
                job = jobRepository.save(job);
            }
            job.setStatus(ImportJob.Status.COMPLETED);
        } catch (RuntimeException ex) {
            log.error("Import {} failed", jobId, ex);
            errors.add("Import aborted: " + ex.getMessage());
            job.setErrors(String.join("\n", errors));
            job.setStatus(ImportJob.Status.FAILED);
        }
        job.setFinishedAt(Instant.now());
        jobRepository.save(job);
        log.info("Import {} finished: {} created, {} updated, {} failed", jobId, job.getCreated(), job.getUpdated(),
                job.getFailed());
    }

    /** @return {created, updated, failed} */
    private int[] processBatch(List<String[]> batch, int firstLine, Map<String, Category> categories,
                               List<String> errors) {
        int created = 0;
        int updated = 0;
        int failed = 0;
        for (int i = 0; i < batch.size(); i++) {
            String[] c = batch.get(i);
            try {
                String sku = c[0].trim().toUpperCase();
                if (!SKU.matcher(sku).matches()) {
                    throw new IllegalArgumentException("invalid SKU '" + c[0] + "'");
                }
                Category category = categories.get(c[3].trim());
                if (category == null) {
                    throw new IllegalArgumentException("unknown category '" + c[3] + "'");
                }
                String name = c[1].trim();
                if (name.isEmpty() || name.length() > 150) {
                    throw new IllegalArgumentException("name is required (max 150 characters)");
                }
                BigDecimal price = new BigDecimal(c[4].trim());
                if (price.signum() <= 0) {
                    throw new IllegalArgumentException("price must be positive");
                }
                int stock = Integer.parseInt(c[5].trim());

                var existing = productRepository.findBySku(sku);
                Product product = existing.orElseGet(Product::new);
                product.setSku(sku);
                product.setName(name);
                product.setBrand(c[2].trim().length() > 80 ? c[2].trim().substring(0, 80) : c[2].trim());
                product.setCategory(category);
                product.setPrice(price);
                productRepository.save(product);
                if (existing.isEmpty()) {
                    inventoryService.initialize(product, Math.max(stock, 0), 10);
                    created++;
                } else {
                    updated++;
                }
            } catch (RuntimeException ex) {
                failed++;
                if (errors.size() < MAX_ERRORS) {
                    errors.add("Line " + (firstLine + i) + ": " + ex.getMessage());
                }
            }
        }
        return new int[] {created, updated, failed};
    }

    private static List<String[]> parse(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("The file is empty");
        }
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null || !header.replace("﻿", "").trim().equalsIgnoreCase("sku,name,brand,category_slug,price,stock")) {
                throw new IllegalArgumentException("Expected header: sku,name,brand,category_slug,price,stock");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = line.split(",", -1);
                if (cols.length != 6) {
                    cols = new String[] {line, "", "", "", "0", "0"};
                }
                rows.add(cols);
                if (rows.size() > MAX_ROWS) {
                    throw new IllegalArgumentException("Too many rows, the limit is " + MAX_ROWS);
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the file", e);
        }
        return rows;
    }
}
