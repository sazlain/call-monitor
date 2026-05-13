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

    @Query("SELECT a FROM AgentEntity a LEFT JOIN FETCH a.group WHERE a.extension = :extension")
    Optional<AgentEntity> findByExtension(@Param("extension") String extension);

    @Query("SELECT a FROM AgentEntity a LEFT JOIN FETCH a.group WHERE a.userId = :userId")
    Optional<AgentEntity> findByUserId(@Param("userId") Long userId);

    @Query("SELECT a FROM AgentEntity a LEFT JOIN FETCH a.group WHERE a.group.id = :groupId")
    List<AgentEntity> findByGroupId(@Param("groupId") Long groupId);

    @Query("SELECT a FROM AgentEntity a LEFT JOIN FETCH a.group WHERE a.group.id = :groupId AND a.active = true")
    List<AgentEntity> findByGroupIdAndActiveTrue(@Param("groupId") Long groupId);

    boolean existsByExtension(String extension);
    boolean existsByUserId(Long userId);

    /** Todas las extensiones de un grupo — util para queries de estadisticas */
    @Query("SELECT a.extension FROM AgentEntity a WHERE a.group.id = :groupId AND a.active = true")
    List<String> findExtensionsByGroupId(@Param("groupId") Long groupId);

    /** Todas las extensiones de todos los grupos de un admin */
    @Query("SELECT a.extension FROM AgentEntity a WHERE a.group.adminId = :adminId AND a.active = true")
    List<String> findExtensionsByAdminId(@Param("adminId") Long adminId);

    /** Agentes de todos los grupos de un admin */
    @Query("SELECT a FROM AgentEntity a JOIN FETCH a.group WHERE a.group.adminId = :adminId AND a.active = true")
    List<AgentEntity> findByAdminId(@Param("adminId") Long adminId);
}
