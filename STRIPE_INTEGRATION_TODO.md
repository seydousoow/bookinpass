# Stripe integration — TODO

Book In Pass takes payment with **Stripe Checkout, embedded form** (`ui_mode=form`, API `2026-03-25.dahlia`).
The flight checkout's "Vérifiez et payez" step shows it. The amount is always recomputed server-side from Duffel.
Payments are **authorisations only** (`capture_method=manual`): they are captured once the Duffel order exists.

## Values to Replace

None of the integration's values are placeholders.

- **`mode`**: `payment`, because every booking is a one-off purchase.
- **`line_items`**: not taken from Stripe Price IDs. Each session carries a single `price_data` line built from the
  live Duffel offer: the amount comes from `FlightPricing.sellingTotal`, the name is the IATA route (`DSS → CDG`), and
  the currency is the offer's.

**Files containing these values:**
- [backend/src/main/java/com/bookinpass/backend/client/StripeClient.java](backend/src/main/java/com/bookinpass/backend/client/StripeClient.java)
- [backend/src/main/java/com/bookinpass/backend/service/PaymentService.java](backend/src/main/java/com/bookinpass/backend/service/PaymentService.java)

| Field | Current Value | What to Set |
|-------|--------------|-------------|
| mode | payment | Keep it (one-off charges). Use `subscription` only if recurring products appear one day. |
| line_items[0].price_data | dynamic (offer total, route label) | Nothing to do. Use Price IDs only for fixed-price catalogue products. |

## Configured Parameters

These parameters were configured in Checkout Studio and are already set correctly.

**Files containing these parameters:**
- [backend/src/main/java/com/bookinpass/backend/client/StripeClient.java](backend/src/main/java/com/bookinpass/backend/client/StripeClient.java)
- [frontend/src/app/core/payment/payment-form/payment-form.ts](frontend/src/app/core/payment/payment-form/payment-form.ts) (appearance)

| Parameter | Value |
|-----------|-------|
| ui_mode | form |
| billing_address_collection | required |
| phone_number_collection.enabled | false |
| automatic_tax.enabled | false |
| submit_type | pay |
| name_collection.individual.enabled | true |
| integration_identifier | custom_embedded_web_0001 |
| appearance | stripe theme, floating labels, condensed inputs, Studio colours (`#0570de`…), Inter 14px |

Studio parameters that were **not** applied:

| Parameter | Why |
|-----------|-----|
| saved_payment_method_options.payment_method_save = enabled | Stripe refuses it without a `customer` ("`saved_payment_method_options` requires a customer"), and there are no user accounts yet. Add it together with `customer` once accounts exist (one Stripe Customer per account). |
| locale = fr | Dropped from the latest Studio config. Stripe now uses the browser's language. To follow the site's fr/en switch instead, send the UI language with the request and pass it as `locale`. |
| payment_method_collection | Applies only to `subscription` mode. |

Parameters added for Book In Pass's own flow: `payment_intent_data[capture_method]=manual`,
`return_url={FRONTEND_URL}{return_path}?checkout_session_id={CHECKOUT_SESSION_ID}`, and `metadata` (on the session and
on the PaymentIntent: `offer_id`, `services`, `amount`). The front sends `return_path`, the current page; it must be
a path, so it can't redirect to another origin.

Requests also send an `Idempotency-Key` built from offer + services + amount + `attempt`. The same attempt reopens the
same session (reload, return from a redirect). The front increments `attempt` whenever a payment has been released.

## Setup

1. **Keys.** Put them in `backend/.env`, never in code. `.env.example` lists them:
   - `STRIPE_SECRET_KEY=sk_test_…`: server only.
   - `STRIPE_PUBLISHABLE_KEY=pk_test_…`: sent to the browser with each session, so the frontend needs no env var.
   - `STRIPE_WEBHOOK_SECRET=whsec_…`
   - `FRONTEND_URL=http://localhost:4200`: the site's origin, base of `return_url`.

   ⚠️ A test secret key was pasted into a chat on 2026-10-16. **Roll it** (Dashboard → Developers → API keys) and
   update `.env`.
2. **Webhook.** Endpoint: `POST {API}/payments/webhooks/stripe`. Subscribe to these events (they update
   `stripe_payment`):
   - `checkout.session.completed`: authorisation recorded, even if the customer closes the tab before the booking
   - `checkout.session.async_payment_succeeded` and `checkout.session.async_payment_failed`
   - `payment_intent.succeeded` (captured), `payment_intent.canceled` (released), `payment_intent.payment_failed`
   - `charge.refunded` (refunded or partially refunded)

   Locally: `stripe listen --forward-to localhost:8080/api/payments/webhooks/stripe`, then copy the `whsec_…` it prints.
3. **Payment methods.** The Dashboard settings decide what the form shows. Redirect-based methods come back to `return_url`.
4. **Dependencies.** None added. The backend calls the REST API directly (no Java SDK, because of the GraalVM native
   image). The frontend uses `@stripe/stripe-js` 10.1, whose `loadStripe` loads Stripe.js from `js.stripe.com`
   (never bundled, PCI-compliant) and provides `initCheckoutFormSdk`.

## Files

| File | Role |
|------|------|
| `backend/.../client/StripeClient.java` | `POST /v1/checkout/sessions` |
| `backend/.../client/StripeWebhookVerifier.java` | `Stripe-Signature` check (HMAC-SHA256, 5-minute tolerance) |
| `backend/.../service/PaymentService.java` | Selling price recomputed from Duffel, session creation, webhook handling |
| `backend/.../controller/PaymentController.java` | `POST /payments/flights/{offerId}/checkout-session`, `POST /payments/webhooks/stripe` |
| `backend/.../service/FlightOrderService.java` | Booking: session checked with Stripe, Duffel order, capture or cancel |
| `backend/.../controller/FlightOrderController.java` | `POST /flights/orders` |
| `backend/.../config/StripeProperties.java` | Keys, `api-version`, `webhook-secret`, `frontend-url` |
| `frontend/src/app/core/payment/payment.service.ts` | Calls the backend endpoint |
| `frontend/src/app/core/payment/payment-form/` | `<bip-payment-form>`: embedded form, shared by every booking type |

## How it works

1. On the pay step, the front re-prices the offer with Duffel. Then it sends `{services}` (bags, seats) to
   `/payments/flights/{offerId}/checkout-session`, never an amount.
2. The backend re-reads the offer and seat maps from Duffel and computes the selling price: ticket + BIP Service Fees
   + ancillaries at +20%. It then creates the session and returns `client_secret` and `publishable_key`.
3. `<bip-payment-form>` calls `initCheckoutFormSdk`, `createForm({layout: 'expanded'})` and `loadActions()`. On the
   form's `confirm` event it calls `actions.confirm({formConfirmEvent, redirect: 'if_required'})`. A `complete` status
   (authorisation obtained) emits `(authorised)` with `checkoutSessionId`.
4. The front sends `POST /flights/orders` with the passengers, contact details and `checkout_session_id`. The backend:
   - reads the session back from Stripe (`expand[]=payment_intent`): it must be `complete`, for this offer, with
     `requires_capture`;
   - recomputes the price; if it changed, it cancels the authorisation and returns 409 `price-changed`;
   - creates the Duffel order (`instant`, `payments: balance` at raw Duffel prices, `metadata.checkout_session_id`);
   - **captures** the payment, or **cancels** it if the order fails. After a timeout or a 5xx it first looks for the
     order on Duffel (`GET /air/orders?offer_id=`).

   Replaying the request returns the order already placed.
5. The front shows the booking reference (PNR), the amount charged and the operating carriers (US rule).
6. Stripe sends the `checkout.session.*` events to the webhook, which logs them for tracking.

## Next steps

- [x] Order creation (`POST /flights/orders`), capture or cancel.
- [ ] **Check in Duffel test mode** that `GET /air/offers/{id}` after the price action still gives the ticket alone in
      `total_amount`. The Duffel amount (`balance`) is that total + services. If the services are already included,
      Duffel rejects the amount: the payment is released, but nobody can book.
- [x] Bookings stored after each order (`users`, `flight_order`, passengers, history, `stripe_payment`), statuses
      updated by the Stripe and Duffel webhooks.
- [ ] Authorisation without an order (tab closed between payment and booking): now traced in `stripe_payment`
      (`authorised`, no `order_id`), but it still expires on its own (about 7 days, nothing is charged). Cancel it
      earlier with a scheduled job.
- [ ] Refunds: a Duffel cancellation stores its refund (`flight_order_event.refund_*`), but the customer is not yet
      refunded on Stripe. Decide the policy (BIP fees and markups refunded or not), then refund via
      `POST /v1/refunds`.
- [ ] Capture failed after the order was created: logged as ERROR ("capture it manually"). Add an alert.
- [ ] Concurrent bookings are only locked within a single instance: use Redis (`SET NX`) once there are several.
- [ ] Confirmation email: the contact-field hint promises "Nous enverrons la confirmation…", but nothing sends it
      yet (the airline may send its own).
- [ ] "Remember card": once accounts exist, `customer` + `saved_payment_method_options[payment_method_save]=enabled`.
- [ ] Stays: use `<bip-payment-form>` with a `/payments/stays/...` endpoint built the same way.
- [ ] Before going live: live keys, live webhook, register payment-method domains (Apple Pay / Google Pay).

## Testing

Use a test key (`sk_test_…`); nothing is charged.

| Scenario | Card |
|----------|------|
| Payment succeeds | 4242 4242 4242 4242 |
| 3DS authentication required | 4000 0025 0000 3155 |
| Declined | 4000 0000 0000 9995 |

Any future expiry date and any CVC work. Sessions and authorisations appear in the Dashboard under Payments →
"Uncaptured". Webhooks: `stripe trigger checkout.session.completed`, or a real payment with `stripe listen` running.

## Resources

- https://support.stripe.com
- https://docs.stripe.com/mcp
- https://docs.stripe.com/checkout/form/quickstart
