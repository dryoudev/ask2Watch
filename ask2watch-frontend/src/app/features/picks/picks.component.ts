import { Component, computed, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AppHeaderComponent } from '../../shared/components/app-header/app-header.component';
import { PickCardComponent } from '../../shared/components/pick-card/pick-card.component';
import { MediaDetailDialogComponent } from '../../shared/components/media-detail-dialog/media-detail-dialog.component';
import { PickService } from '../../core/services/pick.service';
import { AgentService } from '../../core/services/agent.service';
import { PickResponse } from '../../shared/models/pick.model';

@Component({
  selector: 'app-picks',
  standalone: true,
  imports: [FormsModule, AppHeaderComponent, PickCardComponent, MediaDetailDialogComponent],
  templateUrl: './picks.component.html',
  styleUrl: './picks.component.css',
})
export class PicksComponent implements OnInit {
  picks = signal<PickResponse[]>([]);
  search = signal('');
  selectedItem = signal<PickResponse | null>(null);
  dialogOpen = signal(false);
  generating = signal(false);

  filteredPicks = computed(() => {
    const s = this.search().toLowerCase();
    return s ? this.picks().filter((p) => p.media.title.toLowerCase().includes(s)) : this.picks();
  });

  constructor(
    private pickService: PickService,
    private agentService: AgentService,
  ) {}

  ngOnInit(): void {
    this.loadPicks();
  }

  loadPicks(): void {
    this.pickService.getAllPicks().subscribe((data) => this.picks.set(data));
  }

  onItemClick(item: PickResponse): void {
    this.selectedItem.set(item);
    this.dialogOpen.set(true);
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
