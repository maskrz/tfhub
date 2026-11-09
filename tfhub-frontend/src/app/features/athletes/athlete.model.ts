export type Gender = 'MALE' | 'FEMALE';

export interface AthleteResponse {
  id: number;
  firstName: string;
  lastName: string;
  birthYear: number;
  gender: Gender;
  active: boolean;
  trainingGroupId?: number | null;
}

export interface ChangeAthleteStatusRequest {
  active: boolean;
}

export interface CreateAthleteRequest {
  firstName: string;
  lastName: string;
  birthYear: number;
  gender: Gender;
  trainingGroupId?: number | null;
}

export interface UpdateAthleteRequest {
  firstName: string;
  lastName: string;
  birthYear: number;
  gender: Gender;
}

export type AthleteFormMode = 'CREATE' | 'EDIT';
