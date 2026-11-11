package br.com.agendar.scheduling.internal;

import br.com.agendar.scheduling.api.AppointmentView;
import br.com.agendar.scheduling.api.SchedulingFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
class SchedulingFacadeImpl implements SchedulingFacade {

    private final AppointmentService appointments;

    SchedulingFacadeImpl(AppointmentService appointments) {
        this.appointments = appointments;
    }

    @Override
    @Transactional
    public AppointmentView agendar(AgendarCommand comando) {
        return appointments.agendar(comando);
    }

    /**
     * Não implementado nesta issue: busca de agendamento por id fica para a lista de agendamentos.
     */
    public AppointmentView buscar(UUID tenantId, UUID appointmentId) {
        throw new UnsupportedOperationException("busca de agendamento não implementada");
    }
}
