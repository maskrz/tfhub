export type CompetitionSeasonType = 'INDOOR' | 'OUTDOOR' | 'UNKNOWN';
export type CompetitionResultSource = 'PZLA' | 'MANUAL';
export type CompetitionSyncMode = 'CURRENT_SEASON' | 'FULL_HISTORY';
export type CompetitionSyncState =
  | 'QUEUED'
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'NOT_SYNCHRONIZED';

export interface CompetitionEvent {
  id: number;
  name: string;
  unit: string;
  active: boolean;
}

export interface CompetitionResult {
  id: number;
  athleteId: number;
  eventId: number;
  eventName: string;
  resultDate: string;
  originalMark: string;
  numericValue: number | null;
  unit: string;
  wind: number | null;
  season: number | null;
  seasonType: CompetitionSeasonType;
  competitionName: string | null;
  location: string | null;
  resultRound: string | null;
  source: CompetitionResultSource;
}

export interface SaveCompetitionEventRequest {
  name: string;
  unit: string;
}

export interface ChangeCompetitionEventStatusRequest {
  active: boolean;
}

export interface SaveCompetitionResultRequest {
  eventId: number;
  resultDate: string;
  originalMark: string;
  numericValue: number | null;
  wind: number | null;
  season: number | null;
  seasonType: CompetitionSeasonType;
  competitionName: string | null;
  location: string | null;
  resultRound: string | null;
}

export interface CompetitionSyncStatus {
  runId: number | null;
  athleteId: number;
  mode: CompetitionSyncMode | null;
  status: CompetitionSyncState;
  requestedAt: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  lastSuccessfulSyncAt: string | null;
  errorCode: string | null;
  errorMessage: string | null;
}

export interface GroupCompetitionSyncResponse {
  groupId: number;
  submitted: CompetitionSyncStatus[];
  skipped: { athleteId: number; reason: string }[];
}
