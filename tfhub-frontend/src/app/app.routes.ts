import { Routes } from '@angular/router';
import { AthletesPage } from './features/athletes/athletes.page';
import { AthleteProfilePage } from './features/athletes/athlete-profile.page';
import { DashboardPage } from './features/dashboard/dashboard.page';
import { ResultsPage } from './features/results/results.page';
import { TestManagementPage } from './features/tests/test-management.page';
import { adminGuard, authenticatedGuard, homeGuard, staffGuard } from './core/auth/auth.guards';
import { LoginPage } from './core/auth/login.page';

export const routes: Routes = [
  { path: '', pathMatch: 'full', canActivate: [homeGuard], component: DashboardPage },
  { path: 'login', component: LoginPage, title: 'Logowanie' },
  { path: 'dashboard', component: DashboardPage, canActivate: [staffGuard], title: 'Panel główny' },
  { path: 'tests', component: TestManagementPage, canActivate: [adminGuard], title: 'Testy' },
  {
    path: 'accounts',
    loadComponent: () => import('./features/accounts/accounts.page').then((module) => module.AccountsPage),
    canActivate: [adminGuard],
    title: 'Zarządzanie kontami',
  },
  { path: 'athletes/:id', component: AthleteProfilePage, canActivate: [authenticatedGuard], title: 'Profil zawodnika' },
  { path: 'athletes', component: AthletesPage, canActivate: [staffGuard], title: 'Zawodnicy' },
  { path: 'results', component: ResultsPage, canActivate: [staffGuard], title: 'Wyniki' },
  {
    path: 'trainings/new',
    loadComponent: () => import('./features/trainings/training-create.page').then((module) => module.TrainingCreatePage),
    canActivate: [staffGuard],
    title: 'Dodaj trening',
  },
  {
    path: 'trainings',
    loadComponent: () => import('./features/trainings/trainings.page').then((module) => module.TrainingsPage),
    canActivate: [staffGuard],
    title: 'Treningi',
  },
  { path: '**', redirectTo: 'dashboard' },
];
