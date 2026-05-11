package com.monitor.call.infrastructure.adapters.out.persistence.repositories;

import com.monitor.call.infrastructure.adapters.out.persistence.entities.AppointmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppointmentJpaRepository extends JpaRepository<AppointmentEntity, Long> {

    List<AppointmentEntity> findByLeadId(Long leadId);

    List<AppointmentEntity> findByAgentId(Long agentId);

    // Todas las citas del agente — sin filtro de fecha (frontend filtra)
    @Query("SELECT a FROM AppointmentEntity a WHERE a.agentId = :agentId " +
           "ORDER BY a.appointmentDate DESC, a.appointmentTime DESC")
    List<AppointmentEntity> findAllByAgent(@Param("agentId") Long agentId);

    // Todas las citas de los agentes del admin — sin filtro de fecha
    @Query("SELECT a FROM AppointmentEntity a WHERE a.agentId IN :agentIds " +
           "ORDER BY a.appointmentDate DESC, a.appointmentTime DESC")
    List<AppointmentEntity> findAllByAgents(@Param("agentIds") List<Long> agentIds);

    // Citas vencidas sin confirmar — para alertas
    @Query("SELECT a FROM AppointmentEntity a WHERE a.agentId = :agentId " +
           "AND a.status = 'SCHEDULED' " +
           "AND a.appointmentDate < CURRENT_DATE")
    List<AppointmentEntity> findOverdueByAgent(@Param("agentId") Long agentId);
}
