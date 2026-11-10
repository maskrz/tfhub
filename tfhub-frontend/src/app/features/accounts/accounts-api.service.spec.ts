import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AccountsApiService } from './accounts-api.service';
import { AccountResponse, CreateAccountRequest, UpdateAccountRequest } from './account.model';

describe('AccountsApiService', () => {
  let service: AccountsApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), AccountsApiService],
    });
    service = TestBed.inject(AccountsApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const account: AccountResponse = {
    id: 4,
    username: 'trener',
    role: 'COACH',
    active: true,
    trainingGroups: [{ id: 2, name: 'Sprint' }],
    athlete: null,
  };

  it('lists all accounts without backend filter parameters', () => {
    service.getAll().subscribe((result) => {
      expect(result).toEqual([account]);
    });
    const request = http.expectOne((req) => req.url === '/api/accounts');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual([]);
    request.flush([account]);
  });

  it('creates and updates accounts with the backend DTO shapes', () => {
    const create: CreateAccountRequest = {
      username: 'admin',
      password: 'password',
      role: 'ADMIN',
      trainingGroupIds: [],
      athleteId: null,
    };
    service.createAccount(create).subscribe();
    const createRequest = http.expectOne('/api/accounts');
    expect(createRequest.request.method).toBe('POST');
    expect(createRequest.request.body).toEqual(create);
    createRequest.flush({ ...account, role: 'ADMIN', trainingGroups: [] });

    const update: UpdateAccountRequest = {
      username: 'nowy-trener',
      role: 'COACH',
      trainingGroupIds: [2, 3],
      athleteId: null,
    };
    service.updateAccount(4, update).subscribe();
    const updateRequest = http.expectOne('/api/accounts/4');
    expect(updateRequest.request.method).toBe('PUT');
    expect(updateRequest.request.body).toEqual(update);
    updateRequest.flush({ ...account, username: update.username });
  });

  it('changes status and resets passwords at the backend endpoints', () => {
    service.changeStatus(4, { active: false }).subscribe();
    const statusRequest = http.expectOne('/api/accounts/4/status');
    expect(statusRequest.request.method).toBe('PATCH');
    expect(statusRequest.request.body).toEqual({ active: false });
    statusRequest.flush({ ...account, active: false });

    service.resetPassword(4, { newPassword: 'new-password' }).subscribe();
    const passwordRequest = http.expectOne('/api/accounts/4/password');
    expect(passwordRequest.request.method).toBe('POST');
    expect(passwordRequest.request.body).toEqual({ newPassword: 'new-password' });
    passwordRequest.flush(null);
  });
});
