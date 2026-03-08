import { Component, OnInit, signal } from '@angular/core';
import { AppHeaderComponent } from '../../shared/components/app-header/app-header.component';
import { MovieRowComponent } from '../../shared/components/movie-row/movie-row.component';
import { MovieCardComponent } from '../../shared/components/movie-card/movie-card.component';
import { PickCardComponent } from '../../shared/components/pick-card/pick-card.component';
import { MediaDetailDialogComponent } from '../../shared/components/media-detail-dialog/media-detail-dialog.component';
import { MediaService } from '../../core/services/media.service';
import { PickService } from '../../core/services/pick.service';
import { WatchedMediaResponse } from '../../shared/models/watched.model';
import { PickResponse } from '../../shared/models/pick.model';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [AppHeaderComponent, MovieRowComponent, MovieCardComponent, PickCardComponent, MediaDetailDialogComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
})
export class HomeComponent implements OnInit {
  watchedMovies = signal<WatchedMediaResponse[]>([]);
  picks = signal<PickResponse[]>([]);
  selectedItem = signal<WatchedMediaResponse | PickResponse | null>(null);
  dialogOpen = signal(false);

  constructor(
    private mediaService: MediaService,
    private pickService: PickService,
  ) {}

  ngOnInit(): void {
    this.mediaService.getWatchedMovies().subscribe({
      next: (data) => this.watchedMovies.set(data.slice(0, 10)),
      error: (err) => console.error('Error loading watched movies:', err),
    });
    this.pickService.getCurrentPicks().subscribe({
      next: (data) => this.picks.set(data),
      error: (err) => console.error('Error loading picks:', err),
    });
  }

  onWatchedClick(item: WatchedMediaResponse): void {
    this.selectedItem.set(item);
    this.dialogOpen.set(true);
  }

  onPickClick(item: PickResponse): void {
    this.selectedItem.set(item);
    this.dialogOpen.set(true);
  }
}
