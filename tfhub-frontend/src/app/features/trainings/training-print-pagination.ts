import { TrainingPrintItem } from './training.model';
import { formatTrainingDate } from './training-copy';
import { formatRestMinutes } from './training-rest-time';

interface PrintDay {
  date: string | null;
  trainings: TrainingPrintItem[];
}

export function groupPrintTrainings(trainings: TrainingPrintItem[]): PrintDay[] {
  const sorted = [...trainings].sort((a, b) =>
    (a.trainingDate ?? '').localeCompare(b.trainingDate ?? '') || a.id - b.id);
  const days: PrintDay[] = [];
  for (const training of sorted) {
    let day = days[days.length - 1];
    if (!day || day.date !== training.trainingDate) {
      day = { date: training.trainingDate, trainings: [] };
      days.push(day);
    }
    day.trainings.push(training);
  }
  return days;
}

export function dayEquipment(trainings: TrainingPrintItem[]): string[] {
  const seen = new Set<string>();
  const result: string[] = [];
  for (const training of trainings) {
    if (training.trainingType !== 'FREE_TEXT' || !training.equipment) continue;
    for (const item of training.equipment.split(/[\r\n,]+/)) {
      const value = item.trim();
      const key = value.toLocaleLowerCase('pl');
      if (value && !seen.has(key)) {
        seen.add(key);
        result.push(value);
      }
    }
  }
  return result;
}

function text(className: string, value: string): HTMLElement {
  const element = document.createElement('div');
  element.className = className;
  element.textContent = value;
  return element;
}

function trainingContent(training: TrainingPrintItem, showGroups: boolean): HTMLElement {
  const result = document.createElement('div');
  result.className = 'print-training';
  result.append(text('print-training-name', training.name));
  if (showGroups && training.trainingGroup) result.append(text('print-assignment', training.trainingGroup.name));
  if (training.assignmentType === 'ATHLETES' && training.assignedAthletes.length) {
    result.append(text('print-assignment', training.assignedAthletes.map((a) => `${a.firstName} ${a.lastName}`).join(', ')));
  }
  if (training.trainingType === 'TEMPO') {
    for (const definition of [...training.definitions].sort((a, b) => a.sequenceNumber - b.sequenceNumber)) {
      result.append(text('print-subheading', definition.name));
      for (const segment of [...definition.segments].sort((a, b) => a.sequenceNumber - b.sequenceNumber)) {
        const rest = segment.restAfterSeconds === null ? '' : ` — przerwa: ${formatRestMinutes(segment.restAfterSeconds)}`;
        result.append(text('print-content', `${segment.distanceM} m${rest}`));
      }
    }
  } else {
    for (const section of [...training.sections].sort((a, b) => a.sequenceNumber - b.sequenceNumber)) {
      result.append(text('print-subheading', section.title));
      result.append(text('print-content', section.content));
    }
  }
  return result;
}

/** Builds pages in the live, physically sized layout so browser font metrics determine breaks. */
export function paginateTrainings(host: HTMLElement, trainings: TrainingPrintItem[]): boolean {
  host.replaceChildren();
  let page: HTMLElement;
  let column: HTMLElement;
  let columnIndex = 2;
  let oversized = false;
  const groups = new Set(trainings.flatMap((item) => item.trainingGroup ? [item.trainingGroup.id] : []));
  const showGroups = groups.size > 1;
  const nextColumn = (): void => {
    if (columnIndex >= 2) {
      page = document.createElement('div');
      page.className = 'print-sheet';
      host.append(page);
      columnIndex = 0;
    }
    column = document.createElement('div');
    column.className = 'print-column';
    page.append(column);
    columnIndex++;
  };
  const fitsColumn = (candidate: HTMLElement): boolean => {
    const last = candidate.lastElementChild as HTMLElement | null;
    return candidate.scrollHeight <= candidate.clientHeight && (!last
      || last.getBoundingClientRect().bottom + parseFloat(getComputedStyle(last).marginBottom)
        <= candidate.getBoundingClientRect().bottom - 1);
  };
  const fits = (): boolean => fitsColumn(column);
  const appendWhole = (element: HTMLElement): boolean => {
    column.append(element);
    if (fits()) return true;
    element.remove();
    return false;
  };
  const appendFragments = (container: HTMLElement): void => {
    for (const child of Array.from(container.children) as HTMLElement[]) {
      if (appendWhole(child)) continue;
      const value = child.textContent ?? '';
      // Intl.Segmenter prevents splitting composed characters and emoji.
      const units = [...new Intl.Segmenter('pl', { granularity: 'grapheme' }).segment(value)]
        .map((item) => item.segment);
      let start = 0;
      while (start < units.length) {
        let low = 0;
        let high = units.length - start;
        while (low < high) {
          const middle = Math.ceil((low + high) / 2);
          const candidate = text(child.className, units.slice(start, start + middle).join(''));
          column.append(candidate);
          const valid = fits();
          candidate.remove();
          if (valid) low = middle;
          else high = middle - 1;
        }
        if (low === 0) {
          if (!column.hasChildNodes()) throw new Error('Print content cannot fit in an empty column');
          nextColumn();
          continue;
        }
        column.append(text(child.className, units.slice(start, start + low).join('')));
        start += low;
        if (start < units.length) nextColumn();
      }
    }
  };

  nextColumn();
  if (!column!.clientHeight) throw new Error('Print layout could not be measured');
  for (const day of groupPrintTrainings(trainings)) {
    const heading = text('print-date', day.date ? formatTrainingDate(day.date) : 'Bez daty');
    const equipment = dayEquipment(day.trainings);
    const dayNodes = [heading.cloneNode(true) as HTMLElement,
      ...day.trainings.map((training) => trainingContent(training, showGroups))];
    if (equipment.length) {
      const block = document.createElement('div');
      block.className = 'print-equipment';
      block.append(text('print-subheading', 'Sprzęt'), text('print-content', equipment.join(', ')));
      dayNodes.push(block);
    }
    const hadContent = column!.hasChildNodes();
    column!.append(...dayNodes);
    const fitsRemaining = fits();
    dayNodes.forEach((node) => node.remove());
    if (!fitsRemaining && hadContent) {
      const probe = document.createElement('div');
      probe.className = 'print-column';
      page!.append(probe);
      probe.append(...dayNodes);
      const fitsWholeColumn = fitsColumn(probe);
      probe.remove();
      if (fitsWholeColumn) nextColumn();
    }
    const first = trainingContent(day.trainings[0], showGroups);
    column!.append(heading, first);
    if (!fits()) {
      heading.remove();
      first.remove();
      if (column!.hasChildNodes()) nextColumn();
    } else {
      first.remove();
      heading.remove();
    }
    if (!appendWhole(heading)) throw new Error('Date does not fit in a column');
    for (const training of day.trainings) {
      const item = trainingContent(training, showGroups);
      if (appendWhole(item)) continue;
      if (day.trainings.length > 1) {
        const probe = document.createElement('div');
        probe.className = 'print-column';
        page!.append(probe);
        probe.append(item);
        const fitsEmptyColumn = fitsColumn(probe);
        item.remove();
        probe.remove();
        if (fitsEmptyColumn) {
          nextColumn();
          if (!appendWhole(item)) throw new Error('Training does not fit in an empty column');
          continue;
        }
        oversized = true;
      }
      appendFragments(item);
    }
    if (equipment.length) {
      const block = document.createElement('div');
      block.className = 'print-equipment';
      block.append(text('print-subheading', 'Sprzęt'), text('print-content', equipment.join(', ')));
      if (!appendWhole(block)) appendFragments(block);
    }
  }
  return oversized;
}
