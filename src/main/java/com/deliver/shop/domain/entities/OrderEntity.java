package com.deliver.shop.domain.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.deliver.shop.domain.enums.OrderStatus;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Getter
@Setter
@Entity
@Table(name = "orders")
public class OrderEntity extends BaseEntity {

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private OrderStatus status = OrderStatus.CREATED;

    @Column(name = "total_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalPrice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "address_id", nullable = false)
    private AddressEntity address;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<OrderItemEntity> items = new LinkedHashSet<>();


    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" +
                "id=" + getId() + ", " +
                "createdAt=" + createdAt + ", " +
                "status=" + status + ", " +
                "totalPrice=" + totalPrice + ")";
    }
}
