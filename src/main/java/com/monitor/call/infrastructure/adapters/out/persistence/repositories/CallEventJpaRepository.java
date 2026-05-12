package com.monitor.call.infrastructure.adapters.out.persistence.repositories;

import com.monitor.call.domain.enums.CallStatus;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.CallEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CallEventJpaRepository extends JpaRepository<CallEventEntity, Long>,
        JpaSpecificationExecutor<CallEventEntity> {

    List<CallEventEntity> findByCallId(String callId);
    Optional<CallEventEntity> findTopByCallIdOrderByCreatedAtDesc(String callId);
    List<CallEventEntity> findByCallerExtension(String callerExtension);

    // ── Bloque 1+2: KPIs de volumen ──────────────────────────────────────────

    @Query("SELECT COUNT(DISTINCT e.callId) FROM CallEventEntity e WHERE e.callerExtension = :ext AND e.callStatus = 'CALLING' AND e.createdAt BETWEEN :from AND :to")
    long countTotalCalls(@Param("ext") String ext, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query("SELECT COUNT(DISTINCT e.callId) FROM CallEventEntity e WHERE e.callerExtension = :ext AND e.callStatus = 'ANSWER' AND e.createdAt BETWEEN :from AND :to")
    long countAnsweredCalls(@Param("ext") String ext, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query("SELECT COUNT(DISTINCT e.callId) FROM CallEventEntity e WHERE e.callerExtension = :ext AND e.callStatus IN ('NOANSWER','BUSY','CANCEL','CONGESTION','CHANUNAVAIL') AND e.createdAt BETWEEN :from AND :to")
    long countMissedCalls(@Param("ext") String ext, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query("SELECT COUNT(DISTINCT e.callId) FROM CallEventEntity e WHERE e.callerExtension = :ext AND e.callFlow = 'out' AND e.callStatus = 'CALLING' AND e.createdAt BETWEEN :from AND :to")
    long countOutboundCalls(@Param("ext") String ext, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query("SELECT COUNT(DISTINCT e.callId) FROM CallEventEntity e WHERE e.callerExtension = :ext AND e.callFlow = 'in' AND e.callStatus = 'CALLING' AND e.createdAt BETWEEN :from AND :to")
    long countInboundCalls(@Param("ext") String ext, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    // ── Bloque 2: Duracion — nativeQuery porque JPQL no soporta self-JOIN con ON ──

    @Query(value = "SELECT COALESCE(SUM(EXTRACT(EPOCH FROM (h.created_at - a.created_at))), 0) FROM call_events a JOIN call_events h ON h.call_id = a.call_id AND h.call_status = 'HANGUP' WHERE a.caller_extension = :ext AND a.call_status = 'ANSWER' AND a.created_at BETWEEN :from AND :to", nativeQuery = true)
    Double sumDurationSeconds(@Param("ext") String ext, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query(value = "SELECT MAX(EXTRACT(EPOCH FROM (h.created_at - a.created_at))) FROM call_events a JOIN call_events h ON h.call_id = a.call_id AND h.call_status = 'HANGUP' WHERE a.caller_extension = :ext AND a.call_status = 'ANSWER' AND a.created_at BETWEEN :from AND :to", nativeQuery = true)
    Double maxDurationSeconds(@Param("ext") String ext, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query(value = "SELECT MIN(EXTRACT(EPOCH FROM (h.created_at - a.created_at))) FROM call_events a JOIN call_events h ON h.call_id = a.call_id AND h.call_status = 'HANGUP' WHERE a.caller_extension = :ext AND a.call_status = 'ANSWER' AND a.created_at BETWEEN :from AND :to AND EXTRACT(EPOCH FROM (h.created_at - a.created_at)) > 0", nativeQuery = true)
    Double minDurationSeconds(@Param("ext") String ext, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query(value = "SELECT COUNT(DISTINCT a.call_id) FROM call_events a JOIN call_events h ON h.call_id = a.call_id AND h.call_status = 'HANGUP' WHERE a.caller_extension = :ext AND a.call_status = 'ANSWER' AND a.created_at BETWEEN :from AND :to AND EXTRACT(EPOCH FROM (h.created_at - a.created_at)) < 30", nativeQuery = true)
    long countShortCalls(@Param("ext") String ext, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query(value = "SELECT COUNT(DISTINCT a.call_id) FROM call_events a JOIN call_events h ON h.call_id = a.call_id AND h.call_status = 'HANGUP' WHERE a.caller_extension = :ext AND a.call_status = 'ANSWER' AND a.created_at BETWEEN :from AND :to AND EXTRACT(EPOCH FROM (h.created_at - a.created_at)) > 600", nativeQuery = true)
    long countLongCalls(@Param("ext") String ext, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    // ── Bloque 3+7: Tendencias temporales ────────────────────────────────────

    @Query(value = "SELECT EXTRACT(HOUR FROM created_at) AS hour, COUNT(DISTINCT call_id) FROM call_events WHERE caller_extension = :ext AND call_status = 'CALLING' AND created_at BETWEEN :from AND :to GROUP BY EXTRACT(HOUR FROM created_at) ORDER BY hour", nativeQuery = true)
    List<Object[]> countByHour(@Param("ext") String ext, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query(value = "SELECT CAST(created_at AS date), COUNT(DISTINCT call_id) FROM call_events WHERE caller_extension = :ext AND call_status = 'CALLING' AND created_at BETWEEN :from AND :to GROUP BY CAST(created_at AS date) ORDER BY CAST(created_at AS date)", nativeQuery = true)
    List<Object[]> countByDay(@Param("ext") String ext, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query(value = "SELECT EXTRACT(DOW FROM created_at) AS dow, COUNT(DISTINCT call_id) FROM call_events WHERE caller_extension = :ext AND call_status = 'CALLING' AND created_at BETWEEN :from AND :to GROUP BY EXTRACT(DOW FROM created_at) ORDER BY dow", nativeQuery = true)
    List<Object[]> countByDayOfWeek(@Param("ext") String ext, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    // ── Bloque 5: Estado en tiempo real ──────────────────────────────────────

    @Query(value = "SELECT DISTINCT caller_extension FROM call_events e WHERE caller_extension IN :extensions AND call_status IN ('CALLING','ANSWER') AND created_at = (SELECT MAX(e2.created_at) FROM call_events e2 WHERE e2.call_id = e.call_id)", nativeQuery = true)
    List<String> findActiveExtensions(@Param("extensions") List<String> extensions);

    @Query("SELECT e FROM CallEventEntity e WHERE e.callerExtension = :ext ORDER BY e.createdAt DESC")
    List<CallEventEntity> findLastEventsByExtension(@Param("ext") String ext, Pageable pageable);

    default Optional<CallEventEntity> findLastEventByExtension(String ext) {
        List<CallEventEntity> results = findLastEventsByExtension(ext,
                org.springframework.data.domain.PageRequest.of(0, 1));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Query("SELECT e FROM CallEventEntity e WHERE e.callerExtension IN :extensions ORDER BY e.createdAt DESC")
    List<CallEventEntity> findRecentEvents(@Param("extensions") List<String> extensions, Pageable pageable);

    // ── Bloque 6: Resumen por multiples extensiones ───────────────────────────

    @Query(value = "SELECT caller_extension, COUNT(DISTINCT CASE WHEN call_status = 'CALLING' THEN call_id END), COUNT(DISTINCT CASE WHEN call_status = 'ANSWER' THEN call_id END), COUNT(DISTINCT CASE WHEN call_status IN ('NOANSWER','BUSY','CANCEL','CONGESTION','CHANUNAVAIL') THEN call_id END) FROM call_events WHERE caller_extension IN :extensions AND created_at BETWEEN :from AND :to GROUP BY caller_extension", nativeQuery = true)
    List<Object[]> getCallSummaryByExtensions(@Param("extensions") List<String> extensions, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query(value = "SELECT CAST(created_at AS date), caller_extension, COUNT(DISTINCT call_id) FROM call_events WHERE caller_extension IN :extensions AND call_status = 'CALLING' AND created_at BETWEEN :from AND :to GROUP BY CAST(created_at AS date), caller_extension ORDER BY CAST(created_at AS date)", nativeQuery = true)
    List<Object[]> countByDayAndExtension(@Param("extensions") List<String> extensions, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query(value = "SELECT COALESCE(SUM(EXTRACT(EPOCH FROM (h.created_at - a.created_at))), 0) FROM call_events a JOIN call_events h ON h.call_id = a.call_id AND h.call_status = 'HANGUP' WHERE a.caller_extension IN :extensions AND a.call_status = 'ANSWER' AND a.created_at BETWEEN :from AND :to", nativeQuery = true)
    Double sumDurationByExtensions(@Param("extensions") List<String> extensions, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    // ── Bloque 9: Alertas ─────────────────────────────────────────────────────

    @Query(value = "SELECT DISTINCT e.caller_extension FROM call_events e WHERE e.caller_extension IN :extensions AND e.call_status IN ('CALLING','ANSWER') AND e.created_at = (SELECT MAX(e2.created_at) FROM call_events e2 WHERE e2.call_id = e.call_id) AND EXTRACT(EPOCH FROM (NOW() - e.created_at)) > :thresholdSeconds", nativeQuery = true)
    List<String> findLongActiveCalls(@Param("extensions") List<String> extensions, @Param("thresholdSeconds") long thresholdSeconds);

    @Query(value = "SELECT DISTINCT caller_extension FROM call_events WHERE caller_extension IN :extensions AND caller_extension NOT IN (SELECT DISTINCT caller_extension FROM call_events WHERE created_at > :since)", nativeQuery = true)
    List<String> findInactiveExtensions(@Param("extensions") List<String> extensions, @Param("since") OffsetDateTime since);

    // ── Historial del agente (filtrado por lista de extensiones) ─────────────

    @Query(value = """
        SELECT * FROM call_events c
        WHERE c.caller_extension IN :extensions
          AND (CAST(:from AS timestamptz) IS NULL OR c.created_at >= CAST(:from AS timestamptz))
          AND (CAST(:to AS timestamptz)   IS NULL OR c.created_at <= CAST(:to AS timestamptz))
          AND (CAST(:status AS text)      IS NULL OR c.call_status  = CAST(:status AS text))
        ORDER BY c.created_at DESC
        """,
        countQuery = """
        SELECT COUNT(*) FROM call_events c
        WHERE c.caller_extension IN :extensions
          AND (CAST(:from AS timestamptz) IS NULL OR c.created_at >= CAST(:from AS timestamptz))
          AND (CAST(:to AS timestamptz)   IS NULL OR c.created_at <= CAST(:to AS timestamptz))
          AND (CAST(:status AS text)      IS NULL OR c.call_status  = CAST(:status AS text))
        """,
        nativeQuery = true)
    Page<CallEventEntity> findHistory(
            @Param("extensions") List<String> extensions,
            @Param("from")       OffsetDateTime from,
            @Param("to")         OffsetDateTime to,
            @Param("status")     String status,
            Pageable pageable
    );

    // ── Historial admin (todas las extensiones, filtro opcional por extensión) ─

    @Query(value = """
        SELECT * FROM call_events c
        WHERE (CAST(:from AS timestamptz)      IS NULL OR c.created_at >= CAST(:from AS timestamptz))
          AND (CAST(:to AS timestamptz)        IS NULL OR c.created_at <= CAST(:to AS timestamptz))
          AND (CAST(:status AS text)           IS NULL OR c.call_status = CAST(:status AS text))
          AND (CAST(:extension AS text)        IS NULL OR LOWER(c.caller_extension)
                                               LIKE LOWER('%' || CAST(:extension AS text) || '%'))
        ORDER BY c.created_at DESC
        """,
        countQuery = """
        SELECT COUNT(*) FROM call_events c
        WHERE (CAST(:from AS timestamptz)      IS NULL OR c.created_at >= CAST(:from AS timestamptz))
          AND (CAST(:to AS timestamptz)        IS NULL OR c.created_at <= CAST(:to AS timestamptz))
          AND (CAST(:status AS text)           IS NULL OR c.call_status = CAST(:status AS text))
          AND (CAST(:extension AS text)        IS NULL OR LOWER(c.caller_extension)
                                               LIKE LOWER('%' || CAST(:extension AS text) || '%'))
        """,
        nativeQuery = true)
    Page<CallEventEntity> findAllHistory(
            @Param("from")      OffsetDateTime from,
            @Param("to")        OffsetDateTime to,
            @Param("status")    String status,
            @Param("extension") String extension,
            Pageable pageable
    );

}