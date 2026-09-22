package com.shopscale.catalog;

import static com.shopscale.catalog.ProductSpecifications.hasStatus;
import static com.shopscale.catalog.ProductSpecifications.inCategory;
import static com.shopscale.catalog.ProductSpecifications.matchesText;
import static com.shopscale.catalog.ProductSpecifications.priceBetween;

import com.shopscale.catalog.dto.ProductFilter;
import com.shopscale.catalog.dto.ProductRequest;
import com.shopscale.catalog.dto.ProductResponse;
import com.shopscale.common.BusinessException;
import com.shopscale.common.NotFoundException;
import com.shopscale.common.PageResponse;
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

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(ProductFilter filter, Pageable pageable) {
        Specification<Product> spec = Specification.allOf(
                matchesText(filter.q()),
                inCategory(filter.categoryId()),
                priceBetween(filter.minPrice(), filter.maxPrice()),
                hasStatus(filter.status()));
        return PageResponse.from(productRepository.findAll(spec, pageable).map(ProductResponse::from));
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return ProductResponse.from(getEntity(id));
    }

    @Transactional(readOnly = true)
    public ProductResponse findBySku(String sku) {
        return productRepository.findBySku(sku).map(ProductResponse::from)
                .orElseThrow(() -> new NotFoundException("Product", sku));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new BusinessException("SKU already exists: " + request.sku());
        }
        Product product = new Product();
        apply(product, request);
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = getEntity(id);
        if (!product.getSku().equals(request.sku()) && productRepository.existsBySku(request.sku())) {
            throw new BusinessException("SKU already exists: " + request.sku());
        }
        apply(product, request);
        return ProductResponse.from(product);
    }

    /**
     * Soft delete: products referenced by past orders must stay in the database for history and reports.
     */
    @Transactional
    public void archive(Long id) {
        getEntity(id).setStatus(ProductStatus.ARCHIVED);
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
