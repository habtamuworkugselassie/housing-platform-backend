# Property Purchase Orders with Optional Bank Financing

Design for a new `purchase` module that lets a buyer place a purchase order on a
property. At creation time the service inspects the property's product catalog
(the `financing_offers` linked to the property) and, when an **active** offer
backed by an **active** `CreditProduct` exists, embeds the bank-financing
workflow in the order. Otherwise the order runs as a plain cash / direct
purchase.

Everything below is grounded in what already exists in this code base:

| Concept | Existing type | Notes |
| --- | --- | --- |
| Property listing | `property.domain.Property` | `priceETB` / `priceUSD`, `status`, `category`, `building`, `realEstateCompanyId`, `agentId` |
| Bank product catalog | `banking.domain.CreditProduct` | `interestRate`, `maxLoanToValueRatio`, `min/maxTenureMonths`, `min/maxLoanAmount`, `status` |
| Product linked to a property | `banking.domain.FinancingOffer` | `propertyId` or `buildingId`, `creditProductId`, `bankId`, optional `specialInterestRate` / `specialLTVRatio`, `status` |
| Loan workflow | `loan.domain.LoanApplication` (+ status history) | Created via `LoanApplicationService.createLoanApplication(buyerId, request)` |
| Auth | `@AuthPolicyScope` + `@AuthActionScope` | `BUYER_SECURED`, `REALTOR_SECURED`, `BANKER_SECURED` |
| Errors | `ErrorResponse` via `GlobalExceptionHandler` | 400 `BusinessException`, 404 `ResourceNotFoundException` |

---

## 1. Domain model

### 1.1 `PropertyPurchaseOrder` (aggregate root) — table `property_purchase_orders`

| Column | Type | Rules |
| --- | --- | --- |
| `id` | UUID PK | from `BaseAuditEntity` |
| `order_number` | VARCHAR(32) UNIQUE | `PPO-YYYY-NNNNNN`, generated from a DB sequence |
| `property_id` | UUID FK `properties` | required |
| `buyer_id` | UUID FK `users` | the authenticated caller |
| `real_estate_company_id`, `agent_id` | UUID | **snapshot** from the property at creation, so seller-side listing does not depend on later property edits |
| `contact_phone` | VARCHAR(20) | **required**, normalised to E.164 (`+2519XXXXXXXX`) |
| `contact_email` | VARCHAR(255) | optional, lower-cased |
| `purchase_type` | VARCHAR(20) | `CASH` or `BANK_FINANCED`. **Derived by the service, never supplied by the client** |
| `status` | VARCHAR(32) | see state machine below |
| `listed_price` | NUMERIC(19,2) | snapshot of the property price in `currency` |
| `currency` | VARCHAR(3) | `ETB` (default) or `USD` |
| `buyer_message` | TEXT | optional free text |
| `expires_at` | TIMESTAMP | `created_at + 14 days` while in `PENDING_SELLER_REVIEW` |
| `cancellation_reason`, `rejection_reason` | TEXT | set on terminal transitions |
| `created_at`, `updated_at`, `created_by`, `updated_by`, `version` | | base entity |

### 1.2 `PurchaseOrderFinancing` (1:1, only when `purchase_type = BANK_FINANCED`) — table `purchase_order_financing`

| Column | Rules |
| --- | --- |
| `purchase_order_id` | UUID PK / FK, `ON DELETE CASCADE` |
| `financing_offer_id`, `bank_id`, `credit_product_id` | which catalog entry was applied |
| `offer_level` | `PROPERTY` or `BUILDING` (where the offer was found) |
| `applied_interest_rate` | `offer.specialInterestRate ?? product.interestRate` (snapshot) |
| `applied_ltv_ratio` | `offer.specialLTVRatio ?? product.maxLoanToValueRatio` (snapshot) |
| `max_financeable_amount` | `min(listed_price × applied_ltv_ratio, product.maxLoanAmount)` |
| `min_financeable_amount` | `product.minLoanAmount` (snapshot) |
| `financing_mode` | `MAXIMUM` (financed amount = max financeable) or `PARTIAL` (buyer chose to finance less and pay more cash) |
| `financed_amount` | requested loan principal, anywhere in `[min_financeable_amount, max_financeable_amount]` (see rules in §3.3) |
| `cash_portion_amount` | `listed_price − financed_amount`; the buyer's own contribution (down payment). Equals the minimum down payment in `MAXIMUM` mode, larger in `PARTIAL` mode |
| `financing_coverage_ratio` | `financed_amount ÷ listed_price`, scale 4, informational (e.g. `0.5000` for 50 % financed) |
| `tenure_months` | requested or default tenure |
| `estimated_monthly_installment` | standard amortised instalment, informational only |
| `loan_application_id` | FK `loan_applications`, the workflow handle |
| `financing_status` | `APPLICATION_SUBMITTED`, `UNDER_REVIEW`, `APPROVED`, `PARTIALLY_APPROVED`, `REJECTED`, `DISBURSED`, `WITHDRAWN` |

Snapshotting the rate / LTV is deliberate: banks can edit or deactivate a
product later and the buyer must keep the terms they were shown.

### 1.3 `PurchaseOrderStatusHistory` — table `purchase_order_status_history`

Same shape as `LoanApplicationStatusHistory`: `from_status`, `to_status`,
`changed_by`, `changed_at`, `notes`.

### 1.4 Order state machine

```
                       ┌──────────────────────────┐
  create ─────────────►│  PENDING_SELLER_REVIEW   │──── expires_at passed ───► EXPIRED
                       └───────────┬──────────────┘
          seller reject            │ seller accept                buyer cancel ──► CANCELLED
              ▼                    ▼                              (allowed from any
           REJECTED     ┌──────────────────────┐                   non-terminal state)
                        │ purchase_type = CASH │──► AWAITING_PAYMENT ──► COMPLETED
                        └──────────────────────┘
                        ┌──────────────────────────────┐
                        │ purchase_type = BANK_FINANCED│
                        └──────────────┬───────────────┘
                                       ▼
                             AWAITING_FINANCING ──(loan APPROVED in full)──► FINANCING_APPROVED ──► AWAITING_PAYMENT ──► COMPLETED
                                       │
                                       ├──(loan APPROVED for less)──► FINANCING_PARTIALLY_APPROVED ──(buyer accepts larger cash portion)──► FINANCING_APPROVED
                                       │                                        └──(buyer declines)──► CANCELLED
                                       │
                                       └──(loan REJECTED)──► FINANCING_REJECTED ──(buyer converts to CASH)──► AWAITING_PAYMENT
                                                                    └──(buyer re-applies with a smaller amount)──► AWAITING_FINANCING
```

Terminal states: `COMPLETED`, `CANCELLED`, `REJECTED`, `EXPIRED`.

---

## 2. API

Base path `/api/v1/purchase-orders`. All bodies are JSON. Errors use the
existing `ErrorResponse` envelope.

### 2.1 Preview (no side effects)

`GET /api/v1/properties/{propertyId}/purchase-preview` — `AUTHENTICATED`

Lets the client render the right form (cash vs financed) before submitting.

```json
{
  "propertyId": "3f6b1e0c-…",
  "listedPrice": 8500000.00,
  "currency": "ETB",
  "purchaseType": "BANK_FINANCED",
  "financingAvailable": true,
  "financingOffers": [
    {
      "financingOfferId": "9a1c…",
      "bankId": "b2d4…",
      "bankName": "Awash Bank",
      "creditProductId": "c77e…",
      "creditProductName": "Home Purchase Loan",
      "offerLevel": "PROPERTY",
      "interestRate": 14.50,
      "ltvRatio": 0.80,
      "minTenureMonths": 12,
      "maxTenureMonths": 240,
      "minFinanceableAmount": 500000.00,
      "maxFinanceableAmount": 6800000.00,
      "minimumDownPayment": 1700000.00,
      "partialFinancingAllowed": true,
      "recommended": true
    }
  ]
}
```

`purchaseType` is `CASH` and `financingOffers` is `[]` when nothing eligible is
linked. `minFinanceableAmount` and `maxFinanceableAmount` define the range a
partial-financing slider may offer; `partialFinancingAllowed` is `false` only
when the two are equal.

### 2.2 Create purchase order

`POST /api/v1/purchase-orders` — `BUYER_SECURED`, action scope
`purchase-orders.create`

**Request** (`CreatePurchaseOrderRequest`)

```json
{
  "propertyId": "3f6b1e0c-…",                 // @NotNull
  "contactPhone": "+251911223344",           // @NotBlank, @Pattern E.164 / local 09…/07… form
  "contactEmail": "buyer@example.com",       // optional, @Email
  "currency": "ETB",                         // optional, default ETB; must have a price in that currency
  "buyerMessage": "Can I view it on Saturday?",  // optional, @Size(max = 2000)
  "useFinancing": null,                      // optional tri-state, see below
  "financing": {                             // optional; only read when financing is applied
    "financingOfferId": "9a1c…",             // optional; required only if >1 eligible offer and no default wanted
    "financedAmount": 4250000.00,            // optional; PARTIAL financing: loan principal wanted, within [minFinanceable, maxFinanceable]
    "downPaymentAmount": null,               // optional alternative to financedAmount: cash the buyer will pay; the two are mutually exclusive
    "requestedTenureMonths": 180             // optional; within product min/max
  }
}
```

`useFinancing` semantics:

| value | behaviour |
| --- | --- |
| `null` / omitted | **Automatic** (the requested behaviour): financed if an eligible offer exists, cash otherwise |
| `false` | Force a cash order even if financing exists |
| `true` | Require financing; `400` if the property has no eligible offer |

Validation:

* `contactPhone` is mandatory. Accepted inputs: `+2519XXXXXXXX`, `+2517XXXXXXXX`,
  `09XXXXXXXX`, `07XXXXXXXX`, `2519…`; stored normalised as E.164. Any other
  well-formed international number (`^\+[1-9][0-9]{7,14}$`) is also accepted.
* `contactEmail` is optional; when present it must satisfy `@Email`.
* Any field under `financing` is ignored (with a `warnings[]` entry in the
  response) when the resulting order is `CASH`.
* **Partial financing.** The buyer may finance any amount between the
  product's minimum loan and the LTV-capped maximum, and pay the rest in cash.
  They express it either as `financedAmount` or as `downPaymentAmount`
  (`listedPrice − financedAmount`); sending both is a `400`. Omitting both
  gives `MAXIMUM` mode (finance as much as the offer allows). Choosing less
  than the maximum gives `PARTIAL` mode.

**Response** `201 Created`, `Location: /api/v1/purchase-orders/{id}`
(`PurchaseOrderResponse`)

```json
{
  "id": "7c0d…",
  "orderNumber": "PPO-2026-000042",
  "status": "PENDING_SELLER_REVIEW",
  "purchaseType": "BANK_FINANCED",
  "property": {
    "id": "3f6b1e0c-…",
    "title": "3BR Apartment, Bole",
    "city": "Addis Ababa",
    "unitNumber": "A-101",
    "realEstateCompanyId": "…",
    "realEstateCompanyName": "Ayat Real Estate",
    "agentId": "…"
  },
  "buyer": { "id": "…", "contactPhone": "+251911223344", "contactEmail": "buyer@example.com" },
  "pricing": { "listedPrice": 8500000.00, "currency": "ETB" },
  "financing": {
    "financingStatus": "APPLICATION_SUBMITTED",
    "financingOfferId": "9a1c…",
    "offerLevel": "PROPERTY",
    "bankId": "b2d4…",
    "bankName": "Awash Bank",
    "creditProductId": "c77e…",
    "creditProductName": "Home Purchase Loan",
    "appliedInterestRate": 14.50,
    "appliedLtvRatio": 0.80,
    "minFinanceableAmount": 500000.00,
    "maxFinanceableAmount": 6800000.00,
    "financingMode": "PARTIAL",
    "financedAmount": 4250000.00,
    "cashPortionAmount": 4250000.00,
    "financingCoverageRatio": 0.5000,
    "tenureMonths": 180,
    "estimatedMonthlyInstallment": 58033.79,
    "loanApplicationId": "e51a…",
    "nextSteps": [
      "Upload income documents to the loan application",
      "Await bank review"
    ]
  },
  "buyerMessage": "Can I view it on Saturday?",
  "expiresAt": "2026-10-01T10:15:00",
  "warnings": [],
  "createdAt": "2026-09-17T10:15:00",
  "updatedAt": "2026-09-17T10:15:00",
  "statusHistory": [
    { "fromStatus": null, "toStatus": "PENDING_SELLER_REVIEW", "changedBy": "…", "changedAt": "2026-09-17T10:15:00" }
  ]
}
```

For a cash order `purchaseType` is `CASH` and `financing` is `null`.

**Error responses**

| HTTP | `error` | When |
| --- | --- | --- |
| 400 | `Validation Failed` | missing phone, bad email, tenure outside product range, down payment below minimum |
| 400 | `Bad Request` (`BusinessException`) | property not `AVAILABLE`, not `FOR_SALE`, not `VERIFIED`; no price in requested currency; `useFinancing=true` with no eligible offer; `financingOfferId` not linked to this property or inactive |
| 403 | `Forbidden` | caller is the property's own agent / company |
| 404 | `Not Found` | unknown `propertyId` / `financingOfferId` |
| 409 | `Conflict` (**new** `DuplicateResourceException`) | buyer already has an open order for the property |

### 2.3 Read / list

| Method & path | Policy | Purpose |
| --- | --- | --- |
| `GET /api/v1/purchase-orders/{id}` | `AUTHENTICATED` | Buyer, seller (agent / company), the financing bank, or admin. Others get `404` (not `403`) to avoid leaking order existence |
| `GET /api/v1/purchase-orders/me?status=&page=&size=` | `BUYER_SECURED` | Buyer's own orders, `Page<PurchaseOrderResponse>` |
| `GET /api/v1/properties/{propertyId}/purchase-orders?status=` | `REALTOR_SECURED` | Seller view for one listing |
| `GET /api/v1/purchase-orders/received?status=&page=&size=` | `REALTOR_SECURED` | All orders on the caller's company's listings |
| `GET /api/v1/purchase-orders/financed?status=` | `BANKER_SECURED` | Orders financed by the caller's bank |

### 2.4 Transitions

| Method & path | Policy / action scope | Body | Effect |
| --- | --- | --- | --- |
| `POST /purchase-orders/{id}/accept` | `REALTOR_SECURED`, `purchase-orders.review` | `{ "notes"?: string }` | `PENDING_SELLER_REVIEW → AWAITING_PAYMENT` (cash) or `→ AWAITING_FINANCING` (financed). Property `status → RESERVED` |
| `POST /purchase-orders/{id}/reject` | `REALTOR_SECURED`, `purchase-orders.review` | `{ "reason": string }` (`@NotBlank`) | `→ REJECTED`; withdraws the loan application if one exists |
| `POST /purchase-orders/{id}/cancel` | `BUYER_SECURED`, `purchase-orders.cancel` | `{ "reason"?: string }` | `→ CANCELLED` from any non-terminal state; releases `RESERVED`; withdraws loan application |
| `PUT /purchase-orders/{id}/financing` | `BUYER_SECURED` | `{ "financedAmount"? \| "downPaymentAmount"?, "requestedTenureMonths"? }` | Change the financing split. From `PENDING_SELLER_REVIEW`: updates the `SUBMITTED` loan application in place. From `FINANCING_REJECTED`: withdraws the old application, opens a new one with the smaller amount, `→ AWAITING_FINANCING`. Re-runs the §3.3 bounds |
| `POST /purchase-orders/{id}/accept-partial-approval` | `BUYER_SECURED` | `{}` | only from `FINANCING_PARTIALLY_APPROVED`; buyer agrees to cover the shortfall in cash. `financed_amount := approved_amount`, `cash_portion_amount` recomputed, `financing_mode = PARTIAL`, `→ FINANCING_APPROVED → AWAITING_PAYMENT` |
| `POST /purchase-orders/{id}/convert-to-cash` | `BUYER_SECURED` | `{}` | from `FINANCING_REJECTED` or `FINANCING_PARTIALLY_APPROVED`; withdraws the loan application, sets `purchase_type = CASH`, `→ AWAITING_PAYMENT` |
| `POST /purchase-orders/{id}/complete` | `REALTOR_SECURED`, `purchase-orders.complete` | `{ "paymentReference"?: string }` | `AWAITING_PAYMENT → COMPLETED`; property `status → SOLD`; every other open order on the property `→ REJECTED` with reason `PROPERTY_SOLD` |

All transitions append a `PurchaseOrderStatusHistory` row and are guarded by a
single `transition(order, to, actor, notes)` method that validates the allowed
edges from §1.4.

---

## 3. Backend service logic

### 3.1 Module layout (mirrors the other DDD modules)

```
com.housingplatform.purchase
├── api/PurchaseOrderController.java
├── api/PropertyPurchasePreviewController.java     (or add to PropertyController)
├── domain/PropertyPurchaseOrder.java
├── domain/PurchaseOrderFinancing.java
├── domain/PurchaseOrderStatusHistory.java
├── dto/CreatePurchaseOrderRequest.java, PurchaseOrderResponse.java, PurchasePreviewResponse.java, …
├── repository/PropertyPurchaseOrderRepository.java
├── service/PurchaseOrderService.java, PurchaseOrderMapper.java (MapStruct)
├── service/PropertyFinancingResolver.java          ← the catalog lookup, reusable
├── service/PurchaseOrderEvents.java                ← Spring `record` events
└── service/impl/PurchaseOrderServiceImpl.java
```

Cross-module access goes through the existing service / repository interfaces
(`PropertyRepository`, `FinancingOfferRepository`, `CreditProductRepository`,
`LoanApplicationService`), the same way `PropertyController` already depends on
`FinancingOfferService`.

### 3.2 `createPurchaseOrder(buyerId, request)` — step by step

```
@Transactional
PurchaseOrderResponse createPurchaseOrder(UUID buyerId, CreatePurchaseOrderRequest req)

 1. Load property           propertyRepository.findById(req.propertyId) → 404
 2. Guard property          status == AVAILABLE, category == FOR_SALE,
                            verificationStatus == VERIFIED           → 400 BusinessException
                            buyer is not the listing agent/company   → 403
 3. Duplicate guard         repo.existsOpenOrder(buyerId, propertyId) → 409
                            (backed by a partial unique index, see §4, so a race
                             surfaces as DataIntegrityViolation → 409 as well)
 4. Resolve price           currency = req.currency ?? ETB
                            listedPrice = priceETB or priceUSD accordingly; null → 400
 5. Normalise contact       phone = PhoneNumbers.toE164(req.contactPhone)   (required)
                            email = lower-case trimmed or null              (optional)
 6. Resolve financing       FinancingResolution r = financingResolver.resolve(property, currency, req)
                            (§3.3). r.applied() decides purchase_type.
 7. Build order             purchase_type = r.applied() ? BANK_FINANCED : CASH
                            snapshot company/agent/price; status = PENDING_SELLER_REVIEW;
                            expires_at = now + 14d; orderNumber from sequence
 8. If financed             a) loanApplicationService.createLoanApplication(buyerId,
                                  LoanApplicationRequest{ bankId, creditProductId,
                                  financingOfferId, propertyId,
                                  requestedAmount = financedAmount, currency,
                                  requestedTenureMonths = tenure,
                                  purpose = "Purchase order " + orderNumber })
                               → status SUBMITTED, visible to the bank immediately
                            b) attach PurchaseOrderFinancing with the snapshot terms
                               and loanApplicationId; financing_status = APPLICATION_SUBMITTED
 9. History                 append (null → PENDING_SELLER_REVIEW, changedBy = buyerId)
10. Persist                 repo.save(order)
11. Publish                 eventPublisher.publishEvent(new PurchaseOrderCreatedEvent(orderId))
                            (listeners run AFTER_COMMIT — see §3.5)
12. Return                  mapper.toResponse(order) enriched with bank / product names
```

Steps 1–10 are one transaction, so a failure creating the loan application
rolls back the order and the buyer never sees a half-created financed order.

### 3.3 `PropertyFinancingResolver.resolve(property, currency, request)`

This is the "check the product catalog" rule, kept in its own component so the
preview endpoint and the create endpoint cannot drift apart.

```
1. Candidate offers
   offers  = financingOfferRepository.findByPropertyIdAndStatus(property.id, ACTIVE)   → level PROPERTY
   if property.building != null:
       offers += findByBuildingIdAndStatus(building.id, ACTIVE)                        → level BUILDING
   (property-level offers are preferred over building-level ones)

2. Eligibility filter — an offer is eligible only if ALL hold:
   - offer.status == ACTIVE                       (already filtered)
   - product = creditProductRepository.findById(offer.creditProductId) exists
   - product.status == ACTIVE                     ← "active financing product"
   - product.productType == HOME_PURCHASE         (never MATERIAL_FINANCING etc.)
   - product.currency == order currency
   - maxFinanceable = min(listedPrice × ltv, product.maxLoanAmount) ≥ product.minLoanAmount
     where ltv = offer.specialLTVRatio ?? product.maxLoanToValueRatio

3. Decide
   if request.useFinancing == FALSE            → NOT_APPLIED (reason BUYER_DECLINED)
   if eligible.isEmpty():
        if request.useFinancing == TRUE        → 400 "No active financing product is linked to this property"
        else                                   → NOT_APPLIED (reason NONE_AVAILABLE)   ⇒ CASH flow
   chosen =
        request.financing.financingOfferId if given (must be in `eligible`, else 400)
        else the single eligible offer
        else deterministic default: lowest effective interest rate,
             tie-break highest LTV, tie-break PROPERTY level over BUILDING

4. Compute terms for `chosen`
   rate            = offer.specialInterestRate ?? product.interestRate
   ltv             = offer.specialLTVRatio ?? product.maxLoanToValueRatio
   maxFinanceable  = min(listedPrice × ltv, product.maxLoanAmount)     (scale 2, HALF_UP)
   minFinanceable  = product.minLoanAmount
   minDownPayment  = listedPrice − maxFinanceable
   if both financedAmount and downPaymentAmount given                     → 400
   financedAmount  = request.financing.financedAmount
                     ?? (listedPrice − request.financing.downPaymentAmount)
                     ?? maxFinanceable                                     (MAXIMUM mode default)
                     must satisfy minFinanceable ≤ financedAmount ≤ maxFinanceable → 400 otherwise,
                     error message quotes both bounds so the client can render the range
   cashPortion     = listedPrice − financedAmount                          (≥ minDownPayment by construction)
   mode            = financedAmount == maxFinanceable ? MAXIMUM : PARTIAL
   coverageRatio   = financedAmount ÷ listedPrice                          (scale 4)
   tenure          = request.financing.requestedTenureMonths ?? product.maxTenureMonths
                     must be within [product.minTenureMonths, product.maxTenureMonths] → 400
   installment     = amortised: P·i / (1 − (1+i)^−n), i = rate/100/12  (rate 0 → P/n)

   return APPLIED(offer, product, level, rate, ltv, minFinanceable, maxFinanceable,
                  mode, financedAmount, cashPortion, coverageRatio, tenure, installment)
```

Only `LoanApplication`'s own bank review decides approval; the resolver never
does credit scoring. It simply guarantees the order carries a valid, currently
active product with consistent numbers.

### 3.4 Keeping the order in sync with the loan

`LoanApplicationServiceImpl` already owns the `SUBMITTED → UNDER_REVIEW →
APPROVED / REJECTED → DISBURSED` transitions. Add one line to each transition:

```java
eventPublisher.publishEvent(new LoanApplicationStatusChangedEvent(id, from, to));
```

`PurchaseOrderLoanListener` (`@TransactionalEventListener`, `AFTER_COMMIT`)
maps it onto the order:

| Loan status | `financing_status` | Order status change |
| --- | --- | --- |
| `UNDER_REVIEW` | `UNDER_REVIEW` | none |
| `APPROVED`, `approvedAmount ≥ financed_amount` | `APPROVED` (stores approved amount / rate / tenure) | `AWAITING_FINANCING → FINANCING_APPROVED → AWAITING_PAYMENT` |
| `APPROVED`, `approvedAmount < financed_amount` | `PARTIALLY_APPROVED` | `AWAITING_FINANCING → FINANCING_PARTIALLY_APPROVED`; the buyer is told the new cash portion and must `accept-partial-approval`, `convert-to-cash` or `cancel`. Nothing moves automatically because the shortfall is the buyer's money |
| `REJECTED` | `REJECTED` | `AWAITING_FINANCING → FINANCING_REJECTED` (buyer may re-apply for a smaller amount via `PUT …/financing`, `convert-to-cash` or `cancel`) |
| `DISBURSED` | `DISBURSED` | none (payment module completes the order) |

Conversely, buyer cancel / seller reject before a decision calls
`loanApplicationService.withdraw(...)` (new small method: `SUBMITTED |
UNDER_REVIEW → CLOSED` with a note) so the bank's queue is cleaned up.

### 3.5 Events and notifications

`PurchaseOrderEvents` (plain Spring `record`s like `SupportRagIndexEvents`):

* `PurchaseOrderCreatedEvent(orderId)` → notification listener:
  * in-app notification to the listing agent / company users
  * SMS confirmation to `contact_phone` (mandatory channel, which is why the
    phone is required)
  * email confirmation to `contact_email` if present
  * if financed: in-app notification to the bank's users that a loan
    application was submitted through a purchase order
* `PurchaseOrderStatusChangedEvent(orderId, from, to)` → buyer (SMS + email
  when available) and seller notifications; on `COMPLETED` also
  `RagIndexPropertyEvent` so the support assistant learns the property is sold.

Listeners are `@TransactionalEventListener(phase = AFTER_COMMIT)` and
`@Async`, so a notification failure can never roll back an order.

### 3.6 Authorisation rules inside the service

* Buyer endpoints check `order.buyerId == UserContext.getCurrentUserId()`.
* Seller endpoints resolve the caller's agent (`RealEstateAgentService`, as
  `PropertyController.getAgentIdForUser` does) or organisation
  (`UserContext.getCurrentUserOrganizationId()`) and compare with the snapshot
  `agent_id` / `real_estate_company_id`.
* Bank endpoints compare `UserContext.getCurrentUserOrganizationId()` with
  `financing.bank_id`.
* Admins (`UserContext.isAdmin()`) can read everything.

New action scopes to register: `purchase-orders.create`,
`purchase-orders.cancel`, `purchase-orders.review`, `purchase-orders.complete`.

### 3.7 Expiry job

`@Scheduled(cron = "0 */15 * * * *")` in `PurchaseOrderExpiryJob`: move
`PENDING_SELLER_REVIEW` orders with `expires_at < now()` to `EXPIRED`
(`changedBy = "system"`), withdraw any linked loan application, emit the
status event.

---

## 4. Database migration — `V59__Create_property_purchase_orders.sql`

```sql
CREATE SEQUENCE property_purchase_order_seq START 1;

CREATE TABLE property_purchase_orders (
    id                      UUID PRIMARY KEY,
    order_number            VARCHAR(32)  NOT NULL UNIQUE,
    property_id             UUID         NOT NULL REFERENCES properties(id),
    buyer_id                UUID         NOT NULL REFERENCES users(id),
    real_estate_company_id  UUID,
    agent_id                UUID,
    contact_phone           VARCHAR(20)  NOT NULL,
    contact_email           VARCHAR(255),
    purchase_type           VARCHAR(20)  NOT NULL,
    status                  VARCHAR(32)  NOT NULL,
    listed_price            NUMERIC(19,2) NOT NULL,
    currency                VARCHAR(3)   NOT NULL,
    buyer_message           TEXT,
    expires_at              TIMESTAMP,
    cancellation_reason     TEXT,
    rejection_reason        TEXT,
    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP    NOT NULL,
    created_by              VARCHAR(255),
    updated_by              VARCHAR(255),
    version                 BIGINT,
    CONSTRAINT chk_ppo_purchase_type CHECK (purchase_type IN ('CASH', 'BANK_FINANCED'))
);

-- one open order per buyer per property (race-safe version of the 409 check)
CREATE UNIQUE INDEX uq_ppo_open_buyer_property
    ON property_purchase_orders (buyer_id, property_id)
    WHERE status NOT IN ('COMPLETED', 'CANCELLED', 'REJECTED', 'EXPIRED');

CREATE INDEX idx_ppo_buyer_created    ON property_purchase_orders (buyer_id, created_at DESC);
CREATE INDEX idx_ppo_property_status  ON property_purchase_orders (property_id, status);
CREATE INDEX idx_ppo_company_status   ON property_purchase_orders (real_estate_company_id, status);
CREATE INDEX idx_ppo_pending_expiry   ON property_purchase_orders (expires_at)
    WHERE status = 'PENDING_SELLER_REVIEW';

CREATE TABLE purchase_order_financing (
    purchase_order_id             UUID PRIMARY KEY REFERENCES property_purchase_orders(id) ON DELETE CASCADE,
    financing_offer_id            UUID NOT NULL REFERENCES financing_offers(id),
    bank_id                       UUID NOT NULL,
    credit_product_id             UUID NOT NULL REFERENCES credit_products(id),
    offer_level                   VARCHAR(16)  NOT NULL,
    applied_interest_rate         NUMERIC(5,2) NOT NULL,
    applied_ltv_ratio             NUMERIC(5,2) NOT NULL,
    min_financeable_amount        NUMERIC(19,2) NOT NULL,
    max_financeable_amount        NUMERIC(19,2) NOT NULL,
    financing_mode                VARCHAR(16)  NOT NULL,
    financed_amount               NUMERIC(19,2) NOT NULL,
    cash_portion_amount           NUMERIC(19,2) NOT NULL,
    financing_coverage_ratio      NUMERIC(5,4)  NOT NULL,
    tenure_months                 INTEGER      NOT NULL,
    estimated_monthly_installment NUMERIC(19,2),
    loan_application_id           UUID REFERENCES loan_applications(id),
    financing_status              VARCHAR(32)  NOT NULL,
    approved_amount               NUMERIC(19,2),
    approved_interest_rate        NUMERIC(5,2),
    approved_tenure_months        INTEGER,
    CONSTRAINT chk_pof_mode   CHECK (financing_mode IN ('MAXIMUM', 'PARTIAL')),
    CONSTRAINT chk_pof_bounds CHECK (financed_amount BETWEEN min_financeable_amount AND max_financeable_amount),
    CONSTRAINT chk_pof_split  CHECK (financed_amount > 0 AND cash_portion_amount >= 0)
);

CREATE INDEX idx_pof_bank_status ON purchase_order_financing (bank_id, financing_status);
CREATE INDEX idx_pof_loan        ON purchase_order_financing (loan_application_id);

CREATE TABLE purchase_order_status_history (
    id                UUID PRIMARY KEY,
    purchase_order_id UUID NOT NULL REFERENCES property_purchase_orders(id) ON DELETE CASCADE,
    from_status       VARCHAR(32),
    to_status         VARCHAR(32) NOT NULL,
    changed_by        VARCHAR(255),
    changed_at        TIMESTAMP   NOT NULL,
    notes             TEXT,
    created_at        TIMESTAMP   NOT NULL,
    updated_at        TIMESTAMP   NOT NULL,
    version           BIGINT
);

CREATE INDEX idx_posh_order_changed ON purchase_order_status_history (purchase_order_id, changed_at);
```

---

## 5. Worked examples

**A. Property with an active financing product**

* Property `priceETB = 8 500 000`, linked offer: Awash Bank "Home Purchase
  Loan", product `ACTIVE`, `interestRate 14.5`, `maxLTV 0.80`,
  `maxLoanAmount 20 000 000`, tenure 12–240.
* Buyer posts `{ propertyId, contactPhone: "0911223344" }` only.
* Resolver: one eligible offer → applied. `maxFinanceable = 6 800 000`,
  `downPayment = 1 700 000` (default = minimum), `tenure = 240` (default),
  installment ≈ 87 039.85.
* Result: `purchaseType = BANK_FINANCED`, `LoanApplication` created with
  `requestedAmount 6 800 000`, order `PENDING_SELLER_REVIEW`, SMS sent to
  `+251911223344`, no email (none given).

**B. Property with no linked offer, or offer whose product was set INACTIVE**

* Resolver finds no eligible offer → `NOT_APPLIED (NONE_AVAILABLE)`.
* Result: `purchaseType = CASH`, `financing = null`, no loan application. If
  the buyer had sent a `financing` block it is ignored and
  `warnings = ["financing block ignored: property has no active financing product"]`.

**C. Buyer with cash on a financed property**

* `useFinancing = false` → `CASH` order even though an offer exists; preview
  still shows the offers so the UI can explain the choice.

**D. Partial financing**

* Same property and offer as A (`minLoanAmount 500 000`, max financeable
  `6 800 000`). Buyer posts `financing: { financedAmount: 4 250 000,
  requestedTenureMonths: 180 }`.
* Resolver: `500 000 ≤ 4 250 000 ≤ 6 800 000` → valid. `cashPortion =
  4 250 000`, `mode = PARTIAL`, `coverageRatio = 0.5000`, instalment ≈
  58 033.79. Loan application opened for `4 250 000`.
* If the bank later approves only `3 000 000`, the order moves to
  `FINANCING_PARTIALLY_APPROVED` with a proposed cash portion of
  `5 500 000`; the buyer accepts, converts to cash, or cancels.
* Posting `financedAmount: 300 000` instead fails with `400` because it is
  below the product minimum; the message quotes the allowed range.

---

## 6. Test plan (JUnit 5 + Mockito, matching the existing service tests)

* `PropertyFinancingResolverTest`: no offers → cash; active offer + inactive
  product → cash; building-level offer picked when property-level absent;
  lowest-rate default among several; explicit `financingOfferId` not linked
  → 400; tenure bounds; partial financing inside / below / above the
  `[minLoanAmount, maxFinanceable]` range; `financedAmount` and
  `downPaymentAmount` both present → 400; mode `MAXIMUM` vs `PARTIAL`
  derivation; currency mismatch excluded.
* `PurchaseOrderServiceImplTest`: happy paths A/B/C/D above; `PUT …/financing`
  in `PENDING_SELLER_REVIEW` updates the loan amount, in
  `FINANCING_REJECTED` re-applies, elsewhere → 400; partial bank approval
  parks the order in `FINANCING_PARTIALLY_APPROVED` and
  `accept-partial-approval` recomputes the split; property not
  `AVAILABLE` → 400; duplicate open order → 409; loan-application failure
  rolls back the order; phone normalisation; email optional; status machine
  rejects illegal transitions; accept sets property `RESERVED`; complete sets
  `SOLD` and rejects sibling orders.
* `PurchaseOrderControllerIT` (`@SpringBootTest` + Testcontainers Postgres):
  201 with `Location` header, 400 field errors for missing phone, scope
  enforcement (`BUYER_SECURED` vs realtor token), partial unique index under
  concurrent creates.

## 7. Open decisions (defaults chosen here, easy to flip)

| Decision | Default in this design | Alternative |
| --- | --- | --- |
| When to create the loan application | immediately at order creation (`SUBMITTED`) | create as `DRAFT` and submit on seller acceptance |
| Default tenure | `product.maxTenureMonths` (lowest instalment) | require the buyer to choose |
| Default financed amount | maximum the offer allows (`MAXIMUM` mode); partial is opt-in | default to a fixed coverage, e.g. 50 % |
| Bank approves less than requested | park in `FINANCING_PARTIALLY_APPROVED` until the buyer accepts the larger cash portion | auto-accept and move straight to `AWAITING_PAYMENT` |
| Building-level offers | count as "linked to the property" | property-level offers only |
| Multiple eligible offers with no choice | pick lowest effective rate | reject with 400 and force a choice |
| Reservation | property `RESERVED` on seller accept | reserve on order creation |
