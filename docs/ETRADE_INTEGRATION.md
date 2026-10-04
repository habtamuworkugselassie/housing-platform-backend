# eTrade integration (business details by TIN)

The Ministry of Trade's eTrade portal publishes a business licence checker at
`https://etrade.gov.et/business-license-checker?tin=<TIN>`. This module calls the JSON API behind
that page so the platform can show, and compare against, the official registration of an
organization from its Tax Identification Number (TIN).

## Our API

Both endpoints require a signed-in user (`AUTHENTICATED`).

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/etrade/businesses/{tin}` | Registration + every business licence under the TIN. `?includePhotos=true` adds managers' photos as `data:image/jpeg;base64,…` (hundreds of KB each). |
| `GET` | `/api/v1/etrade/organizations/{organizationId}/verification` | Looks up the organization's stored TIN and reports whether its name, licence number and business registration number match eTrade, and whether a licence is valid today. Admin, or the organization's primary contact / members. Nothing is persisted. |

Status codes: `400` malformed TIN (must be 10 digits; spaces, dashes, dots and slashes are
stripped) or integration disabled; `404` eTrade has no record; `403` not allowed to check that
organization; `502` eTrade unreachable or answered with something unexpected.

Example (`/api/v1/etrade/businesses/0081723672`):

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
  "businesses": [
    {
      "id": "1e18bf2a-6532-4249-b937-65ffbeb4cb54",
      "tradeName": "FIREW WONDE BELAY",
      "tradeNameAmharic": "ፍሬው ወንዴ በላይ",
      "licenseNumber": "YK/AA/14/672/5990336/2016",
      "dateRegistered": "2024-08-30",
      "renewalDate": "2026-09-02",
      "renewedFrom": "2026-07-08",
      "renewedTo": "2027-07-07",
      "licenseStatus": "ACTIVE",
      "sectors": [{ "code": 62222, "description": "Retail trade in beverage products" }]
    }
  ],
  "hasActiveLicense": true,
  "fetchedAt": "2026-10-04T11:05:40Z",
  "sourceUrl": "https://etrade.gov.et/business-license-checker?tin=0081723672"
}
```

`licenseStatus` is derived from `renewedFrom`/`renewedTo` against today's date: `ACTIVE`,
`EXPIRED`, `NOT_YET_VALID`, or `UNKNOWN` when eTrade gives no parseable dates (it sometimes returns
the literal text `Data not available`; that text is kept in the matching `*Raw` field).

## Upstream API (as observed, October 2026)

`GET https://etrade.gov.et/api/Registration/GetRegistrationInfoByTin/{tin}/{lang}`

* No authentication, but the request **must carry a `Referer` of an etrade.gov.et page**, otherwise
  the server answers `417 Invalid referer headerr`. We send
  `https://etrade.gov.et/business-license-checker`.
* `lang` is `en` or `am`. Only `en` returns Gregorian dates (`M/d/yyyy`); `am` returns the same
  record with Ethiopian-calendar dates, which we do not convert, so we always query `en`.
* `200` + JSON when the TIN is registered; `204` with an empty body when it is not (also for
  malformed TINs). Several all-zero style TINs (`0000000000`, `0000000123`) are real placeholder
  records upstream, so "looks fake" is not "not found".
* Response fields are PascalCase and keep upstream typos (`LegalCondtion`, `LicenceNumber`). The
  raw shape lives in `EtradeRegistrationResponse`; everything else sees `EtradeBusinessDetails`.
* Legal-form codes come from `GET /api/LegalStatus/{lang}`; the table is embedded in
  `EtradeLegalCondition` so we do not make a second call.
* The server is slow (2–20 s) and occasionally resets connections. Successful lookups are cached in
  Caffeine (`etradeBusinesses`, 6 h, 2 000 entries); misses are not cached.

### TLS: incomplete certificate chain

etrade.gov.et sends only its leaf certificate (issued by *GlobalSign GCC R3 EV TLS CA 2025*) and
omits the intermediate, so a stock JDK fails with `unable to find valid certification path` (curl:
`unable to get local issuer certificate`). The intermediate is bundled at
`src/main/resources/etrade/globalsign-gcc-r3-ev-tls-ca-2025.pem` (fetched from the leaf's AIA URL,
`http://secure.globalsign.com/cacert/gsgccr3evtlsca2025.crt`, valid until 2027-07-16) and added as
an extra trust anchor **for this client only**; the JVM's default trust store is unchanged and
hostname/validity checks still apply. If the server starts sending a full chain, set
`ETRADE_TRUST_BUNDLED_INTERMEDIATE=false`. If GlobalSign rotates the intermediate, replace the PEM
(the `EtradeTrustMaterialTest` asserts its subject and validity).

## Configuration

```yaml
etrade:
  enabled: ${ETRADE_ENABLED:true}
  base-url: ${ETRADE_BASE_URL:https://etrade.gov.et}
  referer: ${ETRADE_REFERER:https://etrade.gov.et/business-license-checker}
  language: ${ETRADE_LANGUAGE:en}
  connect-timeout: ${ETRADE_CONNECT_TIMEOUT:10s}
  read-timeout: ${ETRADE_READ_TIMEOUT:30s}
  trust-bundled-intermediate: ${ETRADE_TRUST_BUNDLED_INTERMEDIATE:true}
  intermediate-certificate: ${ETRADE_INTERMEDIATE_CERTIFICATE:classpath:etrade/globalsign-gcc-r3-ev-tls-ca-2025.pem}
```

## Code map

```
com.housingplatform.etrade
├── EtradeProperties                 config above
├── EtradeUnavailableException       -> 502 (GlobalExceptionHandler)
├── api/EtradeController             the two endpoints
├── client/EtradeClient              port
├── client/EtradeRestClient          RestClient over JDK HttpClient; Referer, 204/417 handling
├── client/EtradeTrustMaterial       SSLContext = JDK anchors + bundled intermediate
├── client/EtradeRegistrationResponse upstream wire record
├── dto/EtradeBusinessDetails        public shape (+ LicensedBusiness, Associate, Sector)
├── dto/EtradeLegalCondition         legal-form code table (en/am)
├── dto/EtradeOrganizationVerification
├── service/EtradeRegistrationLookup @Cacheable fetch
├── service/EtradeBusinessMapper     wire -> dto, date parsing, licence status
└── service/EtradeBusinessService    TIN normalisation, 404/403 rules, organization comparison
```

## Tests

* `EtradeRestClientTest` – MockRestServiceServer: parsing, Referer header, 204, 417, 5xx, HTML-200.
* `EtradeTrustMaterialTest` – the bundled CA is the right one and still valid.
* `EtradeBusinessMapperTest`, `EtradeBusinessServiceTest` – mapping, status, TIN rules, comparison.
* `EtradeLiveSmokeTest` – hits the real portal; skipped unless `ETRADE_LIVE_TEST=true`:

  ```bash
  ETRADE_LIVE_TEST=true ./mvnw -Dtest=EtradeLiveSmokeTest test
  ```

## Frontend

* `src/shared/api/etrade.ts` – typed client + `normalizeTin` + `etradeErrorMessage`.
* `src/shared/components/EtradeBusinessCard.vue` – renders a record (and match flags).
* Realtor dashboard, *Edit organization* → TIN field: **Look up on eTrade**, then **Use these
  details** fills business registration number, licence number and (if empty) the name.
* Admin → Organizations → view → *TIN registration* tab: **Check against eTrade** shows the record
  with name / licence / registration / active-licence match flags next to the document review.
