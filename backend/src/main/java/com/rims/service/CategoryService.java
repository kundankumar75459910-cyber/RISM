package com.rims.service;

import com.rims.dto.CategoryDto;
import com.rims.entity.Category;
import com.rims.exception.ApiException;
import com.rims.repository.CategoryRepository;
import com.rims.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categories;
    private final ProductRepository products;

    public CategoryService(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    public List<CategoryDto.Response> list() {
        return categories.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public CategoryDto.Response create(CategoryDto.Request r) {
        String name = r.name().trim();
        if (categories.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("A category named '" + name + "' already exists");
        }
        Category c = new Category();
        c.setName(name);
        c.setDescription(blankToNull(r.description()));
        return toResponse(categories.save(c));
    }

    @Transactional
    public CategoryDto.Response update(Long id, CategoryDto.Request r) {
        Category c = find(id);
        String name = r.name().trim();
        if (categories.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw ApiException.conflict("A category named '" + name + "' already exists");
        }
        c.setName(name);
        c.setDescription(blankToNull(r.description()));
        return toResponse(c);
    }

    @Transactional
    public void delete(Long id) {
        Category c = find(id);
        if (products.existsByCategoryId(id)) {
            throw ApiException.conflict("Category '" + c.getName() + "' still has products and cannot be deleted");
        }
        categories.delete(c);
    }

    private Category find(Long id) {
        return categories.findById(id).orElseThrow(() -> ApiException.notFound("Category not found"));
    }

    private CategoryDto.Response toResponse(Category c) {
        return new CategoryDto.Response(c.getId(), c.getName(), c.getDescription(), products.countByCategoryId(c.getId()));
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
