# Expo lifecycle emails

What a registrant hears from us after they fill in the interest form, when they hear it, and how
to run it. Companion to the frontend's `docs/GOOGLE-ADS.md`, which covers getting people to the
form in the first place.

## The problem this solves

Before this existed, registering interest produced silence. The form saved a row, created a
`PENDING_APPROVAL` organization and waited for an admin to work the queue. Nobody was told the
form had arrived, nobody was told what would happen next, and nobody heard anything again in the
weeks between registering and the expo opening.

That is the most expensive gap in the funnel, because it wastes leads that were already won. Paid
search, SEO and outreach all exist to produce a registration; a registration that goes unanswered
throws that spend away at the last step.

## What gets sent

| When | Kind | Goes to |
| --- | --- | --- |
| Immediately after registering | `CONFIRMATION` | Everyone, including anyone who has unsubscribed |
| 30 days before the expo opens | `REMINDER_T30` | Everyone who has not opted out |
| 7 days before | `REMINDER_T7` | Same |
| 1 day before | `REMINDER_T1` | Same |

The confirmation is written three ways — visitor, exhibitor, partner — because the three want
different things and only one of them is owed a phone call. The exhibitor and partner versions
commit to a response time; the visitor version deliberately does not, because nobody is going to
call a visitor and promising otherwise is how a lead learns not to trust the next email.

The offsets live in `ExhibitionEmailKind`, not in configuration. The send log keys on the kind's
name, so its identity and its timing have to agree: a `REMINDER_T30` row that actually went out at
T-14 would make the log a record of nothing.

### Copy rules

- **Every mail names the dates and the venue.** A reminder that does not say when is not a
  reminder. `ExhibitionEmailComposerTest` asserts this for every kind.
- **Nothing is claimed that the site does not say.** No exhibitor counts, no visitor numbers, no
  "free entry" — the same rule `GOOGLE-ADS.md` applies to ad copy, and for the same reason.
- **Plain text.** Every other mail this platform sends is plain text and these match. A new
  sending domain has no reputation, and a text mail that reads as though a person wrote it clears
  filters that an image-heavy template does not.
- **Links carry campaign tags** (`utm_source=lifecycle&utm_medium=email`), so a click from the
  T-7 mail is not counted as direct traffic, and email can be compared against paid search in the
  same report.

## Running it

Everything is off until SMTP is configured. With `spring.mail.username` empty the dispatcher logs
what it *would* have sent and records the mail as `SUPPRESSED` — the same fail-soft contract the
password-reset mail has, so local and staging work without a mail server.

| Variable | Default | Notes |
| --- | --- | --- |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | Gmail SMTP, blank credentials | Nothing sends until the username is set |
| `MAIL_FROM` | same as `MAIL_USERNAME` | The address readers see. Set it whenever the SMTP login is not an address anyone should reply to — every relay provider. A display name is allowed: `Ethio Build Connect Expo <expo@ethiobuildconnect.et>` |
| `EXPO_START_DATE`, `EXPO_END_DATE` | 2026-11-16 / 2026-11-18 | Must match the site — see below |
| `EXPO_VENUE`, `EXPO_CITY` | Addis Convention Center, Addis Ababa | Same |
| `EXPO_TIMEZONE` | `Africa/Addis_Ababa` | The zone "how many days until" is decided in |
| `EXPO_LIFECYCLE_EMAILS_ENABLED` | `true` | Master switch; off means nothing is sent or logged |
| `EXPO_LIFECYCLE_EMAILS_CRON` | `0 0 * * * *` | Hourly reminder run, in the expo timezone |
| `EXPO_LIFECYCLE_EMAILS_DAILY_QUOTA` | `300` | Mails per calendar day, counted from the send log — set to the relay's cap |
| `EXPO_LIFECYCLE_EMAILS_REPLY_TO` | empty | Set it; "reply to this email" should reach a person |

### Sending for free until there is a budget

Two different things are needed, and no free product does both well:

**An inbox at the domain** — so `expo@ethiobuildconnect.et` exists, replies land somewhere, and a
relay can verify the sender. Forwarding-only services do this for nothing:

- **Cloudflare Email Routing** — free, unlimited addresses, forwards to any Gmail. Requires the
  domain's nameservers to be on Cloudflare (the free plan is enough). Receive only.
- **ImprovMX** — free for one domain and 25 aliases, up to 500 forwards a day. Any DNS host.
  Receive only; sending is a paid add-on.

**A relay to send through** — this is what `MAIL_*` points at. Free tiers that include SMTP and a
verified custom domain with DKIM:

| Relay | Free cap | Fits |
| --- | --- | --- |
| **Brevo** | 300/day, no card, no expiry | Best fit. Set `EXPO_LIFECYCLE_EMAILS_DAILY_QUOTA=270` to leave headroom for confirmations |
| Mailjet | 200/day (6,000/month) | Queues overflow for up to three days — which for the day-before reminder means sending it on opening day. Quota `180` |
| Resend | 100/day (3,000/month), one domain | Fine while the lead list is small. Quota `90` |
| Gmail (personal) | ~500 recipients/day | Works with zero changes, but From is a `@gmail.com` address, and Google is winding down the "send mail as" route to a custom domain. Stopgap only |

Whichever relay: add its SPF include and DKIM records to the domain's DNS before the first send,
and set `MAIL_FROM` to the verified address. A relay will refuse, or silently drop, mail from an
address it has not verified — and verification means receiving a mail at that address, which is
why the inbox comes first.

**The dates must match the site.** `src/features/exhibition/eventDetails.js` and
`exhibition.hero.dateVenue` in the frontend locale files carry the same dates and venue, and the
expo's schema.org markup is held to agreeing with the visible page. A mail that names a different
date than the landing page is worse than no mail. Move all three or none.

**One thing to settle before the first reminder goes out:** `SEO-TARGETS.md` records an open
question about whether the venue is "Addis Convention Center" or "Addis International Convention
Center". The mail repeats whatever the site says, so fixing it in one place fixes it here too.

## Why it cannot double-send

`exhibition_interest_email` holds one row per (registrant, kind), with a unique index on the pair.
A send checks that row first, writes it in the same transaction as the send, and updates it rather
than inserting on a retry. That is what makes a rerun harmless — a redeploy, a restart, a second
cron fire or an operator triggering a catch-up by hand all land on the same row.

- `SENT` and `SUPPRESSED` are settled: never tried again.
- `FAILED` stays retryable, so a mail server having a bad afternoon is picked up by the next run.
- Each send gets its own transaction, so a bounce on the fortieth lead does not roll back the log
  of the thirty-nine already mailed.
- The daily quota is measured from the log too — `SENT` rows since midnight, confirmations
  included, because the relay's cap does not know one from the other. The job runs hourly so a
  reminder the quota cut short is finished later the same day; one it cannot finish by midnight is
  not sent late.

A day that is missed entirely stays missed. "One week to go", sent four days late, tells the
reader we are not paying attention and gives them the wrong date to plan around.

## Unsubscribing

Every reminder carries a link with a 64-character random token. The confirmation does not — it
answers something the reader did seconds ago, and offering an opt-out on a receipt reads as though
we expect them to regret it.

**The GET does not unsubscribe anyone.** Corporate mail gateways, spam filters and link-preview
scanners fetch every URL in a message before a human sees it. If the GET carried out the opt-out,
those scanners would silently unsubscribe registrants who never clicked anything, and nobody would
find out until the mail stopped arriving. So `GET /api/v1/exhibition/interest/unsubscribe?token=…`
renders a page with a button, and the POST does the work.

Opting out suppresses reminders only. Someone who registers and immediately unsubscribes has still
asked us a question, and the confirmation is the reply.

## Where the lead came from

The registration form now posts campaign attribution alongside the lead: `utm_source`,
`utm_medium`, `utm_campaign`, `utm_term`, `utm_content`, the referrer, and the page the visitor
landed on. The browser side is `src/utils/campaignAttribution.js` in the frontend; it is attached
by `exhibitionApi.registerInterest`, so a form added later cannot quietly stop reporting.

This exists because Google Analytics only sees visitors who accepted cookies, and only in
aggregate. This copy is recorded for every registration, attached to the individual lead, and
visible to admins in the exhibition-interests list. It is what lets the question that actually
matters be answered — *which campaign produced exhibitors, not just visitors* — which is the split
`GOOGLE-ADS.md` says should drive where the budget goes.

Attribution is last-campaign-wins and lives in session storage, matching how GA4 attributes a
session, so the two reports can be read side by side. It is listed in the cookie policy as
`housing.campaign`.

## Known gaps

- **English only.** The site serves Amharic at its own URLs, but these mails do not, and the
  form does not record which language the visitor was reading. Machine-guessed Amharic at this
  length would be worse than English — this wants a native writer, then a locale column, in that
  order. The frontend's success message mentions the confirmation email in English only for the
  same reason.
- **No SMS or WhatsApp.** The form takes a phone number and Twilio credentials already exist in
  configuration. For an Ethiopian audience a message on the day before would likely outperform
  email; that is a follow-up, not a gap in this one.
- **No bounce handling.** A rejected address is recorded as `FAILED` and retried on the next run
  of the same reminder. Nothing reads bounce reports, so a permanently dead address is retried
  once per reminder rather than being marked dead.
- **Reminders are date-based, not behaviour-based.** Nobody gets a different mail for having
  opened the last one or visited the site since.
