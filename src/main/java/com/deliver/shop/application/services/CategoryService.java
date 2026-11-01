package com.deliver.shop.application.services;

import com.deliver.shop.application.contracts.category.CategoryResponse;
import com.deliver.shop.application.contracts.category.CreateCategoryRequest;
import com.deliver.shop.application.contracts.category.UpdateCategoryRequest;
import com.deliver.shop.application.exceptions.BusinessException;
import com.deliver.shop.application.exceptions.NotFoundException;
import com.deliver.shop.application.interfaces.repository.CategoryRepository;
import com.deliver.shop.application.interfaces.repository.DishRepository;
import com.deliver.shop.domain.entities.CategoryEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final String NOT_FOUND = "Категория не найдена";

    private final CategoryRepository categoryRepository;
    private final DishRepository dishRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAll() {
        return categoryRepository.findAllBy().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getById(UUID id) {
        return categoryRepository.findWithDishesById(id)
                .map(CategoryResponse::from)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }

    @Transactional
    public UUID create(CreateCategoryRequest request) {
        if (categoryRepository.existsByName(request.name())) {
            throw new BusinessException("Категория с таким названием уже существует");
        }

        CategoryEntity category = new CategoryEntity();
        category.setName(request.name());
        categoryRepository.save(category);

        log.info("Создана категория {}", category.getId());
        return category.getId();
    }

    @Transactional
    public void update(UUID id, UpdateCategoryRequest request) {
        CategoryEntity category = categoryRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Категория {} не найдена для обновления", id);
                    return new NotFoundException(NOT_FOUND);
                });

        if (categoryRepository.existsByNameAndIdNot(request.name(), id)) {
            throw new BusinessException("Категория с таким названием уже существует");
        }

        category.setName(request.name());
        log.info("Обновлена категория {}", id);
    }

    @Transactional
    public void delete(UUID id) {
        if (dishRepository.existsByCategoryId(id)) {
            throw new BusinessException("В категории есть блюда, сначала удалите или перенесите их");
        }

        if (!categoryRepository.existsById(id)) {
            log.warn("Категория {} не найдена для удаления", id);
            throw new NotFoundException(NOT_FOUND);
        }

        categoryRepository.deleteById(id);
        log.info("Удалена категория {}", id);
    }
}
