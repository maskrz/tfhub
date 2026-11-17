import { formatFreeTextTraining, formatTempoTraining } from './training-copy';
import { TrainingDetails } from './training.model';

const training: TrainingDetails = {
  id: 901,
  name: 'Szybkość',
  trainingDate: '2026-01-02',
  status: 'COMPLETED',
  trainingType: 'FREE_TEXT',
  assignmentType: 'ATHLETES',
  trainingGroup: { id: 902, name: 'Prywatna grupa' },
  assignedAthletes: [{ id: 903, firstName: 'Jan', lastName: 'Kowalski' }],
  equipment: 'Prywatny sprzęt',
  sections: [
    { id: 904, sequenceNumber: 2, title: 'Część główna', content: '  Skip A\nSkip B\n' },
    { id: 905, sequenceNumber: 1, title: 'Rozgrzewka', content: 'Trucht\nĆwiczenia' },
  ],
  definitions: [{
    id: 906,
    sequenceNumber: 1,
    name: 'Grupa A',
    segments: [
      { id: 907, sequenceNumber: 3, distanceM: 300, restAfterSeconds: null },
      { id: 908, sequenceNumber: 1, distanceM: 100, restAfterSeconds: 0 },
      { id: 909, sequenceNumber: 2, distanceM: 200, restAfterSeconds: 250 },
    ],
    participations: [{
      id: 910, athleteId: 911, firstName: 'Anna', lastName: 'Nowak',
      executions: [{ id: 912, segmentId: 907, segmentSequenceNumber: 3, status: 'RECORDED', measuredResult: 42, usesSpikes: true }],
    }],
  }, {
    id: 913, sequenceNumber: 2, name: 'Inna podgrupa', segments: [], participations: [],
  }],
};

describe('training copy formatting', () => {
  it('formats all sections in order without changing their content or exposing other fields', () => {
    expect(formatFreeTextTraining(training)).toBe(
      'Szybkość\n2.01.2026\n\nRozgrzewka\nTrucht\nĆwiczenia\n\nCzęść główna\n  Skip A\nSkip B\n',
    );
    expect(training.sections.map((section) => section.sequenceNumber)).toEqual([2, 1]);
  });

  it('formats only the selected definition with ordered distances and rest values', () => {
    expect(formatTempoTraining(training, training.definitions[0])).toBe(
      'Szybkość\n2.01.2026\n\nGrupa A\n\n1. 100 m — przerwa: 0m\n2. 200 m — przerwa: 4m 10s\n3. 300 m — przerwa: —',
    );
    expect(training.definitions[0].segments.map((segment) => segment.sequenceNumber)).toEqual([3, 1, 2]);
  });

  it('omits absent dates and handles empty sections and segments', () => {
    expect(formatFreeTextTraining({ ...training, trainingDate: null, sections: [] })).toBe('Szybkość');
    expect(formatTempoTraining({ ...training, trainingDate: null }, training.definitions[1])).toBe(
      'Szybkość\n\nInna podgrupa',
    );
  });
});
