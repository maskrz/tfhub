import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  TrainingImportListItem,
  TrainingImportStatusResponse,
  TrainingImportUploadResponse,
  TrainingImportDraft,
  TrainingImportDraftUpdate,
  ImportCandidate,
} from './training-import.model';

@Injectable({ providedIn: 'root' })
export class TrainingImportsApiService {
  private readonly http = inject(HttpClient);

  getActive(): Observable<TrainingImportListItem[]> {
    return this.http.get<TrainingImportListItem[]>('/api/training-imports');
  }

  upload(trainingGroupId: number, image: File): Observable<TrainingImportUploadResponse> {
    const formData = new FormData();
    formData.append('trainingGroupId', String(trainingGroupId));
    formData.append('image', image, image.name);
    return this.http.post<TrainingImportUploadResponse>('/api/training-imports', formData);
  }

  getStatus(importId: number): Observable<TrainingImportStatusResponse> {
    return this.http.get<TrainingImportStatusResponse>(`/api/training-imports/${importId}`);
  }

  retry(importId: number): Observable<TrainingImportStatusResponse> {
    return this.http.post<TrainingImportStatusResponse>(
      `/api/training-imports/${importId}/retry`,
      null,
    );
  }

  getDraft(importId: number): Observable<TrainingImportDraft> {
    return this.http.get<TrainingImportDraft>(`/api/training-imports/${importId}/draft`);
  }

  updateDraft(importId: number, draft: TrainingImportDraftUpdate): Observable<TrainingImportDraft> {
    return this.http.put<TrainingImportDraft>(`/api/training-imports/${importId}/draft`, draft);
  }

  getCandidates(importId: number): Observable<ImportCandidate[]> {
    return this.http.get<ImportCandidate[]>(`/api/training-imports/${importId}/athlete-candidates`);
  }

  sourceImageUrl(importId: number): string {
    return `/api/training-imports/${importId}/source-image`;
  }

  accept(importId: number): Observable<{ importId: number; status: 'ACCEPTED'; trainingId: number }> {
    return this.http.post<{ importId: number; status: 'ACCEPTED'; trainingId: number }>(
      `/api/training-imports/${importId}/accept`, null,
    );
  }

  reject(importId: number): Observable<{ importId: number; status: 'REJECTED' }> {
    return this.http.post<{ importId: number; status: 'REJECTED' }>(
      `/api/training-imports/${importId}/reject`, null,
    );
  }
}
