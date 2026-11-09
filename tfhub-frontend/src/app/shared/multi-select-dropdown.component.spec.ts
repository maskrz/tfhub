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

  it('summarizes selected groups and emits an empty selection when cleared', async () => {
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

    expect(fixture.nativeElement.textContent).toContain('Wybrano grup: 3');
    fixture.nativeElement.querySelector('button').click();
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.clear') as HTMLButtonElement).click();

    expect(selection).toHaveBeenCalledWith([]);
  });
});
