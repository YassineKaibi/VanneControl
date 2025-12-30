# IoT Piston Control System

A production-ready self-hosted IoT platform for remotely controlling piston actuators via MQTT. Features comprehensive scheduling, admin management, and real-time monitoring capabilities.

## Features

### Core Functionality
- **Secure Authentication** - JWT-based authentication with refresh tokens and BCrypt password hashing
- **REST API** - Complete RESTful API for device management, user profiles, and system control
- **MQTT Communication** - Real-time bidirectional device communication via Mosquitto broker with TLS support
- **WebSocket Support** - Live status updates with JWT-authenticated WebSocket connections
- **Automated Scheduling** - Cron-based scheduling system using Quartz for automated piston control

### Administration & Management
- **Role-Based Access Control** - User and admin roles with granular permissions
- **Web Admin Dashboard** - Full-featured HTML admin panel with FreeMarker templates
- **Audit Logging** - Comprehensive activity tracking for compliance and security
- **User Management** - Admin tools for user creation, role assignment, and deletion
- **System Statistics** - Real-time dashboard with user counts, device metrics, and activity logs

### Infrastructure
- **Docker Compose** - Complete containerized deployment with health checks
- **PostgreSQL Database** - Robust data persistence with JSONB support and optimized indexes
- **Redis Caching** - High-performance caching layer for improved response times
- **Nginx Reverse Proxy** - SSL/TLS termination and load balancing
- **Mobile App Support** - Native Android application (see separate README)

## Architecture

```
┌─────────────────┐
│  Mobile/Web App │
└────────┬────────┘
         │ HTTPS/WSS
    ┌────▼─────┐
    │  Nginx   │ (Reverse Proxy)
    └────┬─────┘
         │
    ┌────▼─────┐
    │  Ktor    │ (Backend API)
    │ Backend  │
    └─┬──┬──┬──┘
      │  │  │
   ┌──▼──▼──▼────┐
   │ PostgreSQL  │
   │   Redis     │
   └─────────────┘
         │
    ┌────▼──────┐
    │ Mosquitto │ (MQTT Broker)
    └────┬──────┘
         │
    ┌────▼──────┐
    │   ESP32   │ (IoT Devices)
    │  Devices  │
    └───────────┘
```

### System Components

**Backend Server** (`backend/`)
- Ktor framework (Kotlin)
- REST API + WebSocket endpoints
- JWT authentication with role-based access
- MQTT client integration
- Quartz scheduler for automated tasks
- FreeMarker templating for admin web UI

**Database Layer** (PostgreSQL)
- User accounts & authentication
- Device registry with ownership
- Piston states & history (8 pistons per device)
- Telemetry data with JSONB payloads
- Schedules with cron expressions
- Audit logs for admin actions

**Message Broker** (Mosquitto MQTT)
- Device command/control messages
- Real-time status updates
- TLS encryption support
- Binary protocol for ESP32 devices

**Caching Layer** (Redis)
- Session storage
- API response caching
- Real-time data buffering

**Reverse Proxy** (Nginx)
- SSL/TLS termination
- Load balancing
- Static content serving
- WebSocket proxying

## Quick Start

### Prerequisites

- Docker & Docker Compose
- OpenSSL (for certificate generation)
- Python 3.8+ (for device client/simulator)

### Installation

1. **Clone the repository**
```bash
git clone https://github.com/YassineKaibi/VanneControl.git
cd VanneControl
```

2. **Generate certificates**
```bash
chmod +x generate-certs.sh
./generate-certs.sh
```

3. **Configure environment**
```bash
cp .env.example .env
# Edit .env with your secure passwords
nano .env
```

Required environment variables:
```bash
POSTGRES_PASSWORD=your_secure_postgres_password
REDIS_PASSWORD=your_secure_redis_password
JWT_SECRET=your_jwt_secret_key
JWT_ISSUER=piston-control
JWT_AUDIENCE=piston-app
SESSION_ENCRYPT_KEY=your_32_char_session_encrypt_key
SESSION_SIGN_KEY=your_32_char_session_sign_key
```

4. **Start services**
```bash
docker compose build
docker compose up -d
```

5. **Verify deployment**
```bash
curl http://localhost:8080/health
```

Expected response:
```json
{
  "status": "healthy",
  "timestamp": 1234567890
}
```

## API Documentation

### Authentication

**Register User**
```bash
POST /auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "SecurePass123!"
}
```

Response (201 Created):
```json
{
  "user": {
    "id": "uuid",
    "email": "user@example.com",
    "role": "user"
  },
  "token": "eyJhbGc..."
}
```

**Login**
```bash
POST /auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "SecurePass123!"
}
```

Response (200 OK):
```json
{
  "token": "eyJhbGc...",
  "refreshToken": "refresh_token_here"
}
```

### Device Management

**List Devices**
```bash
GET /devices
Authorization: Bearer <token>
```

**Get Device Details**
```bash
GET /devices/{deviceId}
Authorization: Bearer <token>
```

**Control Piston**
```bash
POST /devices/{deviceId}/pistons/{pistonNumber}
Authorization: Bearer <token>
Content-Type: application/json

{
  "action": "activate"
}
```

**Get Device Statistics**
```bash
GET /devices/{deviceId}/stats
Authorization: Bearer <token>
```

### Schedule Management

**Create Schedule**
```bash
POST /schedules
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Morning activation",
  "deviceId": "device-uuid",
  "pistonNumber": 1,
  "action": "ACTIVATE",
  "cronExpression": "0 0 8 * * ?",
  "enabled": true
}
```

Cron expression format: `second minute hour day month day-of-week`

**List User Schedules**
```bash
GET /schedules
Authorization: Bearer <token>
```

**Get Schedule by ID**
```bash
GET /schedules/{scheduleId}
Authorization: Bearer <token>
```

**Update Schedule**
```bash
PUT /schedules/{scheduleId}
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Updated name",
  "cronExpression": "0 0 20 * * ?",
  "enabled": false
}
```

**Delete Schedule**
```bash
DELETE /schedules/{scheduleId}
Authorization: Bearer <token>
```

**Get Schedules by Device**
```bash
GET /schedules/device/{deviceId}
Authorization: Bearer <token>
```

### User Profile

**Get Profile**
```bash
GET /user/profile
Authorization: Bearer <token>
```

**Update Profile**
```bash
PUT /user/profile
Authorization: Bearer <token>
Content-Type: application/json

{
  "firstName": "John",
  "lastName": "Doe",
  "phoneNumber": "+1234567890",
  "location": "New York"
}
```

### Admin Endpoints

All admin endpoints require `admin` role.

**List All Users**
```bash
GET /admin/users?limit=50&offset=0
Authorization: Bearer <admin-token>
```

**Get User by ID**
```bash
GET /admin/users/{userId}
Authorization: Bearer <admin-token>
```

**Update User Role**
```bash
PATCH /admin/users/{userId}/role
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "role": "admin"
}
```

**Delete User**
```bash
DELETE /admin/users/{userId}
Authorization: Bearer <admin-token>
```

**Get System Statistics**
```bash
GET /admin/stats
Authorization: Bearer <admin-token>
```

Response:
```json
{
  "totalUsers": 150,
  "totalAdmins": 5,
  "totalDevices": 75,
  "totalSchedules": 200,
  "recentAuditLogs": [...]
}
```

**Get Audit Logs**
```bash
GET /admin/audit-logs?limit=100&offset=0
Authorization: Bearer <admin-token>
```

### Admin Web Dashboard

Access the web-based admin panel at:
```
http://localhost:8080/admin/login
```

Features:
- User management interface
- System statistics dashboard
- Audit log viewer
- Role assignment tools
- Session-based authentication

### WebSocket Connection

**Connect to WebSocket**
```javascript
const ws = new WebSocket('ws://localhost:8080/ws?token=<jwt-token>');

ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  console.log('Device update:', data);
};
```

WebSocket message format:
```json
{
  "deviceId": "uuid",
  "pistonNumber": 1,
  "state": "active",
  "timestamp": 1234567890
}
```

## Security

### Production Security Checklist

**Environment Variables**
- Change all default passwords in `.env`
- Use strong random values for JWT secrets
- Generate unique session encryption keys

**TLS/SSL**
- Enable HTTPS in Nginx configuration
- Use valid SSL certificates (Let's Encrypt recommended)
- Configure MQTT broker for TLS on port 8883

**Database**
- Use strong PostgreSQL password
- Restrict database network access
- Enable SSL connections in production

**Authentication**
- JWT tokens expire after configurable period
- Refresh tokens for long-lived sessions
- BCrypt password hashing with salt rounds

**Admin Access**
- Create admin users manually via database
- Use separate admin JWT authentication
- All admin actions logged to audit_logs table

**Network Security**
- Use Docker network isolation
- Configure firewall rules
- Limit exposed ports

### Creating Admin User

1. Register a normal user via `/auth/register`
2. Promote to admin via database:
```sql
UPDATE users SET role = 'admin' WHERE email = 'admin@example.com';
```

## Database Schema

### Tables

**users**
- `id` (UUID, PK)
- `email` (TEXT, UNIQUE)
- `password_hash` (TEXT)
- `role` (TEXT: 'user' | 'admin')
- `first_name`, `last_name`, `phone_number`, `date_of_birth`, `location`
- `avatar_url` (TEXT)
- `preferences` (JSONB)
- `created_at`, `updated_at` (TIMESTAMP)

**devices**
- `id` (UUID, PK)
- `name` (TEXT)
- `owner_id` (UUID, FK -> users)
- `mqtt_client_id` (TEXT, UNIQUE)
- `status` (TEXT: 'online' | 'offline')
- `created_at`, `updated_at` (TIMESTAMP)

**pistons**
- `id` (UUID, PK)
- `device_id` (UUID, FK -> devices)
- `piston_number` (INT: 1-8)
- `state` (TEXT: 'active' | 'inactive')
- `last_triggered` (TIMESTAMP)

**telemetry**
- `id` (BIGSERIAL, PK)
- `device_id` (UUID, FK -> devices)
- `piston_id` (UUID, FK -> pistons)
- `event_type` (TEXT: 'activated' | 'deactivated' | 'status_update')
- `payload` (JSONB)
- `created_at` (TIMESTAMP)

**schedules**
- `id` (UUID, PK)
- `name` (TEXT)
- `device_id` (UUID, FK -> devices)
- `piston_number` (INT: 1-8)
- `action` (TEXT: 'ACTIVATE' | 'DEACTIVATE')
- `cron_expression` (TEXT)
- `enabled` (BOOLEAN)
- `user_id` (UUID, FK -> users)
- `created_at`, `updated_at` (TIMESTAMP)

**audit_logs**
- `id` (UUID, PK)
- `user_id` (UUID, FK -> users)
- `action` (TEXT)
- `target_user_id` (UUID, FK -> users)
- `target_resource_type` (TEXT)
- `target_resource_id` (TEXT)
- `details` (JSONB)
- `ip_address`, `user_agent` (TEXT)
- `created_at` (TIMESTAMP)

**auth_tokens**
- `id` (UUID, PK)
- `user_id` (UUID, FK -> users)
- `refresh_token` (TEXT, UNIQUE)
- `expires_at` (TIMESTAMP)
- `created_at` (TIMESTAMP)

## Technology Stack

### Backend
- **Framework**: Ktor 2.3 (Kotlin)
- **Language**: Kotlin 1.9
- **Database ORM**: Exposed (JetBrains)
- **Authentication**: JWT (JSON Web Tokens)
- **Password Hashing**: BCrypt (jBCrypt)
- **Scheduler**: Quartz Scheduler
- **Templating**: FreeMarker
- **WebSocket**: Ktor WebSockets
- **Serialization**: kotlinx.serialization

### Infrastructure
- **Database**: PostgreSQL 15 Alpine
- **Cache**: Redis 7.2 Alpine
- **MQTT Broker**: Eclipse Mosquitto 2.0.18
- **Reverse Proxy**: Nginx 1.25 Alpine
- **Containerization**: Docker & Docker Compose
- **Container Registry**: Docker Hub (zizyp/vannecontrol-backend)

### Device Integration
- **MQTT Client**: Paho MQTT
- **Hardware**: ESP32, Raspberry Pi
- **Protocol**: Custom binary protocol for efficient communication
- **Language**: Python 3 (device simulator), C++ (ESP32 firmware)

### Monitoring & Observability
- **Health Checks**: Built-in health endpoints for all services
- **Logging**: Logback (backend), Docker logs
- **Metrics**: MQTT system topics, telemetry database

## Project Structure

```
.
├── backend/                    # Ktor backend application
│   ├── src/main/kotlin/com/pistoncontrol/
│   │   ├── Application.kt      # Main application entry point
│   │   ├── database/
│   │   │   ├── DatabaseFactory.kt
│   │   │   ├── Tables.kt       # Database schema definitions
│   │   │   └── JsonbColumnType.kt
│   │   ├── models/
│   │   │   └── Models.kt       # Data models and DTOs
│   │   ├── routes/
│   │   │   ├── AuthRoutes.kt   # Authentication endpoints
│   │   │   ├── DeviceRoutes.kt # Device management endpoints
│   │   │   ├── UserRoutes.kt   # User profile endpoints
│   │   │   ├── ScheduleRoutes.kt # Scheduling endpoints
│   │   │   ├── AdminRoutes.kt  # Admin API endpoints
│   │   │   ├── AdminWebRoutes.kt # Admin web UI routes
│   │   │   └── Models.kt       # Route-specific models
│   │   ├── services/
│   │   │   ├── AuthService.kt
│   │   │   ├── UserService.kt
│   │   │   ├── DeviceService.kt
│   │   │   ├── ScheduleService.kt
│   │   │   ├── ScheduleExecutor.kt # Quartz integration
│   │   │   ├── AdminService.kt
│   │   │   ├── AuditLogService.kt
│   │   │   └── DeviceMessageHandler.kt
│   │   ├── plugins/
│   │   │   ├── Routing.kt      # Route configuration
│   │   │   ├── Security.kt     # JWT authentication
│   │   │   ├── Serialization.kt
│   │   │   ├── Sockets.kt      # WebSocket config
│   │   │   ├── Sessions.kt     # Session management
│   │   │   ├── Templating.kt   # FreeMarker config
│   │   │   ├── StaticContent.kt
│   │   │   └── Monitoring.kt
│   │   ├── mqtt/
│   │   │   └── MqttManager.kt  # MQTT client
│   │   ├── websocket/
│   │   │   └── WebSocketManager.kt
│   │   └── protocol/
│   │       └── BinaryProtocolParser.kt
│   ├── src/main/resources/
│   │   ├── templates/          # FreeMarker templates
│   │   │   └── admin/          # Admin dashboard pages
│   │   ├── static/             # Static assets (CSS, JS)
│   │   └── logback.xml         # Logging configuration
│   ├── build.gradle.kts
│   └── Dockerfile
├── mosquitto/                  # MQTT broker configuration
│   └── config/
│       └── mosquitto.conf
├── nginx/                      # Reverse proxy configuration
│   ├── nginx.conf
│   └── ssl/                    # SSL certificates
├── certs/                      # MQTT TLS certificates
├── esp32/                      # ESP32 device firmware
│   └── device_client/
├── scripts/                    # Utility scripts
├── testing/                    # Test scripts and tools
├── init-db.sql                 # Database initialization
├── docker-compose.yml          # Service orchestration
├── generate-certs.sh           # Certificate generation script
├── .env.example                # Environment template
└── README.md
```

## Development

### Running Locally

**Start all services:**
```bash
docker compose up -d
```

**View logs:**
```bash
docker compose logs -f backend
docker compose logs -f mosquitto
docker compose logs -f postgres
```

**Rebuild after code changes:**
```bash
docker compose build backend
docker compose up -d backend
```

**Access services:**
- Backend API: http://localhost:8080
- Admin Dashboard: http://localhost:8080/admin/login
- PostgreSQL: localhost:5432
- MQTT Broker: localhost:1883 (plaintext), localhost:8883 (TLS)
- Redis: localhost:6379
- Nginx: http://localhost:80

### Database Management

**Connect to PostgreSQL:**
```bash
docker compose exec postgres psql -U piston_user -d piston_control
```

**Run migrations:**
```bash
docker compose exec postgres psql -U piston_user -d piston_control -f /docker-entrypoint-initdb.d/01-init-db.sql
```

**Backup database:**
```bash
docker compose exec postgres pg_dump -U piston_user piston_control > backup.sql
```

### Testing Device Connections

**Using Python MQTT client:**
```bash
cd testing
python3 device_client.py
```

**Manual MQTT publish:**
```bash
mosquitto_pub -h localhost -t "devices/device-id/command" -m '{"piston":1,"action":"activate"}'
```

**Subscribe to device updates:**
```bash
mosquitto_sub -h localhost -t "devices/+/status" -v
```

## Deployment

### Production Deployment

1. **Configure environment for production**
   - Set strong passwords and secrets
   - Configure SSL certificates
   - Update CORS settings
   - Set appropriate resource limits

2. **Use production Docker image**
   ```bash
   docker pull zizyp/vannecontrol-backend:latest
   ```

3. **Enable SSL/TLS**
   - Configure Nginx with valid SSL certificates
   - Enable MQTT TLS on port 8883
   - Update client configurations

4. **Set up monitoring**
   - Configure log aggregation
   - Set up health check monitoring
   - Enable PostgreSQL backup automation

5. **Secure the database**
   - Restrict network access
   - Enable SSL connections
   - Configure automated backups

### Environment Variables Reference

| Variable | Description | Default |
|----------|-------------|---------|
| `POSTGRES_PASSWORD` | PostgreSQL password | - |
| `REDIS_PASSWORD` | Redis password | - |
| `JWT_SECRET` | JWT signing secret | - |
| `JWT_ISSUER` | JWT issuer claim | piston-control |
| `JWT_AUDIENCE` | JWT audience claim | piston-app |
| `SESSION_ENCRYPT_KEY` | Session encryption key (32 chars) | - |
| `SESSION_SIGN_KEY` | Session signing key (32 chars) | - |
| `DATABASE_URL` | PostgreSQL connection URL | jdbc:postgresql://postgres:5432/piston_control |
| `MQTT_BROKER` | MQTT broker URL | tcp://mosquitto:1883 |

## Troubleshooting

### Common Issues

**Backend fails to start:**
- Check if PostgreSQL is ready: `docker compose logs postgres`
- Verify environment variables are set correctly
- Check database connection: `docker compose exec postgres pg_isready`

**MQTT connection issues:**
- Verify Mosquitto is running: `docker compose ps mosquitto`
- Check broker logs: `docker compose logs mosquitto`
- Test connection: `mosquitto_sub -h localhost -t test`

**WebSocket connection fails:**
- Ensure JWT token is valid
- Check Nginx WebSocket proxy configuration
- Verify CORS settings

**Admin login not working:**
- Ensure user has admin role in database
- Check session configuration
- Verify admin JWT configuration

## Mobile Application

The Android mobile application provides a comprehensive interface for device management. See the [Mobile App README](../piston-control-mobile/MyApplicationV10/README.md) for:
- Installation instructions
- Feature documentation
- Development setup
- API integration details

## Contributing

Contributions are welcome! Please follow these guidelines:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Code Style

- Kotlin: Follow official Kotlin coding conventions
- Use meaningful variable and function names
- Add comments for complex logic
- Write unit tests for new features

## License

This project is licensed under the Apache-2.0 License - see the [LICENSE](LICENSE) file for details.

## Contact

**Mohamed Yassine Kaibi**
- LinkedIn: [https://www.linkedin.com/in/mohamedyassinekaibi/](https://www.linkedin.com/in/mohamedyassinekaibi/)
- GitHub: [https://github.com/YassineKaibi](https://github.com/YassineKaibi)

## Acknowledgments

- [Ktor](https://ktor.io/) - Kotlin web framework
- [Mosquitto](https://mosquitto.org/) - MQTT broker
- [Exposed](https://github.com/JetBrains/Exposed) - Kotlin SQL framework
- [Quartz Scheduler](http://www.quartz-scheduler.org/) - Job scheduling library
- [FreeMarker](https://freemarker.apache.org/) - Template engine
