package com.finbank.transfers.infrastructure;

import com.finbank.transfers.domain.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {
    List<Transfer> findBySourceAccountIdOrTargetAccountIdOrderByCreatedAtDesc(
        UUID sourceAccountId, UUID targetAccountId);

    List<Transfer> findByStatusAndCreatedAtBefore(String status, Instant before);
}
