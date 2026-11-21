package com.deliver.shop.application.interfaces.repository;

import com.deliver.shop.domain.entities.OrderEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    @Query("""
            select o.id from OrderEntity o where o.user.id = :userId
            order by o.createdAt desc, o.id desc
            """)
    List<UUID> findHistoryIds(UUID userId, Pageable page);

    @EntityGraph(attributePaths = {"address", "items", "items.dish"})
    List<OrderEntity> findByUserIdAndIdInOrderByCreatedAtDescIdDesc(UUID userId, Collection<UUID> ids);

    @EntityGraph(attributePaths = {"address", "items", "items.dish"})
    Optional<OrderEntity> findByIdAndUserId(UUID id, UUID userId);
}
