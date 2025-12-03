#!/bin/bash
set -e

################################################################################
# Piston Control System - Security Initialization Script
################################################################################

RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Organization info for certificates
COUNTRY="TN"
STATE="Sfax"
CITY="Sfax"
ORG="Tesla Energie"
OU="IoT"

# Let's Encrypt configuration (set via environment variables)
# Example: DOMAIN="vannecontrol.swedencentral.cloudapp.azure.com" ./init-secrets.sh
DOMAIN="${DOMAIN:-}"
LETSENCRYPT_EMAIL="${LETSENCRYPT_EMAIL:-admin@localhost}"

echo -e "${BLUE}🔐 Piston Control System Security Initialization${NC}"

# Check if using Let's Encrypt
if [ -n "$DOMAIN" ]; then
    echo -e "${CYAN}Mode:${NC} Let's Encrypt (domain: ${DOMAIN})"
else
    echo -e "${CYAN}Mode:${NC} Self-signed certificates (development)"
fi

################################################################################
# STEP 1: Generate Passwords and Secrets
################################################################################
echo -e "${CYAN}[1/7]${NC} Generating passwords and secrets..."
POSTGRES_PASSWORD=$(openssl rand -hex 32)
JWT_SECRET=$(openssl rand -base64 96 | tr -d "=+/" | cut -c1-128)
JWT_ISSUER="piston-control"
JWT_AUDIENCE="piston-app"
REDIS_PASSWORD=$(openssl rand -hex 32)
SESSION_ENCRYPT_KEY=$(openssl rand -hex 16)
SESSION_SIGN_KEY=$(openssl rand -hex 32)
MQTT_PASSWORD=$(openssl rand -hex 32)
BUILD_DATE=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
VCS_REF=$(git rev-parse --short HEAD 2>/dev/null || echo "main")
echo -e "  ${GREEN}✓${NC} Secrets generated"

################################################################################
# STEP 2: Create .env File
################################################################################
echo -e "${CYAN}[2/7]${NC} Creating .env file..."
tee "${SCRIPT_DIR}/.env" > /dev/null <<EOF
POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
JWT_SECRET=${JWT_SECRET}
JWT_ISSUER=${JWT_ISSUER}
JWT_AUDIENCE=${JWT_AUDIENCE}
REDIS_PASSWORD=${REDIS_PASSWORD}
SESSION_ENCRYPT_KEY=${SESSION_ENCRYPT_KEY}
SESSION_SIGN_KEY=${SESSION_SIGN_KEY}
MQTT_PASSWORD=${MQTT_PASSWORD}
BUILD_DATE=${BUILD_DATE}
VCS_REF=${VCS_REF}
EOF
chmod 644 "${SCRIPT_DIR}/.env"
echo -e "  ${GREEN}✓${NC} .env created (644)"

################################################################################
# STEP 3: Generate MQTT TLS Certificates
################################################################################
echo -e "${CYAN}[3/7]${NC} Generating MQTT TLS certificates..."
CERTS_DIR="${SCRIPT_DIR}/certs"
mkdir -p "${CERTS_DIR}"
touch "${CERTS_DIR}/.gitkeep" 2>/dev/null || true
CERT_DAYS=3650
cd "${CERTS_DIR}"

# CA
openssl req -new -x509 -days ${CERT_DAYS} -extensions v3_ca \
    -keyout ca.key -out ca.crt \
    -subj "/C=${COUNTRY}/ST=${STATE}/L=${CITY}/O=${ORG}/OU=${OU}/CN=Piston-CA" \
    -nodes >/dev/null 2>&1

# Server certificate
openssl genrsa -out server.key 2048 >/dev/null 2>&1
openssl req -new -key server.key -out server.csr \
    -subj "/C=${COUNTRY}/ST=${STATE}/L=${CITY}/O=${ORG}/OU=${OU}/CN=mosquitto" >/dev/null 2>&1
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key \
    -CAcreateserial -out server.crt -days ${CERT_DAYS} >/dev/null 2>&1
rm -f server.csr ca.srl

chmod 644 ca.crt server.crt
chmod 600 ca.key server.key

cd "${SCRIPT_DIR}"

################################################################################
# STEP 4: Generate Nginx SSL Certificates
################################################################################
echo -e "${CYAN}[4/7]${NC} Generating Nginx SSL certificates..."
NGINX_SSL_DIR="${SCRIPT_DIR}/nginx/ssl"
mkdir -p "${NGINX_SSL_DIR}"
touch "${NGINX_SSL_DIR}/.gitkeep" 2>/dev/null || true

if [ -n "$DOMAIN" ]; then
    # Use Let's Encrypt via certbot
    echo -e "  ${BLUE}→${NC} Using Let's Encrypt for ${DOMAIN}"

    # Check if certbot is installed
    if ! command -v certbot &> /dev/null; then
        echo -e "  ${RED}✗${NC} certbot not found. Installing..."
        if command -v apt-get &> /dev/null; then
            sudo apt-get update -qq && sudo apt-get install -y certbot >/dev/null 2>&1
        elif command -v yum &> /dev/null; then
            sudo yum install -y certbot >/dev/null 2>&1
        else
            echo -e "  ${RED}✗${NC} Cannot install certbot automatically. Please install manually:"
            echo -e "     Ubuntu/Debian: sudo apt-get install certbot"
            echo -e "     CentOS/RHEL: sudo yum install certbot"
            exit 1
        fi
    fi

    # Check if port 80 is available
    if ! sudo netstat -tuln 2>/dev/null | grep -q ':80 ' && ! sudo ss -tuln 2>/dev/null | grep -q ':80 '; then
        echo -e "  ${BLUE}→${NC} Port 80 available for ACME challenge"
    else
        echo -e "  ${RED}⚠${NC}  Port 80 is in use. Stop nginx/apache first or use certbot with existing webserver"
    fi

    # Request certificate (standalone mode)
    echo -e "  ${BLUE}→${NC} Requesting certificate from Let's Encrypt..."
    sudo certbot certonly --standalone \
        --non-interactive \
        --agree-tos \
        --email "${LETSENCRYPT_EMAIL}" \
        --domain "${DOMAIN}" \
        --cert-name piston-control \
        2>&1 | grep -E "(Successfully received|Certificate not yet due)" || {
            echo -e "  ${RED}✗${NC} Let's Encrypt failed. Falling back to self-signed certificate..."
            DOMAIN=""  # Trigger fallback below
        }

    if [ -n "$DOMAIN" ]; then
        # Copy certificates to nginx/ssl directory
        sudo cp "/etc/letsencrypt/live/piston-control/fullchain.pem" "${NGINX_SSL_DIR}/cert.pem"
        sudo cp "/etc/letsencrypt/live/piston-control/privkey.pem" "${NGINX_SSL_DIR}/key.pem"
        sudo chown $(id -u):$(id -g) "${NGINX_SSL_DIR}/cert.pem" "${NGINX_SSL_DIR}/key.pem"
        chmod 644 "${NGINX_SSL_DIR}/cert.pem"
        chmod 600 "${NGINX_SSL_DIR}/key.pem"
        echo -e "  ${GREEN}✓${NC} Let's Encrypt certificate installed"
        echo -e "  ${BLUE}→${NC} Valid for 90 days. Set up auto-renewal with:"
        echo -e "     sudo certbot renew --deploy-hook 'cp /etc/letsencrypt/live/piston-control/*.pem ${NGINX_SSL_DIR}/'"
    fi
fi

# Fallback to self-signed certificate if DOMAIN not set or Let's Encrypt failed
if [ -z "$DOMAIN" ] || [ ! -f "${NGINX_SSL_DIR}/cert.pem" ]; then
    echo -e "  ${BLUE}→${NC} Generating self-signed certificate..."
    cd "${NGINX_SSL_DIR}"
    openssl req -x509 -nodes -days ${CERT_DAYS} -newkey rsa:4096 \
        -keyout key.pem -out cert.pem \
        -subj "/C=${COUNTRY}/ST=${STATE}/L=${CITY}/O=${ORG}/OU=${OU}/CN=localhost" >/dev/null 2>&1
    chmod 644 cert.pem
    chmod 600 key.pem
    cd "${SCRIPT_DIR}"
    echo -e "  ${GREEN}✓${NC} Self-signed certificate generated"
fi

################################################################################
# STEP 5: MQTT Password File
################################################################################
echo -e "${CYAN}[5/7]${NC} Creating MQTT password file..."
MOSQUITTO_DIR="${SCRIPT_DIR}/mosquitto"
mkdir -p "${MOSQUITTO_DIR}/config" "${MOSQUITTO_DIR}/data" "${MOSQUITTO_DIR}/log"
touch "${MOSQUITTO_DIR}/config/.gitkeep" 2>/dev/null || true

PASSWD_FILE="${MOSQUITTO_DIR}/config/passwd"
tee "${PASSWD_FILE}" > /dev/null <<EOF
# MQTT password file template
EOF
chmod 600 "${PASSWD_FILE}"

# Ensure mosquitto.conf is readable by host and Git
if [ -f "${MOSQUITTO_DIR}/config/mosquitto.conf" ]; then
    chmod 644 "${MOSQUITTO_DIR}/config/mosquitto.conf"
fi

################################################################################
# STEP 6: Directory Permissions
################################################################################
echo -e "${CYAN}[6/7]${NC} Setting directory permissions..."
chmod 755 "${CERTS_DIR}" "${NGINX_SSL_DIR}" "${MOSQUITTO_DIR}/config"
chmod 755 "${MOSQUITTO_DIR}/data" "${MOSQUITTO_DIR}/log"
echo -e "  ${GREEN}✓${NC} Permissions configured"

################################################################################
# STEP 7: Verification
################################################################################
echo -e "${CYAN}[7/7]${NC} Verifying generated files and permissions..."

ERRORS=0

# Function to check file existence and permissions
check_file() {
    local file=$1
    local expected_perms=$2
    local description=$3

    if [ ! -f "$file" ]; then
        echo -e "  ${RED}✗${NC} Missing: ${description} (${file})"
        ((ERRORS++))
        return 1
    fi

    local actual_perms=$(stat -c '%a' "$file" 2>/dev/null || stat -f '%A' "$file" 2>/dev/null)
    if [ "$actual_perms" != "$expected_perms" ]; then
        echo -e "  ${RED}✗${NC} Wrong permissions: ${description} (expected ${expected_perms}, got ${actual_perms})"
        ((ERRORS++))
        return 1
    fi

    echo -e "  ${GREEN}✓${NC} ${description} (${expected_perms})"
    return 0
}

# Function to check directory existence and permissions
check_dir() {
    local dir=$1
    local expected_perms=$2
    local description=$3

    if [ ! -d "$dir" ]; then
        echo -e "  ${RED}✗${NC} Missing directory: ${description} (${dir})"
        ((ERRORS++))
        return 1
    fi

    local actual_perms=$(stat -c '%a' "$dir" 2>/dev/null || stat -f '%A' "$dir" 2>/dev/null)
    if [ "$actual_perms" != "$expected_perms" ]; then
        echo -e "  ${RED}✗${NC} Wrong directory permissions: ${description} (expected ${expected_perms}, got ${actual_perms})"
        ((ERRORS++))
        return 1
    fi

    echo -e "  ${GREEN}✓${NC} ${description}/ (${expected_perms})"
    return 0
}

# Check .env file
check_file "${SCRIPT_DIR}/.env" "644" ".env file"

# Verify .env contains required variables
if [ -f "${SCRIPT_DIR}/.env" ]; then
    for var in POSTGRES_PASSWORD JWT_SECRET JWT_ISSUER JWT_AUDIENCE REDIS_PASSWORD SESSION_ENCRYPT_KEY SESSION_SIGN_KEY MQTT_PASSWORD; do
        if ! grep -q "^${var}=" "${SCRIPT_DIR}/.env"; then
            echo -e "  ${RED}✗${NC} Missing variable in .env: ${var}"
            ((ERRORS++))
        fi
    done
fi

# Check MQTT certificates
check_file "${CERTS_DIR}/ca.crt" "644" "MQTT CA certificate"
check_file "${CERTS_DIR}/ca.key" "600" "MQTT CA private key"
check_file "${CERTS_DIR}/server.crt" "644" "MQTT server certificate"
check_file "${CERTS_DIR}/server.key" "600" "MQTT server private key"

# Check Nginx certificates
check_file "${NGINX_SSL_DIR}/cert.pem" "644" "Nginx SSL certificate"
check_file "${NGINX_SSL_DIR}/key.pem" "600" "Nginx SSL private key"

# Check MQTT password file
check_file "${MOSQUITTO_DIR}/config/passwd" "600" "MQTT password file"

# Check directories
check_dir "${CERTS_DIR}" "755" "certs"
check_dir "${NGINX_SSL_DIR}" "755" "nginx/ssl"
check_dir "${MOSQUITTO_DIR}/config" "755" "mosquitto/config"
check_dir "${MOSQUITTO_DIR}/data" "755" "mosquitto/data"
check_dir "${MOSQUITTO_DIR}/log" "755" "mosquitto/log"

# Validate certificate expiry (MQTT CA)
if [ -f "${CERTS_DIR}/ca.crt" ]; then
    if openssl x509 -in "${CERTS_DIR}/ca.crt" -noout -checkend 86400 >/dev/null 2>&1; then
        EXPIRY=$(openssl x509 -in "${CERTS_DIR}/ca.crt" -noout -enddate | cut -d= -f2)
        echo -e "  ${GREEN}✓${NC} MQTT CA valid until: ${EXPIRY}"
    else
        echo -e "  ${RED}✗${NC} MQTT CA certificate expired or invalid"
        ((ERRORS++))
    fi
fi

# Validate Nginx certificate
if [ -f "${NGINX_SSL_DIR}/cert.pem" ]; then
    if openssl x509 -in "${NGINX_SSL_DIR}/cert.pem" -noout -checkend 86400 >/dev/null 2>&1; then
        EXPIRY=$(openssl x509 -in "${NGINX_SSL_DIR}/cert.pem" -noout -enddate | cut -d= -f2)
        SUBJECT=$(openssl x509 -in "${NGINX_SSL_DIR}/cert.pem" -noout -subject | sed 's/subject=//')
        echo -e "  ${GREEN}✓${NC} Nginx SSL valid until: ${EXPIRY}"
        echo -e "  ${BLUE}→${NC} Certificate subject: ${SUBJECT}"
    else
        echo -e "  ${RED}✗${NC} Nginx SSL certificate expired or invalid"
        ((ERRORS++))
    fi
fi

################################################################################
# DONE
################################################################################
if [ $ERRORS -eq 0 ]; then
    echo -e "${GREEN}✅ Initialization Complete! All checks passed.${NC}"
    echo ""
    echo -e "${BLUE}Next steps:${NC}"
    if [ -n "$DOMAIN" ] && [ -f "/etc/letsencrypt/live/piston-control/fullchain.pem" ]; then
        echo -e "  1. Set up certificate auto-renewal:"
        echo -e "     ${CYAN}sudo crontab -e${NC}"
        echo -e "     Add: ${CYAN}0 3 * * * certbot renew --deploy-hook 'cp /etc/letsencrypt/live/piston-control/*.pem ${NGINX_SSL_DIR}/ && docker-compose restart nginx'${NC}"
    fi
    echo -e "  2. Start the system:"
    echo -e "     ${CYAN}docker-compose up -d${NC}"
    echo -e "  3. Check logs:"
    echo -e "     ${CYAN}docker-compose logs -f${NC}"
    echo ""
    echo -e "Generated files:"
    echo -e "  • .env (644)"
    echo -e "  • certs/*.key (600), *.crt (644)"
    echo -e "  • nginx/ssl/*.pem (key 600, cert 644)"
    echo -e "  • mosquitto/config/passwd (600)"
    echo -e "  • mosquitto/data, log (755)"
else
    echo -e "${RED}❌ Initialization completed with ${ERRORS} error(s)${NC}"
    echo -e "Please review the errors above and re-run the script."
    exit 1
fi
