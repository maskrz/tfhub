import { TestBed } from '@angular/core/testing';
import { SearchableSelectDropdownComponent } from './searchable-select-dropdown.component';

describe('SearchableSelectDropdownComponent', () => {
  it('exposes searchable buttons and consumes Escape only while open', () => {
    const fixture = TestBed.createComponent(SearchableSelectDropdownComponent);
    fixture.componentRef.setInput('options', [{ id: 1, name: 'Anna' }, { id: 2, name: 'Jan' }]);
    fixture.componentRef.setInput('selectedId', 1);
    fixture.componentRef.setInput('label', 'Zawodnik');
    fixture.componentRef.setInput('controlId', 'athlete-options');
    fixture.detectChanges();

    const trigger = fixture.nativeElement.querySelector('.trigger') as HTMLButtonElement;
    trigger.click();
    fixture.detectChanges();
    const options = fixture.nativeElement.querySelectorAll('.option') as NodeListOf<HTMLButtonElement>;
    expect(options[0].getAttribute('aria-pressed')).toBe('true');
    expect(options[1].getAttribute('aria-pressed')).toBe('false');
    const escape = new KeyboardEvent('keydown', { key: 'Escape', cancelable: true });
    document.dispatchEvent(escape);
    fixture.detectChanges();
    expect(escape.defaultPrevented).toBe(true);
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
    const closedEscape = new KeyboardEvent('keydown', { key: 'Escape', cancelable: true });
    document.dispatchEvent(closedEscape);
    expect(closedEscape.defaultPrevented).toBe(false);
  });
});
