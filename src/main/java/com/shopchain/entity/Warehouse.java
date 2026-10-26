package com.shopchain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "warehouses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Warehouse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String address;
    private String phone;
    private Double latitude;
    private Double longitude;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
