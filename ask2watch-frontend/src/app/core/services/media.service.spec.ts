import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { MediaService } from './media.service';
import { MediaType } from '../../shared/models/media.model';
import { CsvImportResponse } from '../../shared/models/csv-import.model';

describe('MediaService', () => {
  let service: MediaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    service = TestBed.inject(MediaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('posts a multipart CSV payload to the backend', () => {
    const file = new File(['title'], 'test.csv', { type: 'text/csv' });
    const mockResponse: CsvImportResponse = {
      message: 'ok',
      summary: { totalLines: 1, imported: 1, skipped: 0, duplicates: 0, errors: 0 },
      errors: [],
    };

    service.importCsv(file, 'SERIES' as MediaType).subscribe((response) => {
      expect(response).toEqual(mockResponse);
    });

    const request = http.expectOne('/api/media/import/csv');
    expect(request.request.method).toBe('POST');
    const body = request.request.body as FormData;
    expect(body.get('file')).toBe(file);
    expect(body.get('type')).toBe('SERIES');
    request.flush(mockResponse);
  });
});
