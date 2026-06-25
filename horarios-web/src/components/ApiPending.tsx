import type { ReactNode } from 'react';

export function ApiPending({ children }: { children: ReactNode }) {
  return (
    <div className="api-pending">
      <strong>API pendiente</strong>
      <span>{children}</span>
    </div>
  );
}
