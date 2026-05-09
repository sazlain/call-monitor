# Guía de integración Frontend — Call Monitor API

Referencia rápida para conectar el frontend al backend.

---

## Autenticación

Todas las peticiones (excepto login y register) requieren el header:

```
Authorization: Bearer {token}
```

El token se obtiene del endpoint de login y expira en **60 horas** (configurable).

### Detectar roles en el frontend

```javascript
// Decodificar el JWT sin librería (solo el payload)
function getRoles(token) {
  const payload = JSON.parse(atob(token.split('.')[1]));
  return payload.roles; // ["ADMIN", "SALES_AGENT"]
}

// Mostrar dashboards según roles
const roles = getRoles(token);
const showAdminDashboard   = roles.includes('ADMIN');
const showSalesDashboard   = roles.includes('SALES_AGENT') || roles.includes('ADMIN');
const showCallDashboard    = roles.includes('CALL_AGENT')  || roles.includes('ADMIN');
```

---

## Polling para estado en tiempo real

El endpoint de status está diseñado para polling cada ~10 segundos:

```javascript
// Estado en tiempo real de los agentes
setInterval(async () => {
  const response = await fetch('/api/dashboard/status?groupId=1', {
    headers: { Authorization: `Bearer ${token}` }
  });
  const status = await response.json();
  updateAgentStatusUI(status.agents);
}, 10000);
```

---

## Descargar un reporte CSV

```javascript
async function downloadReport(url, filename) {
  const response = await fetch(url, {
    headers: { Authorization: `Bearer ${token}` }
  });
  const blob = await response.blob();
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = filename;
  link.click();
}

// Uso
downloadReport(
  '/api/reports/agent/101/calls?from=2025-05-01T00:00:00Z&to=2025-05-09T23:59:59Z',
  'llamadas_101.csv'
);
```

---

## Carga de CSV de leads

```javascript
async function uploadLeadsCsv(file, assignedAgentId) {
  const formData = new FormData();
  formData.append('file', file);
  if (assignedAgentId) formData.append('assignedAgentId', assignedAgentId);

  const response = await fetch('/api/leads/bulk', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: formData
  });

  const result = await response.json();
  // result: { total, created, failed, errors }
  return result;
}
```

---

## Tipificar una llamada

El frontend recibe el evento HANGUP por WebSocket con `frontendAction: 'OPEN_TYPIFICATION_FORM'`.
Luego envía la tipificación:

```javascript
async function typifyCall(callId, leadId, result, notes, callbackDate) {
  const response = await fetch('/api/calls/typification', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      callId,
      leadId,      // null si la llamada no tiene lead asociado
      result,      // 'SALE' | 'INTERESTED' | 'NOT_INTERESTED' | 'CALLBACK' | 'WRONG_NUMBER' | 'NO_ANSWER' | 'VOICEMAIL' | 'OTHER'
      notes,
      callbackDate // solo si result === 'CALLBACK' — formato 'YYYY-MM-DD'
    })
  });
  return response.json();
}
```

---

## Manejo de errores de la API

Todos los errores siguen el mismo formato:

```json
{
  "id": "ERR404",
  "title": "Recurso no encontrado",
  "message": "No se encontró el agente con extensión 999",
  "data": null,
  "httpStatus": "NOT_FOUND"
}
```

Errores de validación (400):

```json
{
  "error": "VALIDATION_ERROR",
  "message": "Uno o más campos tienen valores inválidos",
  "fields": {
    "email": "El email no tiene formato válido",
    "extension": "La extensión es requerida"
  }
}
```

```javascript
async function apiCall(url, options) {
  const response = await fetch(url, options);

  if (!response.ok) {
    const error = await response.json();

    if (response.status === 400 && error.fields) {
      // Mostrar errores de validación por campo
      showFieldErrors(error.fields);
    } else if (response.status === 401) {
      // Token expirado — redirigir al login
      redirectToLogin();
    } else if (response.status === 403) {
      showError('No tienes permisos para esta acción');
    } else {
      showError(error.message || 'Error inesperado');
    }
    throw error;
  }

  return response.json();
}
```

---

## Enums disponibles

### CallStatus (estados de llamada)
```
CALLING    — marcando
ANSWER     — contestada
HANGUP     — finalizada
BUSY       — línea ocupada
NOANSWER   — no contestó
CANCEL     — cancelada
CONGESTION — congestión de red
CHANUNAVAIL — canal no disponible
```

### CallFlow (flujo de llamada)
```
out — saliente (el agente marca)
in  — entrante (el agente recibe)
```

### CallResult (resultado de tipificación)
```
SALE           — venta / objetivo cumplido
INTERESTED     — interesado, requiere seguimiento
NOT_INTERESTED — no tiene interés
CALLBACK       — pidió que lo llamen después
WRONG_NUMBER   — número incorrecto
NO_ANSWER      — no contestó
VOICEMAIL      — buzón de voz
OTHER          — otro (detallar en notas)
```

### LeadStatus (estado del lead)
```
NEW        — recién cargado
PENDING    — asignado, pendiente de llamar
CALLED     — se intentó llamar
CONTACTED  — se habló con el contacto
INTERESTED — mostró interés
CONVERTED  — objetivo cumplido (venta)
CALLBACK   — agendado para rellamar
DISCARDED  — descartado
```

### Role (roles de usuario)
```
ADMIN        — administrador
SALES_AGENT  — agente de ventas (carga leads)
CALL_AGENT   — agente de llamadas (gestiona llamadas)
```
