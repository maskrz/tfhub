import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  CreateTrainingRequest,
  CreateTrainingResponse,
  CompleteTrainingRequest,
  EditTrainingRequest,
  TrainingDetails,
  TrainingListItem,
  UpdateTrainingResponse,
  TrainingJsonDocument,
  TrainingJsonImportMetadata,
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

  it('retrieves planned trainings from the UC-501 endpoint by default', () => {
    const response: TrainingListItem[] = [{
      id: 8,
      name: 'Trening szybkościowy',
      trainingDate: '2026-11-11',
      trainingGroup: { id: 3, name: 'Sprint' },
      definitionCount: 1,
      participantCount: 4,
      status: 'PLANNED',
      trainingType: 'TEMPO',
      assignmentType: 'GROUP',
      assignedAthleteCount: 0,
    }];
    let actual: typeof response | undefined;

    service.getAll().subscribe((trainings) => (actual = trainings));

    const request = httpTesting.expectOne('/api/trainings');
    expect(request.request.method).toBe('GET');
    request.flush(response);
    expect(actual).toEqual(response);
  });

  it('includes completed trainings when requested', () => {
    service.getAll(true).subscribe();

    const request = httpTesting.expectOne('/api/trainings?includeCompleted=true');
    expect(request.request.method).toBe('GET');
    request.flush([]);
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
      status: 'PLANNED',
      trainingGroup: { id: 3, name: 'Sprint' },
      assignmentType: 'GROUP',
      assignedAthletes: [],
      definitions: [],
      trainingType: 'TEMPO',
      sections: [],
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

  it('deletes a training through the UC-505 endpoint with no response body', () => {
    let completed = false;

    service.deleteTraining(12).subscribe(() => (completed = true));

    const request = httpTesting.expectOne('/api/trainings/12');
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    expect(completed).toBe(true);
  });

  it('sends one request for each bulk action and print preview', () => {
    service.bulkDelete([1, 2]).subscribe();
    service.bulkComplete([1, 2]).subscribe();
    service.printPreview([1, 2]).subscribe();
    for (const path of ['bulk-delete', 'bulk-complete', 'print-preview']) {
      const request = httpTesting.expectOne(`/api/trainings/${path}`);
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({ trainingIds: [1, 2] });
      request.flush(path === 'bulk-delete' ? null : {});
    }
  });

  it('exports selected trainings as a JSON blob', () => {
    let actual: Blob | undefined;

    service.exportJson([4, 9]).subscribe((blob) => (actual = blob));

    const request = httpTesting.expectOne('/api/trainings/export');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ trainingIds: [4, 9] });
    expect(request.request.responseType).toBe('blob');
    const response = new Blob(['{"formatVersion":1,"trainings":[]}'], { type: 'application/json' });
    request.flush(response);
    expect(actual).toBe(response);
  });

  it('uploads the JSON file for complete backend validation', async () => {
    const file = new File(['{}'], 'trainings.json', { type: 'application/json' });
    let actual: TrainingJsonDocument | undefined;
    service.validateJsonImport(file).subscribe((document) => (actual = document));

    const request = httpTesting.expectOne('/api/trainings/import/validate');
    expect(request.request.method).toBe('POST');
    const uploadedFile = request.request.body.get('file') as File;
    expect(uploadedFile.name).toBe(file.name);
    expect(await uploadedFile.text()).toBe('{}');
    const response: TrainingJsonDocument = { formatVersion: 1, trainings: [] };
    request.flush(response);
    expect(actual).toEqual(response);
  });

  it('sends the source file with assignment and selection metadata for creation', async () => {
    const file = new File(['{}'], 'trainings.json', { type: 'application/json' });
    const metadata: TrainingJsonImportMetadata = {
      formatVersion: 1,
      trainings: [{
        selected: true,
        trainingDate: null,
        assignmentType: 'GROUP',
        trainingGroupId: 3,
        athleteIds: [],
      }],
    };
    service.importJson(file, metadata).subscribe();

    const request = httpTesting.expectOne('/api/trainings/import');
    expect(request.request.method).toBe('POST');
    const uploadedFile = request.request.body.get('file') as File;
    expect(uploadedFile.name).toBe(file.name);
    expect(await uploadedFile.text()).toBe('{}');
    const metadataPart = request.request.body.get('metadata') as Blob;
    expect(metadataPart.type).toBe('application/json');
    expect(await metadataPart.text()).toBe(JSON.stringify(metadata));
    request.flush({ createdTrainingIds: [44], createdCount: 1 });
  });
});
