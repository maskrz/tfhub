import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreateTrainingRequest,
  CreateTrainingResponse,
  CompleteTrainingRequest,
  CreateFreeTextTrainingRequest,
  EditTrainingRequest,
  EditFreeTextTrainingRequest,
  TrainingDetails,
  TrainingListItem,
  TrainingType,
  UpdateTrainingResponse,
  BulkTrainingCompletion,
  TrainingPrintResponse,
} from './training.model';

@Injectable({ providedIn: 'root' })
export class TrainingsApiService {
  private readonly http = inject(HttpClient);

  getAll(includeCompleted = false, trainingType?: TrainingType): Observable<TrainingListItem[]> {
    const params: Record<string, string> = {};
    if (includeCompleted) params['includeCompleted'] = 'true';
    if (trainingType) params['trainingType'] = trainingType;
    return this.http.get<TrainingListItem[]>('/api/trainings', {
      params,
    });
  }

  create(request: CreateTrainingRequest): Observable<CreateTrainingResponse> {
    return this.http.post<CreateTrainingResponse>('/api/trainings', request);
  }

  createFreeText(request: CreateFreeTextTrainingRequest): Observable<CreateTrainingResponse> {
    return this.http.post<CreateTrainingResponse>('/api/trainings/free-text', request);
  }

  getById(id: number): Observable<TrainingDetails> {
    return this.http.get<TrainingDetails>(`/api/trainings/${id}`);
  }

  update(id: number, request: EditTrainingRequest): Observable<UpdateTrainingResponse> {
    return this.http.put<UpdateTrainingResponse>(`/api/trainings/${id}`, request);
  }

  updateFreeText(id: number, request: EditFreeTextTrainingRequest): Observable<UpdateTrainingResponse> {
    return this.http.put<UpdateTrainingResponse>(`/api/trainings/${id}/free-text`, request);
  }

  complete(id: number, request: CompleteTrainingRequest): Observable<UpdateTrainingResponse> {
    return this.http.patch<UpdateTrainingResponse>(`/api/trainings/${id}/participations`, request);
  }

  completeFreeText(id: number): Observable<UpdateTrainingResponse> {
    return this.http.patch<UpdateTrainingResponse>(`/api/trainings/${id}/complete`, {});
  }

  deleteTraining(id: number): Observable<void> {
    return this.http.delete<void>(`/api/trainings/${id}`);
  }

  bulkDelete(trainingIds: number[]): Observable<void> {
    return this.http.post<void>('/api/trainings/bulk-delete', { trainingIds });
  }

  bulkComplete(trainingIds: number[]): Observable<BulkTrainingCompletion> {
    return this.http.post<BulkTrainingCompletion>('/api/trainings/bulk-complete', { trainingIds });
  }

  printPreview(trainingIds: number[]): Observable<TrainingPrintResponse> {
    return this.http.post<TrainingPrintResponse>('/api/trainings/print-preview', { trainingIds });
  }
}
