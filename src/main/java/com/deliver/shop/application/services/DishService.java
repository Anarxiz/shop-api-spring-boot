package com.deliver.shop.application.services;

import com.deliver.shop.application.contracts.dish.CreateDishRequest;
import com.deliver.shop.application.contracts.dish.DishResponse;
import com.deliver.shop.application.contracts.dish.UpdateDishRequest;
import com.deliver.shop.application.exceptions.BusinessException;
import com.deliver.shop.application.exceptions.NotFoundException;
import com.deliver.shop.application.interfaces.repository.CategoryRepository;
import com.deliver.shop.application.interfaces.repository.DishRepository;
import com.deliver.shop.domain.entities.CategoryEntity;
import com.deliver.shop.domain.entities.DishEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DishService {

    private static final String DISH_NOT_FOUND = "Блюдо не найдено";

    private final DishRepository dishRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<DishResponse> getAll() {
        return dishRepository.findAllBy().stream().map(DishResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DishResponse> getByCategory(UUID categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new NotFoundException("Категория не найдена");
        }
        return dishRepository.findByCategoryId(categoryId).stream().map(DishResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DishResponse getById(UUID id) {
        return dishRepository.findWithCategoryById(id)
                .map(DishResponse::from)
                .orElseThrow(() -> new NotFoundException(DISH_NOT_FOUND));
    }

    @Transactional
    public UUID create(CreateDishRequest request) {
        CategoryEntity category = findCategoryOrFail(request.categoryId());

        DishEntity dish = new DishEntity();
        dish.setName(request.name());
        dish.setDescription(request.description());
        dish.setStructure(request.structure());
        dish.setPrice(request.price());
        dish.setImageUrl(request.imageUrl());
        dish.setAvailable(false); // новое блюдо недоступно, пока админ его не включит
        dish.setCategory(category);
        dishRepository.save(dish);

        log.info("Создано блюдо {}", dish.getId());
        return dish.getId();
    }

    @Transactional
    public void update(UUID id, UpdateDishRequest request) {
        CategoryEntity category = findCategoryOrFail(request.categoryId());

        DishEntity dish = dishRepository.findById(id).orElseThrow(() -> {
            log.warn("Блюдо {} не найдено для обновления", id);
            return new NotFoundException(DISH_NOT_FOUND);
        });

        dish.setName(request.name());
        dish.setDescription(request.description());
        dish.setStructure(request.structure());
        dish.setPrice(request.price());
        dish.setImageUrl(request.imageUrl());
        dish.setAvailable(request.isAvailable());
        dish.setCategory(category);

        log.info("Обновлено блюдо {}", id);
    }

    @Transactional
    public void delete(UUID id) {
        if (!dishRepository.existsById(id)) {
            log.warn("Блюдо {} не найдено для удаления", id);
            throw new NotFoundException(DISH_NOT_FOUND);
        }
        dishRepository.deleteById(id);
        log.info("Удалено блюдо {}", id);
    }

    private CategoryEntity findCategoryOrFail(UUID categoryId) {
        return categoryRepository.findById(categoryId).orElseThrow(() -> {
            log.info("Категория не обнаружена {}", categoryId);
            return new BusinessException("Категория с Id " + categoryId + " не найдена");
        });
    }
}
