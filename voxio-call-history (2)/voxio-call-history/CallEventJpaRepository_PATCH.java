// AGREGAR estos métodos al CallEventJpaRepository existente:

// Historial del agente con JOIN a tipificaciones
@Query("""
    SELECT c FROM CallEventEntity c
    WHERE c.callerExtension IN :extensions
    AND (:from IS NULL OR c.createdAt >= :from)
    AND (:to IS NULL OR c.createdAt <= :to)
    AND (:status IS NULL OR c.callStatus = :status)
    ORDER BY c.createdAt DESC
    """)
Page<CallEventEntity> findHistory(
    @Param("extensions") List<String> extensions,
    @Param("from")       OffsetDateTime from,
    @Param("to")         OffsetDateTime to,
    @Param("status")     String status,
    Pageable pageable
);

// Para admin — todas las extensiones del grupo
@Query("""
    SELECT c FROM CallEventEntity c
    WHERE (:from IS NULL OR c.createdAt >= :from)
    AND (:to IS NULL OR c.createdAt <= :to)
    AND (:status IS NULL OR c.callStatus = :status)
    AND (:extension IS NULL OR c.callerExtension LIKE %:extension%)
    ORDER BY c.createdAt DESC
    """)
Page<CallEventEntity> findAllHistory(
    @Param("from")      OffsetDateTime from,
    @Param("to")        OffsetDateTime to,
    @Param("status")    String status,
    @Param("extension") String extension,
    Pageable pageable
);
