import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class TrainingPrintSelectionService {
  private ids: number[] = [];

  set(ids: number[]): void {
    this.ids = [...ids];
  }

  consume(): number[] {
    const ids = this.ids;
    this.ids = [];
    return ids;
  }
}
