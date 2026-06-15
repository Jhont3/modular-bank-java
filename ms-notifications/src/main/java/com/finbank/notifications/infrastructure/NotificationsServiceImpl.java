package com.finbank.notifications.infrastructure;

import com.finbank.notifications.application.NotificationsService;
import com.finbank.notifications.domain.Notification;
import com.finbank.notifications.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationsServiceImpl implements NotificationsService {

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional
    public void send(UUID userId, NotificationType type, Map<String, String> payload) {
        notificationRepository.save(Notification.builder()
            .userId(userId)
            .type(type)
            .payload(payload)
            .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> getForUser(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
