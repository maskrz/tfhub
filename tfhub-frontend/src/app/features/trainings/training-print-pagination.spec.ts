import { dayEquipment, groupPrintTrainings, paginateTrainings } from './training-print-pagination';
import { TrainingPrintItem } from './training.model';

const item: TrainingPrintItem = {
  id: 1, name: 'Plan', trainingDate: '2026-01-01', trainingType: 'FREE_TEXT',
  assignmentType: 'GROUP', trainingGroup: { id: 1, name: 'A' }, assignedAthletes: [],
  definitions: [], sections: [], equipment: 'Piłka, Gumy\nSKAKANKA',
};

describe('print grouping', () => {
  it('orders undated and dated sessions deterministically', () => {
    expect(groupPrintTrainings([item, { ...item, id: 3, trainingDate: null },
      { ...item, id: 2 }]).map((day) => day.trainings.map((training) => training.id)))
      .toEqual([[3], [1, 2]]);
  });

  it('splits equipment on lines and commas and deduplicates case-insensitively', () => {
    expect(dayEquipment([item, { ...item, equipment: 'gumy,  piłka\nPachołki' }]))
      .toEqual(['Piłka', 'Gumy', 'SKAKANKA', 'Pachołki']);
  });

  it('uses four measured areas before creating another page', () => {
    const height = (element: Element): number => element.classList.contains('print-date') ? 20 : 70;
    vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockReturnValue(100);
    vi.spyOn(HTMLElement.prototype, 'scrollHeight', 'get').mockImplementation(function (this: HTMLElement) {
      return Array.from(this.children).reduce((sum, child) => sum + height(child), 0);
    });
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (this: HTMLElement) {
      const bottom = this.classList.contains('print-column') ? 100 :
        Array.from(this.parentElement?.children ?? []).reduce((sum, child) => sum + height(child), 0);
      return { bottom } as DOMRect;
    });
    const style = document.createElement('div').style;
    style.marginBottom = '0px';
    vi.spyOn(window, 'getComputedStyle').mockReturnValue(style);
    try {
      const host = document.createElement('div');
      const trainings = Array.from({ length: 5 }, (_, i) => ({
        ...item, id: i + 1, name: `Plan ${i + 1}`, trainingDate: `2026-01-0${i + 1}`, equipment: null,
      }));
      expect(paginateTrainings(host, trainings)).toBe(false);
      const pages = Array.from(host.children);
      expect(pages.map((page) => page.children.length)).toEqual([4, 1]);
      expect(Array.from(host.querySelectorAll('.print-training-name')).map((name) => name.textContent))
        .toEqual(trainings.map((training) => training.name));
    } finally {
      vi.restoreAllMocks();
    }
  });
});
