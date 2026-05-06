# Guía Docker para Call Application

## Descripción General

Este documento explica cómo ejecutar la aplicación **Call** usando Docker y Docker Compose. La configuración incluye:

- **PostgreSQL 15-Alpine**: Base de datos
- **Spring Boot 4.0.6**: Aplicación Java 17

## Requisitos Previos

- Docker >= 20.10
- Docker Compose >= 1.29
- Git (opcional, para clonar el repositorio)

## Estructura de Archivos

```
.
├── Dockerfile                           # Construcción de imagen de la aplicación
├── docker-compose.yml                   # Orquestación de servicios
├── .env.docker                          # Variables de entorno para Docker
├── pom.xml                              # Configuración Maven
├── src/
│   └── main/
│       └── resources/
│           ├── application.properties   # Configuración de la aplicación
│           └── init.sql                 # (Opcional) Script de inicialización BD
└── Dockerfile                           # Multi-stage build
```

## Inicio Rápido

### 1. Clonar/Descargar el repositorio

```bash
cd /path/to/call
```

### 2. Construir y ejecutar con Docker Compose

```bash
# Usar archivo .env.docker con variables predefinidas
docker-compose --env-file .env.docker up -d

# O sin archivo .env (usa valores por defecto)
docker-compose up -d
```

### 3. Verificar que los servicios están corriendo

```bash
docker-compose ps
```

**Salida esperada:**
```
NAME         COMMAND                  SERVICE   STATUS           PORTS
call-app     "java -jar app.jar"      app       Up 2 minutes     0.0.0.0:80->8080/tcp
call-db      "docker-entrypoint…"     db        Up 2 minutes     0.0.0.0:5432->5432/tcp
```

### 4. Acceder a la aplicación

- **Swagger UI**: http://localhost/swagger-ui.html
- **API Docs**: http://localhost/v3/api-docs
- **Health Check**: http://localhost/actuator/health

### 5. Detener servicios

```bash
docker-compose down
```

## Variables de Entorno

Archivo `.env.docker` contiene:

| Variable | Valor por Defecto | Descripción |
|----------|----------------|-------------|
| `POSTGRES_USER` | `calluser` | Usuario de PostgreSQL |
| `POSTGRES_PASSWORD` | `callpassword` | Contraseña de PostgreSQL |
| `POSTGRES_DB` | `calldb` | Nombre de base de datos |
| `POSTGRES_PORT` | `5432` | Puerto de PostgreSQL |
| `APP_PORT` | `80` | Puerto de la aplicación |
| `ENVIRONMENT` | `docker` | Ambiente de ejecución |

### Personalizar Variables

Para cambiar valores, edita `.env.docker`:

```bash
POSTGRES_USER=mi_usuario
POSTGRES_PASSWORD=mi_contraseña_segura
POSTGRES_DB=mi_base_datos
APP_PORT=9000
```

Luego reinicia:

```bash
docker-compose --env-file .env.docker down
docker-compose --env-file .env.docker up -d
```

## Configuración de Base de Datos

### Url de Conexión

La aplicación usa la siguiente URL en Docker:

```properties
spring.datasource.url=jdbc:postgresql://db:5432/calldb
```

**Nota**: `db` es el hostname del contenedor PostgreSQL definido en `docker-compose.yml`.

### Script de Inicialización (Opcional)

Si necesitas ejecutar SQL al inicializar:

1. Crea archivo `src/main/resources/init.sql` (o similar)
2. El script se ejecutará automáticamente cuando PostgreSQL inicie

Ejemplo `init.sql`:

```sql
-- Crear tablas, insertar datos iniciales, etc.
CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE
);
```

## Persistencia de Datos

Los datos de PostgreSQL se almacenan en un volumen Docker llamado `postgres_data`:

```bash
# Ver volúmenes
docker volume ls

# Inspeccionar volumen
docker volume inspect call_postgres_data

# Eliminar volumen (CUIDADO: borra datos)
docker volume rm call_postgres_data
```

## Logs y Debugging

### Ver logs en tiempo real

```bash
# Todos los servicios
docker-compose logs -f

# Solo la aplicación
docker-compose logs -f app

# Solo la base de datos
docker-compose logs -f db
```

### Conectar a PostgreSQL directamente

```bash
docker exec -it call-db psql -U calluser -d calldb

# Listar tablas
\dt

# Salir
\q
```

### Ejecutar comandos en la aplicación

```bash
docker exec -it call-app sh

# Dentro del contenedor
java -version
ls -la /app
```

## Problemas Comunes

### 1. Puerto 5432 ya está en uso

**Error**: `bind: address already in use`

**Solución**: Cambia el puerto en `.env.docker`:

```bash
POSTGRES_PORT=5433  # o cualquier otro puerto disponible
```

### 2. Aplicación no se conecta a BD

**Error**: `Unable to connect to PostgreSQL`

**Verificar**:
- La BD está running: `docker-compose logs db`
- Variables de entorno coinciden en ambos servicios
- Hostname es `db` (no `localhost`)

### 3. Recompilar después de cambios

```bash
# Forzar reconstrucción de imagen
docker-compose build --no-cache

# Luego reinicia
docker-compose up -d
```

### 4. Limpiar completamente

```bash
# Detener servicios
docker-compose down

# Eliminar volúmenes (CUIDADO: borra datos)
docker-compose down -v

# Eliminar imagen
docker rmi call-app:latest

# Reconstruir desde cero
docker-compose up -d --build
```

## Producción

Para ambiente de producción:

1. **Cambiar credenciales**: Usa valores seguros en `.env.docker`
2. **Usar variables secretas**: Integra con sistemas de secrets (AWS Secrets Manager, HashiCorp Vault, etc.)
3. **Optimizar imágenes**: Reduce tamaño con `.dockerignore`
4. **Agregar reverse proxy**: Nginx o similar
5. **Monitoreo**: Implementa health checks y logging centralizados
6. **SSL/TLS**: Configura certificados para conexiones seguras

Ejemplo `.env.docker` para producción:

```bash
POSTGRES_USER=secure_user_prod
POSTGRES_PASSWORD=SecurePassword123!@#
POSTGRES_DB=calldb_prod
APP_PORT=8086
ENVIRONMENT=production
```

## Referencias

- [Docker Documentation](https://docs.docker.com/)
- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Spring Boot Docker Guide](https://spring.io/guides/gs/spring-boot-docker/)
- [PostgreSQL in Docker](https://hub.docker.com/_/postgres)

## Soporte

Para problemas o preguntas, revisa los logs:

```bash
docker-compose logs --tail=50
```

