import { Component, effect, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { WatchedMediaResponse } from '../../models/watched.model';
import { PickResponse } from '../../models/pick.model';
import { MediaResponse } from '../../models/media.model';
import { RecommendationDto } from '../../models/recommendation.model';
import { StarRatingComponent } from '../star-rating/star-rating.component';
import { DurationPipe } from '../../pipes/duration.pipe';
import { posterUrl } from '../../utils/tmdb.util';
import { MediaService } from '../../../core/services/media.service';
import { PickService } from '../../../core/services/pick.service';

@Component({
  selector: 'app-media-detail-dialog',
  standalone: true,
  imports: [FormsModule, StarRatingComponent, DurationPipe],
  templateUrl: './media-detail-dialog.component.html',
  styleUrl: './media-detail-dialog.component.css',
})
export class MediaDetailDialogComponent {
  item = input<WatchedMediaResponse | PickResponse | RecommendationDto | null>(null);
  open = input(false);
  openChange = output<boolean>();
  itemUpdated = output<WatchedMediaResponse>();
  recommendationAddedToPick = output<RecommendationDto>();

  showCommentInput = signal(false);
  commentText = signal('');

  constructor(private mediaService: MediaService, private pickService: PickService) {
    effect(() => {
      this.item();
      this.commentText.set('');
      this.showCommentInput.set(false);
    });
  }

  get media(): MediaResponse | null {
    const i = this.item();
    if (!i) return null;
    if ('watchedId' in i) return i.media; // WatchedMediaResponse
    if ('pickId' in i) return i.media; // PickResponse
    if ('media' in i) return i.media; // RecommendationDto
    return null;
  }

  get isWatched(): boolean {
    const i = this.item();
    return !!i && 'watchedId' in i;
  }

  get isRecommendation(): boolean {
    const i = this.item();
    return !!i && 'source' in i && 'reason' in i;
  }

  get recommendation(): RecommendationDto | null {
    const i = this.item();
    return i && this.isRecommendation ? (i as RecommendationDto) : null;
  }

  get watchedData(): WatchedMediaResponse | null {
    const i = this.item();
    return i && 'watchedId' in i ? i : null;
  }

  get poster(): string {
    return posterUrl(this.media?.posterPath ?? null);
  }

  get starsArray(): string[] {
    return this.media?.stars?.split(', ') ?? [];
  }

  close(): void {
    this.openChange.emit(false);
    this.showCommentInput.set(false);
  }

  saveComment(): void {
    const w = this.watchedData;
    if (!w) return;
    this.mediaService.updateWatched(w.watchedId, {
      ...(w.userRating ? { userRating: w.userRating } : {}),
      comment: this.commentText(),
    }).subscribe({
      next: (updated) => {
        this.showCommentInput.set(false);
        this.itemUpdated.emit(updated);
      },
      error: (err) => console.error('Failed to save comment:', err),
    });
  }

  addToPick(): void {
    const rec = this.recommendation;
    if (!rec || !rec.media) return;

    const reason = prompt(`Ajouter "${rec.media.title}" aux Sélections de la Semaine.\n\nRaison:`, rec.reason);
    if (reason === null) return;

    this.pickService.addPick({
      tmdbId: rec.media.tmdbId,
      mediaType: rec.media.mediaType,
      title: rec.media.title,
      reason: reason
    }).subscribe({
      next: () => {
        alert('Ajouté aux Sélections!');
        this.close();
        this.recommendationAddedToPick.emit(rec);
      },
      error: (err) => console.error('Failed to add pick', err),
    });
  }
}
