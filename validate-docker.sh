#!/bin/bash

# Script de validación de configuración Docker

echo "🔍 Validando configuración Docker..."
echo ""

# Colores
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ERRORS=0

check_file() {
    if [ -f "$1" ]; then
        echo -e "${GREEN}✓${NC} $1 existe"
    else
        echo -e "${RED}✗${NC} $1 NO EXISTE"
        ERRORS=$((ERRORS + 1))
    fi
}

check_command() {
    if command -v "$1" &> /dev/null; then
        VERSION=$($1 --version 2>/dev/null | head -n1 || echo "installed")
        echo -e "${GREEN}✓${NC} $1 instalado ($VERSION)"
    else
        echo -e "${RED}✗${NC} $1 NO INSTALADO"
        ERRORS=$((ERRORS + 1))
    fi
}

echo "📁 Verificando archivos del proyecto:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
check_file "$PROJECT_DIR/Dockerfile"
check_file "$PROJECT_DIR/docker-compose.yml"
check_file "$PROJECT_DIR/.env.docker"
check_file "$PROJECT_DIR/.dockerignore"
check_file "$PROJECT_DIR/docker.sh"
check_file "$PROJECT_DIR/pom.xml"
check_file "$PROJECT_DIR/src/main/resources/application.properties"
echo ""

echo "🛠️  Verificando herramientas instaladas:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
check_command "docker"
check_command "docker-compose"
check_command "maven" || check_command "mvn"
check_command "java"
echo ""

echo "📝 Validando contenido de archivos:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if grep -q "FROM maven" "$PROJECT_DIR/Dockerfile"; then
    echo -e "${GREEN}✓${NC} Dockerfile contiene multi-stage build"
else
    echo -e "${RED}✗${NC} Dockerfile no tiene estructura esperada"
    ERRORS=$((ERRORS + 1))
fi

if grep -q "postgres:15-alpine" "$PROJECT_DIR/docker-compose.yml"; then
    echo -e "${GREEN}✓${NC} docker-compose.yml usa PostgreSQL 15-Alpine"
else
    echo -e "${RED}✗${NC} docker-compose.yml configuración incompleta"
    ERRORS=$((ERRORS + 1))
fi

if grep -q "POSTGRES_USER" "$PROJECT_DIR/.env.docker"; then
    echo -e "${GREEN}✓${NC} .env.docker contiene variables PostgreSQL"
else
    echo -e "${RED}✗${NC} .env.docker incompleto"
    ERRORS=$((ERRORS + 1))
fi

if grep -q "call-network" "$PROJECT_DIR/docker-compose.yml"; then
    echo -e "${GREEN}✓${NC} docker-compose.yml define red personalizada"
else
    echo -e "${YELLOW}⚠${NC} docker-compose.yml podría no tener red definida"
fi

if grep -q "healthcheck" "$PROJECT_DIR/docker-compose.yml"; then
    echo -e "${GREEN}✓${NC} docker-compose.yml contiene health checks"
else
    echo -e "${RED}✗${NC} Health checks no configurados"
    ERRORS=$((ERRORS + 1))
fi

echo ""
echo "🔗 Validando conectividad:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if grep -q "depends_on" "$PROJECT_DIR/docker-compose.yml"; then
    echo -e "${GREEN}✓${NC} docker-compose.yml define dependencias"
else
    echo -e "${YELLOW}⚠${NC} No hay dependencias entre servicios"
fi

if grep -q "jdbc:postgresql://db:" "$PROJECT_DIR/docker-compose.yml"; then
    echo -e "${GREEN}✓${NC} Configuración de conexión a BD correcta"
else
    echo -e "${YELLOW}⚠${NC} Verificar configuración de conexión DB"
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if [ $ERRORS -eq 0 ]; then
    echo -e "${GREEN}✅ Validación exitosa - Todo está configurado correctamente${NC}"
    echo ""
    echo "🚀 Próximos pasos:"
    echo "  1. Ejecuta: ./docker.sh up"
    echo "  2. Espera a que carguen los servicios"
    echo "  3. Accede a: http://localhost:8086/swagger-ui.html"
    exit 0
else
    echo -e "${RED}❌ Se encontraron $ERRORS error(es) en la configuración${NC}"
    echo ""
    echo "📚 Consulta DOCKER.md para más información"
    exit 1
fi

