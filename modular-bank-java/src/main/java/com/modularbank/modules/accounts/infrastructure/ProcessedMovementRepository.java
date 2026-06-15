package com.modularbank.modules.accounts.infrastructure;

import com.modularbank.modules.accounts.domain.ProcessedMovement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedMovementRepository extends JpaRepository<ProcessedMovement, String> {
}
