import { HttpErrorResponse } from '@angular/common/http';
import { AfterViewInit, Component, ElementRef, EventEmitter, Input, Output, ViewChild, inject, signal } from '@angular/core';
import { EMPTY, catchError, finalize, take } from 'rxjs';
import { TrainingsApiService } from './trainings-api.service';

@Component({
  selector: 'app-training-deactivate-confirmation',
  templateUrl: './training-deactivate-confirmation.component.html',
  styleUrl: './training-deactivate-confirmation.component.scss',
})
export class TrainingDeactivateConfirmationComponent implements AfterViewInit {
  @Input({ required: true }) trainingId!: number;
  @Input({ required: true }) trainingName!: string;
  @Output() deactivated = new EventEmitter<number>();
  @Output() cancelled = new EventEmitter<void>();
  @ViewChild('dialog') private dialog!: ElementRef<HTMLDialogElement>;

  readonly deactivating = signal(false);
  readonly errorMessage = signal<string | null>(null);

  private readonly api = inject(TrainingsApiService);

  ngAfterViewInit(): void {
    const dialog = this.dialog.nativeElement;
    if (typeof dialog.showModal === 'function') {
      dialog.showModal();
    } else {
      dialog.setAttribute('open', '');
    }
  }

  confirm(): void {
    if (this.deactivating()) return;

    this.errorMessage.set(null);
    this.deactivating.set(true);
    this.api.deactivate(this.trainingId).pipe(
      take(1),
      catchError((error: unknown) => {
        this.errorMessage.set(this.getErrorMessage(error));
        return EMPTY;
      }),
      finalize(() => this.deactivating.set(false)),
    ).subscribe((response) => {
      this.closeDialog();
      this.deactivated.emit(response.id);
    });
  }

  cancel(): void {
    if (this.deactivating()) return;
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
      return 'Brak dostępu do dezaktywacji tego treningu.';
    }
    if (error instanceof HttpErrorResponse && error.status === 404) {
      return 'Nie znaleziono treningu lub brak dostępu. Trening nie został zmieniony.';
    }
    if (error instanceof HttpErrorResponse && error.status === 409) {
      return 'Trening jest już nieaktywny. Lista nie została zmieniona.';
    }
    return 'Nie udało się dezaktywować treningu. Spróbuj ponownie.';
  }
}
