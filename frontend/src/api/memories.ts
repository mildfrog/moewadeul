import { apiFetch } from './client';
import type {
  CursorPage,
  ImageUpload,
  ImageUploadRequest,
  MemoryCreateRequest,
  MemberMemorySummary,
  MemoryDetail,
  MemoryFeedItem,
  MemoryLikeResponse,
  MemoryReportRequest,
  MemorySummary,
  MemoryUpdateRequest,
} from './types';

export function createMemory(req: MemoryCreateRequest, idempotencyKey: string): Promise<{ memoryId: number }> {
  return apiFetch<{ memoryId: number }>('/api/memories', { method: 'POST', body: req, idempotencyKey });
}

export function getMemory(memoryId: number, signal?: AbortSignal): Promise<MemoryDetail> {
  return apiFetch<MemoryDetail>(`/api/memories/${memoryId}`, { signal });
}

export function updateMemory(memoryId: number, req: MemoryUpdateRequest): Promise<MemoryDetail> {
  return apiFetch<MemoryDetail>(`/api/memories/${memoryId}`, { method: 'PATCH', body: req });
}

export function deleteMemory(memoryId: number): Promise<void> {
  return apiFetch<void>(`/api/memories/${memoryId}`, { method: 'DELETE' });
}

export function requestMemoryImageUpload(req: ImageUploadRequest): Promise<ImageUpload> {
  return apiFetch<ImageUpload>('/api/memories/images', { method: 'POST', body: req });
}

export function fetchMyMemories(signal?: AbortSignal): Promise<MemorySummary[]> {
  return apiFetch<MemorySummary[]>('/api/me/memories', { signal });
}

export function fetchMemoryFeed(
  cursor: number | null,
  size: number,
  signal?: AbortSignal,
): Promise<CursorPage<MemoryFeedItem>> {
  return apiFetch<CursorPage<MemoryFeedItem>>('/api/memories/feed', {
    query: { cursor, size },
    signal,
  });
}

export function fetchMemberMemories(memberId: number, signal?: AbortSignal): Promise<MemberMemorySummary[]> {
  return apiFetch<MemberMemorySummary[]>(`/api/members/${memberId}/memories`, { signal });
}

export function likeMemory(memoryId: number): Promise<MemoryLikeResponse> {
  return apiFetch<MemoryLikeResponse>(`/api/memories/${memoryId}/like`, { method: 'PUT' });
}

export function unlikeMemory(memoryId: number): Promise<MemoryLikeResponse> {
  return apiFetch<MemoryLikeResponse>(`/api/memories/${memoryId}/like`, { method: 'DELETE' });
}

export function reportMemory(memoryId: number, req: MemoryReportRequest): Promise<void> {
  return apiFetch<void>(`/api/memories/${memoryId}/reports`, { method: 'POST', body: req });
}
