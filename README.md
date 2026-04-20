# CodeSync — Eureka Server

## Why you got the Whitelabel 404

The **Whitelabel Error Page** from Spring Boot means a request hit no
registered handler. For Eureka, the two most common causes are:

| Cause | Fix applied in this version |
|---|---|
| Spring Security blocking the `/` dashboard route | `SecurityConfig` now `permitAll()` on `/` and `/eureka/**` |
| CSRF protection rejecting client POST requests to `/eureka/apps/**` | CSRF disabled for `/eureka/**` in `SecurityConfig` |
| Navigating to `/` before Eureka fully initialises | Wait ~15 s after startup |

Once fixed, the Eureka dashboard is at: **http://localhost:8761**

---

## Local development

```bash
mvn spring-boot:run
# Dashboard → http://localhost:8761
# Login: admin / admin123
```

---

## Deploy FREE on Replit (step-by-step)

### 1. Create a new Repl
1. Go to [replit.com](https://replit.com) → **+ Create Repl**
2. Choose **Import from GitHub** (or **Blank Repl → Java**)
3. Upload / paste all files from this folder

### 2. Add the config files
Make sure these files are present in the root of your Repl:
- `.replit` ✅
- `replit.nix` ✅
- `pom.xml` ✅
- `src/` directory tree ✅

### 3. Set Secrets (Environment Variables)
In your Repl → **Tools → Secrets**, add:

| Key | Value |
|---|---|
| `EUREKA_PASSWORD` | a strong password of your choice |
| `SPRING_PROFILES_ACTIVE` | `prod` |

> **Do NOT add EUREKA_HOSTNAME yet** — first run the app to get your Replit URL,
> then add it.

### 4. First run
Click **Run**. Maven will download dependencies (~2 min first time).
You'll see:
```
Started EurekaServerApplication in X.XXX seconds
```

### 5. Get your public URL
Replit shows a webview URL like:
```
https://eureka-server.yourusername.repl.co
```

### 6. Set EUREKA_HOSTNAME secret
Now add:

| Key | Value |
|---|---|
| `EUREKA_HOSTNAME` | `eureka-server.yourusername.repl.co` |

Click **Run** again. The service will restart with the correct hostname.

### 7. Verify
Open the webview URL — you should see the **Eureka dashboard** with the
green header and "Instances currently registered with Eureka" table.

---

## Registering other CodeSync services with this Eureka

In every other microservice's `application.yml`, set:

```yaml
eureka:
  client:
    service-url:
      # Replace with your actual Replit URL and password
      defaultZone: http://admin:YOUR_PASSWORD@eureka-server.yourusername.repl.co/eureka/
```

---

## Keeping the Repl alive (free tier limitation)

Free Replit Repls sleep after ~1 hour of inactivity. Options:

1. **UptimeRobot** (free) — ping `https://your-repl.repl.co/actuator/health`
   every 5 minutes to keep it awake.
2. **Replit Always On** — paid feature ($7/month) that prevents sleeping.
3. **Replit Deployments** — one-click deploy to always-on cloud (free tier
   available with limits).

For UptimeRobot:
- Create a free account at [uptimerobot.com](https://uptimerobot.com)
- New Monitor → HTTP(s) → URL: `https://YOUR-REPL.repl.co/actuator/health`
- Monitoring Interval: 5 minutes
