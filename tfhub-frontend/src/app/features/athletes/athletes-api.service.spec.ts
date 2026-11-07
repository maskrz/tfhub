import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AthleteResponse } from './athlete.model';
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
});
