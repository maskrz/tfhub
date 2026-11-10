import { AfterViewInit, Component, ElementRef, EventEmitter, Input, Output, ViewChild, signal } from '@angular/core';
import { AccountResponse } from './account.model';
import { AccountFormComponent, AccountFormMode } from './account-form.component';

@Component({
  imports: [AccountFormComponent],
  selector: 'app-account-form-modal',
  templateUrl: './account-form-modal.component.html',
  styleUrl: './account-form-modal.component.scss',
})
export class AccountFormModalComponent implements AfterViewInit {
  @Input({ required: true }) mode!: AccountFormMode;
  @Input() account: AccountResponse | null = null;
  @Output() saved = new EventEmitter<AccountResponse>();
  @Output() cancelled = new EventEmitter<void>();
  @ViewChild('dialog') private dialog!: ElementRef<HTMLDialogElement>;

  readonly saving = signal(false);

  ngAfterViewInit(): void {
    const dialog = this.dialog.nativeElement;
    if (typeof dialog.showModal === 'function') dialog.showModal();
    else dialog.setAttribute('open', '');
  }

  onSaved(account: AccountResponse): void {
    this.closeDialog();
    this.saved.emit(account);
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
    if (typeof dialog.close === 'function') dialog.close();
    else dialog.removeAttribute('open');
  }
}
