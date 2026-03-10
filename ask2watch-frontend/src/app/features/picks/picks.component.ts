import { Component, computed, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AppHeaderComponent } from '../../shared/components/app-header/app-header.component';
import { PickCardComponent } from '../../shared/components/pick-card/pick-card.component';
import { MediaDetailDialogComponent } from '../../shared/components/media-detail-dialog/media-detail-dialog.component';
import { StarRatingComponent } from '../../shared/components/star-rating/star-rating.component';
import { PickService } from '../../core/services/pick.service';
import { MediaService } from '../../core/services/media.service';
import { AgentService } from '../../core/services/agent.service';
import { PickResponse } from '../../shared/models/pick.model';
import { of, switchMap } from 'rxjs';

@Component({
  selector: 'app-picks',
  standalone: true,
  imports: [FormsModule, AppHeaderComponent, PickCardComponent, MediaDetailDialogComponent, StarRatingComponent],
  templateUrl: './picks.component.html',
  styleUrl: './picks.component.css',
})
export class PicksComponent implements OnInit {
  picks = signal<PickResponse[]>([]);
  search = signal('');
  selectedItem = signal<PickResponse | null>(null);
  dialogOpen = signal(false);
  generating = signal(false);
  addToWatchedDialogOpen = signal(false);
  selectedPickForWatched = signal<PickResponse | null>(null);
  watchedRating = signal(0);
  watchedComment = signal('');
  savingWatched = signal(false);

  filteredPicks = computed(() => {
    const s = this.search().toLowerCase();
    return s ? this.picks().filter((p) => p.media.title.toLowerCase().includes(s)) : this.picks();
  });

  constructor(
    private pickService: PickService,
    private mediaService: MediaService,
    private agentService: AgentService,
  ) {}

  ngOnInit(): void {
    this.loadPicks();
  }

  loadPicks(): void {
    this.pickService.getAllPicks().subscribe({
      next: (data) => this.picks.set(data),
      error: (err) => console.error('Error loading picks:', err),
    });
  }

  onItemClick(item: PickResponse): void {
    this.selectedItem.set(item);
    this.dialogOpen.set(true);
  }

  onAddToWatched(item: PickResponse): void {
    this.selectedPickForWatched.set(item);
    this.watchedRating.set(0);
    this.watchedComment.set('');
    this.addToWatchedDialogOpen.set(true);
  }

  closeAddToWatchedDialog(): void {
    this.addToWatchedDialogOpen.set(false);
    this.selectedPickForWatched.set(null);
    this.watchedRating.set(0);
    this.watchedComment.set('');
    this.savingWatched.set(false);
  }

  confirmAddToWatched(): void {
    const item = this.selectedPickForWatched();
    if (!item || this.savingWatched()) return;

    const comment = this.watchedComment().trim();
    const rating = this.watchedRating();
    this.savingWatched.set(true);

    this.mediaService.addToWatched({
      tmdbId: item.media.tmdbId,
      mediaType: item.media.mediaType,
      title: item.media.title,
    }).pipe(
      switchMap((watched) => {
        if (!rating && !comment) {
          return of(watched);
        }

        return this.mediaService.updateWatched(watched.watchedId, {
          ...(rating ? { userRating: rating } : {}),
          ...(comment ? { comment } : {}),
        });
      }),
      switchMap(() => this.pickService.removePick(item.pickId))
    ).subscribe({
      next: () => {
        this.picks.update((items) => items.filter((pick) => pick.pickId !== item.pickId));
        if (this.selectedItem()?.pickId === item.pickId) {
          this.dialogOpen.set(false);
          this.selectedItem.set(null);
        }
        this.closeAddToWatchedDialog();
      },
      error: (err) => {
        console.error('Error moving pick to watched:', err);
        this.savingWatched.set(false);
      },
    });
  }

  onRemovePick(item: PickResponse): void {
    this.pickService.removePick(item.pickId).subscribe({
      next: () => {
        this.picks.update((items) => items.filter((pick) => pick.pickId !== item.pickId));
        if (this.selectedItem()?.pickId === item.pickId) {
          this.dialogOpen.set(false);
          this.selectedItem.set(null);
        }
      },
      error: (err) => console.error('Error removing pick:', err),
    });
  }

  generateNewPicks(): void {
    this.generating.set(true);
    this.agentService.generatePicks().subscribe({
      next: () => {
        this.loadPicks();
        this.generating.set(false);
      },
      error: () => this.generating.set(false),
    });
  }
}
