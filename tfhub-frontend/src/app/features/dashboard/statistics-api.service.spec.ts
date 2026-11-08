import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { StatisticsApiService } from './statistics-api.service';

describe('StatisticsApiService', () => {
  let service: StatisticsApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [StatisticsApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(StatisticsApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('requests the dashboard overview', () => {
    service.getDashboardOverview().subscribe();
    const request = httpTesting.expectOne('/api/statistics/dashboard');
    expect(request.request.method).toBe('GET');
    request.flush({ activeAthleteCount: 4, availableTests: [], availableSeasons: [], recentResults: [] });
  });

  it('omits season when all-time records are requested', () => {
    service.getClubRecords(7, null).subscribe();
    const request = httpTesting.expectOne((req) => req.url === '/api/statistics/records');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('testId')).toBe('7');
    expect(request.request.params.has('season')).toBe(false);
    request.flush({ testId: 7, season: null, FEMALE: [], MALE: [] });
  });

  it('includes selected season when requesting records', () => {
    service.getClubRecords(7, 2025).subscribe();
    const request = httpTesting.expectOne((req) => req.url === '/api/statistics/records');
    expect(request.request.params.get('testId')).toBe('7');
    expect(request.request.params.get('season')).toBe('2025');
    request.flush({ testId: 7, season: 2025, FEMALE: [], MALE: [] });
  });
});
