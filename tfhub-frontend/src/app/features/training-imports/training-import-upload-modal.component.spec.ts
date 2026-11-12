import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, Subject, of, throwError } from 'rxjs';
import { TrainingGroupsApiService } from '../training-groups/training-groups-api.service';
import { TrainingImportUploadResponse } from './training-import.model';
import { TrainingImportsApiService } from './training-imports-api.service';
import { TrainingImportUploadModalComponent } from './training-import-upload-modal.component';

describe('TrainingImportUploadModalComponent', () => {
  let fixture: ComponentFixture<TrainingImportUploadModalComponent>;
  let groupsApi: { getAccessible: ReturnType<typeof vi.fn> };
  let importsApi: { upload: ReturnType<typeof vi.fn> };

  async function createModal(upload: () => Observable<TrainingImportUploadResponse> = () => of({
    id: 21,
    trainingGroupId: 7,
    status: 'PENDING' as const,
    possibleDuplicate: false,
  })): Promise<void> {
    groupsApi = { getAccessible: vi.fn(() => of([{ id: 7, name: 'Sprint' }])) };
    importsApi = { upload: vi.fn(upload) };
    await TestBed.configureTestingModule({
      imports: [TrainingImportUploadModalComponent],
      providers: [
        { provide: TrainingGroupsApiService, useValue: groupsApi },
        { provide: TrainingImportsApiService, useValue: importsApi },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(TrainingImportUploadModalComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function chooseGroup(): void {
    const select = fixture.nativeElement.querySelector('#training-group') as HTMLSelectElement;
    select.value = '7';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function chooseImage(file: File): void {
    const input = fixture.nativeElement.querySelector('#training-image') as HTMLInputElement;
    Object.defineProperty(input, 'files', {
      configurable: true,
      value: { item: (index: number) => index === 0 ? file : null },
    });
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function submitButton(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;
  }

  function submitForm(): void {
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    expect(submitButton().form).toBe(form);
    const event = new Event('submit', { bubbles: true, cancelable: true });
    form.dispatchEvent(event);
    expect(event.defaultPrevented).toBe(true);
    fixture.detectChanges();
  }

  it('loads accessible groups and requires a group and supported image before enabling upload', async () => {
    await createModal();
    const select = fixture.nativeElement.querySelector('#training-group') as HTMLSelectElement;

    expect(groupsApi.getAccessible).toHaveBeenCalledOnce();
    expect([...select.options].map((option) => option.textContent?.trim())).toContain('Sprint');
    expect(submitButton().disabled).toBe(true);

    chooseGroup();
    expect(submitButton().disabled).toBe(true);
    chooseImage(new File(['photo'], 'training.jpg', { type: 'image/jpeg' }));
    expect(fixture.nativeElement.textContent).toContain('training.jpg');
    expect(submitButton().disabled).toBe(false);
  });

  it('rejects unsupported image types and lets the user replace a selected photo', async () => {
    await createModal();
    chooseGroup();
    chooseImage(new File(['photo'], 'training.png', { type: 'image/png' }));
    chooseImage(new File(['document'], 'notes.pdf', { type: 'application/pdf' }));

    expect(fixture.nativeElement.textContent).toContain('Wybierz zdjęcie w formacie JPG, JPEG lub PNG.');
    expect(fixture.nativeElement.textContent).not.toContain('training.png');
    expect(submitButton().disabled).toBe(true);

    chooseImage(new File(['photo'], 'replacement.png', { type: 'image/png' }));
    expect(fixture.nativeElement.textContent).toContain('replacement.png');
    expect(submitButton().disabled).toBe(false);
  });

  it('prevents duplicate uploads, disables cancellation while pending, and preserves selections on errors', async () => {
    const pendingUpload = new Subject<TrainingImportUploadResponse>();
    await createModal(() => pendingUpload);
    chooseGroup();
    const image = new File(['photo'], 'training.png', { type: 'image/png' });
    chooseImage(image);

    submitForm();
    submitForm();
    expect(importsApi.upload).toHaveBeenCalledOnce();
    expect(importsApi.upload).toHaveBeenCalledWith(7, image);
    expect(submitButton().disabled).toBe(true);
    expect((fixture.nativeElement.querySelector('.modal-footer .button--secondary') as HTMLButtonElement).disabled)
      .toBe(true);

    pendingUpload.error(new HttpErrorResponse({ status: 500 }));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Wybrane dane zachowano');
    expect(fixture.nativeElement.textContent).toContain('training.png');
    expect((fixture.nativeElement.querySelector('#training-group') as HTMLSelectElement).value).toBe('7');
    expect(submitButton().disabled).toBe(false);
  });

  it('emits the upload response and closes only after a successful upload', async () => {
    await createModal();
    const uploaded = vi.fn();
    fixture.componentInstance.uploaded.subscribe(uploaded);
    chooseGroup();
    chooseImage(new File(['photo'], 'training.png', { type: 'image/png' }));
    submitForm();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(uploaded).toHaveBeenCalledWith({
      id: 21,
      trainingGroupId: 7,
      status: 'PENDING',
      possibleDuplicate: false,
    });
    expect(fixture.nativeElement.querySelector('dialog').hasAttribute('open')).toBe(false);
  });

  it('reports an accessible-groups loading error and supports retrying the request', async () => {
    const getAccessible = vi.fn()
      .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })))
      .mockReturnValueOnce(of([{ id: 7, name: 'Sprint' }]));
    groupsApi = { getAccessible };
    importsApi = { upload: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [TrainingImportUploadModalComponent],
      providers: [
        { provide: TrainingGroupsApiService, useValue: groupsApi },
        { provide: TrainingImportsApiService, useValue: importsApi },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(TrainingImportUploadModalComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nie udało się pobrać dostępnych grup treningowych.');
    (fixture.nativeElement.querySelector('.groups-error button') as HTMLButtonElement).click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(getAccessible).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.querySelector('#training-group option[value="7"]')).toBeTruthy();
  });
});
