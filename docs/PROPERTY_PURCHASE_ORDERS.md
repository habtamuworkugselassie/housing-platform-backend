# Property Purchase Orders with Optional Bank Financing

> **Status: implemented** in `com.housingplatform.purchase` (migrations `V59`–`V62`). Unit tests
> cover the financing resolver, the phone normaliser, the order service, the agreement service and
> the template renderer. The seeded agreement texts are drafts for legal review (§8). Not yet built: an SMS/email
> gateway (the `PurchaseOrderContactNotifier` default only logs) and HTTP-level integration tests.

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
| `order_number` | VARCHAR(32) UNIQUE | `PPO-YYYY-XXXXXXXX`, year plus 8 random hex characters; the unique index guards collisions |
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
| `payment_reference` | VARCHAR(255) | set by the seller on completion |
| `created_at`, `updated_at`, `created_by`, `updated_by`, `version` | | base entity |

### 1.2 `PurchaseOrderFinancing` (1:1, only when `purchase_type = BANK_FINANCED`) — table `purchase_order_financing`

| Column | Rules |
| --- | --- |
| `id` | UUID PK (own identity, `BaseEntity`) |
| `purchase_order_id` | UUID FK UNIQUE, `ON DELETE CASCADE` |
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

`GET /api/v1/properties/{propertyId}/purchase-preview` — `UNSECURED` (a signed-in buyer gets the agreement rendered with their name; a visitor sees "the Buyer")

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

The preview also carries `agreementsToSign`: the agreements the buyer must sign inside the
create call (today the Promise to Purchase), rendered for this buyer and property with the order
number shown as "to be assigned" (§8).

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
  },
  "promiseToPurchase": {                     // REQUIRED, see §8
    "templateId": "a1000000-0000-4000-8000-000000000001",  // from the preview's agreementsToSign
    "accepted": true,
    "signatoryFullName": "Abebe Kebede"
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
  "agreements": [
    {
      "id": "c3a1…", "type": "PROMISE_TO_PURCHASE", "templateVersion": 1, "sequence": 1,
      "title": "Promise to Purchase Agreement", "status": "FULLY_SIGNED", "blocksCompletion": true,
      "contentHash": "9f2c…", "issuedAt": "2026-09-17T10:15:00",
      "buyerSignatoryName": "Abebe Kebede", "buyerSignedAt": "2026-09-17T10:15:00",
      "providerName": "Dream Team PLC", "providerSignatoryName": "Authorized Signatory",
      "providerSignedAt": "2026-09-17T10:15:00"
    }
  ],
  "pendingSignatures": 0,
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
| 400 | `Validation Failed` | missing phone, bad email, missing or unaccepted `promiseToPurchase`, tenure outside product range, down payment below minimum |
| 400 | `Bad Request` (`BusinessException`) | property not `AVAILABLE`, not `FOR_SALE`, not `VERIFIED`; no price in requested currency; `useFinancing=true` with no eligible offer; `financingOfferId` not linked to this property or inactive; `promiseToPurchase.templateId` is not the current Promise to Purchase version |
| 403 | `Forbidden` (**new** `ForbiddenOperationException`) | caller is the property's own agent / company; seller action by someone who can see but not manage the order |
| 404 | `Not Found` | unknown `propertyId` / `financingOfferId` |
| 409 | `Conflict` (**new** `DuplicateResourceException`) | buyer already has an open order for the property |

### 2.3 Read / list

| Method & path | Policy | Purpose |
| --- | --- | --- |
| `GET /api/v1/purchase-orders/{id}` | `AUTHENTICATED` | Buyer, seller (agent / company), the financing bank, or admin. Others get `404` (not `403`) to avoid leaking order existence |
| `GET /api/v1/purchase-orders/me?status=&page=&size=` | `BUYER_SECURED` | Buyer's own orders, `Page<PurchaseOrderResponse>` |
| `GET /api/v1/purchase-orders/by-property/{propertyId}?status=` | `REALTOR_SECURED` | Seller view for one listing |
| `GET /api/v1/purchase-orders/received?status=&page=&size=` | `REALTOR_SECURED` | All orders on the caller's company's listings |
| `GET /api/v1/purchase-orders/financed?status=` | `BANKER_SECURED` | Orders financed by the caller's bank |

### 2.3a Admin listing

| Endpoint | Policy | Notes |
|---|---|---|
| `GET /api/v1/admin/purchase-orders` | `ADMIN_SECURED` | Every order on the platform, newest first, paged (`page`, `size` ≤ 200). Optional filters combine with AND: `status`, `purchaseType`, `realEstateCompanyId`, `bankId`, `buyerId`, `q` (substring of order number, contact phone or contact email, case-insensitive), `createdFrom` / `createdTo` (ISO dates; from inclusive, to exclusive). Built with a JPA `Specification` (`PurchaseOrderSpecifications.forAdmin`) so absent filters add no predicate. |
| `GET /api/v1/admin/purchase-orders/stats` | `ADMIN_SECURED` | `{ total, open, byStatus }` — every status is present, 0 when empty; `open` = statuses in `PurchaseOrderStatus.OPEN`. |
| `GET /api/v1/purchase-orders/{id}` | `AUTHENTICATED` | Admins read any order here (see §3.6); the admin UI links each row to it. |

Deposit administration stays under `POST /api/v1/admin/purchase-orders/{id}/deposit/waive|refunded` (§10.2).

### 2.4 Transitions

| Method & path | Policy / action scope | Body | Effect |
| --- | --- | --- | --- |
| `POST /purchase-orders/{id}/accept` | `REALTOR_SECURED`, `purchase-orders.review` | `{ "notes"?: string }` | `PENDING_SELLER_REVIEW → AWAITING_PAYMENT` (cash) or `→ AWAITING_FINANCING` (financed). Property `status → RESERVED` |
| `POST /purchase-orders/{id}/reject` | `REALTOR_SECURED`, `purchase-orders.review` | `{ "reason": string }` (`@NotBlank`) | `→ REJECTED` from any open state; withdraws the loan application if one exists; releases `RESERVED` |
| `POST /purchase-orders/{id}/cancel` | `BUYER_SECURED`, `purchase-orders.cancel` | `{ "notes"?: string }` | `→ CANCELLED` from any non-terminal state; releases `RESERVED`; withdraws loan application |
| `PUT /purchase-orders/{id}/financing` | `BUYER_SECURED` | `{ "financedAmount"? \| "downPaymentAmount"?, "requestedTenureMonths"? }` | Change the financing split. From `PENDING_SELLER_REVIEW`: updates the `SUBMITTED` loan application in place. From `FINANCING_REJECTED`: withdraws the old application, opens a new one with the smaller amount, `→ AWAITING_FINANCING`. Re-runs the §3.3 bounds |
| `POST /purchase-orders/{id}/accept-partial-approval` | `BUYER_SECURED` | `{}` | only from `FINANCING_PARTIALLY_APPROVED`; buyer agrees to cover the shortfall in cash. `financed_amount := approved_amount`, `cash_portion_amount` recomputed, `financing_mode = PARTIAL`, `→ FINANCING_APPROVED → AWAITING_PAYMENT` |
| `POST /purchase-orders/{id}/convert-to-cash` | `BUYER_SECURED` | `{}` | from `FINANCING_REJECTED` or `FINANCING_PARTIALLY_APPROVED`; withdraws the loan application, sets `purchase_type = CASH`, `→ AWAITING_PAYMENT` |
| `POST /purchase-orders/{id}/complete` | `REALTOR_SECURED`, `purchase-orders.complete` | `{ "paymentReference"?: string }` | refused with 400 while any blocking agreement is unsigned (§8); `AWAITING_PAYMENT → COMPLETED`; property `status → SOLD`; every other open order on the property `→ REJECTED` with reason `PROPERTY_SOLD` |

All transitions append a `PurchaseOrderStatusHistory` row and are guarded by a
single `transition(order, to, actor, notes)` method that validates the allowed
edges from §1.4.

---

## 3. Backend service logic

### 3.1 Module layout (mirrors the other DDD modules)

```
com.housingplatform.purchase
├── api/PurchaseOrderController.java                 /api/v1/purchase-orders/**
├── api/PropertyPurchasePreviewController.java       /api/v1/properties/{id}/purchase-preview
├── api/PurchaseOrderActorResolver.java              security context → PurchaseOrderActor
├── config/PurchaseSchedulingConfig.java             @EnableScheduling for the expiry job
├── domain/PropertyPurchaseOrder.java, PurchaseOrderFinancing.java, PurchaseOrderStatusHistory.java
├── dto/CreatePurchaseOrderRequest.java, UpdatePurchaseFinancingRequest.java,
│       PurchaseOrderDecisionRequest.java, RejectPurchaseOrderRequest.java,
│       PurchaseOrderResponse.java, PurchasePreviewResponse.java
├── repository/PropertyPurchaseOrderRepository.java
├── service/PurchaseOrderService.java, PurchaseOrderActor.java
├── service/PropertyFinancingResolver.java           ← the catalog lookup, shared by preview + create
├── service/PhoneNumberNormalizer.java               ← E.164 normalisation of the mandatory phone
├── service/PurchaseOrderMapper.java                 hand-written (joins property, users, bank, product)
├── service/PurchaseOrderEvents.java                 Spring `record` events
├── service/PurchaseOrderLoanListener.java           loan status → order status
├── service/PurchaseOrderNotificationListener.java   in-app notifications + contact notifier
├── service/PurchaseOrderContactNotifier.java        SMS/email seam (default impl logs)
├── service/PurchaseOrderExpiryJob.java              @Scheduled expiry
├── service/PurchaseAgreementService.java, PurchaseAgreementMapper.java, PurchaseOrderAccess.java
├── service/AgreementTemplateRenderer.java           {{placeholders}} + conditional blocks + SHA-256
├── api/PurchaseAgreementController.java            /api/v1/purchase-orders/{id}/agreements/**
├── api/AdminAgreementController.java               templates, countersign, manual issue
├── config/PurchaseProviderProperties.java          purchase.provider.* (Dream Team PLC)
└── service/impl/PurchaseOrderServiceImpl.java, PurchaseAgreementServiceImpl.java,
        LoggingPurchaseOrderContactNotifier.java
```

Shared additions: `DuplicateResourceException` (409) and `ForbiddenOperationException` (403) with
handlers in `GlobalExceptionHandler`; `Notification.NotificationType.PURCHASE_ORDER_UPDATE`.

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
 9. Agreements              agreementService.signPromiseToPurchaseAtCreation(order, req.promiseToPurchase, evidence)
                            issues every active ORDER_CREATION agreement and applies the buyer's
                            signature (+ the provider's, when auto-countersign is on). A stale
                            templateId or a missing active template → 400, nothing is saved.
10. History                 append (null → PENDING_SELLER_REVIEW, changedBy = buyerId)
11. Persist                 repo.save(order)
12. Publish                 eventPublisher.publishEvent(new PurchaseOrderCreatedEvent(orderId))
                            (listeners run AFTER_COMMIT — see §3.5)
13. Return                  mapper.toResponse(order) enriched with bank / product names
```

Steps 1–11 are one transaction, so a failure creating the loan application
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
APPROVED / REJECTED → DISBURSED` transitions. Each one now publishes
`LoanApplicationEvents.LoanApplicationStatusChangedEvent(id, from, to)`. Two methods were added to
`LoanApplicationService`: `updateRequestedTerms(id, amount, tenure)` (only while `SUBMITTED`) and
`withdrawLoanApplication(id, reason)` (`SUBMITTED | UNDER_REVIEW → CLOSED`, no-op otherwise).

`PurchaseOrderLoanListener` (`@TransactionalEventListener`, `AFTER_COMMIT`)
maps it onto the order:

| Loan status | `financing_status` | Order status change |
| --- | --- | --- |
| `UNDER_REVIEW` | `UNDER_REVIEW` | none |
| `APPROVED`, `approvedAmount ≥ financed_amount` | `APPROVED` (stores approved amount / rate / tenure) | `AWAITING_FINANCING → FINANCING_APPROVED → AWAITING_PAYMENT` |
| `APPROVED`, `approvedAmount < financed_amount` | `PARTIALLY_APPROVED` | `AWAITING_FINANCING → FINANCING_PARTIALLY_APPROVED`; the buyer is told the new cash portion and must `accept-partial-approval`, `convert-to-cash` or `cancel`. Nothing moves automatically because the shortfall is the buyer's money |
| `REJECTED` | `REJECTED` | `AWAITING_FINANCING → FINANCING_REJECTED` (buyer may re-apply for a smaller amount via `PUT …/financing`, `convert-to-cash` or `cancel`) |
| `DISBURSED` | `DISBURSED` | none (payment module completes the order) |

Conversely, buyer cancel / seller reject / expiry before a decision calls
`loanApplicationService.withdrawLoanApplication(...)` so the bank's queue is cleaned up. A
`CLOSED` loan event marks the order's financing `WITHDRAWN` unless it was already rejected or
disbursed.

### 3.5 Events and notifications

`PurchaseOrderEvents` (plain Spring `record`s like `SupportRagIndexEvents`):

* `PurchaseOrderCreatedEvent(orderId)` → notification listener:
  * in-app notification to the listing agent / company users
  * SMS confirmation to `contact_phone` (mandatory channel, which is why the
    phone is required) and email to `contact_email` if present, both through
    `PurchaseOrderContactNotifier`; the platform has no SMS gateway yet, so the
    default bean logs and a provider is plugged in by replacing that bean
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

Action scopes on the endpoints: `purchase-orders.create`, `purchase-orders.cancel`,
`purchase-orders.review`, `purchase-orders.complete`. As with every other module, the policy
(`BUYER_SECURED` etc.) is the gate; `ScopeAuthorizationFilter` treats action scopes as
documentation until they are minted into tokens.

The controller builds a `PurchaseOrderActor` (user, organisation, agent, admin flag) from
`UserContext` and the agent repository; the service does all ownership checks against it and never
touches the security context, which keeps it unit-testable.

### 3.7 Expiry job

`PurchaseOrderExpiryJob`, cron from `purchase.orders.expiry-cron` (default every 15 minutes): move
`PENDING_SELLER_REVIEW` orders with `expires_at < now()` to `EXPIRED`
(`changedBy = "system"`), withdraw any linked loan application, emit the
status event.

---

## 4. Database migration — `V59__Create_property_purchase_orders.sql`

```sql
-- Property purchase orders with optional (and optionally partial) bank financing.

CREATE TABLE property_purchase_orders (
    id                      UUID PRIMARY KEY,
    order_number            VARCHAR(32)   NOT NULL UNIQUE,
    property_id             UUID          NOT NULL REFERENCES properties(id),
    buyer_id                UUID          NOT NULL REFERENCES users(id),
    real_estate_company_id  UUID,
    agent_id                UUID,
    contact_phone           VARCHAR(20)   NOT NULL,
    contact_email           VARCHAR(255),
    purchase_type           VARCHAR(20)   NOT NULL,
    status                  VARCHAR(32)   NOT NULL,
    listed_price            NUMERIC(19,2) NOT NULL,
    currency                VARCHAR(3)    NOT NULL,
    buyer_message           TEXT,
    expires_at              TIMESTAMP,
    cancellation_reason     TEXT,
    rejection_reason        TEXT,
    payment_reference       VARCHAR(255),
    created_at              TIMESTAMP     NOT NULL,
    updated_at              TIMESTAMP     NOT NULL,
    created_by              VARCHAR(255),
    updated_by              VARCHAR(255),
    version                 BIGINT,
    CONSTRAINT chk_ppo_purchase_type CHECK (purchase_type IN ('CASH', 'BANK_FINANCED'))
);

-- One open order per buyer per property: the race-safe twin of the service's 409 check.
CREATE UNIQUE INDEX uq_ppo_open_buyer_property
    ON property_purchase_orders (buyer_id, property_id)
    WHERE status NOT IN ('COMPLETED', 'CANCELLED', 'REJECTED', 'EXPIRED');

CREATE INDEX idx_ppo_buyer_created   ON property_purchase_orders (buyer_id, created_at DESC);
CREATE INDEX idx_ppo_property_status ON property_purchase_orders (property_id, status);
CREATE INDEX idx_ppo_company_created ON property_purchase_orders (real_estate_company_id, created_at DESC);
CREATE INDEX idx_ppo_pending_expiry  ON property_purchase_orders (expires_at)
    WHERE status = 'PENDING_SELLER_REVIEW';

CREATE TABLE purchase_order_financing (
    id                            UUID PRIMARY KEY,
    purchase_order_id             UUID          NOT NULL UNIQUE REFERENCES property_purchase_orders(id) ON DELETE CASCADE,
    financing_offer_id            UUID          NOT NULL REFERENCES financing_offers(id),
    bank_id                       UUID          NOT NULL,
    credit_product_id             UUID          NOT NULL REFERENCES credit_products(id),
    offer_level                   VARCHAR(16)   NOT NULL,
    applied_interest_rate         NUMERIC(5,2)  NOT NULL,
    applied_ltv_ratio             NUMERIC(5,2)  NOT NULL,
    min_financeable_amount        NUMERIC(19,2) NOT NULL,
    max_financeable_amount        NUMERIC(19,2) NOT NULL,
    financing_mode                VARCHAR(16)   NOT NULL,
    financed_amount               NUMERIC(19,2) NOT NULL,
    cash_portion_amount           NUMERIC(19,2) NOT NULL,
    financing_coverage_ratio      NUMERIC(5,4)  NOT NULL,
    tenure_months                 INTEGER       NOT NULL,
    estimated_monthly_installment NUMERIC(19,2),
    loan_application_id           UUID          REFERENCES loan_applications(id),
    financing_status              VARCHAR(32)   NOT NULL,
    approved_amount               NUMERIC(19,2),
    approved_interest_rate        NUMERIC(5,2),
    approved_tenure_months        INTEGER,
    created_at                    TIMESTAMP     NOT NULL,
    updated_at                    TIMESTAMP     NOT NULL,
    version                       BIGINT,
    CONSTRAINT chk_pof_mode  CHECK (financing_mode IN ('MAXIMUM', 'PARTIAL')),
    CONSTRAINT chk_pof_split CHECK (financed_amount > 0 AND cash_portion_amount >= 0)
);

CREATE INDEX idx_pof_bank_status ON purchase_order_financing (bank_id, financing_status);
CREATE INDEX idx_pof_loan        ON purchase_order_financing (loan_application_id);

CREATE TABLE purchase_order_status_history (
    id                UUID PRIMARY KEY,
    purchase_order_id UUID        NOT NULL REFERENCES property_purchase_orders(id) ON DELETE CASCADE,
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

## 6. Tests (JUnit 5 + Mockito, under `src/test/java/com/housingplatform/purchase`)

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
* `PhoneNumberNormalizerTest`: Ethiopian local and international forms, rejects malformed input.
* Not yet written: `PurchaseOrderControllerIT` (`@SpringBootTest`): 201 with `Location`
  header, 400 field errors for missing phone, scope enforcement (`BUYER_SECURED` vs realtor
  token), partial unique index under concurrent creates (needs PostgreSQL, not H2).

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

---

## 8. Agreements between the buyer and the provider

Every purchase order carries a chain of agreements between the **buyer** and the **provider**, the
company operating the platform (Dream Team PLC by default, configured under `purchase.provider.*`).
The listing company is not a party: its sale contract with the buyer is concluded off-platform and
merely acknowledged here.

### 8.1 Model

| Entity | Table | Purpose |
| --- | --- | --- |
| `AgreementTemplate` | `agreement_templates` | Versioned Markdown text with `{{placeholders}}`. Versions are immutable; one version per `type` is active. Carries `issueTrigger` (when it is issued), `appliesTo` (`ALL` / `CASH_ONLY` / `FINANCED_ONLY`), `sequence`, `blocksCompletion`. |
| `PurchaseAgreement` | `purchase_agreements` | One agreement on one order: the rendered `content`, its SHA-256 `contentHash`, status, and both signatures with evidence (typed name, time, IP, user agent for the buyer; signatory name/title, time and countersigning admin for the provider). |

Agreement statuses: `PENDING_BUYER_SIGNATURE → PENDING_PROVIDER_SIGNATURE → FULLY_SIGNED`, or
`VOID`. With `purchase.provider.auto-countersign=true` (default) the provider's signature is
applied the moment the buyer signs, so standard-form agreements go straight to `FULLY_SIGNED`;
with it off an admin countersigns.

### 8.2 The chain

| # | Type | Issued | Seeded as | Applies to |
| --- | --- | --- | --- | --- |
| 1 | `PROMISE_TO_PURCHASE` | inside `POST /purchase-orders`; the order is not created without the buyer's signature | **active** v1 | all orders |
| 2 | `SALE_AGREEMENT` (acknowledgement) | on seller acceptance | inactive draft v1 | all orders |
| 3 | `FINANCING_ACKNOWLEDGEMENT` | when the bank approves (full or partial-accepted) | inactive draft v1 | financed orders |
| … | `HANDOVER_AGREEMENT`, `OTHER` | `MANUAL` (admin issues) or any trigger | none | — |

Follow-ups are issued automatically by `issueForTrigger` when the order reaches the trigger, once
per template, and the buyer is notified. Closing an order (`CANCELLED`, `REJECTED`, `EXPIRED`)
voids every agreement not yet fully signed; converting to cash voids `FINANCED_ONLY` ones. The
seller cannot **complete** the sale while any `blocksCompletion` agreement is unsigned.

The seeded texts are **drafts**: the Promise to Purchase is active because orders need it, the
other two are inactive until legal review and an admin activates them (or publishes a new
version).

### 8.3 Endpoints

| Method & path | Policy | Purpose |
| --- | --- | --- |
| `GET /properties/{id}/purchase-preview` | `AUTHENTICATED` | includes `agreementsToSign[]` with `templateId`, `version`, rendered `content` |
| `POST /purchase-orders` | `BUYER_SECURED` | body carries `promiseToPurchase { templateId, accepted, signatoryFullName }` |
| `GET /purchase-orders/{id}/agreements` | `AUTHENTICATED` (buyer, seller, bank, admin) | list without content |
| `GET /purchase-orders/{id}/agreements/{agreementId}` | same | signed text, hash, signatures |
| `POST /purchase-orders/{id}/agreements/{agreementId}/sign` | `BUYER_SECURED` | buyer signs a follow-up agreement |
| `GET/POST /admin/agreement-templates` | `ADMIN_SECURED` | list versions / create a new version (`activate: true` makes it current) |
| `POST /admin/agreement-templates/{id}/activate|deactivate` | `ADMIN_SECURED` | switch the active version |
| `POST /admin/purchase-agreements/{id}/countersign` | `ADMIN_SECURED` | provider signature when auto-countersign is off |
| `POST /admin/purchase-orders/{id}/agreements?templateId=` | `ADMIN_SECURED` | issue an active template manually |

### 8.4 Template placeholders

`order.number`, `date.today`, `provider.name|registrationNumber|address|email|phone|signatoryName|signatoryTitle`,
`buyer.fullName|phone|email`, `property.title|address|city|unitNumber`, `seller.companyName`,
`price.amount|currency`, and on financed orders `financing` (flag) plus
`financing.financedAmount|cashPortion|bankName|interestRate|tenureMonths|approvedAmount`.
`{{#key}}…{{/key}}` keeps a block when the key has a value, `{{^key}}…{{/key}}` when it does not.
Amounts render with thousands separators (`8,500,000.00`).

### 8.5 Evidence

What the buyer saw is exactly what is stored: the template is rendered once at issue time, hashed,
and never re-rendered. A signature references the template id, so a buyer who read version 1 in
the preview cannot accidentally sign version 2 published in between (400 with the current
version). Signature method is `TYPED_NAME` today; the column exists so OTP or digital signatures
can be added without a schema change.

---

## 9. Getting visitors to an account: quick registration and Google

Placing an order needs a BUYER account (the signature, the follow-up agreements and the loan
application all hang off a user id), but a visitor should not have to leave the property page to
get one. Two additions to the `identity` module make that possible; the existing WhatsApp-code
login (`/auth/login/otp/*`) is the third door.

| Endpoint | Policy | Behaviour |
| --- | --- | --- |
| `POST /api/v1/auth/quick-register` | `UNSECURED` | `{ fullName, phoneNumber, email?, password? }`. Phone is normalised to E.164 (`PhoneNumberNormalizer`, now in `shared.util`); the name is split into first/last. Creates a `BUYER` in `PENDING_VERIFICATION`, sends a WhatsApp code best-effort so the phone can be confirmed later, and returns tokens immediately (201). Without a password the account is passwordless: a random unusable hash is stored and sign-in is by WhatsApp code. Known phone or email → 409. |
| `POST /api/v1/auth/google` | `UNSECURED` | `{ idToken }` from the Google Identity Services button. `GoogleIdTokenVerifier` checks signature (Google JWKS), issuer, audience (`google.oauth.client-id`) and expiry. A verified email that is unknown opens an `ACTIVE` `BUYER` account; a known email signs into that account and marks it verified. Unverified Google emails and disabled accounts are refused. Disabled when `GOOGLE_OAUTH_CLIENT_ID` is empty. |

Schema: `V61` makes `users.email` nullable (phone-only buyers); the unique constraint stays,
PostgreSQL treats NULLs as distinct.

Purchase-order tie-in: when a buyer whose profile has no phone (a Google sign-up, an old
email-only account) places an order, the order's contact phone is stored on the profile unless
another account already owns that number. That is what later lets them sign in with a WhatsApp
code.

Deliberate choices: quick registration signs the user in before the phone is verified, mirroring
the existing `register` behaviour, so a Twilio outage never blocks a purchase; the code is sent
so verification can be enforced later by product decision. Google accounts are linked by
verified email rather than by Google subject id, which is the usual trade-off when no separate
identity table exists.

---

## 10. Reservation deposit through Chapa

When the seller accepts, the buyer owes the **provider** a reservation deposit, paid through
Chapa's hosted checkout. Card and wallet details are entered on Chapa; this platform stores only
Chapa's transaction reference and the outcome. Chapa does not expose card tokenisation, so there is
no card-on-file: every payment goes through the hosted page.

### 10.1 Amount and lifecycle

`DepositPolicy`: `percent` of the listed price (default 1 %), clamped per currency (ETB 5,000 –
250,000; USD 100 – 2,500), never above the price, due `dueDays` (3) after acceptance. All under
`purchase.deposit.*`; `enabled=false` skips deposits entirely.

```
seller accepts ─► DUE ─(buyer starts checkout)─► PENDING ─(verify: success)─► PAID
                                                    └──(verify: failed)──► FAILED ─(retry)─► PENDING
order closes while DUE/PENDING/FAILED ─► CANCELLED         order closes while PAID ─► REFUND_PENDING ─(admin)─► REFUNDED
admin ─► WAIVED
```

The seller cannot **complete** the sale while the deposit is neither `PAID` nor `WAIVED`. The
**Reservation Deposit Terms** agreement (`RESERVATION_DEPOSIT_TERMS`, seeded active by `V62`,
issued on seller acceptance, blocks completion) must be signed before checkout can start; its
text quotes `{{deposit.amount}}`, `{{deposit.currency}}` and `{{deposit.dueDate}}`, so the deposit
is issued before the acceptance agreements.

### 10.2 Endpoints

| Method & path | Policy | Purpose |
| --- | --- | --- |
| `GET /purchase-orders/{id}/deposit` | `AUTHENTICATED` (viewers of the order) | amount, status, due date, `termsPending`, `checkoutAvailable`, resume URL while pending |
| `POST /purchase-orders/{id}/deposit/checkout` | `BUYER_SECURED` | `POST /transaction/initialize` at Chapa with `tx_ref = <orderNumber>-DEP<n>-<random>`, the buyer's phone/email, `return_url = <frontend>/purchase-orders/{id}?deposit=return`, `callback_url` = our webhook; returns `checkoutUrl`. A pending checkout is resumed (after a verify) rather than duplicated; a failed one gets a new `tx_ref`. |
| `POST /purchase-orders/{id}/deposit/confirm` | `BUYER_SECURED` | when the buyer returns: `GET /transaction/verify/{tx_ref}` and record PAID / FAILED (pending stays pending). Idempotent. |
| `POST /payments/chapa/webhook` | `UNSECURED` | checks the `Chapa-Signature` / `x-chapa-signature` HMAC-SHA256 against `chapa.webhook-secret` (both known variants), then **verifies through the API** — the webhook body is never trusted for the outcome. Repeats are ignored. |
| `POST /admin/purchase-orders/{id}/deposit/waive` | `ADMIN_SECURED` | no deposit needed for this order |
| `POST /admin/purchase-orders/{id}/deposit/refunded` | `ADMIN_SECURED` | records the reference of a refund executed in the Chapa dashboard |

An underpayment reported by verify (amount below the deposit) is recorded as `FAILED` with the
reason, never as paid. `PurchaseDepositPaidEvent` notifies the buyer and the seller.

### 10.3 Configuration

| Key | Env | Meaning |
| --- | --- | --- |
| `chapa.secret-key` | `CHAPA_SECRET_KEY` | `CHASECK_TEST-…` (sandbox) or `CHASECK-…` (live). Empty → checkout endpoints answer "not configured" and the clients show a hint instead of the pay button. |
| `chapa.webhook-secret` | `CHAPA_WEBHOOK_SECRET` | from the Chapa dashboard → Webhooks |
| `chapa.public-api-base-url` | `PUBLIC_API_BASE_URL` | e.g. `https://ethiobuildconnect.et`; builds the `callback_url`. Alternatively set `https://<api>/api/v1/payments/chapa/webhook` as the webhook URL in the dashboard. |
| `app.frontend-base-url` | `FRONTEND_BASE_URL` | where Chapa sends the buyer back |
| `purchase.deposit.*` | `PURCHASE_DEPOSIT_*` | policy, see 10.1 |

Test with Chapa's sandbox key and test cards before switching to the live key; both keys work
against the same `base-url`.

