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

export interface CreateMultipleResultsEntryRequest {
  athleteId: number;
  value: number;
}

export interface CreateMultipleResultsRequest {
  testId: number;
  resultDate: string;
  type: ResultType;
  results: CreateMultipleResultsEntryRequest[];
}

export interface CreateMultipleResultsResponse {
  createdCount: number;
  results: ResultResponse[];
}

export interface ResultRow extends ResultResponse {
  athleteName: string;
  testName: string;
  unit: string;
}
