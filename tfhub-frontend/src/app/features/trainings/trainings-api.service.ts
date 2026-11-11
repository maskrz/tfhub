import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreateTrainingRequest,
  CreateTrainingResponse,
  CompleteTrainingRequest,
  EditTrainingRequest,
  TrainingDetails,
  TrainingListItem,
  UpdateTrainingResponse,
} from './training.model';

@Injectable({ providedIn: 'root' })
export class TrainingsApiService {
  private readonly http = inject(HttpClient);

  getAll(): Observable<TrainingListItem[]> {
    return this.http.get<TrainingListItem[]>('/api/trainings');
  }

  create(request: CreateTrainingRequest): Observable<CreateTrainingResponse> {
    return this.http.post<CreateTrainingResponse>('/api/trainings', request);
  }

  getById(id: number): Observable<TrainingDetails> {
    return this.http.get<TrainingDetails>(`/api/trainings/${id}`);
  }

  update(id: number, request: EditTrainingRequest): Observable<UpdateTrainingResponse> {
    return this.http.put<UpdateTrainingResponse>(`/api/trainings/${id}`, request);
  }

  complete(id: number, request: CompleteTrainingRequest): Observable<UpdateTrainingResponse> {
    return this.http.patch<UpdateTrainingResponse>(`/api/trainings/${id}/participations`, request);
  }
}
