package com.moewadeul.notification.application.inbox;

import com.moewadeul.common.paging.CursorSlice;
import com.moewadeul.common.paging.PageSlice;
import com.moewadeul.notification.domain.Notification;
import com.moewadeul.notification.domain.NotificationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public CursorSlice<NotificationItem> list(long memberId, Long cursor, int size) {
        List<Notification> rows = notificationRepository.findRecent(memberId, cursor, PageSlice.fetchSize(size));
        return CursorSlice.of(rows, size, Notification::getId).map(NotificationQueryService::toItem);
    }

    @Transactional(readOnly = true)
    public long countUnread(long memberId) {
        return notificationRepository.countByMemberIdAndReadAtIsNull(memberId);
    }

    private static NotificationItem toItem(Notification notification) {
        return new NotificationItem(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getLink(),
                notification.getReadAt(),
                notification.getCreatedAt());
    }
}
