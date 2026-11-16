// Compile-time checks that the generated models behave as intended. Run: tsc -p .
import { StatusValues, type OrderSearch, type Result, type Shape, type Status } from '../resources/golden/models';

// Unions narrow on their tag.
export function area(shape: Shape): number {
  switch (shape['@type']) {
    case 'circle':
      return Math.PI * shape.radius ** 2;
    case 'square':
      return shape.side ** 2;
  }
}

export function unwrap<T>(result: Result<T>, fallback: T): T {
  return result.kind === 'ok' ? result.value : fallback;
}

// Enum values are available at runtime and typed.
export const statuses: readonly Status[] = StatusValues;

// Generic arguments flow through.
export function firstId(search: OrderSearch): number | undefined {
  return search.page.items[0]?.id;
}

// @ts-expect-error: unknown enum values are rejected.
export const wrong: Status = 'PENDING';
