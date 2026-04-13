# DIPROGRA v2.1 — Java + Spring Boot + PostgreSQL

## Requisitos

| Herramienta | Versión mínima | Descarga |
|-------------|----------------|----------|
| Java JDK    | 17+            | https://adoptium.net |
| Maven       | 3.8+           | https://maven.apache.org |
| PostgreSQL  | 14+            | https://www.postgresql.org/download/windows/ |

---

## Paso 1 — Crear la base de datos en PostgreSQL

Abre pgAdmin o psql y ejecuta:

```sql
CREATE DATABASE diprogra
    WITH ENCODING = 'UTF8'
    LC_COLLATE = 'Spanish_Guatemala.1252'
    TEMPLATE = template0;
```

---

## Paso 2 — Configurar la conexión

Edita el archivo `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/diprogra
spring.datasource.username=postgres
spring.datasource.password=TU_CONTRASEÑA
```

O si ya tienes el JAR compilado, edita `INICIAR.bat` y cambia:
```
set DB_PASS=TU_CONTRASEÑA
```

---

## Paso 3 — Compilar

Doble clic en `COMPILAR.bat`

O desde consola:
```bash
mvn clean package -DskipTests
```

Esto genera: `target/diprogra-2.1.0.jar`

---

## Paso 4 — Iniciar

Doble clic en `INICIAR.bat`

El sistema abrirá automáticamente http://localhost:8080

**Credenciales iniciales:**
- Usuario: `admin`
- Contraseña: `admin123`

---

## Subir a servidor en internet (Render / Railway / VPS)

### Opción A — Render.com (gratis)

1. Sube el proyecto a GitHub
2. Crea un nuevo **Web Service** en render.com
3. Selecciona el repositorio
4. Configura:
   - **Build Command:** `mvn clean package -DskipTests`
   - **Start Command:** `java -jar target/diprogra-2.1.0.jar`
5. Agrega estas variables de entorno:
   ```
   SPRING_DATASOURCE_URL=jdbc:postgresql://HOST:5432/diprogra
   SPRING_DATASOURCE_USERNAME=postgres
   SPRING_DATASOURCE_PASSWORD=tu_password
   DIPROGRA_UPLOADS_DIR=/opt/render/project/uploads
   DIPROGRA_BACKUPS_DIR=/opt/render/project/backups
   ```

### Opción B — Railway.app

1. Sube el proyecto a GitHub
2. Conecta el repositorio en railway.app
3. Agrega un servicio PostgreSQL (Railway lo crea automáticamente)
4. Las variables de entorno de DB se inyectan automáticamente

### Opción C — VPS Ubuntu

```bash
# Instalar Java
sudo apt install openjdk-17-jre

# Instalar PostgreSQL
sudo apt install postgresql

# Crear base de datos
sudo -u postgres createdb diprogra

# Copiar JAR y ejecutar
java -jar diprogra-2.1.0.jar \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/diprogra \
  --spring.datasource.username=postgres \
  --spring.datasource.password=tu_password
```

---

## Estructura del proyecto

```
diprogra-java/
├── pom.xml                          ← Dependencias Maven
├── INICIAR.bat                      ← Iniciar en Windows
├── COMPILAR.bat                     ← Compilar el proyecto
├── src/main/
│   ├── java/com/diprogra/
│   │   ├── DiprograApplication.java ← Punto de entrada
│   │   ├── config/
│   │   │   ├── SecurityConfig.java  ← Seguridad
│   │   │   └── AppConfig.java       ← Rutas de archivos
│   │   ├── service/
│   │   │   └── AuthService.java     ← Login, sesiones, bitácora
│   │   └── controller/
│   │       ├── AuthController.java       ← Login/Logout
│   │       ├── ArticulosController.java  ← CRUD artículos
│   │       ├── MovimientosController.java ← Entradas/Salidas
│   │       ├── EntidadesController.java  ← Proveedores/Clientes/Empleados
│   │       ├── DashboardController.java  ← Dashboard, Cotizaciones, Usuarios
│   │       └── ExportController.java     ← Excel y PDF
│   └── resources/
│       ├── application.properties   ← Configuración
│       ├── schema.sql               ← Tablas PostgreSQL
│       └── templates/
│           ├── login.html
│           └── index.html
```

---

## Equivalencias Python → Java

| Python/Flask        | Java/Spring Boot         |
|---------------------|--------------------------|
| `Flask`             | `Spring Boot`            |
| `sqlite3`           | `JdbcTemplate + PostgreSQL` |
| `render_template`   | `Thymeleaf`              |
| `jsonify()`         | `ResponseEntity.ok()`    |
| `request.json`      | `@RequestBody Map<>`     |
| `session[]`         | `HttpSession`            |
| `@app.route`        | `@GetMapping/@PostMapping` |
| `openpyxl`          | `Apache POI`             |
| `reportlab`         | `OpenPDF`                |
| `hashlib.sha256`    | `MessageDigest SHA-256`  |
