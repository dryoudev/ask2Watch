import { Component, ElementRef, input, viewChild } from '@angular/core';

@Component({
  selector: 'app-movie-row',
  standalone: true,
  templateUrl: './movie-row.component.html',
  styleUrl: './movie-row.component.css',
})
export class MovieRowComponent {
  title = input.required<string>();
  icon = input<'eye' | 'sparkles' | ''>('');

  scrollContainer = viewChild<ElementRef>('scrollContainer');

  scroll(direction: 'left' | 'right'): void {
    const el = this.scrollContainer()?.nativeElement;
    if (!el) return;
    const amount = direction === 'left' ? -400 : 400;
    el.scrollBy({ left: amount, behavior: 'smooth' });
  }
}
