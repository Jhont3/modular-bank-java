package com.modularbank.modules.accounts.infrastructure;

import com.modularbank.modules.accounts.application.MovementsService;
import com.modularbank.modules.accounts.application.dto.MovementRequest;
import com.modularbank.modules.accounts.application.dto.MovementResult;
import com.modularbank.modules.accounts.domain.ProcessedMovement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MovementsServiceImpl implements MovementsService {

    private final AccountRepository accountRepository;
    private final ProcessedMovementRepository processedMovementRepository;

    @Override
    @Transactional
    public MovementResult executeMovement(String idempotencyKey, MovementRequest request) {
        Optional<ProcessedMovement> existing = processedMovementRepository.findById(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent replay detected, skipping movement. idempotencyKey={}", idempotencyKey);
            return new MovementResult(existing.get().getMovementId(), MovementResult.DUPLICATE);
        }

        if (request.sourceAccountId().equals(request.targetAccountId())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Source and target accounts must be different");
        }

        if (request.type() == MovementRequest.MovementType.TRANSFER) {
            boolean owns = accountRepository.findByUserId(request.userId()).stream()
                .anyMatch(a -> a.getId().equals(request.sourceAccountId()));
            if (!owns) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Source account does not belong to the user");
            }
        }

        if (!accountRepository.existsById(request.sourceAccountId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Source account not found");
        }
        if (!accountRepository.existsById(request.targetAccountId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Target account not found");
        }

        int debited = accountRepository.debitIfSufficient(request.sourceAccountId(), request.amount());
        if (debited == 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds");
        }
        accountRepository.credit(request.targetAccountId(), request.amount());

        UUID movementId = UUID.randomUUID();
        processedMovementRepository.save(ProcessedMovement.builder()
            .idempotencyKey(idempotencyKey)
            .movementId(movementId)
            .build());

        log.info("Movement applied. movementId={} type={} amount={} source={} target={}",
            movementId, request.type(), request.amount(), request.sourceAccountId(), request.targetAccountId());
        return new MovementResult(movementId, MovementResult.APPLIED);
    }

    @Override
    @Transactional(readOnly = true)
    public MovementResult findMovement(String idempotencyKey) {
        ProcessedMovement movement = processedMovementRepository.findById(idempotencyKey)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movement not found"));
        return new MovementResult(movement.getMovementId(), MovementResult.APPLIED);
    }
}
