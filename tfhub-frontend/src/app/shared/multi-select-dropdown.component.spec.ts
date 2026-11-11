import { TestBed } from '@angular/core/testing';
import { MultiSelectDropdownComponent } from './multi-select-dropdown.component';

describe('MultiSelectDropdownComponent', () => {
  it('opens a checkbox dropdown and emits selected options', async () => {
    await TestBed.configureTestingModule({
      imports: [MultiSelectDropdownComponent],
    }).compileComponents();

    const fixture = TestBed.createComponent(MultiSelectDropdownComponent);
    fixture.componentRef.setInput('options', [
      { id: 1, name: 'Grupa 1' },
      { id: 2, name: 'Grupa 2' },
    ]);
    fixture.componentRef.setInput('selectedIds', []);
    fixture.componentRef.setInput('label', 'Grupy treningowe');
    fixture.componentRef.setInput('controlId', 'group-options');
    const selection = vi.fn();
    fixture.componentInstance.selectedIdsChange.subscribe(selection);
    fixture.detectChanges();

    const trigger = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    trigger.click();
    fixture.detectChanges();
    expect(trigger.getAttribute('aria-expanded')).toBe('true');
    expect(fixture.nativeElement.textContent).toContain('Wszystkie przypisane grupy');

    const checkbox = fixture.nativeElement.querySelector('input[type="checkbox"]') as HTMLInputElement;
    Object.defineProperty(checkbox, 'checked', { value: true, configurable: true });
    checkbox.dispatchEvent(new Event('change'));

    expect(selection).toHaveBeenCalledWith([1]);
  });

  it('lists selected groups and emits an empty selection when cleared', async () => {
    await TestBed.configureTestingModule({
      imports: [MultiSelectDropdownComponent],
    }).compileComponents();

    const fixture = TestBed.createComponent(MultiSelectDropdownComponent);
    fixture.componentRef.setInput('options', [
      { id: 1, name: 'Grupa 1' },
      { id: 2, name: 'Grupa 2' },
      { id: 3, name: 'Grupa 3' },
    ]);
    fixture.componentRef.setInput('selectedIds', [1, 2, 3]);
    fixture.componentRef.setInput('label', 'Grupy treningowe');
    fixture.componentRef.setInput('controlId', 'group-options');
    const selection = vi.fn();
    fixture.componentInstance.selectedIdsChange.subscribe(selection);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Grupa 1, Grupa 2, Grupa 3');
    fixture.nativeElement.querySelector('button').click();
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.clear') as HTMLButtonElement).click();

    expect(selection).toHaveBeenCalledWith([]);
  });

  it('lists up to ten selected options and summarizes larger selections', async () => {
    await TestBed.configureTestingModule({
      imports: [MultiSelectDropdownComponent],
    }).compileComponents();

    const fixture = TestBed.createComponent(MultiSelectDropdownComponent);
    fixture.componentRef.setInput('options', Array.from({ length: 11 }, (_, index) => ({
      id: index + 1,
      name: `Zawodnik ${index + 1}`,
    })));
    fixture.componentRef.setInput('selectedIds', Array.from({ length: 10 }, (_, index) => index + 1));
    fixture.componentRef.setInput('label', 'Zawodnicy');
    fixture.componentRef.setInput('controlId', 'athlete-options');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'Zawodnik 1, Zawodnik 2, Zawodnik 3, Zawodnik 4, Zawodnik 5, Zawodnik 6, Zawodnik 7, Zawodnik 8, Zawodnik 9, Zawodnik 10',
    );

    fixture.componentRef.setInput('selectedIds', Array.from({ length: 11 }, (_, index) => index + 1));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Wybrano: 11');
  });

  it('filters searchable options by name and restores all options when closed', async () => {
    await TestBed.configureTestingModule({
      imports: [MultiSelectDropdownComponent],
    }).compileComponents();

    const fixture = TestBed.createComponent(MultiSelectDropdownComponent);
    fixture.componentRef.setInput('options', [
      { id: 1, name: 'Jan Kowalski' },
      { id: 2, name: 'Anna Nowak' },
    ]);
    fixture.componentRef.setInput('selectedIds', []);
    fixture.componentRef.setInput('label', 'Zawodnicy');
    fixture.componentRef.setInput('controlId', 'athlete-options');
    fixture.componentRef.setInput('searchable', true);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('button').click();
    fixture.detectChanges();
    const search = fixture.nativeElement.querySelector('input[type="search"]') as HTMLInputElement;
    search.value = 'anna';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.option')).toHaveLength(1);
    expect(fixture.nativeElement.textContent).toContain('Anna Nowak');
    expect(fixture.nativeElement.textContent).not.toContain('Jan Kowalski');

    fixture.nativeElement.querySelector('button.trigger').click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('button.trigger').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.option')).toHaveLength(2);
  });
});
