import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AthleteResponse, CreateAthleteRequest, UpdateAthleteRequest } from './athlete.model';
import { AthletesApiService } from './athletes-api.service';

describe('AthletesApiService', () => {
  let service: AthletesApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), AthletesApiService],
    });
    service = TestBed.inject(AthletesApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  const athlete: AthleteResponse = {
    id: 8,
    firstName: 'Jan',
    lastName: 'Kowalski',
    birthYear: 2010,
    gender: 'MALE',
    active: true,
  };

  it('retrieves all athletes, including inactive ones, with an unfiltered search request', () => {
    let actual: AthleteResponse[] | undefined;

    service.getAll().subscribe((athletes) => (actual = athletes));

    const request = httpTesting.expectOne('/api/athletes/search');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush([athlete, { ...athlete, id: 9, active: false }]);

    expect(actual).toEqual([athlete, { ...athlete, id: 9, active: false }]);
  });

  it('changes athlete status using the dedicated endpoint and request DTO', () => {
    service.changeStatus(athlete.id, { active: false }).subscribe();

    const request = httpTesting.expectOne('/api/athletes/8/status');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ active: false });
    request.flush({ ...athlete, active: false });
  });

  it('creates an athlete using the existing endpoint and request fields', () => {
    const body: CreateAthleteRequest = {
      firstName: 'Jan',
      lastName: 'Kowalski',
      birthYear: 2010,
      gender: 'MALE',
    };

    service.createAthlete(body).subscribe();

    const request = httpTesting.expectOne('/api/athletes');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush(athlete);
  });

  it('updates an athlete using the existing endpoint and request fields', () => {
    const body: UpdateAthleteRequest = {
      firstName: 'Jan',
      lastName: 'Nowak',
      birthYear: 2011,
      gender: 'MALE',
    };

    service.updateAthlete(athlete.id, body).subscribe();

    const request = httpTesting.expectOne('/api/athletes/8');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(body);
    request.flush({ ...athlete, ...body });
  });

  it('retrieves an athlete by ID and returns the DTO contract', () => {
    service.getById(8).subscribe((actual) => expect(actual).toEqual(athlete));
    const request = httpTesting.expectOne('/api/athletes/8');
    expect(request.request.method).toBe('GET');
    request.flush(athlete);
  });

  it('retrieves tests with results for the athlete', () => {
    service.getTestsWithResults(8).subscribe((actual) => expect(actual).toEqual([
      { id: 3, name: 'Bieg 100 m', unit: 's', category: 'TIME', higherIsBetter: false, active: false },
    ]));
    const request = httpTesting.expectOne('/api/athletes/8/tests');
    expect(request.request.method).toBe('GET');
    request.flush([{ id: 3, name: 'Bieg 100 m', unit: 's', category: 'TIME', higherIsBetter: false, active: false }]);
  });

  it('requests athlete results with repeated test IDs and optional date bounds', () => {
    service.getResultHistory(8, {
      testIds: [3, 5],
      fromDate: '2024-01-01',
      toDate: '2024-12-31',
    }).subscribe((actual) => expect(actual).toEqual([
      { id: 10, testId: 3, testName: 'Bieg 100 m', testUnit: 's', resultDate: '2024-05-01', value: 12.1, type: 'TRAINING' },
    ]));

    const request = httpTesting.expectOne((req) => req.url === '/api/athletes/8/results');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.getAll('testIds')).toEqual(['3', '5']);
    expect(request.request.params.get('fromDate')).toBe('2024-01-01');
    expect(request.request.params.get('toDate')).toBe('2024-12-31');
    request.flush([
      { id: 10, testId: 3, testName: 'Bieg 100 m', testUnit: 's', resultDate: '2024-05-01', value: 12.1, type: 'TRAINING' },
    ]);
  });

  it('omits empty date bounds from athlete result history requests', () => {
    service.getResultHistory(8, { testIds: [3], fromDate: '', toDate: '' }).subscribe();
    const request = httpTesting.expectOne((req) => req.url === '/api/athletes/8/results');
    expect(request.request.params.getAll('testIds')).toEqual(['3']);
    expect(request.request.params.has('fromDate')).toBe(false);
    expect(request.request.params.has('toDate')).toBe(false);
    request.flush([]);
  });

  it('requests training history with only the selected date bounds', () => {
    service.getTrainingHistory(8, { fromDate: '2026-01-01', toDate: '2026-11-11' }).subscribe();
    const request = httpTesting.expectOne((req) => req.url === '/api/athletes/8/training-history');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual(['fromDate', 'toDate']);
    expect(request.request.params.get('fromDate')).toBe('2026-01-01');
    expect(request.request.params.get('toDate')).toBe('2026-11-11');
    request.flush([]);

    service.getTrainingHistory(8, {}).subscribe();
    const unfiltered = httpTesting.expectOne('/api/athletes/8/training-history');
    expect(unfiltered.request.params.keys()).toEqual([]);
    unfiltered.flush([]);
  });
});
