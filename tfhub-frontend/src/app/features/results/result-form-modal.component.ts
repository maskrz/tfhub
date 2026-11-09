import { AfterViewInit, Component, ElementRef, EventEmitter, Input, Output, ViewChild, signal } from '@angular/core';
import { AthleteResponse } from '../athletes/athlete.model';
import { TestResponse } from '../tests/test.model';
import { ResultFormComponent } from './result-form.component';
import { ResultFormMode, ResultResponse } from './result.model';

@Component({
  imports: [ResultFormComponent],
  selector: 'app-result-form-modal',
  templateUrl: './result-form-modal.component.html',
  styleUrl: './result-form-modal.component.scss',
})
export class ResultFormModalComponent implements AfterViewInit {
  @Input({ required: true }) mode: ResultFormMode = 'CREATE';
  @Input({ required: true }) athletes: AthleteResponse[] = [];
  @Input({ required: true }) tests: TestResponse[] = [];
  @Input() result: ResultResponse | null = null;
  @Input() initialAthleteId: number | null = null;
  @Output() saved = new EventEmitter<ResultResponse>();
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

  onSaved(result: ResultResponse): void {
    this.closeDialog();
    this.saved.emit(result);
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
