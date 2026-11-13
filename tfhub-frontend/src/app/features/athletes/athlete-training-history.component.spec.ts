import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { AthleteTrainingHistory } from './athlete-profile.model';
import { AthleteTrainingHistoryComponent } from './athlete-training-history.component';
import { AthletesApiService } from './athletes-api.service';

const history: AthleteTrainingHistory[] = [
  {
    trainingId: 1, trainingDate: '2026-11-01', canViewDetails: false, totalSegments: 1,
    segments: [{ segmentId: 1, sequenceNumber: 1, distanceM: 300, status: 'NOT_COMPLETED', measuredResult: null, usesSpikes: false }],
  },
  {
    trainingId: 2, trainingDate: '2026-11-11', canViewDetails: true, totalSegments: 4,
    segments: [
      { segmentId: 2, sequenceNumber: 1, distanceM: 200, status: 'RECORDED', measuredResult: 33.125, usesSpikes: true },
      { segmentId: 3, sequenceNumber: 2, distanceM: 300, status: 'COMPLETED_WITHOUT_RESULT', measuredResult: null, usesSpikes: false },
      { segmentId: 4, sequenceNumber: 3, distanceM: 200, status: 'PENDING', measuredResult: null, usesSpikes: true },
      { segmentId: 5, sequenceNumber: 4, distanceM: 300, status: 'NOT_COMPLETED', measuredResult: null, usesSpikes: false },
    ],
  },
];

describe('AthleteTrainingHistoryComponent', () => {
  let fixture: ComponentFixture<AthleteTrainingHistoryComponent>;
  let component: AthleteTrainingHistoryComponent;
  const api = { getTrainingHistory: vi.fn() };
  const user = signal({ authenticated: true as const, userId: 1, username: 'admin', role: 'ADMIN', athleteId: null });

  beforeEach(async () => {
    api.getTrainingHistory.mockReset();
    api.getTrainingHistory.mockReturnValue(of(history));
    user.set({ authenticated: true, userId: 1, username: 'admin', role: 'ADMIN', athleteId: null });
    await TestBed.configureTestingModule({
      imports: [AthleteTrainingHistoryComponent],
      providers: [
        provideRouter([]),
        { provide: AthletesApiService, useValue: api },
        { provide: AuthService, useValue: { currentUser: user } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AthleteTrainingHistoryComponent);
    fixture.componentRef.setInput('athleteId', 8);
    fixture.detectChanges();
    component = fixture.componentInstance;
  });

  afterEach(() => vi.useRealTimers());

  it('starts unloaded, changes dates without requests, and fetches only on action', () => {
    expect(api.getTrainingHistory).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.distance-picker')).toBeNull();
    component.fromDate.set('2026-01-01');
    component.toDate.set('2026-11-11');
    expect(api.getTrainingHistory).not.toHaveBeenCalled();
    const loadButton = [...fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>]
      .find((button) => button.textContent?.includes('Pobierz dane'));
    expect(loadButton).toBeDefined();
    loadButton!.click();
    fixture.detectChanges();
    expect(api.getTrainingHistory).toHaveBeenCalledWith(8, { fromDate: '2026-01-01', toDate: '2026-11-11' });
    expect(component.history().map((row) => row.trainingId)).toEqual([2, 1]);
    expect(fixture.nativeElement.querySelector('.distance-picker')).not.toBeNull();
    component.clearDates();
    expect(component.fromDate()).toBe('');
    expect(component.toDate()).toBe('');
    expect(api.getTrainingHistory).toHaveBeenCalledTimes(1);
  });

  it('clamps inclusive calendar-month ranges and rejects reversed bounds', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2024, 2, 31, 12));
    component.setQuickRange(1);
    expect(component.fromDate()).toBe('2024-02-29');
    expect(component.toDate()).toBe('2024-03-31');
    component.setQuickRange(3);
    expect(component.fromDate()).toBe('2023-12-31');
    component.setQuickRange(6);
    expect(component.fromDate()).toBe('2023-09-30');
    component.setQuickRange(12);
    expect(component.fromDate()).toBe('2023-03-31');
    component.fromDate.set('2025-01-01');
    component.load();
    expect(component.filterError()).toBeTruthy();
    expect(api.getTrainingHistory).not.toHaveBeenCalled();
  });

  it('renders statuses, precision, spikes, accessible pending and authorized links', () => {
    component.load();
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('33,125');
    expect(element.textContent).toContain('1/4');
    expect(element.textContent).toContain('X');
    expect(element.textContent).toContain('–');
    expect(element.querySelector('[aria-label*="Oczekuje na uzupełnienie"]')).not.toBeNull();
    expect(element.querySelectorAll('.segment-spikes')[0].textContent).toContain('K');
    expect(element.querySelectorAll('tbody a').length).toBe(1);
    expect(element.querySelector('tbody a')?.getAttribute('href')).toBe('/trainings/2');
    user.set({ authenticated: true, userId: 1, username: 'athlete', role: 'ATHLETE', athleteId: null });
    fixture.detectChanges();
    expect(element.querySelectorAll('tbody a').length).toBe(0);
  });

  it('retains millisecond precision and formats zero and minute boundaries', () => {
    const recorded = history[1].segments[0];
    expect(component.resultLabel({ ...recorded, measuredResult: 0 })).toBe('00,00');
    expect(component.resultLabel({ ...recorded, measuredResult: 59.999 })).toBe('59,999');
    expect(component.resultLabel({ ...recorded, measuredResult: 60.125 })).toBe('1:00,125');
    expect(component.resultLabel({ ...recorded, measuredResult: 60.12 })).toBe('1:00,12');
  });

  it('filters repeated distances without renumbering and preserves rows when none are selected', () => {
    component.load();
    fixture.detectChanges();
    expect(component.selectedDistances()).toEqual([200, 300]);
    const input = fixture.nativeElement.querySelectorAll('.distance-options input')[1] as HTMLInputElement;
    input.checked = false;
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(component.visibleSegments(component.history()[0]).map((segment) => segment.sequenceNumber)).toEqual([1, 3]);
    expect(fixture.nativeElement.textContent).toContain('3/4');
    component.toggleAll();
    expect(component.selectedDistances()).toEqual([200, 300]);
    component.toggleAll();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(2);
    expect(fixture.nativeElement.querySelectorAll('.segment').length).toBe(0);
  });

  it('retains previous data and selections on error, then resets distances after success', () => {
    component.load();
    component.selectedDistances.set([200]);
    api.getTrainingHistory.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.load();
    expect(component.error()).toContain('Nie udało się');
    expect(component.history()).toHaveLength(2);
    expect(component.selectedDistances()).toEqual([200]);
    component.retry();
    expect(component.selectedDistances()).toEqual([200, 300]);
  });

  it('shows loading and empty results and ignores a response for a previous athlete', () => {
    const pending = new Subject<AthleteTrainingHistory[]>();
    api.getTrainingHistory.mockReturnValueOnce(pending);
    component.load();
    expect(component.loading()).toBe(true);
    fixture.componentRef.setInput('athleteId', 9);
    fixture.detectChanges();
    pending.next(history);
    expect(component.loaded()).toBe(false);
    api.getTrainingHistory.mockReturnValueOnce(of([]));
    component.load();
    fixture.detectChanges();
    expect(component.loaded()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Brak treningów');
    expect(fixture.nativeElement.textContent).toContain('Brak dystansów');
  });
});
