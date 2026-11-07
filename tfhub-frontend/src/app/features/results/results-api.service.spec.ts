import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ResultResponse } from './result.model';
import { ResultsApiService } from './results-api.service';

describe('ResultsApiService', () => {
  let service: ResultsApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), ResultsApiService],
    });
    service = TestBed.inject(ResultsApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('retrieves all results with one unfiltered request', () => {
    const response: ResultResponse[] = [
      {
        id: 1,
        athleteId: 2,
        testId: 3,
        resultDate: '2025-02-01',
        value: 7.25,
        type: 'TRAINING',
      },
    ];
    let actual: ResultResponse[] | undefined;

    service.getAll().subscribe((results) => (actual = results));

    const request = httpTesting.expectOne('/api/results');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual([]);
    request.flush(response);

    expect(actual).toEqual(response);
  });
});
