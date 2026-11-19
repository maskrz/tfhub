export interface TrainingGroupSummary {
  id: number;
  name: string;
}

export type TrainingStatus = 'PLANNED' | 'COMPLETED';
export type TrainingType = 'TEMPO' | 'FREE_TEXT';
export type TrainingAssignmentType = 'GROUP' | 'ATHLETES';

export interface TrainingListItem {
  id: number;
  name: string;
  trainingDate: string | null;
  trainingGroup: TrainingGroupSummary | null;
  definitionCount: number;
  participantCount: number;
  status: TrainingStatus;
  trainingType: TrainingType;
  assignmentType: TrainingAssignmentType;
  assignedAthleteCount: number;
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
  trainingDate: string | null;
  trainingGroupId: number;
  definitions: CreateTrainingDefinitionRequest[];
}

export interface CreateTrainingSectionRequest {
  title: string;
  content: string;
  sequenceNumber: number;
}

export interface EditTrainingSectionRequest extends CreateTrainingSectionRequest {
  id: number | null;
}

export interface CreateFreeTextTrainingRequest {
  name: string;
  trainingDate: string | null;
  assignmentType: TrainingAssignmentType;
  trainingGroupId: number | null;
  athleteIds: number[];
  sections: CreateTrainingSectionRequest[];
  equipment?: string | null;
}

export interface EditFreeTextTrainingRequest {
  name: string;
  trainingDate: string | null;
  assignmentType: TrainingAssignmentType;
  trainingGroupId: number | null;
  athleteIds: number[];
  sections: EditTrainingSectionRequest[];
  equipment?: string | null;
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

export interface TrainingSectionDetails {
  id: number;
  title: string;
  content: string;
  sequenceNumber: number;
}

export interface TrainingAssignedAthlete {
  id: number;
  firstName: string;
  lastName: string;
}

export interface TrainingDetails {
  id: number;
  name: string;
  trainingDate: string | null;
  status: TrainingStatus;
  trainingGroup: TrainingGroupSummary | null;
  assignmentType: TrainingAssignmentType;
  assignedAthletes: TrainingAssignedAthlete[];
  definitions: TrainingDefinitionDetails[];
  trainingType: TrainingType;
  sections: TrainingSectionDetails[];
  equipment?: string | null;
}

export interface BulkTrainingCompletion {
  completedIds: number[];
  alreadyCompletedIds: number[];
  skippedUndatedIds: number[];
}

export interface TrainingPrintItem {
  id: number;
  name: string;
  trainingDate: string | null;
  trainingType: TrainingType;
  assignmentType: TrainingAssignmentType;
  trainingGroup: TrainingGroupSummary | null;
  assignedAthletes: Pick<TrainingAssignedAthlete, 'firstName' | 'lastName'>[];
  definitions: {
    name: string;
    sequenceNumber: number;
    segments: Pick<TrainingSegmentDetails, 'sequenceNumber' | 'distanceM' | 'restAfterSeconds'>[];
  }[];
  sections: Pick<TrainingSectionDetails, 'title' | 'content' | 'sequenceNumber'>[];
  equipment?: string | null;
}

export interface TrainingPrintResponse {
  trainings: TrainingPrintItem[];
}

export interface TrainingTransferSegment {
  distanceM: number;
  restAfterSeconds: number | null;
}

export interface TrainingTransferDefinition {
  name: string;
  segments: TrainingTransferSegment[];
}

export interface TrainingTransferSection {
  title: string;
  content: string;
}

export interface TempoTrainingTransfer {
  name: string;
  trainingType: 'TEMPO';
  definitions: TrainingTransferDefinition[];
}

export interface FreeTextTrainingTransfer {
  name: string;
  trainingType: 'FREE_TEXT';
  sections: TrainingTransferSection[];
  equipment: string | null;
}

export type TrainingTransfer = TempoTrainingTransfer | FreeTextTrainingTransfer;

export interface TrainingJsonDocument {
  formatVersion: number;
  trainings: TrainingTransfer[];
}

export interface TrainingJsonImportItemMetadata {
  selected: boolean;
  trainingDate: string | null;
  assignmentType: TrainingAssignmentType | null;
  trainingGroupId: number | null;
  athleteIds: number[];
}

export interface TrainingJsonImportMetadata {
  formatVersion: number;
  trainings: TrainingJsonImportItemMetadata[];
}

export interface TrainingJsonImportResponse {
  createdTrainingIds: number[];
  createdCount: number;
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
  trainingDate: string | null;
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
