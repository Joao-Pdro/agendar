import { Component, EventEmitter, Input, Output } from '@angular/core';

import type { Appointment, Slot } from '../models/scheduling';

interface WeekDay {
  readonly date: string;
  readonly label: string;
  readonly appointments: Appointment[];
  readonly slots: Slot[];
}

/**
 * Grade da semana: uma coluna por dia, com os agendamentos e a contagem de
 * slots livres de cada dia.
 */
@Component({
  selector: 'app-week-grid',
  standalone: true,
  templateUrl: './week-grid.component.html',
  styleUrl: './week-grid.component.scss',
})
export class WeekGridComponent {
  @Input({ required: true }) days: WeekDay[] = [];

  @Output() readonly daySelected = new EventEmitter<string>();

  appointmentsOf(day: WeekDay): Appointment[] {
    return day.appointments;
  }

  timeOf(isoDate: string): string {
    return new Date(isoDate).toLocaleTimeString('pt-BR', {
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  onDayClick(date: string): void {
    this.daySelected.emit(date);
  }
}

export type { WeekDay };
