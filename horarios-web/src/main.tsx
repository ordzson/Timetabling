import React, { FormEvent, useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { CSS } from '@dnd-kit/utilities';
import {
  BookOpen,
  CalendarDays,
  Clock,
  FileSpreadsheet,
  GraduationCap,
  LayoutDashboard,
  LogOut,
  MapPinned,
  PanelLeft,
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
import './styles.css';
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
  SchedulePlanSummary,
  ScheduleResult,
  SubstitutionDraft,
  SubstitutionResponse,
  ValidationIssue,
  ValidationResponse,
  Violation,
} from './types/schedule';
import { api } from './api/client';
import { createCatalogItem, listCatalog } from './api/catalogs';
import { importAcademicData, listImportErrors } from './api/imports';
import {
  approveSchedulePlan,
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
import { ApiPending } from './components/ApiPending';
import { Badge } from './components/Badge';
import { EmptyState } from './components/EmptyState';
import { StatusBadge } from './components/StatusBadge';
import { Table } from './components/Table';

const SESSION_KEY = 'horarios.session';

const CATALOGS: Catalog[] = [
  {
    resource: 'careers',
    title: 'Carreras',
    fields: [
      { name: 'name', label: 'Nombre', type: 'text', required: true },
      { name: 'active', label: 'Activa', type: 'checkbox' },
    ],
    columns: ['code', 'name', 'active'],
  },
  {
    resource: 'courses',
    title: 'Cursos',
    fields: [
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
      { name: 'name', label: 'Nombre', type: 'text', required: true },
      { name: 'blockMinutes', label: 'Minutos bloque', type: 'number', required: true },
      { name: 'startTime', label: 'Inicio', type: 'time', required: true },
      { name: 'endTime', label: 'Fin', type: 'time', required: true },
    ],
    columns: ['code', 'name', 'blockMinutes', 'startTime', 'endTime'],
  },
];

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
  payload.code = crypto.randomUUID();
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
          <CatalogPage catalog={catalog} token={session.accessToken} />
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
          <EmptyState title="API pendiente">
            Esta vista queda bloqueada hasta conectar endpoints administrativos reales.
          </EmptyState>
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
          <p>{latestPlan ? `${latestPlan.code} · ${latestPlan.startDate} a ${latestPlan.endDate}` : 'No hay un plan real para mostrar todavia.'}</p>
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
                    <th>Codigo</th>
                    <th>Nombre</th>
                    <th>Tipo</th>
                    <th>Estado</th>
                    <th>Rango</th>
                  </tr>
                </thead>
                <tbody>
                  {plans.items.map((plan) => (
                    <tr key={plan.id}>
                      <td>{plan.code}</td>
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
            <h3>Datos faltantes</h3>
            <ApiPending>Resumen global de asignaciones, conflictos y sesiones sin asignar.</ApiPending>
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
  const [careers, setCareers] = useState<PageResponse>({ items: [], page: 0, size: 50, totalItems: 0, totalPages: 0 });
  const [courses, setCourses] = useState<PageResponse>({ items: [], page: 0, size: 50, totalItems: 0, totalPages: 0 });
  const [selectedCareer, setSelectedCareer] = useState('');
  const [pending, setPending] = useState<Record<string, string>>({});
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
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
        ...(curriculaResult.status === 'rejected' ? { curricula: 'GET /api/catalog/curricula no disponible.' } : {}),
        ...(matrixResult.status === 'rejected' ? { curriculumCourses: 'GET /api/catalog/curriculum-courses no disponible.' } : {}),
        ...(cohortResult.status === 'rejected' ? { cohorts: 'GET /api/catalog/cohorts no disponible.' } : {}),
      });
      setLoading(false);
    }
    void loadAcademia();
    return () => {
      ignore = true;
    };
  }, [token]);

  return (
    <div className="academia-page">
      <section className="form-panel">
        <p className="eyebrow">Academia</p>
        <h2>Carrera</h2>
        <label>
          Carrera base
          <select value={selectedCareer} onChange={(event) => setSelectedCareer(event.target.value)} disabled={!careers.items.length}>
            {careers.items.map((career) => (
              <option key={String(career.id ?? career.code)} value={String(career.id ?? career.code)}>
                {String(career.code ?? '')} {String(career.name ?? '')}
              </option>
            ))}
          </select>
        </label>
        <Metric label="Carreras reales" value={loading ? '...' : careers.totalItems} />
        {error ? <ErrorBox message={error} /> : null}
        {!careers.items.length && !loading ? (
          <EmptyState title="Sin carreras">No hay carreras reales desde /api/catalog/careers.</EmptyState>
        ) : null}
      </section>

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
          <AcademicStep title="2. Pensums" value="curricula" state={pending.curricula ? 'API pendiente' : 'Disponible'} pending={pending.curricula} />
          <AcademicStep title="3. Cursos por semestre" value="curriculum-courses" state={pending.curriculumCourses ? 'API pendiente' : 'Disponible'} pending={pending.curriculumCourses} />
          <AcademicStep title="4. Cohortes" value="cohorts" state={pending.cohorts ? 'API pendiente' : 'Disponible'} pending={pending.cohorts} />
        </div>

        <div className="section-title">
          <h2>Catalogo base de cursos</h2>
          <span className="muted">{courses.totalItems} cursos reales</span>
        </div>
        {pending.courses ? <ApiPending>{pending.courses}</ApiPending> : null}
        <Table>
          <table>
            <thead>
              <tr>
                <th>Codigo</th>
                <th>Nombre</th>
                <th>Lab</th>
                <th>Bloques min</th>
                <th>Bloques max</th>
              </tr>
            </thead>
            <tbody>
              {courses.items.map((course, index) => (
                <tr key={String(course.id ?? course.code ?? index)}>
                  <td>{String(course.code ?? '')}</td>
                  <td>{String(course.name ?? '')}</td>
                  <td>{String(course.requiresLab ?? '')}</td>
                  <td>{String(course.weeklyBlocksMin ?? '')}</td>
                  <td>{String(course.weeklyBlocksMax ?? '')}</td>
                </tr>
              ))}
              {!courses.items.length ? (
                <tr>
                  <td colSpan={5}>{loading ? 'Cargando...' : 'Sin cursos reales cargados'}</td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </Table>
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
      {pending ? <ApiPending>{pending}</ApiPending> : null}
    </article>
  );
}

function TeachersPage({ token }: { token: string }) {
  const [teachers, setTeachers] = useState<PageResponse>({ items: [], page: 0, size: 50, totalItems: 0, totalPages: 0 });
  const [careers, setCareers] = useState<PageResponse>({ items: [], page: 0, size: 50, totalItems: 0, totalPages: 0 });
  const [journeys, setJourneys] = useState<PageResponse>({ items: [], page: 0, size: 50, totalItems: 0, totalPages: 0 });
  const [careerFilter, setCareerFilter] = useState('');
  const [journeyFilter, setJourneyFilter] = useState('');
  const [pending, setPending] = useState<Record<string, string>>({});
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let ignore = false;
    async function loadTeachers() {
      setError('');
      setPending({});
      setLoading(true);
      const [teacherResult, careerResult, journeyResult, careerJourneyResult, coursesResult, availabilityResult] = await Promise.allSettled([
        listCatalog('teachers', 'page=0&size=100&sort=code,asc', token),
        listCatalog('careers', 'page=0&size=100&sort=code,asc', token),
        listCatalog('journeys', 'page=0&size=100&sort=code,asc', token),
        listCatalog('teacher-career-journeys', 'page=0&size=20', token),
        listCatalog('teacher-courses', 'page=0&size=20', token),
        listCatalog('teacher-availability', 'page=0&size=20', token),
      ]);
      if (ignore) {
        return;
      }
      if (teacherResult.status === 'fulfilled') {
        setTeachers(teacherResult.value);
      } else {
        setError(teacherResult.reason instanceof Error ? teacherResult.reason.message : 'Error cargando docentes.');
      }
      if (careerResult.status === 'fulfilled') {
        setCareers(careerResult.value);
      }
      if (journeyResult.status === 'fulfilled') {
        setJourneys(journeyResult.value);
      }
      setPending({
        ...(careerJourneyResult.status === 'rejected' ? { careerJourneys: 'GET /api/catalog/teacher-career-journeys no disponible.' } : {}),
        ...(coursesResult.status === 'rejected' ? { courses: 'GET /api/catalog/teacher-courses no disponible.' } : {}),
        ...(availabilityResult.status === 'rejected' ? { availability: 'GET /api/catalog/teacher-availability no disponible para CRUD admin.' } : {}),
      });
      setLoading(false);
    }
    void loadTeachers();
    return () => {
      ignore = true;
    };
  }, [token]);

  return (
    <div className="teachers-page">
      <section className="form-panel">
        <p className="eyebrow">Docentes</p>
        <h2>Filtros preparados</h2>
        <label>
          Carrera
          <select value={careerFilter} onChange={(event) => setCareerFilter(event.target.value)} disabled={!careers.items.length || Boolean(pending.careerJourneys)}>
            <option value="">Todas</option>
            {careers.items.map((career) => (
              <option key={String(career.id ?? career.code)} value={String(career.id ?? career.code)}>
                {String(career.code ?? '')} {String(career.name ?? '')}
              </option>
            ))}
          </select>
        </label>
        <label>
          Jornada
          <select value={journeyFilter} onChange={(event) => setJourneyFilter(event.target.value)} disabled={!journeys.items.length || Boolean(pending.careerJourneys)}>
            <option value="">Todas</option>
            {journeys.items.map((journey) => (
              <option key={String(journey.id ?? journey.code)} value={String(journey.id ?? journey.code)}>
                {String(journey.code ?? '')} {String(journey.name ?? '')}
              </option>
            ))}
          </select>
        </label>
        {pending.careerJourneys ? <ApiPending>{pending.careerJourneys} Filtros sin aplicar hasta tener asignaciones reales.</ApiPending> : null}
        <Metric label="Docentes reales" value={loading ? '...' : teachers.totalItems} />
      </section>

      <section className="catalog-main">
        <div className="section-title">
          <h2>Catalogo de docentes</h2>
          <Badge tone="info">disponibilidad -&gt; carrera+jornada -&gt; cursos</Badge>
        </div>
        {error ? <ErrorBox message={error} /> : null}
        <Table>
          <table>
            <thead>
              <tr>
                <th>Codigo</th>
                <th>Nombre</th>
                <th>Prioridad</th>
                <th>Min</th>
                <th>Max</th>
                <th>Activo</th>
              </tr>
            </thead>
            <tbody>
              {teachers.items.map((teacher, index) => (
                <tr key={String(teacher.id ?? teacher.code ?? index)}>
                  <td>{String(teacher.code ?? '')}</td>
                  <td>{String(teacher.fullName ?? '')}</td>
                  <td>{String(teacher.priority ?? '')}</td>
                  <td>{String(teacher.minCourses ?? '')}</td>
                  <td>{String(teacher.maxCourses ?? '')}</td>
                  <td>{String(teacher.active ?? '')}</td>
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

        <div className="relation-grid">
          <PendingPanel title="Docente -> carrera+jornada" pending={pending.careerJourneys} />
          <PendingPanel title="Docente -> cursos" pending={pending.courses} />
          <PendingPanel title="Disponibilidad admin" pending={pending.availability}>
            <AvailabilityMatrix locked />
          </PendingPanel>
        </div>
      </section>
    </div>
  );
}

function AvailabilityPage({ role, token }: { role: string; token: string }) {
  const isTeacher = role === 'TEACHER';
  const [pending, setPending] = useState('Consultando GET /api/teacher/availability...');

  useEffect(() => {
    let ignore = false;
    if (!isTeacher) {
      setPending('ADMIN no edita su disponibilidad aqui. Use endpoints admin cuando existan.');
      return;
    }
    api('/api/teacher/availability', {}, token)
      .then(() => {
        if (!ignore) {
          setPending('GET /api/teacher/availability existe, falta conectar DTO real.');
        }
      })
      .catch(() => {
        if (!ignore) {
          setPending('GET/PUT /api/teacher/availability no disponible en backend actual.');
        }
      });
    return () => {
      ignore = true;
    };
  }, [isTeacher, token]);

  return (
    <div className="availability-page">
      <section className="form-panel">
        <p className="eyebrow">Disponibilidad docente</p>
        <h2>{isTeacher ? 'Mi matriz' : 'Vista fuera de alcance'}</h2>
        <ApiPending>{pending}</ApiPending>
      </section>
      <section className="catalog-main">
        <div className="section-title">
          <h2>Dia x bloque</h2>
          <Badge tone="warning">bloqueado</Badge>
        </div>
        <AvailabilityMatrix locked />
      </section>
    </div>
  );
}

function TimePage({ token }: { token: string }) {
  const catalog = CATALOGS.find((item) => item.resource === 'journeys') ?? CATALOGS[0];
  const [page, setPage] = useState<PageResponse>({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
  const [values, setValues] = useState(() => defaultValues(catalog));
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

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
    try {
      await createCatalogItem('journeys', normalizePayload(catalog, values), token);
      setValues(defaultValues(catalog));
      await load();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Error creando jornada.');
    }
  }

  return (
    <div className="time-page">
      <section className="catalog-main">
        <div className="section-title">
          <h2>Jornadas</h2>
          <button className="ghost" onClick={load} type="button" disabled={loading}>Actualizar</button>
        </div>
        {error ? <ErrorBox message={error} /> : null}
        <Table>
          <table>
            <thead>
              <tr>
                <th>Codigo</th>
                <th>Nombre</th>
                <th>Min/bloque</th>
                <th>Inicio</th>
                <th>Fin</th>
                <th>Bloques</th>
              </tr>
            </thead>
            <tbody>
              {page.items.map((journey, index) => (
                <tr key={String(journey.id ?? journey.code ?? index)}>
                  <td>{String(journey.code ?? '')}</td>
                  <td>{String(journey.name ?? '')}</td>
                  <td>{String(journey.blockMinutes ?? '')}</td>
                  <td>{String(journey.startTime ?? '')}</td>
                  <td>{String(journey.endTime ?? '')}</td>
                  <td>{countJourneyBlocks(journey)}</td>
                </tr>
              ))}
              {!page.items.length ? (
                <tr>
                  <td colSpan={6}>{loading ? 'Cargando...' : 'Sin jornadas reales'}</td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </Table>
        <section className="form-panel">
          <div className="section-title">
            <h2>Descansos fijos</h2>
            <Badge tone="warning">fixed_breaks</Badge>
          </div>
          <ApiPending>GET/POST para fixed_breaks no existe. No se calculan ni persisten bloques desde frontend.</ApiPending>
        </section>
      </section>
      <form className="form-panel" onSubmit={create}>
        <h2>Crear jornada</h2>
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

function PendingPanel({ title, pending, children }: { title: string; pending?: string; children?: React.ReactNode }) {
  return (
    <article className="pending-panel">
      <div className="section-title">
        <h2>{title}</h2>
        <Badge tone={pending ? 'warning' : 'success'}>{pending ? 'API pendiente' : 'Disponible'}</Badge>
      </div>
      {pending ? <ApiPending>{pending}</ApiPending> : null}
      <button type="button" disabled title={pending ?? 'Pendiente de conectar DTO real'}>Editar</button>
      {children}
    </article>
  );
}

const AVAILABILITY_STATES = [
  ['Disponible', 'Preferido', 'No disponible', 'Disponible', 'Disponible'],
  ['No disponible', 'Disponible', 'Disponible', 'Preferido', 'Disponible'],
  ['Disponible', 'Disponible', 'Preferido', 'No disponible', 'Disponible'],
  ['Preferido', 'Disponible', 'Disponible', 'Disponible', 'No disponible'],
  ['Disponible', 'No disponible', 'Disponible', 'Disponible', 'Preferido'],
];

function AvailabilityMatrix({ locked }: { locked: boolean }) {
  return (
    <div className="availability-matrix" aria-label={locked ? 'Matriz de disponibilidad bloqueada' : 'Matriz de disponibilidad'}>
      <div className="matrix-head">Bloque</div>
      {DAYS.slice(0, 5).map((day) => <div className="matrix-head" key={day}>{day}</div>)}
      {AVAILABILITY_STATES.map((row, rowIndex) => (
        <React.Fragment key={rowIndex}>
          <div className="matrix-time">B{rowIndex + 1}</div>
          {row.map((state, colIndex) => (
            <button className={`matrix-cell state-${state.toLowerCase().replaceAll(' ', '-')}`} disabled={locked} key={`${rowIndex}-${colIndex}`} type="button">
              {state}
            </button>
          ))}
        </React.Fragment>
      ))}
    </div>
  );
}

function countJourneyBlocks(journey: Record<string, unknown>) {
  const start = minutesOfDay(String(journey.startTime ?? ''));
  const end = minutesOfDay(String(journey.endTime ?? ''));
  const blockMinutes = Number(journey.blockMinutes);
  if (start === null || end === null || !Number.isFinite(blockMinutes) || blockMinutes <= 0 || end <= start) {
    return 'Sin dato';
  }
  return Math.floor((end - start) / blockMinutes);
}

function minutesOfDay(value: string) {
  const [hour, minute] = value.split(':').map(Number);
  if (!Number.isFinite(hour) || !Number.isFinite(minute)) {
    return null;
  }
  return hour * 60 + minute;
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
  const [planId, setPlanId] = useState('');
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
    const response = await runAction('validate', () => validateSchedulePlan(numericPlanId, token));
    if (response) {
      setValidation(response);
      setStatus(response.status);
    }
  }

  async function generatePlan() {
    const response = await runAction('generate', () => generateSchedulePlan(numericPlanId, token));
    if (response) {
      setGeneration(response);
      setRunId(response.runId);
      setStatus(response.planStatus);
      void loadResult(response.runId);
      void loadViolations(response.runId);
    }
  }

  async function loadResult(nextRunId = runId) {
    const response = await runAction('result', () => getScheduleResult(numericPlanId, nextRunId, token));
    if (response) {
      setResult(response);
      setRunId(response.runId);
      setStatus(response.planStatus);
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
      setStatus(response.status);
    }
  }

  async function lockPlan() {
    const response = await runAction('lock', () => lockSchedulePlan(numericPlanId, token));
    if (response) {
      setStatus(response.status);
    }
  }

  async function submitManualEdit(draft: ManualDraft) {
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

  return (
    <div className="plans-page">
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
          <ActionButton type="button" onClick={validatePlan} disabled={!hasPlan || !canValidate || loading !== ''}>
            {loading === 'validate' ? 'Validando...' : 'Validar'}
          </ActionButton>
          <ActionButton type="button" onClick={generatePlan} disabled={!hasPlan || !canGenerate || loading !== ''}>
            {loading === 'generate' ? 'Generando...' : 'Generar'}
          </ActionButton>
          <ActionButton type="button" onClick={approvePlan} disabled={!hasPlan || !canApprove || loading !== ''}>
            Aprobar
          </ActionButton>
          <ActionButton type="button" onClick={lockPlan} disabled={!hasPlan || !canLock || loading !== ''}>
            Bloquear
          </ActionButton>
        </div>
        <button className="ghost" type="button" onClick={() => { void loadResult(); void loadViolations(); }} disabled={!hasPlan || loading !== ''}>
          Cargar resultado
        </button>
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
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 6 } }));
  const [view, setView] = useState<GridView>('cohort');
  const [filter, setFilter] = useState('all');
  const [draft, setDraft] = useState<ManualDraft | null>(null);
  const [activeAssignment, setActiveAssignment] = useState<Assignment | null>(null);
  const options = useMemo(() => gridOptions(assignments, view), [assignments, view]);
  const visibleAssignments = useMemo(
    () => assignments.filter((assignment) => matchesGridFilter(assignment, view, filter)),
    [assignments, filter, view],
  );
  const maxBlock = Math.max(7, ...assignments.map((assignment) => assignment.startBlock + assignment.durationBlocks - 1));
  const blocks = Array.from({ length: maxBlock + 1 }, (_, index) => index);

  function moveFromDrag(event: DragEndEvent) {
    const assignment = assignments.find((item) => `session-${item.sessionId}` === event.active.id);
    setActiveAssignment(null);
    if (!assignment || !event.over || !canEdit) {
      return;
    }
    const [, day, block] = String(event.over.id).split(':');
    openDraft(assignment, Number(day), Number(block));
  }

  function openDraft(assignment: Assignment, targetDay = assignment.dayOfWeek, targetStartBlock = assignment.startBlock) {
    setDraft({
      assignment,
      targetDay,
      targetStartBlock,
      targetTimeBlockId: String(toTimeBlockId(targetDay, targetStartBlock)),
      targetTeacherId: String(assignment.teacherId),
      targetRoomId: String(assignment.roomId),
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
      <DndContext
        sensors={sensors}
        onDragStart={(event) => setActiveAssignment(assignments.find((item) => `session-${item.sessionId}` === event.active.id) ?? null)}
        onDragEnd={moveFromDrag}
        onDragCancel={() => setActiveAssignment(null)}
      >
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
                      <SessionBlock
                        assignment={assignment}
                        canEdit={canEdit && !assignment.pinned}
                        key={assignment.id}
                        onOpen={() => openDraft(assignment)}
                      />
                    ))}
                  </GridCell>
                );
              })}
            </React.Fragment>
          ))}
        </div>
        <DragOverlay>{activeAssignment ? <SessionCard assignment={activeAssignment} /> : null}</DragOverlay>
      </DndContext>
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

function GridCell({ canDrop, day, block, children }: { canDrop: boolean; day: number; block: number; children: React.ReactNode }) {
  const { isOver, setNodeRef } = useDroppable({ id: `cell:${day}:${block}`, disabled: !canDrop });
  return (
    <div className={`grid-cell ${isOver ? 'over' : ''}`} ref={setNodeRef}>
      {children}
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
  return (
    <>
      <strong>{assignment.courseCode}</strong>
      <span>{assignment.courseName}</span>
      <small>{assignment.teacherName}</small>
      <div className="session-badges">
        <em>Aula {assignment.roomCode}</em>
        <em>C{assignment.cohortIds.join(',')}</em>
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
        <button className="ghost icon-button" onClick={onClose} type="button">x</button>
      </div>
      <p className="muted">{draft.assignment.courseCode} · sesion {draft.assignment.sessionId}</p>
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

function ConflictPanel({
  validation,
  violations,
  unassigned,
}: {
  validation: ValidationIssue[];
  violations: Violation[];
  unassigned: Assignment[];
}) {
  const hard = [...validation.filter((item) => item.severity === 'ERROR'), ...violations.filter((item) => item.severity === 'ERROR')];
  const soft = [...validation.filter((item) => item.severity !== 'ERROR'), ...violations.filter((item) => item.severity !== 'ERROR')];
  return (
    <aside className="conflict-panel">
      <h2>Conflictos</h2>
      <ConflictList title="Duros" tone="danger" items={hard} />
      <ConflictList title="Blandos" tone="warning" items={soft} />
      <div className="conflict-list suggestion">
        <h3>Sin asignar</h3>
        {unassigned.map((item) => <p key={item.sessionId}>{item.courseCode} · sesion {item.sessionId}</p>)}
        {!unassigned.length ? <p>Sin sesiones no asignadas</p> : null}
      </div>
    </aside>
  );
}

function ConflictList({ title, tone, items }: { title: string; tone: 'danger' | 'warning'; items: (ValidationIssue | Violation)[] }) {
  return (
    <div className={`conflict-list ${tone}`}>
      <h3>{title}</h3>
      {items.map((item) => <p key={`${item.code}-${item.id}`}><strong>{item.code}</strong> {item.message}</p>)}
      {!items.length ? <p>Sin registros</p> : null}
    </div>
  );
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
  draft,
  items,
  loading,
  onChange,
  onSubmit,
}: {
  assignments: Assignment[];
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
        <span className="muted">{items.length} vigentes/historicas</span>
      </div>
      <form className="substitution-form" onSubmit={onSubmit}>
        <label>
          Clase base
          <select
            required
            value={draft.assignmentId}
            onChange={(event) => onChange({ ...draft, assignmentId: event.target.value })}
          >
            <option value="">Selecciona clase</option>
            {assignments.map((assignment) => (
              <option key={assignment.id} value={assignment.id}>
                {assignment.courseCode} · {assignment.teacherName} · {DAYS[assignment.dayOfWeek - 1]} B{assignment.startBlock + 1}
              </option>
            ))}
          </select>
        </label>
        <label>
          ID docente sustituto
          <input
            min="1"
            required
            type="number"
            value={draft.substituteTeacherId}
            onChange={(event) => onChange({ ...draft, substituteTeacherId: event.target.value })}
          />
        </label>
        <label>
          Inicio
          <input
            required
            type="datetime-local"
            value={draft.startsAt}
            onChange={(event) => onChange({ ...draft, startsAt: event.target.value })}
          />
        </label>
        <label>
          Fin
          <input
            type="datetime-local"
            value={draft.endsAt}
            onChange={(event) => onChange({ ...draft, endsAt: event.target.value })}
          />
        </label>
        <label className="checkbox">
          <input
            checked={draft.isPermanent}
            onChange={(event) => onChange({ ...draft, isPermanent: event.target.checked })}
            type="checkbox"
          />
          Permanente
        </label>
        <label>
          Motivo
          <input value={draft.reason} onChange={(event) => onChange({ ...draft, reason: event.target.value })} />
        </label>
        <button type="submit" disabled={loading || !draft.assignmentId || !draft.substituteTeacherId}>
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
      map.set(String(assignment.teacherId), assignment.teacherName);
    } else if (view === 'room') {
      map.set(String(assignment.roomId), assignment.roomCode);
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
    return String(assignment.teacherId) === filter;
  }
  if (view === 'room') {
    return String(assignment.roomId) === filter;
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

function CatalogPage({ catalog, token }: { catalog: Catalog; token: string }) {
  const [page, setPage] = useState<PageResponse>({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
  const [values, setValues] = useState(() => defaultValues(catalog));
  const [sameWeeklyBlocks, setSameWeeklyBlocks] = useState(true);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const isCourseCatalog = catalog.resource === 'courses';

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

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
