# AGENTS.md

## Project

Single-module Spring Boot 4.1 + Apache Camel 4.23 gateway between an MCZ Maestro pellet stove's
WebSocket and a JSON-only MQTT 5 interface. Java 21 target, Maven (no wrapper, no monorepo, no
frontend).

## Commands

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS; pom targets Java 21
mvn test                                           # 52 tests, fully offline
mvn -Dtest=InfoFrameParserTest test                # single class
mvn -Dtest='InfoFrameParserTest#parsesShortFrames' test
mvn clean package                                  # fat jar: target/maestrogateway-1.0.0-SNAPSHOT.jar
mvn spring-boot:run
```

There is no Maven wrapper, CI, lint, formatter, or typecheck step; `mvn test` is the verification gate.

Run without stove or broker:

```bash
java -jar target/maestrogateway-1.0.0-SNAPSHOT.jar \
  --maestro.stove.auto-connect=false --maestro.mqtt.enabled=false --maestro.home-assistant.enabled=false
```

Docker: `mvn compile jib:dockerBuild` builds locally into Docker with Jib. Run with `docker compose up -d`. `network_mode: host` is required on Linux because the stove
is reached at its own AP address; macOS/Windows need a Linux VM or the jar directly.

## Wiring

- `MaestroWebSocketClient` owns the stove connection (JDK `HttpClient` WebSocket, scheduled ping and
  silence watchdog). Complete `01|...` frames go through `CamelFrameSink` into `direct:maestroFrame`.
- Route `stove-frame`: `FrameProcessor` -> `ChangeFilterProcessor` -> `InfoPublishProcessor`.
- Route `stove-poll` (default 15 s) enqueues `GetInfo` and publishes the `Maestro/state=ON` heartbeat.
- Commands: JSON on `Maestro/Command` -> `CommandIngestProcessor` -> `CommandQueue` (dedupes by
  command name, latest value wins) -> single sender thread encodes with `CommandEncoder` at send time.
  Encoding at send time is deliberate: `Set_DateTime=NOW` must use the current clock.
- `MqttRouteSupervisor` starts the `mqtt-command` route, which is declared `autoStartup=false` because
  the Paho consumer throws during route start when the broker is down. Keep the retry loop rather than
  making the route auto-start.
- `MqttPublisher` reuses one producer endpoint (`paho-mqtt5:mcz-publish`) and sets
  `CamelPahoMqtt5OverrideTopic` per message; do not create one producer per topic.

## Protocol and MQTT contract

- Information frames are pipe-separated hex tokens. `InformationRegistry`'s index is the frame
  position (index 0 is the message type and is never published). Adding a field there also adds it to
  Home Assistant discovery.
- `TEMPERATURE` = raw / 2.0; `TIMESPAN` = `H:MM:SS`; `3WayValve`/`Brazier` map to text. Invalid hex
  tokens are skipped with a metric and must never fail the whole frame. `Power` and `Diagnostics` are
  derived from `Stove_State` in `InfoFrameParser`.
- Encoding: `TEMPERATURE` x2; `ON_OFF` -> 1/0; `ON_OFF_40` -> 1 on, **40 off** (`Power`);
  `PERCENTAGE` clamped 0-100; diagnostics commands use `C|Diagnostica|`, basic commands use
  `C|WriteParametri|`; `GetInfo` -> `C|RecuperoInfo`; `Set_DateTime` ->
  `C|SalvaDataOra|ddMMyyyyHHmm`.
- Name collision: basic `DuctedFan1` keeps the short name; diagnostics variants are
  `Diagnostics_DuctedFan1` / `Diagnostics_DuctedFan2`. Other aliases live in `CommandRegistry`.
- MQTT is JSON-only. State and status go to `topics.json()` (`Maestro`); commands are
  `{"Command":"Power","Value":1}` on exactly `Maestro/Command`; `Maestro/state` remains a plain
  heartbeat topic. There is no payload-type switch or per-field topic mode.
- Home Assistant discovery reads state from `Maestro` via `value_template`; controls publish JSON
  command payloads/templates to `Maestro/Command`.

## Config and Spring notes

- All settings live under `maestro.*` in `src/main/resources/application.yaml` and are overridable by
  relaxed-bound env vars (`maestro.mqtt.broker-url` -> `MAESTRO_MQTT_BROKER_URL`).
  `MaestroProperties` is an immutable record tree, so constructor binding is required.
- `TestProperties` binds the real `application.yaml`; put production defaults there. Tests fail if a
  default disappears, so do not add test-only defaults elsewhere.
- Spring Boot 4 moved health types to `org.springframework.boot.health.contributor.Health` /
  `HealthIndicator` (not the old `org.springframework.boot.actuate.health` package).
- The `camel-dataset-starter` / `camel-mock-starter` exclusions in `pom.xml` are intentional. Those
  classes reference `camel-mock`, which is not on the runtime classpath and breaks the fat jar with
  `NoClassDefFoundError: MockComponent`. Do not remove them.

## Tests

- `FrameFixtures.frame(...)` values start at frame index 1 (`Stove_State`), not index 0.
- `MqttPublisherTest` mocks `ProducerTemplate`/`ObjectProvider`; `@SpringBootTest` disables stove
  auto-connect, MQTT, and discovery. The full suite needs no broker or hardware.
- Command tests publish JSON bodies only; there is no topic-suffix command mode.
