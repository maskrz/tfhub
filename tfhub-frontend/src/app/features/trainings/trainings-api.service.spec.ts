import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  CreateTrainingRequest,
  CreateTrainingResponse,
  CompleteTrainingRequest,
  EditTrainingRequest,
  TrainingDetails,
  UpdateTrainingResponse,
} from './training.model';
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

  it('creates a training using the UC-502 endpoint and request DTO', () => {
    const body: CreateTrainingRequest = {
      name: 'Trening szybkościowy',
      trainingDate: '2026-11-11',
      trainingGroupId: 3,
      definitions: [{
        name: 'Główna',
        sequenceNumber: 1,
        segments: [{ sequenceNumber: 1, distanceM: 200, restAfterSeconds: null }],
      }],
    };
    const response: CreateTrainingResponse = { id: 12 };
    let actual: CreateTrainingResponse | undefined;

    service.create(body).subscribe((result) => (actual = result));

    const request = httpTesting.expectOne('/api/trainings');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush(response, { status: 201, statusText: 'Created' });
    expect(actual).toEqual(response);
  });

  it('retrieves a training with all details from the UC-504 endpoint', () => {
    const response: TrainingDetails = {
      id: 12,
      name: 'Trening szybkościowy',
      trainingDate: '2026-11-11',
      active: true,
      trainingGroup: { id: 3, name: 'Sprint' },
      definitions: [],
    };
    let actual: TrainingDetails | undefined;

    service.getById(12).subscribe((training) => (actual = training));

    const request = httpTesting.expectOne('/api/trainings/12');
    expect(request.request.method).toBe('GET');
    request.flush(response);
    expect(actual).toEqual(response);
  });

  it('updates a training using the UC-503 endpoint and edit request DTO', () => {
    const body: EditTrainingRequest = {
      name: 'Trening zmieniony',
      trainingDate: '2026-11-12',
      definitions: [{
        id: 5,
        name: 'Główna',
        sequenceNumber: 1,
        segments: [{ id: 8, sequenceNumber: 1, distanceM: 400, restAfterSeconds: 90 }],
      }],
    };
    const response: UpdateTrainingResponse = { id: 12 };
    let actual: UpdateTrainingResponse | undefined;

    service.update(12, body).subscribe((result) => (actual = result));

    const request = httpTesting.expectOne('/api/trainings/12');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(body);
    request.flush(response);
    expect(actual).toEqual(response);
  });

  it('submits explicit completion operations to UC-510', () => {
    const body: CompleteTrainingRequest = {
      add: [{ definitionId: 5, athleteId: 20, executionUpdates: [] }],
      update: [{ id: 10, executionUpdates: [{
        id: 15,
        status: 'RECORDED',
        measuredResult: 52.4,
        usesSpikes: true,
      }] }],
      removeParticipationIds: [],
    };
    let actual: UpdateTrainingResponse | undefined;

    service.complete(12, body).subscribe((result) => (actual = result));

    const request = httpTesting.expectOne('/api/trainings/12/participations');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual(body);
    request.flush({ id: 12 });
    expect(actual).toEqual({ id: 12 });
  });
});
