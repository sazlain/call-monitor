# Quick Start Guide - Call Application Docker

## ⚡ 30 Segundos para Empezar

```bash
cd /Users/azlainsaavedra/projects/call
./docker.sh up
```

Espera 20-30 segundos y accede a:
👉 **http://localhost/swagger-ui.html**

## 🎯 Comandos Más Usados

```bash
# Estado de servicios
./docker.sh ps
./docker.sh status

# Logs
./docker.sh logs           # Todos los servicios (en vivo)
./docker.sh logs-app       # Solo aplicación
./docker.sh logs-db        # Solo BD

# Base de datos
./docker.sh db-shell       # psql interactivo
./docker.sh db-backup      # Guardar BD
./docker.sh db-restore backup.sql  # Restaurar

# Control
./docker.sh restart        # Reiniciar
./docker.sh down           # Detener
./docker.sh rebuild        # Recompilar
./docker.sh clean          # Eliminar todo
```

## 🔑 Variables de Configuración

Edita `.env.docker`:

```bash
POSTGRES_USER=calluser           # Usuario BD
POSTGRES_PASSWORD=callpassword   # Contraseña
POSTGRES_DB=calldb              # Nombre BD
POSTGRES_PORT=5432              # Puerto BD
APP_PORT=8086                   # Puerto App
```

## 🐛 Troubleshooting

| Problema | Solución |
|----------|----------|
| Puerto en uso | Cambia `APP_PORT` o `POSTGRES_PORT` en `.env.docker` |
| App no conecta BD | Revisa logs: `./docker.sh logs-app` |
| Cambios no se ven | Ejecuta `./docker.sh rebuild` |
| Limpiar todo | Ejecuta `./docker.sh clean` |

## 📊 URLs Importantes

- 🎨 **Swagger UI**: http://localhost:8086/swagger-ui.html
- 📚 **OpenAPI Docs**: http://localhost:8086/v3/api-docs
- ❤️ **Health Check**: http://localhost:8086/actuator/health
- 🗄️ **PostgreSQL**: localhost:5432

## 📖 Documentación

- **Guía completa**: `DOCKER.md`
- **Detalles técnicos**: `IMPLEMENTACION_DOCKER.md`
- **Este documento**: `QUICK_START.md`

## ✅ Validar Configuración

```bash
./validate-docker.sh
```

## 🚀 Uso en Producción

1. Edita `.env.docker` con credenciales seguras
2. Corre en máquina de producción:
   ```bash
   docker-compose --env-file .env.docker up -d
   ```
3. Configura backups automáticos de BD
4. Agrega reverse proxy (Nginx)
5. Habilita SSL/TLS

---

**Más ayuda**: Revisa `DOCKER.md` o `./docker.sh help`

