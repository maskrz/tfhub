export type UserRole = 'ADMIN' | 'COACH' | 'ATHLETE';

export interface AuthenticatedUser {
  authenticated: true;
  userId: number;
  username: string;
  role: UserRole;
  athleteId: number | null;
}

export interface UserSession {
  id: string;
  createdAt: string;
  lastActivityAt: string;
  current: boolean;
}
