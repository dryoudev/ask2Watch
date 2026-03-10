import { Component, computed, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AppHeaderComponent } from '../../shared/components/app-header/app-header.component';
import { MovieRowComponent } from '../../shared/components/movie-row/movie-row.component';
import { MovieCardComponent } from '../../shared/components/movie-card/movie-card.component';
import { MediaCardComponent } from '../../shared/components/media-card/media-card.component';
import { PickCardComponent } from '../../shared/components/pick-card/pick-card.component';
import { MediaDetailDialogComponent } from '../../shared/components/media-detail-dialog/media-detail-dialog.component';
import { MediaService } from '../../core/services/media.service';
import { PickService } from '../../core/services/pick.service';
import { AgentService } from '../../core/services/agent.service';
import { AuthService } from '../../core/services/auth.service';
import { WatchedMediaResponse } from '../../shared/models/watched.model';
import { MediaResponse } from '../../shared/models/media.model';
import { PickResponse } from '../../shared/models/pick.model';
import { ChatMessage } from '../../shared/models/agent.model';
import { RecommendationDto } from '../../shared/models/recommendation.model';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [FormsModule, AppHeaderComponent, MovieRowComponent, MovieCardComponent, MediaCardComponent, PickCardComponent, MediaDetailDialogComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
})
export class HomeComponent implements OnInit {
  watchedMovies = signal<WatchedMediaResponse[]>([]);
  topRatedMovies = signal<WatchedMediaResponse[]>([]);
  trendingMovies = signal<MediaResponse[]>([]);
  tmdbRecommendations = signal<RecommendationDto[]>([]);
  picks = signal<PickResponse[]>([]);
  selectedItem = signal<WatchedMediaResponse | PickResponse | RecommendationDto | null>(null);
  dialogOpen = signal(false);

  // Chat avec Dobby
  chatMessages = signal<ChatMessage[]>([]);
  chatInput = signal('');
  chatLoading = signal(false);
  chatOpen = signal(false);

  greeting = computed(() => {
    const name = this.authService.username();
    return name ? `Bonjour, ${name}` : 'Bonjour';
  });

  constructor(
    private mediaService: MediaService,
    private pickService: PickService,
    private agentService: AgentService,
    private authService: AuthService,
  ) {}

  ngOnInit(): void {
    this.mediaService.getWatchedMovies().subscribe({
      next: (data) => {
        this.watchedMovies.set(data.slice(0, 10));
        this.topRatedMovies.set(data.filter(m => m.userRating && m.userRating >= 4).slice(0, 10));
      },
      error: (err) => console.error('Error loading watched movies:', err),
    });
    this.pickService.getCurrentPicks().subscribe({
      next: (data) => this.picks.set(data),
      error: (err) => console.error('Error loading picks:', err),
    });
    this.mediaService.getRecommendations(10).subscribe({
      next: (data: RecommendationDto[]) => this.tmdbRecommendations.set(data),
      error: (err) => console.error('Error loading recommendations:', err),
    });
    this.mediaService.getTrending(10).subscribe({
      next: (data) => this.trendingMovies.set(data),
      error: (err) => console.error('Error loading trending:', err),
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

  onTrendingClick(event: { media: MediaResponse; recommendation: RecommendationDto | null }): void {
    // Wrap as a RecommendationDto so the dialog can display it
    const rec: RecommendationDto = {
      media: event.media,
      source: 'Tendance',
      reason: 'Film tendance de la semaine sur TMDB',
    };
    this.selectedItem.set(rec);
    this.dialogOpen.set(true);
  }

  onRecommendationClick(event: { media: any; recommendation: RecommendationDto | null }): void {
    if (event.recommendation) {
      this.selectedItem.set(event.recommendation);
      this.dialogOpen.set(true);
    }
  }

  onRecommendationAddedToPick(rec: RecommendationDto): void {
    // Reload picks after adding
    this.pickService.getCurrentPicks().subscribe({
      next: (data) => this.picks.set(data),
    });
  }

  sendChatMessage(): void {
    const msg = this.chatInput().trim();
    if (!msg || this.chatLoading()) return;

    this.chatMessages.update((msgs) => [...msgs, { role: 'user', content: msg }]);
    this.chatInput.set('');
    this.chatLoading.set(true);

    this.agentService.chat(msg).subscribe({
      next: (res) => {
        this.chatMessages.update((msgs) => [...msgs, { role: 'assistant', content: res.message }]);
        this.chatLoading.set(false);
      },
      error: () => {
        this.chatMessages.update((msgs) => [...msgs, { role: 'assistant', content: 'Dobby a un problème... réessayez!' }]);
        this.chatLoading.set(false);
      },
    });
  }
}
