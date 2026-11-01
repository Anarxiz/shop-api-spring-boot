package com.deliver.shop.application.interfaces.repository;

import com.deliver.shop.domain.entities.DishEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DishRepository extends JpaRepository<DishEntity, UUID> {

    boolean existsByCategoryId(UUID categoryId);

    @EntityGraph(attributePaths = "category")
    List<DishEntity> findAllBy();

    @EntityGraph(attributePaths = "category")
    List<DishEntity> findByCategoryId(UUID categoryId);

    @EntityGraph(attributePaths = "category")
    java.util.Optional<DishEntity> findWithCategoryById(UUID id);
}
