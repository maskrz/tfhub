import { Routes } from '@angular/router';
import { TestManagementPage } from './features/tests/test-management.page';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'tests' },
  { path: 'tests', component: TestManagementPage, title: 'Testy' },
  { path: '**', redirectTo: 'tests' },
];
