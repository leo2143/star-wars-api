# Star Wars API (backend)

Backend del challenge técnico "Back-end JAVA Challenge": una aplicación Java 21 que se integra con
[swapi.tech](https://www.swapi.tech/documentation), lista **People, Films, Starships y Vehicles** de forma
paginada, permite filtrar por **ID y/o nombre**, expone el **detalle** de cada elemento con sus relaciones, protege
los endpoints con **JWT** y está documentada y probada.

> Se consume **swapi.tech**.

## Demo

La API y un frontend que la consume están desplegados, así que se pueden probar sin instalar nada:

| Recurso | URL |
|---|---|
| Swagger UI (API) | https://star-wars-api-sgro.onrender.com/swagger-ui/index.html |
| Aplicación web | https://star-wars-web.onrender.com |
| Repositorio del frontend (Angular) | https://github.com/leo2143/star-wars-web |

- **Cuenta:** el registro es público. Se puede crear una cuenta desde la pantalla de registro de la aplicación web o
  con `POST /api/v1/auth/register` en Swagger, y después iniciar sesión.
- **Probar en Swagger:** ejecutar `POST /api/v1/auth/login`, copiar el `accessToken` de la respuesta, pegarlo en
  **Authorize** y llamar a cualquier endpoint de listado o detalle.
- **Primer acceso lento:** los servicios usan el plan gratuito, que se suspende tras unos minutos sin tráfico. El
  primer request puede tardar alrededor de un minuto mientras el servicio arranca; los siguientes responden normal.

## Tabla de contenido

1. [Stack](#stack)
2. [Arquitectura](#arquitectura)
3. [Requisitos previos](#requisitos-previos)
4. [Ejecución local](#ejecución-local)
5. [Variables de entorno](#variables-de-entorno)
6. [Base de datos](#base-de-datos)
7. [Usuario de demostración y clave JWT](#usuario-de-demostración-y-clave-jwt)
8. [Endpoints](#endpoints)
9. [Contrato de paginación y filtros](#contrato-de-paginación-y-filtros)
10. [Errores](#errores)
11. [Seguridad](#seguridad)
12. [Swagger UI](#swagger-ui)
13. [Tests](#tests)
14. [Construir el JAR](#construir-el-jar)
15. [Docker](#docker)
16. [Decisiones de diseño](#decisiones-de-diseño)
17. [Limitaciones conocidas y evolutivos](#limitaciones-conocidas-y-evolutivos)

## Stack

| Componente | Versión |
|---|---|
| Java (`release`) | 21 |
| Spring Boot | 4.1.2 (Spring Framework 7.0, Spring Security 7.1) |
| Jackson | 3.1 (`tools.jackson.*`) |
| Springdoc OpenAPI | 3.1.1 |
| Caché | Caffeine (versión administrada por Spring Boot) |
| Base de datos | PostgreSQL 17, Spring Data JPA (Hibernate), Flyway 12 |
| Maven (wrapper) | 3.10.0 |
| Tests | JUnit 5, Mockito, AssertJ, MockMvc, `MockRestServiceServer`, Testcontainers (PostgreSQL) |

Lombok (1.18.48, administrado por Spring Boot) se usa en las entidades JPA `User` y `RefreshToken` (`@Getter`, `@Setter` en los campos mutables y `@NoArgsConstructor` protegido) y, en el resto de los componentes, `@RequiredArgsConstructor` genera el constructor de la inyección (conservando el acceso de paquete donde corresponde); los DTO son `record`. El procesador se declara en `annotationProcessorPaths` de `maven-compiler-plugin`, porque desde JDK 23 `javac` no ejecuta los procesadores del classpath; `lombok.config` marca el código generado para las herramientas de cobertura y Spring Boot excluye Lombok del JAR final.

## Arquitectura

Organización por capas (paquetes en plural). Los datos de Star Wars vienen de SWAPI; la base de datos guarda solo las
cuentas de usuario. El algoritmo de consulta se escribe una sola vez en un núcleo compartido; cada recurso aporta
únicamente lo que lo diferencia.

```text
com.challenge.starwars
├── controllers/  Capa web: un controlador por recurso (FilmsController, PeopleController, StarshipsController,
│   │             VehiclesController) y AuthController (login, refresh, logout y registro)
│   └── support/   La meta-anotación @ListingApi y ListingDocs (texto OpenAPI común)
├── services/     Lógica de aplicación: un servicio por recurso (FilmsService, PeopleService, ...), AuthService, RegistrationService,
│                 SwapiQueryEngine (filtros, orden y paginación de los listados, escritos una sola vez) y el
│                 catálogo en caché (SwapiCatalogLoader + SwapiCatalog) del que salen listados y detalles
├── mappers/      Un mapper por recurso (FilmsMapper, PeopleMapper, ...; `map`, `toMin` y `toDetail`), el contrato
│                 SwapiItemMapper<T> y la utilidad SwapiFields (IDs, URLs, relaciones, números)
├── clients/      Integración con swapi.tech: SwapiClient (única clase que habla con SWAPI), SwapiClientConfig (RestClient),
│                 SwapiRequestLoggingInterceptor (log DEBUG por intento), SwapiResource
│                 (descriptor: ruta, parámetro de búsqueda, si SWAPI pagina) y SortField (campo ordenable)
├── dtos/
│   ├── films/ people/ starships/ vehicles/   DTO públicos de cada recurso: de listado (FilmDto...), mínimo
│   │             (FilmMinDto...) y de detalle (FilmDetailDto...)
│   ├── common/   ListQuery (page, id, name, sort) y PageResponse<T>, el contrato público de los listados
│   ├── auth/     LoginRequest, RefreshTokenRequest, RegisterRequest, RegisterResponse y TokenResponse
│   └── swapi/    Modelos de lo que devuelve swapi.tech (SwapiItem y sobres de respuesta)
├── entities/     Entidades JPA (User, RefreshToken)
├── repositories/ Repositorios Spring Data (UserRepository, RefreshTokenRepository)
├── security/     SecurityFilterChain, CORS, errores 401/403,
│   │             DatabaseUserDetailsService (usuarios desde la base), AuthConfig (BCrypt, AuthenticationManager) y
│   │             AuthProperties (usuario de demostración)
│   ├── jwt/       JWT (codificador/decodificador/validadores, JwtProperties)
│   └── ratelimit/ RateLimitFilter y RateLimitProperties (límite por IP en login, refresh y registro)
├── config/       Infraestructura de la aplicación: ClockConfig (Clock), propiedades de SWAPI,
│                 DemoUserInitializer y documento OpenAPI
└── exceptions/   Excepciones de integración con SWAPI y de dominio (ResourceNotFoundException, ...)
    └── handler/   GlobalExceptionHandler (@RestControllerAdvice) y ProblemDetails (construcción de ProblemDetail)
```

Cómo se evita duplicar código entre recursos:

- `SwapiQueryEngine.listPage(SwapiResource, ListQuery, SwapiItemMapper<T>)` resuelve cualquier listado desde el
  catálogo en caché del recurso (filtra por `id` y `name`, ordena y pagina) y devuelve `PageResponse<T>`.
- Cada recurso declara su propio `SwapiResource` (constante `RESOURCE` en su servicio), que incluye los campos por los
  que se puede ordenar (`sortFields`), por lo que no existe una lista central que haya que editar.
- Cada `XxxMapper` implementa `SwapiItemMapper<XxxDto>` y reutiliza `SwapiFields`; es lo único que difiere de verdad.
  Los mappers son puros y **nunca dependen de otros mappers** (`FilmsMapper` y `PeopleMapper` se referencian entre
  sí, así que serían circulares): `toDetail` solo ensambla listas de DTO mínimos que ya vienen resueltas.
- El **servicio** orquesta el detalle: obtiene el elemento del catálogo, resuelve los IDs de sus relaciones contra los
  catálogos de los otros recursos, los mapea con el `toMin` del mapper dueño de cada uno y llama a `toDetail`.
- `@ListingApi("/api/{version}/xxx")` agrupa `@RestController`, la ruta y la versión `v1`; cada controlador declara su propia etiqueta OpenAPI (`People`, `Films`, `Starships`, `Vehicles`); además, `OpenApiConfig` usa
  esa anotación para agregar a cada listado las respuestas de error comunes (400, 401, 502, 503, 504). Cada
  controlador conserva su endpoint explícito, con su propio DTO y su descripción en Swagger.
- Todas las rutas fuera de la superficie pública requieren JWT (`anyRequest().authenticated()`).

Un recurso nuevo se agrega con clases propias en cada capa (DTO, mapper, servicio con su `RESOURCE` y controlador
con `@ListingApi`), sin editar las existentes; su catálogo se carga y se cachea sin registrarlo en ningún lado.

## Requisitos previos

- JDK 21 o superior (el bytecode se genera con `--release 21`).
- Acceso a Internet para descargar dependencias y para consultar swapi.tech al ejecutar la aplicación.
- Docker con Docker Compose: levanta la aplicación junto con PostgreSQL y es necesario para ejecutar los tests
  (Testcontainers).

## Ejecución local

La aplicación **no arranca** sin las variables obligatorias (ver más abajo). La forma principal de ejecutarla es con
Docker Compose, que levanta PostgreSQL y la API:

1. Crear un archivo `.env` en la raíz con la plantilla de la sección [Variables de entorno](#variables-de-entorno).
2. Ejecutar:

```bash
docker compose up --build
```

La API queda en `http://localhost:8080`. Para detener el entorno: `docker compose down` (el volumen `db-data`
conserva los datos; `docker compose down -v` los elimina).

`./mvnw spring-boot:run` requiere una instancia de PostgreSQL accesible en `DB_HOST:DB_PORT` (por defecto
`localhost:5432`, base `starwars`, usuario `starwars`). El servicio `db` de Compose no publica puertos hacia el host;
para usarlo desde la máquina local hay que agregarle `ports: ["5432:5432"]`. Con la base disponible:

```bash
export APP_AUTH_USERNAME=demo
export APP_AUTH_PASSWORD='elegi-una-clave-de-demo'
export JWT_SECRET_BASE64="$(openssl rand -base64 48)"
export DB_PASSWORD='elegi-una-clave-de-base'

./mvnw spring-boot:run
```

En Windows (`cmd`) usar `mvnw.cmd` y `set VARIABLE=valor`; `openssl` no viene instalado en `cmd` ni en PowerShell, ver
[cómo generar la clave JWT](#usuario-de-demostración-y-clave-jwt) sin él. Con `spring-boot:run` la API queda en
`http://localhost:8083`; con Docker Compose, en `http://localhost:8080`. Los ejemplos de este documento usan el puerto
de Docker.

Prueba rápida:

```bash
# 1) Obtener un token
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"elegi-una-clave-de-demo"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

# 2) Consultar un endpoint protegido
curl -s -H "Authorization: Bearer $TOKEN" 'http://localhost:8080/api/v1/people?page=1'
```

## Variables de entorno

| Variable | Obligatoria | Valor por defecto | Descripción |
|---|---|---|---|
| `APP_AUTH_USERNAME` | Sí | – | Usuario de demostración. |
| `APP_AUTH_PASSWORD` | Sí | – | Contraseña de demostración (se guarda hasheada con BCrypt en la base). |
| `DB_PASSWORD` | Sí | – | Contraseña de PostgreSQL. |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | No | `localhost` / `5432` / `starwars` | Ubicación de la base; forman la URL JDBC. |
| `DB_USER` | No | `starwars` | Usuario de PostgreSQL. |
| `JWT_SECRET_BASE64` | Sí | – | Clave HMAC en Base64; debe decodificar a **32 bytes o más**. |
| `JWT_ISSUER` | No | `star-wars-api` | Claim `iss`; se valida en cada token. |
| `JWT_AUDIENCE` | No | `star-wars-client` | Claim `aud`; se valida en cada token. |
| `JWT_TTL_SECONDS` | No | `900` | Duración del access token en segundos (60 a 86400). |
| `JWT_REFRESH_TTL` | No | `7d` | Duración de la sesión desde el login (formato `Duration` de Spring: `12h`, `7d`...); debe ser mayor que cero. |
| `SWAPI_BASE_URL` | No | `https://www.swapi.tech/api` | URL base de SWAPI. |
| `SWAPI_CONNECT_TIMEOUT` / `SWAPI_READ_TIMEOUT` | No | `3s` / `8s` | Timeouts de la llamada a SWAPI. |
| `CORS_ALLOWED_ORIGINS` | No | `http://localhost:4200` | Orígenes permitidos, separados por coma. No admite `*`. |
| `SWAPI_CATALOG_TTL` | No | `1h` | Intervalo tras el cual un catálogo de SWAPI se recarga en segundo plano; mientras tanto (o si la recarga falla, que se reintenta pasado otro intervalo) se sigue sirviendo el catálogo anterior (formato `Duration` de Spring: `30m`, `1h`...). |
| `SWAPI_RETRY_MAX_RETRIES` / `SWAPI_RETRY_DELAY` | No | `2` / `200ms` | Reintentos tras el primer intento (0 a 5; `0` los desactiva) y espera inicial, que se duplica en cada reintento. |
| `SWAPI_CB_*` | No | ver [Circuit breaker](#circuit-breaker-frente-a-swapi) | Umbral de fallos, ventana, llamadas mínimas, espera en estado abierto y llamadas de prueba del circuit breaker frente a SWAPI. |
| `APP_LOG_LEVEL` | No | `INFO` | Nivel de log de toda la aplicación (`com.challenge.starwars`). Con `DEBUG` se registran listados, detalles y cargas de catálogo. |
| `SWAPI_LOG_LEVEL` | No | `INFO` | Nivel de log de las llamadas salientes a SWAPI (`com.challenge.starwars.clients`). Con `DEBUG` se escribe una línea por intento (método, URI, estado, duración), por lo que los reintentos son visibles. Nunca se registran cabeceras ni cuerpos. |
| `PORT` | No | `8083` (`8080` en la imagen Docker) | Puerto HTTP. |

Los secretos obligatorios no tienen valor por defecto a propósito: si faltan o la clave es corta, el arranque falla
con un mensaje claro que **nunca incluye el valor**.

**Trazas de las llamadas a servicios.** Para ver en producción qué ocurre en las llamadas a SWAPI, definir
`SWAPI_LOG_LEVEL=DEBUG` (solo el cliente) o `APP_LOG_LEVEL=DEBUG` (toda la aplicación) y reiniciar el servicio; para
apagarlas, volver a `INFO` o quitar la variable. No hay cambio de nivel en caliente: el endpoint `/actuator/loggers` no
se expone porque cualquiera puede registrarse y obtener un token, y la API no tiene un rol de administrador.

Plantilla para un archivo `.env` local (ya está en `.gitignore`). Docker Compose lo lee para la API y para
interpolar las variables del servicio `db`; Spring Boot no lee este archivo por sí solo:

```dotenv
APP_AUTH_USERNAME=<usuario-de-demo>
APP_AUTH_PASSWORD=<clave-de-demo>
JWT_SECRET_BASE64=<clave-base64-de-48-bytes>
DB_PASSWORD=<clave-de-postgres>
# Opcionales: DB_NAME, DB_USER (Compose los usa también para crear la base), JWT_ISSUER, JWT_AUDIENCE,
# JWT_TTL_SECONDS, JWT_REFRESH_TTL, SWAPI_BASE_URL, SWAPI_CONNECT_TIMEOUT, SWAPI_READ_TIMEOUT, SWAPI_CATALOG_TTL,
# SWAPI_RETRY_MAX_RETRIES, SWAPI_RETRY_DELAY, SWAPI_CB_* (circuit breaker), CORS_ALLOWED_ORIGINS, APP_LOG_LEVEL, SWAPI_LOG_LEVEL
# No definir PORT con Compose: el mapeo de puertos asume 8080 dentro del contenedor.
```

## Base de datos

PostgreSQL guarda las cuentas de usuario. El esquema lo crea y versiona Flyway (`src/main/resources/db/migration`) y
Hibernate solo lo valida (`spring.jpa.hibernate.ddl-auto=validate`); `spring.jpa.open-in-view` está desactivado.

| Tabla | Contenido |
|---|---|
| `users` | `id`, `username` (único, hasta 50 caracteres, en minúsculas), `password_hash` (BCrypt), `enabled`, `created_at`. |
| `refresh_tokens` | `id`, `user_id` (clave foránea a `users`, `ON DELETE CASCADE`), `family_id`, `token_hash` (único), `created_at`, `expires_at`, `used_at`, `revoked_at`; índice por `family_id`. Guarda las sesiones de refresh (solo el hash del token); ver [Sesiones y refresh tokens](#sesiones-y-refresh-tokens). |

- La conexión se arma con `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` y `DB_PASSWORD`
  (`jdbc:postgresql://<host>:<puerto>/<base>`). `DB_PASSWORD` no tiene valor por defecto: sin ella PostgreSQL rechaza la conexión y el arranque
  falla (Docker Compose lo exige antes de crear los contenedores).
  La contraseña no se escribe en los logs.
- Las migraciones no contienen usuarios, contraseñas ni hashes.
- Al arrancar, `DemoUserInitializer` crea el usuario de demostración (`APP_AUTH_USERNAME` en minúsculas) si no existe y,
  si existe pero `APP_AUTH_PASSWORD` ya no coincide con su hash, actualiza el hash. Es idempotente.
- Un usuario con `enabled = false` no puede iniciar sesión.

## Usuario de demostración y clave JWT

- **Usuario de demostración:** se define con `APP_AUTH_USERNAME` / `APP_AUTH_PASSWORD` y se crea o actualiza en la base
  al arrancar (ver [Base de datos](#base-de-datos)). Cualquier otra cuenta se crea con `POST /api/v1/auth/register`. La
  verificación de credenciales la hace Spring Security (`DaoAuthenticationProvider`) contra el hash BCrypt guardado.
  El nombre de usuario no distingue mayúsculas: el login lo normaliza (sin espacios en los extremos y en minúsculas).
- **Clave JWT:** generar una clave aleatoria de 48 bytes en Base64, según la terminal:

  ```bash
  # Linux, macOS o Git Bash
  openssl rand -base64 48
  ```

  ```powershell
  # PowerShell 7 o superior (no requiere openssl)
  [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))

  # Windows PowerShell 5.1
  $b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
  ```

  Debe ser la misma entre reinicios y despliegues mientras los tokens emitidos deban seguir siendo válidos
  (no se genera una clave aleatoria al arrancar). En local se exporta como variable de entorno; en el entorno de despliegue se carga
  como variable secreta en el panel. No se versiona ningún valor real.

## Endpoints

### Versionado

La API usa el versionado nativo de Spring Framework 7 por segmento de ruta: la versión es el segundo segmento
(`/api/v1/...`). Se configura con `spring.mvc.apiversion.use.path-segment=1` y `spring.mvc.apiversion.required=true`
en `application.properties`; cada controlador declara `/api/{version}/...` con `version = "v1"`.

- Una ruta sin versión (`/api/people`) no existe: responde `404` (con token válido) o `401` (sin token).
- Una versión no soportada (`/api/v2/people`) responde `400`.
- Para publicar una nueva versión basta con mapear los handlers con `version = "v2"`; `v1` sigue disponible.
- `/actuator`, `/v3/api-docs` y Swagger UI no están versionados.

### Rutas

| Método | Ruta | Autenticación | Descripción |
|---|---|---|---|
| POST | `/api/v1/auth/register` | No | Crea una cuenta (no inicia sesión). |
| POST | `/api/v1/auth/login` | No | Devuelve un JWT (access token) y un refresh token. |
| POST | `/api/v1/auth/refresh` | No (el refresh token es la credencial) | Canjea un refresh token por un par nuevo. |
| POST | `/api/v1/auth/logout` | No (el refresh token es la credencial) | Termina la sesión del refresh token; responde `204`. |
| GET | `/api/v1/people` | Bearer JWT | Personajes. |
| GET | `/api/v1/people/{id}` | Bearer JWT | Detalle de un personaje con sus películas, naves y vehículos. |
| GET | `/api/v1/films` | Bearer JWT | Películas (el filtro `name` busca por título). |
| GET | `/api/v1/films/{id}` | Bearer JWT | Detalle de una película con sus personajes, naves y vehículos. |
| GET | `/api/v1/starships` | Bearer JWT | Naves. |
| GET | `/api/v1/starships/{id}` | Bearer JWT | Detalle de una nave con sus películas. |
| GET | `/api/v1/vehicles` | Bearer JWT | Vehículos. |
| GET | `/api/v1/vehicles/{id}` | Bearer JWT | Detalle de un vehículo con sus películas. |
| GET | `/actuator/health` | No | Estado de la aplicación (sin detalles). |
| GET | `/v3/api-docs`, `/swagger-ui.html` | No | Documentación OpenAPI. |

### Registro

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"luke","password":"una-clave-larga-de-ejemplo"}'
```

```json
{ "id": 2, "username": "luke" }
```

Responde `201`. El registro no inicia sesión: después se llama a `/api/v1/auth/login`.

| Campo | Reglas |
|---|---|
| `username` | 3 a 50 caracteres: letras, dígitos, `.`, `_` y `-`. Se guarda en minúsculas, por lo que `Luke` y `luke` son la misma cuenta. |
| `password` | 15 a 64 caracteres y como máximo 72 bytes en UTF-8. Sin reglas de composición. No puede ser igual al usuario (sin distinguir mayúsculas). |

- El mínimo de 15 caracteres sigue NIST SP 800-63B rev. 4 para contraseñas usadas como único factor, sin exigir mayúsculas,
  símbolos ni rotación.
- BCrypt solo procesa los primeros 72 bytes de la entrada, por eso el límite se aplica en bytes: una contraseña de 64
  caracteres con acentos puede superarlo y se rechaza con `400`.
- Un usuario existente devuelve `409` con el mensaje fijo `Username is not available.` La base de datos decide: la
  restricción `unique` de `users.username` cubre también dos registros simultáneos del mismo usuario. Que el `409`
  revele si un nombre existe es una enumeración posible, acotada por el límite de 5 registros por hora y por IP.
- Los errores de validación no incluyen la contraseña, y ni la contraseña ni su hash se escriben en logs.

### Login

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"elegi-una-clave-de-demo"}'
```

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "refreshToken": "<token-opaco>",
  "refreshExpiresIn": 604800
}
```

`expiresIn` son los segundos de vida del access token y `refreshExpiresIn` los que le quedan a la sesión.

Credenciales inválidas devuelven `401` con un mensaje genérico idéntico para "usuario inexistente" y "clave incorrecta".

### Refresh y logout

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/refresh \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<token-opaco>"}'
```

Responde `200` con el mismo cuerpo que el login. El refresh token usado deja de servir y el de la respuesta lo
reemplaza. Cualquier problema con el token (desconocido, vencido, revocado, ya usado o de un usuario deshabilitado)
devuelve `401` con el mismo mensaje genérico (`Invalid refresh token.`).

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/logout \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<token-opaco>"}'
```

Responde siempre `204`, exista o no el token. Un cuerpo sin `refreshToken` (o con uno vacío o de más de 200
caracteres) devuelve `400` en ambos endpoints.

### Listados

Parámetros (todos opcionales):

| Parámetro | Tipo | Descripción |
|---|---|---|
| `page` | entero >= 1 (por defecto 1) | Página. El tamaño de página es fijo (10) y no se expone. |
| `id` | entero positivo | Identificador en SWAPI. Devuelve 0 o 1 resultado. |
| `name` | texto, máx. 100 | Filtro "contiene", sin distinguir mayúsculas. En films se aplica al título. Se recorta; vacío = ausente. |
| `sort` | `campo` o `-campo`, máx. 50 (`^-?[a-zA-Z]+$`) | Orden ascendente (`campo`) o descendente (`-campo`) por **un** campo. Sin `sort` se conserva el orden de SWAPI. Ver [Ordenamiento](#ordenamiento). |

```bash
curl -s -H "Authorization: Bearer $TOKEN" 'http://localhost:8080/api/v1/people?page=2'
curl -s -H "Authorization: Bearer $TOKEN" 'http://localhost:8080/api/v1/people?page=1&name=Luke'
curl -s -H "Authorization: Bearer $TOKEN" 'http://localhost:8080/api/v1/people?id=1'
curl -s -H "Authorization: Bearer $TOKEN" 'http://localhost:8080/api/v1/people?id=1&name=Luke'
curl -s -H "Authorization: Bearer $TOKEN" 'http://localhost:8080/api/v1/films?name=hope'
curl -s -H "Authorization: Bearer $TOKEN" 'http://localhost:8080/api/v1/starships?name=Falcon'
curl -s -H "Authorization: Bearer $TOKEN" 'http://localhost:8080/api/v1/vehicles?page=1&name=speeder'
curl -s -H "Authorization: Bearer $TOKEN" 'http://localhost:8080/api/v1/people?sort=-height'
```

Respuesta (`PageResponse<T>`, igual para los cuatro recursos):

```json
{
  "count": 82,
  "currentPage": 2,
  "totalPages": 9,
  "nextPage": 3,
  "previousPage": 1,
  "results": [
    {
      "id": "11", "name": "Anakin Skywalker", "height": "188", "mass": "84",
      "hairColor": "blond", "skinColor": "fair", "eyeColor": "blue", "birthYear": "41.9BBY", "gender": "male"
    }
  ]
}
```

`nextPage` y `previousPage` son **números de página** (o `null`), nunca URLs de SWAPI. Los listados **no incluyen
relaciones** (ni arreglos de IDs ni objetos): para eso está el detalle. Los planetas y los pilotos no se representan en
ningún endpoint (decisión de alcance); tampoco se expone la referencia al planeta natal, porque no hay un recurso de
planetas con el que resolverla.

### Detalle

`GET /api/v1/{people|films|starships|vehicles}/{id}` devuelve un único objeto (sin envoltorio `PageResponse`) con todos
los campos del listado más las relaciones, resueltas como DTO **mínimos**:

| Detalle | Relaciones | DTO mínimo |
|---|---|---|
| `PersonDetailDto` | `films`, `starships`, `vehicles` | `FilmMinDto` (id, title, episodeId, releaseDate), `StarshipMinDto` (id, name, model, starshipClass), `VehicleMinDto` (id, name, model) |
| `FilmDetailDto` | `characters`, `starships`, `vehicles` | `PersonMinDto` (id, name, gender), `StarshipMinDto`, `VehicleMinDto` |
| `StarshipDetailDto` | `films` | `FilmMinDto` |
| `VehicleDetailDto` | `films` | `FilmMinDto` |

```bash
curl -s -H "Authorization: Bearer $TOKEN" 'http://localhost:8080/api/v1/people/1'
```

```json
{
  "id": "1", "name": "Luke Skywalker", "height": "172", "mass": "77",
  "hairColor": "blond", "skinColor": "fair", "eyeColor": "blue", "birthYear": "19BBY", "gender": "male",
  "films": [
    { "id": "1", "title": "A New Hope", "episodeId": 4, "releaseDate": "1977-05-25" },
    { "id": "2", "title": "The Empire Strikes Back", "episodeId": 5, "releaseDate": "1980-05-17" }
  ],
  "starships": [
    { "id": "12", "name": "X-wing", "model": "T-65 X-wing", "starshipClass": "Starfighter" }
  ],
  "vehicles": [
    { "id": "14", "name": "Snowspeeder", "model": "t-47 airspeeder" }
  ]
}
```

(el ejemplo está abreviado: la respuesta real incluye todas las relaciones del personaje.) Una película devuelve, por
ejemplo, `"characters": [{ "id": "1", "name": "Luke Skywalker", "gender": "male" }, ...]`.

Reglas del detalle:

- `{id}` debe ser un entero positivo; `abc`, `0`, `-1` o un número fuera de rango devuelven `400`. Un `id` inexistente
  devuelve `404` (a diferencia del filtro `?id=` de los listados, que responde `200` con `results: []`).
- Las relaciones siguen el orden de SWAPI. Un ID relacionado que no esté en el catálogo se omite.
- Un detalle en frío cuesta **como máximo 4 llamadas** a SWAPI (el catálogo del propio recurso y los de sus
  relaciones); uno en caliente cuesta **0**. Ver [Caché de catálogos](#caché-de-catálogos).

#### Caché de catálogos

SWAPI limita a 100 solicitudes por ventana. Resolver las relaciones con una llamada por elemento no es viable (la
película 1 sola tiene 18 personajes, 8 naves y 4 vehículos). Por eso listados y detalles se arman contra **catálogos**: la
colección completa de un recurso, indexada por ID, que cabe en una sola llamada `?page=1&limit=100&expanded=true`
(people 82, starships 36, vehicles 39; films, en su llamada única `/films`). Si una colección superara los 100
elementos, `SwapiCatalogLoader` sigue paginando con `total_pages`.

- `SwapiCatalogLoader.load(SwapiResource)` carga y cachea el catálogo en una `LoadingCache` de Caffeine propia (varias
  solicitudes simultáneas en frío comparten una sola carga).
- `SwapiCatalog.lookup(resource, id)` y `SwapiCatalog.resolveRelated(resource, ids, toMin)` responden desde memoria; una
  relación vacía no carga ningún catálogo.
- Caché: Caffeine con recarga en segundo plano (*stale-while-revalidate*), configurada con `app.swapi.catalog-ttl`
  (variable `SWAPI_CATALOG_TTL`, por defecto `1h`):
  - Los catálogos **no expiran**. Pasado el intervalo, el siguiente acceso recibe el catálogo cacheado y dispara una
    recarga en segundo plano.
  - Si la recarga falla, se registra un `WARN`, se conserva el catálogo anterior y el siguiente intento ocurre pasado
    otro intervalo: como máximo un intento de recarga por recurso e intervalo.
  - Las recargas pasan por el circuit breaker (ver [Circuit breaker](#circuit-breaker-frente-a-swapi)) y se ejecutan en
    un executor propio de hilos virtuales.
- Los **fallos de la carga en frío no se cachean**: los errores de SWAPI se propagan como siempre (502, 503 con
  `Retry-After`, 504) y la siguiente solicitud reintenta.
- Los listados (`SwapiQueryEngine`) se sirven del mismo catálogo: un listado en frío cuesta la carga del catálogo del
  recurso (1 llamada para people, starships, vehicles y films) y uno en caliente cuesta **0**. Listado y detalle son
  consistentes entre sí.
- Caché acotada a 16 entradas (hay una por recurso).

| Detalle | Llamadas a SWAPI en frío | En caliente |
|---|---|---|
| `/api/v1/people/{id}` y `/api/v1/films/{id}` | hasta 4 (propio + tres relaciones) | 0 |
| `/api/v1/starships/{id}` y `/api/v1/vehicles/{id}` | hasta 2 (propio + films) | 0 |

#### Mapeo de campos

Los nombres de SWAPI en `snake_case` se exponen en `camelCase` (`hair_color` -> `hairColor`, `MGLT` -> `mglt`,
`episode_id` -> `episodeId`). Ninguna URL de SWAPI se expone como dato, de modo que no se filtran URLs externas; las
relaciones solo existen en el detalle, ya resueltas como objetos. El resto de los valores se conserva tal cual, incluidos `unknown`, `n/a` o espacios
finales. Los atributos ausentes en SWAPI son `null`. El `id` es siempre texto (el `uid` de SWAPI; la URL del recurso
solo se usa como respaldo).

| DTO de listado | Atributos |
|---|---|
| `PersonDto` | id, name, height, mass, hairColor, skinColor, eyeColor, birthYear, gender |
| `FilmDto` | id, title, episodeId (entero), director, producer, releaseDate, openingCrawl |
| `StarshipDto` | id, name, model, starshipClass, manufacturer, costInCredits, length, crew, passengers, maxAtmospheringSpeed, hyperdriveRating, mglt, cargoCapacity, consumables |
| `VehicleDto` | id, name, model, vehicleClass, manufacturer, costInCredits, length, crew, passengers, maxAtmospheringSpeed, cargoCapacity, consumables |

## Contrato de paginación y filtros

Todos los listados se resuelven en un único lugar (`SwapiQueryEngine`) sobre el catálogo en caché del recurso, así que
el cliente siempre ve el mismo contrato:

| Paso | Qué hace |
|---|---|
| 1. Catálogo | `SwapiCatalogLoader.load(recurso)`: en frío es una llamada a SWAPI (`/{recurso}?page=1&limit=100&expanded=true`; films: `/films`); en caliente, memoria. |
| 2. Filtros | `id` exacto y `name` "contiene", sin distinguir mayúsculas (films compara con el título). |
| 3. Orden | `sort` (opcional). Sin `sort`, el orden del catálogo, que es el de SWAPI. |
| 4. Página | Se recorta la página pedida (tamaño fijo 10) y se calculan `count`, `totalPages`, `nextPage` y `previousPage`. |

Reglas:

- `page` inválido (0, negativo, no numérico) -> `400`. `page` mayor que `totalPages` -> `200` con `results` vacío y
  metadatos correctos.
- `id` inválido (no positivo o no numérico) -> `400`. `id` inexistente -> `200` con `count: 0`, `totalPages: 0` y
  `results: []`.
- `id` + `name`: **ambos deben coincidir**. No se ignora ningún parámetro.
- `id` + `page` > 1: `results` vacío, con `currentPage` igual a la página pedida.
- `name` vacío o solo espacios se trata como ausente; se recorta y admite hasta 100 caracteres.
- Los filtros y el orden se conservan al navegar: el frontend reenvía los mismos `name` y `sort` con otro `page`.
- El parámetro `name` nunca se envía a SWAPI: el filtrado es local.
- Los datos pueden tener más de `SWAPI_CATALOG_TTL` de antigüedad si SWAPI no responde a la recarga (ver [Caché de catálogos](#caché-de-catálogos)).

El tamaño de página (10) es configurable por propiedad (`app.swapi.page-size`) pero no se expone como parámetro.

### Ordenamiento

`sort=campo` ordena de forma ascendente y `sort=-campo` de forma descendente, por **un** campo. Cada recurso declara
los campos permitidos junto a su `SwapiResource`:

| Recurso | Campos de `sort` |
|---|---|
| People | `name`, `height`, `mass` |
| Films | `title`, `episodeId`, `releaseDate` |
| Starships | `name`, `costInCredits`, `length`, `crew`, `passengers`, `maxAtmospheringSpeed`, `hyperdriveRating`, `mglt`, `cargoCapacity` |
| Vehicles | `name`, `costInCredits`, `length`, `crew`, `passengers`, `maxAtmospheringSpeed`, `cargoCapacity` |

- Los campos numéricos se comparan como números (`9` antes que `100`; se admiten separadores de miles). El resto, como
  texto sin distinguir mayúsculas (`releaseDate` es ISO, por lo que ordena cronológicamente).
- Los valores que no se pueden comparar (`unknown`, `n/a`, vacío, ausente, rangos como `30-165`) van **siempre al
  final**, en ambos sentidos.
- Los empates se desempatan por `id` numérico ascendente, de modo que la paginación es estable.
- Un campo no permitido responde `400` con una entrada en `errors[]` para `sort` cuyo mensaje lista los valores
  válidos. Un `sort` con formato inválido (vacío, más de 50 caracteres, caracteres fuera de `^-?[a-zA-Z]+$`) también
  responde `400`. El orden se valida antes de cargar el catálogo.

## Errores

Todos los errores usan `application/problem+json` (RFC 9457, `ProblemDetail`) y mensajes fijos: nunca se devuelven
trazas, mensajes de excepciones ni valores rechazados.

| Situación | Estado |
|---|---|
| Parámetros inválidos (`page`, `id`, `name`, `sort`, `{id}` del detalle, cuerpo del login, del registro, del refresh o del logout) | `400` (con la lista `errors[{field, message}]`) |
| Registro con un usuario que ya existe | `409` (mensaje fijo `Username is not available.`) |
| Detalle de un `id` que no existe | `404` (mensaje fijo, sin detalles internos) |
| Credenciales de login incorrectas o usuario deshabilitado | `401` |
| Refresh token desconocido, vencido, revocado o ya usado | `401` (mensaje fijo `Invalid refresh token.`) |
| Token ausente, inválido, vencido, con otro emisor/audiencia/firma | `401` + cabecera `WWW-Authenticate: Bearer` |
| Autenticado sin permiso | `403` |
| Ruta inexistente / método no permitido | `404` / `405` |
| Demasiados intentos en login, refresh o registro desde la misma IP | `429 Too Many Requests` + `Retry-After` en segundos |
| SWAPI responde 5xx, es inalcanzable o responde algo ilegible | `502 Bad Gateway` |
| SWAPI limita las solicitudes (`429`, 100 por ventana) | `503 Service Unavailable` + `Retry-After` si se conoce |
| SWAPI no responde dentro del timeout | `504 Gateway Timeout` |
| El circuit breaker frente a SWAPI está abierto | `503 Service Unavailable` + `Retry-After` en segundos (cota superior), sin llamar a SWAPI |
| Error inesperado | `500` genérico (el detalle solo se registra en el log del servidor) |

Decisión sobre el `429`: el límite lo excede nuestro servidor frente a SWAPI, no el cliente frente a nosotros, por lo
que devolverle `429` sería engañoso. Se responde `503` y, si SWAPI informa `Retry-After` o `x-ratelimit-reset`, se
traduce a un `Retry-After` en segundos (con un tope de 1 hora).

### Reintentos frente a SWAPI

`SwapiClient` reintenta con el `RetryTemplate` de Spring Framework, con backoff exponencial (espera inicial
`SWAPI_RETRY_DELAY`, multiplicador 2 y un pequeño jitter) y hasta `SWAPI_RETRY_MAX_RETRIES` reintentos. Solo se
reintentan los fallos transitorios:

- Respuestas `5xx`.
- Fallos de conexión (conexión rechazada o reiniciada).

No se reintenta:

- `429`: reintentar empeoraría el límite de SWAPI.
- Timeouts de lectura: multiplicarían la latencia que ve el cliente.
- Otros `4xx` y respuestas ilegibles: volverían a fallar igual.

Agotados los reintentos, el error se traduce igual que sin ellos (`502`, `503` o `504`).

### Circuit breaker frente a SWAPI

`SwapiClient` envuelve cada operación (ya con sus reintentos) en un circuit breaker de Resilience4j (módulo
`resilience4j-circuitbreaker`). Al momento de escribir no hay un starter de Resilience4j para Spring Boot 4 publicado
en Maven Central, por eso se usa el módulo core sin starter. El breaker registra el resultado final de cada
operación, no cada intento.

- **Cuentan como fallo:** fallos HTTP de `RestClient` que no sean `4xx` (`5xx`), timeouts, errores de conexión y
  respuestas ilegibles o con una forma inválida (por ejemplo, un `200` sin `results`, `total_pages` o `result`). Las
  respuestas inválidas cuentan para el breaker pero no se reintentan.
- **No cuentan ni como fallo ni como acierto:** los `4xx` y el `429` (este último ya tiene su propio manejo con
  `Retry-After`). Nunca abren el breaker.
- **Cerrado:** funciona con normalidad. Se abre cuando, con al menos `SWAPI_CB_MINIMUM_NUMBER_OF_CALLS` operaciones
  en la ventana, el porcentaje de fallos entre las últimas `SWAPI_CB_SLIDING_WINDOW_SIZE` alcanza
  `SWAPI_CB_FAILURE_RATE_THRESHOLD`.
- **Abierto:** durante `SWAPI_CB_WAIT_DURATION` las llamadas se rechazan al instante con `503 Service Unavailable`,
  sin llamar a SWAPI. La respuesta incluye `Retry-After` en segundos, con el valor de `SWAPI_CB_WAIT_DURATION`
  redondeado hacia arriba como cota superior (la espera restante real puede ser menor).
- **Semiabierto:** pasada la espera, se permiten `SWAPI_CB_HALF_OPEN_CALLS` llamadas de prueba. Si aciertan, el
  breaker se cierra; si fallan, vuelve a abrirse. La transición es perezosa: ocurre en la primera llamada posterior
  a la espera, que es cuando aparece en el log el `WARN` del cambio de estado.

Cada cambio de estado se registra a nivel `WARN`; las llamadas rechazadas no generan una línea de log cada una.
Como los listados se sirven desde la caché de catálogos, un breaker abierto solo afecta a lo que no esté cacheado.
Las recargas en segundo plano de los catálogos también pasan por el breaker; si una recarga falla (o es rechazada) se
conserva el catálogo anterior y se registra un único `WARN`, sin reintentar hasta pasado otro `SWAPI_CATALOG_TTL`.

| Variable | Por defecto | Propiedad |
| --- | --- | --- |
| `SWAPI_CB_FAILURE_RATE_THRESHOLD` | `50` | `app.swapi.circuit-breaker.failure-rate-threshold` (1 a 100, en %) |
| `SWAPI_CB_SLIDING_WINDOW_SIZE` | `10` | `app.swapi.circuit-breaker.sliding-window-size` (1 a 1000 llamadas) |
| `SWAPI_CB_MINIMUM_NUMBER_OF_CALLS` | `5` | `app.swapi.circuit-breaker.minimum-number-of-calls` (no puede superar la ventana) |
| `SWAPI_CB_WAIT_DURATION` | `30s` | `app.swapi.circuit-breaker.wait-duration-in-open-state` (mínimo 1 ms) |
| `SWAPI_CB_HALF_OPEN_CALLS` | `2` | `app.swapi.circuit-breaker.permitted-calls-in-half-open-state` (1 a 100) |

## Seguridad

- API REST **stateless**; sin sesiones ni cookies. CSRF está desactivado únicamente por eso: la autenticación viaja
  en la cabecera `Authorization: Bearer`.
- **Login propio** (`POST /api/v1/auth/login`) que verifica credenciales con Spring Security y emite el JWT con
  `NimbusJwtEncoder`. La **validación** de los tokens la hace el soporte nativo OAuth2 Resource Server
  (`oauth2ResourceServer(o -> o.jwt(...))`); no hay filtro JWT propio ni JJWT. Esto **no** es un servidor de
  autorización OAuth 2.0 completo (ver evolutivos).
- **JWT:** HS256 (clave simétrica, adecuada porque emisor y validador son la misma aplicación). Claims: `iss`,
  `sub`, `aud`, `iat`, `exp` y `jti`. El decodificador valida firma, algoritmo (solo HS256), vencimiento, `iss` y
  `aud` mediante un `DelegatingOAuth2TokenValidator`. Al definir un `JwtDecoder` propio, Spring Boot ignora las
  propiedades que construyen el decodificador (`jwk-set-uri`, `issuer-uri`, `public-key-location`, `jws-algorithms`,
  `audiences`), y ninguna admite una clave simétrica HS256; por eso los validadores se declaran de forma explícita.
  Siguen vigentes `authorities-claim-name`, `authority-prefix`, `authorities-claim-delimiter` y
  `principal-claim-name`, porque configuran el `JwtAuthenticationConverter` de Boot y no el decodificador.
- **Autorización:** basta con estar autenticado; no hay roles.
- **Contraseñas:** solo se guarda el hash BCrypt; la contraseña y el hash nunca se escriben en logs ni en respuestas.
- **Superficie pública:** el login, el registro, el refresh, el logout, la documentación OpenAPI/Swagger UI (solo describe, no expone datos) y
  `/actuator/health`. Cualquier otra ruta, incluso inexistente, exige token. Se puede apagar la documentación en
  producción con `springdoc.api-docs.enabled=false` y `springdoc.swagger-ui.enabled=false`.
- **CORS:** lista explícita de orígenes (`CORS_ALLOWED_ORIGINS`), métodos `GET`/`POST`/`OPTIONS`, sin credenciales.
  Expone la cabecera `Retry-After` para que el navegador pueda leer el tiempo de espera de los `503` y los `429`.
- **Rate limiting de autenticación:** `RateLimitFilter` limita únicamente `POST /api/v1/auth/login`,
  `POST /api/v1/auth/refresh` y `POST /api/v1/auth/register`; el resto de rutas y métodos nunca se limita. Cada
  combinación de endpoint e IP del cliente tiene su propio bucket (Bucket4j, con recarga gradual), guardado en una
  caché Caffeine con expiración por inactividad. Límites por defecto, configurables en `app.rate-limit.*`
  (`capacity` y `period`, p. ej. `app.rate-limit.login.capacity=5`, `app.rate-limit.login.period=1m`):

  | Endpoint | Límite por IP |
  |---|---|
  | `POST /api/v1/auth/login` | 5 por minuto |
  | `POST /api/v1/auth/refresh` | 10 por minuto |
  | `POST /api/v1/auth/register` | 5 por hora |

  Al excederlo responde `429 Too Many Requests` (`application/problem+json`) con `Retry-After` en segundos. La
  respuesta conserva las cabeceras CORS de los orígenes permitidos.
- **IP del cliente:** se lee `getRemoteAddr()`; nunca se interpreta `X-Forwarded-For` a mano. Con
  `server.forward-headers-strategy=native`, Tomcat resuelve la IP real detrás del proxy inverso solo si el proxy
  está dentro de `server.tomcat.remoteip.internal-proxies`, que por defecto cubre rangos privados y de loopback y se
  puede ajustar.
- **Actuator:** solo se expone `health`, sin detalles.

### Sesiones y refresh tokens

- **Token opaco:** 32 bytes de `SecureRandom` en Base64 URL sin relleno. En la base solo se guarda su SHA-256 en
  hexadecimal (`refresh_tokens.token_hash`); el valor original existe únicamente en la respuesta y nunca se escribe en logs.
- **Familia:** cada login crea una sesión (una `family_id` aleatoria). Cada refresh marca el token presentado como
  usado (`UPDATE ... WHERE used_at IS NULL AND revoked_at IS NULL`, atómico) y emite el siguiente de la misma familia.
- **Vencimiento heredado:** el token nuevo conserva el `expires_at` del anterior, de modo que la sesión dura
  `JWT_REFRESH_TTL` desde el login por muchas veces que se renueve; `refreshExpiresIn` decrece en cada refresh.
- **Detección de reutilización:** presentar un token ya usado revoca toda la familia (también el token más nuevo) y
  responde `401`. La revocación se confirma aunque la petición termine en error.
- **Logout:** revoca la familia del token recibido. Es idempotente y responde `204` incluso si el token no existe.
- **Access token sin estado:** no se consulta la base en cada petición, así que tras el logout (o una revocación) un
  access token ya emitido sigue siendo válido hasta su vencimiento (15 minutos por defecto).
- **Concurrencia:** dos refrescos en paralelo con el mismo token son indistinguibles de una reutilización: uno recibe
  `200` y el resto `401`, y la familia queda revocada, lo que termina la sesión. El cliente debe serializar los refrescos.
- **Rate limiting:** el refresh comparte el esquema de límites por IP; el logout no se limita.

- Ningún secreto ni token se imprime en logs; los `toString` de las propiedades y DTO sensibles están ofuscados.

## Swagger UI

1. Abrir `http://localhost:8080/swagger-ui.html`.
2. Ejecutar `POST /api/v1/auth/login` con el usuario de demostración y copiar `accessToken`.
3. Pulsar **Authorize**, pegar el token (sin el prefijo `Bearer`) y confirmar.
4. Probar cualquiera de los cuatro listados o de los cuatro detalles. Los endpoints se agrupan en cinco etiquetas: `Authentication`, `People`, `Films`, `Starships` y `Vehicles`. El documento JSON está en `/v3/api-docs`.

## Tests

```bash
./mvnw test      # solo tests
./mvnw verify    # build completo + tests
```

Los tests necesitan **Docker en ejecución**: todos los que levantan el contexto de Spring usan un único contenedor
PostgreSQL (`postgres:17-alpine`) compartido por toda la suite, creado con Testcontainers y conectado con
`@ServiceConnection`. La primera ejecución descarga la imagen. Los tests corren con `./mvnw verify` en el host o en CI,
no durante `docker build`.

Ninguna prueba llama a swapi.tech. Los fixtures de `src/test/resources/swapi` son respuestas reales capturadas del
servicio (listado expandido, búsqueda, films y recurso individual); las páginas de catálogo (`*-catalog.json`)
son extractos con el mismo formato que devuelve `?page=1&limit=100&expanded=true`. Los secretos de prueba viven solo en
`src/test/resources/application-test.properties`.

| Nivel | Qué cubre |
|---|---|
| Unitarias | mappers por recurso (`PeopleMapper`, `FilmsMapper`, `StarshipsMapper`, `VehiclesMapper`), `SwapiFields` y extracción de ID desde la URL, `PageResponse` (paginación local, fuera de rango), `ListQuery`, `SwapiQueryEngine` (filtros, combinaciones `id`+`name`, films vs. `title`, orden ascendente y descendente, numéricos vs. texto, no comparables al final, desempate por `id`, `sort` inválido), `toMin`/`toDetail` de cada mapper, `SwapiCatalogLoader` (paginación, carga en frío compartida, fallos no cacheados, stale-while-revalidate: sirve el catálogo anterior tras el TTL y si la recarga falla) y `SwapiCatalog`, los servicios de detalle (relaciones resueltas, IDs desconocidos omitidos, 404), `SwapiClient` con `MockRestServiceServer` (páginas y colección, 404, 429, 5xx, timeouts, respuestas malformadas), clave JWT y propiedades. |
| Seguridad (contexto completo + MockMvc) | Login válido/inválido, claims y firma del JWT, token ausente, mal firmado, con otro algoritmo, `alg=none`, vencido, otro emisor/audiencia, Basic auth, acceso con token válido, CORS, superficie pública y actuator. Cada rechazo verifica además que no se llamó a SWAPI. |
| Contrato HTTP | Forma de `PageResponse` y de los cuatro detalles (nombres exactos de los campos), filtros y `sort` por recurso, validación de parámetros (incluido `sort`) y de `{id}` (400), `404`, `401` sin token, traducción de errores externos (502/503/504/500), sin fugas de información. |
| Extremo a extremo | Login -> listado/detalle -> `RestClient` real -> servidor HTTP falso de SWAPI (JDK `HttpServer`): cantidad de llamadas a SWAPI de listados y detalles en frío y en caliente, filtros y orden sobre el catálogo, fallos de SWAPI que no se cachean, timeouts reales y conexión rechazada. |
| Base de datos y registro (PostgreSQL real) | Migración Flyway aplicada y `validate` de Hibernate, restricción `unique` de `users.username`, `DemoUserInitializer` (crea, es idempotente y actualiza una contraseña cambiada), login contra la base (sin distinguir mayúsculas, usuario deshabilitado rechazado), registro (`201`, normalización, `409` incluido `Luke`/`luke` y registros simultáneos, límites de largo y de bytes, contraseña igual al usuario, sin fugas en las respuestas) y traducción de la violación de unicidad. |
| Refresh tokens (PostgreSQL real, reloj controlado) | Login con ambos tokens y solo el hash en la tabla, rotación en la misma familia, reutilización que revoca la familia (con la revocación confirmada pese al `401`), vencimiento y vencimiento heredado, usuario deshabilitado, mensaje `401` idéntico en todos los casos, logout idempotente, cuatro refrescos concurrentes con el mismo token (un `200`, tres `401`), validación `400` y que los tokens no aparezcan en logs. |
| OpenAPI | El documento publicado declara el esquema Bearer, los doce endpoints, filtros, `sort` con los campos permitidos de cada recurso, el parámetro `{id}`, los esquemas de detalle y las respuestas de error (incluido `404`). |

## Construir el JAR

```bash
./mvnw clean package          # genera target/star-wars-api-0.0.1-SNAPSHOT.jar
java -jar target/star-wars-api-0.0.1-SNAPSHOT.jar
```

(con las variables de entorno obligatorias definidas y una base PostgreSQL accesible).

## Docker

```bash
docker compose up --build
```

`docker-compose.yml` define dos servicios:

- `db`: `postgres:17-alpine` con `POSTGRES_DB`, `POSTGRES_USER` y `POSTGRES_PASSWORD` tomados de `DB_NAME`, `DB_USER` y
  `DB_PASSWORD`, volumen con nombre `db-data` y health check con `pg_isready`.
- `api`: se construye con el `Dockerfile`, lee `.env`, usa `DB_HOST=db` y espera a que `db` esté saludable
  (`depends_on: condition: service_healthy`). Publica el puerto `8080`.

El archivo no contiene secretos: todo sale de `.env`. Para construir y ejecutar solo la imagen de la API
(con una base accesible desde el contenedor):

```bash
docker build -t star-wars-api .
docker run --rm -p 8080:8080 --env-file .env star-wars-api
```

El `Dockerfile` es multi-etapa: compila con **JDK 21** (`eclipse-temurin:21-jdk`) usando el Maven Wrapper (`mvnw`, que fija Maven 3.10.0) y ejecuta el JAR en un
**JRE 21** (`eclipse-temurin:21-jre`) como usuario no root. Respeta la variable `PORT`. No ejecuta los tests: necesitan
Docker (Testcontainers), que no está disponible dentro de `docker build`; se ejecutan con `./mvnw verify`.

### Verificación posterior al despliegue

El rate limiting agrupa las solicitudes por la IP que ve la aplicación (`getRemoteAddr()`). Detrás de un proxy inverso,
Tomcat solo la resuelve desde `X-Forwarded-For` si el proxy está dentro de `server.tomcat.remoteip.internal-proxies`
(por defecto, rangos privados y de loopback). Para comprobarlo:

1. Desde un cliente, enviar 5 `POST /api/v1/auth/login` con credenciales inválidas: la sexta responde `429`.
2. Desde otra red o IP (por ejemplo, un teléfono con datos móviles), enviar un `POST /api/v1/auth/login`: debe responder
   `401`, no `429`.

Si el segundo cliente recibe `429`, todos comparten la IP del proxy y hay que ajustar
`server.tomcat.remoteip.internal-proxies`.

## Decisiones de diseño

- **Errores con `ProblemDetail`** (RFC 9457) y datos sin envoltorio propio; las excepciones de la integración con SWAPI
  llevan su estado HTTP.
- **Caché solo de catálogos:** el detalle se arma contra colecciones completas cacheadas (Caffeine, con recarga en
  segundo plano configurable) por el límite de 100 solicitudes de SWAPI; ver [Caché de catálogos](#caché-de-catálogos). Los
  listados, filtros y orden se resuelven sobre esos catálogos.
- **Planetas y pilotos fuera de alcance:** no hay recurso `planets` y los pilotos no se representan; la referencia al
  planeta natal de los personajes no se expone, porque no existe un recurso de planetas con el que resolverla.
- **Timeouts** mediante `spring.http.clients.*` de Spring Boot, aplicados al `RestClient.Builder` autoconfigurado.
- **IDs como texto**, tal cual los entrega SWAPI (`uid`).

## Limitaciones conocidas y evolutivos

- SWAPI limita a 100 solicitudes por ventana. Un listado en frío consume 1 llamada y un detalle en frío hasta 4; en
  caliente ninguno consume.
- Los datos de listados y detalles se recargan cada `SWAPI_CATALOG_TTL` en segundo plano; si la recarga falla, se siguen sirviendo los anteriores y su antigüedad puede superar ese intervalo. La caché es local a cada instancia.
- La búsqueda por `name` solo admite una coincidencia "contiene"; el orden admite un único campo por consulta.
- Los access tokens no se pueden revocar antes de vencer (viven hasta 15 minutos tras un logout).
- Los refresh tokens vencidos o revocados no se eliminan de la base: no hay limpieza programada.
- El registro es público y no verifica correo electrónico ni tiene recuperación de contraseña.
- Los buckets del rate limiting están en memoria y son por instancia: con varias instancias el límite efectivo se
  multiplica y se reinicia al reiniciar la aplicación.

