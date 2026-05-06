#!/bin/bash

# Script para gestionar Docker Compose de la aplicación Call

set -e

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Directorio raíz del proyecto
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${PROJECT_DIR}/.env.docker"
COMPOSE_FILE="${PROJECT_DIR}/docker-compose.yml"

# Funciones
print_header() {
    echo -e "${BLUE}════════════════════════════════════════${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}════════════════════════════════════════${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}ℹ $1${NC}"
}

# Comandos disponibles
case "${1:-help}" in
    up)
        print_header "Iniciando servicios Docker"

        if [ ! -f "$ENV_FILE" ]; then
            print_error "Archivo $ENV_FILE no encontrado"
            exit 1
        fi

        docker-compose --env-file "$ENV_FILE" up -d
        print_success "Servicios iniciados"
        echo ""
        echo -e "${YELLOW}URLs disponibles:${NC}"
        echo "  • Swagger UI: http://localhost/swagger-ui.html"
        echo "  • API Docs:   http://localhost/v3/api-docs"
        echo "  • Health:     http://localhost/actuator/health"
        echo ""
        ;;

    down)
        print_header "Deteniendo servicios Docker"
        docker-compose down
        print_success "Servicios detenidos"
        ;;

    restart)
        print_header "Reiniciando servicios Docker"
        docker-compose restart
        print_success "Servicios reiniciados"
        ;;

    rebuild)
        print_header "Reconstruyendo imagen de aplicación"
        docker-compose build --no-cache
        print_success "Imagen reconstruida"
        ;;

    logs)
        print_header "Logs de servicios (últimas 50 líneas)"
        docker-compose logs --tail=50 -f
        ;;

    logs-app)
        print_header "Logs de la aplicación"
        docker-compose logs -f app
        ;;

    logs-db)
        print_header "Logs de la base de datos"
        docker-compose logs -f db
        ;;

    ps)
        print_header "Estado de servicios"
        docker-compose ps
        ;;

    clean)
        print_header "Limpieza completa"
        print_info "Esto eliminará contenedores, volúmenes e imágenes"
        read -p "¿Estás seguro? (s/n): " confirm

        if [ "$confirm" = "s" ]; then
            docker-compose down -v
            docker rmi call-app:latest 2>/dev/null || true
            print_success "Limpieza completada"
        else
            print_info "Operación cancelada"
        fi
        ;;

    db-shell)
        print_header "Conexión a PostgreSQL"
        docker exec -it call-db psql -U calluser -d calldb
        ;;

    db-backup)
        print_header "Realizando backup de base de datos"
        BACKUP_FILE="${PROJECT_DIR}/backup_$(date +%Y%m%d_%H%M%S).sql"
        docker exec call-db pg_dump -U calluser -d calldb > "$BACKUP_FILE"
        print_success "Backup guardado en: $BACKUP_FILE"
        ;;

    db-restore)
        if [ -z "$2" ]; then
            print_error "Especifica el archivo de backup: ./docker.sh db-restore backup_file.sql"
            exit 1
        fi

        BACKUP_FILE="$2"
        if [ ! -f "$BACKUP_FILE" ]; then
            print_error "Archivo no encontrado: $BACKUP_FILE"
            exit 1
        fi

        print_header "Restaurando base de datos"
        cat "$BACKUP_FILE" | docker exec -i call-db psql -U calluser -d calldb
        print_success "Base de datos restaurada"
        ;;

    app-shell)
        print_header "Shell de la aplicación"
        docker exec -it call-app sh
        ;;

    app-build)
        print_header "Construyendo aplicación Maven"
        docker exec -it call-app mvn clean package
        ;;

    status)
        print_header "Verificando status"

        if docker ps --filter "name=call-app" --filter "status=running" | grep -q call-app; then
            print_success "Aplicación: RUNNING"
        else
            print_error "Aplicación: STOPPED"
        fi

        if docker ps --filter "name=call-db" --filter "status=running" | grep -q call-db; then
            print_success "Base de datos: RUNNING"
        else
            print_error "Base de datos: STOPPED"
        fi
        ;;

    help|--help|-h)
        cat << EOF
${BLUE}Gestión de Docker para Call Application${NC}

${YELLOW}Uso:${NC}
  ./docker.sh <comando> [opciones]

${YELLOW}Comandos:${NC}
  up              Inicia todos los servicios
  down            Detiene todos los servicios
  restart         Reinicia los servicios
  rebuild         Reconstruye la imagen de la aplicación
  ps              Muestra el estado de los contenedores
  logs            Muestra logs de todos los servicios (en tiempo real)
  logs-app        Muestra logs de la aplicación
  logs-db         Muestra logs de la base de datos
  clean           Elimina contenedores, volúmenes e imágenes
  status          Verifica el estado de los servicios

${YELLOW}Base de Datos:${NC}
  db-shell        Conecta a PostgreSQL (psql)
  db-backup       Realiza un backup de la BD
  db-restore FILE Restaura un backup (db-restore backup.sql)

${YELLOW}Aplicación:${NC}
  app-shell       Abre una terminal en el contenedor de la app
  app-build       Construye la aplicación con Maven

${YELLOW}Ejemplos:${NC}
  ./docker.sh up
  ./docker.sh logs -f
  ./docker.sh db-shell
  ./docker.sh db-backup
  ./docker.sh db-restore backup_20260506_120000.sql
  ./docker.sh clean

${YELLOW}Variables de entorno:${NC}
  Edita .env.docker para personalizar configuración

EOF
        ;;

    *)
        print_error "Comando desconocido: $1"
        echo "Usa './docker.sh help' para ver comandos disponibles"
        exit 1
        ;;
esac

