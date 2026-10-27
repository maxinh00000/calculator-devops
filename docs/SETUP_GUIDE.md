# Setup guide and 3-day plan

**Decision: run Docker on the same Ubuntu VM as Jenkins, not on Windows.**
The Jenkins pipeline itself runs `docker build`, `docker push` and `docker compose up`. If Docker is on the
same machine, the `jenkins` user just needs to be in the `docker` group. With Docker Desktop on Windows you
would have to expose the Docker daemon over TCP to the VM, fight VirtualBox/Hyper-V conflicts, and the demo
deployment would run on a different machine from the pipeline. Keep everything on the VM.

Recommended VM size: **4 GB RAM (6 GB if you can), 2 vCPUs, 25 GB disk.** Maven, Docker builds, two JVM
containers and Jenkins all run on it at the same time.

Ports used on the VM: Jenkins `8080`, the deployed app through Nginx `8085`.

---

## Day 1: VM tools, GitHub repo, app and tests

### 1. Install the toolchain on the Ubuntu VM

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk maven git curl

# Docker Engine + Compose plugin (official convenience script)
curl -fsSL https://get.docker.com | sudo sh

# let your user AND the jenkins user talk to Docker
sudo usermod -aG docker $USER
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins
newgrp docker            # or log out and back in

# verify
java -version            # 17.x
mvn -v
docker run --rm hello-world
docker compose version   # v2.x
```

### 2. Create the GitHub repo

On GitHub, create a **public** repository `calculator-devops`. Leave it empty: no README and no .gitignore.

### 3. Commit the project progressively

Copy the project files into a working folder (on the VM, or on Windows with Git for Windows; `.gitattributes`
keeps the line endings Linux-safe). Then commit **in pieces**, not as one "final commit":

```bash
cd calculator-devops
git init -b main
git config user.name  "Taral Sanka"
git config user.email "taral.sanka@gmail.com"
git remote add origin https://github.com/maxinh00000/calculator-devops.git

# commit 1: skeleton
git add pom.xml .gitignore .gitattributes src/main/java/com/spe/calculator/CalculatorApplication.java src/main/resources
git commit -m "Initialize Spring Boot project with Actuator"

# commit 2: business logic + endpoints
git add src/main/java/com/spe/calculator/service src/main/java/com/spe/calculator/controller src/main/java/com/spe/calculator/model
git commit -m "Add calculator service and REST endpoints under /api/v1"

# commit 3: validation / errors
git add src/main/java/com/spe/calculator/exception
git commit -m "Return structured HTTP 400 errors for invalid input and division by zero"

# commit 4: tests
git add src/test
git commit -m "Add JUnit 5 unit tests and MockMvc integration tests"

# commit 5: docs
git add README.md
git commit -m "Add README with build, run and API usage"

git push -u origin main
```

Verify the app and tests **before going further**:

```bash
mvn clean package -DskipTests
mvn test                         # expect: Tests run: 27, Failures: 0, Errors: 0
java -jar target/calculator.jar &
curl "http://localhost:8080/api/v1/add?a=2&b=3"
curl -i "http://localhost:8080/api/v1/divide?a=5&b=0"
curl http://localhost:8080/actuator/health
kill %1
```

If `mvn test` fails, fix it now. Every later stage depends on it.

### 4. Feature branch: Docker (this is your branch + PR for the Git marks)

```bash
git checkout -b feature/containerization
git add Dockerfile .dockerignore
git commit -m "Add multi-stage Dockerfile with non-root JRE runtime"
git add docker-compose.yml nginx/
git commit -m "Add Docker Compose stack: 2 replicas behind Nginx with health checks"
git push -u origin feature/containerization
```

On GitHub: **Compare & pull request → Create pull request → Merge pull request**. Then locally:

```bash
git checkout main && git pull
```

Test it by hand on the VM:

```bash
docker build -t maxinh0/calculator:dev .
docker run --rm -d -p 8081:8080 --name calc-test maxinh0/calculator:dev
sleep 20 && curl "http://localhost:8081/api/v1/multiply?a=6&b=7"
docker rm -f calc-test

IMAGE_TAG=dev docker compose up -d
docker compose ps                     # all three should become (healthy)
curl -i "http://localhost:8085/api/v1/add?a=2&b=3"   # note X-Upstream-Server header
docker compose down
```

---

## Day 2: Jenkins pipeline, Docker Hub, email

### 5. Docker Hub

1. Sign in at hub.docker.com. Your username must be **lowercase** in image names.
2. Account settings → **Personal access tokens** → Generate, with **Read & Write** access. Copy the token.
3. Public repo: the first push creates `maxinh0/calculator` automatically. Make sure it is public.

> The Docker Hub username `maxinh0` is already set in **Jenkinsfile** (`DOCKERHUB_USER`) and as the
> default in **docker-compose.yml**. Changing accounts means updating both.

### 6. Jenkins plugins

Log in to Jenkins (`http://<vm-ip>:8080`) as **`unditrivedh`**. That user needs admin rights to do steps 6 to 10.


Manage Jenkins → Plugins → Available. Make sure these are installed (most come with "suggested plugins"):
**Pipeline, Git, GitHub, Credentials Binding, JUnit, Email Extension, Timestamper**.

### 7. Jenkins credentials (Docker Hub)

Manage Jenkins → Credentials → System → Global credentials → **Add Credentials**
- Kind: *Username with password*
- Username: `maxinh0`
- Password: the access token from step 5
- **ID: `dockerhub-creds`** (must match the Jenkinsfile exactly)

### 8. Email notifications (Gmail)

1. Google Account → Security → turn on **2-Step Verification** → **App passwords** → create one named
   "jenkins" and copy the 16-character password.
2. Manage Jenkins → **Credentials** → add *Username with password*: username = your Gmail address,
   password = the app password, ID `gmail-smtp`.
3. Manage Jenkins → **System**:
   - *Jenkins Location* → System Admin e-mail address: `taral.sanka@gmail.com`
   - *Extended E-mail Notification*: SMTP server `smtp.gmail.com`, port `465`, Advanced → Credentials
     `gmail-smtp`, tick **Use SSL**. Default Content Type: `HTML`.
   - *E-mail Notification* (the plain Mailer section): same server, port `465`, Use SMTP Authentication
     (Gmail + app password), Use SSL. Then use **Test configuration by sending test e-mail** to check it works.
4. Save.

### 9. Commit the pipeline on a second branch

```bash
git checkout -b feature/jenkins-pipeline
git add scripts/
git commit -m "Add idempotent rolling deploy and post-deploy smoke test scripts"
git add Jenkinsfile docs/
git commit -m "Add declarative Jenkins pipeline with Docker Hub push, deploy and email"
git push -u origin feature/jenkins-pipeline
# open a PR on GitHub and merge it, then:
git checkout main && git pull
```

### 10. Create the pipeline job

Jenkins → **New Item** → name `calculator-pipeline` → **Pipeline** → OK
- Pipeline → Definition: **Pipeline script from SCM**
- SCM: Git, Repository URL `https://github.com/maxinh00000/calculator-devops.git`
  (public repo, so no credentials needed)
- Branch: `*/main`, Script Path: `Jenkinsfile`
- Save → **Build Now** once. The `triggers {}` block only registers after this first run.

Expected result: all 6 stages green, a **Test Result** trend on the job page, the image on Docker Hub
with tags `1` and `latest`, an email in your inbox, and `docker ps` showing 3 healthy containers.

### 11. Auto-trigger

- **Poll SCM** (already in the Jenkinsfile, every ~2 min) works with the VM behind NAT. That is enough for full marks.
- **Webhook** (optional, faster): GitHub cannot reach a VM on a private IP, so tunnel it.
  ```bash
  sudo snap install ngrok
  ngrok config add-authtoken <token from ngrok.com>
  ngrok http 8080
  ```
  GitHub repo → Settings → Webhooks → Add: Payload URL `https://<id>.ngrok-free.app/github-webhook/`,
  content type `application/json`, event: *Just the push event*. A green tick under "Recent Deliveries" means it works.
  Also tick **GitHub hook trigger for GITScm polling** in the job config if it is not already ticked.

---

## Day 3: Screenshots, report, demo rehearsal

### Screenshots checklist (put them in `screenshots/` and in the report)

- [ ] Jenkins **Stage View**, all stages green
- [ ] Jenkins **Test Result** page (27 tests passed)
- [ ] Success **email** in Gmail (optionally a failure email too, see below)
- [ ] `docker ps` showing `calculator-1`, `calculator-2`, `nginx` all **(healthy)**
- [ ] Docker Hub repository page with build-number tags
- [ ] API responses: add / subtract / multiply / divide / divide-by-zero 400 / health
      (`curl -i` in a terminal, or a browser)
- [ ] GitHub: commit history + merged pull requests (network graph)

To get a failure email for the report: break a test assertion, push, wait for the red build and the email, then revert and push.

### Live demo script (Class 17)

1. Show Jenkins with the last green build, then `docker ps` and `curl http://localhost:8085/actuator/info`
   (shows `"version":"1.0.0"`).
2. Make a visible change. Bump `<version>1.0.0</version>` to `1.1.0` in `pom.xml`:
   ```bash
   git checkout -b feature/bump-version
   # edit pom.xml
   git commit -am "Bump version to 1.1.0"
   git push -u origin feature/bump-version   # open PR, merge on GitHub
   ```
3. Within ~2 minutes (or instantly with the webhook) Jenkins starts a new build by itself.
4. When it is green: `docker ps` shows the new image tag, and `curl http://localhost:8085/actuator/info` now
   shows `1.1.0`. Show the new tag on Docker Hub and the email.
5. Hit `curl -i http://localhost:8085/api/v1/add?a=1&b=1` a few times. The `X-Upstream-Server` header
   alternates between the two replicas.

### Things you must be able to explain

- **Why multi-stage?** The build stage has Maven and the JDK (~500 MB). The runtime stage is a JRE on Alpine
  with only the JAR, so the image is much smaller and has less attack surface.
- **Layer caching:** `pom.xml` is copied and `dependency:go-offline` runs *before* `src` is copied, so a code-only change reuses the dependency layer.
- **Non-root:** `adduser spring` + `USER spring`. If the app is compromised, the attacker is not root inside the container.
- **Idempotent deploy:** `scripts/deploy.sh` updates replica 1, waits for *healthy*, then replica 2. Nginx keeps
  serving from whichever replica is up. A re-run with no changes is a no-op (`up -d` on an unchanged service does nothing).
  `disableConcurrentBuilds()` stops two deploys from racing.
- **Health checks:** Compose polls `/actuator/health` in each container. Nginx starts only after both replicas
  are `service_healthy`. `restart: unless-stopped` brings crashed containers back.
- **Nginx `resolve`:** when a replica is recreated it gets a new IP, and Nginx re-resolves the name through Docker DNS (127.0.0.11).
- **Credentials:** `withCredentials` injects the Docker Hub token as env vars only inside that block. Jenkins
  masks it in the log, and `--password-stdin` keeps it out of the process list.
- **Test gating:** `mvn test` exits non-zero on failure, so the stage fails and later stages never run. `junit` publishes the XML anyway (in `post { always }`).

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `permission denied ... /var/run/docker.sock` in Jenkins | `sudo usermod -aG docker jenkins && sudo systemctl restart jenkins` |
| `docker: 'compose' is not a docker command` | `sudo apt install docker-compose-plugin` |
| `bash\r: No such file` / `$'\r': command not found` | Windows line endings. `.gitattributes` fixes it for new commits; run `git add --renormalize . && git commit -m "Normalize line endings"` |
| Push fails `denied: requested access to the resource is denied` | `DOCKERHUB_USER` in Jenkinsfile doesn't match your Docker Hub username, or the token is read-only |
| Email not arriving | Use the app password (not your Gmail password), port 465 + SSL; check the spam folder; try the "Test configuration" button |
| `Bind for 0.0.0.0:8085 failed: port is already allocated` | Something else is on 8085. Set `PROXY_PORT` and update `APP_URL` in the Jenkinsfile |
| Containers stuck in `(health: starting)` then unhealthy | VM low on RAM. Give it more memory, or `docker logs calculator-calculator-1-1` |
| `No such DSL method 'githubPush'` | Install the **GitHub** plugin, or delete the `githubPush()` line (Poll SCM still works) |
| `Invalid option type "timestamps"` | Install the **Timestamper** plugin |
| Build is slow the first time | Expected: Maven and Docker download all dependencies once. Later builds use the caches |
