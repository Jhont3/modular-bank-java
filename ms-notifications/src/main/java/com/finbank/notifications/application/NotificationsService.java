package com.finbank.notifications.application;

import com.finbank.notifications.domain.Notification;
import com.finbank.notifications.domain.NotificationType;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface NotificationsService {
    void send(UUID userId, NotificationType type, Map<String, String> payload);
    List<Notification> getForUser(UUID userId);
}
