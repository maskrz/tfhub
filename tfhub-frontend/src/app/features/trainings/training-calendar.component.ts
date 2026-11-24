import { Component, computed, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TrainingListItem } from './training.model';

interface CalendarDayCell {
  date: string;
  dayNumber: number;
  plannedCount: number;
  completedCount: number;
  isToday: boolean;
}

function dateKey(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function monthKey(date: Date): string {
  return dateKey(date).slice(0, 7);
}

function localDate(date: string): Date {
  const [year, month, day] = date.split('-').map(Number);
  return new Date(year, month - 1, day);
}

@Component({
  imports: [RouterLink],
  selector: 'app-training-calendar',
  styleUrl: './training-calendar.component.scss',
  templateUrl: './training-calendar.component.html',
})
export class TrainingCalendarComponent {
  readonly month = input.required<string>();
  readonly trainings = input.required<TrainingListItem[]>();
  readonly loading = input(false);
  readonly errorMessage = input<string | null>(null);
  readonly selectedDate = input<string | null>(null);

  readonly monthChanged = output<string>();
  readonly selectedDateChanged = output<string>();
  readonly retry = output<void>();

  readonly weekdays = ['Pn', 'Wt', 'Śr', 'Cz', 'Pt', 'Sb', 'Nd'];
  readonly monthLabel = computed(() => {
    const [year, month] = this.month().split('-').map(Number);
    return new Intl.DateTimeFormat('pl-PL', { month: 'long', year: 'numeric' })
      .format(new Date(year, month - 1, 1));
  });
  readonly cells = computed<Array<CalendarDayCell | null>>(() => {
    const [year, month] = this.month().split('-').map(Number);
    const firstDay = new Date(year, month - 1, 1);
    const leadingCells = (firstDay.getDay() + 6) % 7;
    const daysInMonth = new Date(year, month, 0).getDate();
    const counts = new Map<string, { planned: number; completed: number }>();

    for (const training of this.trainings()) {
      if (!training.trainingDate) continue;
      const count = counts.get(training.trainingDate) ?? { planned: 0, completed: 0 };
      if (training.status === 'PLANNED') count.planned += 1;
      else count.completed += 1;
      counts.set(training.trainingDate, count);
    }

    const today = dateKey(new Date());
    const cells: Array<CalendarDayCell | null> = Array(leadingCells).fill(null);
    for (let dayNumber = 1; dayNumber <= daysInMonth; dayNumber += 1) {
      const date = dateKey(new Date(year, month - 1, dayNumber));
      const count = counts.get(date) ?? { planned: 0, completed: 0 };
      cells.push({
        date,
        dayNumber,
        plannedCount: count.planned,
        completedCount: count.completed,
        isToday: date === today,
      });
    }
    while (cells.length % 7 !== 0) cells.push(null);
    return cells;
  });
  readonly selectedDateLabel = computed(() => {
    const selectedDate = this.selectedDate();
    if (!selectedDate) return '';
    return new Intl.DateTimeFormat('pl-PL', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    }).format(localDate(selectedDate));
  });
  readonly selectedDateTrainings = computed(() => {
    const selectedDate = this.selectedDate();
    return selectedDate
      ? this.trainings().filter((training) => training.trainingDate === selectedDate)
      : [];
  });
  readonly undatedTrainings = computed(() =>
    this.trainings().filter((training) => training.trainingDate === null),
  );

  previousMonth(): void {
    this.changeMonth(-1);
  }

  nextMonth(): void {
    this.changeMonth(1);
  }

  goToToday(): void {
    const today = dateKey(new Date());
    this.monthChanged.emit(today.slice(0, 7));
    this.selectedDateChanged.emit(today);
  }

  selectDate(date: string): void {
    this.selectedDateChanged.emit(date);
  }

  dayLabel(cell: CalendarDayCell): string {
    const count = cell.plannedCount + cell.completedCount;
    return `${cell.date}: treningów ${count}, planowanych ${cell.plannedCount}, zakończonych ${cell.completedCount}`;
  }

  statusLabel(status: TrainingListItem['status']): string {
    return status === 'COMPLETED' ? 'Zakończony' : 'Planowany';
  }

  private changeMonth(direction: -1 | 1): void {
    const [year, month] = this.month().split('-').map(Number);
    this.monthChanged.emit(monthKey(new Date(year, month - 1 + direction, 1)));
  }
}
