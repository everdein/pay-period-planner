import type { ReactNode } from 'react';

type RecordTone = 'caution' | 'positive';

export function FinancialRecordList({
  children,
  description,
  emptyDescription,
  emptyTitle,
  footer,
  headingId,
  itemCount,
  summary,
  summaryLabel,
  title,
}: {
  children: ReactNode;
  description: ReactNode;
  emptyDescription: ReactNode;
  emptyTitle: ReactNode;
  footer?: ReactNode;
  headingId: string;
  itemCount: number;
  summary?: ReactNode;
  summaryLabel: string;
  title: ReactNode;
}) {
  return (
    <section aria-labelledby={headingId} className="record-list-section">
      <header className="record-list-header">
        <div className="record-list-heading">
          <h3 id={headingId}>{title}</h3>
          <p>{description}</p>
        </div>
        <div aria-label={summaryLabel} className="record-list-summary">
          <span>
            {itemCount} {itemCount === 1 ? 'item' : 'items'}
          </span>
          {summary && <strong>{summary}</strong>}
        </div>
      </header>

      {itemCount > 0 ? (
        <ul className="record-list">{children}</ul>
      ) : (
        <div className="record-list-empty">
          <strong>{emptyTitle}</strong>
          <p>{emptyDescription}</p>
        </div>
      )}

      {footer && <p className="record-list-total">{footer}</p>}
    </section>
  );
}

export function FinancialRecordListItem({
  actions,
  badge,
  metadata,
  primary,
  state,
  tone,
  value,
}: {
  actions?: ReactNode;
  badge?: ReactNode;
  metadata?: ReactNode[];
  primary: ReactNode;
  state?: ReactNode;
  tone?: RecordTone;
  value: ReactNode;
}) {
  const hasControls = Boolean(actions || state);
  const hasMetadata = Boolean(metadata?.length);
  const className = [
    'record-list-item',
    tone,
    hasControls ? null : 'without-controls',
    hasMetadata ? null : 'without-meta',
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <li className={className}>
      <div className="record-list-primary">
        <span className="record-list-name">{primary}</span>
        {badge && <span className={`record-list-badge${tone ? ` ${tone}` : ''}`}>{badge}</span>}
      </div>

      <div className="record-list-value">{value}</div>

      {hasMetadata && (
        <div className="record-list-meta">
          {metadata?.map((item, index) => (
            <span className="record-list-meta-item" key={index}>
              {item}
            </span>
          ))}
        </div>
      )}

      {hasControls && (
        <div className="record-list-controls">
          {state}
          {actions && <div className="record-list-actions">{actions}</div>}
        </div>
      )}
    </li>
  );
}
