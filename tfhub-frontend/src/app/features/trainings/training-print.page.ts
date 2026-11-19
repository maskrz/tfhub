import { Component, DestroyRef, ElementRef, OnInit, ViewChild, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { debounceTime, fromEvent } from 'rxjs';
import { TrainingPrintItem } from './training.model';
import { TrainingPrintSelectionService } from './training-print-selection.service';
import { TrainingsApiService } from './trainings-api.service';
import { paginateTrainings } from './training-print-pagination';

@Component({
  selector: 'app-training-print-page',
  imports: [RouterLink],
  templateUrl: './training-print.page.html',
  styleUrl: './training-print.page.scss',
})
export class TrainingPrintPage implements OnInit {
  @ViewChild('pages', { static: true }) private pages!: ElementRef<HTMLElement>;
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly oversized = signal(false);
  private readonly selection = inject(TrainingPrintSelectionService);
  private readonly api = inject(TrainingsApiService);
  private readonly destroyRef = inject(DestroyRef);
  private trainings: TrainingPrintItem[] = [];
  private layoutVersion = 0;

  ngOnInit(): void {
    const ids = this.selection.consume();
    if (!ids.length) {
      this.loading.set(false);
      this.error.set('Brak wybranych treningów. Wróć do listy i wybierz treningi.');
      return;
    }
    this.api.printPreview(ids).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (response) => {
        this.trainings = response.trainings;
        void this.layout();
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Nie udało się pobrać treningów lub brak dostępu. Wróć do listy i spróbuj ponownie.');
      },
    });
    fromEvent(window, 'resize').pipe(debounceTime(150), takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (this.trainings.length) void this.layout();
    });
  }

  async print(): Promise<void> {
    if (await this.layout()) window.print();
  }

  private async layout(): Promise<boolean> {
    const version = ++this.layoutVersion;
    this.loading.set(true);
    try {
      await document.fonts.ready;
      if (this.destroyRef.destroyed || version !== this.layoutVersion) return false;
      this.oversized.set(paginateTrainings(this.pages.nativeElement, this.trainings));
      this.error.set(null);
      return true;
    } catch {
      if (this.destroyRef.destroyed || version !== this.layoutVersion) return false;
      this.pages.nativeElement.replaceChildren();
      this.error.set('Nie udało się przygotować układu wydruku. Spróbuj ponownie.');
      return false;
    } finally {
      if (!this.destroyRef.destroyed && version === this.layoutVersion) this.loading.set(false);
    }
  }
}
