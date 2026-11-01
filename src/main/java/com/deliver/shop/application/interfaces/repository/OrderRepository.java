package com.deliver.shop.application.interfaces.repository;

import com.deliver.shop.domain.entities.OrderEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    @EntityGraph(attributePaths = {"address", "items", "items.dish"})
    List<OrderEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @EntityGraph(attributePaths = {"address", "items", "items.dish"})
    Optional<OrderEntity> findByIdAndUserId(UUID id, UUID userId);
}
