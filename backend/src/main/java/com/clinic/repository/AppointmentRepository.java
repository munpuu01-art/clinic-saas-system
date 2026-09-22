package com.clinic.repository;

import com.clinic.domain.appointment.Appointment;
import com.clinic.domain.appointment.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Optional<Appointment> findByIdAndClinicId(Long id, Long clinicId);
    Optional<Appointment> findByAppointmentNoAndClinicId(String appointmentNo, Long clinicId);

    List<Appointment> findByClinicIdAndDoctorIdAndAppointmentDate(Long clinicId, Long doctorId, LocalDate date);

    List<Appointment> findByClinicIdAndPatientIdOrderByAppointmentDateDesc(Long clinicId, Long patientId);

    @Query("""
           SELECT a FROM Appointment a
           WHERE a.clinicId = :clinicId AND a.doctor.id = :doctorId AND a.appointmentDate = :date
             AND a.status NOT IN (com.clinic.domain.appointment.AppointmentStatus.CANCELLED,
                                  com.clinic.domain.appointment.AppointmentStatus.NO_SHOW)
           """)
    List<Appointment> findActiveByDoctorAndDate(@Param("clinicId") Long clinicId,
                                                @Param("doctorId") Long doctorId,
                                                @Param("date") LocalDate date);

    @Query("""
           SELECT a FROM Appointment a
           WHERE a.clinicId = :clinicId AND a.patient.id = :patientId AND a.appointmentDate = :date
             AND a.status NOT IN (com.clinic.domain.appointment.AppointmentStatus.CANCELLED,
                                  com.clinic.domain.appointment.AppointmentStatus.NO_SHOW)
           """)
    List<Appointment> findActiveByPatientAndDate(@Param("clinicId") Long clinicId,
                                                 @Param("patientId") Long patientId,
                                                 @Param("date") LocalDate date);

    Page<Appointment> findByClinicIdAndAppointmentDateAndStatus(Long clinicId, LocalDate date,
                                                                 AppointmentStatus status, Pageable pageable);

    Page<Appointment> findByClinicIdAndAppointmentDate(Long clinicId, LocalDate date, Pageable pageable);

    long countByClinicIdAndAppointmentDate(Long clinicId, LocalDate date);

    long countByClinicIdAndAppointmentDateAndStatus(Long clinicId, LocalDate date, AppointmentStatus status);

    @Query("""
           SELECT a.doctor.id, COUNT(a) FROM Appointment a
           WHERE a.clinicId = :clinicId AND a.appointmentDate = :date GROUP BY a.doctor.id
           """)
    List<Object[]> countGroupedByDoctor(@Param("clinicId") Long clinicId, @Param("date") LocalDate date);
}
