export type ValidationIssue = {
  id: number | null;
  severity: string;
  code: string;
  entityType: string;
  entityId: number | null;
  message: string;
  suggestedAction?: string;
  source?: Record<string, unknown>;
};

export type SchedulePlanSummary = {
  id: number;
  code: string;
  name: string;
  scheduleType: string;
  status: PlanStatus;
  startDate: string;
  endDate: string;
  createdAt: string;
  updatedAt: string;
};

export type SchedulePlanDraft = {
  code: string;
  name: string;
  scheduleType: string;
  startDate: string;
  endDate: string;
  config: Record<string, number>;
};

export type ValidationResponse = {
  planId: number;
  status: PlanStatus;
  hasBlockingErrors: boolean;
  issues: ValidationIssue[];
};

export type PlanStatus =
  | 'DRAFT'
  | 'VALIDATING'
  | 'INVALID_INPUT'
  | 'GENERATING'
  | 'GENERATED'
  | 'GENERATED_WITH_CONFLICTS'
  | 'APPROVED'
  | 'LOCKED'
  | 'ARCHIVED';

export type GenerationResponse = {
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

export type Violation = {
  id: number;
  severity: string;
  code: string;
  message: string;
  affectedEntities?: Record<string, unknown>[];
  cost?: number;
};

export type Assignment = {
  id: number;
  sessionId: number;
  courseId: number;
  courseCode: string;
  courseName: string;
  teacherId: number | null;
  teacherName: string | null;
  roomId: number | null;
  roomCode: string | null;
  cohortIds: number[];
  dayOfWeek: number;
  startBlock: number;
  durationBlocks: number | null;
  status: string;
  pinned: boolean;
};

export type UnassignedSession = {
  sessionId: number;
  courseId: number;
  courseCode: string;
  reason: string;
};

export type ScheduleResult = {
  planId: number;
  runId: number;
  planStatus: PlanStatus;
  score?: Record<string, number>;
  assignments: Assignment[];
  unassigned: UnassignedSession[];
};

export type ManualEditResponse = {
  status: string;
  resultRunId: number;
  pinnedSessionIds: number[];
  movedSessionIds: number[];
  remainingViolations: Violation[];
  scoreBefore: number;
  scoreAfter: number;
  repairTimeMs: number;
};

export type SubstitutionResponse = {
  id: number;
  assignmentId: number;
  originalTeacherId: number;
  substituteTeacherId: number;
  startsAt: string;
  endsAt?: string | null;
  isPermanent: boolean;
  reason?: string | null;
};

export type GridView = 'cohort' | 'teacher' | 'room';

export type SubstitutionDraft = {
  assignmentId: string;
  substituteTeacherId: string;
  startsAt: string;
  endsAt: string;
  isPermanent: boolean;
  reason: string;
};

export type ManualDraft = {
  assignment: Assignment;
  targetDay: number;
  targetStartBlock: number;
  targetTimeBlockId: string;
  targetTeacherId: string;
  targetRoomId: string;
};
