import { api } from './client';
import type { PageResponse } from '../types/catalog';
import type {
  GenerationResponse,
  ManualEditResponse,
  ManualDraft,
  PlanStatus,
  SchedulePlanSummary,
  ScheduleResult,
  ValidationResponse,
  Violation,
} from '../types/schedule';

export function listSchedulePlans(query: string, token: string) {
  return api<PageResponse<SchedulePlanSummary>>(`/api/schedule-plans?${query}`, {}, token);
}

export function validateSchedulePlan(planId: number, token: string) {
  return api<ValidationResponse>(`/api/schedule-plans/${planId}/validate`, { method: 'POST', body: '{}' }, token);
}

export function generateSchedulePlan(planId: number, token: string) {
  return api<GenerationResponse>(`/api/schedule-plans/${planId}/generate`, {
    method: 'POST',
    body: JSON.stringify({ solverMode: 'NORMAL', timeLimitSeconds: 30, weights: {} }),
  }, token);
}

export function getScheduleResult(planId: number, runId: number | null, token: string) {
  const query = runId ? `?runId=${runId}` : '';
  return api<ScheduleResult>(`/api/schedule-plans/${planId}/result${query}`, {}, token);
}

export function listScheduleViolations(planId: number, runId: number | null, token: string) {
  const query = runId ? `?runId=${runId}` : '';
  return api<{ items: Violation[] }>(`/api/schedule-plans/${planId}/violations${query}`, {}, token);
}

export function approveSchedulePlan(planId: number, runId: number | null, token: string) {
  return api<{ status: PlanStatus }>(`/api/schedule-plans/${planId}/approve`, {
    method: 'POST',
    body: JSON.stringify({ runId, comment: 'Aprobado desde frontend.' }),
  }, token);
}

export function lockSchedulePlan(planId: number, token: string) {
  return api<{ status: PlanStatus }>(`/api/schedule-plans/${planId}/lock`, {
    method: 'POST',
    body: JSON.stringify({ comment: 'Bloqueado desde frontend.' }),
  }, token);
}

export function submitManualScheduleEdit(planId: number, runId: number, draft: ManualDraft, token: string) {
  return api<ManualEditResponse>(`/api/schedule-plans/${planId}/manual-edits`, {
    method: 'POST',
    body: JSON.stringify({
      clientRequestId: `edit-${Date.now()}-${draft.assignment.sessionId}`,
      baseRunId: runId,
      sessionId: draft.assignment.sessionId,
      targetTeacherId: draft.targetTeacherId ? Number(draft.targetTeacherId) : null,
      targetRoomId: draft.targetRoomId ? Number(draft.targetRoomId) : null,
      targetTimeBlockId: Number(draft.targetTimeBlockId),
    }),
  }, token);
}
