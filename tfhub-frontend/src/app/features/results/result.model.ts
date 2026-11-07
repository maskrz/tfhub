export type ResultType = 'TRAINING' | 'COMPETITION';

export interface ResultResponse {
  id: number;
  athleteId: number;
  testId: number;
  resultDate: string;
  value: number;
  type: ResultType;
}

export interface CreateResultRequest {
  athleteId: number;
  testId: number;
  resultDate: string;
  value: number;
  type: ResultType;
}

export interface ResultRow extends ResultResponse {
  athleteName: string;
  testName: string;
  unit: string;
}
