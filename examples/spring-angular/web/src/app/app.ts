import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { scan } from 'rxjs';
import { PriorityParamValues, TaskApi, apiError, type PriorityParam, type TaskEvent } from './api';

/** Uses every part of the generated API layer: resources, calls, typed errors and streams. */
@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Tasks</h1>

    <section>
      <label>
        Priority
        <select data-testid="filter" (change)="filterBy($any($event.target).value)">
          <option value="">Any</option>
          @for (priority of priorities; track priority) {
            <option [value]="priority">{{ priority }}</option>
          }
        </select>
      </label>
      <ul data-testid="tasks">
        @for (task of tasks.value(); track task.id) {
          <li>
            {{ task.title }} ({{ task.priority }})
            @if (task.priority === 'low') {
              <button (click)="raise(task.id)">Raise</button>
            }
          </li>
        }
      </ul>
    </section>

    <section>
      <input data-testid="title" #title placeholder="New task" />
      <button data-testid="add" (click)="add(title.value); title.value = ''">Add</button>
      <button data-testid="missing" (click)="loadMissing()">Load a missing task</button>
      <p data-testid="error">{{ error() }}</p>
    </section>

    <section>
      <button data-testid="progress" (click)="watchProgress()">Watch progress</button>
      <p data-testid="events">{{ events().join(' · ') }}</p>
      <button data-testid="greeting" (click)="greet()">Stream a greeting</button>
      <p data-testid="words">{{ words().join(' ') }}</p>
    </section>
  `,
})
export class App {
  private readonly api = inject(TaskApi);

  protected readonly priorities = PriorityParamValues;
  private readonly filter = signal<PriorityParam | undefined>(undefined);
  protected readonly tasks = this.api.listResource(() => ({ priority: this.filter() }));
  protected readonly error = signal('');
  protected readonly events = signal<string[]>([]);
  protected readonly words = signal<string[]>([]);

  protected filterBy(value: string): void {
    this.filter.set(value === '' ? undefined : (value as PriorityParam));
  }

  protected add(title: string): void {
    this.api.create({ title, priority: 'low' }).subscribe(() => this.tasks.reload());
  }

  protected raise(id: string): void {
    this.api.setPriority(id, 'high').subscribe(() => this.tasks.reload());
  }

  protected loadMissing(): void {
    this.api.get('00000000-0000-0000-0000-000000000000').subscribe({
      error: (error: unknown) => this.error.set(apiError(error)?.title ?? 'Unknown error'),
    });
  }

  protected watchProgress(): void {
    this.api
      .progress()
      .pipe(scan((all: string[], event: TaskEvent) => [...all, describe(event)], []))
      .subscribe((all) => this.events.set(all));
  }

  protected greet(): void {
    this.api
      .greeting()
      .pipe(scan((all: string[], word: string) => [...all, word], []))
      .subscribe((all) => this.words.set(all));
  }
}

function describe(event: TaskEvent): string {
  switch (event.type) {
    case 'started':
      return `started ${event.total}`;
    case 'progressed':
      return `done ${event.done}`;
    case 'finished':
      return event.summary;
  }
}
