import { UserRole } from '../../core/auth/auth.model';

export interface AccountTrainingGroup {
  id: number;
  name: string;
}

export interface AccountAthlete {
  id: number;
  firstName: string;
  lastName: string;
  birthYear: number;
  trainingGroupId: number | null;
  trainingGroupName: string | null;
}

export interface AccountResponse {
  id: number;
  username: string;
  role: UserRole;
  active: boolean;
  trainingGroups: AccountTrainingGroup[];
  athlete: AccountAthlete | null;
}

export interface CreateAccountRequest {
  username: string;
  password: string;
  role: UserRole;
  trainingGroupIds: number[];
  athleteId: number | null;
}

export interface UpdateAccountRequest {
  username: string;
  role: UserRole;
  trainingGroupIds: number[];
  athleteId: number | null;
}

export interface ChangeAccountStatusRequest {
  active: boolean;
}

export interface ResetAccountPasswordRequest {
  newPassword: string;
}
