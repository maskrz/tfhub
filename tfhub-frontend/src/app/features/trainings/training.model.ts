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
