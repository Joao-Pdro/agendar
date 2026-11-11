import { Component, EventEmitter, Input, Output } from '@angular/core';

import type { Appointment, Slot } from '../models/scheduling';

/**
 * Grade de um dia: colunas de horário com os agendamentos e os slots livres.
 * Clicar num slot emite o evento para abrir o formulário.
 */
@Component({
  selector: 'app-day-grid',
  standalone: true,
  templateUrl: './day-grid.component.html',
  styleUrl: './day-grid.component.scss',
})
export class DayGridComponent {
  @Input({ required: true }) date = '';
  @Input() slots: Slot[] = [];
  @Input() appointments: Appointment[] = [];

  @Output() readonly slotSelected = new EventEmitter<Slot>();

  onSlotClick(slot: Slot): void {
    this.slotSelected.emit(slot);
  }

  /** `09:00` no fuso local do navegador, a partir do offset recebido. */
  timeOf(isoDate: string): string {
    return new Date(isoDate).toLocaleTimeString('pt-BR', {
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  /** Offset de exibição, vindo do valor recebido da API (ex.: `-03:00`). */
  offsetOf(isoDate: string): string {
    const match = /([+-]\d{2}:\d{2}|Z)$/.exec(isoDate);
    return match ? match[1] : '';
  }
}
