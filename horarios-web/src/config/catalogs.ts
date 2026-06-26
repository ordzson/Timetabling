import type { Catalog } from '../types/catalog';

export const CATALOGS: Catalog[] = [
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

export type JourneyFormValues = Record<'name' | 'blockMinutes' | 'startTime' | 'endTime', string>;
export type RoomType = 'THEORY' | 'LAB' | 'MIXED';
export type RoomFormValues = Record<'capacity' | 'floor' | 'number', string> & { type: RoomType; active: boolean };

export const JOURNEY_PRESETS: JourneyFormValues[] = [
  { name: 'Matutina', blockMinutes: '50', startTime: '07:00', endTime: '12:00' },
  { name: 'Vespertina', blockMinutes: '50', startTime: '13:00', endTime: '18:00' },
  { name: 'Nocturna', blockMinutes: '45', startTime: '18:00', endTime: '21:00' },
];

export function defaultJourneyValues(): Record<string, unknown> {
  return { ...JOURNEY_PRESETS[0], name: '' };
}

export const ROOM_TYPE_OPTIONS: { value: RoomType; label: string; help: string; tone: 'info' | 'success' | 'warning' }[] = [
  { value: 'THEORY', label: 'Teorica', help: 'Clase normal', tone: 'info' },
  { value: 'LAB', label: 'Laboratorio', help: 'Equipo especial', tone: 'success' },
  { value: 'MIXED', label: 'Mixta', help: 'Teoria y lab', tone: 'warning' },
];

export function defaultRoomValues(): RoomFormValues {
  return { capacity: '', floor: '', number: '', type: 'THEORY', active: true };
}

export const CURRICULA_CATALOG: Catalog = {
  resource: 'curricula',
  title: 'Pensums',
  fields: [
    { name: 'code', label: 'Codigo', type: 'text', required: true },
    { name: 'careerId', label: 'Carrera ID', type: 'number', required: true },
    { name: 'year', label: 'Anio', type: 'number', required: true },
    { name: 'isActive', label: 'Activo', type: 'checkbox' },
    { name: 'validFrom', label: 'Vigente desde', type: 'text' },
    { name: 'validUntil', label: 'Vigente hasta', type: 'text' },
  ],
  columns: ['id', 'code', 'careerId', 'year', 'isActive', 'validFrom', 'validUntil'],
};

export const CURRICULUM_COURSES_CATALOG: Catalog = {
  resource: 'curriculum-courses',
  title: 'Cursos por semestre',
  fields: [
    { name: 'curriculumId', label: 'Pensum ID', type: 'number', required: true },
    { name: 'courseId', label: 'Curso ID', type: 'number', required: true },
    { name: 'semesterNumber', label: 'Semestre', type: 'number', required: true },
  ],
  columns: ['id', 'curriculumId', 'courseId', 'semesterNumber'],
};

export const COHORTS_CATALOG: Catalog = {
  resource: 'cohorts',
  title: 'Cohortes',
  fields: [
    { name: 'careerId', label: 'Carrera ID', type: 'number', required: true },
    { name: 'curriculumId', label: 'Pensum ID', type: 'number', required: true },
    { name: 'semesterNumber', label: 'Semestre', type: 'number', required: true },
    { name: 'section', label: 'Seccion', type: 'text', required: true },
    { name: 'journeyId', label: 'Jornada ID', type: 'number', required: true },
    { name: 'expectedStudents', label: 'Estudiantes', type: 'number', required: true },
    { name: 'active', label: 'Activa', type: 'checkbox' },
  ],
  columns: ['id', 'careerId', 'curriculumId', 'semesterNumber', 'section', 'journeyId', 'expectedStudents', 'active'],
};

export const FIXED_BREAKS_CATALOG: Catalog = {
  resource: 'fixed-breaks',
  title: 'Descansos fijos',
  fields: [
    { name: 'journeyId', label: 'Jornada ID', type: 'number' },
    { name: 'dayOfWeek', label: 'Dia', type: 'number', required: true },
    { name: 'startBlock', label: 'Bloque inicio', type: 'number', required: true },
    { name: 'durationBlocks', label: 'Duracion', type: 'number', required: true },
    { name: 'reason', label: 'Motivo', type: 'text' },
  ],
  columns: ['id', 'journeyId', 'dayOfWeek', 'startBlock', 'durationBlocks', 'reason'],
};

export const COMMON_AREAS_CATALOG: Catalog = {
  resource: 'common-areas',
  title: 'Areas comunes',
  fields: [
    { name: 'code', label: 'Codigo', type: 'text', required: true },
    { name: 'courseId', label: 'Curso ID', type: 'number', required: true },
    { name: 'journeyId', label: 'Jornada ID', type: 'number' },
    { name: 'semesterNumber', label: 'Semestre', type: 'number', required: true },
    { name: 'name', label: 'Nombre', type: 'text' },
    { name: 'active', label: 'Activa', type: 'checkbox' },
  ],
  columns: ['id', 'code', 'courseId', 'journeyId', 'semesterNumber', 'name', 'active'],
};

export const COMMON_AREA_CAREERS_CATALOG: Catalog = {
  resource: 'common-area-careers',
  title: 'Carreras por area comun',
  fields: [
    { name: 'commonAreaRuleId', label: 'Area comun ID', type: 'number', required: true },
    { name: 'careerId', label: 'Carrera ID', type: 'number', required: true },
    { name: 'curriculumId', label: 'Pensum ID', type: 'number', required: true },
  ],
  columns: ['id', 'commonAreaRuleId', 'careerId', 'curriculumId'],
};

export type RelationLookupKey = 'teachers' | 'careers' | 'journeys' | 'courses';

export type RelationField =
  | { name: string; label: string; kind: 'select'; lookup: RelationLookupKey }
  | { name: string; label: string; kind: 'number'; defaultValue?: number; min?: number; max?: number }
  | { name: string; label: string; kind: 'text'; defaultValue?: string }
  | { name: string; label: string; kind: 'checkbox'; defaultValue?: boolean };

export type TeacherRelationConfig = {
  resource: string;
  title: string;
  description: string;
  columns: string[];
  fields: RelationField[];
};

export const TEACHER_RELATION_PANELS: TeacherRelationConfig[] = [
  {
    resource: 'teacher-career-journeys',
    title: 'Asignaciones por carrera y jornada',
    description: 'Define en que carreras y jornadas puede impartir cada docente.',
    columns: ['id', 'teacherId', 'careerId', 'journeyId', 'active'],
    fields: [
      { name: 'teacherId', label: 'Docente', kind: 'select', lookup: 'teachers' },
      { name: 'careerId', label: 'Carrera', kind: 'select', lookup: 'careers' },
      { name: 'journeyId', label: 'Jornada', kind: 'select', lookup: 'journeys' },
      { name: 'active', label: 'Activa', kind: 'checkbox', defaultValue: true },
    ],
  },
  {
    resource: 'teacher-courses',
    title: 'Cursos que puede impartir',
    description: 'Relaciona docentes con cursos permitidos.',
    columns: ['id', 'teacherId', 'courseId', 'preference'],
    fields: [
      { name: 'teacherId', label: 'Docente', kind: 'select', lookup: 'teachers' },
      { name: 'courseId', label: 'Curso', kind: 'select', lookup: 'courses' },
      { name: 'preference', label: 'Preferencia', kind: 'number', defaultValue: 0 },
    ],
  },
  {
    resource: 'teacher-availability',
    title: 'Disponibilidad admin',
    description: 'Registra bloques disponibles, preferidos o no disponibles por docente.',
    columns: ['id', 'teacherId', 'journeyId', 'dayOfWeek', 'startBlock', 'durationBlocks', 'preference', 'source'],
    fields: [
      { name: 'teacherId', label: 'Docente', kind: 'select', lookup: 'teachers' },
      { name: 'journeyId', label: 'Jornada', kind: 'select', lookup: 'journeys' },
      { name: 'dayOfWeek', label: 'Dia', kind: 'number', defaultValue: 1, min: 1, max: 7 },
      { name: 'startBlock', label: 'Bloque inicio', kind: 'number', defaultValue: 0, min: 0 },
      { name: 'durationBlocks', label: 'Duracion', kind: 'number', defaultValue: 1, min: 1 },
      { name: 'preference', label: 'Preferencia', kind: 'number', defaultValue: 0 },
      { name: 'source', label: 'Origen', kind: 'text', defaultValue: 'PORTAL' },
    ],
  },
];
