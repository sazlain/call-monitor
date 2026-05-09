package com.monitor.call.infrastructure.adapters.out.persistence.repositories;

import com.monitor.call.infrastructure.adapters.out.persistence.entities.AgentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AgentJpaRepository extends JpaRepository<AgentEntity, Long> {

    Optional<AgentEntity> findByExtension(String extension);
    Optional<AgentEntity> findByUserId(Long userId);
    List<AgentEntity> findByGroupId(Long groupId);
    List<AgentEntity> findByGroupIdAndActiveTrue(Long groupId);
    boolean existsByExtension(String extension);
    boolean existsByUserId(Long userId);

    /** Todas las extensiones de un grupo — util para queries de estadisticas */
    @Query("SELECT a.extension FROM AgentEntity a WHERE a.group.id = :groupId AND a.active = true")
    List<String> findExtensionsByGroupId(@Param("groupId") Long groupId);

    /** Todas las extensiones de todos los grupos de un admin */
    @Query("SELECT a.extension FROM AgentEntity a WHERE a.group.adminId = :adminId AND a.active = true")
    List<String> findExtensionsByAdminId(@Param("adminId") Long adminId);

    /** Agentes de todos los grupos de un admin */
    @Query("SELECT a FROM AgentEntity a WHERE a.group.adminId = :adminId AND a.active = true")
    List<AgentEntity> findByAdminId(@Param("adminId") Long adminId);
}
