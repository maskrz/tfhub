import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TrainingsApiService } from './trainings-api.service';

describe('TrainingsApiService', () => {
  let service: TrainingsApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), TrainingsApiService],
    });
    service = TestBed.inject(TrainingsApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('retrieves the active training list from the UC-501 endpoint', () => {
    const response = [{
      id: 8,
      name: 'Trening szybkościowy',
      trainingDate: '2026-11-11',
      trainingGroup: { id: 3, name: 'Sprint' },
      definitionCount: 1,
      participantCount: 4,
    }];
    let actual: typeof response | undefined;

    service.getAll().subscribe((trainings) => (actual = trainings));

    const request = httpTesting.expectOne('/api/trainings');
    expect(request.request.method).toBe('GET');
    request.flush(response);
    expect(actual).toEqual(response);
  });
});
