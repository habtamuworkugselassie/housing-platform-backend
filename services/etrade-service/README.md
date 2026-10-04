# etrade-service

Standalone Spring Boot service that looks up Ethiopian businesses on the Ministry of Trade
**eTrade** portal by Tax Identification Number (TIN). It wraps the JSON API behind
`https://etrade.gov.et/business-license-checker?tin=<TIN>` and hides that portal's quirks from the
rest of the platform. No database; in-memory cache only.

## API

| Method | Path | Notes |
| --- | --- | --- |
| `GET` | `/api/v1/businesses/{tin}` | Registration + every licence under the TIN. `?includePhotos=true` adds managers' photos as data URLs. |
| `GET` | `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` | Open, for probes. |
| `GET` | `/swagger-ui.html` | OpenAPI docs (`SWAGGER_ENABLED=false` to hide). |

Every `/api/**` call must send `X-API-Key: <one of ETRADE_SERVICE_API_KEYS>`; with no keys configured
the API is open (private network only).

Status codes: `400` TIN not ten digits · `401` bad/missing key · `404` eTrade has no record ·
`502` eTrade unreachable or unparseable. Error body:
`{"timestamp","status","error","message","path"}`.

```bash
curl -H 'X-API-Key: dev' http://localhost:8085/api/v1/businesses/0081723672
```

```json
{
  "tin": "0081723672",
  "businessName": "FIREW WONDE BELAY",
  "businessNameAmharic": "ፍሬው ወንዴ በላይ",
  "legalCondition": { "code": "1", "name": "Sole Proprietorship", "nameAmharic": "ግለሰብ ነጋዴ" },
  "registrationNumber": "AA/LK/14/1/3631426/2016",
  "registrationDate": "2022-12-01",
  "paidUpCapital": 500.0,
  "associates": [{ "name": "FIREW WONDE", "nameAmharic": "ፍሬው ወንዴ" }],
  "businesses": [{
    "id": "1e18bf2a-6532-4249-b937-65ffbeb4cb54",
    "tradeName": "FIREW WONDE BELAY",
    "licenseNumber": "YK/AA/14/672/5990336/2016",
    "dateRegistered": "2024-08-30",
    "renewalDate": "2026-09-02",
    "renewedFrom": "2026-07-08",
    "renewedTo": "2027-07-07",
    "licenseStatus": "ACTIVE",
    "sectors": [{ "code": 62222, "description": "Retail trade in beverage products" }]
  }],
  "hasActiveLicense": true,
  "fetchedAt": "2026-10-04T11:05:40Z",
  "sourceUrl": "https://etrade.gov.et/business-license-checker?tin=0081723672"
}
```

`licenseStatus` is `ACTIVE`, `EXPIRED`, `NOT_YET_VALID` or `UNKNOWN` (eTrade sometimes returns the
text `Data not available` for a date; it is kept in the matching `*Raw` field).

## Configuration (environment variables)

| Variable | Default | Meaning |
| --- | --- | --- |
| `PORT` | `8085` | Listen port |
| `ETRADE_SERVICE_API_KEYS` | *(empty)* | Comma-separated accepted keys; several allow rotation |
| `ETRADE_CACHE_TTL` | `6h` | How long a successful lookup is reused |
| `ETRADE_CACHE_MAX_SIZE` | `5000` | Max cached TINs |
| `ETRADE_BASE_URL` | `https://etrade.gov.et` | Portal origin |
| `ETRADE_REFERER` | `https://etrade.gov.et/business-license-checker` | eTrade answers 417 without an etrade.gov.et Referer |
| `ETRADE_LANGUAGE` | `en` | `en` = Gregorian dates; `am` = Ethiopian calendar (not parsed) |
| `ETRADE_CONNECT_TIMEOUT` / `ETRADE_READ_TIMEOUT` | `10s` / `30s` | Upstream timeouts |
| `ETRADE_TRUST_BUNDLED_INTERMEDIATE` | `true` | See TLS below |
| `SWAGGER_ENABLED` | `true` | Swagger UI / OpenAPI |

### TLS: etrade.gov.et sends an incomplete chain

The portal omits its *GlobalSign GCC R3 EV TLS CA 2025* intermediate, so a stock JDK cannot build
the chain. The intermediate is bundled at `src/main/resources/etrade/globalsign-gcc-r3-ev-tls-ca-2025.pem`
(from the leaf's AIA URL, valid until 2027-07-16) and trusted **for this client only**; hostname and
validity checks still apply. `EtradeTrustMaterialTest` fails when the bundled CA expires.

## Run

```bash
./mvnw spring-boot:run                                    # http://localhost:8085
ETRADE_SERVICE_API_KEYS=dev ./mvnw spring-boot:run        # with a key
docker build -t etrade-service . && docker run -p 8085:8085 -e ETRADE_SERVICE_API_KEYS=dev etrade-service
docker compose up etrade-service                          # from the repository root
```

Tests: `./mvnw verify` (unit + MockMvc; also runs the formatter check). Against the real portal:
`ETRADE_LIVE_TEST=true ./mvnw -Dtest=EtradeLiveSmokeTest test`.

## Deployment

`.github/workflows/build-etrade-service.yml` tests on every change under `services/etrade-service`
and, on `main`, pushes `ghcr.io/<owner>/housing-platform-backend/etrade-service:{latest,<sha>}`. Add
it to the server's compose file next to the backend and point the backend at it:

```yaml
etrade-service:
  image: ghcr.io/<owner>/housing-platform-backend/etrade-service:latest
  environment:
    ETRADE_SERVICE_API_KEYS: ${ETRADE_SERVICE_API_KEYS}
backend:
  environment:
    ETRADE_SERVICE_URL: http://etrade-service:8085
    ETRADE_SERVICE_API_KEY: ${ETRADE_SERVICE_API_KEYS}   # one of them
```

The service lives in this repository under `services/` for now; it has no code dependency on the
backend, so it can be moved to its own repository by copying the directory and the workflow.
