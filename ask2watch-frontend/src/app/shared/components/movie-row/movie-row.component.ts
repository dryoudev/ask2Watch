import { Component, ElementRef, input, signal, viewChild, AfterViewInit } from '@angular/core';

@Component({
  selector: 'app-movie-row',
  standalone: true,
  templateUrl: './movie-row.component.html',
  styleUrl: './movie-row.component.css',
})
export class MovieRowComponent implements AfterViewInit {
  title = input.required<string>();
  icon = input<'eye' | 'sparkles' | ''>('');

  scrollContainer = viewChild<ElementRef>('scrollContainer');
  canScrollLeft = signal(false);
  canScrollRight = signal(false);

  ngAfterViewInit(): void {
    setTimeout(() => this.updateScrollState(), 100);
  }

  scroll(direction: 'left' | 'right'): void {
    const el = this.scrollContainer()?.nativeElement;
    if (!el) return;
    const amount = direction === 'left' ? -400 : 400;
    el.scrollBy({ left: amount, behavior: 'smooth' });
  }

  updateScrollState(): void {
    const el = this.scrollContainer()?.nativeElement;
    if (!el) return;
    this.canScrollLeft.set(el.scrollLeft > 0);
    this.canScrollRight.set(el.scrollLeft + el.clientWidth < el.scrollWidth - 1);
  }
}
