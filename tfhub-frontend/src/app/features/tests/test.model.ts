export type TestCategory = 'TIME' | 'DISTANCE' | 'SPEED';

export interface TestResponse {
  id: number;
  name: string;
  unit: string;
  category: TestCategory;
  higherIsBetter: boolean;
  active: boolean;
}

export interface TestSearchRequest {
  name?: string;
  active?: boolean;
}

interface TestDefinitionFields {
  name: string;
  unit: string;
  category: TestCategory;
  higherIsBetter: boolean;
}

export type CreateTestRequest = TestDefinitionFields;

export type UpdateTestRequest = TestDefinitionFields;

export interface ChangeTestStatusRequest {
  active: boolean;
}

export type TestFormMode = 'CREATE' | 'EDIT';
