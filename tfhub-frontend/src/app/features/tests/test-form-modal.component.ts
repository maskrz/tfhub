import { AfterViewInit, Component, ElementRef, EventEmitter, Input, Output, ViewChild, signal } from '@angular/core';
import { TestFormComponent } from './test-form.component';
import { TestFormMode, TestResponse } from './test.model';

@Component({
  imports: [TestFormComponent],
  selector: 'app-test-form-modal',
  templateUrl: './test-form-modal.component.html',
  styleUrl: './test-form-modal.component.scss',
})
export class TestFormModalComponent implements AfterViewInit {
  @Input({ required: true }) mode!: TestFormMode;
  @Input() test: TestResponse | null = null;
  @Output() saved = new EventEmitter<TestResponse>();
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

  onSaved(test: TestResponse): void {
    this.closeDialog();
    this.saved.emit(test);
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
