import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { TrainingImportStatusResponse } from './training-import.model';
import { TrainingImportsApiService } from './training-imports-api.service';
import { TrainingImportStatusPage } from './training-import-status.page';

const pendingStatus: TrainingImportStatusResponse = {
  id: 21,
  trainingGroupId: 7,
  status: 'PENDING',
  nextRetryAt: null,
  createdAt: '2026-11-12T10:00:00Z',
  possibleDuplicate: false,
  latestAttempt: null,
};

const failedStatus: TrainingImportStatusResponse = {
  ...pendingStatus,
  status: 'FAILED',
  possibleDuplicate: true,
  latestAttempt: {
    attemptNumber: 1,
    status: 'FAILED',
    startedAt: '2026-11-12T10:00:00Z',
    finishedAt: '2026-11-12T10:01:00Z',
    failureCode: 'PROCESSING_FAILED',
    failureMessage: 'Nie udało się odczytać treningu.',
  },
};

describe('TrainingImportStatusPage', () => {
  let fixture: ComponentFixture<TrainingImportStatusPage>;
  let api: {
    getStatus: ReturnType<typeof vi.fn>;
    retry: ReturnType<typeof vi.fn>;
  };
  let router: { navigate: ReturnType<typeof vi.fn> };

  async function createPage(
    getStatus: () => Observable<TrainingImportStatusResponse> = () => of(failedStatus),
    retry: () => Observable<TrainingImportStatusResponse> = () =>
      of({ ...pendingStatus, status: 'PROCESSING' }),
  ): Promise<void> {
    api = { getStatus: vi.fn(getStatus), retry: vi.fn(retry) };
    router = { navigate: vi.fn(() => Promise.resolve(true)) };
    await TestBed.configureTestingModule({
      imports: [TrainingImportStatusPage],
      providers: [
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '21' } } } },
        { provide: Router, useValue: router },
        { provide: TrainingImportsApiService, useValue: api },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(TrainingImportStatusPage);
    fixture.detectChanges();
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();
  }

  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('loads status on direct entry, renders Polish state, safe failure text, and duplicate warning', async () => {
    await createPage();

    expect(api.getStatus).toHaveBeenCalledWith(21);
    expect(fixture.nativeElement.textContent).toContain('Nie udało się przetworzyć');
    expect(fixture.nativeElement.textContent).toContain('Nie udało się odczytać treningu.');
    expect(fixture.nativeElement.textContent).toContain('Możliwy duplikat');
    expect(fixture.nativeElement.textContent).toContain('Grupa nr 7');
  });

  it('retries the same import once and resumes polling after success', async () => {
    let statusRequestCount = 0;
    await createPage(() => {
      statusRequestCount += 1;
      return of(statusRequestCount === 1 ? failedStatus : { ...pendingStatus, status: 'PROCESSING' });
    });
    const retryButton = fixture.nativeElement.querySelector('.button--primary') as HTMLButtonElement;
    retryButton.click();
    retryButton.click();
    fixture.detectChanges();
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();

    expect(api.retry).toHaveBeenCalledOnce();
    expect(api.retry).toHaveBeenCalledWith(21);
    expect(api.getStatus).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent).toContain('Przetwarzanie');

    await vi.advanceTimersByTimeAsync(3000);
    fixture.detectChanges();
    expect(api.getStatus).toHaveBeenCalledTimes(3);
  });

  it('navigates to the placeholder review route and stops polling after review is required', async () => {
    await createPage(() => of({ ...pendingStatus, status: 'REVIEW_REQUIRED' }));

    expect(router.navigate).toHaveBeenCalledWith(['/training-imports', 21, 'review']);
    await vi.advanceTimersByTimeAsync(6000);
    expect(api.getStatus).toHaveBeenCalledOnce();
  });

  it('keeps polling after a temporary network error instead of showing a terminal import failure', async () => {
    const getStatus = vi.fn()
      .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 0 })))
      .mockReturnValueOnce(of(pendingStatus));
    await createPage(getStatus);

    expect(fixture.nativeElement.textContent).toContain('Ponowimy próbę');
    expect(fixture.nativeElement.textContent).not.toContain('Nie udało się przetworzyć');
    await vi.advanceTimersByTimeAsync(3000);
    fixture.detectChanges();

    expect(api.getStatus).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent).toContain('Oczekuje na przetworzenie');
  });

  it('does not overlap status requests when one poll takes longer than the interval', async () => {
    const pendingResponse = new Subject<TrainingImportStatusResponse>();
    await createPage(() => pendingResponse);
    expect(api.getStatus).toHaveBeenCalledOnce();

    await vi.advanceTimersByTimeAsync(6000);
    expect(api.getStatus).toHaveBeenCalledOnce();

    pendingResponse.next(pendingStatus);
    pendingResponse.complete();
    await vi.advanceTimersByTimeAsync(3000);
    expect(api.getStatus).toHaveBeenCalledTimes(2);
  });

  it('stops polling when the component is destroyed', async () => {
    await createPage(() => of(pendingStatus));
    expect(api.getStatus).toHaveBeenCalledOnce();

    fixture.destroy();
    await vi.advanceTimersByTimeAsync(6000);
    expect(api.getStatus).toHaveBeenCalledOnce();
  });

  it('shows retry errors without changing the failed status', async () => {
    await createPage(() => of(failedStatus), () =>
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );
    (fixture.nativeElement.querySelector('.button--primary') as HTMLButtonElement).click();
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nie udało się ponowić przetwarzania.');
    expect(fixture.nativeElement.textContent).toContain('Nie udało się przetworzyć');
    expect(api.getStatus).toHaveBeenCalledOnce();
  });
});
