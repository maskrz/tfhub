import { Routes } from '@angular/router';
import { TestEditPendingComponent } from './features/tests/test-edit-pending.component';
import { TestManagementPage } from './features/tests/test-management.page';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'tests' },
  { path: 'tests', component: TestManagementPage, title: 'Testy' },
  { path: 'tests/:id/edit', component: TestEditPendingComponent, title: 'Edycja testu' },
  { path: '**', redirectTo: 'tests' },
];
