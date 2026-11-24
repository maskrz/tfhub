import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TrainingListItem } from './training.model';
import { TrainingCalendarComponent } from './training-calendar.component';

function training(
  id: number,
  trainingDate: string | null,
  status: TrainingListItem['status'] = 'PLANNED',
): TrainingListItem {
  return {
    id,
    name: `Trening ${id}`,
    trainingDate,
    trainingGroup: { id: 2, name: 'Sprint' },
    definitionCount: 0,
    participantCount: 0,
    status,
    trainingType: 'TEMPO',
    assignmentType: 'GROUP',
    assignedAthleteCount: 0,
  };
}

describe('TrainingCalendarComponent', () => {
  let fixture: ComponentFixture<TrainingCalendarComponent>;

  async function createComponent(
    trainings: TrainingListItem[] = [],
    selectedDate: string | null = null,
  ): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [TrainingCalendarComponent],
      providers: [provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(TrainingCalendarComponent);
    fixture.componentRef.setInput('month', '2026-11');
    fixture.componentRef.setInput('trainings', trainings);
    fixture.componentRef.setInput('selectedDate', selectedDate);
    fixture.detectChanges();
  }

  it('aligns the month Monday-first', async () => {
    await createComponent();

    const grid = fixture.nativeElement.querySelector('.calendar-grid') as HTMLElement;
    expect(grid.children[0].classList.contains('calendar-day-placeholder')).toBe(true);
    expect(grid.children[5].classList.contains('calendar-day-placeholder')).toBe(true);
    expect((grid.children[6] as HTMLButtonElement).getAttribute('aria-label')).toContain('2026-11-01');
  });

  it('counts multiple trainings on a day and indicates both statuses', async () => {
    await createComponent([
      training(1, '2026-11-12'),
      training(2, '2026-11-12', 'COMPLETED'),
    ]);

    const day = fixture.nativeElement.querySelector('[aria-label^="2026-11-12"]') as HTMLElement;
    expect(day.querySelector('.calendar-day-count')?.textContent?.trim()).toBe('2');
    expect(day.querySelector('.calendar-status-dot--planned')).toBeTruthy();
    expect(day.querySelector('.calendar-status-dot--completed')).toBeTruthy();
  });

  it('shows selected-day trainings with group, status, and existing details links', async () => {
    await createComponent([
      training(1, '2026-11-12'),
      training(2, '2026-11-12', 'COMPLETED'),
      training(3, '2026-11-13'),
    ], '2026-11-12');

    const selected = fixture.nativeElement.querySelector(
      '[aria-labelledby="selected-day-heading"]',
    ) as HTMLElement;
    const links = selected.querySelectorAll('a');
    expect(Array.from(links).map((link) => link.getAttribute('href'))).toEqual([
      '/trainings/1',
      '/trainings/2',
    ]);
    expect(selected.textContent).toContain('Sprint');
    expect(selected.textContent).toContain('Planowany');
    expect(selected.textContent).toContain('Zakończony');
    expect(selected.querySelector('.status-badge--completed')).toBeTruthy();
  });

  it('shows undated trainings separately and links them to details', async () => {
    await createComponent([
      training(1, null),
      training(2, null, 'COMPLETED'),
      training(3, '2026-11-12'),
    ], '2026-11-12');

    const undated = fixture.nativeElement.querySelector('.undated-section') as HTMLElement;
    expect(undated.querySelectorAll('a')).toHaveLength(2);
    expect(undated.querySelector('a')?.getAttribute('href')).toBe('/trainings/1');
    expect(undated.textContent).toContain('Bez daty');
    expect(undated.textContent).toContain('Zakończony');
  });

  it('emits month changes and returns to the current month', async () => {
    await createComponent();
    const component = fixture.componentInstance;
    const changedMonths: string[] = [];
    const changedDates: string[] = [];
    component.monthChanged.subscribe((month) => changedMonths.push(month));
    component.selectedDateChanged.subscribe((date) => changedDates.push(date));

    (fixture.nativeElement.querySelector('[aria-label="Następny miesiąc"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(changedMonths).toEqual(['2026-12']);

    (fixture.nativeElement.querySelector('.today-button') as HTMLButtonElement).click();
    fixture.detectChanges();
    const today = new Date();
    expect(changedMonths[1]).toBe(
      `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}`,
    );
    expect(changedDates[0]).toBe(
      `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`,
    );
  });
});
