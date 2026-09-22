package com.shopscale.catalog;

import com.shopscale.catalog.dto.CategoryRequest;
import com.shopscale.catalog.dto.CategoryResponse;
import com.shopscale.common.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll(Sort.by("name")).stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsBySlug(request.slug())) {
            throw new BusinessException("Category slug already exists: " + request.slug());
        }
        Category saved = categoryRepository.save(
                new Category(request.name(), request.slug(), request.description()));
        return CategoryResponse.from(saved);
    }
}
