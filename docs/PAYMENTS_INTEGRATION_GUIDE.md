# Receiving purchase-order payments: Chapa today, other providers tomorrow

A practical guide for Dream Teams Trading PLC on how money from a property purchase order reaches
the company, what the platform already does, what you must set up on the provider side, and how
to extend it (full payment, instalments, paying realtors, other gateways).

Companion to `PROPERTY_PURCHASE_ORDERS.md` §10, which documents the code paths in detail.

---

## 1. What is built today

| Payment | Who pays whom | How | Status in code |
| --- | --- | --- | --- |
| **Reservation deposit** (default 1 % of the price, ETB 5,000 – 250,000 / USD 100 – 2,500, due 3 days after the seller accepts) | buyer → **Dream Teams Trading PLC** | Chapa hosted checkout (cards, telebirr, CBE Birr, other wallets Chapa enables for your account) | **Done.** `POST /purchase-orders/{id}/deposit/checkout` → Chapa page → `confirm` / webhook → `PAID`. Blocks completion until paid or waived. |
| **Balance of the purchase price** | buyer → seller (realtor / developer) | **Off-platform** (bank transfer, cheque, financing disbursement). The seller records a reference when they click *Confirm payment & complete sale*. | Manual. No money moves through the platform. |
| **Bank-financed portion** | bank → seller | Loan disbursement handled by the bank after approval | Tracked as loan status only. |

So the platform is currently the merchant for **deposits only**, and those funds settle to the
provider's bank account. Everything below explains how to get that live, and how to grow it.

The relevant code is small and self-contained:

```
payment/chapa/
├── ChapaProperties        secret key, webhook secret, base URL, public API URL (env-driven)
├── ChapaClient            interface: isConfigured(), initialize(), verify()
├── ChapaRestClient        POST /transaction/initialize, GET /transaction/verify/{tx_ref}
├── ChapaSignature         HMAC-SHA256 check of Chapa-Signature / x-chapa-signature
└── ChapaWebhookController POST /api/v1/payments/chapa/webhook (UNSECURED, signature-gated)
purchase/service/impl/PurchaseDepositServiceImpl
                           issue → checkout → confirm/webhook → paid/failed; waive; refund record
purchase/service/DepositPolicy   amount rule
```

Design principles you should keep for any provider:

1. **Hosted checkout, never card data.** Card and wallet details are typed on the provider's page.
   The platform stores only a transaction reference and the outcome, so PCI scope stays minimal.
2. **Verify is the source of truth.** A webhook or a browser redirect only tells us *to go and ask*;
   the outcome is always read back with the provider's verify API. Never mark anything paid from
   a redirect query string or an unverified webhook body.
3. **Idempotent by `tx_ref`.** Our reference (`<orderNumber>-DEP<n>-<random>`) is unique per
   attempt; repeated webhooks and double clicks change nothing.
4. **Underpayment is failure.** If verify reports less than the amount owed, the record is `FAILED`
   with the reason, never `PAID`.

---

## 2. Getting a Chapa merchant account

1. **Sign up** at <https://chapa.co> as a business. You immediately get a **test** secret key
   (`CHASECK_TEST-…`) in *Settings → API*. Test keys work against the same base URL
   (`https://api.chapa.co/v1`) and accept Chapa's test cards and test wallet numbers.
2. **Business verification (KYC)** unlocks the **live** key (`CHASECK-…`) and payouts. Chapa
   typically asks for: trade licence, TIN certificate, business registration, ID of the
   authorised signatory, and the **settlement bank account** in the company's name. Use Dream Teams
   Trading PLC's documents and the CBE account the sponsorship letters already quote, or whichever
   account you want deposits to land in.
3. **Payment methods.** In the dashboard, enable what you want buyers to see on the checkout page:
   telebirr, CBE Birr, M-Pesa, Amole, Awash Birr, local cards, and international Visa/Mastercard.
   International cards and **USD** pricing are enabled on request and reviewed by Chapa; ask for
   them explicitly because diaspora buyers are a target audience.
4. **Webhooks.** *Settings → Webhooks*: set the URL to
   `https://<your api host>/api/v1/payments/chapa/webhook` and copy the **webhook secret**. Chapa
   signs every call with HMAC-SHA256; the platform rejects anything unsigned or mis-signed.
5. **Settlement and fees.** Chapa settles collected funds to the verified bank account on its
   schedule (usually next business day for local methods; cards can take longer) and deducts its
   fee per transaction. Fees differ by method and are negotiable at volume; check the current
   pricing page and put the numbers in your deposit terms if you pass them on.
6. **Limits.** Each method has per-transaction and daily limits set by the wallet or card issuer,
   not by Chapa (telebirr and CBE Birr limits are the ones you will hit first). A 1 % deposit on an
   ETB 8.5 M apartment (ETB 85,000) is fine; the full price is not. Section 5 covers that.

---

## 3. Configuring the platform (15 minutes)

Set these on the API host (the droplet's compose environment or your secret store):

| Variable | Value | Notes |
| --- | --- | --- |
| `CHAPA_SECRET_KEY` | `CHASECK_TEST-…` first, `CHASECK-…` for production | Empty disables checkout; the apps then show a "payments not configured" hint instead of the pay button. |
| `CHAPA_WEBHOOK_SECRET` | from the dashboard | Required for the webhook to be accepted. |
| `PUBLIC_API_BASE_URL` | `https://ethiobuildconnect.et` | Used to build `callback_url` so Chapa notifies us even if the dashboard webhook is not set. |
| `FRONTEND_BASE_URL` | `https://ethiobuildconnect.et` | Where Chapa sends the buyer back (`/purchase-orders/{id}?deposit=return`). |
| `PURCHASE_DEPOSIT_ENABLED` / `_PERCENT` / `_MIN_ETB` / `_MAX_ETB` / `_MIN_USD` / `_MAX_USD` / `_DUE_DAYS` | optional | Defaults: on, 1 %, 5,000 – 250,000 ETB, 100 – 2,500 USD, 3 days. |
| `CHAPA_BASE_URL` | leave default | Same host for test and live. |

Restart the backend. `GET /actuator/health` must be green; the deploy workflow's health gate
already checks this.

### Sandbox end-to-end test (do this before the live key)

1. As a buyer, place an order on a test listing; as the realtor (or an admin via *Manage*),
   **accept** it. The order moves to `AWAITING_PAYMENT` (cash) or `AWAITING_FINANCING`, and a
   deposit appears as `DUE` on the order page with the amount and due date.
2. The buyer signs the **Reservation Deposit Terms** agreement (issued on acceptance), then clicks
   **Pay deposit**. The platform calls Chapa `initialize` and redirects to the hosted page.
3. Pay with a Chapa **test card** (numbers are in the Chapa docs) or a test telebirr number.
4. Chapa redirects back; the page calls `confirm`, which runs `verify` and shows **Paid**. In
   parallel the webhook arrives and is ignored as a repeat. Check the backend log for
   `Chapa webhook` lines to confirm the signature passed.
5. Try the failure path with a declining test card: the deposit shows `FAILED` and the buyer can
   retry, which creates a new `tx_ref`.
6. In the Chapa dashboard, refund the test payment; record it with
   `POST /admin/purchase-orders/{id}/deposit/refunded` (or wait for the admin UI for this).

Switch `CHAPA_SECRET_KEY` to the live key, re-register the webhook secret for live mode, and pay
one real ETB 5,000 deposit yourself before announcing it.

---

## 4. Where the money goes, and paying the realtor

With the setup above, **every deposit lands in Dream Teams Trading PLC's account**, because the
platform is the Chapa merchant and the deposit is contractually owed to the provider (that is what
the Reservation Deposit Terms say). What happens next is a business rule, not code:

- **Keep it** as the platform's reservation fee and let the seller collect the full price
  separately, or
- **Net it off**: the seller confirms the price minus the deposit, and you transfer the deposit to
  them, or
- **Refund it** when the sale falls through (the platform tracks `REFUND_PENDING` → `REFUNDED`
  once you record the refund reference).

If you later want buyers to pay the **seller** through the platform (balance, instalments), you
have two options with Chapa:

1. **Split payments (subaccounts).** Chapa lets you register a **subaccount** for each realtor
   (their bank + account number, `POST /subaccount`) and pass it on `initialize`
   (`"subaccounts": [{"id": "<subaccount id>"}]`) with a split type (`flat` or `percentage`) so
   Chapa routes their share to their bank at settlement and keeps your commission in your account.
   Code change: add `subaccountId` and split fields to `ChapaClient.InitializeRequest`, store the
   subaccount id on the `Organization` (realtor company), and pass it for balance payments only
   (never for the provider's deposit). This is the cleanest way to run a marketplace without
   touching the sellers' money yourself.
2. **Collect and pay out.** Funds land with you; you pay realtors by bank transfer and record it.
   Simpler to start, but you become a money intermediary, with the accounting and regulatory
   weight that carries under NBE rules. Prefer option 1 unless volumes are tiny.

---

## 5. Extending from deposit to full payment and instalments

The deposit machinery is deliberately generic: a `PurchaseDeposit` row is "an amount owed on an
order, paid through a provider, with a lifecycle". To take more than the deposit:

1. **Generalise the row.** Rename conceptually to *purchase payment* and add
   `purpose ∈ {RESERVATION_DEPOSIT, BALANCE, INSTALMENT}` plus a `payee ∈ {PROVIDER, SELLER}`.
   Keep the statuses (`DUE, PENDING, PAID, FAILED, CANCELLED, WAIVED, REFUND_PENDING, REFUNDED`).
   One order then has one deposit and zero or more balance/instalment payments.
2. **Schedule.** On seller acceptance, create the balance payment (price − deposit − financed
   amount for financed orders) as `DUE` with the due date from the sale agreement, or a set of
   instalments if the developer sells on a plan. `PurchaseOrderExpiryJob` already runs every 15
   minutes and is the place to flag overdue payments.
3. **Respect method limits.** Full prices exceed wallet and card ceilings. For each `DUE` payment
   offer the buyer:
   - **Pay online** through Chapa when the amount is under the limit (instalments, small balances,
     diaspora card payments in USD if enabled), and/or
   - **Bank transfer with reference**: show the payee's bank details and a unique reference
     (`<orderNumber>-BAL1`), let the buyer upload the slip, and let the seller or an admin confirm
     it (this is what *Confirm payment & complete sale* does today, made per-payment).
4. **Completion rule.** `PurchaseDepositService.blocksCompletion` becomes "any payment with
   `payee = SELLER` not `PAID`/`WAIVED` blocks completion", so the seller cannot close the sale until
   the price is covered, however it was paid.
5. **Agreements.** Add a `SALE_AGREEMENT` template (already an `AgreementType`) issued on
   acceptance that quotes the payment schedule the same way the deposit terms quote
   `{{deposit.amount}}`.

None of this needs a new provider: the same `initialize`/`verify`/webhook loop works for any
amount Chapa accepts.

---

## 6. Other providers

`ChapaClient` is already an interface with three methods; make it the contract for every gateway:

```java
interface PaymentGateway {
  String id();                                   // "chapa", "telebirr", "stripe"
  boolean isConfigured();
  InitializeResult initialize(InitializeRequest r); // returns a hosted checkout URL
  VerifyResult verify(String txRef);             // provider truth
  boolean verifySignature(String payload, Map<String,String> headers);
}
```

Store `provider` on each payment row (the deposit table already has a `provider` column, set to
`chapa`), route the webhook per provider (`/api/v1/payments/{provider}/webhook`), and let the buyer
pick a method on the order page when more than one gateway is configured. Candidates:

| Provider | Good for | What onboarding involves | Integration notes |
| --- | --- | --- | --- |
| **Chapa** (done) | ETB cards + all major wallets in one integration; USD cards on request | Business KYC, webhook secret | Hosted checkout, verify API, subaccounts for splits. |
| **telebirr** (Ethio telecom) direct | Largest wallet user base; lower fees than going through an aggregator | Merchant agreement with Ethio telecom, RSA key pair, IP allow-listing; slower process | H5/web checkout ("Web Pay"): create order, redirect, notify URL with RSA-signed payload, query API for status. Same hosted-page pattern. |
| **CBE Birr** direct | CBE customers | Merchant agreement with Commercial Bank of Ethiopia | Similar order/notify/query trio. |
| **ArifPay, SantimPay, Kacha, AddisPay** | Alternative Ethiopian aggregators; competitive fees; some offer USSD and bank-to-bank | Business KYC | REST hosted checkout + webhook + verify, same shape as Chapa. Useful as a fallback if Chapa is down or for negotiated rates. |
| **Stripe** | Diaspora cards in USD/EUR/GBP, Apple/Google Pay, card-on-file for instalments | Stripe does not onboard Ethiopian entities; you need a company in a supported country (e.g. a US/UK subsidiary or Stripe Atlas) and settle there, then repatriate | Checkout Sessions + webhooks; excellent tooling. Consider only once diaspora volume justifies a foreign entity. |
| **Flutterwave / Paystack** | Pan-African cards and some ETB rails | Check current Ethiopia availability for *collections* before planning; it changes | Hosted checkout + verify, same shape. |
| **PayPal** | Not viable: Ethiopia is not supported for receiving payments. | | |

Rules of thumb: keep **one aggregator (Chapa) as default**, add **telebirr direct** only when fees
at your volume justify the onboarding effort, and add **Stripe** only through a foreign entity for
diaspora buyers. Every addition is a new `PaymentGateway` implementation plus a webhook route;
the purchase-order code does not change.

---

## 7. Operations: reconciliation, refunds, disputes

- **Daily reconciliation.** Export the Chapa dashboard transactions for the day and compare with
  `purchase_deposits` where `paid_at` is that day (`tx_ref` ↔ Chapa `tx_ref`, `provider_reference`
  ↔ Chapa `reference`). Anything `PENDING` older than an hour: call `confirm` (or let the webhook
  retry) and it resolves to `PAID` or `FAILED`.
- **Refunds** are executed in the Chapa dashboard (Chapa has a refund API too, not wired yet), then
  recorded with the admin endpoint so the order shows `REFUNDED`. The admin UI for waive/refund is
  still API-only; it is a small addition to the admin purchase-orders screen.
- **Disputes/chargebacks** on international cards arrive by email from Chapa; the evidence you
  need is exactly what the platform keeps: the signed Reservation Deposit Terms (with the buyer's
  typed signature, time, IP and user agent) and the order history.
- **Monitoring.** Log lines to alert on: `Rejected Chapa webhook with a bad or missing signature`
  (misconfigured secret or an attack) and `Chapa initialize failed` (provider outage).

---

## 8. Compliance notes for Ethiopia

- The platform never sees card numbers or wallet PINs, so it stays outside PCI DSS scope beyond
  the SAQ-A style basics (TLS everywhere, no logging of payloads with personal data).
- Collecting money on behalf of sellers may bring you under NBE payment-system rules for payment
  instrument issuers / aggregators. Option 1 in §4 (subaccount splits, where the licensed provider
  moves the money) keeps you on the safe side; option 2 (collect and pay out) is the one to run
  past a lawyer before scaling.
- Keep the **agreement texts** (Promise to Purchase, Reservation Deposit Terms) reviewed by
  counsel; they are what a refund or dispute turns on.

---

## 9. Checklist

- [ ] Chapa business account created and verified; live key issued
- [ ] Payment methods enabled (telebirr, CBE Birr, cards; USD/international requested)
- [ ] Webhook URL and secret set in the dashboard
- [ ] `CHAPA_SECRET_KEY`, `CHAPA_WEBHOOK_SECRET`, `PUBLIC_API_BASE_URL`, `FRONTEND_BASE_URL` set on the server; backend restarted and healthy
- [ ] Sandbox deposit paid, failed and refunded once each; webhook seen in the log
- [ ] One real ETB 5,000 deposit paid with the live key
- [ ] Decision recorded: deposit kept / netted / refunded policy (§4)
- [ ] Decision recorded: when to add balance/instalment payments (§5) and whether to use subaccounts
- [ ] Reconciliation routine assigned (§7)
