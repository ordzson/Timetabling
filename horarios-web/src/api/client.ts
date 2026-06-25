import type { ApiError } from '../types/catalog';

const API_BASE = ((import.meta as ImportMeta & { env?: Record<string, string> }).env?.VITE_API_BASE) ?? '';

export async function api<T>(path: string, init: RequestInit = {}, token?: string): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init.headers,
    },
  });
  const body = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) {
    const error = (body ?? {}) as ApiError;
    throw new Error(formatApiError(error, response.status));
  }
  return body as T;
}

export async function apiForm<T>(path: string, form: FormData, token: string): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: form,
  });
  const body = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) {
    const error = (body ?? {}) as ApiError;
    throw new Error(formatApiError(error, response.status));
  }
  return body as T;
}

export function formatApiError(error: ApiError, status: number) {
  const fieldErrors = error.details?.fields?.map((field) => `${field.field}: ${field.message}`).join(' ');
  return [error.code, error.message, fieldErrors].filter(Boolean).join(' - ') || `HTTP ${status}`;
}
