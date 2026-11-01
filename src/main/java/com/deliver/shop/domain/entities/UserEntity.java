package com.deliver.shop.domain.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.deliver.shop.domain.enums.Role;
import java.util.*;

@Getter
@Setter
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uq_users_email", columnNames = "email"),
        @UniqueConstraint(name = "uq_users_phone", columnNames = "phone")
})
public class UserEntity extends BaseEntity {

    @Column(name = "first_name", nullable = false, length = 20)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 20)
    private String lastName = "";

    @Column(name = "email", nullable = false, length = 100)
    private String email = "";

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role = Role.UNDEFINED;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "password_salt", nullable = false)
    private String passwordSalt;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private Set<OrderEntity> orders = new LinkedHashSet<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private Set<AddressEntity> addresses = new LinkedHashSet<>();

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" +
                "id=" + getId() + ", " +
                "firstName=" + firstName + ", " +
                "lastName=" + lastName + ", " +
                "email=" + email + ", " +
                "phone=" + phone + ", " +
                "role=" + role + ")";
    }
}
