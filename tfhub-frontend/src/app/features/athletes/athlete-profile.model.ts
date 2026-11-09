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
