export type TrainingImportStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'REVIEW_REQUIRED'
  | 'FAILED'
  | 'ACCEPTED'
  | 'REJECTED';

export interface TrainingImportUploadResponse {
  id: number;
  trainingGroupId: number;
  status: TrainingImportStatus;
  possibleDuplicate: boolean;
}

export interface TrainingImportListItem {
  id: number;
  trainingGroupId: number;
  trainingGroupName: string;
  status: TrainingImportStatus;
  nextRetryAt: string | null;
  createdAt: string;
  possibleDuplicate: boolean;
  name: string | null;
  trainingDate: string | null;
  failureMessage: string | null;
}

export interface TrainingImportAttempt {
  attemptNumber: number;
  status: 'PROCESSING' | 'SUCCEEDED' | 'FAILED';
  startedAt: string;
  finishedAt: string | null;
  failureCode: string | null;
  failureMessage: string | null;
}

export interface TrainingImportStatusResponse {
  id: number;
  trainingGroupId: number;
  status: TrainingImportStatus;
  nextRetryAt: string | null;
  createdAt: string;
  possibleDuplicate: boolean;
  latestAttempt: TrainingImportAttempt | null;
}

export type ImportExecutionStatus = 'PENDING' | 'RECORDED' | 'COMPLETED_WITHOUT_RESULT' | 'NOT_COMPLETED';
export type ImportMatchStatus = 'UNRESOLVED' | 'MATCHED' | 'SKIPPED';

export interface ImportCandidate {
  id: number;
  firstName: string;
  lastName: string;
  birthYear: number | null;
  gender: 'MALE' | 'FEMALE';
  active: boolean;
}

export interface ImportExecution {
  id: number;
  segmentId: number;
  segmentSequenceNumber: number;
  status: ImportExecutionStatus;
  measuredResult: number | null;
  usesSpikes: boolean | null;
  recognizedText: string | null;
  recognitionMetadata: Record<string, unknown>;
  recognitionReviewed: boolean;
}

export interface ImportAthleteRow {
  id: number;
  recognizedName: string;
  matchStatus: ImportMatchStatus;
  athlete: ImportCandidate | null;
  recognitionMetadata: Record<string, unknown>;
  executions: ImportExecution[];
}

export interface ImportSegment {
  id: number;
  sequenceNumber: number;
  distanceM: number | null;
  restAfterSeconds: number | null;
  recognitionMetadata: Record<string, unknown>;
  recognitionReviewed: boolean;
}

export interface ImportDefinition {
  id: number;
  name: string;
  sequenceNumber: number;
  segments: ImportSegment[];
  athleteRows: ImportAthleteRow[];
}

export interface TrainingImportDraft {
  importId: number;
  status: TrainingImportStatus;
  trainingGroup: { id: number; name: string };
  name: string;
  trainingDate: string;
  recognitionMetadata: Record<string, unknown>;
  recognitionReviewed: boolean;
  definitions: ImportDefinition[];
  validation: { readyForAcceptance: boolean; issues: { code: string; path: string; message: string }[] };
}

export interface TrainingImportDraftUpdate {
  name: string;
  trainingDate: string;
  recognitionReviewed: boolean;
  definitions: {
    id: number;
    name: string;
    sequenceNumber: number;
    segments: { id: number; sequenceNumber: number; distanceM: number | null;
      restAfterSeconds: number | null; recognitionReviewed: boolean }[];
    athleteRows: {
      id: number;
      recognizedName: string;
      athleteId: number | null;
      matchStatus: ImportMatchStatus;
      executions: { id: number; segmentSequenceNumber: number; status: ImportExecutionStatus;
        measuredResult: number | null; usesSpikes: boolean | null; recognitionReviewed: boolean }[];
    }[];
  }[];
}
