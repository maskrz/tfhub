export interface TrainingGroupSummary {
  id: number;
  name: string;
}

export interface TrainingListItem {
  id: number;
  name: string;
  trainingDate: string;
  trainingGroup: TrainingGroupSummary;
  definitionCount: number;
  participantCount: number;
}

export interface CreateTrainingSegmentRequest {
  sequenceNumber: number;
  distanceM: number;
  restAfterSeconds: number | null;
}

export interface CreateTrainingDefinitionRequest {
  name: string;
  sequenceNumber: number;
  segments: CreateTrainingSegmentRequest[];
  athleteIds: number[];
}

export interface CreateTrainingRequest {
  name: string;
  trainingDate: string;
  trainingGroupId: number;
  definitions: CreateTrainingDefinitionRequest[];
}

export interface CreateTrainingResponse {
  id: number;
}
