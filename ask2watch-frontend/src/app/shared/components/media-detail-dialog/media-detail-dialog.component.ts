import { Component, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { WatchedMediaResponse } from '../../models/watched.model';
import { PickResponse } from '../../models/pick.model';
import { MediaResponse } from '../../models/media.model';
import { StarRatingComponent } from '../star-rating/star-rating.component';
import { DurationPipe } from '../../pipes/duration.pipe';
import { posterUrl } from '../../utils/tmdb.util';
import { MediaService } from '../../../core/services/media.service';

@Component({
  selector: 'app-media-detail-dialog',
  standalone: true,
  imports: [FormsModule, StarRatingComponent, DurationPipe],
  templateUrl: './media-detail-dialog.component.html',
  styleUrl: './media-detail-dialog.component.css',
})
export class MediaDetailDialogComponent {
  item = input<WatchedMediaResponse | PickResponse | null>(null);
  open = input(false);
  openChange = output<boolean>();

  showCommentInput = signal(false);
  commentText = signal('');

  constructor(private mediaService: MediaService) {}

  get media(): MediaResponse | null {
    const i = this.item();
    if (!i) return null;
    return 'watchedId' in i ? i.media : i.media;
  }

  get isWatched(): boolean {
    const i = this.item();
    return !!i && 'watchedId' in i;
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
      userRating: w.userRating ?? 0,
      comment: this.commentText(),
    }).subscribe(() => {
      this.showCommentInput.set(false);
    });
  }
}
