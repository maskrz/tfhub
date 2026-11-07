import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TestResponse } from './test.model';
import { TestsApiService } from './tests-api.service';

describe('TestsApiService', () => {
  let service: TestsApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), TestsApiService],
    });
    service = TestBed.inject(TestsApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('retrieves all tests, including inactive ones, with one unfiltered search request', () => {
    const response: TestResponse[] = [
      {
        id: 8,
        name: 'Bieg na 40 m',
        unit: 's',
        category: 'TIME',
        higherIsBetter: false,
        active: true,
      },
    ];
    let actual: TestResponse[] | undefined;

    service.getAll().subscribe((tests) => (actual = tests));

    const request = httpTesting.expectOne('/api/tests/search');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush(response);

    expect(actual).toEqual(response);
  });
});
