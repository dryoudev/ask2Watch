import { Pipe, PipeTransform } from '@angular/core';
import { formatDuration } from '../utils/duration.util';

@Pipe({
  name: 'duration',
  standalone: true,
})
export class DurationPipe implements PipeTransform {
  transform(value: number | null): string {
    return formatDuration(value);
  }
}
