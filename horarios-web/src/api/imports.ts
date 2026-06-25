import { api, apiForm } from './client';
import type { PageResponse } from '../types/catalog';
import type { ImportErrorRow, ImportResponse } from '../types/import';

export function importAcademicData(form: FormData, token: string) {
  return apiForm<ImportResponse>('/api/imports/academic-data', form, token);
}

export function listImportErrors(importBatchId: number, token: string) {
  return api<PageResponse<ImportErrorRow>>(`/api/imports/${importBatchId}/errors?page=0&size=20`, {}, token);
}
