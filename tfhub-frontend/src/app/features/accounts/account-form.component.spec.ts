import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject } from 'rxjs';
import { AthleteResponse } from '../athletes/athlete.model';
import { AthletesApiService } from '../athletes/athletes-api.service';
import { TrainingGroupsApiService } from '../training-groups/training-groups-api.service';
import { AccountResponse } from './account.model';
import { AccountsApiService } from './accounts-api.service';
import { AccountFormComponent } from './account-form.component';

describe('AccountFormComponent', () => {
  let fixture: ComponentFixture<AccountFormComponent>;
  let api: {
    createAccount: ReturnType<typeof vi.fn>;
    updateAccount: ReturnType<typeof vi.fn>;
  };
  const savedAccount: AccountResponse = {
    id: 8,
    username: 'nowe-konto',
    role: 'ADMIN',
    active: true,
    trainingGroups: [],
    athlete: null,
  };
  const athletes: AthleteResponse[] = [
    { id: 5, firstName: 'Anna', lastName: 'Nowak', birthYear: 2005, gender: 'FEMALE', active: true },
    { id: 6, firstName: 'Jan', lastName: 'Kowalski', birthYear: 2004, gender: 'MALE', active: true },
  ];

  beforeEach(async () => {
    api = {
      createAccount: vi.fn(() => of(savedAccount)),
      updateAccount: vi.fn(() => of(savedAccount)),
    };
    await TestBed.configureTestingModule({
      imports: [AccountFormComponent],
      providers: [
        { provide: AccountsApiService, useValue: api },
        { provide: TrainingGroupsApiService, useValue: { getAccessible: vi.fn(() => of([
          { id: 2, name: 'Sprint' }, { id: 3, name: 'Skok' },
        ])) } },
        { provide: AthletesApiService, useValue: { getAll: vi.fn(() => of(athletes)) } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AccountFormComponent);
    fixture.componentRef.setInput('mode', 'CREATE');
    fixture.detectChanges();
  });

  it('creates an ADMIN account using only common fields', () => {
    const saved = vi.fn();
    fixture.componentInstance.saved.subscribe(saved);
    fixture.componentInstance.form.controls.username.setValue(' admin ');
    fixture.componentInstance.form.controls.password.setValue('initial');
    fixture.componentInstance.submit();

    expect(api.createAccount).toHaveBeenCalledWith({
      username: 'admin',
      password: 'initial',
      role: 'ADMIN',
      trainingGroupIds: [],
      athleteId: null,
    });
    expect(saved).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.querySelector('#account-groups-label')).toBeNull();
    expect(fixture.nativeElement.querySelector('#account-athlete')).toBeNull();
  });

  it('shows role-dependent fields and creates a COACH with multiple groups', () => {
    const component = fixture.componentInstance;
    component.form.controls.username.setValue('coach');
    component.form.controls.password.setValue('initial');
    component.form.controls.role.setValue('COACH');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#account-groups-label')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#account-athlete')).toBeNull();

    component.onGroupIdsChange([2, 3]);
    component.submit();
    expect(api.createAccount).toHaveBeenCalledWith({
      username: 'coach',
      password: 'initial',
      role: 'COACH',
      trainingGroupIds: [2, 3],
      athleteId: null,
    });
  });

  it('creates an ATHLETE account with one athlete and clears stale group assignments', () => {
    const component = fixture.componentInstance;
    component.form.controls.username.setValue('athlete');
    component.form.controls.password.setValue('initial');
    component.form.controls.role.setValue('COACH');
    component.onGroupIdsChange([2]);
    component.form.controls.role.setValue('ATHLETE');
    fixture.detectChanges();

    expect(component.form.controls.trainingGroupIds.value).toEqual([]);
    expect(fixture.nativeElement.querySelector('#account-athlete')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#account-groups-label')).toBeNull();
    component.onAthleteIdChange(5);
    component.submit();

    expect(api.createAccount).toHaveBeenCalledWith({
      username: 'athlete',
      password: 'initial',
      role: 'ATHLETE',
      trainingGroupIds: [],
      athleteId: 5,
    });
  });

  it('filters athlete assignment options and selects one athlete in the searchable dropdown', () => {
    const component = fixture.componentInstance;
    component.form.controls.role.setValue('ATHLETE');
    fixture.detectChanges();

    const dropdown = fixture.nativeElement.querySelector('#account-athlete') as HTMLElement;
    (dropdown.querySelector('.trigger') as HTMLButtonElement).click();
    fixture.detectChanges();
    const search = dropdown.querySelector('input[type="search"]') as HTMLInputElement;
    search.value = 'kow';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(dropdown.querySelectorAll('[role="option"]')).toHaveLength(1);
    const option = dropdown.querySelector('[role="option"]') as HTMLButtonElement;
    expect(option.textContent).toContain('Jan Kowalski');
    option.click();
    fixture.detectChanges();

    expect(component.form.controls.athleteId.value).toBe(6);
    expect((dropdown.querySelector('.trigger') as HTMLButtonElement).textContent).toContain('Jan Kowalski');
  });

  it('loads edit values and submits role and assignment changes without a password', () => {
    const existing: AccountResponse = {
      id: 12,
      username: 'old-coach',
      role: 'COACH',
      active: false,
      trainingGroups: [{ id: 2, name: 'Sprint' }],
      athlete: null,
    };
    fixture.destroy();
    fixture = TestBed.createComponent(AccountFormComponent);
    fixture.componentRef.setInput('mode', 'EDIT');
    fixture.componentRef.setInput('account', existing);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    expect(component.form.controls.username.value).toBe('old-coach');
    expect(component.form.controls.trainingGroupIds.value).toEqual([2]);
    component.form.controls.role.setValue('ADMIN');
    component.submit();

    expect(api.updateAccount).toHaveBeenCalledWith(12, {
      username: 'old-coach',
      role: 'ADMIN',
      trainingGroupIds: [],
      athleteId: null,
    });
    expect(api.createAccount).not.toHaveBeenCalled();
  });

  it('does not submit invalid assignment data or send duplicate requests', () => {
    const component = fixture.componentInstance;
    component.form.controls.username.setValue('coach');
    component.form.controls.password.setValue('initial');
    component.form.controls.role.setValue('COACH');
    component.submit();
    expect(api.createAccount).not.toHaveBeenCalled();
    expect(component.fieldError('trainingGroupIds')).toContain('co najmniej jedną');

    const pending = new Subject<AccountResponse>();
    api.createAccount.mockReturnValue(pending);
    component.form.controls.role.setValue('ADMIN');
    component.submit();
    component.submit();
    expect(api.createAccount).toHaveBeenCalledOnce();
    expect(component.saving()).toBe(true);
    pending.next(savedAccount);
    pending.complete();
    expect(component.saving()).toBe(false);
  });
});
