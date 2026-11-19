import { dayEquipment, groupPrintTrainings } from './training-print-pagination';
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
});
