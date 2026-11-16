import { expect, test } from '@playwright/test';

// Each test checks a behavior on the wire, against the real Spring Boot server.

test('lists tasks through an httpResource and filters by enum constant name', async ({ page }) => {
  await page.goto('/');
  const tasks = page.getByTestId('tasks');
  await expect(tasks.getByRole('listitem')).toHaveCount(2);
  // The query parameter must be the constant name (HIGH): Spring binds it with Enum.valueOf.
  await page.getByTestId('filter').selectOption('HIGH');
  await expect(tasks.getByRole('listitem')).toHaveCount(1);
  await expect(tasks).toContainText('Read the RecordBridge README (high)');
});

test('posts a JSON body and sends an enum body as JSON', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('title').fill('Ship RecordBridge');
  await page.getByTestId('add').click();
  const created = page.getByRole('listitem').filter({ hasText: 'Ship RecordBridge' });
  await expect(created).toContainText('(low)');
  // The body is just "high": it must go out as application/json, not text/plain (415).
  await created.getByRole('button', { name: 'Raise' }).click();
  await expect(created).toContainText('(high)');
});

test('reads typed problem details from an error response', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('missing').click();
  await expect(page.getByTestId('error')).toHaveText('Task not found');
});

test('streams typed JSON events', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('progress').click();
  await expect(page.getByTestId('events')).toHaveText(
    'started 3 · done 1 · done 2 · done 3 · 3 of 3 done',
  );
});

test('streams plain text events without JSON parsing', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('greeting').click();
  await expect(page.getByTestId('words')).toHaveText('Hello from Spring');
});
