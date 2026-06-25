import type { ReactNode } from 'react';

export function Table({ children, flat = false }: { children: ReactNode; flat?: boolean }) {
  return <div className={`table-wrap ${flat ? 'flat' : ''}`}>{children}</div>;
}
