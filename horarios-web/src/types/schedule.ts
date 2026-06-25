export type ValidationIssue = {
  id: number;
  severity: string;
  code: string;
  entityType: string;
  entityId: number;
  message: string;
  suggestedAction?: string;
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
  cost?: number;
};

export type Assignment = {
  id: number;
  sessionId: number;
  courseId: number;
  courseCode: string;
  courseName: string;
  teacherId: number;
  teacherName: string;
  roomId: number;
  roomCode: string;
  cohortIds: number[];
  dayOfWeek: number;
  startBlock: number;
  durationBlocks: number;
  status: string;
  pinned: boolean;
};

export type ScheduleResult = {
  planId: number;
  runId: number;
  planStatus: PlanStatus;
  score?: Record<string, number>;
  assignments: Assignment[];
  unassigned: Assignment[];
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
