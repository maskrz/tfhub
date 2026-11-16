import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { CompetitionApiService } from './competition-api.service';
import { CompetitionEventsPage } from './competition-events.page';
import { CompetitionEvent } from './competition.model';

describe('CompetitionEventsPage', () => {
  let fixture: ComponentFixture<CompetitionEventsPage>;
  let api: {
    getEvents: ReturnType<typeof vi.fn>;
    changeEventStatus: ReturnType<typeof vi.fn>;
  };
  const events: CompetitionEvent[] = [
    { id: 1, name: 'Skok w dal', unit: 'm', active: true },
    { id: 2, name: '100 m', unit: 's', active: true },
    { id: 3, name: 'Rzut dyskiem', unit: 'm', active: false },
  ];

  beforeEach(async () => {
    api = {
      getEvents: vi.fn(() => of(events)),
      changeEventStatus: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [CompetitionEventsPage],
      providers: [{ provide: CompetitionApiService, useValue: api }],
    }).compileComponents();
    fixture = TestBed.createComponent(CompetitionEventsPage);
    fixture.detectChanges();
  });

  it('filters and sorts locally, keeping inactive events hidden by default', () => {
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelectorAll('tbody tr')).toHaveLength(2);
    expect(root.querySelector('tbody')?.textContent).not.toContain('Rzut dyskiem');
    expect(root.querySelectorAll('thead th')).toHaveLength(2);
    const inactive = root.querySelector('.inactive-toggle input') as HTMLInputElement;
    inactive.checked = true;
    inactive.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(root.querySelectorAll('tbody tr')).toHaveLength(3);
    expect(root.querySelector('tr.inactive-row')?.textContent).toContain('Rzut dyskiem');
    (root.querySelector('.sort-button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(root.querySelector('tbody tr strong')?.textContent).toBe('Skok w dal');
    const search = root.querySelector('input[type="search"]') as HTMLInputElement;
    search.value = ' RZUT ';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(root.querySelectorAll('tbody tr')).toHaveLength(1);
    expect(root.querySelector('tbody')?.textContent).toContain('Rzut dyskiem');
    expect(api.getEvents).toHaveBeenCalledOnce();
  });

  it('updates status with an accessible switch and handles failure', () => {
    const response = new Subject<CompetitionEvent>();
    api.changeEventStatus.mockReturnValueOnce(response);
    const button = fixture.nativeElement.querySelector('[role="switch"]') as HTMLButtonElement;
    expect(button.getAttribute('aria-checked')).toBe('true');
    button.click();
    fixture.detectChanges();
    expect(button.disabled).toBe(true);
    expect(api.changeEventStatus).toHaveBeenCalledWith(2, { active: false });
    response.next({ ...events[1], active: false });
    response.complete();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(1);

    api.changeEventStatus.mockReturnValueOnce(throwError(() => new Error('failure')));
    (fixture.nativeElement.querySelector('[role="switch"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent)
      .toContain('Nie udało się zmienić statusu konkurencji.');
  });

  it('opens the event editor from the row', () => {
    (fixture.nativeElement.querySelector('.action-cell .button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.componentInstance.selectedEvent()?.id).toBe(2);
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeTruthy();
  });
});
