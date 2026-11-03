package com.prog2.appointment.reservation.application.usecases;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.prog2.appointment.reservation.domain.model.ReservationAction;
import com.prog2.appointment.reservation.domain.ports.in.ReceiveReservationActionUseCase;
import com.prog2.appointment.reservation.domain.ports.out.ReservationProcessRepository;

/**
 * Entrega el hecho al repositorio atómico; solo un retorno confirmado habilita el ACK en
 * infraestructura.
 */
@Component
@RequiredArgsConstructor
public final class ReceiveReservationActionUseCaseImpl implements ReceiveReservationActionUseCase {
    private final ReservationProcessRepository repository;

    @Override
    public void receive(ReservationAction action) {
        repository.accept(action);
    }
}
