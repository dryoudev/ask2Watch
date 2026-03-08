import { Component, computed, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AppHeaderComponent } from '../../shared/components/app-header/app-header.component';
import { MovieCardComponent } from '../../shared/components/movie-card/movie-card.component';
import { MediaDetailDialogComponent } from '../../shared/components/media-detail-dialog/media-detail-dialog.component';
import { MediaService } from '../../core/services/media.service';
import { WatchedMediaResponse } from '../../shared/models/watched.model';

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
    this.mediaService.getWatchedMovies().subscribe({
      next: (data) => this.movies.set(data),
      error: (err) => console.error('Error loading movies:', err),
    });
    this.mediaService.getWatchedSeries().subscribe({
      next: (data) => this.series.set(data),
      error: (err) => console.error('Error loading series:', err),
    });
  }

  onItemClick(item: WatchedMediaResponse): void {
    this.selectedItem.set(item);
    this.dialogOpen.set(true);
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
}
