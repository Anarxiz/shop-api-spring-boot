package com.deliver.shop.api.controllers;

import com.deliver.shop.api.security.CurrentUser;
import com.deliver.shop.application.contracts.address.AddressResponse;
import com.deliver.shop.application.contracts.address.CreateAddressRequest;
import com.deliver.shop.application.contracts.address.UpdateAddressRequest;
import com.deliver.shop.application.services.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    public List<AddressResponse> getAll(@AuthenticationPrincipal Jwt jwt) {
        return addressService.getByUser(CurrentUser.id(jwt));
    }

    @PostMapping
    public ResponseEntity<UUID> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateAddressRequest request) {
        UUID id = addressService.create(CurrentUser.id(jwt), request);
        return ResponseEntity.created(URI.create("/api/addresses/" + id)).body(id);
    }

    @PutMapping("/{id}")
    public void update(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                       @Valid @RequestBody UpdateAddressRequest request) {
        addressService.update(id, CurrentUser.id(jwt), request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        addressService.delete(id, CurrentUser.id(jwt));
    }
}
