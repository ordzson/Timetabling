export type PageResponse<T = Record<string, unknown>> = {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
};

export type ApiError = {
  code?: string;
  message?: string;
  details?: { fields?: { field: string; message: string }[] };
};

export type Field =
  | { name: string; label: string; type: 'text' | 'number' | 'time'; required?: boolean }
  | { name: string; label: string; type: 'checkbox' }
  | { name: string; label: string; type: 'select'; options: string[] };

export type Catalog = {
  resource: string;
  title: string;
  fields: Field[];
  columns: string[];
};

export type AdminView =
  | 'dashboard'
  | 'academia'
  | 'teachers'
  | 'time'
  | 'rooms'
  | 'import'
  | 'plans'
  | 'availability'
  | 'reports';
