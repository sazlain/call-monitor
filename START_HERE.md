# ⚡ START HERE - Comienza Aquí

## 🚀 30 Segundos para Empezar

```bash
cd /Users/azlainsaavedra/projects/call
./docker.sh up
# Espera 20-30 segundos...
# Accede a: http://localhost:8086/swagger-ui.html
```

**¡Eso es todo!**

---

## 📖 Dónde Leer Según Tu Prisa

| Tiempo | Lee | Qué Obtienes |
|--------|------|-------------|
| ⏱️ 2 min | [QUICK_START.md](QUICK_START.md) | Comandos esenciales |
| ⏱️ 5 min | [RESUMEN.md](RESUMEN.md) | Overview completo |
| ⏱️ 10 min | [README_DOCKER.md](README_DOCKER.md) | Todo visualizado |
| ⏱️ 30 min | [SETUP_COMPLETO.md](SETUP_COMPLETO.md) | Paso a paso |
| ⏱️ 60 min | [DOCKER.md](DOCKER.md) | Referencia total |

---

## 🎯 Comandos Esenciales

```bash
# INICIAR
./docker.sh up

# VER ESTADO
./docker.sh ps
./docker.sh status

# VER LOGS (en vivo)
./docker.sh logs -f

# BD
./docker.sh db-shell          # Conectar
./docker.sh db-backup         # Backup
./docker.sh db-restore FILE   # Restaurar

# DETENER
./docker.sh down

# AYUDA
./docker.sh help
```

---

## 🌐 URLs

- 🎨 Swagger UI: http://localhost:8086/swagger-ui.html
- 📚 OpenAPI: http://localhost:8086/v3/api-docs
- ❤️ Health: http://localhost:8086/actuator/health

---

## 🐛 Algo No Funciona?

1. Ejecuta: `./validate-docker.sh`
2. Lee: [DOCKER.md](DOCKER.md) → "Problemas Comunes"
3. Última opción: `./docker.sh clean && ./docker.sh up`

---

## 📁 Qué Se Generó

```
✅ Dockerfile (optimizado)
✅ docker-compose.yml (2 servicios)
✅ docker.sh (10+ comandos)
✅ validate-docker.sh (validador)
✅ 8 guías de documentación
```

---

## ✨ Lo Que Hace

```
PostgreSQL 15   (BD)         :5432
       ↕ (JDBC)
Spring Boot     (App)        :8086
       ↓
http://localhost:8086/swagger-ui.html ✅
```

---

## 🎓 Por Rol

- **Dev**: Leer [QUICK_START.md](QUICK_START.md) → Ejecutar `./docker.sh up`
- **DevOps**: Leer [EVALUACION_COMPLETA.md](EVALUACION_COMPLETA.md)
- **Nuevo en equipo**: Leer [SETUP_COMPLETO.md](SETUP_COMPLETO.md)
- **Manager**: Leer [RESUMEN.md](RESUMEN.md)

---

## ✅ Checklist

- [ ] Ejecuté `./validate-docker.sh` ✓
- [ ] Ejecuté `./docker.sh up` ✓
- [ ] Accedí a http://localhost:8086/swagger-ui.html ✓
- [ ] Todo funciona ✓

---

**Listo**. Ya puedes empezar. → `./docker.sh up`

