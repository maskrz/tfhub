import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { TrainingPrintPage } from './training-print.page';
import { TrainingPrintSelectionService } from './training-print-selection.service';
import { TrainingsApiService } from './trainings-api.service';

describe('TrainingPrintPage', () => {
  const api = { printPreview: vi.fn() };

  beforeEach(() => {
    api.printPreview.mockReset();
    Object.defineProperty(document, 'fonts', { configurable: true, value: { ready: Promise.resolve() } });
    TestBed.configureTestingModule({
      imports: [TrainingPrintPage],
      providers: [provideRouter([]), { provide: TrainingsApiService, useValue: api }],
    });
  });

  it('rejects direct navigation without a transient selection', async () => {
    const fixture = TestBed.createComponent(TrainingPrintPage);
    fixture.detectChanges();
    expect(api.printPreview).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Brak wybranych treningów');
  });

  it('loads one batch and blocks printing if layout cannot be measured', async () => {
    TestBed.inject(TrainingPrintSelectionService).set([1, 2]);
    api.printPreview.mockReturnValue(of({ trainings: [] }));
    const print = vi.spyOn(window, 'print').mockImplementation(() => undefined);
    const fixture = TestBed.createComponent(TrainingPrintPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(api.printPreview).toHaveBeenCalledOnce();
    expect(api.printPreview).toHaveBeenCalledWith([1, 2]);
    expect(print).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Nie udało się przygotować układu wydruku');
    expect((fixture.nativeElement.querySelector('button') as HTMLButtonElement).disabled).toBe(true);
    print.mockRestore();
  });

  it('reports an unavailable or inaccessible selection without printing', () => {
    TestBed.inject(TrainingPrintSelectionService).set([3]);
    api.printPreview.mockReturnValue(throwError(() => new Error('inaccessible')));
    const fixture = TestBed.createComponent(TrainingPrintPage);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('brak dostępu');
    expect((fixture.nativeElement.querySelector('button') as HTMLButtonElement).disabled).toBe(true);
  });
});
