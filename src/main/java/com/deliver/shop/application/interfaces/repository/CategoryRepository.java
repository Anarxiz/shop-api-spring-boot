package com.deliver.shop.application.interfaces.repository;

import com.deliver.shop.domain.entities.CategoryEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<CategoryEntity, UUID> {

    @EntityGraph(attributePaths = "dishes")
    List<CategoryEntity> findAllBy();

    @EntityGraph(attributePaths = "dishes")
    Optional<CategoryEntity> findWithDishesById(UUID id);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);
}
