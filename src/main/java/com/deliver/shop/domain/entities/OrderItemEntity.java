package com.deliver.shop.domain.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "order_items")
public class OrderItemEntity extends BaseEntity {

    @Column(name = "quantity", nullable = false)
    private int quantity;

    /** Цена блюда на момент покупки. */
    @Column(name = "price_at_order", nullable = false, precision = 18, scale = 2)
    private BigDecimal priceAtOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private OrderEntity order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dish_id", nullable = false)
    private DishEntity dish;

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" +
                "id=" + getId() + ", " +
                "quantity=" + quantity + ", " +
                "priceAtOrder=" + priceAtOrder + ")";
    }
}
