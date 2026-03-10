import { Component, computed, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { AppHeaderComponent } from '../../shared/components/app-header/app-header.component';
import { MovieCardComponent } from '../../shared/components/movie-card/movie-card.component';
import { MediaDetailDialogComponent } from '../../shared/components/media-detail-dialog/media-detail-dialog.component';
import { MediaService } from '../../core/services/media.service';
import { WatchedMediaResponse } from '../../shared/models/watched.model';
import { CsvImportError, CsvImportSummary } from '../../shared/models/csv-import.model';
import { MediaType } from '../../shared/models/media.model';

@Component({
  selector: 'app-watched',
  standalone: true,
  imports: [FormsModule, AppHeaderComponent, MovieCardComponent, MediaDetailDialogComponent],
  templateUrl: './watched.component.html',
  styleUrl: './watched.component.css',
})
export class WatchedComponent implements OnInit {
  activeTab = signal<'movies' | 'series'>('movies');
  movieSearch = signal('');
  seriesSearch = signal('');
  sortBy = signal<'default' | 'rating-asc' | 'rating-desc' | 'year-asc' | 'year-desc'>('default');
  filterGenre = signal('');
  movies = signal<WatchedMediaResponse[]>([]);
  series = signal<WatchedMediaResponse[]>([]);
  selectedItem = signal<WatchedMediaResponse | null>(null);
  dialogOpen = signal(false);
  uploadType = signal<MediaType>('MOVIE');
  uploadFile = signal<File | null>(null);
  uploadStatus = signal<'idle' | 'loading' | 'success' | 'error'>('idle');
  uploadMessage = signal('');
  uploadSummary = signal<CsvImportSummary | null>(null);
  uploadErrors = signal<CsvImportError[]>([]);

  private applySortAndFilter(items: WatchedMediaResponse[]): WatchedMediaResponse[] {
    let result = [...items];

    // Filter by genre if selected
    if (this.filterGenre()) {
      result = result.filter((item) =>
        item.media.genres?.toLowerCase().includes(this.filterGenre().toLowerCase())
      );
    }

    // Sort by selected criteria
    switch (this.sortBy()) {
      case 'rating-asc':
        result.sort((a, b) => (a.userRating || 0) - (b.userRating || 0));
        break;
      case 'rating-desc':
        result.sort((a, b) => (b.userRating || 0) - (a.userRating || 0));
        break;
      case 'year-asc':
        result.sort((a, b) => (a.media.year || '0').localeCompare(b.media.year || '0'));
        break;
      case 'year-desc':
        result.sort((a, b) => (b.media.year || '0').localeCompare(a.media.year || '0'));
        break;
    }

    return result;
  }

  filteredMovies = computed(() => {
    const search = this.movieSearch().toLowerCase();
    let result = search
      ? this.movies().filter((m) => m.media.title.toLowerCase().includes(search))
      : this.movies();
    return this.applySortAndFilter(result);
  });

  filteredSeries = computed(() => {
    const search = this.seriesSearch().toLowerCase();
    let result = search
      ? this.series().filter((s) => s.media.title.toLowerCase().includes(search))
      : this.series();
    return this.applySortAndFilter(result);
  });

  constructor(private mediaService: MediaService) {}

  ngOnInit(): void {
    this.loadMovies();
    this.loadSeries();
  }

  onItemClick(item: WatchedMediaResponse): void {
    this.selectedItem.set(item);
    this.dialogOpen.set(true);
  }

  onItemUpdated(updated: WatchedMediaResponse): void {
    this.movies.update((items) =>
      items.map((item) => (item.watchedId === updated.watchedId ? updated : item))
    );
    this.series.update((items) =>
      items.map((item) => (item.watchedId === updated.watchedId ? updated : item))
    );
    this.selectedItem.set(updated);
  }

  onRatingChanged(event: { watchedId: number; newRating: number }): void {
    // Update the rating in both movies and series arrays
    this.movies.update((items) =>
      items.map((item) =>
        item.watchedId === event.watchedId ? { ...item, userRating: event.newRating } : item
      )
    );
    this.series.update((items) =>
      items.map((item) =>
        item.watchedId === event.watchedId ? { ...item, userRating: event.newRating } : item
      )
    );
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.uploadFile.set(file);
    this.uploadStatus.set('idle');
    this.uploadMessage.set(file ? `${file.name} selected` : '');
    this.uploadSummary.set(null);
    this.uploadErrors.set([]);
  }

  triggerUpload(): void {
    const file = this.uploadFile();
    if (!file) {
      this.uploadStatus.set('error');
      this.uploadMessage.set('Select a CSV file before importing.');
      return;
    }

    this.uploadStatus.set('loading');
    this.uploadMessage.set('Import in progress...');
    this.uploadSummary.set(null);
    this.uploadErrors.set([]);

    this.mediaService.importCsv(file, this.uploadType()).subscribe({
      next: (response) => {
        this.uploadStatus.set('success');
        this.uploadMessage.set(response.message);
        this.uploadSummary.set(response.summary);
        this.uploadErrors.set(response.errors);
        this.refreshWatchedList(this.uploadType());
      },
      error: (error: HttpErrorResponse) => {
        this.uploadStatus.set('error');
        this.uploadSummary.set(null);
        this.uploadErrors.set([]);
        this.uploadMessage.set(this.extractErrorMessage(error));
      },
    });
  }

  private loadMovies(): void {
    this.mediaService.getWatchedMovies().subscribe({
      next: (data) => this.movies.set(data),
      error: () => this.movies.set([]),
    });
  }

  private loadSeries(): void {
    this.mediaService.getWatchedSeries().subscribe({
      next: (data) => this.series.set(data),
      error: () => this.series.set([]),
    });
  }

  private refreshWatchedList(type: MediaType): void {
    if (type === 'MOVIE') {
      this.loadMovies();
      return;
    }
    this.loadSeries();
  }

  private extractErrorMessage(error: HttpErrorResponse): string {
    const payload = error.error;
    if (typeof payload?.error === 'string') {
      return payload.error;
    }
    if (typeof payload?.message === 'string') {
      return payload.message;
    }
    return 'CSV import failed.';
  }
}
