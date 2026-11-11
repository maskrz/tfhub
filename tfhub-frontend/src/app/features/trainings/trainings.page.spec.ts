import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { Observable, Subject, of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { TrainingListItem } from './training.model';
import { TrainingsApiService } from './trainings-api.service';
import { TrainingsPage } from './trainings.page';

const trainings: TrainingListItem[] = [
  {
    id: 1,
    name: 'Trening wytrzymałościowy',
    trainingDate: '2026-11-10',
    trainingGroup: { id: 2, name: 'Średni dystans' },
    definitionCount: 2,
    participantCount: 5,
  },
  {
    id: 2,
    name: 'Sprint',
    trainingDate: '2026-11-12',
    trainingGroup: { id: 1, name: 'Sprint' },
    definitionCount: 1,
    participantCount: 0,
  },
];

describe('TrainingsPage', () => {
  let fixture: ComponentFixture<TrainingsPage>;
  let api: { getAll: ReturnType<typeof vi.fn> };

  async function createPage(
    getAll: () => Observable<TrainingListItem[]> = () => of(trainings),
    waitForResponse = true,
  ): Promise<void> {
    api = { getAll: vi.fn(getAll) };
    await TestBed.configureTestingModule({
      imports: [TrainingsPage],
      providers: [
        provideRouter([]),
        { provide: TrainingsApiService, useValue: api },
        {
          provide: AuthService,
          useValue: {
            currentUser: signal({
              authenticated: true,
              userId: 1,
              username: 'coach',
              role: 'COACH',
              athleteId: null,
            }),
          },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(TrainingsPage);
    fixture.detectChanges();
    if (waitForResponse) {
      await fixture.whenStable();
      fixture.detectChanges();
    }
  }

  function setSearch(value: string): void {
    const input = fixture.nativeElement.querySelector('input[type="search"]') as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function visibleNames(): string[] {
    return Array.from(fixture.nativeElement.querySelectorAll('tbody tr')).map(
      (row) => (row as HTMLElement).querySelector('td strong')?.textContent?.trim() ?? '',
    );
  }

  it('shows loading while the API request is pending', async () => {
    const response = new Subject<TrainingListItem[]>();
    await createPage(() => response, false);

    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain('Ładowanie');
    response.next(trainings);
    response.complete();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(visibleNames()).toHaveLength(2);
  });

  it('shows the empty state when the API returns no trainings', async () => {
    await createPage(() => of([]));

    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain(
      'Brak treningów do wyświetlenia',
    );
  });

  it('shows an error and retries the list request', async () => {
    let attempt = 0;
    await createPage(() => {
      attempt += 1;
      return attempt === 1
        ? throwError(() => new HttpErrorResponse({ status: 500 }))
        : of(trainings);
    });

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie udało się pobrać listy treningów',
    );
    (fixture.nativeElement.querySelector('.retry-button') as HTMLButtonElement).click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(api.getAll).toHaveBeenCalledTimes(2);
    expect(visibleNames()).toHaveLength(2);
  });

  it('displays response fields, including a zero participant count, in newest-first order', async () => {
    await createPage();

    expect(visibleNames()).toEqual(['Sprint', 'Trening wytrzymałościowy']);
    expect(fixture.nativeElement.textContent).toContain('Średni dystans');
    expect(fixture.nativeElement.textContent).toContain('12.11.2026');
    expect(fixture.nativeElement.textContent).toContain('0');
    expect(fixture.nativeElement.querySelector('a[href="/trainings/2"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('a[href="/trainings/2/edit"]')?.textContent).toContain(
      'Edytuj',
    );
    expect(fixture.nativeElement.querySelector('a[href="/trainings/2/complete"]')?.textContent)
      .toContain('Uzupełnij trening');
    expect(fixture.nativeElement.querySelector('a[href="/trainings/new"]')?.textContent).toContain(
      'Dodaj trening',
    );
  });

  it('searches by training name or group and filters by date range locally', async () => {
    await createPage();
    setSearch('  ŚREDNI  ');

    expect(visibleNames()).toEqual(['Trening wytrzymałościowy']);
    expect(api.getAll).toHaveBeenCalledTimes(1);

    setSearch('');
    const from = fixture.nativeElement.querySelector('[aria-label="Data treningu od"]') as HTMLInputElement;
    from.value = '2026-11-11';
    from.dispatchEvent(new Event('input'));
    const to = fixture.nativeElement.querySelector('[aria-label="Data treningu do"]') as HTMLInputElement;
    to.value = '2026-11-12';
    to.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(visibleNames()).toEqual(['Sprint']);
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('filters by multiple selected training groups', async () => {
    await createPage();

    const groupFilter = fixture.nativeElement.querySelector('app-multi-select-dropdown');
    (groupFilter.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();
    const checkbox = groupFilter.querySelector('input[type="checkbox"]') as HTMLInputElement;
    checkbox.checked = true;
    checkbox.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(visibleNames()).toEqual(['Sprint']);
    expect(groupFilter.textContent).toContain('Sprint');
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('provides sort controls for the returned training data', async () => {
    await createPage();

    const dateSort = Array.from(fixture.nativeElement.querySelectorAll('thead th button')).find(
      (button) => (button as HTMLButtonElement).textContent?.includes('Data'),
    ) as HTMLButtonElement;
    dateSort.click();
    fixture.detectChanges();

    expect(visibleNames()).toEqual(['Trening wytrzymałościowy', 'Sprint']);
    expect(dateSort.closest('th')?.getAttribute('aria-sort')).toBe('ascending');
  });
});
