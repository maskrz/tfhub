import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { signal } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { AthletesApiService } from '../athletes/athletes-api.service';
import { TrainingGroup, TrainingGroupsApiService } from '../training-groups/training-groups-api.service';
import { TrainingCreatePage } from './training-create.page';
import { CreateTrainingRequest, CreateTrainingResponse } from './training.model';
import { TrainingsApiService } from './trainings-api.service';

const groups: TrainingGroup[] = [
  { id: 1, name: 'Sprint' },
  { id: 2, name: 'Średni dystans' },
];

describe('TrainingCreatePage', () => {
  let fixture: ComponentFixture<TrainingCreatePage>;
  let groupsApi: { getAccessible: ReturnType<typeof vi.fn> };
  let athletesApi: { getActive: ReturnType<typeof vi.fn> };
  let trainingsApi: {
    create: ReturnType<typeof vi.fn>;
    createFreeText: ReturnType<typeof vi.fn>;
  };

  async function createPage(
    createLoader: (request: CreateTrainingRequest) => Observable<CreateTrainingResponse> = () => of({ id: 77 }),
  ): Promise<void> {
    groupsApi = { getAccessible: vi.fn(() => of(groups)) };
    athletesApi = {
      getActive: vi.fn(() => of([
        { id: 1, firstName: 'Alex', lastName: 'Runner', birthYear: 2000, gender: 'MALE', active: true },
        { id: 2, firstName: 'Sam', lastName: 'Runner', birthYear: 2001, gender: 'MALE', active: true },
      ])),
    };
    trainingsApi = {
      create: vi.fn(createLoader),
      createFreeText: vi.fn(() => of({ id: 77 })),
    };
    await TestBed.configureTestingModule({
      imports: [TrainingCreatePage],
      providers: [
        provideRouter([]),
        { provide: AthletesApiService, useValue: athletesApi },
        { provide: TrainingGroupsApiService, useValue: groupsApi },
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

  function selectTrainingType(trainingType: 'TEMPO' | 'FREE_TEXT'): void {
    const select = fixture.nativeElement.querySelector('#training-type') as HTMLSelectElement;
    select.value = trainingType;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function selectAssignmentType(assignmentType: 'GROUP' | 'ATHLETES'): void {
    const select = fixture.nativeElement.querySelector('#training-assignment-type') as HTMLSelectElement;
    select.value = assignmentType;
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

  it('validates required training fields, definitions, and segment distance', async () => {
    await createPage();
    (fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Wpisz nazwę treningu.');
    expect(fixture.nativeElement.textContent).toContain('Data (opcjonalnie)');
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
    restInput.value = '35791395';
    restInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Dystans musi być dodatnią liczbą całkowitą.');
    expect(fixture.nativeElement.textContent).toContain('Podaj minuty lub sekundy');
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

  it('submits the complete ordered UC-502 request and navigates to the created training', async () => {
    await createPage();
    fillRequiredFields();
    await fixture.whenStable();
    fixture.detectChanges();
    enterDefinition(0, 'Główna', '400');
    const restInput = fixture.nativeElement.querySelector('input[id^="segment-rest-"]') as HTMLInputElement;
    restInput.value = '1,5';
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
      }],
    });
    expect(navigate).toHaveBeenCalledWith(['/trainings', 77]);
  });

  it('converts plain minute values and explicit second values to seconds', async () => {
    await createPage();
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fillRequiredFields();
    await fixture.whenStable();
    fixture.detectChanges();
    enterDefinition(0, 'Główna', '400');

    const page = fixture.componentInstance;
    page.definitions.at(0).controls.segments.at(0).controls.restAfterSeconds.setValue('5');
    page.addSegment(0);
    page.definitions.at(0).controls.segments.at(1).controls.distanceM.setValue(200);
    page.definitions.at(0).controls.segments.at(1).controls.restAfterSeconds.setValue('45s');
    page.submit();

    const request = trainingsApi.create.mock.calls[0][0] as CreateTrainingRequest;
    expect(request.definitions[0].segments.map((segment) => segment.restAfterSeconds)).toEqual([300, 45]);
  });

  it('creates ordered FREE_TEXT sections without requiring tempo definitions', async () => {
    await createPage();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fillRequiredFields();
    selectTrainingType('FREE_TEXT');
    const page = fixture.componentInstance;
    page.sections.at(0).controls.title.setValue('Rozgrzewka');
    page.sections.at(0).controls.content.setValue('Trucht 10 minut');
    page.addSection();
    page.sections.at(1).controls.title.setValue('Część główna');
    page.sections.at(1).controls.content.setValue('Skip A 3 × 30 m');
    page.form.controls.equipment.setValue('Piłka\nGumy');
    page.moveSection(1, -1);
    page.submit();
    await fixture.whenStable();

    expect(trainingsApi.create).not.toHaveBeenCalled();
    expect(trainingsApi.createFreeText).toHaveBeenCalledWith({
      name: 'Trening szybkościowy',
      trainingDate: '2026-11-11',
      assignmentType: 'GROUP',
      trainingGroupId: 1,
      athleteIds: [],
      equipment: 'Piłka\nGumy',
      sections: [
        { title: 'Część główna', content: 'Skip A 3 × 30 m', sequenceNumber: 1 },
        { title: 'Rozgrzewka', content: 'Trucht 10 minut', sequenceNumber: 2 },
      ],
    });
    expect(navigate).toHaveBeenCalledWith(['/trainings', 77]);
  });

  it('creates an undated TEMPO training', async () => {
    await createPage();
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fillRequiredFields();
    setInput('#training-date', '');
    enterDefinition(0, 'Główna', '400');
    fixture.componentInstance.submit();
    expect(trainingsApi.create.mock.calls[0][0].trainingDate).toBeNull();
  });

  it('creates FREE_TEXT training for multiple selected athletes without a group', async () => {
    await createPage();
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    selectTrainingType('FREE_TEXT');
    selectAssignmentType('ATHLETES');
    const page = fixture.componentInstance;
    page.form.controls.name.setValue('Trening indywidualny');
    page.form.controls.trainingDate.setValue('2026-11-11');
    page.form.controls.athleteIds.setValue([1, 2]);
    page.sections.at(0).controls.title.setValue('Część główna');
    page.sections.at(0).controls.content.setValue('Ćwiczenia biegowe');

    page.submit();
    await fixture.whenStable();

    expect(trainingsApi.createFreeText).toHaveBeenCalledWith({
      name: 'Trening indywidualny',
      trainingDate: '2026-11-11',
      assignmentType: 'ATHLETES',
      trainingGroupId: null,
      athleteIds: [1, 2],
      equipment: null,
      sections: [{ title: 'Część główna', content: 'Ćwiczenia biegowe', sequenceNumber: 1 }],
    });
  });

  it('preserves entered form data and reports failed creation', async () => {
    await createPage(() => throwError(() => new HttpErrorResponse({ status: 500 })));
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
