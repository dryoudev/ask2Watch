import { Component, input, computed, output } from '@angular/core';

@Component({
  selector: 'app-star-rating',
  standalone: true,
  templateUrl: './star-rating.component.html',
  styleUrl: './star-rating.component.css',
})
export class StarRatingComponent {
  rating = input(0);
  max = input(5);
  size = input(16);
  editable = input(false);

  ratingChanged = output<number>();

  stars = computed(() =>
    Array.from({ length: this.max() }, (_, i) => i < this.rating())
  );

  onStarClick(index: number): void {
    if (this.editable()) {
      const newRating = index + 1;
      this.ratingChanged.emit(newRating);
    }
  }
}
