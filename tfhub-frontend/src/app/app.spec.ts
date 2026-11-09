import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { signal } from '@angular/core';
import { App } from './app';
import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { AuthenticatedUser } from './core/auth/auth.model';
import { AthletesApiService } from './features/athletes/athletes-api.service';
import { ResultsApiService } from './features/results/results-api.service';
import { StatisticsApiService } from './features/dashboard/statistics-api.service';
import { TestsApiService } from './features/tests/tests-api.service';

describe('App', () => {
  let currentUser: ReturnType<typeof signal<AuthenticatedUser | null>>;

  beforeEach(async () => {
    currentUser = signal({
      authenticated: true,
      userId: 1,
      username: 'admin',
      role: 'ADMIN',
      athleteId: null,
    });
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(routes),
        {
          provide: AuthService,
          useValue: {
            currentUser,
            ensureSession: () => of(currentUser()),
          },
        },
        {
          provide: TestsApiService,
          useValue: { getAll: () => of([]) },
        },
        {
          provide: AthletesApiService,
          useValue: {
            getAll: () => of([]),
            getById: (id: number) => of({
              id,
              firstName: 'Jan',
              lastName: 'Kowalski',
              birthYear: 2010,
              gender: 'MALE',
              active: true,
            }),
            getTestsWithResults: () => of([]),
            getResultHistory: () => of([]),
          },
        },
        {
          provide: ResultsApiService,
          useValue: { getAll: () => of([]) },
        },
        {
          provide: StatisticsApiService,
          useValue: {
            getDashboardOverview: () => of({
              activeAthleteCount: 0,
              availableTests: [],
              availableSeasons: [],
              recentResults: [],
            }),
            getClubRecords: () => of({ testId: 1, season: null, FEMALE: [], MALE: [] }),
          },
        },
      ],
    })
      .compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('renders the shared shell and dashboard by default', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await TestBed.inject(Router).navigateByUrl('/');
    await fixture.whenStable();
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-header')).toBeTruthy();
    expect(compiled.querySelector('app-footer')).toBeTruthy();
    expect(compiled.querySelector('h1')?.textContent).toContain('Panel główny');
    expect(compiled.querySelector('nav a[routerlink="/dashboard"]')).toBeTruthy();
  });

  it('renders the Athletes view inside the shared shell', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await TestBed.inject(Router).navigateByUrl('/athletes');
    await fixture.whenStable();
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-header')).toBeTruthy();
    expect(compiled.querySelector('app-footer')).toBeTruthy();
    expect(compiled.querySelector('h1')?.textContent).toContain('Zawodnicy');
    expect(compiled.querySelector('nav a[routerlink="/athletes"]')).toBeTruthy();
  });

  it('renders the Results view inside the shared shell', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await TestBed.inject(Router).navigateByUrl('/results');
    await fixture.whenStable();
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-header')).toBeTruthy();
    expect(compiled.querySelector('app-footer')).toBeTruthy();
    expect(compiled.querySelector('h1')?.textContent).toContain('Wyniki');
    expect(compiled.querySelector('nav a[routerlink="/results"]')).toBeTruthy();
  });

  it('sends an athlete from the home route to their own results profile', async () => {
    currentUser.set({
      authenticated: true,
      userId: 4,
      username: 'athlete1',
      role: 'ATHLETE',
      athleteId: 1,
    });
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/');
    await fixture.whenStable();
    fixture.detectChanges();

    expect(router.url).toBe('/athletes/1');
    expect(fixture.nativeElement.textContent).toContain('Jan Kowalski');
    expect(fixture.nativeElement.textContent).toContain('Moje wyniki');

    await router.navigateByUrl('/results');
    await fixture.whenStable();
    expect(router.url).toBe('/athletes/1');
  });
});
