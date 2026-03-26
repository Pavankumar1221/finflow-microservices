# SonarQube & JaCoCo Integration Guide

This guide provides step-by-step instructions to set up and run SonarQube locally using Docker, and how to execute static code analysis and test coverage reporting across all FinFlow microservices.

## 1. Local SonarQube Setup (Docker)

To run SonarQube locally, ensure Docker is installed and running, then execute the following command:

```bash
docker run -d --name sonarqube -e SONAR_ES_BOOTSTRAP_CHECKS_DISABLE=true -p 9000:9000 sonarqube:lts-community
```

*Note: The Community LTS version is recommended for general local analysis.*

Wait a few minutes for the container to start.

### Accessing the Dashboard
1. Open your browser and navigate to: [http://localhost:9000](http://localhost:9000)
   *(Screenshot Placeholder: SonarQube Login Screen)*
2. Login with the default credentials:
   - **Login**: `admin`
   - **Password**: `admin`
3. You will be immediately prompted to change your password. Set a secure password.

### Generating a Token
To authenticate your Maven scans securely without hardcoding credentials:
1. Click on your profile icon (top right) -> **My Account**.
2. Go to the **Security** tab.
3. Under **Generate Tokens**:
   - Provide a name (e.g., `FinFlow-Local-Scan`).
   - Select **User Token** type.
   - Click **Generate**.
4. **Copy the token immediately!** (You won't be able to see it again).

---

## 2. Environment Configuration

To adhere to security best practices, we avoid hardcoding credentials in our repository. You must expose the SonarQube token as an environment variable before running the analysis.

### Windows (PowerShell)
```powershell
$env:SONAR_TOKEN="your_generated_token_here"
```

### macOS / Linux (Bash/Zsh)
```bash
export SONAR_TOKEN="your_generated_token_here"
```

---

## 3. Microservice Maven Configuration

Each microservice's `pom.xml` has already been updated to include:
- A unique `sonar.projectKey` (e.g., `finflow-auth-service`).
- `sonar.host.url` defaulting to `http://localhost:9000`.
- Token-based login mapped to `${env.SONAR_TOKEN}`.
- JaCoCo plugin for code coverage reporting.

*Note: If your microservices connect to external databases, ensure your Spring boot profiles (`application.yml`) leverage environment variables for database URLs, usernames, and passwords to avoid credential leaks.*

---

## 4. Execution Commands

To execute tests, generate coverage reports, and perform SonarQube analysis, run the following command from the root folder of any microservice:

```bash
# Example for admin-service
cd admin-service
mvn clean verify sonar:sonar
```

*What this does:*
1. `clean`: Removes previous build artifacts.
2. `verify`: Compiles code, runs tests, and generates the JaCoCo coverage report.
3. `sonar:sonar`: Pushes the results (code quality, bugs, vulnerabilities, test coverage) to the local SonarQube server.

### Running for ALL Services (PowerShell script example)
If you want to run analysis for all microservices sequentially from the root directory `d:\FinFlow`:
```powershell
$services = "auth-service", "application-service", "document-service", "admin-service", "api-gateway", "config-server", "eureka-server"

foreach ($service in $services) {
    Write-Host "Scanning $service..."
    Push-Location $service
    mvn clean verify sonar:sonar
    Pop-Location
}
```

---

## 5. Basic Quality Gates

Quality Gates assert whether your code meets baseline standards. To configure a basic Quality Gate in SonarQube:
1. Log in to [http://localhost:9000](http://localhost:9000).
2. Go to **Quality Gates** in the top navigation bar.
3. Click **Create** and name it `FinFlow Quality Gate`.
4. Add the following conditions:
   - **Bugs** is greater than `0`
   - **Vulnerabilities** is greater than `0`
   - **Critical Issues** is greater than `0`
   - **Coverage** is less than `70.0%`
5. Set this gate as the **Default** to apply it to all FinFlow microservices.

*(Screenshot Placeholder: Quality Gate Configuration)*

---

## 6. Expected Output

After successfully running the scan, return to [http://localhost:9000/projects](http://localhost:9000/projects). You should see each microservice listed independently (e.g., `finflow-auth-service`, `finflow-admin-service`).

Clicking on a service will reveal:
- Bugs and Vulnerabilities
- Security Hotspots
- Technical Debt and Code Smells
- Overall Code Coverage %

---

## 7. Troubleshooting & Common Errors

1. **Error: `Not authorized. Please check the properties sonar.login and sonar.password.`**
   - **Fix**: Ensure your `$env:SONAR_TOKEN` is correctly set in the terminal where you are executing the Maven command.

2. **Error: `Coverage information was not collected.`**
   - **Fix**: Verify that tests are executing during the `verify` phase. If you use `-DskipTests` during the build, coverage will report 0%.

3. **Error: `Connection refused: localhost:9000`**
   - **Fix**: Check if your SonarQube Docker container is running (`docker ps`). Start it if it stopped.

4. **Error: memory issues during scan (`java.lang.OutOfMemoryError`)**
   - **Fix**: Increase the Maven heap size using `$env:MAVEN_OPTS="-Xmx2G"`.
