// Runtime tests of the generated SSE parser. Run: npm run test:ts (Node strips the types).
// Angular's http module needs the JIT compiler when loaded outside an Angular build.
import '@angular/compiler';
import { HttpEventType, HttpResponse, type HttpEvent } from '@angular/common/http';
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { Subject, lastValueFrom, toArray } from 'rxjs';
import { sseEvents } from '../resources/golden/angular/api-config.ts';

function stream(chunks: string[]): Promise<unknown[]> {
  const events = new Subject<HttpEvent<string>>();
  const result = lastValueFrom(sseEvents<unknown>(events).pipe(toArray()));
  let text = '';
  for (const chunk of chunks) {
    text += chunk;
    events.next({ type: HttpEventType.DownloadProgress, loaded: text.length, partialText: text });
  }
  events.next(new HttpResponse({ body: text }));
  return result;
}

test('emits each event as its data arrives, whatever the chunk boundaries', async () => {
  const body = 'data:{"n":1}\n\ndata: {"n":2}\n\n';
  for (let cut = 1; cut < body.length; cut++) {
    assert.deepEqual(await stream([body.slice(0, cut), body.slice(cut)]), [{ n: 1 }, { n: 2 }]);
  }
});

test('joins multi-line data and ignores comments and other fields', async () => {
  const body = ': keep-alive\n\nevent: token\nid: 7\ndata:{"text":\ndata:"hi"}\n\n';
  assert.deepEqual(await stream([body]), [{ text: 'hi' }]);
});

test('accepts CRLF line endings and a last event without a blank line', async () => {
  assert.deepEqual(await stream(['data:1\r\n\r\ndata:2']), [1, 2]);
});

test('fails the stream on invalid JSON', async () => {
  await assert.rejects(stream(['data:{oops\n\n']), SyntaxError);
});

test('passes text data through when json is false', async () => {
  const events = new Subject<HttpEvent<string>>();
  const result = lastValueFrom(sseEvents<string>(events, false).pipe(toArray()));
  const text = 'data:Hel\n\ndata:lo "world"\n\n';
  events.next({ type: HttpEventType.DownloadProgress, loaded: text.length, partialText: text });
  events.next(new HttpResponse({ body: text }));
  assert.deepEqual(await result, ['Hel', 'lo "world"']);
});
