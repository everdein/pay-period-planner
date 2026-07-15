import type { ReactNode } from 'react';

export function WorkflowNotice({
  actions,
  detail,
  message,
  requestId,
  title,
  tone,
}: {
  actions?: ReactNode;
  detail?: string;
  message?: string;
  requestId?: string;
  title: string;
  tone: 'conflict' | 'error' | 'success';
}) {
  return (
    <section
      aria-live={tone === 'success' ? 'polite' : 'assertive'}
      className={`workflow-notice ${tone}`}
      role={tone === 'success' ? 'status' : 'alert'}
    >
      <div>
        <strong>{title}</strong>
        {message && <span>{message}</span>}
        {detail && <small>{detail}</small>}
        {requestId && <small>Request reference: {requestId}</small>}
      </div>
      {actions && <div className="workflow-notice-actions">{actions}</div>}
    </section>
  );
}
