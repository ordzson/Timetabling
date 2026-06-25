export type ImportSummary = {
  rowsRead: number;
  rowsValid: number;
  rowsInvalid: number;
};

export type ImportResponse = {
  importBatchId: number;
  status: string;
  filename: string;
  summary: ImportSummary;
  errorCount: number;
};

export type ImportErrorRow = {
  id: number;
  sheetName: string;
  rowNumber: number;
  columnName: string;
  rawValue: string;
  code: string;
  message?: string;
  suggestedAction?: string;
};
