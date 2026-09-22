package com.shopscale.catalog;

import static com.shopscale.catalog.ProductSpecifications.hasStatus;
import static com.shopscale.catalog.ProductSpecifications.inCategory;
import static com.shopscale.catalog.ProductSpecifications.matchesText;
import static com.shopscale.catalog.ProductSpecifications.priceBetween;

import com.shopscale.audit.AuditService;
import com.shopscale.catalog.dto.ProductFilter;
import com.shopscale.catalog.dto.ProductRequest;
import com.shopscale.catalog.dto.ProductResponse;
import com.shopscale.common.BusinessException;
import com.shopscale.common.NotFoundException;
import com.shopscale.common.PageResponse;
import com.shopscale.inventory.InventoryService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryService inventoryService;
    private final AuditService auditService;

    private static final int DEFAULT_REORDER_POINT = 10;

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(ProductFilter filter, Pageable pageable) {
        Specification<Product> spec = Specification.allOf(
                matchesText(filter.q()),
                inCategory(filter.categoryId()),
                priceBetween(filter.minPrice(), filter.maxPrice()),
                hasStatus(filter.status()));
        var page = productRepository.findAll(spec, pageable);
        Map<Long, Integer> stock = inventoryService.availableFor(page.map(Product::getId).getContent());
        return PageResponse.from(page.map(p -> ProductResponse.from(p, stock.get(p.getId()))));
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return toResponse(getEntity(id));
    }

    @Transactional(readOnly = true)
    public ProductResponse findBySku(String sku) {
        return productRepository.findBySku(sku).map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("Product", sku));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new BusinessException("SKU already exists: " + request.sku());
        }
        Product product = new Product();
        apply(product, request);
        productRepository.save(product);
        inventoryService.initialize(product,
                request.initialStock() == null ? 0 : request.initialStock(),
                request.reorderPoint() == null ? DEFAULT_REORDER_POINT : request.reorderPoint());
        auditService.record("PRODUCT_CREATED", "Product", product.getId(), product.getSku() + " at " + product.getPrice());
        return toResponse(product);
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = getEntity(id);
        if (!product.getSku().equals(request.sku()) && productRepository.existsBySku(request.sku())) {
            throw new BusinessException("SKU already exists: " + request.sku());
        }
        var oldPrice = product.getPrice();
        apply(product, request);
        String details = oldPrice.compareTo(product.getPrice()) != 0
                ? "Price changed from " + oldPrice + " to " + product.getPrice() : null;
        auditService.record(details != null ? "PRICE_CHANGED" : "PRODUCT_UPDATED", "Product", id, details);
        return toResponse(product);
    }

    /**
     * Soft delete: products referenced by past orders must stay in the database for history and reports.
     */
    @Transactional
    public void archive(Long id) {
        getEntity(id).setStatus(ProductStatus.ARCHIVED);
        auditService.record("PRODUCT_ARCHIVED", "Product", id, null);
    }

    private ProductResponse toResponse(Product product) {
        return ProductResponse.from(product, inventoryService.availableFor(List.of(product.getId()))
                .get(product.getId()));
    }

    Product getEntity(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new NotFoundException("Product", id));
    }

    private void apply(Product product, ProductRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new NotFoundException("Category", request.categoryId()));
        if (request.compareAtPrice() != null && request.compareAtPrice().compareTo(request.price()) <= 0) {
            throw new IllegalArgumentException("compareAtPrice must be greater than price");
        }
        product.setSku(request.sku());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setBrand(request.brand());
        product.setCategory(category);
        product.setPrice(request.price());
        product.setCompareAtPrice(request.compareAtPrice());
        product.setImageUrl(request.imageUrl());
        if (request.status() != null) {
            product.setStatus(request.status());
        }
    }
}
