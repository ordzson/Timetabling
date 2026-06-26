import React, { FormEvent, useEffect, useMemo, useState } from 'react';
import { CSS } from '@dnd-kit/utilities';
import {
  BookOpen,
  CalendarDays,
  Clock,
  Download,
  Edit3,
  FileSpreadsheet,
  GraduationCap,
  LayoutDashboard,
  LogOut,
  MapPinned,
  PanelLeft,
  Plus,
  Power,
  Save,
  X,
  Users,
} from 'lucide-react';
import {
  DndContext,
  DragEndEvent,
  DragOverlay,
  PointerSensor,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
} from '@dnd-kit/core';
import type { Session } from './types/auth';
import type { AdminView, Catalog, Field, PageResponse } from './types/catalog';
import type { ImportErrorRow, ImportResponse } from './types/import';
import type {
  Assignment,
  GenerationResponse,
  GridView,
  ManualDraft,
  ManualEditResponse,
  PlanStatus,
  SchedulePlanDraft,
  SchedulePlanSummary,
  ScheduleResult,
  SubstitutionDraft,
  SubstitutionResponse,
  UnassignedSession,
  ValidationIssue,
  ValidationResponse,
  Violation,
} from './types/schedule';
import { api, apiUrl } from './api/client';
import { createCatalogItem, listCatalog, updateCatalogItem } from './api/catalogs';
import { importAcademicData, listImportErrors } from './api/imports';
import {
  approveSchedulePlan,
  createSchedulePlan,
  generateSchedulePlan,
  getScheduleResult,
  listSchedulePlans,
  listScheduleViolations,
  lockSchedulePlan,
  submitManualScheduleEdit,
  validateSchedulePlan,
} from './api/schedulePlans';
import { createSubstitution as postSubstitution, listSubstitutions } from './api/substitutions';
import { ActionButton } from './components/ActionButton';
import { Badge } from './components/Badge';
import { EmptyState } from './components/EmptyState';
import { StatusBadge } from './components/StatusBadge';
import { Table } from './components/Table';
import {
  CATALOGS,
  COHORTS_CATALOG,
  COMMON_AREA_CAREERS_CATALOG,
  COMMON_AREAS_CATALOG,
  CURRICULA_CATALOG,
  CURRICULUM_COURSES_CATALOG,
  FIXED_BREAKS_CATALOG,
  JOURNEY_PRESETS,
  ROOM_TYPE_OPTIONS,
  TEACHER_RELATION_PANELS,
  defaultJourneyValues,
  defaultRoomValues,
  type JourneyFormValues,
  type RelationField,
  type RelationLookupKey,
  type RoomFormValues,
  type TeacherRelationConfig,
} from './config/catalogs';

const SESSION_KEY = 'horarios.session';

const NAV_ITEMS: { view: AdminView; label: string; icon: React.ElementType; resource?: string }[] = [
  { view: 'dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { view: 'academia', label: 'Academia', icon: GraduationCap },
  { view: 'teachers', label: 'Docentes', icon: Users, resource: 'teachers' },
  { view: 'time', label: 'Tiempo', icon: Clock, resource: 'journeys' },
  { view: 'rooms', label: 'Espacios', icon: MapPinned, resource: 'rooms' },
  { view: 'import', label: 'Importacion', icon: FileSpreadsheet },
  { view: 'plans', label: 'Planes', icon: CalendarDays },
  { view: 'availability', label: 'Disponibilidad', icon: PanelLeft },
  { view: 'reports', label: 'Reportes', icon: BookOpen },
];

function defaultValues(catalog: Catalog): Record<string, unknown> {
  return Object.fromEntries(
    catalog.fields.map((field) => [
      field.name,
      field.type === 'checkbox' ? true : field.type === 'select' ? field.options[0] : '',
    ]),
  );
}

function normalizePayload(catalog: Catalog, values: Record<string, unknown>) {
  const payload: Record<string, unknown> = {};
  if (catalog.columns.includes('code') && !catalog.fields.some((field) => field.name === 'code')) {
    payload.code = crypto.randomUUID();
  }
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

const TECHNICAL_COLUMNS = new Set(['id', 'code']);
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function userColumns(columns: string[]) {
  return columns.filter((column) => !TECHNICAL_COLUMNS.has(column));
}

function publicText(value: unknown) {
  const text = String(value ?? '').trim();
  return text && !UUID_PATTERN.test(text) ? text : '';
}

function recordName(item: Record<string, unknown>) {
  const label = item.fullName ?? item.name ?? item.section;
  if (label !== undefined && label !== null && String(label).trim()) {
    return String(label);
  }
  return publicText(item.code) || (item.id === undefined || item.id === null ? 'Registro' : `Registro ${item.id}`);
}

function recordId(item?: Record<string, unknown>) {
  return item?.id === undefined || item.id === null ? '' : String(item.id);
}

function courseName(assignment: Assignment) {
  return assignment.courseName || publicText(assignment.courseCode) || `Clase ${assignment.sessionId}`;
}

function unassignedName(session: UnassignedSession) {
  return publicText(session.courseCode) || `Clase ${session.sessionId}`;
}

function readSession() {
  try {
    const raw = localStorage.getItem(SESSION_KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

export function App() {
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
      <div className="login-sticker" aria-hidden="true">
        PAPER-CUT V3
      </div>
      <form className="login-panel" onSubmit={submit}>
        <div className="login-seal" aria-hidden="true">
          <strong>OK</strong>
          <span>JWT</span>
        </div>
        <header className="login-header">
          <h1>Horarios UdeO/UTP</h1>
          <p>Portal de Agenda Academica</p>
        </header>
        <label className="login-field">
          Correo institucional
          <span>
            <b aria-hidden="true">@</b>
            <input
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              type="email"
              placeholder="admin@udeo.edu.gt"
              required
            />
          </span>
        </label>
        <label className="login-field">
          Clave de acceso
          <span>
            <b aria-hidden="true">#</b>
            <input
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              type="password"
              placeholder="************"
              required
            />
          </span>
        </label>
        {error ? <ErrorBox message={error} /> : null}
        <button className="login-submit" type="submit" disabled={loading}>
          <span>{loading ? 'Ingresando...' : 'Iniciar sesion'}</span>
          <span aria-hidden="true">-&gt;</span>
        </button>
        <footer className="login-footer">
          <a href="mailto:soporte@udeo.edu.gt">Soporte de acceso</a>
          <strong>ADMIN ACCESS ONLY</strong>
        </footer>
      </form>
      <div className="login-notes" aria-hidden="true">
        <span />
        <span />
      </div>
    </main>
  );
}

function AdminShell({ session, onLogout }: { session: Session; onLogout: () => void }) {
  const canManageCatalogs = session.user.role === 'ADMIN' || session.user.role === 'SUPERADMIN';
  const [view, setView] = useState<AdminView>('dashboard');
  const [activeResource, setActiveResource] = useState(CATALOGS[0].resource);
  const [topPlan, setTopPlan] = useState<SchedulePlanSummary | null>(null);
  const catalog = useMemo(
    () => CATALOGS.find((item) => item.resource === activeResource) ?? CATALOGS[0],
    [activeResource],
  );

  useEffect(() => {
    if (!canManageCatalogs) {
      return;
    }
    let ignore = false;
    listSchedulePlans('page=0&size=1', session.accessToken)
      .then((data) => {
        if (!ignore) {
          setTopPlan(data.items[0] ?? null);
        }
      })
      .catch(() => {
        if (!ignore) {
          setTopPlan(null);
        }
      });
    return () => {
      ignore = true;
    };
  }, [canManageCatalogs, session.accessToken]);

  function openView(item: (typeof NAV_ITEMS)[number]) {
    setView(item.view);
    if (item.resource) {
      setActiveResource(item.resource);
    }
  }

  return (
    <main className="admin-shell">
      <aside className="sidebar">
        <div>
          <p className="eyebrow">Horarios UdeO/UTP</p>
          <h1>Administracion</h1>
        </div>
        <nav aria-label="Modulos">
          {NAV_ITEMS.map((item) => {
            const Icon = item.icon;
            return (
            <button
              className={view === item.view ? 'active' : ''}
              key={item.view}
              onClick={() => openView(item)}
              type="button"
              disabled={!canManageCatalogs && item.view !== 'availability'}
            >
              <Icon aria-hidden="true" size={18} />
              <span>{item.label}</span>
            </button>
            );
          })}
        </nav>
      </aside>
      <section className="content">
        <header className="topbar">
          <div className="topbar-main">
            <div>
              <strong>{session.user.fullName}</strong>
              <span>{session.user.role}</span>
            </div>
            {topPlan ? (
              <div className="topbar-plan">
                <span>Plan activo</span>
                <strong>{topPlan.name}</strong>
                <StatusBadge status={topPlan.status} />
              </div>
            ) : null}
          </div>
          <div className="topbar-actions">
            <button className="ghost" onClick={() => setView('plans')} type="button" disabled={!canManageCatalogs}>
              Planes
            </button>
            <button className="ghost" onClick={() => setView('import')} type="button" disabled={!canManageCatalogs}>
              Importar
            </button>
            <button className="ghost icon-button" onClick={onLogout} type="button" aria-label="Salir">
              <LogOut aria-hidden="true" size={18} />
            </button>
          </div>
        </header>
        {canManageCatalogs && view === 'dashboard' ? (
          <DashboardHome
            onOpenAcademia={() => setView('academia')}
            onOpenPlans={() => setView('plans')}
            token={session.accessToken}
          />
        ) : null}
        {canManageCatalogs && view === 'academia' ? (
          <AcademiaPage token={session.accessToken} />
        ) : null}
        {canManageCatalogs && view === 'teachers' ? (
          <TeachersPage token={session.accessToken} />
        ) : null}
        {canManageCatalogs && view === 'time' ? (
          <TimePage token={session.accessToken} />
        ) : null}
        {canManageCatalogs && view === 'rooms' ? (
          <RoomsPage token={session.accessToken} />
        ) : null}
        {canManageCatalogs && view === 'import' ? (
          <ImportWizard token={session.accessToken} />
        ) : null}
        {canManageCatalogs && view === 'plans' ? (
          <SchedulePlanPage token={session.accessToken} />
        ) : null}
        {view === 'availability' ? (
          <AvailabilityPage role={session.user.role} token={session.accessToken} />
        ) : null}
        {canManageCatalogs && view === 'reports' ? (
          <ReportsPage token={session.accessToken} />
        ) : null}
        {!canManageCatalogs && view !== 'availability' ? (
          <EmptyState title="Rol sin acceso administrativo">
            Tu rol puede iniciar sesion, pero no administrar catalogos, importaciones ni planes.
          </EmptyState>
        ) : null}
      </section>
    </main>
  );
}

function DashboardHome({
  onOpenAcademia,
  onOpenPlans,
  token,
}: {
  onOpenAcademia: () => void;
  onOpenPlans: () => void;
  token: string;
}) {
  const [catalogCounts, setCatalogCounts] = useState<Record<string, number>>({});
  const [plans, setPlans] = useState<PageResponse<SchedulePlanSummary> | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const latestPlan = plans?.items[0];
  const hasCatalogCounts = Object.keys(catalogCounts).length > 0;

  useEffect(() => {
    let ignore = false;
    async function loadDashboard() {
      setError('');
      setLoading(true);
      const catalogResults = await Promise.allSettled(
        CATALOGS.map(async (catalog) => {
          const data = await listCatalog(catalog.resource, 'page=0&size=1', token);
          return [catalog.resource, data.totalItems] as const;
        }),
      );
      const planResult = await Promise.allSettled([
        listSchedulePlans('page=0&size=5', token),
      ]);
      if (ignore) {
        return;
      }
      setCatalogCounts(
        Object.fromEntries(
          catalogResults
            .filter((result): result is PromiseFulfilledResult<readonly [string, number]> => result.status === 'fulfilled')
            .map((result) => result.value),
        ),
      );
      if (planResult[0]?.status === 'fulfilled') {
        setPlans(planResult[0].value);
      }
      const failures = [
        ...catalogResults.filter((result) => result.status === 'rejected'),
        ...planResult.filter((result) => result.status === 'rejected'),
      ];
      if (failures.length) {
        setError(`No se pudo cargar ${failures.length} dato(s) reales del inicio.`);
      }
      setLoading(false);
    }
    void loadDashboard();
    return () => {
      ignore = true;
    };
  }, [token]);

  return (
    <div className="dashboard-page">
      <section className="dashboard-hero">
        <div className="hero-copy">
          <p className="eyebrow">Plan mas reciente</p>
          <h2>{latestPlan ? latestPlan.name : loading ? 'Cargando planes...' : 'Sin planes creados'}</h2>
          <p>{latestPlan ? `${latestPlan.startDate} a ${latestPlan.endDate}` : 'No hay un plan real para mostrar todavia.'}</p>
        </div>
        <div className="hero-actions" aria-label="Acciones principales">
          <ActionButton type="button" onClick={onOpenPlans}>Abrir planes</ActionButton>
          <ActionButton className="ghost" type="button" onClick={onOpenAcademia}>Revisar academia</ActionButton>
        </div>
      </section>

      {error ? <ErrorBox message={error} /> : null}

      <section className="dashboard-metrics" aria-label="Resumen operativo">
        <Metric label="Planes" value={plans?.totalItems ?? (loading ? '...' : 'Sin dato')} />
        <Metric label="Carreras" value={catalogCounts.careers ?? (loading ? '...' : 'Sin dato')} />
        <Metric label="Cursos" value={catalogCounts.courses ?? (loading ? '...' : 'Sin dato')} />
        <Metric label="Docentes" value={catalogCounts.teachers ?? (loading ? '...' : 'Sin dato')} />
        <Metric label="Aulas" value={catalogCounts.rooms ?? (loading ? '...' : 'Sin dato')} />
        <Metric label="Jornadas" value={catalogCounts.journeys ?? (loading ? '...' : 'Sin dato')} />
      </section>

      <section className="dashboard-board">
        <div className="schedule-preview">
          <div className="section-title">
            <h2>Planes reales</h2>
            {latestPlan ? <StatusBadge status={latestPlan.status} /> : null}
          </div>
          {plans?.items.length ? (
            <Table flat>
              <table>
                <thead>
                  <tr>
                    <th>Nombre</th>
                    <th>Tipo</th>
                    <th>Estado</th>
                    <th>Rango</th>
                  </tr>
                </thead>
                <tbody>
                  {plans.items.map((plan) => (
                    <tr key={plan.id}>
                      <td>{plan.name}</td>
                      <td>{plan.scheduleType}</td>
                      <td><StatusBadge status={plan.status} /></td>
                      <td>{plan.startDate} - {plan.endDate}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Table>
          ) : (
            <EmptyState title={loading ? 'Cargando planes' : 'Sin planes'}>
              {loading ? 'Consultando /api/schedule-plans.' : 'Crea un plan para poblar esta seccion con informacion real.'}
            </EmptyState>
          )}
        </div>

        <aside className="dashboard-side">
          <div className="conflict-list suggestion">
            <h3>Datos reales disponibles</h3>
            <p>{hasCatalogCounts ? 'Conteos de catalogos y lista de planes.' : 'Esperando respuesta de catalogos.'}</p>
          </div>
          <div className="conflict-list suggestion">
            <h3>Resumen operativo</h3>
            <Notice title="Revision por plan">Conflictos y sesiones sin asignar se revisan dentro de cada plan.</Notice>
          </div>
        </aside>
      </section>

      <Table>
        <table>
          <thead>
            <tr>
              <th>Catalogo</th>
              <th>Endpoint</th>
              <th>Total real</th>
              <th>Estado</th>
            </tr>
          </thead>
          <tbody>
            {CATALOGS.map((catalog) => (
              <tr key={catalog.resource}>
                <td>{catalog.title}</td>
                <td>/api/catalog/{catalog.resource}</td>
                <td>{catalogCounts[catalog.resource] ?? (loading ? '...' : 'Sin dato')}</td>
                <td>{catalogCounts[catalog.resource] === undefined ? 'No cargado' : 'Cargado'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Table>
    </div>
  );
}

function AcademiaPage({ token }: { token: string }) {
  const careerCatalog = CATALOGS.find((item) => item.resource === 'careers') ?? CATALOGS[0];
  const [careers, setCareers] = useState<PageResponse>({ items: [], page: 0, size: 50, totalItems: 0, totalPages: 0 });
  const [courses, setCourses] = useState<PageResponse>({ items: [], page: 0, size: 50, totalItems: 0, totalPages: 0 });
  const [selectedCareer, setSelectedCareer] = useState('');
  const [careerValues, setCareerValues] = useState(() => defaultValues(careerCatalog));
  const [editingCareerId, setEditingCareerId] = useState('');
  const [pending, setPending] = useState<Record<string, string>>({});
  const [error, setError] = useState('');
  const [formError, setFormError] = useState('');
  const [loading, setLoading] = useState(true);
  const [savingCareer, setSavingCareer] = useState(false);
  const editingCareer = useMemo(
    () => careers.items.find((career) => recordId(career) === editingCareerId) ?? null,
    [careers.items, editingCareerId],
  );
  const activeCareers = careers.items.filter((career) => Boolean(career.active)).length;
  const selectedCareerName = String(
    careers.items.find((item) => String(item.id ?? item.code) === selectedCareer)?.name ?? 'Carrera sin seleccionar',
  );

  useEffect(() => {
    let ignore = false;
    async function loadAcademia() {
      setError('');
      setPending({});
      setLoading(true);
      const [careerResult, courseResult, curriculaResult, matrixResult, cohortResult] = await Promise.allSettled([
        listCatalog('careers', 'page=0&size=100&sort=code,asc', token),
        listCatalog('courses', 'page=0&size=100&sort=code,asc', token),
        listCatalog('curricula', 'page=0&size=20', token),
        listCatalog('curriculum-courses', 'page=0&size=20', token),
        listCatalog('cohorts', 'page=0&size=20', token),
      ]);
      if (ignore) {
        return;
      }
      if (careerResult.status === 'fulfilled') {
        setCareers(careerResult.value);
        setSelectedCareer((current) => current || String(careerResult.value.items[0]?.id ?? careerResult.value.items[0]?.code ?? ''));
      } else {
        setError(careerResult.reason instanceof Error ? careerResult.reason.message : 'Error cargando carreras.');
      }
      if (courseResult.status === 'fulfilled') {
        setCourses(courseResult.value);
      }
      setPending({
        ...(courseResult.status === 'rejected' ? { courses: 'Catalogo base de cursos no cargo.' } : {}),
        ...(curriculaResult.status === 'rejected' ? { curricula: 'GET /api/catalog/curricula no respondio.' } : {}),
        ...(matrixResult.status === 'rejected' ? { curriculumCourses: 'GET /api/catalog/curriculum-courses no respondio.' } : {}),
        ...(cohortResult.status === 'rejected' ? { cohorts: 'GET /api/catalog/cohorts no respondio.' } : {}),
      });
      setLoading(false);
    }
    void loadAcademia();
    return () => {
      ignore = true;
    };
  }, [token]);

  function resetCareerForm() {
    setEditingCareerId('');
    setCareerValues(defaultValues(careerCatalog));
    setFormError('');
  }

  function editCareer(career: Record<string, unknown>) {
    setFormError('');
    setEditingCareerId(recordId(career));
    setCareerValues({
      name: String(career.name ?? ''),
      active: Boolean(career.active),
    });
  }

  async function reloadCareers(nextSelectedId?: string) {
    const data = await listCatalog('careers', 'page=0&size=100&sort=code,asc', token);
    setCareers(data);
    const fallback = String(data.items[0]?.id ?? data.items[0]?.code ?? '');
    setSelectedCareer(nextSelectedId ?? (selectedCareer || fallback));
  }

  async function saveCareer(event: FormEvent) {
    event.preventDefault();
    setError('');
    setFormError('');
    const name = String(careerValues.name ?? '').trim();
    if (!name) {
      setFormError('Escribe el nombre de la carrera.');
      return;
    }
    setSavingCareer(true);
    try {
      const payload = normalizePayload(careerCatalog, { ...careerValues, name });
      if (editingCareerId) {
        await updateCatalogItem('careers', editingCareerId, { name, active: Boolean(careerValues.active) }, token);
        await reloadCareers(editingCareerId);
      } else {
        const created = await createCatalogItem('careers', payload, token) as Record<string, unknown>;
        await reloadCareers(recordId(created));
      }
      resetCareerForm();
    } catch (caught) {
      setFormError(caught instanceof Error ? caught.message : 'Error guardando carrera.');
    } finally {
      setSavingCareer(false);
    }
  }

  async function toggleCareer(career: Record<string, unknown>) {
    const id = recordId(career);
    if (!id) {
      return;
    }
    setError('');
    setFormError('');
    setSavingCareer(true);
    try {
      await updateCatalogItem('careers', id, { active: !Boolean(career.active) }, token);
      await reloadCareers(selectedCareer);
      if (editingCareerId === id) {
        resetCareerForm();
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Error actualizando carrera.');
    } finally {
      setSavingCareer(false);
    }
  }

  return (
    <div className="academia-page">
      <form className="form-panel career-editor" onSubmit={saveCareer}>
        <div className="section-title">
          <div>
            <p className="eyebrow">Academia</p>
            <h2>{editingCareer ? 'Editar carrera' : 'Nueva carrera'}</h2>
          </div>
          <Badge tone={editingCareer ? 'info' : 'success'}>{editingCareer ? 'Edicion' : 'Crear'}</Badge>
        </div>
        <label>
          Carrera base
          <select value={selectedCareer} onChange={(event) => setSelectedCareer(event.target.value)} disabled={!careers.items.length}>
            {careers.items.map((career) => (
              <option key={String(career.id ?? career.code)} value={String(career.id ?? career.code)}>
                {recordName(career)}
              </option>
            ))}
          </select>
        </label>
        <label>
          Nombre
          <input
            autoComplete="off"
            onChange={(event) => setCareerValues((current) => ({ ...current, name: event.target.value }))}
            placeholder="Ej. Ingenieria en Sistemas"
            required
            value={String(careerValues.name ?? '')}
          />
        </label>
        <label className="checkbox switch-row">
          <input
            checked={Boolean(careerValues.active)}
            onChange={(event) => setCareerValues((current) => ({ ...current, active: event.target.checked }))}
            type="checkbox"
          />
          Carrera activa
        </label>
        {formError ? <ErrorBox message={formError} /> : null}
        <div className="row-actions">
          <button type="submit" disabled={savingCareer}>
            {editingCareer ? <Save aria-hidden="true" size={16} /> : <Plus aria-hidden="true" size={16} />}
            {savingCareer ? 'Guardando...' : editingCareer ? 'Guardar' : 'Crear'}
          </button>
          {editingCareer ? (
            <button className="ghost" onClick={resetCareerForm} type="button" disabled={savingCareer}>
              <X aria-hidden="true" size={16} />
              Cancelar
            </button>
          ) : null}
        </div>
        <div className="career-stats">
          <Metric label="Carreras" value={loading ? '...' : careers.totalItems} />
          <Metric label="Activas" value={loading ? '...' : activeCareers} />
        </div>
        {error ? <ErrorBox message={error} /> : null}
        {!careers.items.length && !loading ? (
          <EmptyState title="Sin carreras">No hay carreras reales desde /api/catalog/careers.</EmptyState>
        ) : null}
      </form>

      <section className="catalog-main">
        <div className="section-title">
          <div>
            <p className="eyebrow">Jerarquia academica</p>
            <h2>{selectedCareerName}</h2>
          </div>
          <Badge tone="info">carrera -&gt; pensum -&gt; semestre -&gt; cohorte</Badge>
        </div>
        <div className="academic-flow">
          <AcademicStep title="1. Carrera" value={selectedCareerName} state={careers.items.length ? 'Disponible' : 'Sin datos'} />
          <AcademicStep title="2. Pensums" value="curricula" state={pending.curricula ? 'Sin datos' : 'Disponible'} pending={pending.curricula} />
          <AcademicStep title="3. Cursos por semestre" value="curriculum-courses" state={pending.curriculumCourses ? 'Sin datos' : 'Disponible'} pending={pending.curriculumCourses} />
          <AcademicStep title="4. Cohortes" value="cohorts" state={pending.cohorts ? 'Sin datos' : 'Disponible'} pending={pending.cohorts} />
        </div>

        <div className="section-title">
          <h2>CRUD de carreras</h2>
          <span className="muted">Crear, leer, editar y activar/desactivar</span>
        </div>
        <Table>
          <table>
            <thead>
              <tr>
                <th>Nombre</th>
                <th>Estado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {careers.items.map((career, index) => {
                const id = recordId(career);
                const isActive = Boolean(career.active);
                return (
                  <tr key={String(career.id ?? career.code ?? index)}>
                    <td>
                      <button
                        className="row-button"
                        onClick={() => setSelectedCareer(String(career.id ?? career.code ?? ''))}
                        type="button"
                      >
                        {recordName(career)}
                      </button>
                    </td>
                    <td>
                      <Badge tone={isActive ? 'success' : 'warning'}>{isActive ? 'Activa' : 'Inactiva'}</Badge>
                    </td>
                    <td>
                      <div className="row-actions">
                        <button className="ghost" onClick={() => editCareer(career)} type="button" disabled={!id || savingCareer}>
                          <Edit3 aria-hidden="true" size={16} />
                          Editar
                        </button>
                        <button className="ghost" onClick={() => toggleCareer(career)} type="button" disabled={!id || savingCareer}>
                          <Power aria-hidden="true" size={16} />
                          {isActive ? 'Desactivar' : 'Activar'}
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
              {!careers.items.length ? (
                <tr>
                  <td colSpan={3}>{loading ? 'Cargando...' : 'Sin carreras reales cargadas'}</td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </Table>

        <div className="section-title">
          <h2>Catalogo base de cursos</h2>
          <span className="muted">{courses.totalItems} cursos reales</span>
        </div>
        {pending.courses ? <Notice title="Cursos sin cargar">{pending.courses}</Notice> : null}
        <Table>
          <table>
            <thead>
              <tr>
                <th>Nombre</th>
                <th>Lab</th>
                <th>Bloques min</th>
                <th>Bloques max</th>
              </tr>
            </thead>
            <tbody>
              {courses.items.map((course, index) => (
                <tr key={String(course.id ?? course.code ?? index)}>
                  <td>{String(course.name ?? '')}</td>
                  <td>{String(course.requiresLab ?? '')}</td>
                  <td>{String(course.weeklyBlocksMin ?? '')}</td>
                  <td>{String(course.weeklyBlocksMax ?? '')}</td>
                </tr>
              ))}
              {!courses.items.length ? (
                <tr>
                  <td colSpan={4}>{loading ? 'Cargando...' : 'Sin cursos reales cargados'}</td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </Table>
        <div className="relation-grid">
          <CatalogMiniTable catalog={CURRICULA_CATALOG} token={token} />
          <CatalogMiniTable catalog={CURRICULUM_COURSES_CATALOG} token={token} />
          <CatalogMiniTable catalog={COHORTS_CATALOG} token={token} />
        </div>
      </section>
    </div>
  );
}

function AcademicStep({
  title,
  value,
  state,
  pending,
}: {
  title: string;
  value: string;
  state: string;
  pending?: string;
}) {
  return (
    <article className="academic-step">
      <div className="section-title">
        <h2>{title}</h2>
        <Badge tone={pending ? 'warning' : 'success'}>{state}</Badge>
      </div>
      <p>{value}</p>
      {pending ? <Notice title="Sin datos">{pending}</Notice> : null}
    </article>
  );
}

function TeachersPage({ token }: { token: string }) {
  const teacherCatalog = CATALOGS.find((item) => item.resource === 'teachers') ?? CATALOGS[0];
  const [teachers, setTeachers] = useState<PageResponse>({ items: [], page: 0, size: 50, totalItems: 0, totalPages: 0 });
  const [careers, setCareers] = useState<PageResponse>({ items: [], page: 0, size: 50, totalItems: 0, totalPages: 0 });
  const [journeys, setJourneys] = useState<PageResponse>({ items: [], page: 0, size: 50, totalItems: 0, totalPages: 0 });
  const [courses, setCourses] = useState<PageResponse>({ items: [], page: 0, size: 50, totalItems: 0, totalPages: 0 });
  const [values, setValues] = useState(() => defaultValues(teacherCatalog));
  const [selectedTeacherId, setSelectedTeacherId] = useState('');
  const [pending, setPending] = useState<Record<string, string>>({});
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const selectedTeacher = useMemo(
    () => teachers.items.find((teacher) => recordId(teacher) === selectedTeacherId) ?? null,
    [selectedTeacherId, teachers.items],
  );
  const selectedTeacherName = selectedTeacher ? recordName(selectedTeacher) : 'Seleccione un docente';
  const assignmentPanels = TEACHER_RELATION_PANELS.slice(0, 2);
  const availabilityPanel = TEACHER_RELATION_PANELS[2];

  useEffect(() => {
    let ignore = false;
    async function loadTeachers() {
      setError('');
      setPending({});
      setLoading(true);
      const [teacherResult, careerResult, journeyResult, courseResult, careerJourneyResult, teacherCoursesResult, availabilityResult] = await Promise.allSettled([
        listCatalog('teachers', 'page=0&size=100&sort=code,asc', token),
        listCatalog('careers', 'page=0&size=100&sort=code,asc', token),
        listCatalog('journeys', 'page=0&size=100&sort=code,asc', token),
        listCatalog('courses', 'page=0&size=100&sort=code,asc', token),
        listCatalog('teacher-career-journeys', 'page=0&size=20', token),
        listCatalog('teacher-courses', 'page=0&size=20', token),
        listCatalog('teacher-availability', 'page=0&size=20', token),
      ]);
      if (ignore) {
        return;
      }
      if (teacherResult.status === 'fulfilled') {
        const nextTeachers = teacherResult.value;
        setTeachers(nextTeachers);
        setSelectedTeacherId((current) => {
          if (current && nextTeachers.items.some((teacher) => recordId(teacher) === current)) {
            return current;
          }
          return recordId(nextTeachers.items[0]);
        });
      } else {
        setError(teacherResult.reason instanceof Error ? teacherResult.reason.message : 'Error cargando docentes.');
      }
      if (careerResult.status === 'fulfilled') {
        setCareers(careerResult.value);
      }
      if (journeyResult.status === 'fulfilled') {
        setJourneys(journeyResult.value);
      }
      if (courseResult.status === 'fulfilled') {
        setCourses(courseResult.value);
      }
      setPending({
        ...(careerJourneyResult.status === 'rejected' ? { careerJourneys: 'GET /api/catalog/teacher-career-journeys no respondio.' } : {}),
        ...(teacherCoursesResult.status === 'rejected' ? { courses: 'GET /api/catalog/teacher-courses no respondio.' } : {}),
        ...(availabilityResult.status === 'rejected' ? { availability: 'GET /api/catalog/teacher-availability no respondio para CRUD admin.' } : {}),
      });
      setLoading(false);
    }
    void loadTeachers();
    return () => {
      ignore = true;
    };
  }, [token]);

  async function createTeacher(event: FormEvent) {
    event.preventDefault();
    setError('');
    setCreating(true);
    try {
      const payload = normalizePayload(teacherCatalog, values);
      const created = await createCatalogItem('teachers', payload, token);
      const createdRecord = created && typeof created === 'object' ? created as Record<string, unknown> : undefined;
      setValues(defaultValues(teacherCatalog));
      const nextTeachers = await listCatalog('teachers', 'page=0&size=100&sort=code,asc', token);
      setTeachers(nextTeachers);
      const createdId = recordId(createdRecord)
        || recordId(nextTeachers.items.find((teacher) => String(teacher.fullName ?? '') === String(payload.fullName ?? '')));
      if (createdId) {
        setSelectedTeacherId(createdId);
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Error creando docente.');
    } finally {
      setCreating(false);
    }
  }

  return (
    <div className="teachers-page">
      <section className="form-panel teacher-sidebar">
        <p className="eyebrow">Paso 1</p>
        <div className="section-title">
          <h2>Crear o elegir docente</h2>
          <Badge tone={teachers.items.length ? 'success' : 'warning'}>{loading ? 'Cargando' : `${teachers.totalItems} docentes`}</Badge>
        </div>
        <label>
          Docente de trabajo
          <select value={selectedTeacherId} onChange={(event) => setSelectedTeacherId(event.target.value)} disabled={!teachers.items.length}>
            <option value="">Seleccione docente</option>
            {teachers.items.map((teacher) => (
              <option key={recordId(teacher)} value={recordId(teacher)}>
                {recordName(teacher)}
              </option>
            ))}
          </select>
        </label>
        <div className="teacher-selected">
          <span>Seleccion actual</span>
          <strong>{selectedTeacherName}</strong>
          <small>{selectedTeacher ? `Carga: ${String(selectedTeacher.minCourses ?? '-')} a ${String(selectedTeacher.maxCourses ?? '-')} cursos` : 'Crea o selecciona un docente.'}</small>
        </div>
        <form className="inline-form teacher-create-form" onSubmit={createTeacher}>
          <h3>Nuevo docente</h3>
          <div className="teacher-form-grid">
            {teacherCatalog.fields.map((field) => (
              <CatalogField
                field={field}
                key={field.name}
                value={values[field.name]}
                onChange={(value) => setValues((current) => ({ ...current, [field.name]: value }))}
              />
            ))}
          </div>
          <button type="submit" disabled={creating}>
            <Plus aria-hidden="true" size={18} />
            {creating ? 'Guardando...' : 'Guardar docente'}
          </button>
        </form>
      </section>

      <section className="catalog-main">
        {error ? <ErrorBox message={error} /> : null}

        <section className="teacher-workbench">
          <div className="section-title">
            <div>
              <p className="eyebrow">Paso 2</p>
              <h2>{selectedTeacherName}</h2>
            </div>
            <Badge tone={selectedTeacher ? 'success' : 'warning'}>{selectedTeacher ? 'Listo para asignar' : 'Sin docente'}</Badge>
          </div>
          <div className="teacher-flow">
            <TeacherFlowStep title="1. Docente" value={selectedTeacher ? 'Seleccionado' : 'Pendiente'} />
            <TeacherFlowStep title="2. Carreras" value={`${careers.totalItems} disponibles`} pending={pending.careerJourneys} />
            <TeacherFlowStep title="3. Cursos" value={`${courses.totalItems} disponibles`} pending={pending.courses} />
          </div>
        </section>

        <div className="teacher-assignment-grid">
          {assignmentPanels.map((panel) => (
            <TeacherRelationPanel
              config={panel}
              key={panel.resource}
              lookups={{
                teachers: teachers.items,
                careers: careers.items,
                journeys: journeys.items,
                courses: courses.items,
              }}
              selectedTeacherId={selectedTeacherId}
              selectedTeacherName={selectedTeacherName}
              token={token}
            />
          ))}
        </div>

        {availabilityPanel ? (
          <TeacherRelationPanel
            config={availabilityPanel}
            lookups={{
              teachers: teachers.items,
              careers: careers.items,
              journeys: journeys.items,
              courses: courses.items,
            }}
            selectedTeacherId={selectedTeacherId}
            selectedTeacherName={selectedTeacherName}
            token={token}
          />
        ) : null}

        <div className="section-title">
          <h2>Catalogo de docentes</h2>
          <span className="muted">Selecciona una fila para asignar carreras y cursos.</span>
        </div>
        <Table>
          <table>
            <thead>
              <tr>
                <th>Nombre</th>
                <th>Prioridad</th>
                <th>Min</th>
                <th>Max</th>
                <th>Activo</th>
                <th>Trabajo</th>
              </tr>
            </thead>
            <tbody>
              {teachers.items.map((teacher, index) => (
                <tr className={recordId(teacher) === selectedTeacherId ? 'selected-row' : ''} key={String(teacher.id ?? teacher.code ?? index)}>
                  <td>{String(teacher.fullName ?? '')}</td>
                  <td>{String(teacher.priority ?? '')}</td>
                  <td>{String(teacher.minCourses ?? '')}</td>
                  <td>{String(teacher.maxCourses ?? '')}</td>
                  <td>{String(teacher.active ?? '')}</td>
                  <td>
                    <button className="ghost row-button" type="button" onClick={() => setSelectedTeacherId(recordId(teacher))}>
                      Elegir
                    </button>
                  </td>
                </tr>
              ))}
              {!teachers.items.length ? (
                <tr>
                  <td colSpan={6}>{loading ? 'Cargando...' : 'Sin docentes reales'}</td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </Table>
      </section>
    </div>
  );
}

function TeacherFlowStep({ title, value, pending }: { title: string; value: string; pending?: string }) {
  return (
    <article className="teacher-flow-step">
      <span>{title}</span>
      <strong>{pending ? 'Sin datos' : value}</strong>
      {pending ? <small>{pending}</small> : null}
    </article>
  );
}

function AvailabilityPage({ role, token }: { role: string; token: string }) {
  const isTeacher = role === 'TEACHER';
  const [cells, setCells] = useState<AvailabilityCell[][]>(() => availabilityCells([]));
  const [message, setMessage] = useState('Cargando disponibilidad...');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let ignore = false;
    if (!isTeacher) {
      setMessage('La edicion personal aplica a usuarios TEACHER.');
      return;
    }
    setLoading(true);
    setError('');
    api<TeacherAvailabilityResponse>('/api/teacher/availability', {}, token)
      .then((data) => {
        if (!ignore) {
          setCells(availabilityCells(data.items));
          setMessage(`${data.items.length} bloque(s) cargado(s).`);
        }
      })
      .catch((caught) => {
        if (!ignore) {
          setError(caught instanceof Error ? caught.message : 'Error cargando disponibilidad.');
          setMessage('No se pudo cargar disponibilidad.');
        }
      })
      .finally(() => {
        if (!ignore) {
          setLoading(false);
        }
      });
    return () => {
      ignore = true;
    };
  }, [isTeacher, token]);

  function toggleCell(blockIndex: number, dayIndex: number) {
    setCells((current) =>
      current.map((row, rowIndex) =>
        row.map((cell, colIndex) =>
          rowIndex === blockIndex && colIndex === dayIndex
            ? { ...cell, preference: nextAvailabilityPreference(cell.preference) }
            : cell,
        ),
      ),
    );
  }

  async function saveAvailability() {
    setError('');
    setSaving(true);
    try {
      const data = await api<TeacherAvailabilityResponse>('/api/teacher/availability', {
        method: 'PUT',
        body: JSON.stringify({ items: availabilityPayload(cells) }),
      }, token);
      setCells(availabilityCells(data.items));
      setMessage('Disponibilidad guardada.');
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Error guardando disponibilidad.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="availability-page">
      <section className="form-panel">
        <p className="eyebrow">Disponibilidad docente</p>
        <h2>{isTeacher ? 'Mi matriz' : 'Administracion'}</h2>
        <Notice title="Estado de disponibilidad">{message}</Notice>
        {error ? <ErrorBox message={error} /> : null}
        {isTeacher ? (
          <button onClick={saveAvailability} type="button" disabled={loading || saving}>
            {saving ? 'Guardando...' : 'Guardar disponibilidad'}
          </button>
        ) : null}
      </section>
      <section className="catalog-main">
        <div className="section-title">
          <h2>Dia x bloque</h2>
          <Badge tone={isTeacher ? 'success' : 'info'}>{isTeacher ? 'editable' : 'solo docentes'}</Badge>
        </div>
        {isTeacher ? (
          <AvailabilityMatrix cells={cells} locked={loading || saving} onToggle={toggleCell} />
        ) : (
          <EmptyState title="Edicion docente">
            Administracion de disponibilidad por docente esta en Docentes.
          </EmptyState>
        )}
      </section>
    </div>
  );
}

function TimePage({ token }: { token: string }) {
  const catalog = CATALOGS.find((item) => item.resource === 'journeys') ?? CATALOGS[0];
  const [page, setPage] = useState<PageResponse>({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
  const [values, setValues] = useState(() => defaultJourneyValues());
  const [error, setError] = useState('');
  const [formError, setFormError] = useState('');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const validationMessage = journeyFormError(values);
  const previewMessage = journeyFormError({ ...values, name: String(values.name ?? '').trim() || 'Vista previa' });
  const canCreate = !saving && !validationMessage;

  function updateValue(field: keyof JourneyFormValues, value: string) {
    setFormError('');
    setValues((current) => ({ ...current, [field]: value }));
  }

  function applyPreset(preset: JourneyFormValues) {
    setFormError('');
    setValues({ ...preset });
  }

  async function load() {
    setError('');
    setLoading(true);
    try {
      setPage(await listCatalog('journeys', 'page=0&size=50&sort=code,asc', token));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Error cargando jornadas.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, [token]);

  async function create(event: FormEvent) {
    event.preventDefault();
    setError('');
    setFormError('');
    if (validationMessage) {
      setFormError(validationMessage);
      return;
    }
    setSaving(true);
    try {
      await createCatalogItem('journeys', normalizePayload(catalog, values), token);
      setValues(defaultJourneyValues());
      await load();
    } catch (caught) {
      setFormError(caught instanceof Error ? caught.message : 'Error creando jornada.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="time-page">
      <form className="form-panel time-builder" onSubmit={create}>
        <div className="section-title">
          <div>
            <p className="eyebrow">Nueva jornada</p>
            <h2>Horario base</h2>
          </div>
          <Badge tone={canCreate ? 'success' : 'info'}>{canCreate ? 'Listo' : 'Borrador'}</Badge>
        </div>

        <div className="time-presets" aria-label="Plantillas de jornada">
          {JOURNEY_PRESETS.map((preset) => (
            <button className="ghost" key={preset.name} onClick={() => applyPreset(preset)} type="button">
              <strong>{preset.name}</strong>
              <span>{formatTimeRange(preset)}</span>
            </button>
          ))}
        </div>

        <label>
          Nombre
          <input
            value={String(values.name ?? '')}
            onChange={(event) => updateValue('name', event.target.value)}
            placeholder="Ej. Matutina"
            required
          />
        </label>

        <div className="time-form-grid">
          <label>
            Inicio
            <input
              value={String(values.startTime ?? '')}
              onChange={(event) => updateValue('startTime', event.target.value)}
              type="time"
              required
            />
          </label>
          <label>
            Fin
            <input
              value={String(values.endTime ?? '')}
              onChange={(event) => updateValue('endTime', event.target.value)}
              type="time"
              required
            />
          </label>
          <label>
            Minutos por bloque
            <input
              min="1"
              step="5"
              value={String(values.blockMinutes ?? '')}
              onChange={(event) => updateValue('blockMinutes', event.target.value)}
              type="number"
              required
            />
          </label>
        </div>

        <div className={`time-preview ${previewMessage ? 'invalid' : ''}`} aria-live="polite">
          <span>Vista previa</span>
          <strong>{journeyBlockLabel(values)}</strong>
          <small>{journeyPreviewDetail(values)}</small>
        </div>

        {formError ? <ErrorBox message={formError} /> : null}
        <button type="submit" disabled={!canCreate}>
          <Plus aria-hidden="true" size={18} /> {saving ? 'Guardando...' : 'Guardar jornada'}
        </button>
      </form>

      <section className="catalog-main">
        <div className="section-title">
          <div>
            <p className="eyebrow">Tiempo</p>
            <h2>Jornadas</h2>
          </div>
          <button className="ghost" onClick={load} type="button" disabled={loading}>
            Actualizar
          </button>
        </div>
        {error ? <ErrorBox message={error} /> : null}
        <Table>
          <table>
            <thead>
              <tr>
                <th>Nombre</th>
                <th>Horario</th>
                <th>Bloque</th>
                <th>Bloques</th>
              </tr>
            </thead>
            <tbody>
              {page.items.map((journey, index) => (
                <tr key={String(journey.id ?? journey.code ?? index)}>
                  <td>{String(journey.name ?? '')}</td>
                  <td>
                    <span className="time-range">
                      <strong>{formatTimeRange(journey)}</strong>
                      <small>{journeyDurationLabel(journey)}</small>
                    </span>
                  </td>
                  <td>{String(journey.blockMinutes ?? '')} min</td>
                  <td>
                    <Badge tone={countJourneyBlocks(journey) === 'Sin dato' ? 'warning' : 'info'}>
                      {journeyBlockLabel(journey)}
                    </Badge>
                  </td>
                </tr>
              ))}
              {!page.items.length ? (
                <tr>
                  <td colSpan={4}>{loading ? 'Cargando...' : 'Sin jornadas reales'}</td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </Table>
        <section className="form-panel">
          <div className="section-title">
            <h2>Descansos fijos</h2>
            <Badge tone="info">fixed-breaks</Badge>
          </div>
          <CatalogMiniTable catalog={FIXED_BREAKS_CATALOG} token={token} />
        </section>
      </section>
    </div>
  );
}

function RoomsPage({ token }: { token: string }) {
  const catalog = CATALOGS.find((item) => item.resource === 'rooms') ?? CATALOGS[0];
  const [page, setPage] = useState<PageResponse>({ items: [], page: 0, size: 50, totalItems: 0, totalPages: 0 });
  const [values, setValues] = useState<RoomFormValues>(() => defaultRoomValues());
  const [error, setError] = useState('');
  const [formError, setFormError] = useState('');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const rooms = useMemo(() => [...page.items].sort(compareRooms), [page.items]);
  const validationMessage = roomFormError(values);
  const canCreate = !saving && !validationMessage;
  const activeRooms = page.items.filter((room) => Boolean(room.active)).length;
  const labReadyRooms = page.items.filter((room) => room.type === 'LAB' || room.type === 'MIXED').length;
  const totalCapacity = page.items.reduce((sum, room) => sum + roomCapacity(room), 0);

  function updateValue(field: keyof RoomFormValues, value: string | boolean) {
    setFormError('');
    setValues((current) => ({ ...current, [field]: value }));
  }

  async function load() {
    setError('');
    setLoading(true);
    try {
      setPage(await listCatalog('rooms', 'page=0&size=50&sort=code,asc', token));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Error cargando aulas.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, [token]);

  async function create(event: FormEvent) {
    event.preventDefault();
    setError('');
    setFormError('');
    if (validationMessage) {
      setFormError(validationMessage);
      return;
    }
    setSaving(true);
    try {
      await createCatalogItem('rooms', normalizePayload(catalog, values), token);
      setValues(defaultRoomValues());
      await load();
    } catch (caught) {
      setFormError(caught instanceof Error ? caught.message : 'Error creando aula.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="rooms-page">
      <div className="rooms-workspace">
        <form className="form-panel room-builder" onSubmit={create}>
          <div className="section-title">
            <div>
              <p className="eyebrow">Nueva aula</p>
              <h2>Espacio fisico</h2>
            </div>
            <Badge tone={canCreate ? 'success' : 'info'}>{canCreate ? 'Listo' : 'Borrador'}</Badge>
          </div>

          <label>
            Capacidad
            <input
              min="1"
              onChange={(event) => updateValue('capacity', event.target.value)}
              placeholder="Ej. 35"
              required
              type="number"
              value={values.capacity}
            />
          </label>

          <div className="room-form-grid">
            <label>
              Nivel
              <input
                min="0"
                onChange={(event) => updateValue('floor', event.target.value)}
                placeholder="Ej. 2"
                required
                type="number"
                value={values.floor}
              />
            </label>
            <label>
              Numero
              <input
                min="1"
                onChange={(event) => updateValue('number', event.target.value)}
                placeholder="Ej. 204"
                required
                type="number"
                value={values.number}
              />
            </label>
          </div>

          <div className="room-type-field">
            <span>Tipo de espacio</span>
            <div className="room-type-picker" role="radiogroup" aria-label="Tipo de aula">
              {ROOM_TYPE_OPTIONS.map((option) => (
                <button
                  aria-pressed={values.type === option.value}
                  className={`ghost ${values.type === option.value ? 'active' : ''}`}
                  key={option.value}
                  onClick={() => updateValue('type', option.value)}
                  type="button"
                >
                  <strong>{option.label}</strong>
                  <span>{option.help}</span>
                </button>
              ))}
            </div>
          </div>

          <label className="checkbox switch-row">
            <input checked={values.active} onChange={(event) => updateValue('active', event.target.checked)} type="checkbox" />
            Aula activa
          </label>

          <div className={`room-preview ${validationMessage ? 'invalid' : ''}`} aria-live="polite">
            <span>Vista previa</span>
            <strong>{roomPreviewTitle(values)}</strong>
            <small>{validationMessage || roomPreviewDetail(values)}</small>
          </div>

          {formError ? <ErrorBox message={formError} /> : null}
          <button type="submit" disabled={!canCreate}>
            <Plus aria-hidden="true" size={18} /> {saving ? 'Guardando...' : 'Guardar aula'}
          </button>
        </form>

        <section className="catalog-main">
          <div className="section-title">
            <div>
              <p className="eyebrow">Espacios</p>
              <h2>Aulas registradas</h2>
            </div>
            <button className="ghost" onClick={load} type="button" disabled={loading}>
              Actualizar
            </button>
          </div>
          {error ? <ErrorBox message={error} /> : null}
          <div className="summary-grid">
            <Metric label="Aulas" value={page.totalItems || (loading ? '...' : 0)} />
            <Metric label="Activas" value={activeRooms || (loading ? '...' : 0)} />
            <Metric label="Lab o mixta" value={labReadyRooms || (loading ? '...' : 0)} />
            <Metric label="Cupos" value={totalCapacity || (loading ? '...' : 0)} />
          </div>
          <Table>
            <table>
              <thead>
                <tr>
                  <th>Aula</th>
                  <th>Capacidad</th>
                  <th>Tipo</th>
                  <th>Ubicacion</th>
                  <th>Estado</th>
                </tr>
              </thead>
              <tbody>
                {rooms.map((room, index) => (
                  <tr key={String(room.id ?? room.code ?? index)}>
                    <td>
                      <span className="room-name">
                        <strong>{roomPreviewTitle(room)}</strong>
                        <small>{publicText(room.code) || 'Codigo interno'}</small>
                      </span>
                    </td>
                    <td>{roomCapacityLabel(room)}</td>
                    <td><Badge tone={roomTypeTone(room.type)}>{roomTypeLabel(room.type)}</Badge></td>
                    <td>{roomLocation(room)}</td>
                    <td>{room.active ? 'Activa' : 'Inactiva'}</td>
                  </tr>
                ))}
                {!rooms.length ? (
                  <tr>
                    <td colSpan={5}>{loading ? 'Cargando...' : 'Sin aulas reales'}</td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </Table>
          <p className="muted">
            {page.totalItems} registros · pagina {page.page + 1} de {Math.max(page.totalPages, 1)}
          </p>
        </section>
      </div>
      <section className="form-panel">
        <div className="section-title">
          <h2>Recursos y areas comunes</h2>
          <Badge tone="info">Catalogos activos</Badge>
        </div>
        <CatalogMiniTable catalog={COMMON_AREAS_CATALOG} token={token} />
        <CatalogMiniTable catalog={COMMON_AREA_CAREERS_CATALOG} token={token} />
      </section>
    </div>
  );
}

function roomFormError(room: RoomFormValues) {
  if (roomCapacity(room) < 1) {
    return 'La capacidad debe ser mayor a 0.';
  }
  if (roomInteger(room.floor) < 0) {
    return 'El nivel debe ser 0 o mayor.';
  }
  if (roomInteger(room.number) < 1) {
    return 'El numero debe ser mayor a 0.';
  }
  return '';
}

function roomPreviewTitle(room: Record<string, unknown>) {
  const floor = String(room.floor ?? '').trim();
  const number = String(room.number ?? '').trim();
  if (floor && number) {
    return `Nivel ${floor} · Aula ${number}`;
  }
  if (number) {
    return `Aula ${number}`;
  }
  if (floor) {
    return `Nivel ${floor}`;
  }
  return 'Nueva aula';
}

function roomPreviewDetail(room: Record<string, unknown>) {
  return `${roomCapacityLabel(room)} · ${roomTypeLabel(room.type)} · ${room.active ? 'Activa' : 'Inactiva'}`;
}

function roomLocation(room: Record<string, unknown>) {
  const location = roomPreviewTitle(room);
  return location === 'Nueva aula' ? 'Sin ubicacion' : location;
}

function roomCapacityLabel(room: Record<string, unknown>) {
  const capacity = roomCapacity(room);
  return capacity ? `${capacity} estudiantes` : 'Sin dato';
}

function roomCapacity(room: Record<string, unknown>) {
  const capacity = Number(room.capacity);
  return Number.isFinite(capacity) && capacity > 0 ? capacity : 0;
}

function roomInteger(value: unknown) {
  if (!String(value ?? '').trim()) {
    return -1;
  }
  const number = Number(value);
  return Number.isInteger(number) ? number : -1;
}

function roomSortValue(value: unknown) {
  const number = Number(value);
  return Number.isFinite(number) ? number : Number.MAX_SAFE_INTEGER;
}

function compareRooms(left: Record<string, unknown>, right: Record<string, unknown>) {
  const floorDiff = roomSortValue(left.floor) - roomSortValue(right.floor);
  if (floorDiff) {
    return floorDiff;
  }
  const numberDiff = roomSortValue(left.number) - roomSortValue(right.number);
  if (numberDiff) {
    return numberDiff;
  }
  return String(left.code ?? '').localeCompare(String(right.code ?? ''));
}

function roomTypeLabel(value: unknown) {
  return ROOM_TYPE_OPTIONS.find((option) => option.value === value)?.label ?? String(value ?? 'Tipo');
}

function roomTypeTone(value: unknown): 'info' | 'success' | 'warning' {
  return ROOM_TYPE_OPTIONS.find((option) => option.value === value)?.tone ?? 'info';
}

function TeacherRelationPanel({
  config,
  lookups,
  selectedTeacherId,
  selectedTeacherName,
  token,
}: {
  config: TeacherRelationConfig;
  lookups: Record<RelationLookupKey, Record<string, unknown>[]>;
  selectedTeacherId?: string;
  selectedTeacherName?: string;
  token: string;
}) {
  const [page, setPage] = useState<PageResponse>({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
  const [values, setValues] = useState<Record<string, unknown>>(() => relationDefaults(config.fields));
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [creating, setCreating] = useState(false);
  const hasTeacherField = config.fields.some((field) => field.name === 'teacherId');
  const focusTeacherId = hasTeacherField && selectedTeacherId ? selectedTeacherId : '';
  const missingLookup = config.fields.some((field) => field.kind === 'select' && field.name !== 'teacherId' && !lookups[field.lookup].length)
    || (hasTeacherField && !focusTeacherId && !lookups.teachers.length);
  const blocked = hasTeacherField && !focusTeacherId;
  const sortColumn = config.columns[0] ?? 'id';
  const columns = userColumns(config.columns).filter((column) => !(focusTeacherId && column === 'teacherId'));
  const formFields = focusTeacherId ? config.fields.filter((field) => field.name !== 'teacherId') : config.fields;
  const visibleItems = focusTeacherId
    ? page.items.filter((item) => String(item.teacherId ?? '') === focusTeacherId)
    : page.items;
  const defaultRelationValues = () => ({
    ...relationDefaults(config.fields),
    ...(focusTeacherId ? { teacherId: focusTeacherId } : {}),
  });

  async function load() {
    setError('');
    setLoading(true);
    try {
      setPage(await listCatalog(config.resource, `page=0&size=100&sort=${sortColumn},asc`, token));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : `Error cargando ${config.title}.`);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    setValues(defaultRelationValues());
    void load();
  }, [config, focusTeacherId, token]);

  async function create(event: FormEvent) {
    event.preventDefault();
    setError('');
    const payload = relationPayload(config.fields, {
      ...values,
      ...(focusTeacherId ? { teacherId: focusTeacherId } : {}),
    });
    if (!payload) {
      setError('Complete los campos requeridos.');
      return;
    }
    setCreating(true);
    try {
      await createCatalogItem(config.resource, payload, token);
      setValues(defaultRelationValues());
      await load();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Error creando relacion.');
    } finally {
      setCreating(false);
    }
  }

  return (
    <article className="relation-panel">
      <div className="section-title">
        <h2>{config.title}</h2>
        <Badge tone={error ? 'warning' : 'info'}>{error ? 'Sin datos' : `${visibleItems.length} asignados`}</Badge>
      </div>
      <p>{focusTeacherId ? `${selectedTeacherName ?? 'Docente'}: ${config.description}` : config.description}</p>
      {blocked ? <Notice title="Seleccione docente">Elige o crea un docente antes de asignar.</Notice> : null}
      {error ? <Notice title="No cargado">{error}</Notice> : null}
      <Table flat>
        <table>
          <thead>
            <tr>
              {columns.map((column) => (
                <th key={column}>{column}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {visibleItems.map((item, index) => (
              <tr key={String(item.id ?? index)}>
                {columns.map((column) => (
                  <td key={column}>{relationCell(column, item, config.fields, lookups)}</td>
                ))}
              </tr>
            ))}
            {!visibleItems.length ? (
              <tr>
                <td colSpan={Math.max(columns.length, 1)}>{loading ? 'Cargando...' : 'Sin asignaciones para este docente'}</td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </Table>
      <form className="inline-form" onSubmit={create}>
        <h3>{focusTeacherId ? 'Agregar a este docente' : 'Agregar'}</h3>
        {formFields.map((field) => (
          <RelationFieldControl
            field={field}
            key={field.name}
            lookups={lookups}
            value={values[field.name]}
            onChange={(value) => setValues((current) => ({ ...current, [field.name]: value }))}
          />
        ))}
        <button type="submit" disabled={creating || missingLookup || blocked}>
          <Plus aria-hidden="true" size={18} />
          {creating ? 'Guardando...' : 'Guardar'}
        </button>
      </form>
    </article>
  );
}

function relationDefaults(fields: RelationField[]): Record<string, unknown> {
  return Object.fromEntries(
    fields.map((field) => [
      field.name,
      field.kind === 'checkbox'
        ? field.defaultValue ?? true
        : field.kind === 'number'
          ? String(field.defaultValue ?? '')
          : field.kind === 'text'
            ? field.defaultValue ?? ''
            : '',
    ]),
  );
}

function relationPayload(fields: RelationField[], values: Record<string, unknown>) {
  const payload: Record<string, unknown> = {};
  for (const field of fields) {
    const value = values[field.name];
    if (field.kind === 'checkbox') {
      payload[field.name] = Boolean(value);
      continue;
    }
    const text = String(value ?? '').trim();
    if (!text) {
      return null;
    }
    payload[field.name] = field.kind === 'text' ? text : Number(text);
    if (typeof payload[field.name] === 'number' && !Number.isFinite(payload[field.name])) {
      return null;
    }
  }
  return payload;
}

function relationCell(
  column: string,
  item: Record<string, unknown>,
  fields: RelationField[],
  lookups: Record<RelationLookupKey, Record<string, unknown>[]>,
) {
  const value = item[column];
  const field = fields.find((current) => current.name === column);
  if (field?.kind === 'select') {
    return relationOptionLabel(lookups[field.lookup], value);
  }
  if (field?.kind === 'checkbox') {
    return value ? 'Si' : 'No';
  }
  return String(value ?? '');
}

function relationOptionLabel(items: Record<string, unknown>[], id: unknown) {
  const item = items.find((current) => String(current.id) === String(id));
  if (!item) {
    return id === undefined || id === null ? 'Registro' : `Registro ${id}`;
  }
  return recordName(item);
}

function RelationFieldControl({
  field,
  lookups,
  value,
  onChange,
}: {
  field: RelationField;
  lookups: Record<RelationLookupKey, Record<string, unknown>[]>;
  value: unknown;
  onChange: (value: unknown) => void;
}) {
  if (field.kind === 'checkbox') {
    return (
      <label className="checkbox">
        <input checked={Boolean(value)} onChange={(event) => onChange(event.target.checked)} type="checkbox" />
        {field.label}
      </label>
    );
  }
  if (field.kind === 'select') {
    const options = lookups[field.lookup];
    return (
      <label>
        {field.label}
        <select value={String(value)} onChange={(event) => onChange(event.target.value)} required disabled={!options.length}>
          <option value="">Seleccione...</option>
          {options.map((item) => (
            <option key={String(item.id)} value={String(item.id)}>
              {relationOptionLabel(options, item.id)}
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
        type={field.kind === 'number' ? 'number' : 'text'}
        min={field.kind === 'number' ? field.min : undefined}
        max={field.kind === 'number' ? field.max : undefined}
        required
      />
    </label>
  );
}

const AVAILABILITY_STATES = [
  ['Disponible', 'Preferido', 'No disponible', 'Disponible', 'Disponible'],
  ['No disponible', 'Disponible', 'Disponible', 'Preferido', 'Disponible'],
  ['Disponible', 'Disponible', 'Preferido', 'No disponible', 'Disponible'],
  ['Preferido', 'Disponible', 'Disponible', 'Disponible', 'No disponible'],
  ['Disponible', 'No disponible', 'Disponible', 'Disponible', 'Preferido'],
];

type TeacherAvailabilityItem = {
  journeyId: number | null;
  dayOfWeek: number;
  startBlock: number;
  durationBlocks: number;
  preference: number;
};

type TeacherAvailabilityResponse = {
  teacherId: number;
  items: TeacherAvailabilityItem[];
};

type AvailabilityCell = {
  journeyId: number | null;
  preference: number;
};

function AvailabilityMatrix({
  cells,
  locked,
  onToggle,
}: {
  cells?: AvailabilityCell[][];
  locked: boolean;
  onToggle?: (blockIndex: number, dayIndex: number) => void;
}) {
  const matrix = cells ?? AVAILABILITY_STATES.map((row) => row.map((state) => ({
    journeyId: null,
    preference: preferenceFromLabel(state),
  })));
  return (
    <div className="availability-matrix" aria-label={locked ? 'Matriz de disponibilidad sin edicion' : 'Matriz de disponibilidad'}>
      <div className="matrix-head">Bloque</div>
      {DAYS.slice(0, 5).map((day) => <div className="matrix-head" key={day}>{day}</div>)}
      {matrix.map((row, rowIndex) => (
        <React.Fragment key={rowIndex}>
          <div className="matrix-time">B{rowIndex + 1}</div>
          {row.map((state, colIndex) => (
            <button
              className={`matrix-cell state-${availabilityLabel(state.preference).toLowerCase().replaceAll(' ', '-')}`}
              disabled={locked || !onToggle}
              key={`${rowIndex}-${colIndex}`}
              onClick={() => onToggle?.(rowIndex, colIndex)}
              type="button"
            >
              {availabilityLabel(state.preference)}
            </button>
          ))}
        </React.Fragment>
      ))}
    </div>
  );
}

function availabilityCells(items: TeacherAvailabilityItem[]) {
  const cells: AvailabilityCell[][] = Array.from({ length: 5 }, () =>
    Array.from({ length: 5 }, () => ({ journeyId: null, preference: -1 })),
  );
  for (const item of items) {
    for (let offset = 0; offset < item.durationBlocks; offset += 1) {
      const dayIndex = item.dayOfWeek - 1;
      const blockIndex = item.startBlock + offset;
      if (dayIndex >= 0 && dayIndex < 5 && blockIndex >= 0 && blockIndex < 5) {
        cells[blockIndex][dayIndex] = {
          journeyId: item.journeyId ?? null,
          preference: item.preference > 0 ? 1 : 0,
        };
      }
    }
  }
  return cells;
}

function availabilityPayload(cells: AvailabilityCell[][]) {
  return cells.flatMap((row, blockIndex) =>
    row
      .map((cell, dayIndex) => ({ cell, dayIndex }))
      .filter(({ cell }) => cell.preference >= 0)
      .map(({ cell, dayIndex }) => ({
        journeyId: cell.journeyId,
        dayOfWeek: dayIndex + 1,
        startBlock: blockIndex,
        durationBlocks: 1,
        preference: cell.preference,
      })),
  );
}

function availabilityLabel(preference: number) {
  if (preference < 0) {
    return 'No disponible';
  }
  return preference > 0 ? 'Preferido' : 'Disponible';
}

function nextAvailabilityPreference(preference: number) {
  if (preference < 0) {
    return 0;
  }
  return preference === 0 ? 1 : -1;
}

function preferenceFromLabel(label: string) {
  if (label === 'No disponible') {
    return -1;
  }
  return label === 'Preferido' ? 1 : 0;
}

function countJourneyBlocks(journey: Record<string, unknown>) {
  const timing = journeyTiming(journey);
  if (!timing || timing.blockMinutes <= 0 || timing.duration <= 0) {
    return 'Sin dato';
  }
  return timing.blocks;
}

function journeyTiming(journey: Record<string, unknown>) {
  const start = minutesOfDay(String(journey.startTime ?? ''));
  const end = minutesOfDay(String(journey.endTime ?? ''));
  const blockMinutes = Number(journey.blockMinutes);
  if (start === null || end === null || !Number.isFinite(blockMinutes)) {
    return null;
  }
  const duration = end - start;
  return {
    blockMinutes,
    blocks: blockMinutes > 0 && duration > 0 ? Math.floor(duration / blockMinutes) : 0,
    duration,
    remainder: blockMinutes > 0 && duration > 0 ? duration % blockMinutes : 0,
  };
}

function journeyFormError(journey: Record<string, unknown>) {
  if (!String(journey.name ?? '').trim()) {
    return 'Escribe un nombre para la jornada.';
  }
  const timing = journeyTiming(journey);
  if (!timing || timing.blockMinutes <= 0) {
    return 'Usa minutos por bloque mayores a 0.';
  }
  if (timing.duration <= 0) {
    return 'La hora de fin debe ser posterior al inicio.';
  }
  if (timing.blocks < 1) {
    return 'La jornada debe tener al menos 1 bloque completo.';
  }
  return '';
}

function journeyBlockLabel(journey: Record<string, unknown>) {
  const blocks = countJourneyBlocks(journey);
  return blocks === 'Sin dato' ? blocks : `${blocks} bloques`;
}

function journeyDurationLabel(journey: Record<string, unknown>) {
  const timing = journeyTiming(journey);
  return timing && timing.duration > 0 ? formatDuration(timing.duration) : 'Sin dato';
}

function journeyPreviewDetail(journey: Record<string, unknown>) {
  const validation = journeyFormError({ ...journey, name: String(journey.name ?? '').trim() || 'Vista previa' });
  if (validation) {
    return validation;
  }
  const timing = journeyTiming(journey);
  if (!timing) {
    return 'Sin dato';
  }
  const remainder = timing.remainder ? ` · ${timing.remainder} min sin bloque completo` : '';
  return `${formatTimeRange(journey)} · ${formatDuration(timing.duration)}${remainder}`;
}

function formatTimeRange(journey: Record<string, unknown>) {
  return `${formatClock(journey.startTime)} a ${formatClock(journey.endTime)}`;
}

function formatClock(value: unknown) {
  const [hour = '', minute = ''] = String(value ?? '').split(':');
  if (!hour || !minute) {
    return '--:--';
  }
  return `${hour.padStart(2, '0')}:${minute.padStart(2, '0')}`;
}

function formatDuration(totalMinutes: number) {
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  if (hours && minutes) {
    return `${hours} h ${minutes} min`;
  }
  if (hours) {
    return `${hours} h`;
  }
  return `${minutes} min`;
}

function minutesOfDay(value: string) {
  const [hour, minute] = value.split(':').map(Number);
  if (!Number.isFinite(hour) || !Number.isFinite(minute)) {
    return null;
  }
  return hour * 60 + minute;
}

const IMPORT_SHEETS = [
  { sheet: 'careers', columns: 'code, name, active', fills: 'Carreras base.' },
  { sheet: 'curricula', columns: 'code, career_code, name, effective_from, active', fills: 'Pensums por carrera.' },
  { sheet: 'courses', columns: 'code, name, requires_lab, weekly_blocks_min, weekly_blocks_max', fills: 'Catalogo base de cursos.' },
  { sheet: 'curriculum_courses', columns: 'curriculum_code, course_code, semester', fills: 'Cursos por semestre dentro del pensum.' },
  { sheet: 'cohorts', columns: 'code, career_code, curriculum_code, journey_code, semester', fills: 'Cohortes activas por carrera, pensum y jornada.' },
  { sheet: 'teachers', columns: 'code, full_name, priority, min_courses, max_courses, active', fills: 'Catalogo de docentes.' },
  { sheet: 'teacher_courses', columns: 'teacher_code, course_code', fills: 'Cursos que puede impartir cada docente.' },
  { sheet: 'teacher_availability', columns: 'teacher_code, day_of_week, start_time, end_time, preference', fills: 'Disponibilidad/preferencia docente.' },
  { sheet: 'rooms', columns: 'code, capacity, type, floor, number, active', fills: 'Aulas reales.' },
  { sheet: 'journeys', columns: 'code, name, block_minutes, start_time, end_time', fills: 'Jornadas y duracion de bloques.' },
  { sheet: 'fixed_breaks', columns: 'journey_code, day_of_week, start_time, end_time, reason', fills: 'Descansos fijos.' },
  { sheet: 'common_areas', columns: 'code, name, type, capacity', fills: 'Areas o recursos compartidos.' },
  { sheet: 'common_area_careers', columns: 'common_area_code, career_code', fills: 'Permisos de uso por carrera.' },
];

function ImportWizard({ token }: { token: string }) {
  const [file, setFile] = useState<File | null>(null);
  const [mode, setMode] = useState<'VALIDATE_ONLY' | 'IMPORT'>('VALIDATE_ONLY');
  const [result, setResult] = useState<ImportResponse | null>(null);
  const [errors, setErrors] = useState<PageResponse<ImportErrorRow>>({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
  const [sheetFilter, setSheetFilter] = useState('');
  const [columnFilter, setColumnFilter] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const filteredErrors = errors.items.filter((item) => {
    const matchesSheet = !sheetFilter || item.sheetName === sheetFilter;
    const matchesColumn = !columnFilter || item.columnName.toLowerCase().includes(columnFilter.toLowerCase());
    return matchesSheet && matchesColumn;
  });
  const errorSheets = [...new Set(errors.items.map((item) => item.sheetName).filter(Boolean))].sort();

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
      const response = await importAcademicData(form, token);
      setResult(response);
      if (response.errorCount > 0) {
        const data = await listImportErrors(response.importBatchId, token);
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
          <h2>Hojas requeridas</h2>
          <Badge tone="info">{IMPORT_SHEETS.length} hojas</Badge>
        </div>
        <Table>
          <table>
            <thead>
              <tr>
                <th>Hoja</th>
                <th>Columnas</th>
                <th>Llena</th>
              </tr>
            </thead>
            <tbody>
              {IMPORT_SHEETS.map((item) => (
                <tr key={item.sheet}>
                  <td>{item.sheet}</td>
                  <td>{item.columns}</td>
                  <td>{item.fills}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Table>
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
            <div className="filters">
              <label>
                Hoja con error
                <select value={sheetFilter} onChange={(event) => setSheetFilter(event.target.value)}>
                  <option value="">Todas</option>
                  {errorSheets.map((sheet) => <option key={sheet} value={sheet}>{sheet}</option>)}
                </select>
              </label>
              <label>
                Columna
                <input value={columnFilter} onChange={(event) => setColumnFilter(event.target.value)} placeholder="Filtrar columna" />
              </label>
            </div>
            <Table>
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
                  {filteredErrors.map((item) => (
                    <tr key={item.id}>
                      <td>{item.sheetName}</td>
                      <td>{item.rowNumber}</td>
                      <td>{item.columnName}</td>
                      <td>{item.rawValue}</td>
                      <td>{item.message ?? item.code}</td>
                      <td>{item.suggestedAction ?? ''}</td>
                    </tr>
                  ))}
                  {!filteredErrors.length ? (
                    <tr>
                      <td colSpan={6}>{result.errorCount ? 'Sin errores para filtros actuales' : 'Archivo valido'}</td>
                    </tr>
                  ) : null}
                </tbody>
              </table>
            </Table>
          </>
        ) : (
          <EmptyState title="Sin archivo procesado">
            Sube un CSV o XLSX para ver resumen y errores por hoja, fila y columna.
          </EmptyState>
        )}
      </section>
    </div>
  );
}

function SchedulePlanPage({ token }: { token: string }) {
  const [plans, setPlans] = useState<PageResponse<SchedulePlanSummary>>({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
  const [selectedPlan, setSelectedPlan] = useState<SchedulePlanSummary | null>(null);
  const [draft, setDraft] = useState<SchedulePlanDraft>({
    code: `PLAN-${new Date().toISOString().slice(0, 10)}`,
    name: 'Nuevo plan de horario',
    scheduleType: 'CLASSES',
    startDate: new Date().toISOString().slice(0, 10),
    endDate: new Date(Date.now() + 1000 * 60 * 60 * 24 * 120).toISOString().slice(0, 10),
    config: { defaultBlockMinutes: 45 },
  });
  const [status, setStatus] = useState<PlanStatus>('DRAFT');
  const [runId, setRunId] = useState<number | null>(null);
  const [validation, setValidation] = useState<ValidationResponse | null>(null);
  const [generation, setGeneration] = useState<GenerationResponse | null>(null);
  const [result, setResult] = useState<ScheduleResult | null>(null);
  const [violations, setViolations] = useState<Violation[]>([]);
  const [manualResult, setManualResult] = useState<ManualEditResponse | null>(null);
  const [substitutions, setSubstitutions] = useState<SubstitutionResponse[]>([]);
  const [substitutionDraft, setSubstitutionDraft] = useState<SubstitutionDraft>({
    assignmentId: '',
    substituteTeacherId: '',
    startsAt: new Date().toISOString().slice(0, 16),
    endsAt: '',
    isPermanent: false,
    reason: '',
  });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState('');
  const numericPlanId = selectedPlan?.id ?? 0;
  const hasPlan = Boolean(selectedPlan);

  async function loadPlans(selectId = selectedPlan?.id) {
    setError('');
    setLoading('plans');
    try {
      const data = await listSchedulePlans('page=0&size=20&sort=updatedAt,desc', token);
      setPlans(data);
      const nextPlan = data.items.find((plan) => plan.id === selectId) ?? data.items[0] ?? null;
      setSelectedPlan(nextPlan);
      if (nextPlan) {
        setStatus(nextPlan.status);
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Error cargando planes.');
    } finally {
      setLoading('');
    }
  }

  useEffect(() => {
    void loadPlans();
  }, [token]);

  function selectPlan(plan: SchedulePlanSummary) {
    setSelectedPlan(plan);
    setStatus(plan.status);
    setRunId(null);
    setValidation(null);
    setGeneration(null);
    setResult(null);
    setViolations([]);
    setManualResult(null);
    setSubstitutions([]);
    setError('');
  }

  function updateSelectedStatus(nextStatus: PlanStatus) {
    setStatus(nextStatus);
    setSelectedPlan((current) => current ? { ...current, status: nextStatus } : current);
    setPlans((current) => ({
      ...current,
      items: current.items.map((plan) => plan.id === numericPlanId ? { ...plan, status: nextStatus } : plan),
    }));
  }

  async function runAction<T>(name: string, action: () => Promise<T>) {
    if (!hasPlan) {
      setError('Selecciona un plan de la lista.');
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

  async function createPlan() {
    setError('');
    setLoading('create-plan');
    try {
      const created = await createSchedulePlan(draft, token);
      setDraft((current) => ({
        ...current,
        code: `PLAN-${new Date().toISOString().slice(0, 10)}-${created.id + 1}`,
        name: 'Nuevo plan de horario',
      }));
      await loadPlans(created.id);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Error creando plan.');
    } finally {
      setLoading('');
    }
  }

  async function validatePlan() {
    const response = await runAction('validate', () => validateSchedulePlan(numericPlanId, token));
    if (response) {
      setValidation(response);
      updateSelectedStatus(response.status);
    }
  }

  async function generatePlan() {
    const response = await runAction('generate', () => generateSchedulePlan(numericPlanId, token));
    if (response) {
      setGeneration(response);
      setRunId(response.runId);
      updateSelectedStatus(response.planStatus);
      void loadResult(response.runId);
      void loadViolations(response.runId);
    }
  }

  async function loadResult(nextRunId = runId) {
    const response = await runAction('result', () => getScheduleResult(numericPlanId, nextRunId, token));
    if (response) {
      setResult(response);
      setRunId(response.runId);
      updateSelectedStatus(response.planStatus);
      void loadSubstitutions();
    }
  }

  async function loadSubstitutions() {
    const response = await runAction('substitutions', () => listSubstitutions(numericPlanId, token));
    if (response) {
      setSubstitutions(response.items);
    }
  }

  async function loadViolations(nextRunId = runId) {
    const response = await runAction('violations', () => listScheduleViolations(numericPlanId, nextRunId, token));
    if (response) {
      setViolations(response.items);
    }
  }

  async function approvePlan() {
    const response = await runAction('approve', () => approveSchedulePlan(numericPlanId, runId, token));
    if (response) {
      updateSelectedStatus(response.status);
    }
  }

  async function lockPlan() {
    const response = await runAction('lock', () => lockSchedulePlan(numericPlanId, token));
    if (response) {
      updateSelectedStatus(response.status);
    }
  }

  async function submitManualEdit(draft: ManualDraft) {
    if (!canManualEdit) {
      setError('Edicion manual solo permitida en APPROVED.');
      return;
    }
    if (runId === null) {
      setError('Carga un resultado antes de editar.');
      return;
    }
    const response = await runAction('manual-edit', () => submitManualScheduleEdit(numericPlanId, runId, draft, token));
    if (response) {
      setManualResult(response);
      setRunId(response.resultRunId);
      await loadResult(response.resultRunId);
      await loadViolations(response.resultRunId);
    }
  }

  async function createSubstitution(event: FormEvent) {
    event.preventDefault();
    if (!canCreateSubstitution) {
      setError('Sustituciones requieren plan LOCKED.');
      return;
    }
    const response = await runAction('substitution', () => postSubstitution(substitutionDraft, token));
    if (response) {
      setSubstitutions((current) => [response, ...current]);
      await loadResult();
    }
  }

  const canValidate = ['DRAFT', 'INVALID_INPUT', 'GENERATED', 'GENERATED_WITH_CONFLICTS'].includes(status);
  const canGenerate = canValidate && !validation?.hasBlockingErrors;
  const canApprove = ['GENERATED', 'GENERATED_WITH_CONFLICTS'].includes(status) && runId !== null;
  const canLock = status === 'APPROVED';
  const canManualEdit = status === 'APPROVED';
  const canCreateSubstitution = status === 'LOCKED';
  const resultScore = result?.score ?? generation?.score;
  const actionReasons = {
    validate: actionReason('Validar', hasPlan, canValidate, loading, status),
    generate: actionReason('Generar', hasPlan, canGenerate, loading, status, validation?.hasBlockingErrors),
    approve: actionReason('Aprobar', hasPlan, canApprove, loading, status, false, runId),
    lock: actionReason('Bloquear', hasPlan, canLock, loading, status),
  };

  return (
    <div className="plans-page">
      <section className="form-panel">
        <p className="eyebrow">Plan de horario</p>
        <h2>Crear plan</h2>
        <label>
          Nombre
          <input value={draft.name} onChange={(event) => setDraft((current) => ({ ...current, name: event.target.value }))} required />
        </label>
        <label>
          Tipo
          <select value={draft.scheduleType} onChange={(event) => setDraft((current) => ({ ...current, scheduleType: event.target.value }))}>
            <option value="CLASSES">CLASSES</option>
          </select>
        </label>
        <label>
          Inicio
          <input type="date" value={draft.startDate} onChange={(event) => setDraft((current) => ({ ...current, startDate: event.target.value }))} required />
        </label>
        <label>
          Fin
          <input type="date" value={draft.endDate} onChange={(event) => setDraft((current) => ({ ...current, endDate: event.target.value }))} required />
        </label>
        <button type="button" onClick={createPlan} disabled={loading !== ''}>
          <Plus aria-hidden="true" size={16} /> {loading === 'create-plan' ? 'Creando...' : 'Crear plan'}
        </button>
        <button className="ghost" type="button" onClick={() => void loadPlans()} disabled={loading !== ''}>
          Actualizar lista
        </button>
        {error ? <ErrorBox message={error} /> : null}
        <div className="selected-plan-detail">
          <span>Plan seleccionado</span>
          <strong>{selectedPlan ? selectedPlan.name : 'Ninguno'}</strong>
          {selectedPlan ? <StatusBadge status={status} /> : null}
        </div>
      </section>
      <section className="catalog-main">
        <div className="section-title">
          <h2>Planes</h2>
          <span className="muted">{plans.totalItems} registros</span>
        </div>
        {plans.items.length ? (
          <Table>
            <table>
              <thead>
                <tr>
                  <th>Nombre</th>
                  <th>Tipo</th>
                  <th>Estado</th>
                  <th>Fechas</th>
                  <th>Actualizado</th>
                  <th>Accion</th>
                </tr>
              </thead>
              <tbody>
                {plans.items.map((plan) => (
                  <tr key={plan.id} className={selectedPlan?.id === plan.id ? 'selected-row' : ''}>
                    <td>{plan.name}</td>
                    <td>{plan.scheduleType}</td>
                    <td><StatusBadge status={plan.status} /></td>
                    <td>{plan.startDate} - {plan.endDate}</td>
                    <td>{new Date(plan.updatedAt).toLocaleString()}</td>
                    <td>
                      <button className="ghost" type="button" onClick={() => selectPlan(plan)}>
                        Seleccionar
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Table>
        ) : (
          <EmptyState title={loading === 'plans' ? 'Cargando planes' : 'Sin planes'}>
            {loading === 'plans' ? 'Consultando /api/schedule-plans.' : 'Crea un plan para validar y generar horarios.'}
          </EmptyState>
        )}
        <div className="section-title">
          <h2>Operar seleccionado</h2>
          {selectedPlan ? <StatusBadge status={status} /> : null}
        </div>
        <div className="action-grid">
          <PlanActionButton label={loading === 'validate' ? 'Validando...' : 'Validar'} reason={actionReasons.validate} onClick={validatePlan} />
          <PlanActionButton label={loading === 'generate' ? 'Generando...' : 'Generar'} reason={actionReasons.generate} onClick={generatePlan} />
          <PlanActionButton label="Aprobar" reason={actionReasons.approve} onClick={approvePlan} />
          <PlanActionButton label="Bloquear" reason={actionReasons.lock} onClick={lockPlan} />
        </div>
        <button className="ghost" type="button" onClick={() => { void loadResult(); void loadViolations(); }} disabled={!hasPlan || loading !== ''}>
          Cargar resultado
        </button>
        <div className="section-title">
          <h2>Resultado</h2>
          {runId ? <span className="muted">Run {runId}</span> : null}
        </div>
        <div className="summary-grid">
          <Metric label="Score" value={resultScore?.total ?? '-'} />
          <Metric label="Asignadas" value={result?.assignments.length ?? generation?.assignedCount ?? 0} />
          <Metric label="Sin asignar" value={result?.unassigned.length ?? generation?.unassignedCount ?? 0} />
          <Metric label="Motor" value={generation?.engineVersion ?? '-'} />
          <Metric label="Seed" value={generation?.seed ?? '-'} />
        </div>
        {resultScore ? <ScoreBreakdown score={resultScore} /> : null}
        {manualResult ? <ManualEditSummary result={manualResult} /> : null}
        {result ? (
          <ScheduleGrid
            assignments={result.assignments}
            canEdit={canManualEdit}
            loading={loading === 'manual-edit'}
            onEdit={submitManualEdit}
          />
        ) : (
          <EmptyState title="Sin resultado cargado">Genera o carga el resultado del plan para ver la grilla.</EmptyState>
        )}
        {result ? (
          <SubstitutionPanel
            assignments={result.assignments}
            canCreate={canCreateSubstitution}
            draft={substitutionDraft}
            items={substitutions}
            loading={loading === 'substitution'}
            onChange={setSubstitutionDraft}
            onSubmit={createSubstitution}
          />
        ) : null}
      </section>
      <ConflictPanel validation={validation?.issues ?? []} violations={violations} unassigned={result?.unassigned ?? []} />
    </div>
  );
}

const DAYS = ['Lun', 'Mar', 'Mie', 'Jue', 'Vie', 'Sab'];

function PlanActionButton({
  label,
  reason,
  onClick,
}: {
  label: string;
  reason: string;
  onClick: () => void;
}) {
  const blocked = Boolean(reason);
  return (
    <div className="plan-action">
      <ActionButton type="button" onClick={onClick} disabled={blocked} title={reason || undefined}>
        {label}
      </ActionButton>
      {reason ? <small>{reason}</small> : <small>Disponible</small>}
    </div>
  );
}

function actionReason(
  action: string,
  hasPlan: boolean,
  allowed: boolean,
  loading: string,
  status: PlanStatus,
  hasBlockingErrors = false,
  runId: number | null = 0,
) {
  if (!hasPlan) {
    return 'Selecciona un plan.';
  }
  if (loading) {
    return 'Otra accion esta en curso.';
  }
  if (hasBlockingErrors) {
    return 'Validacion tiene errores bloqueantes.';
  }
  if (runId === null) {
    return 'Carga o genera un resultado con runId.';
  }
  if (!allowed) {
    return `${action} no permitido desde ${status}.`;
  }
  return '';
}

function ScoreBreakdown({ score }: { score: Record<string, number> }) {
  const entries = Object.entries(score).filter(([key]) => key !== 'total');
  if (!entries.length) {
    return null;
  }
  return (
    <div className="score-breakdown" aria-label="Detalle de score">
      {entries.map(([key, value]) => (
        <span key={key}>{key}: <strong>{value}</strong></span>
      ))}
    </div>
  );
}

function ScheduleGrid({
  assignments,
  canEdit,
  loading,
  onEdit,
}: {
  assignments: Assignment[];
  canEdit: boolean;
  loading: boolean;
  onEdit: (draft: ManualDraft) => Promise<void>;
}) {
  const [view, setView] = useState<GridView>('cohort');
  const [filter, setFilter] = useState('all');
  const [draft, setDraft] = useState<ManualDraft | null>(null);
  const [activeAssignment, setActiveAssignment] = useState<Assignment | null>(null);
  const options = useMemo(() => gridOptions(assignments, view), [assignments, view]);
  const visibleAssignments = useMemo(
    () => assignments.filter((assignment) => matchesGridFilter(assignment, view, filter)),
    [assignments, filter, view],
  );
  const maxBlock = Math.max(7, ...assignments.map((assignment) => assignment.startBlock + (assignment.durationBlocks ?? 1) - 1));
  const blocks = Array.from({ length: maxBlock + 1 }, (_, index) => index);

  function openDraft(assignment: Assignment, targetDay = assignment.dayOfWeek, targetStartBlock = assignment.startBlock) {
    if (!canEdit || assignment.pinned) {
      return;
    }
    setDraft({
      assignment,
      targetDay,
      targetStartBlock,
      targetTimeBlockId: String(toTimeBlockId(targetDay, targetStartBlock)),
      targetTeacherId: assignment.teacherId === null ? '' : String(assignment.teacherId),
      targetRoomId: assignment.roomId === null ? '' : String(assignment.roomId),
    });
  }

  return (
    <section className="schedule-area">
      <div className="section-title">
        <h2>Grilla semanal</h2>
        <StatusBadge status={canEdit ? 'APPROVED' : 'READ_ONLY'} />
      </div>
      <div className="filters">
        <label>
          Vista
          <select value={view} onChange={(event) => { setView(event.target.value as GridView); setFilter('all'); }}>
            <option value="cohort">Cohorte</option>
            <option value="teacher">Docente</option>
            <option value="room">Aula</option>
          </select>
        </label>
        <label>
          Filtro
          <select value={filter} onChange={(event) => setFilter(event.target.value)}>
            <option value="all">Todos</option>
            {options.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
        </label>
      </div>
      {canEdit ? (
        <DndScheduleGrid
          assignments={assignments}
          blocks={blocks}
          visibleAssignments={visibleAssignments}
          activeAssignment={activeAssignment}
          onActiveAssignment={setActiveAssignment}
          onOpen={openDraft}
        />
      ) : (
        <ScheduleGridBody
          canEdit={false}
          blocks={blocks}
          visibleAssignments={visibleAssignments}
          onOpen={openDraft}
        />
      )}
      {draft ? (
        <ManualEditDrawer
          draft={draft}
          loading={loading}
          onChange={setDraft}
          onClose={() => setDraft(null)}
          onSubmit={async () => {
            await onEdit(draft);
            setDraft(null);
          }}
        />
      ) : null}
    </section>
  );
}

function DndScheduleGrid({
  assignments,
  blocks,
  visibleAssignments,
  activeAssignment,
  onActiveAssignment,
  onOpen,
}: {
  assignments: Assignment[];
  blocks: number[];
  visibleAssignments: Assignment[];
  activeAssignment: Assignment | null;
  onActiveAssignment: (assignment: Assignment | null) => void;
  onOpen: (assignment: Assignment, targetDay?: number, targetStartBlock?: number) => void;
}) {
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 6 } }));

  function moveFromDrag(event: DragEndEvent) {
    const assignment = assignments.find((item) => `session-${item.sessionId}` === event.active.id);
    onActiveAssignment(null);
    if (!assignment || !event.over) {
      return;
    }
    const [, day, block] = String(event.over.id).split(':');
    onOpen(assignment, Number(day), Number(block));
  }

  return (
    <DndContext
      sensors={sensors}
      onDragStart={(event) => onActiveAssignment(assignments.find((item) => `session-${item.sessionId}` === event.active.id) ?? null)}
      onDragEnd={moveFromDrag}
      onDragCancel={() => onActiveAssignment(null)}
    >
      <ScheduleGridBody canEdit blocks={blocks} visibleAssignments={visibleAssignments} onOpen={onOpen} />
      <DragOverlay>{activeAssignment ? <SessionCard assignment={activeAssignment} /> : null}</DragOverlay>
    </DndContext>
  );
}

function ScheduleGridBody({
  blocks,
  canEdit,
  visibleAssignments,
  onOpen,
}: {
  blocks: number[];
  canEdit: boolean;
  visibleAssignments: Assignment[];
  onOpen: (assignment: Assignment) => void;
}) {
  return (
    <div className="schedule-grid" style={{ gridTemplateColumns: `84px repeat(${DAYS.length}, minmax(160px, 1fr))` }}>
      <div className="grid-head">Bloque</div>
      {DAYS.map((day) => <div className="grid-head" key={day}>{day}</div>)}
      {blocks.map((block) => (
        <React.Fragment key={block}>
          <div className="grid-time">B{block + 1}</div>
          {DAYS.map((_, index) => {
            const day = index + 1;
            const cellAssignments = visibleAssignments.filter(
              (assignment) => assignment.dayOfWeek === day && assignment.startBlock === block,
            );
            return (
              <GridCell canDrop={canEdit} day={day} block={block} key={`${day}-${block}`}>
                {cellAssignments.map((assignment) => (
                  canEdit ? (
                    <SessionBlock
                      assignment={assignment}
                      canEdit={!assignment.pinned}
                      key={assignment.id}
                      onOpen={() => onOpen(assignment)}
                    />
                  ) : (
                    <StaticSessionBlock assignment={assignment} key={assignment.id} />
                  )
                ))}
              </GridCell>
            );
          })}
        </React.Fragment>
      ))}
    </div>
  );
}

function GridCell({ canDrop, day, block, children }: { canDrop: boolean; day: number; block: number; children: React.ReactNode }) {
  if (!canDrop) {
    return <div className="grid-cell">{children}</div>;
  }
  return <DroppableGridCell day={day} block={block}>{children}</DroppableGridCell>;
}

function DroppableGridCell({ day, block, children }: { day: number; block: number; children: React.ReactNode }) {
  const { isOver, setNodeRef } = useDroppable({ id: `cell:${day}:${block}` });
  return (
    <div className={`grid-cell ${isOver ? 'over' : ''}`} ref={setNodeRef}>
      {children}
    </div>
  );
}

function StaticSessionBlock({ assignment }: { assignment: Assignment }) {
  return (
    <div className={`session-block read-only ${assignment.pinned ? 'pinned' : ''}`}>
      <SessionCard assignment={assignment} />
    </div>
  );
}

function SessionBlock({
  assignment,
  canEdit,
  onOpen,
}: {
  assignment: Assignment;
  canEdit: boolean;
  onOpen: () => void;
}) {
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({
    id: `session-${assignment.sessionId}`,
    disabled: !canEdit,
  });
  const style = { transform: CSS.Translate.toString(transform) };
  return (
    <button
      className={`session-block ${assignment.pinned ? 'pinned' : ''} ${isDragging ? 'dragging' : ''}`}
      onClick={onOpen}
      ref={setNodeRef}
      style={style}
      type="button"
      {...listeners}
      {...attributes}
    >
      <SessionCard assignment={assignment} />
    </button>
  );
}

function SessionCard({ assignment }: { assignment: Assignment }) {
  const roomCode = publicText(assignment.roomCode);
  const roomLabel = roomCode ? `Aula ${roomCode}` : assignment.roomCode ? 'Aula asignada' : 'Aula sin asignar';
  return (
    <>
      <strong>{courseName(assignment)}</strong>
      <span>{assignment.teacherName ?? 'Docente sin asignar'}</span>
      <small>{roomLabel}</small>
      <div className="session-badges">
        {roomCode ? <em>Aula {roomCode}</em> : null}
        {assignment.teacherName ? <em>{assignment.teacherName}</em> : null}
        {assignment.cohortIds.length ? <em>Cohorte {assignment.cohortIds.join(',')}</em> : null}
        <em>{DAYS[assignment.dayOfWeek - 1] ?? `Dia ${assignment.dayOfWeek}`} B{assignment.startBlock + 1}</em>
        {assignment.durationBlocks ? <em>{assignment.durationBlocks} bloques</em> : null}
        {assignment.pinned ? <em>Fijada</em> : null}
      </div>
    </>
  );
}

function ManualEditDrawer({
  draft,
  loading,
  onChange,
  onClose,
  onSubmit,
}: {
  draft: ManualDraft;
  loading: boolean;
  onChange: (draft: ManualDraft) => void;
  onClose: () => void;
  onSubmit: () => void;
}) {
  return (
    <aside className="drawer" aria-label="Edicion manual">
      <div className="section-title">
        <h2>Edicion manual</h2>
        <button className="ghost icon-button" onClick={onClose} type="button" aria-label="Cerrar edicion manual">x</button>
      </div>
      <p className="muted">{courseName(draft.assignment)}</p>
      <label>
        Dia destino
        <select
          value={draft.targetDay}
          onChange={(event) => {
            const targetDay = Number(event.target.value);
            onChange({ ...draft, targetDay, targetTimeBlockId: String(toTimeBlockId(targetDay, draft.targetStartBlock)) });
          }}
        >
          {DAYS.map((day, index) => <option key={day} value={index + 1}>{day}</option>)}
        </select>
      </label>
      <label>
        Bloque destino
        <input
          min="0"
          type="number"
          value={draft.targetStartBlock}
          onChange={(event) => {
            const targetStartBlock = Number(event.target.value);
            onChange({ ...draft, targetStartBlock, targetTimeBlockId: String(toTimeBlockId(draft.targetDay, targetStartBlock)) });
          }}
        />
      </label>
      <label>
        ID bloque tiempo
        <input value={draft.targetTimeBlockId} onChange={(event) => onChange({ ...draft, targetTimeBlockId: event.target.value })} type="number" min="1" />
      </label>
      <label>
        Docente destino
        <input value={draft.targetTeacherId} onChange={(event) => onChange({ ...draft, targetTeacherId: event.target.value })} type="number" min="1" />
      </label>
      <label>
        Aula destino
        <input value={draft.targetRoomId} onChange={(event) => onChange({ ...draft, targetRoomId: event.target.value })} type="number" min="1" />
      </label>
      <button type="button" onClick={onSubmit} disabled={loading || draft.assignment.pinned || !draft.targetTimeBlockId}>
        {loading ? 'Aplicando...' : 'Aplicar LNS'}
      </button>
    </aside>
  );
}

function ReportsPage({ token }: { token: string }) {
  const [plans, setPlans] = useState<SchedulePlanSummary[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState('');

  async function loadReports() {
    setError('');
    setLoading('plans');
    try {
      const data = await listSchedulePlans('page=0&size=30&sort=updatedAt,desc', token);
      setPlans(data.items);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Error cargando planes.');
    } finally {
      setLoading('');
    }
  }

  useEffect(() => {
    void loadReports();
  }, [token]);

  async function downloadReport(planId: number, format: 'pdf' | 'xlsx') {
    setError('');
    setLoading(`${planId}-${format}`);
    try {
      const response = await fetch(apiUrl(`/api/reports/schedule-plans/${planId}.${format}`), {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `schedule-plan-${planId}.${format}`;
      link.click();
      URL.revokeObjectURL(url);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Error descargando reporte.');
    } finally {
      setLoading('');
    }
  }

  return (
    <section className="reports-page">
      <div className="section-title">
        <div>
          <p className="eyebrow">Reportes</p>
          <h2>Descargas PDF/XLSX</h2>
        </div>
        <button className="ghost" type="button" onClick={loadReports} disabled={loading !== ''}>
          Actualizar
        </button>
      </div>
      {error ? <ErrorBox message={error} /> : null}
      <Table>
        <table>
          <thead>
            <tr>
              <th>Nombre</th>
              <th>Estado</th>
              <th>Actualizado</th>
              <th>Descargas</th>
            </tr>
          </thead>
          <tbody>
            {plans.map((plan) => (
              <tr key={plan.id}>
                <td>{plan.name}</td>
                <td><StatusBadge status={plan.status} /></td>
                <td>{new Date(plan.updatedAt).toLocaleString()}</td>
                <td>
                  {plan.status !== 'LOCKED' ? <small className="muted">Requiere LOCKED</small> : null}
                  <div className="row-actions">
                    <button
                      className="ghost"
                      type="button"
                      onClick={() => void downloadReport(plan.id, 'pdf')}
                      disabled={loading !== '' || plan.status !== 'LOCKED'}
                      title={plan.status === 'LOCKED' ? undefined : 'Reportes disponibles solo para planes LOCKED.'}
                    >
                      <Download aria-hidden="true" size={16} /> PDF
                    </button>
                    <button
                      className="ghost"
                      type="button"
                      onClick={() => void downloadReport(plan.id, 'xlsx')}
                      disabled={loading !== '' || plan.status !== 'LOCKED'}
                      title={plan.status === 'LOCKED' ? undefined : 'Reportes disponibles solo para planes LOCKED.'}
                    >
                      <Download aria-hidden="true" size={16} /> XLSX
                    </button>
                  </div>
                </td>
              </tr>
            ))}
            {!plans.length ? (
              <tr>
                <td colSpan={4}>{loading === 'plans' ? 'Cargando...' : 'Sin planes para reportar'}</td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </Table>
    </section>
  );
}

function ConflictPanel({
  validation,
  violations,
  unassigned,
}: {
  validation: ValidationIssue[];
  violations: Violation[];
  unassigned: UnassignedSession[];
}) {
  const hard = [...validation.filter((item) => item.severity === 'ERROR'), ...violations.filter((item) => item.severity === 'ERROR')];
  const soft = [...validation.filter((item) => item.severity !== 'ERROR'), ...violations.filter((item) => item.severity !== 'ERROR')];
  const suggestions = validation.filter((item) => item.suggestedAction);
  return (
    <aside className="conflict-panel">
      <h2>Conflictos</h2>
      <ConflictList title="Duros" tone="danger" items={hard} />
      <ConflictList title="Blandos" tone="warning" items={soft} />
      <div className="conflict-list suggestion">
        <h3>Sugerencias</h3>
        {suggestions.map((item) => (
          <p key={`suggestion-${item.code}-${item.id ?? item.entityId}`}>
            <strong>{item.code}</strong> {item.suggestedAction}
          </p>
        ))}
        {!suggestions.length ? <p>Sin sugerencias de API</p> : null}
      </div>
      <div className="conflict-list suggestion">
        <h3>Sin asignar</h3>
        {unassigned.map((item) => <p key={item.sessionId}>{unassignedName(item)} · {item.reason}</p>)}
        {!unassigned.length ? <p>Sin sesiones no asignadas</p> : null}
      </div>
    </aside>
  );
}

function ConflictList({ title, tone, items }: { title: string; tone: 'danger' | 'warning'; items: (ValidationIssue | Violation)[] }) {
  return (
    <div className={`conflict-list ${tone}`}>
      <h3>{title}</h3>
      {items.map((item) => (
        <p key={`${item.code}-${item.id}`}>
          <strong>{item.code}</strong> {item.message}
          <small>{conflictEntity(item)}</small>
        </p>
      ))}
      {!items.length ? <p>Sin registros</p> : null}
    </div>
  );
}

function conflictEntity(item: ValidationIssue | Violation) {
  if ('entityType' in item && item.entityType) {
    return `${item.entityType} ${item.entityId ?? '-'}`;
  }
  const affected = 'affectedEntities' in item
    ? item.affectedEntities?.map((entity) => `${entity.type ?? 'entity'} ${entity.id ?? '-'}`).join(', ')
    : '';
  return affected ? `Afecta: ${affected}` : 'Entidad no informada';
}

function ManualEditSummary({ result }: { result: ManualEditResponse }) {
  return (
    <div className="lns-result">
      <Metric label="LNS" value={result.status} />
      <Metric label="Movidas" value={result.movedSessionIds.length} />
      <Metric label="Fijadas" value={result.pinnedSessionIds.length} />
      <Metric label="Restantes" value={result.remainingViolations.length} />
    </div>
  );
}

function SubstitutionPanel({
  assignments,
  canCreate,
  draft,
  items,
  loading,
  onChange,
  onSubmit,
}: {
  assignments: Assignment[];
  canCreate: boolean;
  draft: SubstitutionDraft;
  items: SubstitutionResponse[];
  loading: boolean;
  onChange: (draft: SubstitutionDraft) => void;
  onSubmit: (event: FormEvent) => void;
}) {
  return (
    <section className="substitution-panel">
      <div className="section-title">
        <h2>Sustituciones</h2>
        <span className="muted">{canCreate ? `${items.length} vigentes/historicas` : 'Requiere plan LOCKED'}</span>
      </div>
      {!canCreate ? (
        <Notice title="Bloqueado por estado">Las sustituciones se registran solo cuando el plan esta LOCKED.</Notice>
      ) : null}
      <form className="substitution-form" onSubmit={onSubmit}>
        <label>
          Clase base
          <select
            disabled={!canCreate}
            required
            value={draft.assignmentId}
            onChange={(event) => onChange({ ...draft, assignmentId: event.target.value })}
          >
            <option value="">Selecciona clase</option>
            {assignments.map((assignment) => (
              <option key={assignment.id} value={assignment.id}>
                {courseName(assignment)} · {assignment.teacherName ?? 'Docente sin asignar'} · {DAYS[assignment.dayOfWeek - 1]} B{assignment.startBlock + 1}
              </option>
            ))}
          </select>
        </label>
        <label>
          ID docente sustituto
          <input
            min="1"
            disabled={!canCreate}
            required
            type="number"
            value={draft.substituteTeacherId}
            onChange={(event) => onChange({ ...draft, substituteTeacherId: event.target.value })}
          />
        </label>
        <label>
          Inicio
          <input
            disabled={!canCreate}
            required
            type="datetime-local"
            value={draft.startsAt}
            onChange={(event) => onChange({ ...draft, startsAt: event.target.value })}
          />
        </label>
        <label>
          Fin
          <input
            disabled={!canCreate}
            type="datetime-local"
            value={draft.endsAt}
            onChange={(event) => onChange({ ...draft, endsAt: event.target.value })}
          />
        </label>
        <label className="checkbox">
          <input
            checked={draft.isPermanent}
            disabled={!canCreate}
            onChange={(event) => onChange({ ...draft, isPermanent: event.target.checked })}
            type="checkbox"
          />
          Permanente
        </label>
        <label>
          Motivo
          <input disabled={!canCreate} value={draft.reason} onChange={(event) => onChange({ ...draft, reason: event.target.value })} />
        </label>
        <button
          type="submit"
          disabled={!canCreate || loading || !draft.assignmentId || !draft.substituteTeacherId}
          title={canCreate ? undefined : 'Sustituciones requieren plan LOCKED.'}
        >
          {loading ? 'Guardando...' : 'Crear sustitucion'}
        </button>
      </form>
      <Table>
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Clase</th>
              <th>Original</th>
              <th>Sustituto</th>
              <th>Inicio</th>
              <th>Fin</th>
            </tr>
          </thead>
          <tbody>
            {items.map((item) => (
              <tr key={item.id}>
                <td>{item.id}</td>
                <td>{item.assignmentId}</td>
                <td>{item.originalTeacherId}</td>
                <td>{item.substituteTeacherId}</td>
                <td>{new Date(item.startsAt).toLocaleString()}</td>
                <td>{item.endsAt ? new Date(item.endsAt).toLocaleString() : 'Sin fin'}</td>
              </tr>
            ))}
            {!items.length ? (
              <tr>
                <td colSpan={6}>Sin sustituciones</td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </Table>
    </section>
  );
}

function gridOptions(assignments: Assignment[], view: GridView) {
  const map = new Map<string, string>();
  for (const assignment of assignments) {
    if (view === 'teacher') {
      if (assignment.teacherId !== null) {
        map.set(String(assignment.teacherId), assignment.teacherName ?? `Docente ${assignment.teacherId}`);
      }
    } else if (view === 'room') {
      if (assignment.roomId !== null) {
        const roomCode = publicText(assignment.roomCode);
        map.set(String(assignment.roomId), roomCode ? `Aula ${roomCode}` : `Aula ${assignment.roomId}`);
      }
    } else {
      for (const cohortId of assignment.cohortIds) {
        map.set(String(cohortId), `Cohorte ${cohortId}`);
      }
    }
  }
  return [...map.entries()].map(([value, label]) => ({ value, label }));
}

function matchesGridFilter(assignment: Assignment, view: GridView, filter: string) {
  if (filter === 'all') {
    return true;
  }
  if (view === 'teacher') {
    return assignment.teacherId !== null && String(assignment.teacherId) === filter;
  }
  if (view === 'room') {
    return assignment.roomId !== null && String(assignment.roomId) === filter;
  }
  return assignment.cohortIds.map(String).includes(filter);
}

function toTimeBlockId(day: number, startBlock: number) {
  return day * 1000 + startBlock;
}

function Metric({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function Notice({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="data-notice">
      <strong>{title}</strong>
      <span>{children}</span>
    </div>
  );
}

function CatalogMiniTable({ catalog, token }: { catalog: Catalog; token: string }) {
  const [page, setPage] = useState<PageResponse>({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const sortColumn = catalog.columns.includes('code') ? 'code' : catalog.columns[0];
  const columns = userColumns(catalog.columns);

  useEffect(() => {
    let ignore = false;
    async function load() {
      setError('');
      setLoading(true);
      try {
        const data = await listCatalog(catalog.resource, `page=0&size=20&sort=${sortColumn},asc`, token);
        if (!ignore) {
          setPage(data);
        }
      } catch (caught) {
        if (!ignore) {
          setError(caught instanceof Error ? caught.message : 'Error cargando catalogo.');
        }
      } finally {
        if (!ignore) {
          setLoading(false);
        }
      }
    }
    void load();
    return () => {
      ignore = true;
    };
  }, [catalog.resource, sortColumn, token]);

  return (
    <article className="catalog-mini">
      <div className="section-title">
        <h2>{catalog.title}</h2>
        <Badge tone={error ? 'warning' : 'info'}>{error ? 'Sin datos' : `${page.totalItems} registros`}</Badge>
      </div>
      {error ? <Notice title="No cargado">{error}</Notice> : null}
      <Table flat>
        <table>
          <thead>
            <tr>
              {columns.map((column) => (
                <th key={column}>{column}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {page.items.map((item, index) => (
              <tr key={String(item.id ?? index)}>
                {columns.map((column) => (
                  <td key={column}>{String(item[column] ?? '')}</td>
                ))}
              </tr>
            ))}
            {!page.items.length ? (
              <tr>
                <td colSpan={Math.max(columns.length, 1)}>{loading ? 'Cargando...' : 'Sin registros'}</td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </Table>
    </article>
  );
}

function CatalogPage({ catalog, token }: { catalog: Catalog; token: string }) {
  const [page, setPage] = useState<PageResponse>({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
  const [values, setValues] = useState(() => defaultValues(catalog));
  const [sameWeeklyBlocks, setSameWeeklyBlocks] = useState(true);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const isCourseCatalog = catalog.resource === 'courses';
  const columns = userColumns(catalog.columns);

  async function load() {
    setError('');
    setLoading(true);
    try {
      const data = await listCatalog(catalog.resource, 'page=0&size=20&sort=code,asc', token);
      setPage(data);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Error cargando catalogo.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    setValues(defaultValues(catalog));
    setSameWeeklyBlocks(true);
    void load();
  }, [catalog.resource]);

  async function create(event: FormEvent) {
    event.preventDefault();
    setError('');
    try {
      await createCatalogItem(catalog.resource, normalizePayload(catalog, values), token);
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
        <Table>
          <table>
            <thead>
              <tr>
                {columns.map((column) => (
                  <th key={column}>{column}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {page.items.map((item, index) => (
                <tr key={String(item.id ?? index)}>
                  {columns.map((column) => (
                    <td key={column}>{String(item[column] ?? '')}</td>
                  ))}
                </tr>
              ))}
              {!page.items.length ? (
                <tr>
                  <td colSpan={Math.max(columns.length, 1)}>{loading ? 'Cargando...' : 'Sin registros'}</td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </Table>
        <p className="muted">
          {page.totalItems} registros · pagina {page.page + 1} de {Math.max(page.totalPages, 1)}
        </p>
      </section>
      <form className="form-panel" onSubmit={create}>
        <h2>Crear</h2>
        {isCourseCatalog ? (
          <label className="checkbox switch-row">
            <input
              checked={sameWeeklyBlocks}
              onChange={(event) => setSameWeeklyBlocks(event.target.checked)}
              type="checkbox"
            />
            Usar cantidad unica de bloques
          </label>
        ) : null}
        {catalog.fields.map((field) => {
          if (isCourseCatalog && sameWeeklyBlocks && field.name === 'weeklyBlocksMax') {
            return null;
          }
          return (
            <CatalogField
              field={
                isCourseCatalog && sameWeeklyBlocks && field.name === 'weeklyBlocksMin'
                  ? { ...field, label: 'Cantidad de bloques' }
                  : field
              }
              key={field.name}
              value={values[field.name]}
              onChange={(value) => setValues((current) => ({
                ...current,
                [field.name]: value,
                ...(isCourseCatalog && sameWeeklyBlocks && field.name === 'weeklyBlocksMin'
                  ? { weeklyBlocksMax: value }
                  : {}),
              }))}
            />
          );
        })}
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
