# arbiter

`arbiter` is a Java 17+ command-line client and reference service. It speaks
HTTP and Unix-domain sockets, shares one API contract between the CLI and the
server, and ships as a single `tar.gz`.

The CLI has two modes:

- **Direct mode** — run one command and exit.
- **Interactive shell** — run commands repeatedly with Tab completion,
  persistent history, and per-command failures that do not end the session.

`arbiter` is both the executable name and the Maven artifact prefix.

## Contents

- [Quick start](#quick-start)
- [Distribution layout](#distribution-layout)
- [Configuration](#configuration)
- [CLI](#cli)
- [Server](#server)
- [Security](#security)
- [Transports](#transports)
- [Architecture](#architecture)
- [Development](#development)

## Quick start

Build the release and run the CLI from the extracted package:

```bash
mvn clean verify
rm -rf /tmp/arbiter-verify
mkdir -p /tmp/arbiter-verify
tar -xzf arbiter-distribution/target/arbiter-1.0.0-SNAPSHOT.tar.gz -C /tmp/arbiter-verify
/tmp/arbiter-verify/arbiter-1.0.0-SNAPSHOT/bin/arbiter --help
```

On Windows:

```powershell
mvn clean verify
tar -xzf arbiter-distribution\target\arbiter-1.0.0-SNAPSHOT.tar.gz -C $env:TEMP
& "$env:TEMP\arbiter-1.0.0-SNAPSHOT\bin\arbiter.bat" --help
```

The launchers do not change the console code page; the JAR detects the
configured charset, so Chinese text renders correctly in both CP936 and UTF-8
terminals.

Start and stop a server:

```bash
bin/server-start
bin/server-stop
```

The reference server has no built-in users; implement `UserRepository` to
enable `auth login` and `token create`.

Every server setting comes from `conf/common.properties` and
`conf/server.properties`; the server reads no environment variables. See
[Configuration](#configuration) for the complete key list.

## Distribution layout

`mvn package` produces exactly one artifact:
`arbiter-distribution/target/arbiter-1.0.0-SNAPSHOT.tar.gz`. It must be
extracted to a clean directory before it is run or verified.

```text
arbiter-1.0.0-SNAPSHOT/
├── bin/
│   ├── _arbiter-common.sh
│   ├── _arbiter-common.bat
│   ├── arbiter
│   ├── arbiter.bat
│   ├── server-start
│   ├── server-start.bat
│   ├── server-stop
│   └── server-stop.bat
├── conf/
│   ├── cli.properties
│   ├── common.properties
│   ├── cli-jvm.options
│   ├── server.properties
│   └── security/
│       └── README
├── lib/
│   ├── arbiter-api.jar
│   ├── cli/
│   │   ├── arbiter-cli-launcher.jar
│   │   ├── arbiter-core.jar
│   │   ├── arbiter-command.jar
│   │   ├── arbiter-client.jar
│   │   ├── arbiter-shell.jar
│   │   ├── picocli.jar
│   │   ├── picocli-shell-jline3.jar
│   │   └── jline.jar
│   ├── bootstrap/
│   │   ├── arbiter-console.jar
│   │   └── arbiter-control.jar
│   └── 3rd/
│       ├── spring-boot-*.jar
│       ├── spring-*.jar
│       ├── tomcat-embed-*.jar
│       ├── jackson-*.jar
│       ├── logback-*.jar
│       └── ...
```

Classpath rules:

- CLI module JARs are packaged without Maven version suffixes; no Shade or
  Boot executable JAR exists.
- `lib/cli` holds the CLI modules and their CLI-only dependencies
  (Picocli/JLine).
- `lib/arbiter-api.jar` at the `lib` root is the single shared contract JAR.
- `lib/bootstrap/arbiter-console.jar` is the thin Spring Boot console JAR. Its
  Spring Boot, Spring, Tomcat, Jackson, and logging dependencies live in
  `lib/3rd`.
- `lib/bootstrap/arbiter-control.jar` is the Java 8-compatible, Spring-free
  control module. `--check`, `--stop`, `--help`, and `--version` load only this
  JAR.
- The CLI launcher pins exactly the JARs it needs and only reads
  `lib/3rd/jackson-*.jar` from the shared directory.
- The server launcher uses only `arbiter-control.jar` for control commands, and
  adds the console JAR, `lib/*`, and `lib/3rd/*` for normal
  `ArbiterServerApplication` startup.
- Optional `tools/*` and `plugins/*` directories are appended when present; the
  distribution does not create them.

Launcher behavior:

- The scripts resolve the root from `bin/..` or an explicit `ARBITER_HOME`;
  they never search `target/` or a source checkout.
- The Unix launchers are POSIX `sh`, resolve symlinks, and share
  `bin/_arbiter-common.sh`; the Windows launchers share
  `bin\_arbiter-common.bat`. Both are internal and never invoked directly.
- JVM options for the CLI live in `conf/cli-jvm.options`: one JVM argument per
  line, `#` comments, spaces preserved. The launcher reads them only on Java
  17+, so `--enable-native-access` and JLine/Jansi `--add-opens`/`--add-exports`
  can be changed without editing scripts. `ARBITER_JAVA_OPTS` still appends
  ad-hoc options.
- Each server start rotates `logs/server.log` to `logs/server.log.1`.
- `server-stop` is idempotent and uses the Java 8-compatible control JAR, so it
  does not need the Java 17 server runtime.
- On Windows the server starts with `start /b`, so closing the console stops
  it; use a service wrapper for a detached install.

## Configuration

Three files, split by owner:

| File | Read by | Contents |
|---|---|---|
| `conf/common.properties` | both | server endpoint, transport, API prefix |
| `conf/cli.properties` | CLI | timeouts, retries, pool, client TLS, endpoint override |
| `conf/server.properties` | server | limits and TLS material passwords |

All keys use the `arbiter.` prefix and are defined once in
[`ConfigKeys`](arbiter-api/src/main/java/io/github/arbiter/api/config/ConfigKeys.java).

### Client precedence

1. Command-line options.
2. Environment (`ARBITER_TRANSPORT`, locale variables, client TLS passwords).
3. Project `.arbiterrc`, searched upward from the working directory.
4. User `~/.config/arbiter/config`.
5. `conf/cli.properties`.
6. `conf/common.properties`.
7. Built-in defaults.

`--config <path>` uses that file instead of the other client files.
Credentials must never be put in project configuration.

The client has no `.env` loader; set environment variables through the shell,
CI, or a service manager. Client-only keys use `arbiter.client.*`; the endpoint
is derived from `arbiter.client.host` when set, otherwise
`arbiter.server.host`, combined with `arbiter.server.port`.

### Server precedence

1. `conf/server.properties`.
2. `conf/common.properties`.
3. Built-in defaults.

The two files are installed as the first Boot `PropertySource`, so `arbiter.*`
values from `conf` win over process environment, system properties, and
classpath configuration. There are no server environment variables and no server
command-line tunables; operational flags are listed under [Server](#server).

`conf/server.properties` contains TLS passwords, so keep it readable only by the
service account. The token signing key and crypto key ring are generated under
`conf/security/` on first start.

### Shared keys

```properties
# conf/common.properties
arbiter.server.host=127.0.0.1
arbiter.server.port=8080
arbiter.transport=http
arbiter.api.prefix=/api/v1
```

`arbiter.transport` accepts `http`, `unix`, or `both`. With `both` the server
opens HTTP and the Unix-domain socket, and the CLI uses HTTP.

### Client keys

```properties
# conf/cli.properties
# arbiter.client.host=127.0.0.1
arbiter.client.connect.timeout.ms=3000
arbiter.client.read.timeout.ms=10000
arbiter.client.pool.acquire.timeout.ms=2000
arbiter.client.pool.permits=4
arbiter.client.retry.max=2
arbiter.client.retry.base.delay.ms=150
arbiter.client.tls.enabled=true
arbiter.client.tls.truststore=/path/to/client-trust.p12
arbiter.client.tls.truststore.type=PKCS12
arbiter.client.tls.keystore=/path/to/client.p12
arbiter.client.tls.keystore.type=PKCS12
```

Client TLS passwords remain environment-only:
`ARBITER_TLS_TRUSTSTORE_PASSWORD` and `ARBITER_TLS_KEYSTORE_PASSWORD`.

### Client environment

The client reads these variables from the process environment; there is no
`.env` loader.

| Variable | Purpose |
|---|---|
| `ARBITER_TOKEN` | Pre-issued token; wins over the stored credential |
| `ARBITER_USERNAME` | Default login name |
| `ARBITER_LANG` | Message language, for example `en` or `zh_CN` |
| `ARBITER_TRANSPORT` | `http` or `unix` |
| `ARBITER_TLS_ENABLED` | Enable TLS for `https://` URLs |
| `ARBITER_TLS_TRUSTSTORE` | Client trust store path |
| `ARBITER_TLS_TRUSTSTORE_TYPE` | Trust store type, default `PKCS12` |
| `ARBITER_TLS_TRUSTSTORE_PASSWORD` | Trust store password |
| `ARBITER_TLS_KEYSTORE` | Client key store for mutual TLS |
| `ARBITER_TLS_KEYSTORE_TYPE` | Key store type, default `PKCS12` |
| `ARBITER_TLS_KEYSTORE_PASSWORD` | Key store password |

### Server keys

```properties
# conf/server.properties
arbiter.server.token.ttl.seconds=3600
arbiter.server.max.request.bytes=65536
arbiter.server.login.max.failures=5
arbiter.server.login.window.seconds=60

arbiter.server.tls.enabled=false
arbiter.server.tls.keystore.type=PKCS12
arbiter.server.tls.keystore.password=changeit
arbiter.server.tls.client.auth=false
arbiter.server.tls.truststore.type=PKCS12
arbiter.server.tls.truststore.password=changeit
arbiter.server.tls.protocols=TLSv1.3,TLSv1.2
```

### Launcher variables

| Variable | Purpose |
|---|---|
| `ARBITER_HOME` | Explicit application root containing `lib`, `tools`, and `plugins` |
| `ARBITER_MAIN_JAR_OVERRIDE` | Explicit main CLI JAR path |
| `ARBITER_JAVA` | Explicit Java executable for the CLI |
| `ARBITER_JAVA_OPTS` | Additional JVM options for the CLI |
| `ARBITER_SERVER_JAVA` | Explicit Java executable for the server |
| `ARBITER_SERVER_JAVA_OPTS` | Additional JVM options for the server process only (not `--check`/`stop`) |
| `ARBITER_SERVER_START_TIMEOUT` | Seconds to wait for startup, default `10` |
| `ARBITER_DEBUG_LAUNCH` | Print resolved root, main JAR, classpath, and CLI JVM options |

The launchers pass `-Darbiter.home=<root>`. Two JVM system properties are read:

| Property | Written by | Purpose |
|---|---|---|
| `arbiter.home` | launchers | Distribution root used to resolve `conf/`, `run/`, and `logs/` |
| `arbiter.user.home` | tests and restricted hosts | Overrides the user configuration root (default `$HOME/.config/arbiter`) |

`arbiter.user.home` is an internal seam: `UserPaths` prefers `HOME`/`USERPROFILE`
over the JVM's `user.home`, and a test cannot change the process environment.

## CLI

```text
arbiter
├── auth
│   ├── login [--username <name>] [--password-stdin]
│   ├── status
│   └── logout
├── token
│   └── create [--username <name>] [--password-stdin]
├── user
│   ├── list
│   ├── show <username>
│   ├── create <username> [--scope <scope>] [--password-stdin]
│   ├── update <username> [--scope <scope>] [--enable|--disable]
│   └── delete <username> --yes
├── crypto
│   ├── encrypt <text>
│   └── decrypt <text>
├── status
├── query [--name <resource>]
└── server
    ├── start [--debug[=ADDRESS]]
    ├── status
    ├── stop
    └── restart [--debug[=ADDRESS]]
```

Global options:

```text
--config <path>
--verbose
--output json|table
--lang <tag>
--no-input
-h, --help
-V, --version
```

Examples:

```bash
ARBITER_TOKEN="$(cat token.txt)" bin/arbiter status

bin/arbiter --output json --lang en query --name demo

bin/arbiter user list
printf '%s\n' "$PASSWORD" | \
  bin/arbiter user create alice --scope reader --password-stdin
bin/arbiter user update alice --scope admin --disable
bin/arbiter user delete alice --yes

printf '%s\n' "$PASSWORD" | \
  bin/arbiter auth login --username alice --password-stdin --no-input
```

The login username priority is `--username`, `ARBITER_USERNAME`, then an
interactive prompt. Password input is interactive or `--password-stdin`;
`--password` is intentionally unsupported and scrubbed from error output.

### Token generation

`token create` exchanges a username and password for a server-issued token
through `POST /api/v1/auth/login`, prints it, and does **not** write the
credential file:

```bash
printf '%s\n' "$PASSWORD" | \
  bin/arbiter token create --username alice --password-stdin

bin/arbiter --output json token create --username alice --password-stdin
```

Use the printed value as `ARBITER_TOKEN` for subsequent commands. Unlike
`auth login`, this does not persist a credential and does not update
`~/.config/arbiter/credentials`. This requires a `UserRepository` with users;
the reference server has none by default.

### AES text encryption

`crypto encrypt` and `crypto decrypt` use AES-256-GCM with the key the server
creates on its first start:

```text
<arbiter.home>/conf/security/crypto.key
```

The file is a key ring and is written with owner-only permissions:

```properties
version=1
active=k1
key.k1=<Base64 32 bytes>
# key.k2=<Base64 32 bytes>   # kept for decrypting older ciphertext
```

Start the server once before using the crypto commands:

```bash
bin/server-start

CIPHER=$(bin/arbiter crypto encrypt "hello" | sed 's/^Ciphertext: //')
bin/arbiter crypto decrypt "$CIPHER"
```

Ciphertext is self-describing:

```text
arbiter:v1:aes-256-gcm:k1:<Base64(iv || ciphertext || tag)>
         ^^  ^^^^^^^^^^^  ^^
         |   |            key id
         |   algorithm id
         envelope version
```

Decryption selects the algorithm and key from the envelope, never from a
command-line flag. Bare Base64 payloads are rejected. To rotate, add a new
`key.kN` entry and point `active` at it; ciphertext encrypted with the old key
keeps decrypting while that key stays in the ring. New algorithms are added by
registering a `CipherSuite` in `CipherSuites`. A wrong key or modified
ciphertext fails authentication; the command then exits 1 with
`err.crypto.decryptFailed`. Unknown algorithms and key ids exit 2 with
`err.crypto.unsupportedAlgorithm` / `err.crypto.unknownKey`. If the ring has not
been generated yet, the command exits 2 with `err.crypto.keyNotInitialized`.

### Direct mode vs shell

| Behavior | Direct CLI | Interactive shell |
|---|---|---|
| Entry | Any argument | No arguments |
| HTTP client | One process | Reused for the session |
| Parse/execution error | Localized error, nonzero exit | Localized error, prompt returns |
| Missing value, noninteractive | Exit 2, no stdin read | Same with `--no-input` or no TTY |
| Missing value, interactive | Console prompt | JLine prompt |
| Ctrl+C during prompt | Cancel command, exit 9 | Cancel command, shell survives |
| Exit shell | N/A | `exit`, `quit`, `:q`, `:quit`, or Ctrl+D |

Shell completion covers command, subcommand, and option names, not business
values. Passwords and temporary object names are read with history disabled.
History is written to `~/.config/arbiter/history` on a best-effort basis. Server
URL and timeout settings are resolved at shell start and stay fixed until
restart.

### Token precedence

1. `ARBITER_TOKEN` environment variable.
2. Operating-system credential manager (v1: not implemented).
3. Restricted credential file `~/.config/arbiter/credentials`.

On POSIX the credential directory/file use `700`/`600`. Windows currently relies
on the signed-in user's `%USERPROFILE%` and should move to Credential Manager in
a later version.

### Result contract

`--output table` (default) prints localized text. `--output json` prints a
deterministic document:

```json
{
  "success": true,
  "exitCode": 0,
  "messageKey": "cmd.query.success",
  "message": "Query result for demo: value",
  "data": {"name": "demo", "value": "value"},
  "warnings": []
}
```

With `--output json`, success and error documents both go to stdout;
`--verbose` diagnostics go to stderr, so stdout stays parseable.

| Exit | Meaning | Message key |
|---:|---|---|
| 0 | Success | — |
| 1 | Unexpected/unknown remote error | `err.unknown` |
| 2 | Usage, missing parameter, invalid input | `err.usage.*` |
| 3 | Authentication failure or forbidden | `err.auth.expired` / `err.auth.forbidden` |
| 4 | Not found | `err.notfound` |
| 5 | Conflict | `err.conflict` |
| 6 | Rate limited | `err.ratelimit` |
| 7 | Server 5xx | `err.server.5xx` |
| 8 | Connection failure or timeout | `err.network` |
| 9 | Prompt cancelled/EOF | `err.aborted` |

`DomainException` also exposes a stable `ErrorCode` category. It is internal and
does not change the JSON document or exit codes.

Login mapping: 401/403 → 3, 429 → 6, 5xx → 7, network → 8. A 401 never retries;
only idempotent requests retry, with bounded exponential backoff. `--verbose`
appends the server message without translating it.

### Internationalization

Messages live in `arbiter-command/src/main/resources/messages.properties`
(English) and `messages_zh_CN.properties` (UTF-8), grouped as `cmd.*`, `err.*`,
`prompt.*`, and `help.*`. Missing keys fall back to English.

Result locale precedence: `--lang`, `ARBITER_LANG`, `LC_ALL`, `LANG`, JVM
default. `zh` normalizes to `zh_CN`. Help uses the startup locale; `--lang`
changes command-result text only. The shell freezes its locale until restart.

## Server

Start and stop from the distribution:

```bash
bin/server-start
bin/server-stop
```

Or from the CLI:

```text
arbiter> server start
arbiter> server status
arbiter> server stop
arbiter> server restart
```

`server start` does not change configuration; edit `conf/server.properties`
before starting. The standalone scripts remain for service managers.

`server start --debug` (or `bin/server-start --debug`) adds a JDWP agent on
`127.0.0.1:5005`. Pass a different address with `--debug=ADDRESS:PORT`
(`server-start` on Unix also accepts `--debug ADDRESS:PORT`; use the `=`
form in `.bat`):

```bash
bin/server-start --debug=127.0.0.1:5006
bin/arbiter server start --debug=127.0.0.1:5006
```

The agent does not suspend startup and is off by default. `server restart
--debug=ADDRESS:PORT` applies the same flag to the restarted process.

Server command line (operational flags only):

```text
--check    print "RUNNING <pid>" or "STOPPED"
--stop     ask the running server to stop
--help
--version
```

### Lifecycle files and logs

The server owns fixed paths under `<arbiter.home>/run/`:
`.server.pid`, `.server.stop`, `.server.lock`, `.server.ready`. The PID file is
not proof of liveness — `server status` probes the exclusive lock, so a stale
PID file is not reported as running. The lock file is retained across shutdown
and reused.

`--check` requires both the lock and the `.server.ready` marker, which is
written only after Spring Boot has finished starting the HTTP server. `--stop`
writes the stop file, waits up to 35 seconds for the graceful shutdown to
release the lock, and cleans the PID, stop, and ready files. Boot's shutdown
phase timeout is set to 25 seconds so it finishes below the control timeout.
`server-stop` is idempotent.

Logs live under `logs/`: `server.log` for the server (rotated to `server.log.1`
on every start) and `cli.log` for CLI lifecycle-control calls.
Each server run records the running JVM home, Java version, operating system,
and Arbiter root before initialization. Lifecycle entries use a standard
timestamp, level, PID, thread, logger, and message layout. Graceful shutdown
records the stop request, shutdown phase, and final stopped state.

Actuator exposes `health`, `info`, and `metrics` on the same port. Health and
info remain available to probes; `metrics` requires an admin
`ARBITER_TOKEN`. Health includes the user store, crypto material, and
Unix-socket listener (when enabled); details are hidden unless
`management.endpoint.health.show-details` is enabled.

On its first start the server also creates `conf/security/crypto.key`, the
AES-256 key used by `crypto encrypt` and `crypto decrypt`. The file is written
with owner-only permissions and must not be committed.

### Authentication and tokens

- On the first start the server creates `conf/security/users.dat` with an
  `admin` superuser and a random 256-bit password. The credentials are written
  once to `conf/security/initial-admin.txt` with owner-only permissions; delete
  that file after reading it. The user document contains PBKDF2-SHA256
  password hashes and is encrypted with AES-256-GCM using a purpose-specific
  key derived from `conf/security/crypto.key`. Later starts reuse the same
  files. Back up `users.dat` and `crypto.key` together; losing the key makes
  the user store unrecoverable. Delete `users.dat` to generate a new initial
  administrator.
- `FileUserRepository` uses the encrypted file as its sole source of truth and
  exposes add/update/delete operations for future administration APIs.
- Tokens are stateless HMAC-SHA256 values carrying subject, scope, issue time,
  key id, and expiry. The signing key is derived from `crypto.key` with a
  purpose-specific HKDF-SHA256 label, so user-store encryption and token
  signing never reuse the same raw key.
- `reader` or `admin` scope is enforced on `status` and `query`; `whoami`
  accepts any valid token and `*` is a wildcard.
- Login failures are rate limited (HTTP 429) with bounded state.
- Request bodies are size limited; errors use the structured API contract.
- Tokens include the active crypto key id. Key rotation keeps existing tokens
  valid while the old key remains in `crypto.key`.

Production deployments should implement `UserRepository` against their identity
store and keep a stable `conf/security/crypto.key`.

### TLS and mTLS

TLS is configured only in `conf/server.properties`. The key store and trust
store paths are fixed:

```text
<arbiter.home>/conf/security/keystore.p12
<arbiter.home>/conf/security/truststore.p12
```

`arbiter.server.tls.enabled=true` enables HTTPS.
`arbiter.server.tls.client.auth=true` enables mutual TLS: the server requires a
client certificate signed by the fixed trust store. Passwords come from
`arbiter.server.tls.keystore.password` and
`arbiter.server.tls.truststore.password`. mTLS is a transport guard; the
`ARBITER_TOKEN` login remains the application-level identity.

## Security

- Passwords and tokens never enter logs, exception text, JSON output, project
  config, or persisted metadata.
- Login debug logging replaces password values with `"***"`.
- `ARBITER_TOKEN` debug output is always `ARBITER_TOKEN: ***`.
- Known token/password values are removed from server messages before they reach
  a domain exception.
- `--password` is not accepted; only `--password-stdin` or an interactive prompt.
- `auth logout` does not mutate the environment and warns when `ARBITER_TOKEN`
  remains active.

## Transports

### HTTP

The base URL and `/api/v1` prefix are centralized:

| Operation | Method and path | `ARBITER_TOKEN` |
|---|---|---|
| Login | `POST /api/v1/auth/login` | No |
| Verify token | `GET /api/v1/auth/whoami` | Yes |
| Service status | `GET /api/v1/status` | Yes |
| Query resource | `GET /api/v1/query?name=<encoded>` | Yes |
| List users | `GET /api/v1/users` | Yes, admin |
| Show user | `GET /api/v1/users/{username}` | Yes, admin |
| Create user | `POST /api/v1/users` | Yes, admin |
| Update user | `PUT /api/v1/users/{username}` | Yes, admin |
| Delete user | `DELETE /api/v1/users/{username}` | Yes, admin |

Login request: `{"username":"alice","password":"server-password"}`. A successful
response must contain a non-empty `token`; `expiresAt`, `username`, and `scope`
are optional. Unknown response fields are ignored by the client DTOs. The
`status` response may contain `state`, `version`, and `serverTime`; the `query`
response may contain `name` and `value`. These two routes are the sample
business contract.

### Unix domain socket

The socket path is fixed at `<arbiter.home>/run/server.sock` and is not
configurable. Select it with `arbiter.transport=unix` (or `both`) in
`conf/common.properties`; the CLI picks it up from the same file, or from
`ARBITER_TRANSPORT=unix`.

The protocol is a 4-byte big-endian frame length followed by a JSON envelope that
reuses the same routes, DTOs, token header, error codes, and service layer as
HTTP. The socket is created with `0600` permissions where POSIX permissions
exist. TLS is only valid for the HTTP listener.

Starting either process outside the launchers requires
`-Darbiter.home=<distribution root>`; without it the socket path cannot be
derived.

### Authentication header

Every request except login carries:

```text
ARBITER_TOKEN: <raw server-issued token>
```

Header construction is centralized in `AuthHeaders.ARBITER_TOKEN` and
`AuthHeaders.withToken`; no service builds it independently. `auth status`
prints the source and a SHA-256 prefix, never the raw token.

## Architecture

```text
arbiter/
├── pom.xml                 # root parent and dependency management
├── arbiter-api/            # shared routes, headers, error codes, DTOs, ConfigKeys
├── arbiter-server/         # server family parent
│   ├── arbiter-control/    # Java 8 control plane (check/stop/help/version)
│   └── arbiter-console/    # Spring Boot REST/UDS runtime
├── arbiter-cli/            # CLI feature aggregator
│   ├── arbiter-core/       # ports, result records, exceptions, input ports (JDK only)
│   ├── arbiter-command/    # Picocli commands, rendering, help, i18n
│   ├── arbiter-client/     # ApiClient + HTTP/UDS transports, token store
│   ├── arbiter-shell/      # JLine assembly and terminal adapters
│   └── arbiter-cli-launcher/ # configuration, wiring, mode split
└── arbiter-distribution/   # tar.gz assembly and release layout
```

Dependency rules:

- `core` is JDK-only: ports, DTOs, exceptions, input ports. No Picocli.
- `command` adds Picocli, commands, rendering, help, and i18n.
- `client` depends on `core` + `api`, never on `command` or Picocli.
- `api` holds route constants, headers, DTOs, error codes, config keys, and the
  shared crypto layer (`CryptoKeys`, `CipherSuite`, `CipherSuites`,
  `CryptoEnvelope`) used by both processes.
- The server is a root-level module outside the CLI reactor.

Service ports:

```java
interface AuthService {
    LoginResult login(String username, String password);
    AuthStatus status();
    VerificationResult verify(TokenSource source);
    LogoutResult logout();
    boolean environmentTokenPresent();
}

interface SystemService { SystemStatus status(); }

interface QueryService { QueryResult query(String name); }
```

Commands parse arguments, call one port, produce a `CommandResult`, and hand it
to `ResultRenderer`. There is no global service locator; each feature receives
only its own port.

Commands are registered through `CommandModule` instances in feature packages
(`module.auth`, `module.query`, `module.status`, `module.server`):

```java
CommandRegistry.build(context, List.of(
    new AuthCommandModule(services::auth, secretInput),
    new ServiceStatusCommandModule(services::system),
    new QueryCommandModule(services::query),
    new ServerCommandModule(serverControl)));
```

`CommandRegistry` validates duplicate names, applies module order, and attaches
modules to the root. `CommandContext` carries input, renderer, signals,
environment, and the fixed language. Cross-cutting behavior lives in
`InteractionPolicy` (`--no-input`, prompts, retries, secret input) and
`CommandExecutionPipeline` (pre-execution configuration and future hooks).

Client internals: `ApiClient` owns JSON, retries, token headers, and error
mapping; `ApiTransport` has `HttpApiTransport` and `UnixSocketApiTransport`, so
switching transport does not change the command layer.

Server internals: `arbiter-server` is the server-family parent;
`arbiter-control` is its Java 8-compatible, Spring-free control module used by
both `server-start` and `server-stop`; `arbiter-console` is the Boot runtime.
`ArbiterServerApplication` is the Spring Boot 4 composition root. `controller/` contains one REST controller per resource, `service/` owns
login, token checks, user administration, status, and query behavior,
`security/` provides the token interceptor and response headers, and
`error/` maps exceptions to the API error contract. `UnixSocketApiServer`
remains an optional adapter; `UdsRequestDispatcher` maps framed UDS requests to
the same services as the HTTP controllers. The server is packaged as a thin
JAR plus `lib/3rd`, not a Boot executable JAR. Server settings are bound with
`@ConfigurationProperties`, and TLS material is exposed to Boot as an
`SslBundle`. `bin/server-start` dispatches `--check`, `--help`, and
`--version` to the Spring-free `ServerControl` on a minimal classpath; normal
startup goes to `ArbiterServerApplication`. `--stop` uses the Java 8 stop
launcher. Lightweight control operations therefore bypass Spring startup.

## Development

### Build and test

```bash
mvn clean verify
```

`mvn test` runs 133 tests covering API contracts, server routing and scopes,
command parsing and rendering, configuration precedence, WireMock integration
(token header, error mapping, retries, login persistence, whoami), TLS/mTLS
validation, registry duplicate detection, help coverage, `ErrorCode` stability,
and the framed Unix-socket transport.

Verification is always performed from the extracted `arbiter-<version>.tar.gz`,
not from `arbiter-distribution/target`, so the tested layout matches the release.

Requirements: Java 17+ (Maven enforcer), `--release 17`, tests also run on newer
JDKs. The launchers fail early with a clear message when the selected `java` is
older than 17.

### Version

The Maven version is CI-friendly and split in the parent POM:

```xml
<version.major>1</version.major>
<version.minor>0</version.minor>
<version.patch>0</version.patch>
<version.qualifier>-SNAPSHOT</version.qualifier>
<revision>${version.major}.${version.minor}.${version.patch}${version.qualifier}</revision>
```

Release builds can override `-Dversion.qualifier=` to drop the snapshot suffix.
The release file name is `arbiter-<version>.tar.gz` with no classifier.

Key versions:

| Component | Version |
|---|---|
| Java release | 17 |
| Picocli | 4.7.6 |
| `picocli-shell-jline3` | 4.7.6 |
| JLine | 3.26.1 |
| Spring Boot | 4.1.2 |
| Jackson | 3.1.7 (Jackson 2 annotation package 2.21) |
| Logback | Boot-managed 1.5.38 |
| WireMock (tests) | 3.9.1 |

### CI usage

Prefer an injected token:

```bash
export ARBITER_TOKEN="$(cat /run/secrets/service-token)"
bin/arbiter status
```

If CI must log in:

```bash
printf '%s\n' "$PASSWORD" | \
  bin/arbiter --no-input auth login \
  --username "$USERNAME" --password-stdin
```

The first form avoids exposing a password to the CLI. The second keeps the
password out of shell history and the process argument list, but still passes it
through a pipe.

### Deferred v2 work

- Platform-native credential stores (Keychain, Credential Manager, Secret
  Service).
- Object-value completion through a `CandidateProvider` with silent degradation.
- Shell language switching without restart.
- Pagination and streaming for large responses.
