import { apiFetch } from './client';
import type { CursorPage, NotificationItem, NotificationSetting } from './types';

const NOTIFICATION_PAGE_SIZE = 20;

export function fetchNotifications(cursor: number | null, signal?: AbortSignal): Promise<CursorPage<NotificationItem>> {
  return apiFetch<CursorPage<NotificationItem>>('/api/me/notifications', {
    query: { cursor, size: NOTIFICATION_PAGE_SIZE },
    signal,
  });
}

export function fetchUnreadCount(signal?: AbortSignal): Promise<{ count: number }> {
  return apiFetch<{ count: number }>('/api/me/notifications/unread-count', {
    signal,
    skipUnauthorizedHandler: true,
  });
}

export function markNotificationRead(notificationId: number): Promise<void> {
  return apiFetch<void>(`/api/me/notifications/${notificationId}/read`, { method: 'POST' });
}

export function markAllNotificationsRead(): Promise<void> {
  return apiFetch<void>('/api/me/notifications/read-all', { method: 'POST' });
}

export function fetchNotificationSettings(signal?: AbortSignal): Promise<NotificationSetting[]> {
  return apiFetch<NotificationSetting[]>('/api/me/notification-settings', { signal });
}

export function updateNotificationSettings(items: NotificationSetting[]): Promise<NotificationSetting[]> {
  return apiFetch<NotificationSetting[]>('/api/me/notification-settings', { method: 'PUT', body: items });
}
