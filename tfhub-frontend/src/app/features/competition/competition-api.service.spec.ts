import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CompetitionApiService } from './competition-api.service';

describe('CompetitionApiService', () => {
  let service: CompetitionApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), CompetitionApiService],
    });
    service = TestBed.inject(CompetitionApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('loads the competition results for one athlete', () => {
    service.getAthleteResults(12).subscribe((results) => expect(results).toEqual([]));
    const request = httpTesting.expectOne('/api/athletes/12/competition-results');
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('starts an athlete synchronization with the selected mode', () => {
    service.startAthleteSync(12, 'FULL_HISTORY').subscribe();
    const request = httpTesting.expectOne('/api/athletes/12/competition-sync');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ mode: 'FULL_HISTORY' });
    request.flush({});
  });

  it('starts synchronization for a selected training group', () => {
    service.startGroupSync(4, 'CURRENT_SEASON').subscribe();
    const request = httpTesting.expectOne('/api/training-groups/4/competition-sync');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ mode: 'CURRENT_SEASON' });
    request.flush({ groupId: 4, submitted: [], skipped: [] });
  });

  it('creates and deletes an official competition result using the management endpoints', () => {
    const body = {
      eventId: 2,
      resultDate: '2025-06-21',
      originalMark: '12.34',
      numericValue: 12.34,
      wind: 1.2,
      season: 2025,
      seasonType: 'OUTDOOR' as const,
      competitionName: 'Mistrzostwa',
      location: 'Warszawa',
      resultRound: null,
    };

    service.createResult(12, body).subscribe();
    const createRequest = httpTesting.expectOne('/api/athletes/12/competition-results');
    expect(createRequest.request.method).toBe('POST');
    expect(createRequest.request.body).toEqual(body);
    createRequest.flush({});

    service.deleteResult(44).subscribe();
    const deleteRequest = httpTesting.expectOne('/api/competition-results/44');
    expect(deleteRequest.request.method).toBe('DELETE');
    deleteRequest.flush(null);
  });
});
