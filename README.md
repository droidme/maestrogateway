# MaestroGateway (Java)

A Spring Boot + Apache Camel gateway for **MCZ pellet stoves with Maestro technology**.

It speaks the stove's local WebSocket protocol and exposes the stove to Home Assistant,
openHAB or any other MQTT-capable home automation system. It is a Java port of the Python
[Chibald/maestrogateway](https://github.com/Chibald/maestrogateway) project, with the same MQTT
interface plus Home Assistant discovery, health endpoints and metrics.

> **Warning** — the protocol is reverse engineered. Use at your own risk. See the original
> disclaimer: this software comes with no warranty, and controlling a combustion appliance is
> inherently risky.

---

## What it does

```
┌──────────────┐    WebSocket (ws://192.168.120.1:81)    ┌──────────────────────────┐
│  MCZ stove   │ ◄─────────────────────────────────────► │ MaestroGateway (Java)    │
│ (Maestro)    │   C|RecuperoInfo, C|WriteParametri|…    │                          │
└──────────────┘                                         │  JDK WebSocket client    │
                                                         │        │                 │
                                                         │        ▼                 │
                                                         │  Camel routes            │
                                                         │  parse → de-duplicate    │
                                                         │        │                 │
┌──────────────┐               MQTT 5                     │        ▼                 │
│ MQTT broker  │ ◄─────────────────────────────────────► │  Paho MQTT 5 producer    │
│ (Mosquitto,  │   Maestro/<Field>, Maestro/Command/#    │                          │
│  HA add-on)  │                                         │  /actuator/health        │
└──────────────┘                                         └──────────────────────────┘
```

* Connects to the stove's access point (`192.168.120.1:81` by default).
* Polls `C|RecuperoInfo` every 15 seconds and parses the pipe-separated hexadecimal frame.
* Publishes only **changed** values, so MQTT is not flooded.
* Accepts commands on `Maestro/Command/<Name>` and translates them to
  `C|WriteParametri|…` / `C|Diagnostica|…` / `C|SalvaDataOra|…`.
* Reconnects automatically, with an optional `disconnected` status after a configurable number of
  failed attempts.
* Optionally publishes **Home Assistant MQTT discovery** (sensors, switches, numbers, buttons).
* Exposes Spring Boot Actuator **health** and **Micrometer metrics**.

---

## Requirements

| Component | Version |
|---|---|
| Java | 21 (LTS) |
| Spring Boot | 4.1.x |
| Apache Camel | 4.23.x |
| MQTT broker | **MQTT 5** capable (Mosquitto 2.x, EMQX, HiveMQ, HA Mosquitto add-on) |
| Stove network | a host with Wi-Fi connected to the stove's AP and a second interface to your LAN |

The gateway uses Camel's `paho-mqtt5` component. If you must keep an MQTT 3.1.1-only broker,
the Python gateway (or a broker upgrade) is the safer choice.

---

## Quick start

### Local (Maven)

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--maestro.mqtt.broker-url=tcp://192.168.1.10:1883 \
                               --maestro.stove.host=192.168.120.1"
```

Or build a jar:

```bash
mvn clean package
java -jar target/maestrogateway-1.0.0-SNAPSHOT.jar \
  --maestro.mqtt.broker-url=tcp://192.168.1.10:1883
```

Configuration can also be supplied entirely through environment variables:

```bash
MAESTRO_MQTT_BROKER_URL=tcp://192.168.1.10:1883 \
MAESTRO_STOVE_HOST=192.168.120.1 \
java -jar target/maestrogateway-1.0.0-SNAPSHOT.jar
```

### Docker

```bash
cp .env.example .env   # edit broker and stove addresses
docker compose up -d --build
docker compose logs -f maestrogateway
```

`network_mode: host` is used so the container can reach the stove's access point. Host networking
works on Linux; on macOS/Windows use a Linux VM or run the jar directly.

---

## Configuration

All properties live under `maestro.*` and can be overridden with environment variables
(relaxed binding: `maestro.mqtt.broker-url` → `MAESTRO_MQTT_BROKER_URL`).

### MQTT

| Property | Env var | Default | Description |
|---|---|---|---|
| `maestro.mqtt.enabled` | `MAESTRO_MQTT_ENABLED` | `true` | Disable MQTT entirely (offline development/tests) |
| `maestro.mqtt.broker-url` | `MAESTRO_MQTT_BROKER_URL` | `tcp://localhost:1883` | Broker URL |
| `maestro.mqtt.client-id` | `MAESTRO_MQTT_CLIENT_ID` | `mcz-stove` | Base client id (`-sub`/`-pub` are appended) |
| `maestro.mqtt.username` | `MAESTRO_MQTT_USERNAME` | – | Broker username |
| `maestro.mqtt.password` | `MAESTRO_MQTT_PASSWORD` | – | Broker password |
| `maestro.mqtt.payload-type` | `MAESTRO_MQTT_PAYLOAD_TYPE` | `TOPIC` | `TOPIC` (one topic per value) or `JSON` (one document) |
| `maestro.mqtt.publish-prefix` | `MAESTRO_MQTT_PUBLISH_PREFIX` | `Maestro` | Prefix for state topics |
| `maestro.mqtt.command-prefix` | `MAESTRO_MQTT_COMMAND_PREFIX` | `Maestro/Command` | Prefix for command topics |
| `maestro.mqtt.qos` | `MAESTRO_MQTT_QOS` | `1` | QoS for publish and subscribe |
| `maestro.mqtt.start-retry-interval-ms` | `MAESTRO_MQTT_START_RETRY_INTERVAL_MS` | `10000` | Retry interval while the broker is unreachable at startup |

### Stove

| Property | Env var | Default | Description |
|---|---|---|---|
| `maestro.stove.auto-connect` | `MAESTRO_STOVE_AUTO_CONNECT` | `true` | Connect when the application is ready |
| `maestro.stove.host` | `MAESTRO_STOVE_HOST` | `192.168.120.1` | Stove IP address |
| `maestro.stove.port` | `MAESTRO_STOVE_PORT` | `81` | Stove WebSocket port |
| `maestro.stove.path` | `MAESTRO_STOVE_PATH` | `/` | WebSocket path |
| `maestro.stove.connect-timeout` | `MAESTRO_STOVE_CONNECT_TIMEOUT` | `10s` | Connection timeout |
| `maestro.stove.poll-interval-ms` | `MAESTRO_STOVE_POLL_INTERVAL_MS` | `15000` | Information poll interval |
| `maestro.stove.ping-interval-ms` | `MAESTRO_STOVE_PING_INTERVAL_MS` | `5000` | WebSocket ping interval |
| `maestro.stove.read-timeout-ms` | `MAESTRO_STOVE_READ_TIMEOUT_MS` | `45000` | Reconnect when no frame arrived for this long |
| `maestro.stove.reconnect-delay-ms` | `MAESTRO_STOVE_RECONNECT_DELAY_MS` | `2000` | Delay between reconnect attempts |
| `maestro.stove.alert-after-failed-reconnects` | `MAESTRO_STOVE_ALERT_AFTER_FAILED_RECONNECTS` | `5` | Failed attempts before `disconnected` is published |

### Home Assistant / health

| Property | Default | Description |
|---|---|---|
| `maestro.home-assistant.enabled` | `true` | Publish MQTT discovery documents |
| `maestro.home-assistant.discovery-prefix` | `homeassistant` | HA discovery prefix |
| `maestro.home-assistant.device-id` | `mcz_stove` | Device identifier and topic suffix |
| `maestro.home-assistant.device-name` | `MCZ Stove` | Display name |
| `maestro.home-assistant.manufacturer` / `.model` | `MCZ` / `Maestro pellet stove` | Device metadata |
| `maestro.home-assistant.refresh-interval-ms` | `3600000` | Republish retained discovery documents (survives broker restarts losing retained state) |
| `maestro.health.fail-on-stove-disconnect` | `false` | Report health `DOWN` when the stove is not connected |
| `maestro.health.stale-after` | `60s` | Mark the stove stale after this much silence |

---

## MQTT interface

### Published state (`TOPIC` mode, default)

| Topic | Payload |
|---|---|
| `Maestro/Ambient_Temperature` | `23.5` |
| `Maestro/Stove_State` | `11` (see state table below) |
| `Maestro/Power` | `1` / `0` (derived from `Stove_State`) |
| `Maestro/Diagnostics` | `1` / `0` |
| `Maestro/Fume_Temperature` | `155` |
| `Maestro/Total_Operating_Hours` | `307:00:03` |
| `Maestro/state` | `ON` (heartbeat, published every poll while connected) |
| `Maestro/Status` | `connected` / `disconnected` |

The full field list matches the Python gateway (61 information fields). Only changed values are
published. Unknown/new firmware fields are published as `Unknown<n>`.

### Published state (`JSON` mode)

Everything is published as one JSON object on `Maestro`:

```json
{"Stove_State":11,"Power":1,"Ambient_Temperature":23.5,"Fume_Temperature":155}
```

Connection status is published as `{"Status":"connected"}` / `{"Status":"disconnected"}` on the same
topic. The `Maestro/state = ON` heartbeat keeps its own topic in both payload modes, matching the
original gateway.

### Commands

`TOPIC` mode — payload is the value:

```bash
mosquitto_pub -t 'Maestro/Command/Power' -m '1'                  # on
mosquitto_pub -t 'Maestro/Command/Power' -m '0'                  # off
mosquitto_pub -t 'Maestro/Command/Temperature_Setpoint' -m '20.5'
mosquitto_pub -t 'Maestro/Command/Power_Level' -m '3'
mosquitto_pub -t 'Maestro/Command/Eco_Mode' -m '1'
mosquitto_pub -t 'Maestro/Command/Refresh' -m '1'                # clear change cache
mosquitto_pub -t 'Maestro/Command/GetInfo' -m '1'                # poll immediately
mosquitto_pub -t 'Maestro/Command/Set_DateTime' -m 'NOW'         # sync stove clock
```

`JSON` mode — publish to `Maestro/Command`:

```json
{"Command":"Power","Value":1}
{"Command":"Temperature_Setpoint","Value":20.5}
{"Command":"Refresh","Value":1}
```

Supported command names:

| Group | Commands |
|---|---|
| Basic | `Power`, `Power_Level`, `Temperature_Setpoint`, `Boiler_Setpoint`, `Fan_State`, `DuctedFan1`, `DuctedFan2`, `Eco_Mode`, `Silent_Mode`, `Active_Mode`, `Control_Mode`, `Chronostat`, `Chronostat_T1`…`T3`, `Profile`, `Sleep`, `Summer_Mode`, `Pellet_Sensor`, `Adaptive_Mode`, `AntiFreeze`, `Sound_Effects`, `Celsius_Fahrenheit`, `Feeding_Screw`, `Reset_Alarm`, `Reset_Active` |
| Diagnostics | `Diagnostics`, `RPM_Fam_Fume`, `RPM_WormWheel`, `Active`, `Ignitor`, `FrontFan`, `Pump_PWM`, `3wayvalve`, `Diagnostics_DuctedFan1`, `Diagnostics_DuctedFan2` |
| Date/time | `Set_DateTime` (`NOW` or `ddMMyyyyHHmm`) |
| Daemon | `Refresh`, `GetInfo` |

Diagnostics commands only work while the stove is **off** and in diagnostics mode. `Reset_Alarm`
and `Reset_Active` expect `255`.

---

## Home Assistant discovery

With `maestro.home-assistant.enabled=true` the gateway publishes retained discovery documents to
`homeassistant/<component>/mcz_stove_<object>/config` for:

* every information field as a `sensor` (or `binary_sensor` for on/off fields), with units and
  device classes where known;
* switches: power, eco mode, silent mode, active mode, chronostat, sleep, sound effects,
  diagnostics;
* numbers: target temperature (5–40 °C, 0.5 steps), boiler setpoint (30–80 °C), power level (1–5),
  fan speed (0–6);
* buttons: reset alarm, sync clock.

All entities share one device (`identifiers: ["mcz_stove"]`). Discovery is retried until the broker
accepts it, then refreshed hourly (`maestro.home-assistant.refresh-interval-ms`) so a broker restart
that loses retained state is recovered automatically.

---

## Health and metrics

| Endpoint | Purpose |
|---|---|
| `GET /actuator/health` | Aggregate health (includes the `maestro` indicator) |
| `GET /actuator/health/maestro` | Stove connection, last frame age, MQTT publish age, queue size |
| `GET /actuator/metrics` | Micrometer metrics |

Custom metrics:

* `maestro.stove.connected`
* `maestro.stove.last_frame.age.seconds`
* `maestro.mqtt.last_publish.age.seconds`
* `maestro.command.queue.size`
* `maestro.frames.received` / `maestro.frames.parse.errors`
* `maestro.commands.received` / `maestro.commands.sent`
* `maestro.mqtt.publish.success` / `maestro.mqtt.publish.failures`

A disconnected stove is reported as `UP` with details by default; set
`maestro.health.fail-on-stove-disconnect=true` for strict `DOWN` behaviour.

---

## Differences from the Python gateway

The MQTT contract (topic names, command names, payload modes, 15 s polling, change-only publishing,
`Maestro/state` heartbeat, `Maestro/Status`) is preserved. Intentional improvements:

| Area | Python | Java |
|---|---|---|
| WebSocket client | `websocket-client` thread loop | JDK `HttpClient` WebSocket, scheduled ping and watchdog |
| MQTT | Paho 1.5 / MQTT 3.1.1 | Camel Paho MQTT 5, single publisher connection |
| Broker down at startup | daemon fails | subscriber route starts when the broker becomes reachable |
| Invalid frame token | exception | token skipped, warning logged, frame still processed |
| Duplicate diagnostics names | diagnostics `DuctedFan1` unreachable | `Diagnostics_DuctedFan1` / `Diagnostics_DuctedFan2` aliases |
| Home Assistant | not supported | MQTT discovery |
| Observability | log file / journald | Actuator health + Micrometer metrics |
| Configuration | `_config_.py` | `application.yaml` + env vars |

The JSON publish topic is `Maestro` (the Python version used `Maestro/`, with a trailing slash).
The command subscription is `Maestro/Command/#` in both modes, so a JSON command published at
`Maestro/Command` also works.

---

## Development

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn test          # 55 unit and route tests
mvn spring-boot:run
```

Tests cover frame parsing (temperatures, timespans, special fields, invalid tokens, unknown
firmware fields), command encoding, command registry aliases, change detection, command debounce,
MQTT topic building, MQTT publishing (topic/JSON payloads, heartbeat, retained discovery),
Home Assistant discovery documents, and the Camel frame route.

To run the application without a stove or broker:

```bash
java -jar target/maestrogateway-1.0.0-SNAPSHOT.jar \
  --maestro.stove.auto-connect=false \
  --maestro.mqtt.enabled=false \
  --maestro.home-assistant.enabled=false
```

---

## Troubleshooting

| Symptom | Check |
|---|---|
| `Websocket: connection ... failed` | Can the host ping `192.168.120.1`? Is the stove's Wi-Fi AP connected? |
| No MQTT messages | Is the broker MQTT 5 capable? Check `MAESTRO_MQTT_BROKER_URL`, credentials and `Maestro/Status`. |
| `MQTT: broker ... not reachable yet` | The subscriber retries every 10 s; the gateway keeps polling the stove. |
| Commands ignored | Diagnostics commands require the stove to be off and in diagnostics mode. Check the logs for the encoded string. |
| No Home Assistant entities | Ensure MQTT discovery is enabled in HA, the discovery prefix matches, and the gateway could publish retained messages. |
| Stove state stale | `/actuator/health/maestro` shows the last frame age; the watchdog reconnects after 45 s of silence. |

---

## License

GPL-3.0. This is a derivative work of
[Chibald/maestrogateway](https://github.com/Chibald/maestrogateway), which is based on a script by
[Anthony L](https://github.com/Anthony-55/maestro). See `LICENSE` and `NOTICE`.

Not affiliated with MCZ. "Maestro" is a trademark of its owner.
