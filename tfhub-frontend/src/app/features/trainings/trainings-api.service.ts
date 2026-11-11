import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CreateTrainingRequest, CreateTrainingResponse, TrainingListItem } from './training.model';

@Injectable({ providedIn: 'root' })
export class TrainingsApiService {
  private readonly http = inject(HttpClient);

  getAll(): Observable<TrainingListItem[]> {
    return this.http.get<TrainingListItem[]>('/api/trainings');
  }

  create(request: CreateTrainingRequest): Observable<CreateTrainingResponse> {
    return this.http.post<CreateTrainingResponse>('/api/trainings', request);
  }
}
