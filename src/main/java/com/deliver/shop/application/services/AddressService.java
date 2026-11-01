package com.deliver.shop.application.services;

import com.deliver.shop.application.contracts.address.AddressResponse;
import com.deliver.shop.application.contracts.address.CreateAddressRequest;
import com.deliver.shop.application.contracts.address.UpdateAddressRequest;
import com.deliver.shop.application.exceptions.NotFoundException;
import com.deliver.shop.application.interfaces.repository.AddressRepository;
import com.deliver.shop.application.interfaces.repository.UserRepository;
import com.deliver.shop.domain.entities.AddressEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Все операции работают только с адресами текущего пользователя: чужой адрес = «не найден». */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressService {

    private static final String NOT_FOUND = "Не удалось найти адрес";

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<AddressResponse> getByUser(UUID userId) {
        return addressRepository.findByUserId(userId).stream().map(AddressResponse::from).toList();
    }

    @Transactional
    public UUID create(UUID userId, CreateAddressRequest request) {
        AddressEntity address = new AddressEntity();
        address.setUser(userRepository.getReferenceById(userId));
        address.setCity(request.city());
        address.setStreet(request.street());
        address.setHouse(request.house());
        address.setApartment(request.apartment());
        address.setFloor(request.floor());
        addressRepository.save(address);

        log.info("Создан адрес {} для пользователя {}", address.getId(), userId);
        return address.getId();
    }

    @Transactional
    public void update(UUID id, UUID userId, UpdateAddressRequest request) {
        AddressEntity address = addressRepository.findByIdAndUserId(id, userId).orElseThrow(() -> {
            log.warn("Не удалось найти адрес {} для обновления (пользователь {})", id, userId);
            return new NotFoundException(NOT_FOUND);
        });

        address.setCity(request.city());
        address.setStreet(request.street());
        address.setHouse(request.house());
        address.setApartment(request.apartment());
        address.setFloor(request.floor());
        log.info("Обновлён адрес {}", id);
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        AddressEntity address = addressRepository.findByIdAndUserId(id, userId).orElseThrow(() -> {
            log.warn("Не удалось удалить адрес {} (пользователь {})", id, userId);
            return new NotFoundException(NOT_FOUND);
        });

        addressRepository.delete(address);
        log.info("Удалён адрес {}", id);
    }
}
