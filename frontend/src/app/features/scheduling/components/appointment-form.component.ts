import { Component, EventEmitter, Input, Output } from '@angular/core';

import type { ApiError } from '../../../core/http/api-error';
import type { Slot } from '../models/scheduling';

/**
 * Formulário de agendamento aberto por um slot.
 *
 * A `Idempotency-Key` é criada uma vez por abertura e mantida no retry: reenviar
 * com a mesma chave devolve o mesmo agendamento no backend, em vez de criar outro.
 */
@Component({
  selector: 'app-appointment-form',
  standalone: true,
  templateUrl: './appointment-form.component.html',
  styleUrl: './appointment-form.component.scss',
})
export class AppointmentFormComponent {
  @Input({ required: true }) slot!: Slot;
  /** Chave da tentativa atual, gerada pela página para sobreviver ao retry. */
  @Input({ required: true }) idempotencyKey!: string;
  @Input() submitting = false;
  @Input() error: ApiError | null = null;
  @Input() serviceLabel = '';

  @Output() readonly confirmed = new EventEmitter<void>();
  @Output() readonly dismissed = new EventEmitter<void>();

  /** Mensagem de conflito (409) vinda de `ApiError.title` + `ApiError.detail`. */
  get conflictMessage(): string | null {
    if (!this.error || this.error.status !== 409) {
      return null;
    }
    return this.error.detail
      ? `${this.error.title}: ${this.error.detail}`
      : this.error.title;
  }

  /** Qualquer outro erro que precise aparecer para o usuário. */
  get otherError(): ApiError | null {
    return this.error && this.error.status !== 409 ? this.error : null;
  }

  get slotLabel(): string {
    return this.formatRange();
  }

  onConfirm(): void {
    this.confirmed.emit();
  }

  onCancel(): void {
    this.dismissed.emit();
  }

  private formatRange(): string {
    const start = new Date(this.slot.startsAt);
    const end = new Date(this.slot.endsAt);
    const format = (date: Date): string =>
      date.toLocaleString('pt-BR', {
        day: '2-digit',
        month: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      });
    return `${format(start)} – ${format(end)}`;
  }
}
