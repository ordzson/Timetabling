import { Badge } from './Badge';

const TONES: Record<string, 'neutral' | 'info' | 'warning' | 'danger' | 'success'> = {
  DRAFT: 'neutral',
  VALIDATING: 'info',
  INVALID_INPUT: 'danger',
  GENERATING: 'info',
  GENERATED: 'success',
  GENERATED_WITH_CONFLICTS: 'warning',
  APPROVED: 'info',
  LOCKED: 'neutral',
  ARCHIVED: 'neutral',
  READ_ONLY: 'neutral',
  IMPORTED: 'success',
};

export function StatusBadge({ status }: { status: string }) {
  return (
    <Badge className={`status-${status.toLowerCase().replaceAll('_', '-')}`} tone={TONES[status] ?? 'neutral'}>
      <span className={`status-dot status-${status.toLowerCase().replaceAll('_', '-')}`} aria-hidden="true" />
      {status}
    </Badge>
  );
}
