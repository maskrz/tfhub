import { AfterViewInit, Directive, ElementRef, OnDestroy, inject } from '@angular/core';

@Directive({ selector: 'dialog[appModalDialog]' })
export class ModalDialogDirective implements AfterViewInit, OnDestroy {
  private readonly dialog = inject<ElementRef<HTMLDialogElement>>(ElementRef).nativeElement;
  private previousFocus: HTMLElement | null = null;

  ngAfterViewInit(): void {
    this.previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    if (typeof this.dialog.showModal === 'function') {
      this.dialog.showModal();
    } else {
      this.dialog.setAttribute('open', '');
    }
  }

  ngOnDestroy(): void {
    if (this.dialog.open && typeof this.dialog.close === 'function') {
      this.dialog.close();
    }
    if (this.previousFocus?.isConnected) this.previousFocus.focus();
  }
}
