export type UserRole = 'ADMIN' | 'COACH' | 'ATHLETE';

export interface AuthenticatedUser {
  authenticated: true;
  userId: number;
  username: string;
  role: UserRole;
  athleteId: number | null;
}
