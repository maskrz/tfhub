import { Component, EventEmitter, Input, Output, forwardRef, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

export interface SearchableSelectOption {
  value: string;
  label: string;
}

@Component({
  selector: 'app-searchable-select',
  templateUrl: './searchable-select.component.html',
  styleUrl: './searchable-select.component.scss',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => SearchableSelectComponent),
      multi: true,
    },
  ],
})
export class SearchableSelectComponent implements ControlValueAccessor {
  @Input({ required: true }) inputId = '';
  @Input({ required: true }) ariaLabel = '';
  @Input() placeholder = 'Wpisz, aby filtrować...';
  @Input() options: SearchableSelectOption[] = [];
  @Input() ariaInvalid: string | null = null;
  @Input() describedBy = '';
  @Input() unmatchedLabel = '';
  @Input() showAllOnFocus = false;
  @Output() selected = new EventEmitter<string>();

  readonly query = signal('');
  readonly selectedValue = signal('');
  readonly isOpen = signal(false);
  readonly activeIndex = signal(-1);
  readonly isDisabled = signal(false);

  private onChange: (value: string) => void = () => {};
  private onTouched: () => void = () => {};

  get inputValue(): string {
    if (this.selectedValue()) {
      return this.options.find((option) => option.value === this.selectedValue())?.label
        ?? (this.query() || (this.isOpen() ? '' : this.unmatchedLabel));
    }
    return this.query() || (this.isOpen() ? '' : this.unmatchedLabel);
  }

  get showUnmatched(): boolean {
    return !!this.unmatchedLabel && !this.isOpen()
      && !this.options.some((option) => option.value === this.selectedValue());
  }

  get filteredOptions(): SearchableSelectOption[] {
    const search = this.normalize(this.query());
    if (!search) return this.options;
    return this.options.filter((option) => this.normalize(option.label).includes(search));
  }

  writeValue(value: string | null): void {
    const previousValue = this.selectedValue();
    this.selectedValue.set(value ?? '');
    if (!value && previousValue) {
      this.query.set(this.showAllOnFocus ? '' : this.selectedValue()
        ? this.options.find((option) => option.value === this.selectedValue())?.label ?? ''
        : this.query());
    }
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.isDisabled.set(disabled);
  }

  onFocus(event: FocusEvent): void {
    if (this.isDisabled()) return;
    this.query.set('');
    this.isOpen.set(true);
    this.activeIndex.set(-1);
    (event.target as HTMLInputElement).select();
  }

  onInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.query.set(input.value);
    this.selectedValue.set('');
    this.onChange('');
    this.selected.emit('');
    this.isOpen.set(true);
    this.activeIndex.set(this.filteredOptions.length > 0 ? 0 : -1);
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      this.isOpen.set(true);
      const direction = event.key === 'ArrowDown' ? 1 : -1;
      const count = this.filteredOptions.length;
      if (count > 0) {
        this.activeIndex.update((index) => (index + direction + count) % count);
      }
    } else if (event.key === 'Enter' && this.isOpen() && this.activeIndex() >= 0) {
      event.preventDefault();
      const option = this.filteredOptions[this.activeIndex()];
      if (option) this.selectOption(option);
    } else if (event.key === 'Escape') {
      this.isOpen.set(false);
      this.activeIndex.set(-1);
    }
  }

  onFocusOut(event: FocusEvent): void {
    const container = event.currentTarget as HTMLElement;
    if (!event.relatedTarget || !container.contains(event.relatedTarget as Node)) {
      this.isOpen.set(false);
      this.activeIndex.set(-1);
      this.onTouched();
    }
  }

  selectOption(option: SearchableSelectOption): void {
    if (this.isDisabled()) return;
    this.selectedValue.set(option.value);
    this.query.set(option.label);
    this.isOpen.set(false);
    this.activeIndex.set(-1);
    this.onChange(option.value);
    this.onTouched();
    this.selected.emit(option.value);
  }

  optionId(index: number): string {
    return `${this.inputId}-option-${index}`;
  }

  private normalize(value: string): string {
    return value.trim().toLocaleLowerCase('pl-PL');
  }
}
