export const currency = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
});

export function formatDate(value: string) {
  return new Date(`${value}T00:00:00`).toLocaleDateString('en-US', {
    month: '2-digit',
    day: '2-digit',
    year: 'numeric',
  });
}
