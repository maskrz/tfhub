import { HttpErrorResponse } from '@angular/common/http';
import { AfterViewInit, Component, ElementRef, EventEmitter, Input, Output, ViewChild, inject, signal } from '@angular/core';
import { EMPTY, catchError, finalize, take } from 'rxjs';
import { ResultsApiService } from './results-api.service';
import { ResultRow } from './result.model';

@Component({
  selector: 'app-delete-result-confirmation',
  templateUrl: './delete-result-confirmation.component.html',
  styleUrl: './delete-result-confirmation.component.scss',
})
export class DeleteResultConfirmationComponent implements AfterViewInit {
  @Input({ required: true }) result!: ResultRow;
  @Input({ required: true }) formattedDate!: string;
  @Input({ required: true }) formattedValue!: string;
  @Input({ required: true }) resultType!: string;
  @Output() deleted = new EventEmitter<number>();
  @Output() cancelled = new EventEmitter<void>();
  @ViewChild('dialog') private dialog!: ElementRef<HTMLDialogElement>;

  readonly deleting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  private readonly api = inject(ResultsApiService);

  ngAfterViewInit(): void {
    const dialog = this.dialog.nativeElement;
    if (typeof dialog.showModal === 'function') {
      dialog.showModal();
    } else {
      dialog.setAttribute('open', '');
    }
  }

  confirm(): void {
    if (this.deleting()) return;

    this.errorMessage.set(null);
    this.deleting.set(true);
    this.api
      .deleteResult(this.result.id)
      .pipe(
        take(1),
        catchError((error: unknown) => {
          this.errorMessage.set(this.getErrorMessage(error));
          return EMPTY;
        }),
        finalize(() => this.deleting.set(false)),
      )
      .subscribe(() => {
        this.closeDialog();
        this.deleted.emit(this.result.id);
      });
  }

  cancel(): void {
    if (this.deleting()) return;
    this.closeDialog();
    this.cancelled.emit();
  }

  onDialogCancel(event: Event): void {
    event.preventDefault();
    this.cancel();
  }

  private closeDialog(): void {
    const dialog = this.dialog.nativeElement;
    if (typeof dialog.close === 'function') {
      dialog.close();
    } else {
      dialog.removeAttribute('open');
    }
  }

  private getErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return 'Sesja wygasła lub użytkownik nie jest zalogowany.';
    }
    if (error instanceof HttpErrorResponse && error.status === 403) {
      return 'Brak dostępu do usunięcia wyniku.';
    }
    if (error instanceof HttpErrorResponse && error.status === 404) {
      return 'Nie znaleziono wyniku. Lista nie została zmieniona.';
    }
    return 'Nie udało się usunąć wyniku. Spróbuj ponownie.';
  }
}
