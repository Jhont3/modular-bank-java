package com.modularbank.modules.accounts.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "accounts", name = "processed_movements")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedMovement {

    @Id
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "movement_id", nullable = false)
    private UUID movementId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
