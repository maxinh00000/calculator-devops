# Calculator DevOps — CSE 816 SPE Mini-Project

A Calculator REST API in **Java 17 / Spring Boot 3** with a fully automated CI/CD pipeline:
**GitHub → Jenkins → Maven (build + JUnit) → Docker → Docker Hub → Docker Compose (2 replicas + Nginx)**.

- Docker Hub image: `https://hub.docker.com/r/maxinh0/calculator`
- GitHub: `https://github.com/maxinh00000/calculator-devops`
- Author: Taral Sanka (IMT2023588)

## Architecture

```
 git push ──► GitHub ──(webhook / Poll SCM)──► Jenkins (Ubuntu VM)
                                                 │
     Checkout → Build (mvn package) → Test (mvn test + JUnit report)
             → Docker Build (tag = build no.) → Push (Docker Hub) → Deploy (compose) → Smoke test
                                                 │                                    │
                                              email ◄──── success / failure ──────────┘

 Deployed stack (docker compose, network calc-net):
   client ──► nginx :8085 ──round-robin──► calculator-1 :8080
                                       └─► calculator-2 :8080
```

## API

| Operation | Endpoint | Example response |
|-----------|----------|------------------|
| Add | `GET /api/v1/add?a=2&b=3` | `{"operation":"add","a":2.0,"b":3.0,"result":5.0}` |
| Subtract | `GET /api/v1/subtract?a=10&b=4` | `{"operation":"subtract","a":10.0,"b":4.0,"result":6.0}` |
| Multiply | `GET /api/v1/multiply?a=6&b=7` | `{"operation":"multiply","a":6.0,"b":7.0,"result":42.0}` |
| Divide | `GET /api/v1/divide?a=7&b=2` | `{"operation":"divide","a":7.0,"b":2.0,"result":3.5}` |
| Health | `GET /actuator/health` | `{"status":"UP"}` |

Errors (division by zero, missing or non-numeric parameter, NaN/Infinity) return **HTTP 400**:

```json
{"timestamp":"2026-10-27T10:00:00Z","status":400,"error":"Bad Request",
 "message":"Division by zero is not allowed (b must be non-zero)","path":"/api/v1/divide"}
```

## Build, test, run

Prerequisites: JDK 17+, Maven 3.9+, Docker with the Compose plugin.

```bash
mvn clean package -DskipTests   # build target/calculator.jar
mvn test                        # JUnit 5 + MockMvc tests -> target/surefire-reports/*.xml
                                # coverage report -> target/site/jacoco/index.html
java -jar target/calculator.jar # run locally on :8080
```

### Docker (single container)

```bash
docker build -t maxinh0/calculator:dev .
docker run --rm -p 8080:8080 maxinh0/calculator:dev
curl "http://localhost:8080/api/v1/add?a=2&b=3"
```

### Docker Compose (full stack)

```bash
docker compose up -d                       # 2 API replicas + nginx on :8085
curl -i "http://localhost:8085/api/v1/divide?a=5&b=0"
./scripts/smoke-test.sh http://localhost:8085
docker compose down
```

Variables: `DOCKERHUB_USER` (default `maxinh0`), `IMAGE_TAG` (default `latest`), `PROXY_PORT` (default `8085`).
The `X-Upstream-Server` response header shows which replica answered.

## Repository layout

```
├── src/main/java/com/spe/calculator   # controller, service, exception handler, models
├── src/test/java/com/spe/calculator   # JUnit 5 unit tests + MockMvc integration tests
├── pom.xml                            # Maven build (Spring Boot, Actuator, JaCoCo)
├── Dockerfile / .dockerignore         # multi-stage build, non-root JRE runtime
├── docker-compose.yml                 # 2 replicas + nginx, healthchecks, network, restart policy
├── nginx/nginx.conf                   # reverse proxy / load balancer
├── scripts/deploy.sh                  # idempotent rolling deploy used by Jenkins
├── scripts/smoke-test.sh              # post-deploy health + API verification
├── Jenkinsfile                        # declarative CI/CD pipeline
└── docs/SETUP_GUIDE.md                # Jenkins / Docker / email setup on the Ubuntu VM
```

## CI/CD pipeline (Jenkinsfile)

| Stage | What it does |
|-------|--------------|
| Checkout | `checkout scm` from GitHub (triggered by webhook or Poll SCM every 2 minutes) |
| Build | `mvn -B clean package -DskipTests` |
| Test | `mvn -B test`; JUnit XML published, the build fails on any test failure |
| Docker Build | `docker build` tagged `:<BUILD_NUMBER>` and `:latest` |
| Push | `docker login` with Jenkins credential `dockerhub-creds`, then push both tags |
| Deploy | `scripts/deploy.sh` (rolling `docker compose up -d`), then `scripts/smoke-test.sh` |
| Post | Email on success or failure; prune dangling images |

Secrets are never stored in the repository. Docker Hub and SMTP credentials live only in the Jenkins credentials store.
