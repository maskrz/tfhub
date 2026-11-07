export type Gender = 'MALE' | 'FEMALE';

export interface AthleteResponse {
  id: number;
  firstName: string;
  lastName: string;
  birthYear: number;
  gender: Gender;
  active: boolean;
}

export interface ChangeAthleteStatusRequest {
  active: boolean;
}

export interface CreateAthleteRequest {
  firstName: string;
  lastName: string;
  birthYear: number;
  gender: Gender;
}

export interface UpdateAthleteRequest {
  firstName: string;
  lastName: string;
  birthYear: number;
  gender: Gender;
}

export type AthleteFormMode = 'CREATE' | 'EDIT';
