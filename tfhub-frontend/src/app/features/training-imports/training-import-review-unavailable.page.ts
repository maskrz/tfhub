import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  imports: [RouterLink],
  template: `
    <section class="page" aria-labelledby="page-title">
      <h1 id="page-title">Weryfikacja importu jest niedostępna</h1>
      <p>Ten import wymaga weryfikacji, ale ekran weryfikacji nie jest jeszcze dostępny.</p>
      <a class="button button--secondary" routerLink="/trainings">Wróć do treningów</a>
    </section>
  `,
  styles: [`
    :host { display: block; }
    .page { margin: 0 auto; max-width: 62rem; padding: clamp(1.5rem, 4vw, 3.5rem) clamp(1rem, 3.25vw, 3.25rem); }
    h1 { color: #141c2b; font-size: clamp(2rem, 5vw, 3rem); margin: 0 0 .75rem; }
    p { color: #59667e; margin: 0 0 1.25rem; }
  `],
  selector: 'app-training-import-review-unavailable-page',
})
export class TrainingImportReviewUnavailablePage {}
