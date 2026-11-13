export type Gender = 'MALE' | 'FEMALE';

export interface AthleteResponse {
  id: number;
  firstName: string;
  lastName: string;
  birthYear: number;
  gender: Gender;
  active: boolean;
  trainingGroupId?: number | null;
  aliases?: string[];
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
  aliases?: string[];
}

export interface UpdateAthleteRequest {
  firstName: string;
  lastName: string;
  birthYear: number;
  gender: Gender;
  trainingGroupId?: number | null;
  aliases?: string[];
}

export type AthleteFormMode = 'CREATE' | 'EDIT';
