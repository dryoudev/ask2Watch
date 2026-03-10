import { Component, input, output } from '@angular/core';
import { MediaResponse } from '../../models/media.model';
import { RecommendationDto } from '../../models/recommendation.model';
import { posterUrl } from '../../utils/tmdb.util';

@Component({
  selector: 'app-media-card',
  standalone: true,
  templateUrl: './media-card.component.html',
  styleUrl: './media-card.component.css',
})
export class MediaCardComponent {
  media = input.required<MediaResponse>();
  recommendation = input<RecommendationDto | null>(null);
  index = input(0);
  compact = input(false);
  cardClick = output<{ media: MediaResponse; recommendation: RecommendationDto | null }>();

  get poster(): string {
    return posterUrl(this.media().posterPath);
  }

  get delay(): string {
    return `${this.index() * 80}ms`;
  }

  get source(): string | null {
    return this.recommendation()?.source ?? null;
  }

  get sourceColor(): string {
    const src = this.source;
    if (src === 'Dobby') return 'bg-purple-500';
    if (src === 'Films Similaires') return 'bg-blue-500';
    return 'bg-gray-500';
  }

  onCardClick(): void {
    this.cardClick.emit({ media: this.media(), recommendation: this.recommendation() });
  }
}
