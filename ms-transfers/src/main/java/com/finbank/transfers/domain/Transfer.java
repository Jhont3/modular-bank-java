package com.finbank.transfers.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Misma forma JSON que exponía el monolito: {id, sourceAccountId, targetAccountId,
 * amount, reference, status, createdAt}. El status ahora refleja la saga:
 * PENDING → COMPLETED | FAILED | COMPENSATED.
 */
@Entity
@Table(name = "transfers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId;

    @Column(name = "target_account_id", nullable = false)
    private UUID targetAccountId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    private String reference;

    @Column(nullable = false)
    @Builder.Default
    private String status = TransferStatus.PENDING;

    @Column(name = "failure_reason")
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static final class TransferStatus {
        public static final String PENDING = "PENDING";
        public static final String COMPLETED = "COMPLETED";
        public static final String FAILED = "FAILED";
        public static final String COMPENSATED = "COMPENSATED";

        private TransferStatus() {}
    }
}
