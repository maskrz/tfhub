import { TrainingDefinitionDetails, TrainingDetails } from './training.model';
import { formatRestMinutes } from './training-rest-time';

export function formatTrainingDate(date: string): string {
  const [year, month, day] = date.split('-').map(Number);
  return new Intl.DateTimeFormat('pl-PL').format(new Date(year, month - 1, day));
}

function trainingHeading(training: Pick<TrainingDetails, 'name' | 'trainingDate'>): string {
  return training.trainingDate
    ? `${training.name}\n${formatTrainingDate(training.trainingDate)}`
    : training.name;
}

export function formatFreeTextTraining(training: TrainingDetails): string {
  const sections = [...training.sections]
    .sort((a, b) => a.sequenceNumber - b.sequenceNumber)
    .map((section) => `${section.title}\n${section.content}`);
  return [trainingHeading(training), ...sections].join('\n\n');
}

export function formatTempoTraining(
  training: TrainingDetails,
  definition: TrainingDefinitionDetails,
): string {
  const segments = [...definition.segments]
    .sort((a, b) => a.sequenceNumber - b.sequenceNumber)
    .map((segment) => {
      const rest = segment.restAfterSeconds === null ? '—' : formatRestMinutes(segment.restAfterSeconds);
      return `${segment.sequenceNumber}. ${segment.distanceM} m — przerwa: ${rest}`;
    });
  return [trainingHeading(training), definition.name, ...(segments.length ? [segments.join('\n')] : [])].join('\n\n');
}
