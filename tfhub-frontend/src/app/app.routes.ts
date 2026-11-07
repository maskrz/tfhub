import { Routes } from '@angular/router';
import { AthletesPage } from './features/athletes/athletes.page';
import { TestManagementPage } from './features/tests/test-management.page';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'tests' },
  { path: 'tests', component: TestManagementPage, title: 'Testy' },
  { path: 'athletes', component: AthletesPage, title: 'Zawodnicy' },
  { path: '**', redirectTo: 'tests' },
];
