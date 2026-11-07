import { Component } from '@angular/core';

@Component({
  selector: 'app-test-edit-pending',
  template: `
    <section class="notice" role="status">
      Formularz edycji testu nie jest jeszcze dostępny.
    </section>
  `,
  styles: `
    .notice {
      background: #fff;
      border: 1px solid #dbe1eb;
      border-radius: .75rem;
      margin: 3rem auto;
      max-width: 60rem;
      padding: 2rem;
    }
  `,
})
export class TestEditPendingComponent {}
