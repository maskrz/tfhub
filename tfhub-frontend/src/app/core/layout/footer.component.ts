import { Component } from '@angular/core';

@Component({
  selector: 'app-footer',
  template: `
    <footer>
      <p><strong>TFHUB.</strong></p>
      <span>v0.1.0</span>
    </footer>
  `,
  styles: `
    :host { display: block; }
    footer {
      align-items: center;
      border-top: 1px solid #d5dbe5;
      color: #526078;
      display: flex;
      font-size: .92rem;
      justify-content: space-between;
      padding: 1.2rem clamp(1rem, 3vw, 2.5rem);
    }
    p { margin: 0; }
    strong { color: #172033; margin-right: .4rem; }
  `,
})
export class FooterComponent {}
