import { api } from './client';
import type { PageResponse } from '../types/catalog';

export function listCatalog(resource: string, query: string, token: string) {
  return api<PageResponse>(`/api/catalog/${resource}?${query}`, {}, token);
}

export function createCatalogItem(resource: string, payload: Record<string, unknown>, token: string) {
  return api(`/api/catalog/${resource}`, {
    method: 'POST',
    body: JSON.stringify(payload),
  }, token);
}

export function updateCatalogItem(resource: string, id: string, payload: Record<string, unknown>, token: string) {
  return api(`/api/catalog/${resource}/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  }, token);
}
