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
