package com.deliver.shop.domain.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@Entity
@Table(name = "addresses")
public class AddressEntity extends BaseEntity {

    @Column(name = "city", nullable = false, length = 50)
    private String city = "";

    @Column(name = "street", nullable = false, length = 100)
    private String street = "";

    @Column(name = "house", nullable = false, length = 50)
    private String house = "";

    @Column(name = "apartment", nullable = false, length = 5)
    private String apartment;

    @Column(name = "floor", length = 3)
    private String floor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserEntity user;

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" +
                "id=" + getId() + ", " +
                "city=" + city + ", " +
                "street=" + street + ", " +
                "house=" + house + ", " +
                "apartment=" + apartment + ", " +
                "floor=" + floor + ")";
    }
}
