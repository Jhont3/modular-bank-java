package com.finbank.transfers.infrastructure;

import com.finbank.transfers.application.ports.AccountsPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Adaptador HTTP hacia el módulo accounts del monolito remanente.
 * Resiliencia (Paso 3): Retry + Backoff exponencial para fallos transitorios y
 * Circuit Breaker que corta el flujo cuando accounts falla repetidamente
 * (config en application.yml, instancia "accounts"). Los errores de negocio
 * (4xx) NO se reintentan ni abren el circuito.
 */
@Component
@Slf4j
public class AccountsHttpAdapter implements AccountsPort {

    private final RestClient restClient;

    public AccountsHttpAdapter(@Value("${app.accounts.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(4));
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(factory)
            .build();
    }

    @Override
    @CircuitBreaker(name = "accounts")
    @Retry(name = "accounts")
    public MovementResult executeMovement(String idempotencyKey, MovementCommand command) {
        try {
            return restClient.post()
                .uri("/internal/accounts/movements")
                .header("Idempotency-Key", idempotencyKey)
                .body(command)
                .retrieve()
                .body(MovementResult.class);
        } catch (HttpClientErrorException e) {
            throw toBusinessException(e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new AccountsUnavailableException("accounts call failed: " + e.getMessage(), e);
        }
    }

    @Override
    @CircuitBreaker(name = "accounts")
    @Retry(name = "accounts")
    public MovementResult findMovement(String idempotencyKey) {
        try {
            return restClient.get()
                .uri("/internal/accounts/movements/{key}", idempotencyKey)
                .retrieve()
                .body(MovementResult.class);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (HttpClientErrorException e) {
            throw toBusinessException(e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new AccountsUnavailableException("accounts call failed: " + e.getMessage(), e);
        }
    }

    @Override
    @CircuitBreaker(name = "accounts")
    @Retry(name = "accounts")
    public List<AccountSummary> getAccountsByUser(UUID userId) {
        try {
            return restClient.get()
                .uri("/internal/accounts/by-user/{userId}", userId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<AccountSummary>>() {});
        } catch (HttpClientErrorException e) {
            throw toBusinessException(e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new AccountsUnavailableException("accounts call failed: " + e.getMessage(), e);
        }
    }

    private AccountsBusinessException toBusinessException(HttpClientErrorException e) {
        String message = e.getResponseBodyAsString();
        return new AccountsBusinessException(e.getStatusCode().value(),
            message.isBlank() ? e.getStatusText() : extractMessage(message));
    }

    private String extractMessage(String body) {
        // El monolito responde {"message": "..."} en errores de ResponseStatusException
        int idx = body.indexOf("\"message\":\"");
        if (idx >= 0) {
            int start = idx + 11;
            int end = body.indexOf('"', start);
            if (end > start) {
                return body.substring(start, end);
            }
        }
        return body;
    }
}
