# 🚀 SETUP COMPLETO - Instrucciones Paso a Paso

## 📋 Checklist Pre-Requisitos

- [ ] Docker instalado (versión 20.10+)
- [ ] Docker Compose instalado (versión 1.29+)
- [ ] Java 17 instalado (para maven local)
- [ ] Maven 3.9+ instalado

### Verificar Requisitos

```bash
# Verificar Docker
docker --version
docker-compose --version

# Verificar Java
java -version

# Verificar Maven (opcional, docker usa su propia versión)
mvn --version
```

---

## 🎯 Configuración Inicial (2 minutos)

### 1️⃣ Validar la Estructura

```bash
# Ir al directorio del proyecto
cd /Users/azlainsaavedra/projects/call

# Ver los archivos Docker generados
ls -la Dockerfile docker-compose.yml .env.docker docker.sh
```

**Esperado**: Todos los archivos existen ✅

### 2️⃣ Revisar Configuración (.env.docker)

```bash
# Ver variables por defecto
cat .env.docker
```

**Esperado**:
```
POSTGRES_USER=calluser
POSTGRES_PASSWORD=callpassword
POSTGRES_DB=calldb
POSTGRES_PORT=5432
APP_PORT=8086
ENVIRONMENT=docker
```

### 3️⃣ Personalizar si es Necesario

```bash
# Si necesitas cambiar puerto, usuario, etc.
# Edita .env.docker

# Ejemplo: cambiar usuario y contraseña
nano .env.docker
# O en macOS con VIM:
vim .env.docker
```

---

## 🚀 Inicio de Servicios (3 pasos)

### Paso 1: Validar Configuración

```bash
./validate-docker.sh
```

**Salida esperada**:
```
🔍 Validando configuración Docker...
✓ Dockerfile existe
✓ docker-compose.yml existe
✓ docker está instalado
✓ docker-compose está instalado
✅ Validación exitosa
```

### Paso 2: Construir e Iniciar

```bash
# Opción A: Con script helper (RECOMENDADO)
./docker.sh up

# Opción B: Directo con docker-compose
docker-compose --env-file .env.docker up -d
```

**Primera vez tardará**: 2-5 minutos (construcción de imagen)

### Paso 3: Esperar a que Cargue Completamente

```bash
# Ver estado
./docker.sh ps

# O con docker-compose
docker-compose ps
```

**Esperado**:
```
NAME         STATUS                PORTS
call-app     Up 2 minutes          0.0.0.0:8086->8086/tcp
call-db      Up 2 minutes (healthy) 0.0.0.0:5432->5432/tcp
```

---

## ✅ Verificación de Funcionalidad

### Test 1: Verificar Servicios

```bash
./docker.sh status
```

**Debe mostrar**: Ambos servicios RUNNING ✅

### Test 2: Verificar Base de Datos

```bash
# Conectar a PostgreSQL
./docker.sh db-shell

# En la consola psql
\dt              # Ver tablas
\du              # Ver usuarios
\q               # Salir
```

### Test 3: Verificar Aplicación

```bash
# Test Health Check
curl http://localhost:8086/actuator/health

# Esperado: JSON con status "UP"
{
  "status": "UP",
  "components": {...}
}
```

### Test 4: Acceder a Swagger UI

```bash
# Abrir en navegador
open http://localhost:8086/swagger-ui.html
# o
curl http://localhost:8086/swagger-ui.html | head -20
```

---

## 📊 Ver Logs y Monitorear

### Ver logs en vivo

```bash
# Todos los servicios
./docker.sh logs

# Solo aplicación
./docker.sh logs-app

# Solo base de datos
./docker.sh logs-db

# Últimas 50 líneas sin seguimiento
./docker.sh logs --tail=50
```

### Seguimiento en tiempo real

```bash
# Presiona Ctrl+C para salir
docker-compose logs -f
```

---

## 💾 Backup y Restauración

### Hacer Backup

```bash
# Backup automático
./docker.sh db-backup

# El archivo se crea en el directorio actual
# Ejemplo: backup_20260506_143022.sql

# Listar backups
ls backup_*.sql
```

### Restaurar desde Backup

```bash
./docker.sh db-restore backup_20260506_143022.sql
```

---

## 🔄 Operaciones Comunes

### Recompilar después de cambios en código

```bash
./docker.sh rebuild
./docker.sh up
```

### Reiniciar servicios

```bash
./docker.sh restart
```

### Detener temporalmente

```bash
./docker.sh down
```

### Limpieza Completa (CUIDADO: elimina datos)

```bash
./docker.sh clean

# Esto eliminará:
# - Contenedores
# - Volúmenes (datos de BD)
# - Imágenes
```

---

## 🐛 Troubleshooting

### Problema: "Port 5432 already in use"

```bash
# Solución 1: Cambiar puerto en .env.docker
POSTGRES_PORT=5433

# Solución 2: Matar proceso en el puerto
lsof -i :5432
kill -9 <PID>

# Luego reiniciar
./docker.sh restart
```

### Problema: "Connection refused" a la BD

```bash
# Verificar logs de BD
./docker.sh logs-db

# Esperado: "database system is ready to accept connections"

# Si no está lista, esperar y reintentar
# O reiniciar todo
./docker.sh restart
```

### Problema: "Application failed to start"

```bash
# Ver logs detallados
./docker.sh logs-app

# Buscar "ERROR" o "Exception"
./docker.sh logs-app | grep -i error

# Posibles causas:
# 1. BD no está lista (espera)
# 2. Puerto 8086 en uso (cambiar APP_PORT)
# 3. Problemas en el código (revisar stack trace)
```

### Problema: Cambios de código no se ven

```bash
# Los volúmenes están montados pero necesita recompilación
./docker.sh rebuild
```

---

## 📚 Archivos de Referencia

| Archivo | Propósito | Consultar cuando... |
|---------|----------|-------------------|
| `DOCKER.md` | Documentación completa | Necesitas referencia detallada |
| `QUICK_START.md` | Resumen rápido | Necesitas referencia rápida |
| `EVALUACION_COMPLETA.md` | Evaluación técnica | Necesitas entender arquitectura |
| `Dockerfile` | Build de imagen | Necesitas modificar construcción |
| `docker-compose.yml` | Orquestación servicios | Necesitas agregar/cambiar servicios |
| `.env.docker` | Configuración | Necesitas cambiar credenciales/puertos |
| `docker.sh` | Script helper | Necesitas ver comandos disponibles |

---

## 🎓 Flujos de Trabajo Comunes

### Flujo: Desarrollo Diario

```bash
# 1. Iniciar servicios (si no están corriendo)
./docker.sh up

# 2. Hacer cambios en el código
# (modificar archivos en ./src)

# 3. Ver cambios en tiempo real
./docker.sh logs-app

# 4. Acceder a la app
open http://localhost:8086/swagger-ui.html
```

### Flujo: Deployment/Testing

```bash
# 1. Reconstruir imagen
./docker.sh rebuild

# 2. Limpiar datos (opcional)
./docker.sh clean

# 3. Iniciar servicios limpios
./docker.sh up

# 4. Ejecutar tests/validaciones
curl http://localhost:8086/actuator/health

# 5. Hacer backup si todo OK
./docker.sh db-backup
```

### Flujo: Colaboración en Equipo

```bash
# Cada miembro del equipo:
# 1. Clone el repositorio
# 2. Ejecute
./validate-docker.sh
./docker.sh up

# Todos trabajan con el mismo ambiente garantizado
```

---

## 🔐 Antes de Ir a Producción

### Checklist de Seguridad

- [ ] Cambiar POSTGRES_PASSWORD en `.env.docker`
- [ ] Cambiar POSTGRES_USER en `.env.docker`
- [ ] Usar valores seguros (no "calluser"/"callpassword")
- [ ] Configurar SSL/TLS (reverse proxy con Nginx)
- [ ] Habilitar backups automáticos
- [ ] Implementar monitoreo y alertas
- [ ] Revisar logs de seguridad
- [ ] Usar Docker secrets (no .env en prod)
- [ ] Implementar CI/CD pipeline
- [ ] Hacer load testing

### Ejemplo .env.docker para Producción

```bash
POSTGRES_USER=prod_user_$(date +%s)
POSTGRES_PASSWORD=$(openssl rand -base64 32)
POSTGRES_DB=calldb_production
POSTGRES_PORT=5432
APP_PORT=8086
ENVIRONMENT=production
```

---

## 📞 Ayuda Rápida

```bash
# Ver todos los comandos disponibles
./docker.sh help

# Ver versiones
docker --version
docker-compose --version
java -version

# Limpiar imágenes no usadas (libera espacio)
docker system prune

# Ver uso de disco
docker system df

# Ejecutar comando en contenedor
docker exec -it call-app java -version
docker exec -it call-db psql -U calluser -c "SELECT version();"
```

---

## ✨ Resumen Rápido

| Acción | Comando |
|--------|---------|
| **Iniciar** | `./docker.sh up` |
| **Parar** | `./docker.sh down` |
| **Ver estado** | `./docker.sh ps` |
| **Logs** | `./docker.sh logs` |
| **Conectar BD** | `./docker.sh db-shell` |
| **Backup BD** | `./docker.sh db-backup` |
| **Reiniciar** | `./docker.sh restart` |
| **Limpiar** | `./docker.sh clean` |
| **Ayuda** | `./docker.sh help` |

---

## 🎉 ¡Listo para Usar!

```
Después de ejecutar:
  ./docker.sh up

Accede a:
  🎨 http://localhost:8086/swagger-ui.html
  📚 http://localhost:8086/v3/api-docs
  ❤️  http://localhost:8086/actuator/health
```

---

**Versión**: 1.0  
**Fecha**: 6 de Mayo de 2026  
**Proyecto**: Call Application v0.0.1-SNAPSHOT  
**Autor**: GitHub Copilot

