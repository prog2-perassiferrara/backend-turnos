package com.prog2.appointment.reservation.application.usecases;

import com.prog2.appointment.reservation.domain.model.ReservationDelivery;
import com.prog2.appointment.reservation.domain.ports.in.ReceiveReservationActionUseCase;
import com.prog2.appointment.reservation.domain.ports.out.ReservationProcessRepository;

/**
 * Entrega el hecho al repositorio atómico; solo un retorno confirmado habilita el ACK en
 * infraestructura.
 */
public final class ReceiveReservationActionUseCaseImpl implements ReceiveReservationActionUseCase {
    private final ReservationProcessRepository repository;

    public ReceiveReservationActionUseCaseImpl(ReservationProcessRepository repository) {
        this.repository = repository;
    }

    @Override
    public void receive(ReservationDelivery delivery) {
        repository.accept(delivery);
    }
}
