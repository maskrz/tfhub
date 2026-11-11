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

export type TrainingExecutionStatus =
  | 'PENDING'
  | 'RECORDED'
  | 'COMPLETED_WITHOUT_RESULT'
  | 'NOT_COMPLETED';

export interface TrainingExecutionDetails {
  id: number;
  segmentId: number;
  segmentSequenceNumber: number;
  status: TrainingExecutionStatus;
  measuredResult: number | null;
  usesSpikes: boolean;
}

export interface TrainingParticipationDetails {
  id: number;
  athleteId: number;
  firstName: string;
  lastName: string;
  executions: TrainingExecutionDetails[];
}

export interface TrainingSegmentDetails {
  id: number;
  sequenceNumber: number;
  distanceM: number;
  restAfterSeconds: number | null;
}

export interface TrainingDefinitionDetails {
  id: number;
  name: string;
  sequenceNumber: number;
  segments: TrainingSegmentDetails[];
  participations: TrainingParticipationDetails[];
}

export interface TrainingDetails {
  id: number;
  name: string;
  trainingDate: string;
  active: boolean;
  trainingGroup: TrainingGroupSummary;
  definitions: TrainingDefinitionDetails[];
}

export interface EditTrainingExecutionRequest {
  id: number;
  status: TrainingExecutionStatus;
  measuredResult: number | null;
  usesSpikes: boolean;
}

export interface EditTrainingSegmentRequest {
  id: number | null;
  sequenceNumber: number;
  distanceM: number;
  restAfterSeconds: number | null;
}

export interface EditTrainingDefinitionRequest {
  id: number | null;
  name: string;
  sequenceNumber: number;
  segments: EditTrainingSegmentRequest[];
}

export interface EditTrainingRequest {
  name: string;
  trainingDate: string;
  definitions: EditTrainingDefinitionRequest[];
}

export interface UpdateTrainingResponse {
  id: number;
}

export interface NewTrainingExecutionRequest {
  segmentId: number;
  status: TrainingExecutionStatus;
  measuredResult: number | null;
  usesSpikes: boolean;
}

export interface AddTrainingParticipationRequest {
  definitionId: number;
  athleteId: number;
  executionUpdates: NewTrainingExecutionRequest[];
}

export interface UpdateTrainingParticipationRequest {
  id: number;
  executionUpdates: EditTrainingExecutionRequest[];
}

export interface CompleteTrainingRequest {
  add: AddTrainingParticipationRequest[];
  update: UpdateTrainingParticipationRequest[];
  removeParticipationIds: number[];
}
