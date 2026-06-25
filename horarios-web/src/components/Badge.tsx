import type { ReactNode } from 'react';

export function Badge({
  children,
  className = '',
  tone = 'neutral',
}: {
  children: ReactNode;
  className?: string;
  tone?: 'neutral' | 'info' | 'warning' | 'danger' | 'success';
}) {
  return <span className={`badge badge-${tone} ${className}`.trim()}>{children}</span>;
}
