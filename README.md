# Lønnsrapportering – Hendelsesdrevet lønnsrapporteringssystem (Java + Spring Boot + Kafka + MySQL)

> Hendelsesdrevet system for lønnsrapportering, automatisk skatteberegning og AI-støttet feildiagnostikk,
> bygget med Java 21, Spring Boot 3, Apache Kafka og MySQL.

---

## Oversikt

Et hendelsesdrevet backend-system som håndterer lønnsrapportering asynkront via Kafka.
Systemet mottar lønnsmeldinger via REST API, lagrer dem i MySQL, publiserer events til Kafka,
og sender automatisk feilmeldinger til LogSenseAI for AI-basert analyse.

Systemet er designet med fokus på:

- Løst koblet arkitektur via Kafka for asynkron meldingshåndtering
- Tydelig separasjon mellom API-lag, produsent og konsument
- DTO-basert API-grense — entiteter eksponeres aldri direkte
- Persistens med statussporing (PENDING → COMPLETED / FAILED) i MySQL
- Automatisk feilrapportering til LogSenseAI via Kafka
- React-frontend med statusvisning og direktelenke til LogSenseAI ved feil

---

## Arkitektur

```
React-frontend (port 3001)
      │
      │  POST /api/v1/payroll
      ▼
PayrollController
      │  PayrollRequest (DTO)
      ▼
PayrollService
      ├── validate()
      ├── map DTO → PayrollRecord-entitet
      ├── lagre PENDING → MySQL
      ├── PayrollProducer → Kafka topic: payroll-events
      └── send LogEvent  → Kafka topic: payroll-log-events
                                    │
                    ┌───────────────┘
                    ▼
Apache Kafka (delt broker med LogSenseAI)
      │                      │
      │ payroll-events        │ payroll-log-events
      ▼                      ▼
PayrollConsumer          LogSenseAI
      │                   (AI-analyse)
      ▼
Skatteberegning (lønn × 0.28)
      │
      ▼
MySQL (payrolldb) → status: COMPLETED / FAILED
```

---

## Integrasjon med LogSenseAI

Systemet sender automatisk log events til LogSenseAI via Kafka topic `payroll-log-events`:

| Event         | Nivå    | Når                        |
|---------------|---------|----------------------------|
| Lønn godkjent | `INFO`  | Vellykket innsendt         |
| Ugyldig data  | `WARN`  | Validering feilet          |
| Uventet feil  | `ERROR` | Exception under behandling |

`INFO`-events ignoreres av LogSenseAI. `WARN` og `ERROR` analyseres automatisk av AI.

```
payroll ERROR
      ↓
PayrollService → topic: payroll-log-events
      ↓
LogSenseAI consumer
      ↓
AgentService → LLM analyserer rotårsak
      ↓
Resultat lagres i PostgreSQL (LogSenseAI DB)
```

---

## Teknologistabel

| Lag               | Teknologi                      |
|-------------------|--------------------------------|
| Språk             | Java 21                        |
| Backend-rammeverk | Spring Boot 3, Spring Web      |
| Frontend          | React (Create React App)       |
| Meldingssystem    | Apache Kafka + Zookeeper       |
| Database          | MySQL 8 + Spring Data JPA      |
| Serialisering     | Jackson (StringSerializer)     |
| API-dokumentasjon | Swagger UI (SpringDoc OpenAPI) |
| Infrastruktur     | Docker + Docker Compose        |

---

## Nøkkelfunksjoner

### Hendelsesdrevet lønnsrapportering med persistens

`PayrollService` validerer innkommende `PayrollRequest` DTO, mapper den til en `PayrollRecord`-entitet,
lagrer i MySQL med status `PENDING`, publiserer til Kafka og oppdaterer til `COMPLETED` — eller `FAILED` ved feil.

**Statuslivssyklus:**

```
PENDING → COMPLETED
        → FAILED
```

### CorrelationId-basert sporing

Hver lønnsinnmelding tildeles en unik `correlationId` (UUID) av servicelaget.
React-klienten poller `GET /api/v1/payroll/{correlationId}` for å hente behandlingsresultatet.

### Automatisk skatteberegning

Skatten beregnes automatisk av Kafka-konsumenten og lagres i MySQL:

```
skatt = lønn × 0.28
```

### AI-støttet feildiagnostikk

Ved feil sendes en `LogEvent` til Kafka topic `payroll-log-events`.
LogSenseAI plukker opp meldingen og analyserer rotårsaken automatisk med Ollama LLM.

### React-frontend med statusvisning

- Viser `COMPLETED` / `FAILED` status etter innsending
- Ved feil: viser feilmelding + knapp "Se analyse i LogSenseAI"
- Historikktabell med alle innsendte rapporter i sesjonen

---

## Datamodell

### PayrollRecord

| Felt          | Type       | Beskrivelse                            |
|---------------|------------|----------------------------------------|
| id            | Long       | Auto-generert primærnøkkel             |
| correlationId | String     | UUID for sporing gjennom hele systemet |
| employeeId    | String     | Unik identifikator for ansatt          |
| salary        | BigDecimal | Bruttolønn for perioden                |
| month         | String     | Rapporteringsmåned (f.eks. "2025-01")  |
| tax           | BigDecimal | Beregnet skatt (lønn × 0.28)           |
| status        | Enum       | PENDING / COMPLETED / FAILED           |
| createdAt     | DateTime   | Tidspunkt for opprettelse              |
| completedAt   | DateTime   | Tidspunkt for fullføring eller feil    |

---

## API-referanse

### Send lønnsmelding

```http
POST /api/v1/payroll
Content-Type: application/json
```

**Forespørselskropp:**

```json
{
  "employeeId": "E001",
  "salary": 50000.00,
  "month": "2025-01"
}
```

**Svar — 202 Accepted:**

```json
{
  "correlationId": "b3f1c2d4-...",
  "status": "PENDING"
}
```

---

### Hent behandlingsresultat (polling)

```http
GET /api/v1/payroll/{correlationId}
```

**Svar — 200 OK:**

```json
{
  "correlationId": "b3f1c2d4-...",
  "employeeId": "E001",
  "salary": 50000.00,
  "month": "2025-01",
  "status": "COMPLETED",
  "createdAt": "2026-01-01T12:00:00",
  "completedAt": "2026-01-01T12:00:01"
}
```

**Eksempel med curl:**

```bash
curl -X POST http://localhost:8282/api/v1/payroll \
  -H "Content-Type: application/json" \
  -d '{"employeeId":"E001","salary":50000.00,"month":"2025-01"}'
```

---

## Prosjektstruktur

```
backend/
│
├── config/
│   ├── KafkaConfig.java                   # Kafka topic-definisjoner
│   ├── KafkaConfigLoader.java             # Aktiverer binding av Kafka-properties
│   ├── KafkaProperties.java               # Kafka topic- og consumer-properties
│   ├── SecurityConfig.java                # Spring Security + CORS
│   ├── SwaggerConfig.java                 # OpenAPI / Swagger UI
│   └── WebConfig.java                     # CORS-konfigurasjonskilden
│
├── controller/
│   └── PayrollController.java             # POST /api/v1/payroll + GET /{correlationId}
│
├── dto/
│   ├── PayrollRequest.java                # Innkommende API-forespørsel (employeeId, salary, month)
│   └── PayrollResponse.java               # Utgående API-svar (correlationId, status, osv.)
│
├── exception/
│   └── PayrollSerializationException.java # Kastes ved Kafka-serialiseringsfeil
│
├── model/
│   ├── PayrollRecord.java                 # @Entity: correlationId, salary, tax, status, timestamps
│   └── LogEvent.java                      # Logg-event: source, level, message, employeeId
│
├── repository/
│   └── PayrollRepository.java             # findByCorrelationId
│
├── service/
│   ├── PayrollService.java                # Valider → map DTO → lagre → publiser Kafka-event
│   ├── PayrollProducer.java               # Serialiserer og sender til payroll-events topic
│   └── PayrollConsumer.java               # @KafkaListener → skatteberegning → lagre COMPLETED/FAILED
│
└── Application.java                       # Spring Boot-applikasjonens startpunkt

frontend/
└── src/
    ├── App.js                             # Skjema, statusvisning, LogSenseAI-knapp, historikk
    ├── App.css                            # Styling
    └── config.js                          # API_URL, LOGSENSE_URL, POLL_INTERVAL, POLL_MAX
```

---

## Konfigurasjon

All konfigurasjon er eksternalisert i `application.yml`:

| Egenskap                 | Verdi                   |
|--------------------------|-------------------------|
| Server port              | `8282`                  |
| Kafka bootstrap          | `localhost:9092`        |
| Kafka topic (lønn)       | `payroll-events`        |
| Kafka topic (logg)       | `payroll-log-events`    |
| Consumer group           | `payroll-group`         |
| MySQL database           | `payrolldb`             |
| MySQL port               | `3306`                  |
| API base path            | `/api/v1/payroll`       |
| CORS tillatt opprinnelse | `http://localhost:3001` |

---

## Kom i gang

### Forutsetninger

- Java 21+
- Node.js 18+
- Docker og Docker Compose

### 1. Start infrastruktur

```bash
# Fra LogSenseAI-mappen (deler docker-compose med LogSenseAI)
docker-compose up -d
```

Starter PostgreSQL (LogSenseAI), MySQL (lønn), Kafka og Zookeeper.

### 2. Kjør backend

```bash
./mvnw spring-boot:run
```

API tilgjengelig på `http://localhost:8282`  
Swagger UI på `http://localhost:8282/swagger-ui.html`

### 3. Kjør frontend

```bash
cd frontend
npm install
npm start
```

React-appen tilgjengelig på `http://localhost:3001`

---

## Relasjon til LogSenseAI

Begge systemer deler Kafka-broker, men opererer uavhengig:

|                | lønnsrapportering                      | LogSenseAI                       |
|----------------|----------------------------------------|----------------------------------|
| Port           | `8282`                                 | `8080`                           |
| Frontend       | `3001`                                 | `3000`                           |
| Kafka topics   | `payroll-events`, `payroll-log-events` | `log-topic`                      |
| Consumer group | `payroll-group`                        | `log-group`, `logai-loenn-group` |
| Database       | MySQL (`payrolldb`)                    | PostgreSQL (`logdb`)             |
| AI-integrasjon | Sender events                          | Analyserer events                |

---

## Veikart

- [x] Persistens — lagre lønn og beregnet skatt i MySQL
- [x] Statussporing (PENDING → COMPLETED / FAILED)
- [x] CorrelationId-sporing gjennom hele systemet
- [x] DTO-lag — entiteter eksponeres aldri i API-grensen
- [x] Automatisk feilrapportering til LogSenseAI
- [x] React-frontend med statusvisning og LogSenseAI-knapp
- [x] Eksternalisert konfigurasjon via `application.yml`
- [ ] Autentisering med Spring Security + OAuth2
- [ ] Progressive skattesatser
- [ ] Unit- og integrasjonstester (JUnit 5 + Testcontainers)

---

## Om

Utviklet som et læringsprosjekt innen hendelsesdrevet arkitektur med Spring Boot og Kafka,
med fokus på producer/consumer-mønsteret, asynkron meldingshåndtering og integrasjon
mellom distribuerte systemer via Kafka.