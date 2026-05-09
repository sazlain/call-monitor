# Call Monitor — Backend de Estadísticas y Monitoreo de Llamadas

API REST con Spring Boot para monitoreo en tiempo real, estadísticas y gestión de leads para equipos de agentes telefónicos.

---

## Stack tecnológico

- **Java 17** + **Spring Boot 4.0.6**
- **PostgreSQL 15** — base de datos principal
- **Spring Security** + **JWT (jjwt 0.12.6)** — autenticación y autorización
- **Spring WebSocket (STOMP)** — eventos en tiempo real
- **Spring Data JPA** + **Hibernate** — acceso a datos
- **OpenCSV 5.9** — carga masiva de leads
- **SpringDoc/OpenAPI 3.0.2** — documentación Swagger
- **Docker** + **Docker Compose** — infraestructura

---

## Arquitectura

El proyecto sigue **arquitectura hexagonal (ports & adapters)**:

```
domain/
  enums/          Enumeraciones del negocio (Role, CallStatus, CallFlow, LeadStatus, CallResult)
  models/         Modelos de dominio puros (sin anotaciones JPA)
  ports/in/       Interfaces de casos de uso (lo que el dominio expone)
  ports/out/      Interfaces de repositorios (lo que el dominio necesita)
  usecases/       Implementaciones de la lógica de negocio
  responses/      DTOs de salida

infrastructure/
  adapters/in/    Controllers REST (entrada al sistema)
  adapters/out/   Entidades JPA, repositorios, implementaciones de ports out
  mappers/        Conversión entre capas
  requests/       DTOs de entrada (requests)
  security/       JWT, filtros, configuración de Spring Security
  websocket/      Configuración y handler de WebSocket
  exceptions/     Manejo global de errores
```

---

## Variables de entorno (.env)

Crea un archivo `.env` en la raíz del proyecto:

```env
# Base de datos
POSTGRES_USER=calluser
POSTGRES_PASSWORD=callpass
POSTGRES_DB=callmonitor
POSTGRES_PORT=5432

# JWT — mínimo 32 caracteres para HS256
JWT_SECRET=tu-secret-super-seguro-de-minimo-32-caracteres

# Clave maestra para crear administradores
APP_ADMIN_SECRET=clave-para-crear-admins-cambiar-en-produccion

# IPs permitidas del proveedor de telefonía (separadas por coma)
# Usar * mientras no se tenga la IP — el sistema la logueará al primer webhook
WEBHOOK_ALLOWED_IPS=*

# CORS — orígenes permitidos para el frontend
CORS_ALLOWED_ORIGINS=http://localhost:3000
```

---

## Levantar el proyecto

### Con Docker Compose

```bash
# Clonar el repositorio
git clone https://github.com/sazlain/call-monitor.git
cd call-monitor

# Crear el .env con las variables
cp .env.example .env
# Editar .env con tus valores

# Levantar
docker-compose up -d

# Ver logs
docker-compose logs -f app
```

### En local (desarrollo)

```bash
# Requiere PostgreSQL corriendo en localhost:5432
mvn spring-boot:run
```

La API queda disponible en `http://localhost:8080`.
Swagger UI: `http://localhost:8080/swagger-ui.html`

---

## Primeros pasos — Flujo de configuración inicial

### 1. Crear el primer administrador

```http
POST /api/auth/register
Content-Type: application/json
X-Admin-Secret: {APP_ADMIN_SECRET}

{
  "name": "Admin Principal",
  "email": "admin@empresa.com",
  "password": "password123"
}
```

### 2. Hacer login

```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "admin@empresa.com",
  "password": "password123"
}
```

Respuesta:
```json
{
  "token": "eyJhbGci...",
  "tokenType": "Bearer",
  "expiresIn": 216000,
  "userId": 1,
  "name": "Admin Principal",
  "email": "admin@empresa.com",
  "roles": ["ADMIN"],
  "extension": null,
  "mustChangePassword": false
}
```

Usar el `token` en el header `Authorization: Bearer {token}` para todos los demás endpoints.

### 3. Crear un grupo de agentes

```http
POST /api/groups
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "Equipo Ventas",
  "description": "Agentes de ventas outbound"
}
```

### 4. Crear agentes

```http
POST /api/agents
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "Juan Pérez",
  "email": "juan@empresa.com",
  "extension": "101",
  "groupId": 1
}
```

El sistema crea el usuario del agente automáticamente con contraseña temporal. El agente debe cambiarla en su primer login:

```http
POST /api/auth/change-password
Authorization: Bearer {token_del_agente}
Content-Type: application/json

{
  "currentPassword": "{contrasena_temporal}",
  "newPassword": "nueva_contrasena_segura"
}
```

---

## Roles y acceso por endpoint

| Endpoint | ADMIN | SALES_AGENT | CALL_AGENT |
|---|:---:|:---:|:---:|
| `POST /api/auth/register` | ✅ (con secret) | — | — |
| `POST /api/auth/login` | ✅ | ✅ | ✅ |
| `GET/POST /api/groups/**` | ✅ | — | — |
| `GET/POST /api/agents/**` | ✅ | — | — |
| `POST /api/leads` | ✅ | ✅ | — |
| `POST /api/leads/bulk` | ✅ | ✅ | — |
| `GET /api/leads` | ✅ | ✅ (solo suyos) | — |
| `GET /api/leads/assigned` | ✅ | — | ✅ |
| `PUT /api/leads/{id}/assign` | ✅ | ✅ | — |
| `POST /api/calls/typification` | ✅ | — | ✅ |
| `GET /api/dashboard/admin` | ✅ | — | — |
| `GET /api/dashboard/agent/{ext}` | ✅ | — | ✅ (solo suyo) |
| `GET /api/dashboard/sales` | ✅ | ✅ | — |
| `GET /api/dashboard/status` | ✅ | — | ✅ |
| `GET /api/reports/**` | ✅ | ✅ (solo leads) | ✅ (callbacks) |

---

## WebSocket — Integración con el frontend

### Conexión

```javascript
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const client = new Client({
  webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
  onConnect: () => {

    // El agente se suscribe a sus propios eventos
    client.subscribe('/topic/calls/agent/101', (message) => {
      const event = JSON.parse(message.body);
      handleCallEvent(event);
    });

    // El admin se suscribe a todos los eventos de un grupo
    client.subscribe('/topic/calls/group/1', (message) => {
      const event = JSON.parse(message.body);
      updateDashboard(event);
    });
  }
});

client.activate();
```

### Estructura del mensaje WebSocket

```json
{
  "callId": "abc-123",
  "callerExtension": "101",
  "callerIdNum": "3001234567",
  "callerIdName": "Juan Contacto",
  "calledNumber": "3009876543",
  "calledExtension": "101",
  "callStatus": "CALLING",
  "callFlow": "out",
  "callAPIID": "api-xyz",
  "timestamp": "2025-05-09T14:32:00Z",
  "frontendAction": "OPEN_CONTACT_FORM"
}
```

### Acciones del frontend según `frontendAction`

| Valor | Cuándo | Acción sugerida |
|---|---|---|
| `OPEN_CONTACT_FORM` | CALLING | Abrir formulario de captura de datos del contacto |
| `START_CALL_TIMER` | ANSWER | Iniciar cronómetro de duración |
| `OPEN_TYPIFICATION_FORM` | HANGUP | Abrir formulario de tipificación |
| `REGISTER_FAILED_ATTEMPT` | BUSY / NOANSWER / CANCEL | Registrar intento fallido, cerrar formulario |

---

## Webhook del proveedor de telefonía

El proveedor envía eventos a:

```
POST /api/events/calls/event
Content-Type: application/x-www-form-urlencoded
```

Payload esperado:

```
CallID=abc-123&CallerIDNum=3001234567&CallerIDName=Juan&CalledDID=8001&
CalledExtension=101&CallStatus=CALLING&CallFlow=out&
CallerExtension=101&CalledNumber=3009876543&CallAPIID=api-xyz
```

### Estados del ciclo de una llamada

```
CALLING → ANSWER → HANGUP    (llamada contestada)
CALLING → NOANSWER           (no contestó)
CALLING → BUSY               (línea ocupada)
CALLING → CANCEL             (cancelada antes de contestar)
CALLING → CONGESTION         (congestión de red)
CALLING → CHANUNAVAIL        (canal no disponible)
```

### Configurar la IP del proveedor

Cuando el proveedor empiece a enviar webhooks, revisa los logs:

```
WEBHOOK [MODO ABIERTO] IP entrante: 190.x.x.x
```

Luego configura en `.env`:

```env
WEBHOOK_ALLOWED_IPS=190.x.x.x
```

Y reinicia el contenedor.

---

## Carga masiva de leads (CSV)

Formato del archivo:

```csv
contact_name,contact_phone,lead_source,lead_date,notes
Maria López,3001234567,Feria Bogotá mayo,2025-05-01,Interesada en plan básico
Carlos Ruiz,3107654321,Instagram stories,2025-05-02,
```

Endpoint:

```http
POST /api/leads/bulk
Authorization: Bearer {token}
Content-Type: multipart/form-data

file: {archivo.csv}
assignedAgentId: 2  (opcional — asigna todos los leads del lote al mismo agente)
```

---

## Dashboard — Parámetros de fecha

Todos los endpoints de dashboard y reportes aceptan `from` y `to` en formato ISO 8601:

```
GET /api/dashboard/agent/101?from=2025-05-01T00:00:00Z&to=2025-05-09T23:59:59Z
```

Si no se pasan fechas, por defecto devuelve **el día de hoy** desde las 00:00 UTC hasta ahora.

---

## Estructura de la base de datos

| Tabla | Descripción |
|---|---|
| `users` | Todos los usuarios del sistema |
| `user_roles` | Roles por usuario (ADMIN, SALES_AGENT, CALL_AGENT) |
| `agent_groups` | Grupos de agentes, pertenecen a un admin |
| `agents` | Perfil de agente, vincula user con extensión |
| `call_events` | Eventos de llamada del webhook (flujo completo) |
| `leads` | Leads cargados por agentes de ventas |
| `call_typifications` | Tipificaciones de llamadas por agentes |

---

## Orden de implementación de las partes

| Parte | Contenido | Archivos |
|---|---|---|
| Parte 1 | Seguridad, JWT, autenticación | pom.xml, enums, UserEntity, SecurityConfig, JwtUtil, AuthController |
| Parte 2 | Agentes, grupos, WebSocket, refactor webhook | AgentEntity, AgentGroupEntity, WebSocketConfig, CallEventListenerController |
| Parte 3 | Leads y tipificación | LeadEntity, CallTypificationEntity, LeadController, CallTypificationController |
| Parte 4 | Dashboard de estadísticas | CallEventJpaRepository (extendido), DashboardImpl, DashboardController |
| Parte 5 | Reportes exportables CSV | ReportImpl, ReportController |
| Parte 6 | Manejo de errores, init.sql | GlobalExceptionHandler, ErrorCodes, init.sql, messages.properties |
| Parte 7 | Este README | README.md |

---

## Notas de producción

- Cambiar `JWT_SECRET` por un valor de mínimo 32 caracteres generado aleatoriamente
- Cambiar `APP_ADMIN_SECRET` por un valor seguro
- Configurar `WEBHOOK_ALLOWED_IPS` con la IP real del proveedor
- Cambiar `CORS_ALLOWED_ORIGINS` al dominio real del frontend
- Configurar `spring.jpa.hibernate.ddl-auto=validate` en producción (en vez de `update`)
- El `init.sql` crea los índices — asegurarse de que se ejecute al menos una vez
