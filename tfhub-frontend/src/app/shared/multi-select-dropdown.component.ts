import { Component, ElementRef, HostListener, input, output, signal, viewChild } from '@angular/core';

export interface MultiSelectOption {
  id: number;
  name: string;
}

@Component({
  selector: 'app-multi-select-dropdown',
  template: `
    <div class="multi-select">
      <button
        #trigger
        class="field-control select-with-arrow trigger"
        type="button"
        [attr.aria-expanded]="open()"
        [attr.aria-controls]="controlId()"
        (click)="toggleOpen()"
      >
        <span>{{ selectionSummary() }}</span>
      </button>
      @if (open()) {
        <div class="options" [id]="controlId()" role="group" [attr.aria-label]="label()">
          @if (searchable()) {
            <input
              class="field-control option-search"
              type="search"
              [attr.aria-label]="searchLabel()"
              [placeholder]="searchPlaceholder()"
              [value]="searchQuery()"
              (input)="onSearchInput($event)"
            />
          }
          @for (option of filteredOptions(); track option.id) {
            <label class="option">
              <input
                type="checkbox"
                [checked]="selectedIds().includes(option.id)"
                (change)="toggleOption(option.id, $event)"
              />
              <span>{{ option.name }}</span>
            </label>
          }
          @if (filteredOptions().length === 0) {
            <p class="no-options">{{ searchQuery() ? 'Nie znaleziono pasujących pozycji.' : 'Brak dostępnych pozycji.' }}</p>
          }
          @if (selectedIds().length > 0) {
            <button class="clear" type="button" (click)="clearSelection()">Wyczyść wybór</button>
          }
        </div>
      }
    </div>
  `,
  styles: `
    :host { display: block; min-width: 13rem; }
    .multi-select { position: relative; }
    .trigger {
      align-items: center;
      cursor: pointer;
      display: flex;
      justify-content: space-between;
      text-align: left;
      width: 100%;
    }
    .trigger:focus-visible, .option:focus-within, .clear:focus-visible {
      outline: 3px solid #159c68;
      outline-offset: 2px;
    }
    .options {
      background: #fff;
      border: 1px solid #d6dbe2;
      border-radius: .55rem;
      box-shadow: 0 .5rem 1.4rem #17203324;
      left: 0;
      max-height: 16rem;
      min-width: 100%;
      overflow-y: auto;
      padding: .35rem;
      position: absolute;
      top: calc(100% + .35rem);
      z-index: 20;
    }
    .option-search {
      margin: .25rem;
      position: sticky;
      top: 0;
      width: calc(100% - .5rem);
      z-index: 1;
    }
    .option {
      align-items: center;
      border-radius: .35rem;
      cursor: pointer;
      display: flex;
      gap: .65rem;
      min-height: 2.6rem;
      padding: .35rem .55rem;
      white-space: nowrap;
    }
    .option:hover { background: #f2f4f8; }
    .option input { accent-color: #198754; height: 1rem; width: 1rem; }
    .no-options { color: #59667e; font-size: .9rem; margin: .5rem; }
    .clear {
      background: transparent;
      border: 0;
      border-top: 1px solid #e1e4e9;
      color: #365b48;
      cursor: pointer;
      font: inherit;
      margin-top: .25rem;
      padding: .65rem .55rem .35rem;
      text-align: left;
      width: 100%;
    }
    .clear:hover { color: #123d29; }
  `,
})
export class MultiSelectDropdownComponent {
  readonly options = input.required<MultiSelectOption[]>();
  readonly selectedIds = input<number[]>([]);
  readonly label = input.required<string>();
  readonly controlId = input.required<string>();
  readonly emptySummary = input('Wszystkie przypisane grupy');
  readonly searchable = input(false);
  readonly searchLabel = input('Szukaj');
  readonly searchPlaceholder = input('Szukaj...');
  readonly selectedIdsChange = output<number[]>();
  readonly open = signal(false);
  readonly searchQuery = signal('');
  readonly filteredOptions = () => {
    const query = this.searchQuery().trim().toLocaleLowerCase('pl');
    return query
      ? this.options().filter((option) => option.name.toLocaleLowerCase('pl').includes(query))
      : this.options();
  };
  private readonly trigger = viewChild<ElementRef<HTMLButtonElement>>('trigger');

  selectionSummary(): string {
    const selected = this.options().filter((option) => this.selectedIds().includes(option.id));
    if (selected.length === 0) return this.emptySummary();
    if (selected.length <= 10) return selected.map((option) => option.name).join(', ');
    return `Wybrano: ${selected.length}`;
  }

  toggleOption(optionId: number, event: Event): void {
    const checkbox = event.target;
    if (!(checkbox instanceof HTMLInputElement)) return;
    const next = checkbox.checked
      ? [...this.selectedIds(), optionId]
      : this.selectedIds().filter((id) => id !== optionId);
    this.selectedIdsChange.emit(next);
  }

  clearSelection(): void {
    this.selectedIdsChange.emit([]);
  }

  onSearchInput(event: Event): void {
    const input = event.target;
    if (input instanceof HTMLInputElement) this.searchQuery.set(input.value);
  }

  toggleOpen(): void {
    if (this.open()) {
      this.close();
    } else {
      this.open.set(true);
    }
  }

  @HostListener('document:click', ['$event'])
  closeOnOutsideClick(event: MouseEvent): void {
    const element = this.trigger()?.nativeElement;
    const target = event.target;
    if (element && target instanceof Node && !element.parentElement?.contains(target)) {
      this.close();
    }
  }

  @HostListener('document:keydown.escape')
  closeOnEscape(): void {
    this.close();
    this.trigger()?.nativeElement.focus();
  }

  private close(): void {
    this.open.set(false);
    this.searchQuery.set('');
  }
}
