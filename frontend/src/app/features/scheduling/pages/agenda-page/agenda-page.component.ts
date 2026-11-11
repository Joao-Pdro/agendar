import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AppointmentFormComponent } from '../../components/appointment-form.component';
import { DayGridComponent } from '../../components/day-grid.component';
import { WeekGridComponent, type WeekDay } from '../../components/week-grid.component';
import type { Slot } from '../../models/scheduling';
import { AgendaStore, toIsoDate } from '../../services/agenda-store';
import { newIdempotencyKey } from '../../services/idempotency-key';
import { MOCK_SERVICE } from '../../services/mock-agenda';

@Component({
  selector: 'app-agenda-page',
  standalone: true,
  imports: [FormsModule, DayGridComponent, WeekGridComponent, AppointmentFormComponent],
  templateUrl: './agenda-page.component.html',
  styleUrl: './agenda-page.component.scss',
})
export class AgendaPageComponent implements OnInit {
  readonly store = inject(AgendaStore);

  readonly serviceLabel = MOCK_SERVICE.name;
  /** Ainda não há endpoint que liste prestadores; o id é informado no topo. */
  readonly providerIdInput = signal('');

  readonly selectedSlot = signal<Slot | null>(null);
  readonly selectedSlotKey = signal('');
  readonly submitting = signal(false);

  readonly weekDays = signal<WeekDay[]>([]);

  readonly hasProvider = computed(() => this.store.selectedProviderId() !== '');

  ngOnInit(): void {
    this.reload();
  }

  applyProvider(): void {
    this.store.selectedProviderId.set(this.providerIdInput().trim());
    this.reload();
  }

  setView(view: 'dia' | 'semana'): void {
    this.store.view.set(view);
    this.reload();
  }

  changeDate(date: string): void {
    if (date === '') {
      return;
    }
    this.store.selectedDate.set(date);
    this.reload();
  }

  onSlotSelected(slot: Slot): void {
    this.selectedSlot.set(slot);
    // Uma chave por tentativa: o retry de confirmação reusa este mesmo valor.
    this.selectedSlotKey.set(newIdempotencyKey());
  }

  onWeekDaySelected(date: string): void {
    this.store.selectedDate.set(date);
    this.store.view.set('dia');
    this.reload();
  }

  confirmBooking(): void {
    const slot = this.selectedSlot();
    if (!slot) {
      return;
    }

    this.submitting.set(true);
    this.store.createAppointment(slot, this.selectedSlotKey()).subscribe(() => {
      this.submitting.set(false);
      if (!this.store.error()) {
        this.closeForm();
      }
    });
  }

  closeForm(): void {
    this.selectedSlot.set(null);
    this.selectedSlotKey.set('');
  }

  private reload(): void {
    if (!this.hasProvider()) {
      this.store.slots.set([]);
      this.weekDays.set([]);
      return;
    }

    this.store.loadSlots();
    this.store.loadAppointments();

    if (this.store.view() === 'semana') {
      this.buildWeek();
    }
  }

  /** Monta as 7 colunas da semana corrente a partir dos agendamentos carregados. */
  private buildWeek(): void {
    const base = new Date(`${this.store.selectedDate()}T12:00:00`);
    const start = new Date(base);
    start.setDate(start.getDate() - start.getDay());

    const days: WeekDay[] = [];
    for (let index = 0; index < 7; index += 1) {
      const date = new Date(start);
      date.setDate(start.getDate() + index);
      const isoDate = toIsoDate(date);
      days.push({
        date: isoDate,
        label: date.toLocaleDateString('pt-BR', { weekday: 'short' }),
        appointments: this.store
          .appointments()
          .filter((appointment) => appointment.startsAt.startsWith(isoDate)),
        slots: [],
      });
    }

    this.weekDays.set(days);
  }
}
