import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { FooterComponent } from './footer.component';
import { HeaderComponent } from './header.component';

@Component({
  imports: [FooterComponent, HeaderComponent, RouterOutlet],
  selector: 'app-shell',
  template: `
    <div class="shell">
      <app-header />
      <main>
        <router-outlet />
      </main>
      <app-footer />
    </div>
  `,
  styles: `
    :host { display: block; min-height: 100%; }
    .shell {
      background: #f2f4f8;
      color: #172033;
      display: flex;
      flex-direction: column;
      min-height: 100dvh;
    }
    main { flex: 1; }
  `,
})
export class AppShellComponent {}
