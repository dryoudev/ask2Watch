export interface CsvImportError {
  lineNumber: number;
  title: string;
  reason: string;
}

export interface CsvImportSummary {
  totalLines: number;
  imported: number;
  skipped: number;
  duplicates: number;
  errors: number;
}

export interface CsvImportResponse {
  message: string;
  summary: CsvImportSummary;
  errors: CsvImportError[];
}
