#!/bin/bash

################################################################################
# Piston Control System - Complete Security Initialization Script
################################################################################
#
# This script performs a full security initialization for the Piston Control
# System, including:
#   - Generating all passwords, secrets, and encryption keys
#   - Creating SSL/TLS certificates for MQTT and Nginx
#   - Setting up MQTT password authentication files
#   - Creating the .env file with all required environment variables
#
# Run this script ONCE during initial setup:
#   ./init-secrets.sh
#
# WARNING: This will OVERWRITE existing secrets and certificates!
#
################################################################################

set -e  # Exit on any error

# Color output for better readability
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo -e "${BLUE}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                                                          ║${NC}"
echo -e "${BLUE}║    🔐 Piston Control System Security Initialization     ║${NC}"
echo -e "${BLUE}║                                                          ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════╝${NC}"
echo ""

################################################################################
# STEP 1: Generate Passwords and Secrets
################################################################################
echo -e "${CYAN}[1/6]${NC} Generating passwords and secrets..."

# PostgreSQL password (64 bytes = 86 chars base64)
POSTGRES_PASSWORD=$(openssl rand -hex 32)
echo -e "  ${GREEN}✓${NC} PostgreSQL password generated"

# JWT Secret (128 bytes = 171 chars base64, more than minimum 32 chars)
JWT_SECRET=$(openssl rand -base64 96 | tr -d "=+/" | cut -c1-128)
echo -e "  ${GREEN}✓${NC} JWT secret generated (128 chars)"

# JWT Issuer and Audience
JWT_ISSUER="piston-control"
JWT_AUDIENCE="piston-app"
echo -e "  ${GREEN}✓${NC} JWT issuer and audience configured"

# Redis password
REDIS_PASSWORD=$(openssl rand -hex 32)
echo -e "  ${GREEN}✓${NC} Redis password generated"

# Session encryption and signing keys (hex format for Ktor)
SESSION_ENCRYPT_KEY=$(openssl rand -hex 16)  # 16 bytes for encryption
SESSION_SIGN_KEY=$(openssl rand -hex 32)      # 32 bytes for signing
echo -e "  ${GREEN}✓${NC} Session keys generated"

# MQTT broker password (for future password-based auth)
MQTT_PASSWORD=$(openssl rand -hex 32)
echo -e "  ${GREEN}✓${NC} MQTT password generated"

# Build metadata
BUILD_DATE=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
VCS_REF=$(git rev-parse --short HEAD 2>/dev/null || echo "main")
echo -e "  ${GREEN}✓${NC} Build metadata: ${VCS_REF} @ ${BUILD_DATE}"

################################################################################
# STEP 2: Create .env File
################################################################################
echo ""
echo -e "${CYAN}[2/6]${NC} Creating .env file..."

sudo tee "${SCRIPT_DIR}/.env" > /dev/null <<EOF
# ========================================
# Piston Control System - Environment Variables
# ========================================
# Generated: ${BUILD_DATE}
#
# WARNING: This file contains sensitive secrets!
# DO NOT commit this file to version control
# DO NOT share these values
# ========================================

# PostgreSQL Database
POSTGRES_PASSWORD=${POSTGRES_PASSWORD}

# JWT Authentication
JWT_SECRET=${JWT_SECRET}
JWT_ISSUER=${JWT_ISSUER}
JWT_AUDIENCE=${JWT_AUDIENCE}

# Redis Cache
REDIS_PASSWORD=${REDIS_PASSWORD}

# Session Management (for admin web dashboard)
SESSION_ENCRYPT_KEY=${SESSION_ENCRYPT_KEY}
SESSION_SIGN_KEY=${SESSION_SIGN_KEY}

# MQTT Broker (for future password-based authentication)
MQTT_PASSWORD=${MQTT_PASSWORD}

# Build metadata
BUILD_DATE=${BUILD_DATE}
VCS_REF=${VCS_REF}
EOF

sudo chmod 600 "${SCRIPT_DIR}/.env"
echo -e "  ${GREEN}✓${NC} .env file created with secure permissions (600)"

################################################################################
# STEP 3: Generate MQTT Certificates
################################################################################
echo ""
echo -e "${CYAN}[3/6]${NC} Generating MQTT TLS certificates..."

CERTS_DIR="${SCRIPT_DIR}/certs"
sudo mkdir -p "${CERTS_DIR}"
cd "${CERTS_DIR}"

# Certificate validity period
CERT_DAYS=3650  # 10 years

# Certificate subject information
COUNTRY="US"
STATE="California"
CITY="San Francisco"
ORG="Piston Control"
OU="IoT"

# Generate CA (Certificate Authority)
echo -e "  ${YELLOW}→${NC} Generating Certificate Authority..."
sudo openssl req -new -x509 -days ${CERT_DAYS} -extensions v3_ca \
    -keyout ca.key -out ca.crt \
    -subj "/C=${COUNTRY}/ST=${STATE}/L=${CITY}/O=${ORG}/OU=${OU}/CN=Piston-CA" \
    -passout pass:"" 2>/dev/null

sudo chmod 600 ca.key
echo -e "  ${GREEN}✓${NC} CA certificate created"

# Generate MQTT Server Certificate
echo -e "  ${YELLOW}→${NC} Generating MQTT server certificate..."
sudo openssl genrsa -out server.key 2048 2>/dev/null
sudo openssl req -new -key server.key -out server.csr \
    -subj "/C=${COUNTRY}/ST=${STATE}/L=${CITY}/O=${ORG}/OU=${OU}/CN=mosquitto" 2>/dev/null
sudo openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key \
    -CAcreateserial -out server.crt -days ${CERT_DAYS} 2>/dev/null

sudo chmod 600 server.key
echo -e "  ${GREEN}✓${NC} MQTT server certificate created"

# Generate Device Certificates (5 devices)
echo -e "  ${YELLOW}→${NC} Generating device certificates..."
for i in {1..5}; do
    DEVICE_CN="device${i}"

    sudo openssl genrsa -out "${DEVICE_CN}.key" 2048 2>/dev/null
    sudo openssl req -new -key "${DEVICE_CN}.key" -out "${DEVICE_CN}.csr" \
        -subj "/C=${COUNTRY}/ST=${STATE}/L=${CITY}/O=${ORG}/OU=${OU}/CN=${DEVICE_CN}" 2>/dev/null
    sudo openssl x509 -req -in "${DEVICE_CN}.csr" -CA ca.crt -CAkey ca.key \
        -CAcreateserial -out "${DEVICE_CN}.crt" -days ${CERT_DAYS} 2>/dev/null

    sudo chmod 600 "${DEVICE_CN}.key"
done
echo -e "  ${GREEN}✓${NC} Generated certificates for 5 devices"

# Clean up CSR files
sudo rm -f *.csr

cd "${SCRIPT_DIR}"
echo -e "  ${GREEN}✓${NC} MQTT certificates saved to ${CERTS_DIR}/"

################################################################################
# STEP 4: Generate Nginx SSL Certificates
################################################################################
echo ""
echo -e "${CYAN}[4/6]${NC} Generating Nginx SSL certificates..."

NGINX_SSL_DIR="${SCRIPT_DIR}/nginx/ssl"
sudo mkdir -p "${NGINX_SSL_DIR}"
cd "${NGINX_SSL_DIR}"

# Generate self-signed certificate for Nginx (for HTTPS)
echo -e "  ${YELLOW}→${NC} Generating self-signed SSL certificate..."
sudo openssl req -x509 -nodes -days ${CERT_DAYS} -newkey rsa:4096 \
    -keyout key.pem -out cert.pem \
    -subj "/C=${COUNTRY}/ST=${STATE}/L=${CITY}/O=${ORG}/OU=${OU}/CN=localhost" 2>/dev/null

sudo chmod 600 key.pem
echo -e "  ${GREEN}✓${NC} Nginx SSL certificate created"
echo -e "  ${YELLOW}⚠${NC}  Using self-signed certificate (not for production)"
echo -e "  ${YELLOW}⚠${NC}  For production, use Let's Encrypt or a trusted CA"

cd "${SCRIPT_DIR}"

################################################################################
# STEP 5: Create MQTT Password File (Optional)
################################################################################
echo ""
echo -e "${CYAN}[5/6]${NC} Creating MQTT password file..."

MOSQUITTO_DIR="${SCRIPT_DIR}/mosquitto"
sudo mkdir -p "${MOSQUITTO_DIR}/config"

# Create password file with a default user (optional, for testing)
# Note: The current config uses certificate-based auth, but this is for future use
PASSWD_FILE="${MOSQUITTO_DIR}/config/passwd"
sudo tee "${PASSWD_FILE}" > /dev/null <<EOF
# MQTT Password File
# Generated: ${BUILD_DATE}
#
# To add users, run:
#   docker exec piston-mosquitto mosquitto_passwd -b /mosquitto/config/passwd username password
#
# Current authentication: TLS certificates (listener 8883)
# This file is for future password-based authentication
EOF

sudo chmod 600 "${PASSWD_FILE}"
echo -e "  ${GREEN}✓${NC} MQTT password file template created"
echo -e "  ${YELLOW}ℹ${NC}  Current config uses TLS certificate authentication"

################################################################################
# STEP 6: Set Correct Permissions
################################################################################
echo ""
echo -e "${CYAN}[6/6]${NC} Setting file permissions..."

# Secure all certificate directories
sudo chmod 700 "${CERTS_DIR}"
sudo chmod 600 "${CERTS_DIR}"/*.key "${CERTS_DIR}"/*.crt 2>/dev/null || true
echo -e "  ${GREEN}✓${NC} MQTT certificates secured (700/600)"

sudo chmod 700 "${NGINX_SSL_DIR}"
sudo chmod 600 "${NGINX_SSL_DIR}"/*.pem 2>/dev/null || true
echo -e "  ${GREEN}✓${NC} Nginx certificates secured (700/600)"

# Ensure mosquitto directories exist with correct permissions
sudo mkdir -p "${MOSQUITTO_DIR}/data" "${MOSQUITTO_DIR}/log"
sudo chmod 755 "${MOSQUITTO_DIR}/data" "${MOSQUITTO_DIR}/log"
# Set ownership for mosquitto user (1883:1883 as per docker-compose)
sudo chown -R 1883:1883 "${MOSQUITTO_DIR}/data" "${MOSQUITTO_DIR}/log" 2>/dev/null || true
echo -e "  ${GREEN}✓${NC} Mosquitto directories created with correct ownership"

################################################################################
# Summary
################################################################################
echo ""
echo -e "${BLUE}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                                                          ║${NC}"
echo -e "${BLUE}║              ✅ Initialization Complete!                 ║${NC}"
echo -e "${BLUE}║                                                          ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${GREEN}Generated Files:${NC}"
echo -e "  • ${SCRIPT_DIR}/.env"
echo -e "  • ${CERTS_DIR}/ca.{crt,key}"
echo -e "  • ${CERTS_DIR}/server.{crt,key}"
echo -e "  • ${CERTS_DIR}/device{1..5}.{crt,key}"
echo -e "  • ${NGINX_SSL_DIR}/{cert,key}.pem"
echo -e "  • ${MOSQUITTO_DIR}/config/passwd"
echo ""
echo -e "${YELLOW}Security Checklist:${NC}"
echo -e "  ${GREEN}✓${NC} .env file created with secure permissions"
echo -e "  ${GREEN}✓${NC} All secrets are cryptographically random"
echo -e "  ${GREEN}✓${NC} MQTT TLS certificates generated (10-year validity)"
echo -e "  ${GREEN}✓${NC} Nginx SSL certificates generated"
echo -e "  ${GREEN}✓${NC} File permissions set correctly"
echo ""
echo -e "${CYAN}Next Steps:${NC}"
echo -e "  1. Review the .env file: ${SCRIPT_DIR}/.env"
echo -e "  2. Start the containers: docker-compose up -d"
echo -e "  3. Check container logs: docker-compose logs -f"
echo -e "  4. Verify database: docker exec -it piston-postgres psql -U piston_user -d piston_control"
echo ""
echo -e "${YELLOW}Important Reminders:${NC}"
echo -e "  ⚠️  NEVER commit .env to version control"
echo -e "  ⚠️  NEVER share secrets or certificates"
echo -e "  ⚠️  For production, replace self-signed SSL with trusted certificates"
echo -e "  ⚠️  Back up .env and certificates securely"
echo ""
echo -e "${GREEN}System is ready for deployment! 🚀${NC}"
echo ""
