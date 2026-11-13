import { ResultType } from '../results/result.model';

export interface AthleteProfileResult {
  id: number;
  testId: number;
  testName: string;
  testUnit: string;
  resultDate: string;
  value: number;
  type: ResultType;
}

export interface AthleteResultHistoryFilters {
  testIds: number[];
  fromDate?: string;
  toDate?: string;
}
export interface AthleteTrainingHistorySegment {
  segmentId: number;
  sequenceNumber: number;
  distanceM: number;
  status: 'PENDING' | 'RECORDED' | 'COMPLETED_WITHOUT_RESULT' | 'NOT_COMPLETED';
  measuredResult: number | null;
  usesSpikes: boolean;
}

export interface AthleteTrainingHistory {
  trainingId: number;
  trainingDate: string;
  canViewDetails: boolean;
  totalSegments: number;
  segments: AthleteTrainingHistorySegment[];
}

export interface AthleteTrainingHistoryFilters {
  fromDate?: string;
  toDate?: string;
}
