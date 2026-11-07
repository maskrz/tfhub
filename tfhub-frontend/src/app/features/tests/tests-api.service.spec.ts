import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CreateTestRequest, TestResponse, UpdateTestRequest } from './test.model';
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

  const response: TestResponse = {
    id: 8,
    name: 'Bieg na 40 m',
    unit: 's',
    category: 'TIME',
    higherIsBetter: false,
    active: true,
  };

  it('retrieves all tests, including inactive ones, with one unfiltered search request', () => {
    let actual: TestResponse[] | undefined;

    service.getAll().subscribe((tests) => (actual = tests));

    const request = httpTesting.expectOne('/api/tests/search');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush([response]);

    expect(actual).toEqual([response]);
  });

  it('creates a test with the existing create request DTO', () => {
    const body: CreateTestRequest = {
      name: response.name,
      unit: response.unit,
      category: response.category,
      higherIsBetter: response.higherIsBetter,
    };

    service.createTest(body).subscribe();

    const request = httpTesting.expectOne('/api/tests');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush(response, { status: 201, statusText: 'Created' });
  });

  it('updates a test with the existing update request DTO', () => {
    const body: UpdateTestRequest = {
      name: response.name,
      unit: response.unit,
      category: response.category,
      higherIsBetter: response.higherIsBetter,
    };

    service.updateTest(response.id, body).subscribe();

    const request = httpTesting.expectOne('/api/tests/8');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(body);
    request.flush(response);
  });

  it('changes status using the dedicated status endpoint and request DTO', () => {
    service.changeStatus(response.id, { active: false }).subscribe();

    const request = httpTesting.expectOne('/api/tests/8/status');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ active: false });
    request.flush({ ...response, active: false });
  });
});
