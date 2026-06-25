import { api } from './client';
import type { SubstitutionDraft, SubstitutionResponse } from '../types/schedule';

export function listSubstitutions(planId: number, token: string) {
  return api<{ items: SubstitutionResponse[] }>(`/api/substitutions?planId=${planId}`, {}, token);
}

export function createSubstitution(draft: SubstitutionDraft, token: string) {
  return api<SubstitutionResponse>('/api/substitutions', {
    method: 'POST',
    body: JSON.stringify({
      assignmentId: Number(draft.assignmentId),
      substituteTeacherId: Number(draft.substituteTeacherId),
      startsAt: new Date(draft.startsAt).toISOString(),
      endsAt: draft.endsAt ? new Date(draft.endsAt).toISOString() : null,
      isPermanent: draft.isPermanent,
      reason: draft.reason || null,
    }),
  }, token);
}
