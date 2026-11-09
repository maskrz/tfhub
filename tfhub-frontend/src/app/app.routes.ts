import { Routes } from '@angular/router';
import { AthletesPage } from './features/athletes/athletes.page';
import { AthleteProfilePage } from './features/athletes/athlete-profile.page';
import { DashboardPage } from './features/dashboard/dashboard.page';
import { ResultsPage } from './features/results/results.page';
import { TestManagementPage } from './features/tests/test-management.page';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  { path: 'dashboard', component: DashboardPage, title: 'Panel główny' },
  { path: 'tests', component: TestManagementPage, title: 'Testy' },
  { path: 'athletes/:id', component: AthleteProfilePage, title: 'Profil zawodnika' },
  { path: 'athletes', component: AthletesPage, title: 'Zawodnicy' },
  { path: 'results', component: ResultsPage, title: 'Wyniki' },
  { path: '**', redirectTo: 'tests' },
];
