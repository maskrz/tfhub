import { TestCategory } from '../tests/test.model';
import { ResultType } from '../results/result.model';

export interface AvailableTest {
  id: number;
  name: string;
  category: TestCategory;
  unit: string;
  active: boolean;
}

export interface RecentResult {
  id: number;
  resultDate: string;
  athleteId: number;
  athleteName: string;
  testId: number;
  testName: string;
  value: number;
  unit: string;
  type: ResultType;
}

export interface DashboardOverview {
  activeAthleteCount: number;
  availableTests: AvailableTest[];
  availableSeasons: number[];
  recentResults: RecentResult[];
}

export interface ClubRecord {
  place: number;
  athleteId: number;
  athleteName: string;
  value: number;
  unit: string;
  resultDate: string;
}

export interface ClubRecords {
  testId: number;
  season: number | null;
  FEMALE: ClubRecord[];
  MALE: ClubRecord[];
}
