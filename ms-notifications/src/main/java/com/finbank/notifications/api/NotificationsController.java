package com.finbank.notifications.api;

import com.finbank.notifications.application.NotificationsService;
import com.finbank.notifications.domain.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/**
 * Mismo contrato HTTP que exponía el módulo en el monolito (requisito Paso 1):
 * GET /notifications con JWT Bearer → lista de notificaciones del usuario.
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationsController {

    private final NotificationsService notificationsService;

    @GetMapping
    public List<Notification> getNotifications(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return notificationsService.getForUser(userId);
    }
}
