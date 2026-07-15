import type { ReactNode } from 'react';

export function ScrollableTableRegion({ children, label }: { children: ReactNode; label: string }) {
  return (
    <div aria-label={label} className="table-wrap" role="region" tabIndex={0}>
      {children}
    </div>
  );
}
