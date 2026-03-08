import { Component, ElementRef, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AppHeaderComponent } from '../../shared/components/app-header/app-header.component';
import { AgentService } from '../../core/services/agent.service';
import { ChatMessage } from '../../shared/models/agent.model';
import { MediaResponse } from '../../shared/models/media.model';
import { posterUrl } from '../../shared/utils/tmdb.util';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [FormsModule, AppHeaderComponent],
  templateUrl: './chat.component.html',
  styleUrl: './chat.component.css',
})
export class ChatComponent {
  messages = signal<ChatMessage[]>([]);
  inputMessage = signal('');
  loading = signal(false);
  suggestedMedia = signal<MediaResponse[] | null>(null);

  messagesEnd = viewChild<ElementRef>('messagesEnd');

  posterUrl = posterUrl;

  constructor(private agentService: AgentService) {}

  sendMessage(): void {
    const msg = this.inputMessage().trim();
    if (!msg || this.loading()) return;

    this.messages.update((msgs) => [...msgs, { role: 'user', content: msg }]);
    this.inputMessage.set('');
    this.loading.set(true);
    this.scrollToBottom();

    this.agentService.chat(msg).subscribe({
      next: (res) => {
        this.messages.update((msgs) => [...msgs, { role: 'assistant', content: res.message }]);
        this.suggestedMedia.set(res.suggestedMedia);
        this.loading.set(false);
        this.scrollToBottom();
      },
      error: () => {
        this.messages.update((msgs) => [...msgs, { role: 'assistant', content: 'Sorry, something went wrong.' }]);
        this.loading.set(false);
      },
    });
  }

  private scrollToBottom(): void {
    setTimeout(() => this.messagesEnd()?.nativeElement?.scrollIntoView({ behavior: 'smooth' }), 100);
  }
}
