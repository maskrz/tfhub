import { AfterViewInit, Component, ElementRef, EventEmitter, Input, Output, ViewChild, signal } from '@angular/core';
import { AthleteResponse } from '../athletes/athlete.model';
import { TestResponse } from '../tests/test.model';
import { CreateMultipleResultsResponse } from './result.model';
import { MultipleResultsFormComponent } from './multiple-results-form.component';

@Component({
  imports: [MultipleResultsFormComponent],
  selector: 'app-multiple-results-form-modal',
  templateUrl: './multiple-results-form-modal.component.html',
  styleUrl: './multiple-results-form-modal.component.scss',
})
export class MultipleResultsFormModalComponent implements AfterViewInit {
  @Input({ required: true }) athletes: AthleteResponse[] = [];
  @Input({ required: true }) tests: TestResponse[] = [];
  @Output() saved = new EventEmitter<CreateMultipleResultsResponse>();
  @Output() cancelled = new EventEmitter<void>();
  @ViewChild('dialog') private dialog!: ElementRef<HTMLDialogElement>;

  readonly saving = signal(false);

  ngAfterViewInit(): void {
    const dialog = this.dialog.nativeElement;
    if (typeof dialog.showModal === 'function') {
      dialog.showModal();
    } else {
      dialog.setAttribute('open', '');
    }
  }

  onSaved(response: CreateMultipleResultsResponse): void {
    this.closeDialog();
    this.saved.emit(response);
  }

  cancel(): void {
    if (this.saving()) return;
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
}
