# Resumen de todos los endpoints — Call Monitor API

## Autenticación (público / autenticado)

| Método | Endpoint | Rol | Descripción |
|---|---|---|---|
| POST | `/api/auth/login` | Público | Login — devuelve JWT |
| POST | `/api/auth/register` | Público + X-Admin-Secret | Crear administrador |
| POST | `/api/auth/change-password` | Autenticado | Cambiar contraseña |
| GET | `/api/auth/users` | ADMIN | Listar usuarios |
| GET | `/api/auth/users/{userId}` | ADMIN | Obtener usuario |
| POST | `/api/auth/users/{userId}/roles` | ADMIN | Agregar rol |
| DELETE | `/api/auth/users/{userId}/roles/{role}` | ADMIN | Quitar rol |
| DELETE | `/api/auth/users/{userId}` | ADMIN | Desactivar usuario |

## Grupos de agentes

| Método | Endpoint | Rol | Descripción |
|---|---|---|---|
| POST | `/api/groups` | ADMIN | Crear grupo |
| GET | `/api/groups` | ADMIN | Mis grupos |
| GET | `/api/groups/{groupId}` | ADMIN | Detalle de grupo |
| PUT | `/api/groups/{groupId}` | ADMIN | Actualizar grupo |
| DELETE | `/api/groups/{groupId}` | ADMIN | Desactivar grupo |
| POST | `/api/groups/{groupId}/agents/{agentId}` | ADMIN | Asignar agente al grupo |
| DELETE | `/api/groups/{groupId}/agents/{agentId}` | ADMIN | Remover agente del grupo |

## Agentes

| Método | Endpoint | Rol | Descripción |
|---|---|---|---|
| POST | `/api/agents` | ADMIN | Crear agente (y su usuario) |
| GET | `/api/agents?groupId=` | ADMIN | Listar agentes |
| GET | `/api/agents/{agentId}` | ADMIN/AGENT | Detalle de agente |
| GET | `/api/agents/extension/{ext}` | ADMIN/CALL_AGENT | Buscar por extensión |
| PUT | `/api/agents/{agentId}` | ADMIN | Actualizar agente |
| DELETE | `/api/agents/{agentId}` | ADMIN | Desactivar agente |

## Webhook del proveedor

| Método | Endpoint | Rol | Descripción |
|---|---|---|---|
| POST | `/api/events/calls/event` | IP whitelist | Recibir evento de llamada |

## Leads

| Método | Endpoint | Rol | Descripción |
|---|---|---|---|
| POST | `/api/leads` | ADMIN/SALES_AGENT | Crear lead |
| POST | `/api/leads/bulk` | ADMIN/SALES_AGENT | Carga masiva CSV |
| GET | `/api/leads?status=&from=&to=` | ADMIN/SALES_AGENT | Mis leads |
| GET | `/api/leads/assigned` | ADMIN/CALL_AGENT | Leads asignados a mí |
| GET | `/api/leads/callbacks` | ADMIN/SALES_AGENT/CALL_AGENT | Callbacks pendientes |
| GET | `/api/leads/{leadId}` | ADMIN/SALES_AGENT/CALL_AGENT | Detalle de lead |
| PUT | `/api/leads/{leadId}` | ADMIN/SALES_AGENT | Actualizar lead |
| PUT | `/api/leads/{leadId}/assign` | ADMIN/SALES_AGENT | Asignar agente |
| DELETE | `/api/leads/{leadId}` | ADMIN/SALES_AGENT | Descartar lead |

## Tipificación de llamadas

| Método | Endpoint | Rol | Descripción |
|---|---|---|---|
| POST | `/api/calls/typification` | ADMIN/CALL_AGENT | Tipificar llamada |
| PUT | `/api/calls/{callId}/typification` | ADMIN/CALL_AGENT | Corregir tipificación |
| GET | `/api/calls/{callId}/typification` | ADMIN/SALES_AGENT/CALL_AGENT | Ver tipificación |
| GET | `/api/calls/typifications/lead/{leadId}` | Todos | Historial de tipificaciones del lead |

## Dashboard

| Método | Endpoint | Rol | Descripción |
|---|---|---|---|
| GET | `/api/dashboard/agent/{extension}?from=&to=` | ADMIN/CALL_AGENT | Dashboard agente |
| GET | `/api/dashboard/admin?from=&to=&groupId=` | ADMIN | Dashboard admin |
| GET | `/api/dashboard/status?groupId=` | ADMIN/CALL_AGENT | Estado tiempo real |
| GET | `/api/dashboard/sales?from=&to=` | ADMIN/SALES_AGENT | Dashboard ventas |

## Reportes (CSV)

| Método | Endpoint | Rol | Descripción |
|---|---|---|---|
| GET | `/api/reports/agent/{extension}/calls?from=&to=` | ADMIN | Llamadas por agente |
| GET | `/api/reports/group?groupId=&from=&to=` | ADMIN | Comparativo de grupo |
| GET | `/api/reports/leads?from=&to=` | ADMIN/SALES_AGENT | Leads con estado |
| GET | `/api/reports/callbacks?from=&to=` | ADMIN/SALES_AGENT/CALL_AGENT | Callbacks |
