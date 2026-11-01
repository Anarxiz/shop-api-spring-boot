package com.deliver.shop.domain.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "dishes")
public class DishEntity extends BaseEntity {

    @Column(name = "name", nullable = false, length = 40)
    private String name;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Column(name = "structure", nullable = false, length = 200)
    private String structure;

    @Column(name = "price", nullable = false, precision = 18, scale = 2)
    private BigDecimal price;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "is_available", nullable = false)
    private boolean available;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private CategoryEntity category;

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" +
                "id=" + getId() + ", " +
                "name=" + name + ", " +
                "price=" + price + ", " +
                "available=" + available + ")";
    }
}
