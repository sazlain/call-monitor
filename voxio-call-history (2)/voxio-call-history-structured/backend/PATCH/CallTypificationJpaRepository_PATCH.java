// AGREGAR este método al CallTypificationJpaRepository existente:

List<CallTypificationEntity> findByCallIdIn(List<String> callIds);
