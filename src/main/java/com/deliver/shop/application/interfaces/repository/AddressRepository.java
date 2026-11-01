package com.deliver.shop.application.interfaces.repository;

import com.deliver.shop.domain.entities.AddressEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<AddressEntity, UUID> {

    List<AddressEntity> findByUserId(UUID userId);

    Optional<AddressEntity> findByIdAndUserId(UUID id, UUID userId);
}
