import { Component, ElementRef, HostListener, input, output, signal, viewChild } from '@angular/core';
import { MultiSelectOption } from './multi-select-dropdown.component';

@Component({
  selector: 'app-searchable-select-dropdown',
  template: `
    <div class="select">
      <button
        #trigger
        class="field-control select-with-arrow trigger"
        type="button"
        [attr.aria-label]="label()"
        [attr.aria-invalid]="ariaInvalid() ? 'true' : null"
        [attr.aria-describedby]="describedBy()"
        aria-haspopup="listbox"
        [attr.aria-expanded]="open()"
        [attr.aria-controls]="controlId()"
        (click)="toggle()"
      >
        <span>{{ selectedName() || placeholder() }}</span>
      </button>
      @if (open()) {
        <div class="options" [id]="controlId()" role="listbox" [attr.aria-label]="label()">
          <input
            #search
            class="field-control search"
            type="search"
            [attr.aria-label]="'Szukaj: ' + label()"
            placeholder="Wpisz, aby filtrować..."
            [value]="query()"
            (input)="onSearch($event)"
          />
          @for (option of filteredOptions(); track option.id) {
            <button
              class="option"
              type="button"
              role="option"
              [attr.aria-selected]="selectedId() === option.id"
              (click)="select(option.id)"
            >
              {{ option.name }}
            </button>
          } @empty {
            <p class="empty">Brak pasujących pozycji.</p>
          }
        </div>
      }
    </div>
  `,
  styles: `
    :host { display: block; min-width: 13rem; }
    .select { position: relative; }
    .trigger {
      cursor: pointer;
      text-align: left;
    }
    .trigger span {
      display: block;
      overflow: hidden;
      padding-right: 1.5rem;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .options {
      background: #fff;
      border: 1px solid #d6dbe2;
      border-radius: .55rem;
      box-shadow: 0 .5rem 1.4rem #17203324;
      display: grid;
      gap: .25rem;
      left: 0;
      max-height: 18rem;
      min-width: 100%;
      overflow-y: auto;
      padding: .4rem;
      position: absolute;
      top: calc(100% + .35rem);
      z-index: 20;
    }
    .search { height: 2.75rem; margin-bottom: .2rem; }
    .option {
      background: transparent;
      border: 0;
      border-radius: .35rem;
      color: #172033;
      cursor: pointer;
      font: inherit;
      min-height: 2.6rem;
      padding: .55rem;
      text-align: left;
    }
    .option:hover, .option[aria-selected='true'] { background: #f2f4f8; }
    .option:focus-visible, .trigger:focus-visible {
      outline: 3px solid #159c68;
      outline-offset: 2px;
    }
    .empty { color: #59667e; margin: 0; padding: .7rem .55rem; }
  `,
})
export class SearchableSelectDropdownComponent {
  readonly options = input.required<MultiSelectOption[]>();
  readonly selectedId = input<number | null>(null);
  readonly label = input.required<string>();
  readonly controlId = input.required<string>();
  readonly placeholder = input('Wybierz pozycję');
  readonly ariaInvalid = input(false);
  readonly describedBy = input<string | null>(null);
  readonly selectedIdChange = output<number>();
  readonly open = signal(false);
  readonly query = signal('');
  private readonly trigger = viewChild<ElementRef<HTMLButtonElement>>('trigger');

  filteredOptions(): MultiSelectOption[] {
    const query = this.query().trim().toLocaleLowerCase('pl');
    return this.options().filter((option) => option.name.toLocaleLowerCase('pl').includes(query));
  }

  selectedName(): string {
    return this.options().find((option) => option.id === this.selectedId())?.name ?? '';
  }

  toggle(): void {
    this.open.update((value) => !value);
    if (this.open()) {
      this.query.set('');
    }
  }

  onSearch(event: Event): void {
    const input = event.target;
    if (input instanceof HTMLInputElement) this.query.set(input.value);
  }

  select(id: number): void {
    this.selectedIdChange.emit(id);
    this.open.set(false);
    this.query.set('');
    this.trigger()?.nativeElement.focus();
  }

  @HostListener('document:click', ['$event'])
  closeOnOutsideClick(event: MouseEvent): void {
    const target = event.target;
    if (target instanceof Node && !this.trigger()?.nativeElement.parentElement?.contains(target)) {
      this.open.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  closeOnEscape(): void {
    if (!this.open()) return;
    this.open.set(false);
    this.query.set('');
    this.trigger()?.nativeElement.focus();
  }
}
