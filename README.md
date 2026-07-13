# Enterprise Identity & Access Management (IAM) Platform

A production-ready Identity & Access Management platform built with **Java 21** and **Spring Boot 3.3.x**, supporting full authentication, authorization, user lifecycle management, and enterprise-grade security features.

---

## 🏗 Architecture Overview

```
enterprise-iam/
├── src/main/java/com/iam/
│   ├── config/              # Spring configuration beans (Security, Redis, MFA, Async, CORS, Swagger)
│   ├── controller/          # REST API layer (Auth, User, Admin, Roles, Permissions, Audit)
│   ├── dto/                 # Request & Response Data Transfer Objects
│   │   ├── request/
│   │   └── response/
│   ├── entity/              # JPA entities mapped to MySQL tables
│   ├── exception/           # Custom exceptions & Global exception handler
│   ├── repository/          # Spring Data JPA repositories
│   ├── security/            # JWT filter, UserDetailsService, RateLimiting
│   ├── service/             # Core business logic services
│   └── util/                # Security & Token utility helpers
├── src/main/resources/
│   ├── db/migration/        # Flyway migration scripts (V1–V6)
│   ├── application.yml      # Local development config
│   └── application-docker.yml # Docker environment overrides
├── src/test/                # Unit & Integration tests (JUnit 5 + Mockito)
├── Dockerfile               # Multi-stage Docker build
├── docker-compose.yml       # Full stack: MySQL, Redis, MailHog, App
└── pom.xml
```

---

## 🚀 Features

### Authentication & Security
- ✅ User Registration with **email verification**
- ✅ JWT **access & refresh token** authentication (15min / 7day)
- ✅ **Token rotation** on refresh (prevents replay attacks)
- ✅ **Token blacklisting** on logout (Redis-backed)
- ✅ **Multi-Factor Authentication** (TOTP via Google Authenticator / Authy)
- ✅ Email OTP for step-up authentication
- ✅ **Rate limiting** on authentication endpoints (Redis-backed)
- ✅ **Account lockout** after 5 consecutive failed login attempts

### Authorization
- ✅ **Role-Based Access Control (RBAC)** with 4 default roles (SUPER_ADMIN, ADMIN, MANAGER, USER)
- ✅ **Granular Permission management** with resource:action pattern
- ✅ Method-level security with `@PreAuthorize`

### User Lifecycle
- ✅ Profile management (update name, phone)
- ✅ Password change (with current password verification)
- ✅ Password reset (email token-based)
- ✅ Admin user enable/disable/unlock/delete
- ✅ Admin role assignment/removal

### Session & Audit
- ✅ **Active session tracking** (view and revoke individual sessions)
- ✅ **Logout all** device capability
- ✅ Full **audit log trail** (all security events logged)
- ✅ Paginated audit log queries with filters

### DevOps
- ✅ **Flyway** database migrations (V1–V6)
- ✅ **Docker** multi-stage build (Maven → JRE Alpine)
- ✅ **Docker Compose** (MySQL 8, Redis 7, MailHog, App)
- ✅ **OpenAPI/Swagger** documentation at `/swagger-ui.html`
- ✅ Spring Boot Actuator health endpoint

---

## 📋 Prerequisites

| Tool | Version |
|------|---------|
| Java | 21+ |
| Maven | 3.9+ |
| MySQL | 8.0+ |
| Redis | 7.x |
| Docker & Docker Compose | Latest |

---

## ⚙️ Local Development Setup

### 1. Database Setup (MySQL)

```sql
CREATE DATABASE IF NOT EXISTS enterprise_iam;
```

> Flyway will auto-run all migrations on startup.

### 2. Start Redis (local)

```bash
# Using Docker:
docker run -d -p 6379:6379 redis:7-alpine
```

### 3. Start MailHog (email testing)

```bash
# Using Docker:
docker run -d -p 1025:1025 -p 8025:8025 mailhog/mailhog
```

> Access the MailHog web UI at [http://localhost:8025](http://localhost:8025)

### 4. Configure `application.yml`

Update `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/enterprise_iam?...
    username: root
    password: your_password
```

### 5. Build & Run

```bash
# Build and skip tests initially
mvn clean package -DskipTests

# Run the application
mvn spring-boot:run
```

The app will start on `http://localhost:8080`.

---

## 🐳 Docker Compose Setup (Recommended)

```bash
# 1. Copy environment file
cp .env.example .env

# 2. Edit .env if needed (optional - defaults work out-of-the-box)

# 3. Build & start all services
docker-compose up --build -d

# 4. Check logs
docker-compose logs -f app

# 5. Stop all services
docker-compose down
```

**Service Ports:**

| Service | Port |
|---------|------|
| Application | 8080 |
| MySQL | 3307 (host) → 3306 (container) |
| Redis | 6380 (host) → 6379 (container) |
| MailHog SMTP | 1025 |
| MailHog UI | 8025 |

---

## 📚 API Documentation

After starting the application, visit:

- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

---

## 🔑 Default Credentials

| Field | Value |
|-------|-------|
| Username | `superadmin` |
| Email | `superadmin@enterprise-iam.com` |
| Password | `Admin@123` |
| Role | `ROLE_SUPER_ADMIN` |

> ⚠️ **Change the default password immediately in production!**

---

## 🌐 API Endpoints Summary

### Authentication (`/api/v1/auth`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/register` | Register new user |
| GET | `/verify-email?token=` | Verify email address |
| POST | `/login` | Login (returns JWT) |
| POST | `/login/mfa` | Submit TOTP code after login |
| POST | `/refresh-token` | Rotate refresh token |
| POST | `/logout` | Logout current session |
| POST | `/logout-all` | Logout all sessions |
| POST | `/forgot-password` | Request password reset email |
| POST | `/reset-password` | Submit new password with token |

### User Self-Service (`/api/v1/users`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/me` | Get my profile |
| PUT | `/me` | Update my profile |
| PUT | `/me/password` | Change my password |
| POST | `/me/mfa/setup` | Setup TOTP MFA |
| POST | `/me/mfa/verify` | Enable MFA after setup |
| DELETE | `/me/mfa` | Disable MFA |
| GET | `/me/sessions` | List active sessions |
| DELETE | `/me/sessions/{id}` | Revoke a session |

### Admin: Users (`/api/v1/admin/users`) — ADMIN+

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/` | Search & list users |
| GET | `/{id}` | Get user by ID |
| PUT | `/{id}/enable` | Enable user account |
| PUT | `/{id}/disable` | Disable user account |
| PUT | `/{id}/unlock` | Unlock locked account |
| DELETE | `/{id}` | Delete user |
| POST | `/{userId}/roles/{roleId}` | Assign role to user |
| DELETE | `/{userId}/roles/{roleId}` | Remove role from user |

### Admin: Roles & Permissions (`/api/v1/admin/roles`, `/permissions`) — ADMIN+

Full CRUD for roles and permissions, plus permission assignment to roles.

### Admin: Audit Trail (`/api/v1/admin/audit`) — ADMIN/MANAGER+

Paginated audit log queries with filters for `userId`, `action`, `resource`, `from`, `to`.

---

## 🧪 Running Tests

```bash
# Run all tests
mvn test

# Run tests with coverage report (if jacoco configured)
mvn verify

# Run specific test class
mvn test -Dtest=AuthServiceTest
```

---

## 🔧 Configuration Reference

Key configuration properties in `application.yml`:

| Property | Default | Description |
|----------|---------|-------------|
| `app.jwt.access-token-expiration` | 900000 (15 min) | JWT access token TTL in ms |
| `app.jwt.refresh-token-expiration` | 604800000 (7 days) | Refresh token TTL in ms |
| `app.security.max-failed-attempts` | 5 | Login attempts before lockout |
| `app.security.lock-duration-minutes` | 30 | Account lock duration |
| `app.security.email-token-expiry-hours` | 24 | Email verification token TTL |
| `app.security.password-reset-token-expiry-hours` | 1 | Password reset token TTL |

---

## 🔒 Security Considerations (Production)

1. **Change default credentials** — Update `superadmin` password and JWT secret
2. **Set strong JWT secret** — Min 256-bit random string in `app.jwt.secret`
3. **Enable Redis password** — Set `REDIS_PASSWORD` in `.env`
4. **Use HTTPS** — Deploy behind SSL/TLS terminating proxy (nginx, AWS ALB)
5. **Configure CORS** — Set `CORS_ORIGINS` to your actual frontend domain
6. **Set strong DB password** — Change default MySQL credentials

---

## 📦 Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.3.x |
| Security | Spring Security 6, JWT (JJWT 0.12.x) |
| Database | MySQL 8.x + Spring Data JPA + Hibernate |
| Migrations | Flyway |
| Cache/Session | Redis 7 + Spring Data Redis |
| MFA | TOTP (dev.samstevens.totp) + ZXing QR |
| Email | Spring Mail + MailHog (dev) |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Testing | JUnit 5, Mockito, Spring Security Test |
| Build | Maven 3.9+ |
| Container | Docker, Docker Compose |
