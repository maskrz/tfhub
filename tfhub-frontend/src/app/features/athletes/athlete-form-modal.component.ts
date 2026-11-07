import { AfterViewInit, Component, ElementRef, EventEmitter, Input, Output, ViewChild, signal } from '@angular/core';
import { AthleteFormComponent } from './athlete-form.component';
import { AthleteFormMode, AthleteResponse } from './athlete.model';

@Component({
  imports: [AthleteFormComponent],
  selector: 'app-athlete-form-modal',
  templateUrl: './athlete-form-modal.component.html',
  styleUrl: './athlete-form-modal.component.scss',
})
export class AthleteFormModalComponent implements AfterViewInit {
  @Input({ required: true }) mode!: AthleteFormMode;
  @Input() athlete: AthleteResponse | null = null;
  @Output() saved = new EventEmitter<AthleteResponse>();
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

  onSaved(athlete: AthleteResponse): void {
    this.closeDialog();
    this.saved.emit(athlete);
  }

  cancel(): void {
    if (this.saving()) {
      return;
    }
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
