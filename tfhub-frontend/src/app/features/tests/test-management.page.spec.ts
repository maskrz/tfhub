import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { TestEditPendingComponent } from './test-edit-pending.component';
import { TestManagementPage } from './test-management.page';
import { TestResponse } from './test.model';
import { TestsApiService } from './tests-api.service';

const activeTest: TestResponse = {
  id: 1,
  name: 'Bieg na 40 m',
  unit: 's',
  category: 'TIME',
  higherIsBetter: false,
  active: true,
};

const inactiveTest: TestResponse = {
  id: 2,
  name: 'Skok w dal',
  unit: 'm',
  category: 'DISTANCE',
  higherIsBetter: true,
  active: false,
};

describe('TestManagementPage', () => {
  let fixture: ComponentFixture<TestManagementPage>;
  let api: { getAll: ReturnType<typeof vi.fn> };

  async function createPage(
    getAll: () => Observable<TestResponse[]> = () => of([activeTest, inactiveTest]),
  ): Promise<void> {
    api = { getAll: vi.fn(getAll) };
    await TestBed.configureTestingModule({
      imports: [TestManagementPage, TestEditPendingComponent],
      providers: [
        { provide: TestsApiService, useValue: api },
        provideRouter([
          { path: 'tests/:id/edit', component: TestEditPendingComponent },
          { path: 'tests', component: TestManagementPage },
        ]),
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(TestManagementPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('loads the complete list once and displays only active tests by default', async () => {
    await createPage();

    expect(api.getAll).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.textContent).toContain('Bieg na 40 m');
    expect(fixture.nativeElement.textContent).not.toContain('Skok w dal');
    expect(fixture.nativeElement.querySelectorAll('.edit-button')).toHaveLength(1);
  });

  it('includes inactive tests when enabled without another API request', async () => {
    await createPage();

    const toggle = fixture.nativeElement.querySelector('.inactive-toggle input') as HTMLInputElement;
    toggle.checked = true;
    toggle.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(api.getAll).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.textContent).toContain('Skok w dal');
    expect(fixture.nativeElement.querySelectorAll('.edit-button')).toHaveLength(2);
    expect(fixture.nativeElement.querySelectorAll('thead th')).toHaveLength(2);
    expect(fixture.nativeElement.textContent).not.toContain('Nieaktywny');
    expect(fixture.nativeElement.textContent).not.toContain('Kategoria');
    expect(fixture.nativeElement.textContent).not.toContain('Czas');
  });

  it('hides inactive tests again when disabled without another API request', async () => {
    await createPage();

    const toggle = fixture.nativeElement.querySelector('.inactive-toggle input') as HTMLInputElement;
    toggle.checked = true;
    toggle.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    await fixture.whenStable();
    toggle.checked = false;
    toggle.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(api.getAll).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.textContent).not.toContain('Skok w dal');
  });

  it('displays only the test name and edit action for each row', async () => {
    await createPage();

    const row = fixture.nativeElement.querySelector('tbody tr') as HTMLTableRowElement;
    expect(row.querySelectorAll('td')).toHaveLength(2);
    expect(row.cells[0].textContent.trim()).toBe('Bieg na 40 m');
    expect(row.querySelectorAll('.edit-button')).toHaveLength(1);
    expect(row.textContent).not.toContain('Czas');
    expect(row.textContent).not.toContain('Aktywny');
  });

  it('searches locally by name, ignoring case and surrounding whitespace', async () => {
    await createPage();

    const search = fixture.nativeElement.querySelector('input[type="search"]') as HTMLInputElement;
    search.value = '  BIEG  ';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('Bieg na 40 m');
    expect(fixture.nativeElement.textContent).not.toContain('Skok w dal');
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('combines local name search with inclusion of inactive tests', async () => {
    await createPage();

    const toggle = fixture.nativeElement.querySelector('.inactive-toggle input') as HTMLInputElement;
    toggle.checked = true;
    toggle.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    await fixture.whenStable();
    const search = fixture.nativeElement.querySelector('input[type="search"]') as HTMLInputElement;
    search.value = 'skok';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(api.getAll).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.textContent).toContain('Skok w dal');
  });

  it('clears local search and restores rows matching the current status filter', async () => {
    await createPage();
    const toggle = fixture.nativeElement.querySelector('.inactive-toggle input') as HTMLInputElement;
    toggle.checked = true;
    toggle.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    const search = fixture.nativeElement.querySelector('input[type="search"]') as HTMLInputElement;
    search.value = 'skok';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(1);
    search.value = '';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(2);
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('shows a loading state while the list request is pending', async () => {
    const pending = new Subject<TestResponse[]>();
    await createPage(() => pending);

    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain(
      'Ładowanie testów',
    );
    pending.next([activeTest]);
    pending.complete();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Bieg na 40 m');
  });

  it('distinguishes an empty list from no search results', async () => {
    await createPage(() => of([]));
    expect(fixture.nativeElement.textContent).toContain('Brak testów do wyświetlenia.');

    const search = fixture.nativeElement.querySelector('input[type="search"]') as HTMLInputElement;
    search.value = 'nieistniejący';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain(
      'Nie znaleziono testów pasujących do wyszukiwania.',
    );
  });

  it('shows an API error and retries the request', async () => {
    const result = vi
      .fn<() => Observable<TestResponse[]>>()
      .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })))
      .mockReturnValueOnce(of([activeTest]));
    await createPage(result);

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie udało się pobrać listy testów',
    );
    (fixture.nativeElement.querySelector('.retry-button') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(result).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent).toContain('Bieg na 40 m');
  });

  it.each([
    [401, 'Sesja wygasła lub użytkownik nie jest zalogowany.'],
    [403, 'Brak dostępu do listy testów.'],
  ])('shows the documented authentication response for HTTP %i', async (status, message) => {
    await createPage(() =>
      throwError(() => new HttpErrorResponse({ status: Number(status) })),
    );

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(message);
  });

  it('navigates to the planned edit route for the selected test', async () => {
    await createPage();

    (fixture.nativeElement.querySelector('.edit-button') as HTMLAnchorElement).click();
    await fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/tests/1/edit');
  });
});
