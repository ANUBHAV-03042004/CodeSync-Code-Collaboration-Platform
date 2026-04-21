# Deploy API Gateway on Red Hat Developer Sandbox (OpenShift)

## Prerequisites (install once on your Windows machine)

1. **oc CLI** — download from:
   https://console.redhat.com/openshift/downloads
   → Windows → `oc` → Download → extract `oc.exe` → add to PATH

2. **Docker Desktop** — to build the image locally before pushing

---

## Step 1 — Log in to OpenShift from your terminal

1. Go to: https://console.redhat.com/openshift/sandbox
2. Click **Open Console** → top right click your name → **Copy login command**
3. Click **Display Token**, copy the `oc login` command, paste into CMD:

```cmd
oc login --token=sha256~XXXX --server=https://api.sandbox-m2.ll9k3s.p1.openshiftapps.com:6443
```

4. Check your namespace (you'll need this later):
```cmd
oc project
```
It will show something like: `dev-sandbox-yourname-dev`

---

## Step 2 — Set your secret values

Open `secret.yaml` and replace the placeholder values:

```yaml
JWT_SECRET: "your-actual-base64-secret"       # must match auth-service
EUREKA_PASSWORD: "your-eureka-password"        # must match Eureka server
```

Open `configmap.yaml` and update the Eureka host:
```yaml
EUREKA_HOST: "your-eureka-replit-url.repl.co"
```

---

## Step 3 — Apply Secret and ConfigMap

```cmd
oc apply -f secret.yaml
oc apply -f configmap.yaml
```

---

## Step 4 — Build and push the Docker image to OpenShift's registry

OpenShift has a built-in image registry. This is the easiest path — no DockerHub account needed.

### 4a. Log Docker into the OpenShift registry

```cmd
oc whoami -t
```
Copy the token, then:
```cmd
docker login -u unused -p <TOKEN> default-route-openshift-image-registry.apps.sandbox-m2.ll9k3s.p1.openshiftapps.com
```

### 4b. Build the image

From the folder containing your `ApiGatewayService-CodeSync` project AND `Dockerfile`:

```cmd
cd C:\Users\HP\Documents\workspace-spring-tools-for-eclipse-5.0.1.RELEASE
docker build -t api-gateway:latest -f openshift-deploy\Dockerfile ApiGatewayService-CodeSync\
```

### 4c. Tag and push

Replace `YOUR_NAMESPACE` with your actual namespace from Step 1:

```cmd
docker tag api-gateway:latest default-route-openshift-image-registry.apps.sandbox-m2.ll9k3s.p1.openshiftapps.com/YOUR_NAMESPACE/api-gateway:latest

docker push default-route-openshift-image-registry.apps.sandbox-m2.ll9k3s.p1.openshiftapps.com/YOUR_NAMESPACE/api-gateway:latest
```

---

## Step 5 — Update deployment.yaml with your image

Open `deployment.yaml` and replace the image line:

```yaml
# BEFORE:
image: api-gateway:latest

# AFTER (replace YOUR_NAMESPACE):
image: image-registry.openshift-image-registry.svc:5000/YOUR_NAMESPACE/api-gateway:latest
```

---

## Step 6 — Deploy everything

```cmd
oc apply -f deployment.yaml
oc apply -f service-route.yaml
```

---

## Step 7 — Verify deployment

```cmd
# Watch pod come up (Ctrl+C to stop watching)
oc get pods -w

# Once pod is Running, check logs
oc logs deployment/api-gateway -f

# Get your public URL
oc get route api-gateway
```

The URL in the `HOST/PORT` column is your Gateway's public address, e.g.:
```
https://api-gateway-yourname-dev.apps.sandbox-m2.ll9k3s.p1.openshiftapps.com
```

---

## Step 8 — Test it

```cmd
curl https://api-gateway-yourname-dev.apps.sandbox-m2.ll9k3s.p1.openshiftapps.com/actuator/health
```

Expected: `{"status":"UP"}`

---

## Troubleshooting

| Problem | Command | Fix |
|---|---|---|
| Pod stuck in `Pending` | `oc describe pod api-gateway-xxx` | Check resource limits — Sandbox has 1 CPU / 1.5 GB total |
| Pod in `CrashLoopBackOff` | `oc logs api-gateway-xxx` | Check JWT_SECRET and EUREKA_DEFAULT_ZONE env vars |
| `ImagePullBackOff` | `oc describe pod api-gateway-xxx` | Image name in deployment.yaml doesn't match what you pushed |
| Eureka 401 errors in logs | Check EUREKA_PASSWORD in secret.yaml | Must match Eureka server's Spring Security password |
| Route not accessible | `oc get route api-gateway` | If empty HOST, check service name matches route `to.name` |

---

## Update the app (after code changes)

```cmd
docker build -t api-gateway:latest -f openshift-deploy\Dockerfile ApiGatewayService-CodeSync\
docker tag api-gateway:latest default-route-openshift-image-registry.apps.../YOUR_NAMESPACE/api-gateway:latest
docker push default-route-openshift-image-registry.apps.../YOUR_NAMESPACE/api-gateway:latest
oc rollout restart deployment/api-gateway
```

---

## Developer Sandbox limits to be aware of

- **Free, no credit card** — runs for 30 days, then auto-expired (can re-activate)
- **Quota**: 1 CPU + 1.5 GB RAM total per namespace
- **No sleeping** — unlike Replit free tier, pods stay up 24/7 during the 30 days
- **Public HTTPS** — automatic TLS certificates, no config needed
