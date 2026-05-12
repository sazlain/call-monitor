# Módulo Historial de Llamadas

## Backend (carpeta `backend/`)
Copiar `src/` a la raíz del proyecto `call-monitor/`

### Archivos PATCH (carpeta `PATCH/`)
1. `CallEventJpaRepository_PATCH.java` — agregar los 2 métodos al repo existente
2. `CallTypificationJpaRepository_PATCH.java` — agregar `findByCallIdIn` al repo existente

### SecurityConfig.java — agregar:
```java
.requestMatchers("/api/calls/history/**").hasAnyRole("ADMIN","CALL_AGENT")
```

## Frontend (carpeta `frontend/`)
Copiar `src/` a la raíz del proyecto `voxio/`

### Archivos PATCH (carpeta `PATCH/`)
1. `types_patch.ts` — agregar `CallHistoryResponse` y `CallHistoryPage` a `src/types/index.ts`

### router.tsx — agregar:
```tsx
const CallHistoryPage = lazy(() => import('@/features/calls/CallHistoryPage'))
// En el grupo CALL_AGENT + ADMIN:
{ path: '/calls/history', element: <CallHistoryPage /> }
```

### Sidebar.tsx — agregar:
```tsx
{ label: 'Historial', path: '/calls/history', icon: Phone, roles: ['ADMIN', 'CALL_AGENT'] }
```
