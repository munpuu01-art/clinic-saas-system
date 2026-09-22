package com.clinic.service.impl;

import com.clinic.domain.appointment.AppointmentStatus;
import com.clinic.domain.queue.QueueStatus;
import com.clinic.domain.queue.QueueTicket;
import com.clinic.dto.DashboardResponse;
import com.clinic.event.StatisticsObserver;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.DoctorRepository;
import com.clinic.repository.QueueTicketRepository;
import com.clinic.service.DashboardService;
import com.clinic.service.TenantGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final AppointmentRepository appointmentRepository;
    private final QueueTicketRepository ticketRepository;
    private final DoctorRepository doctorRepository;
    private final StatisticsObserver statisticsObserver;
    private final TenantGuard tenantGuard;

    public DashboardServiceImpl(AppointmentRepository appointmentRepository,
                                QueueTicketRepository ticketRepository,
                                DoctorRepository doctorRepository,
                                StatisticsObserver statisticsObserver, TenantGuard tenantGuard) {
        this.appointmentRepository = appointmentRepository;
        this.ticketRepository = ticketRepository;
        this.doctorRepository = doctorRepository;
        this.statisticsObserver = statisticsObserver;
        this.tenantGuard = tenantGuard;
    }

    @Override
    public DashboardResponse summary(LocalDate date) {
        Long clinicId = tenantGuard.requireCurrentClinicId();
        LocalDate target = date != null ? date : LocalDate.now();

        List<DashboardResponse.DoctorLoad> loads = doctorRepository.findByActiveTrueAndClinicId(clinicId).stream()
                .map(d -> {
                    long appointments = appointmentRepository
                            .findByClinicIdAndDoctorIdAndAppointmentDate(clinicId, d.getId(), target).size();
                    int waiting = (int) ticketRepository
                            .findByClinicIdAndDoctorIdAndQueueDate(clinicId, d.getId(), target)
                            .stream().filter(QueueTicket::isWaiting).count();
                    return new DashboardResponse.DoctorLoad(d.getId(), d.getDisplayName(), appointments, waiting);
                })
                .toList();

        long waitingInQueue = ticketRepository
                .findByClinicIdAndQueueDateAndStatus(clinicId, target, QueueStatus.WAITING).size();

        return new DashboardResponse(
                target,
                appointmentRepository.countByClinicIdAndAppointmentDate(clinicId, target),
                appointmentRepository.countByClinicIdAndAppointmentDateAndStatus(clinicId, target, AppointmentStatus.CONFIRMED),
                appointmentRepository.countByClinicIdAndAppointmentDateAndStatus(clinicId, target, AppointmentStatus.CHECKED_IN),
                appointmentRepository.countByClinicIdAndAppointmentDateAndStatus(clinicId, target, AppointmentStatus.COMPLETED),
                appointmentRepository.countByClinicIdAndAppointmentDateAndStatus(clinicId, target, AppointmentStatus.CANCELLED),
                appointmentRepository.countByClinicIdAndAppointmentDateAndStatus(clinicId, target, AppointmentStatus.NO_SHOW),
                waitingInQueue,
                loads,
                statisticsObserver.snapshot()
        );
    }
}
