import { Component, input, output } from '@angular/core';
import { PickResponse } from '../../models/pick.model';
import { posterUrl } from '../../utils/tmdb.util';

@Component({
  selector: 'app-pick-card',
  standalone: true,
  templateUrl: './pick-card.component.html',
  styleUrl: './pick-card.component.css',
})
export class PickCardComponent {
  pick = input.required<PickResponse>();
  index = input(0);
  compact = input(false);
  cardClick = output<PickResponse>();

  get poster(): string {
    return posterUrl(this.pick().media.posterPath);
  }

  get delay(): string {
    return `${this.index() * 80}ms`;
  }
}
