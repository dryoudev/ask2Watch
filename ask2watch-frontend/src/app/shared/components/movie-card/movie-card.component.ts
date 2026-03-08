import { Component, input, output, signal, OnInit } from '@angular/core';
import { WatchedMediaResponse, UpdateWatchedRequest } from '../../models/watched.model';
import { StarRatingComponent } from '../star-rating/star-rating.component';
import { posterUrl } from '../../utils/tmdb.util';
import { MediaService } from '../../../core/services/media.service';

@Component({
  selector: 'app-movie-card',
  standalone: true,
  imports: [StarRatingComponent],
  templateUrl: './movie-card.component.html',
  styleUrl: './movie-card.component.css',
})
export class MovieCardComponent implements OnInit {
  watched = input.required<WatchedMediaResponse>();
  index = input(0);
  compact = input(false);
  cardClick = output<WatchedMediaResponse>();
  ratingChanged = output<{ watchedId: number; newRating: number }>();

  currentRating = signal(0);

  constructor(private mediaService: MediaService) {}

  ngOnInit(): void {
    this.currentRating.set(this.watched().userRating ?? 0);
  }

  get poster(): string {
    return posterUrl(this.watched().media.posterPath);
  }

  get delay(): string {
    return `${this.index() * 80}ms`;
  }

  onRatingChange(newRating: number): void {
    this.currentRating.set(newRating);
    const request: UpdateWatchedRequest = {
      userRating: newRating,
    };
    this.mediaService.updateWatched(this.watched().watchedId, request).subscribe({
      next: (updated) => {
        this.ratingChanged.emit({ watchedId: this.watched().watchedId, newRating });
      },
      error: (error) => {
        console.error('Failed to update rating', error);
        this.currentRating.set(this.watched().userRating ?? 0);
      },
    });
  }
}
