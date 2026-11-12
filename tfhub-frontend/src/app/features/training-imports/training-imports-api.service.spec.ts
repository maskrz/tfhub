import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TrainingImportsApiService } from './training-imports-api.service';

describe('TrainingImportsApiService', () => {
  let service: TrainingImportsApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), TrainingImportsApiService],
    });
    service = TestBed.inject(TrainingImportsApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('retrieves active import summaries', () => {
    service.getActive().subscribe();
    const request = httpTesting.expectOne('/api/training-imports');
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('uploads one image and the selected group as multipart form data', () => {
    const image = new File(['image'], 'training.png', { type: 'image/png' });
    service.upload(7, image).subscribe();

    const request = httpTesting.expectOne('/api/training-imports');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeInstanceOf(FormData);
    expect(request.request.body.get('trainingGroupId')).toBe('7');
    const uploadedImage = request.request.body.get('image') as File;
    expect(uploadedImage.name).toBe(image.name);
    expect(uploadedImage.type).toBe(image.type);
    expect(request.request.headers.has('Content-Type')).toBe(false);
    request.flush({ id: 21, trainingGroupId: 7, status: 'PENDING', possibleDuplicate: false });
  });

  it('retrieves import status and retries the same import ID', () => {
    service.getStatus(21).subscribe();
    const statusRequest = httpTesting.expectOne('/api/training-imports/21');
    expect(statusRequest.request.method).toBe('GET');
    statusRequest.flush({
      id: 21,
      trainingGroupId: 7,
      status: 'FAILED',
      nextRetryAt: null,
      createdAt: '2026-11-12T10:00:00Z',
      possibleDuplicate: false,
      latestAttempt: null,
    });

    service.retry(21).subscribe();
    const retryRequest = httpTesting.expectOne('/api/training-imports/21/retry');
    expect(retryRequest.request.method).toBe('POST');
    expect(retryRequest.request.body).toBeNull();
    retryRequest.flush({
      id: 21,
      trainingGroupId: 7,
      status: 'PROCESSING',
      nextRetryAt: null,
      createdAt: '2026-11-12T10:00:00Z',
      possibleDuplicate: false,
      latestAttempt: null,
    });
  });
});
