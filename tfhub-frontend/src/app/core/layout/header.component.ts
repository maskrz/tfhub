import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  imports: [RouterLink, RouterLinkActive],
  selector: 'app-header',
  template: `
    <header class="app-header">
      <a class="brand" routerLink="/dashboard" aria-label="TFHUB — strona główna">
        TFHUB<span>.</span>
      </a>
      <nav aria-label="Nawigacja główna">
        <a routerLink="/dashboard" routerLinkActive="active" ariaCurrentWhenActive="page">Panel główny</a>
        <a routerLink="/tests" routerLinkActive="active" ariaCurrentWhenActive="page">Testy</a>
        <a routerLink="/athletes" routerLinkActive="active" ariaCurrentWhenActive="page">Zawodnicy</a>
        <a routerLink="/results" routerLinkActive="active" ariaCurrentWhenActive="page">Wyniki</a>
      </nav>
      <span class="account" aria-label="Konto" title="Konto">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="12" cy="8" r="4"></circle>
          <path d="M4 21a8 8 0 0 1 16 0"></path>
        </svg>
      </span>
    </header>
  `,
  styles: `
    :host { display: block; }
    .app-header {
      align-items: center;
      background: #202630;
      color: #f7f8fa;
      display: flex;
      gap: 2rem;
      min-height: 5.6rem;
      padding: 0 clamp(1rem, 3vw, 2.5rem);
    }
    .brand {
      color: inherit;
      font-size: 1.65rem;
      font-weight: 750;
      letter-spacing: -.04em;
      text-decoration: none;
      white-space: nowrap;
    }
    .brand span { color: #41bf72; }
    nav {
      align-items: stretch;
      display: flex;
      flex: 1;
      gap: clamp(.5rem, 2vw, 2rem);
      height: 5.6rem;
    }
    nav a {
      align-items: center;
      border-bottom: 4px solid transparent;
      color: #d3d8e0;
      display: flex;
      font-weight: 550;
      padding: 0 .35rem;
      text-decoration: none;
      white-space: nowrap;
    }
    nav a.active {
      border-bottom-color: #42c66f;
      color: #fff;
    }
    .account {
      align-items: center;
      background: #66707c;
      border-radius: 50%;
      color: white;
      display: flex;
      height: 2.7rem;
      justify-content: center;
      width: 2.7rem;
    }
    .account svg {
      fill: none;
      height: 1.6rem;
      stroke: currentColor;
      stroke-linecap: round;
      stroke-width: 1.7;
      width: 1.6rem;
    }
    @media (max-width: 650px) {
      .app-header { flex-wrap: wrap; gap: .25rem 1rem; padding-block: .6rem 0; }
      nav { flex-basis: 100%; gap: .5rem; height: 3.25rem; justify-content: space-between; order: 1; }
      nav a { font-size: .82rem; padding: 0 .15rem; }
      .account { margin-left: auto; }
    }
  `,
})
export class HeaderComponent {}
