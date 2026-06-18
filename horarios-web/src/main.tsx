import React, { FormEvent, useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

type Role = 'SUPERADMIN' | 'ADMIN' | 'TEACHER' | 'STUDENT';

type User = {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  active: boolean;
};

type Session = {
  accessToken: string;
  user: User;
};

type PageResponse<T = Record<string, unknown>> = {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
};

type ApiError = {
  code?: string;
  message?: string;
  details?: { fields?: { field: string; message: string }[] };
};

type Field =
  | { name: string; label: string; type: 'text' | 'number' | 'time'; required?: boolean }
  | { name: string; label: string; type: 'checkbox' }
  | { name: string; label: string; type: 'select'; options: string[] };

type Catalog = {
  resource: string;
  title: string;
  fields: Field[];
  columns: string[];
};

type AdminView = 'catalogs' | 'import' | 'plans';

type ImportSummary = {
  rowsRead: number;
  rowsValid: number;
  rowsInvalid: number;
};

type ImportResponse = {
  importBatchId: number;
  status: string;
  filename: string;
  summary: ImportSummary;
  errorCount: number;
};

type ImportErrorRow = {
  id: number;
  sheetName: string;
  rowNumber: number;
  columnName: string;
  rawValue: string;
  code: string;
  message?: string;
  suggestedAction?: string;
};

type ValidationIssue = {
  id: number;
  severity: string;
  code: string;
  entityType: string;
  entityId: number;
  message: string;
  suggestedAction?: string;
};

type ValidationResponse = {
  planId: number;
  status: PlanStatus;
  hasBlockingErrors: boolean;
  issues: ValidationIssue[];
};

type PlanStatus =
  | 'DRAFT'
  | 'VALIDATING'
  | 'INVALID_INPUT'
  | 'GENERATING'
  | 'GENERATED'
  | 'GENERATED_WITH_CONFLICTS'
  | 'APPROVED'
  | 'LOCKED'
  | 'ARCHIVED';

type GenerationResponse = {
  planId: number;
  runId: number;
  status: string;
  planStatus: PlanStatus;
  seed: number;
  engineVersion: string;
  assignedCount: number;
  unassignedCount: number;
  score?: Record<string, number>;
};

type Violation = {
  id: number;
  severity: string;
  code: string;
  message: string;
  cost?: number;
};

const API_BASE = ((import.meta as ImportMeta & { env?: Record<string, string> }).env?.VITE_API_BASE) ?? '';
const SESSION_KEY = 'horarios.session';

const CATALOGS: Catalog[] = [
  {
    resource: 'careers',
    title: 'Carreras',
    fields: [
      { name: 'code', label: 'Codigo', type: 'text', required: true },
      { name: 'name', label: 'Nombre', type: 'text', required: true },
      { name: 'active', label: 'Activa', type: 'checkbox' },
    ],
    columns: ['code', 'name', 'active'],
  },
  {
    resource: 'courses',
    title: 'Cursos',
    fields: [
      { name: 'code', label: 'Codigo', type: 'text', required: true },
      { name: 'name', label: 'Nombre', type: 'text', required: true },
      { name: 'requiresLab', label: 'Requiere lab', type: 'checkbox' },
      { name: 'weeklyBlocksMin', label: 'Bloques min', type: 'number', required: true },
      { name: 'weeklyBlocksMax', label: 'Bloques max', type: 'number', required: true },
    ],
    columns: ['code', 'name', 'requiresLab', 'weeklyBlocksMin', 'weeklyBlocksMax'],
  },
  {
    resource: 'teachers',
    title: 'Docentes',
    fields: [
      { name: 'code', label: 'Codigo', type: 'text', required: true },
      { name: 'fullName', label: 'Nombre completo', type: 'text', required: true },
      { name: 'priority', label: 'Prioridad', type: 'number', required: true },
      { name: 'minCourses', label: 'Cursos min', type: 'number', required: true },
      { name: 'maxCourses', label: 'Cursos max', type: 'number', required: true },
      { name: 'active', label: 'Activo', type: 'checkbox' },
    ],
    columns: ['code', 'fullName', 'priority', 'minCourses', 'maxCourses', 'active'],
  },
  {
    resource: 'rooms',
    title: 'Aulas',
    fields: [
      { name: 'code', label: 'Codigo', type: 'text', required: true },
      { name: 'capacity', label: 'Capacidad', type: 'number', required: true },
      { name: 'type', label: 'Tipo', type: 'select', options: ['THEORY', 'LAB', 'MIXED'] },
      { name: 'floor', label: 'Nivel', type: 'number', required: true },
      { name: 'number', label: 'Numero', type: 'number', required: true },
      { name: 'active', label: 'Activa', type: 'checkbox' },
    ],
    columns: ['code', 'capacity', 'type', 'floor', 'number', 'active'],
  },
  {
    resource: 'journeys',
    title: 'Jornadas',
    fields: [
      { name: 'code', label: 'Codigo', type: 'text', required: true },
      { name: 'name', label: 'Nombre', type: 'text', required: true },
      { name: 'blockMinutes', label: 'Minutos bloque', type: 'number', required: true },
      { name: 'startTime', label: 'Inicio', type: 'time', required: true },
      { name: 'endTime', label: 'Fin', type: 'time', required: true },
    ],
    columns: ['code', 'name', 'blockMinutes', 'startTime', 'endTime'],
  },
];

function defaultValues(catalog: Catalog): Record<string, unknown> {
  return Object.fromEntries(
    catalog.fields.map((field) => [
      field.name,
      field.type === 'checkbox' ? true : field.type === 'select' ? field.options[0] : '',
    ]),
  );
}

async function api<T>(path: string, init: RequestInit = {}, token?: string): Promise<T> {
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

async function apiForm<T>(path: string, form: FormData, token: string): Promise<T> {
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

function formatApiError(error: ApiError, status: number) {
  const fieldErrors = error.details?.fields?.map((field) => `${field.field}: ${field.message}`).join(' ');
  return [error.code, error.message, fieldErrors].filter(Boolean).join(' - ') || `HTTP ${status}`;
}

function normalizePayload(catalog: Catalog, values: Record<string, unknown>) {
  const payload: Record<string, unknown> = {};
  for (const field of catalog.fields) {
    const value = values[field.name];
    if (field.type === 'number') {
      payload[field.name] = Number(value);
    } else if (field.type === 'time' && typeof value === 'string') {
      payload[field.name] = value.length === 5 ? `${value}:00` : value;
    } else {
      payload[field.name] = value;
    }
  }
  if (catalog.resource === 'courses') {
    payload.preferences = {};
  }
  return payload;
}

function readSession() {
  try {
    const raw = localStorage.getItem(SESSION_KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

function App() {
  const [session, setSession] = useState<Session | null>(() => readSession());

  function saveSession(next: Session | null) {
    setSession(next);
    if (next) {
      localStorage.setItem(SESSION_KEY, JSON.stringify(next));
    } else {
      localStorage.removeItem(SESSION_KEY);
    }
  }

  if (!session) {
    return <Login onLogin={saveSession} />;
  }
  return <AdminShell session={session} onLogout={() => saveSession(null)} />;
}

function Login({ onLogin }: { onLogin: (session: Session) => void }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError('');
    setLoading(true);
    try {
      const session = await api<Session>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      });
      onLogin(session);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Error de autenticacion.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="login-page">
      <form className="login-panel" onSubmit={submit}>
        <p className="eyebrow">Sistema de Generacion de Horarios</p>
        <h1>Administracion</h1>
        <label>
          Correo
          <input value={email} onChange={(event) => setEmail(event.target.value)} type="email" required />
        </label>
        <label>
          Password
          <input value={password} onChange={(event) => setPassword(event.target.value)} type="password" required />
        </label>
        {error ? <ErrorBox message={error} /> : null}
        <button type="submit" disabled={loading}>
          {loading ? 'Ingresando...' : 'Ingresar'}
        </button>
      </form>
    </main>
  );
}

function AdminShell({ session, onLogout }: { session: Session; onLogout: () => void }) {
  const canManageCatalogs = session.user.role === 'ADMIN' || session.user.role === 'SUPERADMIN';
  const [view, setView] = useState<AdminView>('catalogs');
  const [activeResource, setActiveResource] = useState(CATALOGS[0].resource);
  const catalog = useMemo(
    () => CATALOGS.find((item) => item.resource === activeResource) ?? CATALOGS[0],
    [activeResource],
  );

  return (
    <main className="admin-shell">
      <aside className="sidebar">
        <div>
          <p className="eyebrow">Horarios UdeO/UTP</p>
          <h1>Administracion</h1>
        </div>
        <nav aria-label="Modulos">
          {[
            ['catalogs', 'Catalogos'],
            ['import', 'Importar'],
            ['plans', 'Planes'],
          ].map(([key, label]) => (
            <button
              className={view === key ? 'active' : ''}
              key={key}
              onClick={() => setView(key as AdminView)}
              type="button"
              disabled={!canManageCatalogs}
            >
              {label}
            </button>
          ))}
        </nav>
        {view === 'catalogs' ? <nav aria-label="Catalogos">
          {CATALOGS.map((item) => (
            <button
              className={item.resource === activeResource ? 'active' : ''}
              key={item.resource}
              onClick={() => setActiveResource(item.resource)}
              type="button"
              disabled={!canManageCatalogs}
            >
              {item.title}
            </button>
          ))}
        </nav> : null}
      </aside>
      <section className="content">
        <header className="topbar">
          <div>
            <strong>{session.user.fullName}</strong>
            <span>{session.user.role}</span>
          </div>
          <button className="ghost" onClick={onLogout} type="button">
            Salir
          </button>
        </header>
        {canManageCatalogs && view === 'catalogs' ? (
          <CatalogPage catalog={catalog} token={session.accessToken} />
        ) : null}
        {canManageCatalogs && view === 'import' ? (
          <ImportWizard token={session.accessToken} />
        ) : null}
        {canManageCatalogs && view === 'plans' ? (
          <SchedulePlanPage token={session.accessToken} />
        ) : null}
        {!canManageCatalogs ? (
          <div className="empty-state">
            <h2>Rol sin acceso administrativo</h2>
            <p>Tu rol puede iniciar sesion, pero no administrar catalogos, importaciones ni planes.</p>
          </div>
        ) : null}
      </section>
    </main>
  );
}

function ImportWizard({ token }: { token: string }) {
  const [file, setFile] = useState<File | null>(null);
  const [mode, setMode] = useState<'VALIDATE_ONLY' | 'IMPORT'>('VALIDATE_ONLY');
  const [result, setResult] = useState<ImportResponse | null>(null);
  const [errors, setErrors] = useState<PageResponse<ImportErrorRow>>({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!file) {
      setError('Selecciona un archivo CSV o XLSX.');
      return;
    }
    setError('');
    setLoading(true);
    try {
      const form = new FormData();
      form.append('file', file);
      form.append('mode', mode);
      const response = await apiForm<ImportResponse>('/api/imports/academic-data', form, token);
      setResult(response);
      if (response.errorCount > 0) {
        const data = await api<PageResponse<ImportErrorRow>>(`/api/imports/${response.importBatchId}/errors?page=0&size=20`, {}, token);
        setErrors(data);
      } else {
        setErrors({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Error importando archivo.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="work-layout">
      <form className="form-panel" onSubmit={submit}>
        <p className="eyebrow">Import Wizard</p>
        <h2>Datos academicos</h2>
        <label>
          Archivo CSV/XLSX
          <input
            accept=".csv,.xlsx"
            onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            required
            type="file"
          />
        </label>
        <label>
          Modo
          <select value={mode} onChange={(event) => setMode(event.target.value as 'VALIDATE_ONLY' | 'IMPORT')}>
            <option value="VALIDATE_ONLY">Validar sin importar</option>
            <option value="IMPORT">Importar si es valido</option>
          </select>
        </label>
        {error ? <ErrorBox message={error} /> : null}
        <button type="submit" disabled={loading}>
          {loading ? 'Procesando...' : mode === 'IMPORT' ? 'Importar' : 'Validar'}
        </button>
      </form>
      <section className="catalog-main">
        <div className="section-title">
          <h2>Resumen</h2>
          {result ? <StatusBadge status={result.status} /> : null}
        </div>
        {result ? (
          <>
            <div className="summary-grid">
              <Metric label="Archivo" value={result.filename} />
              <Metric label="Leidas" value={result.summary.rowsRead} />
              <Metric label="Validas" value={result.summary.rowsValid} />
              <Metric label="Invalidas" value={result.summary.rowsInvalid} />
            </div>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Hoja</th>
                    <th>Fila</th>
                    <th>Columna</th>
                    <th>Valor</th>
                    <th>Error</th>
                    <th>Accion</th>
                  </tr>
                </thead>
                <tbody>
                  {errors.items.map((item) => (
                    <tr key={item.id}>
                      <td>{item.sheetName}</td>
                      <td>{item.rowNumber}</td>
                      <td>{item.columnName}</td>
                      <td>{item.rawValue}</td>
                      <td>{item.message ?? item.code}</td>
                      <td>{item.suggestedAction ?? ''}</td>
                    </tr>
                  ))}
                  {!errors.items.length ? (
                    <tr>
                      <td colSpan={6}>{result.errorCount ? 'Sin errores cargados' : 'Archivo valido'}</td>
                    </tr>
                  ) : null}
                </tbody>
              </table>
            </div>
          </>
        ) : (
          <div className="empty-state">
            <h2>Sin archivo procesado</h2>
            <p>Sube un CSV o XLSX para ver resumen y errores por hoja, fila y columna.</p>
          </div>
        )}
      </section>
    </div>
  );
}

function SchedulePlanPage({ token }: { token: string }) {
  const [planId, setPlanId] = useState('');
  const [status, setStatus] = useState<PlanStatus>('DRAFT');
  const [runId, setRunId] = useState<number | null>(null);
  const [validation, setValidation] = useState<ValidationResponse | null>(null);
  const [generation, setGeneration] = useState<GenerationResponse | null>(null);
  const [violations, setViolations] = useState<Violation[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState('');
  const numericPlanId = Number(planId);
  const hasPlan = Number.isInteger(numericPlanId) && numericPlanId > 0;

  async function runAction<T>(name: string, action: () => Promise<T>) {
    if (!hasPlan) {
      setError('Ingresa un ID de plan valido.');
      return null;
    }
    setError('');
    setLoading(name);
    try {
      return await action();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Error ejecutando accion.');
      return null;
    } finally {
      setLoading('');
    }
  }

  async function validatePlan() {
    const response = await runAction('validate', () => api<ValidationResponse>(`/api/schedule-plans/${numericPlanId}/validate`, { method: 'POST', body: '{}' }, token));
    if (response) {
      setValidation(response);
      setStatus(response.status);
    }
  }

  async function generatePlan() {
    const response = await runAction('generate', () => api<GenerationResponse>(`/api/schedule-plans/${numericPlanId}/generate`, {
      method: 'POST',
      body: JSON.stringify({ solverMode: 'NORMAL', timeLimitSeconds: 30, weights: {} }),
    }, token));
    if (response) {
      setGeneration(response);
      setRunId(response.runId);
      setStatus(response.planStatus);
      void loadViolations(response.runId);
    }
  }

  async function loadViolations(nextRunId = runId) {
    const query = nextRunId ? `?runId=${nextRunId}` : '';
    const response = await runAction('violations', () => api<{ items: Violation[] }>(`/api/schedule-plans/${numericPlanId}/violations${query}`, {}, token));
    if (response) {
      setViolations(response.items);
    }
  }

  async function approvePlan() {
    const response = await runAction('approve', () => api<{ status: PlanStatus }>(`/api/schedule-plans/${numericPlanId}/approve`, {
      method: 'POST',
      body: JSON.stringify({ runId, comment: 'Aprobado desde frontend.' }),
    }, token));
    if (response) {
      setStatus(response.status);
    }
  }

  async function lockPlan() {
    const response = await runAction('lock', () => api<{ status: PlanStatus }>(`/api/schedule-plans/${numericPlanId}/lock`, {
      method: 'POST',
      body: JSON.stringify({ comment: 'Bloqueado desde frontend.' }),
    }, token));
    if (response) {
      setStatus(response.status);
    }
  }

  const canValidate = ['DRAFT', 'INVALID_INPUT', 'GENERATED', 'GENERATED_WITH_CONFLICTS'].includes(status);
  const canGenerate = canValidate && !validation?.hasBlockingErrors;
  const canApprove = ['GENERATED', 'GENERATED_WITH_CONFLICTS'].includes(status) && runId !== null;
  const canLock = status === 'APPROVED';

  return (
    <div className="work-layout">
      <section className="form-panel">
        <p className="eyebrow">Plan de horario</p>
        <h2>Validar y generar</h2>
        <label>
          ID de plan
          <input value={planId} onChange={(event) => setPlanId(event.target.value)} type="number" min="1" />
        </label>
        <StatusBadge status={status} />
        {error ? <ErrorBox message={error} /> : null}
        <div className="action-grid">
          <button type="button" onClick={validatePlan} disabled={!hasPlan || !canValidate || loading !== ''}>
            {loading === 'validate' ? 'Validando...' : 'Validar'}
          </button>
          <button type="button" onClick={generatePlan} disabled={!hasPlan || !canGenerate || loading !== ''}>
            {loading === 'generate' ? 'Generando...' : 'Generar'}
          </button>
          <button type="button" onClick={approvePlan} disabled={!hasPlan || !canApprove || loading !== ''}>
            Aprobar
          </button>
          <button type="button" onClick={lockPlan} disabled={!hasPlan || !canLock || loading !== ''}>
            Bloquear
          </button>
        </div>
      </section>
      <section className="catalog-main">
        <div className="section-title">
          <h2>Resultado</h2>
          {runId ? <span className="muted">Run {runId}</span> : null}
        </div>
        <div className="summary-grid">
          <Metric label="Asignadas" value={generation?.assignedCount ?? 0} />
          <Metric label="Sin asignar" value={generation?.unassignedCount ?? 0} />
          <Metric label="Motor" value={generation?.engineVersion ?? '-'} />
          <Metric label="Seed" value={generation?.seed ?? '-'} />
        </div>
        <IssueTable title="Errores de validacion" items={validation?.issues ?? []} />
        <IssueTable title="Conflictos" items={violations} />
      </section>
    </div>
  );
}

function Metric({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  return <span className={`status status-${status.toLowerCase().replaceAll('_', '-')}`}>{status}</span>;
}

function IssueTable({ title, items }: { title: string; items: (ValidationIssue | Violation)[] }) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th colSpan={4}>{title}</th>
          </tr>
          <tr>
            <th>Severidad</th>
            <th>Codigo</th>
            <th>Mensaje</th>
            <th>Accion</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr key={`${item.code}-${item.id}`}>
              <td>{item.severity}</td>
              <td>{item.code}</td>
              <td>{item.message}</td>
              <td>{'suggestedAction' in item ? item.suggestedAction ?? '' : ''}</td>
            </tr>
          ))}
          {!items.length ? (
            <tr>
              <td colSpan={4}>Sin registros</td>
            </tr>
          ) : null}
        </tbody>
      </table>
    </div>
  );
}

function CatalogPage({ catalog, token }: { catalog: Catalog; token: string }) {
  const [page, setPage] = useState<PageResponse>({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
  const [values, setValues] = useState(() => defaultValues(catalog));
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function load() {
    setError('');
    setLoading(true);
    try {
      const data = await api<PageResponse>(`/api/catalog/${catalog.resource}?page=0&size=20&sort=code,asc`, {}, token);
      setPage(data);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Error cargando catalogo.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    setValues(defaultValues(catalog));
    void load();
  }, [catalog.resource]);

  async function create(event: FormEvent) {
    event.preventDefault();
    setError('');
    try {
      await api(`/api/catalog/${catalog.resource}`, {
        method: 'POST',
        body: JSON.stringify(normalizePayload(catalog, values)),
      }, token);
      setValues(defaultValues(catalog));
      await load();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Error creando registro.');
    }
  }

  return (
    <div className="catalog-layout">
      <section className="catalog-main">
        <div className="section-title">
          <h2>{catalog.title}</h2>
          <button className="ghost" onClick={load} type="button" disabled={loading}>
            Actualizar
          </button>
        </div>
        {error ? <ErrorBox message={error} /> : null}
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                {catalog.columns.map((column) => (
                  <th key={column}>{column}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {page.items.map((item, index) => (
                <tr key={String(item.id ?? index)}>
                  {catalog.columns.map((column) => (
                    <td key={column}>{String(item[column] ?? '')}</td>
                  ))}
                </tr>
              ))}
              {!page.items.length ? (
                <tr>
                  <td colSpan={catalog.columns.length}>{loading ? 'Cargando...' : 'Sin registros'}</td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
        <p className="muted">
          {page.totalItems} registros · pagina {page.page + 1} de {Math.max(page.totalPages, 1)}
        </p>
      </section>
      <form className="form-panel" onSubmit={create}>
        <h2>Crear</h2>
        {catalog.fields.map((field) => (
          <CatalogField
            field={field}
            key={field.name}
            value={values[field.name]}
            onChange={(value) => setValues((current) => ({ ...current, [field.name]: value }))}
          />
        ))}
        <button type="submit">Guardar</button>
      </form>
    </div>
  );
}

function CatalogField({
  field,
  value,
  onChange,
}: {
  field: Field;
  value: unknown;
  onChange: (value: unknown) => void;
}) {
  if (field.type === 'checkbox') {
    return (
      <label className="checkbox">
        <input checked={Boolean(value)} onChange={(event) => onChange(event.target.checked)} type="checkbox" />
        {field.label}
      </label>
    );
  }
  if (field.type === 'select') {
    return (
      <label>
        {field.label}
        <select value={String(value)} onChange={(event) => onChange(event.target.value)}>
          {field.options.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      </label>
    );
  }
  return (
    <label>
      {field.label}
      <input
        value={String(value)}
        onChange={(event) => onChange(event.target.value)}
        type={field.type}
        required={field.required}
      />
    </label>
  );
}

function ErrorBox({ message }: { message: string }) {
  return <div className="error">{message}</div>;
}

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
