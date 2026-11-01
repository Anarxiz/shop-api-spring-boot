package com.deliver.shop.domain.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.*;

@Getter
@Setter
@Entity
@Table(name = "categories", uniqueConstraints = @UniqueConstraint(name = "uq_categories_name", columnNames = "name"))
public class CategoryEntity extends BaseEntity {

    @Column(name = "name", nullable = false, length = 40)
    private String name;

    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
    private Set<DishEntity> dishes = new LinkedHashSet<>();

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" +
                "id=" + getId() + ", " +
                "name=" + name + ")";
    }
}
