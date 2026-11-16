// Compile-time checks of the generated Angular services. Run: npm run check:ts
import type { Observable } from 'rxjs';
import { FileStoreApi, OrderApi, type Order, type Page } from '../resources/golden/angular';

declare const orders: OrderApi;
declare const files: FileStoreApi;
declare const order: Order;

// Responses are typed, text endpoints return strings and empty ones void.
export const page: Observable<Page<Order>> = orders.list();
export const one: Observable<Order> = orders.get(1);
export const text: Observable<string> = files.read('a/b.txt');
export const done: Observable<void> = orders.delete(1);

// Optional parameters can be left out; required ones can't.
export const filtered = orders.list({ status: 'OPEN', tags: ['a', 'b'] });
export const created = orders.create(order, { requestId: 'r-1' });
export const noted = orders.note(1, undefined, { in: 'x' });

// @ts-expect-error: path variables keep their Java type.
orders.get('1');

// @ts-expect-error: a required header can't be left out.
orders.create(order, {});

// @ts-expect-error: unknown enum values are rejected in query parameters.
orders.list({ status: 'PENDING' });
