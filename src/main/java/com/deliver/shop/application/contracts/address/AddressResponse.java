package com.deliver.shop.application.contracts.address;

import com.deliver.shop.domain.entities.AddressEntity;

import java.util.UUID;

public record AddressResponse(UUID id, String city, String street, String house, String apartment, String floor) {

    public static AddressResponse from(AddressEntity a) {
        return new AddressResponse(a.getId(), a.getCity(), a.getStreet(), a.getHouse(), a.getApartment(), a.getFloor());
    }
}
