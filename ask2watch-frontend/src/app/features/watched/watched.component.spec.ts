import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MediaService } from '../../core/services/media.service';
import { WatchedComponent } from './watched.component';
import { MediaType } from '../../shared/models/media.model';
import { CsvImportResponse } from '../../shared/models/csv-import.model';

describe('WatchedComponent', () => {
  let component: WatchedComponent;
  let mediaService: Pick<MediaService, 'getWatchedMovies' | 'getWatchedSeries' | 'importCsv'>;

  const mockSummary = { totalLines: 1, imported: 1, skipped: 0, duplicates: 0, errors: 0 };
  const csvResponse: CsvImportResponse = {
    message: 'done',
    summary: mockSummary,
    errors: [],
  };

  beforeEach(() => {
    mediaService = {
      getWatchedMovies: vi.fn(() => of([])),
      getWatchedSeries: vi.fn(() => of([])),
      importCsv: vi.fn(() => of(csvResponse)),
    };

    component = new WatchedComponent(mediaService as MediaService);
    component.ngOnInit();
  });

  it('calls importCsv and refreshes the selected list', () => {
    const file = new File(['data'], 'watched.csv', { type: 'text/csv' });
    component.uploadFile.set(file);
    component.uploadType.set('SERIES' as MediaType);

    component.triggerUpload();

    expect(mediaService.importCsv).toHaveBeenCalledWith(file, 'SERIES');
    expect(component.uploadSummary()).toBe(mockSummary);
    expect(mediaService.getWatchedSeries).toHaveBeenCalledTimes(2);
  });

  it('stores an error message when upload fails', () => {
    const file = new File(['data'], 'watched.csv', { type: 'text/csv' });
    const response = new HttpErrorResponse({
      status: 400,
      error: { error: 'CSV file is required' },
    });

    mediaService.importCsv = vi.fn(() => throwError(() => response)) as MediaService['importCsv'];

    component.uploadFile.set(file);
    component.uploadType.set('MOVIE' as MediaType);

    component.triggerUpload();

    expect(component.uploadStatus()).toBe('error');
    expect(component.uploadMessage()).toBe('CSV file is required');
  });

  it('fails fast when no file is selected', () => {
    component.triggerUpload();

    expect(component.uploadStatus()).toBe('error');
    expect(component.uploadMessage()).toBe('Select a CSV file before importing.');
  });
});
