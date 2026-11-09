import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface TrainingGroup {
  id: number;
  name: string;
}

@Injectable({ providedIn: 'root' })
export class TrainingGroupsApiService {
  private readonly http = inject(HttpClient);

  getAccessible(): Observable<TrainingGroup[]> {
    return this.http.get<TrainingGroup[]>('/api/training-groups/accessible');
  }
}
