import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter, Router } from '@angular/router';
import { signal } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { AthleteResponse } from '../athletes/athlete.model';
import { AthletesApiService } from '../athletes/athletes-api.service';
import { TrainingGroup, TrainingGroupsApiService } from '../training-groups/training-groups-api.service';
import { TrainingCreatePage } from './training-create.page';
import { CreateTrainingRequest, CreateTrainingResponse } from './training.model';
import { TrainingsApiService } from './trainings-api.service';
import { MultiSelectDropdownComponent } from '../../shared/multi-select-dropdown.component';

const groups: TrainingGroup[] = [
  { id: 1, name: 'Sprint' },
  { id: 2, name: 'Średni dystans' },
];

const athletes: AthleteResponse[] = [
  { id: 10, firstName: 'Jan', lastName: 'Kowalski', birthYear: 2010, gender: 'MALE', active: true, trainingGroupId: 1 },
  { id: 11, firstName: 'Anna', lastName: 'Nowak', birthYear: 2011, gender: 'FEMALE', active: true, trainingGroupId: 1 },
  { id: 12, firstName: 'Piotr', lastName: 'Wiśniewski', birthYear: 2009, gender: 'MALE', active: true, trainingGroupId: 2 },
];

describe('TrainingCreatePage', () => {
  let fixture: ComponentFixture<TrainingCreatePage>;
  let groupsApi: { getAccessible: ReturnType<typeof vi.fn> };
  let athletesApi: { getAll: ReturnType<typeof vi.fn> };
  let trainingsApi: { create: ReturnType<typeof vi.fn> };

  async function createPage(
    athleteLoader: () => Observable<AthleteResponse[]> = () => of(athletes),
    createLoader: (request: CreateTrainingRequest) => Observable<CreateTrainingResponse> = () => of({ id: 77 }),
  ): Promise<void> {
    groupsApi = { getAccessible: vi.fn(() => of(groups)) };
    athletesApi = { getAll: vi.fn(athleteLoader) };
    trainingsApi = { create: vi.fn(createLoader) };
    await TestBed.configureTestingModule({
      imports: [TrainingCreatePage],
      providers: [
        provideRouter([]),
        { provide: TrainingGroupsApiService, useValue: groupsApi },
        { provide: AthletesApiService, useValue: athletesApi },
        { provide: TrainingsApiService, useValue: trainingsApi },
        {
          provide: AuthService,
          useValue: {
            currentUser: signal({
              authenticated: true,
              userId: 1,
              username: 'coach',
              role: 'COACH',
              athleteId: null,
            }),
          },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(TrainingCreatePage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function setInput(selector: string, value: string): void {
    const input = fixture.nativeElement.querySelector(selector) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function selectGroup(groupId: number): void {
    const select = fixture.nativeElement.querySelector('#training-group') as HTMLSelectElement;
    select.value = String(groupId);
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function fillRequiredFields(): void {
    setInput('#training-name', 'Trening szybkościowy');
    setInput('#training-date', '2026-11-11');
    selectGroup(1);
    fixture.detectChanges();
  }

  function enterDefinition(definitionIndex: number, name: string, distance: string): void {
    const definition = fixture.nativeElement.querySelectorAll('.definition-card')[definitionIndex] as HTMLElement;
    const nameInput = definition.querySelector('input[id^="definition-name-"]') as HTMLInputElement;
    nameInput.value = name;
    nameInput.dispatchEvent(new Event('input'));
    const distanceInput = definition.querySelector('input[id^="segment-distance-"]') as HTMLInputElement;
    distanceInput.value = distance;
    distanceInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function chooseAthlete(definitionIndex: number, optionIndex: number): void {
    const picker = fixture.debugElement.queryAll(By.directive(MultiSelectDropdownComponent))[definitionIndex]
      .componentInstance as MultiSelectDropdownComponent;
    picker.selectedIdsChange.emit([picker.options()[optionIndex].id]);
    fixture.detectChanges();
  }

  it('validates required training fields, definitions, and segment distance', async () => {
    await createPage();
    (fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Wpisz nazwę treningu.');
    expect(fixture.nativeElement.textContent).toContain('Wybierz prawidłową datę.');
    expect(fixture.nativeElement.textContent).toContain('Wybierz grupę treningową.');
    expect(fixture.nativeElement.textContent).toContain('Wpisz nazwę podgrupy.');
    expect(fixture.nativeElement.textContent).toContain('Wpisz dystans.');
    expect(trainingsApi.create).not.toHaveBeenCalled();
  });

  it('rejects distances and rest values outside the backend integer range', async () => {
    await createPage();
    fillRequiredFields();
    await fixture.whenStable();
    fixture.detectChanges();
    enterDefinition(0, 'Główna', '2147483648');
    const restInput = fixture.nativeElement.querySelector('input[id^="segment-rest-"]') as HTMLInputElement;
    restInput.value = '-1';
    restInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Dystans musi być dodatnią liczbą całkowitą.');
    expect(fixture.nativeElement.textContent).toContain('Przerwa musi być nieujemną liczbą całkowitą.');
    expect(trainingsApi.create).not.toHaveBeenCalled();
  });

  it('supports adding, removing, and reordering definitions and segments', async () => {
    await createPage();
    const page = fixture.componentInstance;

    page.addDefinition();
    page.addSegment(0);
    page.addSegment(0);
    page.moveSegment(0, 2, -1);
    page.removeSegment(0, 1);
    page.moveDefinition(1, -1);
    fixture.detectChanges();

    expect(page.definitions.length).toBe(2);
    expect(page.definitions.at(0).controls.segments.length).toBe(1);
    expect(page.definitions.at(1).controls.segments.length).toBe(2);
    page.removeDefinition(1);
    expect(page.definitions.length).toBe(1);
  });

  it('loads athletes for the selected group and prevents duplicate assignments across definitions', async () => {
    await createPage();
    fillRequiredFields();
    await fixture.whenStable();
    fixture.detectChanges();
    fixture.componentInstance.addDefinition();
    fixture.detectChanges();
    chooseAthlete(0, 0);

    const secondPicker = fixture.debugElement.queryAll(By.directive(MultiSelectDropdownComponent))[1]
      .componentInstance as MultiSelectDropdownComponent;
    const secondOptions = secondPicker.options().map((option) => option.name);
    expect(secondOptions).toEqual(['Anna Nowak']);
    expect(secondPicker.searchable()).toBe(true);
    chooseAthlete(1, 0);

    expect(fixture.componentInstance.definitions.controls.map((definition) => definition.controls.athleteIds.value)).toEqual([
      [10],
      [11],
    ]);
    expect(athletesApi.getAll).toHaveBeenCalledTimes(1);
  });

  it('refreshes group-dependent athletes and clears assignments when the group changes', async () => {
    await createPage();
    fillRequiredFields();
    await fixture.whenStable();
    fixture.detectChanges();
    chooseAthlete(0, 0);

    selectGroup(2);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.componentInstance.definitions.at(0).controls.athleteIds.value).toEqual([]);
    expect(fixture.componentInstance.athletes().map((athlete) => athlete.id)).toEqual([12]);
    expect(athletesApi.getAll).toHaveBeenCalledTimes(2);
  });

  it('submits the complete ordered UC-502 request and navigates to the created training', async () => {
    await createPage();
    fillRequiredFields();
    await fixture.whenStable();
    fixture.detectChanges();
    enterDefinition(0, 'Główna', '400');
    const restInput = fixture.nativeElement.querySelector('input[id^="segment-rest-"]') as HTMLInputElement;
    restInput.value = '90';
    restInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    (fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(trainingsApi.create).toHaveBeenCalledWith({
      name: 'Trening szybkościowy',
      trainingDate: '2026-11-11',
      trainingGroupId: 1,
      definitions: [{
        name: 'Główna',
        sequenceNumber: 1,
        segments: [{ sequenceNumber: 1, distanceM: 400, restAfterSeconds: 90 }],
        athleteIds: [],
      }],
    });
    expect(navigate).toHaveBeenCalledWith(['/trainings', 77]);
  });

  it('preserves entered form data and reports failed creation', async () => {
    await createPage(() => of(athletes), () =>
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );
    fillRequiredFields();
    await fixture.whenStable();
    fixture.detectChanges();
    enterDefinition(0, 'Główna', '200');
    (fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('#training-name').value).toBe('Trening szybkościowy');
    expect(fixture.nativeElement.querySelector('input[id^="definition-name-"]').value).toBe('Główna');
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie udało się utworzyć treningu',
    );
  });
});
