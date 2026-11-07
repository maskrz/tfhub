import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { App } from './app';
import { routes } from './app.routes';
import { AthletesApiService } from './features/athletes/athletes-api.service';
import { ResultsApiService } from './features/results/results-api.service';
import { TestsApiService } from './features/tests/tests-api.service';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(routes),
        {
          provide: TestsApiService,
          useValue: { getAll: () => of([]) },
        },
        {
          provide: AthletesApiService,
          useValue: { getAll: () => of([]) },
        },
        {
          provide: ResultsApiService,
          useValue: { getAll: () => of([]) },
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

  it('renders the shared shell and Test Management view', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await TestBed.inject(Router).navigateByUrl('/');
    await fixture.whenStable();
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-header')).toBeTruthy();
    expect(compiled.querySelector('app-footer')).toBeTruthy();
    expect(compiled.querySelector('h1')?.textContent).toContain('Testy');
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
});
