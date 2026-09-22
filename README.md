# Food Delivery Order Management

A Spring Boot backend for a multi-restaurant, multi-city food delivery platform:
menu management, order placement, the full order lifecycle, delivery-partner
assignment, ratings, and role-based access control.

This README documents the design decisions, the assumptions made to scope an
intentionally open-ended assignment, and how to build/run/test the project.

## Table of contents

- [Problem interpretation & scope](#problem-interpretation--scope)
- [Architecture](#architecture)
- [The two concurrency problems](#the-two-concurrency-problems)
- [Atomicity of order placement](#atomicity-of-order-placement)
- [Async notification fan-out](#async-notification-fan-out)
- [RBAC](#rbac)
- [Tech stack & reasoning](#tech-stack--reasoning)
- [API reference](#api-reference)
- [Assumptions](#assumptions)
- [Running locally](#running-locally)
- [Testing](#testing)
- [AI workflow / Claude Code usage](#ai-workflow--claude-code-usage)
- [Known limitations / explicitly out of scope](#known-limitations--explicitly-out-of-scope)

## Problem interpretation & scope

The brief asked for a food delivery order management system and named four
roles (admin, restaurant owner, customer, delivery partner) and a handful of
hard requirements: no overselling stock under concurrency, no double-assigning
a delivery partner, atomic order placement across stock/state/payment,
non-blocking async status fan-out, and ratings after delivery.

I treated the **two concurrency requirements as the centerpiece** of the
assignment - they're the parts of a food-delivery system that are genuinely
hard to get right, and the parts most likely to be graded closely - and built
everything else (CRUD for cities/restaurants/menu, RBAC, ratings) as a solid,
well-tested supporting cast around them rather than the other way round.

## Architecture

Layered, package-by-feature-within-layer:

```
domain/       JPA entities (User, City, Restaurant, MenuItem, DeliveryPartner,
              Order, OrderItem, Payment, Rating, NotificationLog)
repository/   Spring Data JPA repositories, including the conditional-UPDATE
              queries the concurrency guarantees rely on
service/      Business logic, transaction boundaries, the state machine
controller/   REST endpoints, request validation, @PreAuthorize role checks
security/     JWT issuance/validation, Spring Security wiring
event/        Domain events used for async notification fan-out
dto/          Request/response records (entities never cross the API boundary)
exception/    Domain exceptions + a single @RestControllerAdvice mapping them
              to HTTP status codes with a consistent error body
```

Order lifecycle: `PLACED -> ACCEPTED -> PREPARING -> OUT_FOR_DELIVERY -> DELIVERED`,
with `REJECTED` (only from `PLACED`) and `CANCELLED` (from `PLACED` or
`ACCEPTED`) as terminal side branches. The legal-transition graph lives in
`OrderStatus` itself (`canTransitionTo`), so it's checked in exactly one place.

Delivery-partner assignment is modeled as **orthogonal to the status enum**,
not a status value itself: an order becomes eligible for partner assignment
once it's `ACCEPTED` or `PREPARING` and has no partner yet
(`OrderRepository#findUnassignedInCity`). A partner "claiming" an order just
fills `Order.deliveryPartner`; it doesn't force a status transition. This
keeps "who's delivering this" and "what stage is this order at" independently
queryable and avoids inventing states like `ASSIGNED` that would otherwise
have to interact with every other transition.

## The two concurrency problems

Both are solved the same way: **a single conditional `UPDATE`, not a
read-then-write**. The database's `WHERE` clause is the lock; there is no
`SELECT ... FOR UPDATE`, no optimistic-lock retry loop, and no
application-level mutex.

**Stock oversell** (`MenuItemRepository#decrementStock`):

```sql
UPDATE menu_item SET stock_quantity = stock_quantity - :quantity
WHERE id = :id AND stock_quantity >= :quantity
```

If two customers concurrently order the last unit of an item, both
transactions issue this statement; Postgres serializes the two UPDATEs (one
blocks briefly on the row, then re-evaluates the WHERE clause against the
now-updated row), so **exactly one** gets `stock_quantity >= quantity` = true
and 1 row affected. The other gets 0 rows affected, which `OrderService`
turns into `InsufficientStockException`, rolling back its whole transaction
(see below). Proven under real concurrency in
`ConcurrentOrderPlacementTest` (12 threads, 1 unit of stock, exactly 1 winner).

**Partner assignment race** (`OrderRepository#assignPartnerIfUnassigned`):

```sql
UPDATE orders SET delivery_partner_id = :partnerId
WHERE id = :orderId AND delivery_partner_id IS NULL AND status IN ('ACCEPTED','PREPARING')
```

Same mechanism: N delivery partners can call "accept this order" at the exact
same instant; exactly one UPDATE affects a row, the rest see 0 rows affected
and get a `409 Conflict`. Proven in `ConcurrentPartnerAssignmentTest` (10
partners racing for 1 order).

Order status transitions (`accept`/`reject`/`markPreparing`/...) use the same
pattern (`OrderRepository#updateStatusIfCurrent`, conditioned on the expected
current status) so two concurrent actors on the same order (e.g. a customer
cancelling while the restaurant is accepting) resolve deterministically
instead of one silently clobbering the other.

## Atomicity of order placement

`OrderService#placeOrder` is a single `@Transactional` method that creates the
`Order`, decrements stock per item, and charges the (mocked) `PaymentService`,
all in one transaction. There's no explicit compensation/rollback code for
"stock decremented but payment failed" - **the transaction boundary is the
atomicity mechanism**: if any item's stock reservation fails, or the payment
is declined, an exception propagates out of the method and Spring rolls back
everything that happened in that transaction so far, including stock already
decremented earlier in the same loop. Unit tests
(`OrderServicePlacementTest`) pin down the sequencing with mocks;
`OrderLifecycleIntegrationTest#placingOrderWithMoreThanAvailableStockFailsAndReservesNothing`
proves the rollback against a real database.

A real payment gateway would use a reservation/capture flow with an
idempotency key rather than a synchronous in-transaction call - see
[Assumptions](#assumptions).

## Async notification fan-out

Status changes publish a domain event (`OrderPlacedEvent`,
`OrderStatusChangedEvent`, `PartnerAssignedEvent`) via
`ApplicationEventPublisher`. `NotificationService` listens with
`@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async`, so:

- notifications are never sent for a change that didn't actually commit
  (AFTER_COMMIT), and
- the HTTP request that triggered the change returns as soon as the DB
  transaction commits, without waiting on notification delivery (`@Async`,
  on a dedicated `notificationExecutor` thread pool - not the shared
  ForkJoinPool).

Each listener runs in its own fresh transaction
(`@Transactional(propagation = REQUIRES_NEW)`, required by Spring for a
method that's both `@TransactionalEventListener(AFTER_COMMIT)` and
transactional) so it can safely re-fetch the order and touch its lazy
associations - the original request's persistence context is long gone by
the time this runs on a different thread. In lieu of a real push/email/SMS
provider (out of scope), notifications are written to a `NotificationLog`
table, which is what `NotificationFanOutIntegrationTest` polls to prove the
fan-out actually happens off the request thread.

## RBAC

Four roles (`ADMIN`, `RESTAURANT_OWNER`, `CUSTOMER`, `DELIVERY_PARTNER`),
one role per user account. Self-issued, stateless JWT (`JwtService`,
`io.jsonwebtoken`), validated per-request by `JwtAuthFilter`. Roles are
enforced twice, deliberately at different layers:

- **`@PreAuthorize("hasRole('...')")`** at the controller layer - coarse,
  "can this role even call this endpoint" gating.
- **Ownership checks in the service layer** (`RestaurantService#assertOwnership`,
  `OrderService#assertAssignedPartner`, `OrderService#getForViewer`, ...) -
  fine-grained, "does this *specific* restaurant owner/customer/partner have
  rights to *this* row" checks that a role alone can't express (e.g. two
  different restaurant owners both hold `ROLE_RESTAURANT_OWNER`, but owner A
  must not be able to accept owner B's orders).

Anonymous authentication is disabled at the security-filter level so a
missing/invalid token produces `401 Unauthorized` (via a custom
`AuthenticationEntryPoint`) rather than falling through to a role check and
producing a misleading `403`. An authenticated-but-wrong-role request
correctly gets `403 Forbidden`. Both paths, plus the public read-only browse
endpoints, are covered end-to-end (real HTTP, real JWTs) in
`RbacIntegrationTest`.

`POST /api/auth/register` deliberately refuses `role: "ADMIN"` - self-service
signup can only create `CUSTOMER`, `RESTAURANT_OWNER`, or `DELIVERY_PARTNER`
accounts, none of which grant any capability by themselves (a
restaurant/partner profile still has to be linked to the account by an
existing admin). The very first admin account is provisioned separately via
`AdminBootstrapRunner`, which no-ops unless `ADMIN_BOOTSTRAP_EMAIL` and
`ADMIN_BOOTSTRAP_PASSWORD` are explicitly set - see "Running locally". This
was originally missing (an earlier version accepted any role at
registration) and was caught during the security review described in
`SKILLS.md`.

## Tech stack & reasoning

| Choice | Reasoning |
|---|---|
| Spring Boot 3.3 (Java 17) | Spring Boot 4 is what's on start.spring.io as of this writing, but it's past my ability to be confident about every API surface change - I chose the well-established 3.3 line to prioritize correctness over novelty. |
| Gradle | Maven wasn't installed on the dev machine and Gradle was (via Homebrew); functionally interchangeable for this project. The wrapper is committed, so this is a non-decision for whoever runs it. |
| PostgreSQL + Flyway | The oversell/race-prevention guarantees hinge on specific single-statement UPDATE semantics; testing them against H2 risks masking real Postgres locking/row-visibility behavior. Flyway migrations give an explicit, reviewable schema instead of `ddl-auto: update`. |
| Testcontainers | Integration tests run against a real, ephemeral Postgres - the concurrency tests specifically would be meaningless against a mock or an in-memory DB. |
| Self-issued JWT | Stateless RBAC without pulling in a real OAuth/SSO provider (explicitly out of scope). |
| Lombok | Cuts entity/DTO boilerplate; a normal, low-risk choice for a backend of this size. |

## API reference

All endpoints are under `/api`. Auth: `Authorization: Bearer <jwt>` from
`POST /api/auth/login`.

| Method & path | Role | Purpose |
|---|---|---|
| `POST /api/auth/register` | public | Create an account (email, password, role) |
| `POST /api/auth/login` | public | Get a JWT |
| `GET /api/cities` / `GET /api/cities/{id}` | public | Browse active cities |
| `GET /api/restaurants?cityId=` | public | Browse restaurants in a city |
| `GET /api/restaurants/{id}` / `GET /api/restaurants/{id}/menu-items` | public | Browse a restaurant's available menu |
| `POST /api/admin/cities`, `PATCH /api/admin/cities/{id}/active` | ADMIN | Manage cities |
| `POST /api/admin/restaurants`, `PATCH /api/admin/restaurants/{id}/active` | ADMIN | Onboard/deactivate restaurants |
| `POST /api/admin/delivery-partners` | ADMIN | Onboard a delivery partner profile |
| `GET /api/owner/restaurants` | RESTAURANT_OWNER | My restaurants |
| `POST/GET /api/owner/restaurants/{id}/menu-items` | RESTAURANT_OWNER | Manage menu |
| `PUT /api/owner/menu-items/{id}`, `PATCH .../availability`, `PATCH .../stock` | RESTAURANT_OWNER | Edit a menu item |
| `GET /api/owner/restaurants/{id}/orders` | RESTAURANT_OWNER | Orders for my restaurant |
| `POST /api/owner/orders/{id}/accept` \| `/reject` \| `/preparing` | RESTAURANT_OWNER | Order lifecycle actions |
| `GET /api/owner/restaurants/{id}/ratings` | RESTAURANT_OWNER | Ratings received |
| `POST /api/orders` | CUSTOMER | Place an order (atomic stock+order+payment) |
| `GET /api/orders/mine` | CUSTOMER | My orders |
| `GET /api/orders/{id}` | owner of the order (any role) | Track one order |
| `POST /api/orders/{id}/cancel` | CUSTOMER | Cancel (only while PLACED/ACCEPTED) |
| `POST /api/orders/{id}/rating` \| `GET .../rating` | CUSTOMER | Rate a delivered order |
| `PATCH /api/delivery/me/status` | DELIVERY_PARTNER | Set AVAILABLE/BUSY/OFFLINE |
| `GET /api/delivery/available-orders` | DELIVERY_PARTNER | Unclaimed orders in my city |
| `POST /api/delivery/orders/{id}/accept` | DELIVERY_PARTNER | Race to claim an order (409 if lost) |
| `GET /api/delivery/orders/mine` | DELIVERY_PARTNER | My assignments |
| `POST /api/delivery/orders/{id}/out-for-delivery` \| `/delivered` | DELIVERY_PARTNER | Delivery lifecycle actions |

## Assumptions

Documented here as required by the assignment; each is a scoping decision I
made where the brief left room for interpretation.

1. **One role per user account.** A person who wants to be both a customer
   and a delivery partner registers two accounts. Simpler RBAC model; the
   brief describes roles as a fixed set of hats, not simultaneous ones.
2. **Restaurants and delivery partners are admin-onboarded**, matching "admin
   manages... restaurants and delivery partners" literally - a restaurant
   owner can't self-register a restaurant, and a delivery partner can't
   self-register a partner profile (though they can register a *user*
   account with the `DELIVERY_PARTNER` role; an admin then links it to a
   city via `POST /api/admin/delivery-partners`).
3. **Partner assignment is pull-based (partners browse and claim), not
   push-based (system auto-assigns nearest partner).** This is what makes
   "multiple partners contending for the same order" a meaningful scenario
   to design for at all - an auto-assignment system wouldn't have that race
   in the same form. Nearest-partner routing would need geo data the brief
   doesn't ask for.
4. **Stock is reserved at order placement, not at restaurant acceptance.**
   A restaurant might reject an order for reasons unrelated to stock; when
   that happens (or the customer cancels pre-acceptance) the reserved stock
   is explicitly restocked (`MenuItemRepository#restock`).
5. **Payment is mocked, in-process, and synchronous** (`PaymentService`),
   deliberately, so it can participate in the same transaction as the stock
   decrement and order creation, which is what lets "atomically reflect item
   stock, order state, and payment" be satisfied by a single `@Transactional`
   boundary rather than a saga. A real gateway is an external system and
   would need a reservation/capture flow with an idempotency key and
   likely a transactional outbox - genuinely out of scope for this exercise.
6. **Notifications are logged to a database table**, not actually pushed
   anywhere (no email/SMS/push provider - explicitly out of scope). This
   keeps the fan-out behavior observable and testable.
7. **Ratings are one-per-order**, covering both the restaurant and the
   delivery experience in a single submission (`restaurantRating` +
   `deliveryRating`), only allowed once an order reaches `DELIVERED`, and
   only by that order's customer.
8. **Viewing a single order (`GET /api/orders/{id}`) is restricted** to the
   customer who placed it, the restaurant that owns it, the assigned
   delivery partner, or an admin - not just "any authenticated user," even
   though the brief doesn't say so explicitly. Leaving that open would let
   any logged-in customer page through other customers' order details by
   guessing IDs.
9. **A menu item's price is snapshotted onto the order line
   (`OrderItem.priceAtOrder`)** at placement time, so a later price change
   doesn't rewrite historical order totals.
10. **Self-registration cannot create an ADMIN account** (see RBAC section
    above) - admins are provisioned via `ADMIN_BOOTSTRAP_EMAIL`/
    `ADMIN_BOOTSTRAP_PASSWORD` instead.

## Running locally

Requires Java 17+ and a PostgreSQL instance (no `docker-compose.yml` is
included - containerization is explicitly out of scope for this assignment -
but any local Postgres works, e.g. `docker run -e POSTGRES_DB=food_delivery
-e POSTGRES_USER=food_delivery -e POSTGRES_PASSWORD=food_delivery -p
5432:5432 postgres:16-alpine`, or a native install).

```bash
ADMIN_BOOTSTRAP_EMAIL=admin@example.com ADMIN_BOOTSTRAP_PASSWORD=change-me ./gradlew bootRun
```

The bootstrap env vars are only needed once (first startup) - they no-op on
subsequent runs once that admin account already exists; omit them entirely
if you don't need an admin for what you're testing.

Configuration (`src/main/resources/application.yml`) reads from environment
variables with sane local defaults: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`,
`JWT_SECRET`, `JWT_EXPIRATION_MS`, `SERVER_PORT`. Flyway migrates the schema
automatically on startup.

## Testing

```bash
./gradlew test
```

Two tiers:

- **Unit tests** (`service/*Test.java`, `security/JwtServiceTest.java`) -
  Mockito, no Spring context, no database. Fast; pin down business-logic
  sequencing and error handling in isolation.
- **Integration tests** (`integration/*Test.java`) - full `@SpringBootTest`
  against a real, ephemeral Postgres via Testcontainers. This is where the
  concurrency guarantees are actually proven (real threads, real
  transactions, real row locking), alongside the full order lifecycle,
  async notification fan-out, and RBAC over real HTTP with real JWTs.

Integration tests need a working Docker daemon. If you're on Docker Desktop
this is typically zero-config. **If you're on colima** (as this was
developed with) you may hit two known rough edges, both already worked
around in this repo:

- Very new Docker Engine builds (colima ships one) reject docker-java's old
  default API-version negotiation - worked around via
  `src/test/resources/docker-java.properties` (`api.version=1.44`).
- Colima's Ryuk (Testcontainers' auto-cleanup sidecar) can fail to start
  because it can't bind-mount the docker socket path from outside colima's
  shared mounts - if you hit this, set `ryuk.disabled=true` in your own
  `~/.testcontainers.properties` (machine-local; not something this repo
  should force on Docker Desktop users, so it's not committed).

`AbstractIntegrationTest` starts one Postgres container as a manually-managed
singleton (started once in a static initializer, never explicitly stopped)
rather than via `@Testcontainers`/`@Container` - the latter ties the
container's lifecycle to each test *class*, which fights with Spring's
`ApplicationContext` caching across classes that share identical
configuration (the container gets torn down after the first class while
later classes keep reusing the cached, now-dangling context). This is
Testcontainers' own documented pattern for this exact situation.

## AI workflow / Claude Code usage

Built with Claude Code (Sonnet 5) in an interactive, design-first session.
See `CLAUDE.md` for the persistent project context/conventions given to the
agent. Roughly, the workflow was:

1. Discuss and lock the design up front (entities, API surface, the
   concurrency strategy, tech stack) with the user before writing any code,
   using the assignment's own emphasis on "your interpretation is part of
   what's evaluated" as the driver for the assumptions above.
2. Scaffold the project (Gradle, not Maven - not installed on the dev
   machine; Spring Initializr's hosted service had moved its default to
   Spring Boot 4, so the build file was hand-written pinned to 3.3.x instead,
   for reasons explained in the tech-stack table above).
3. Build bottom-up: domain entities -> repositories (with the conditional
   `UPDATE` queries) -> security -> services (business logic and
   transaction boundaries) -> controllers -> tests.
4. Stand up a local Docker runtime (colima, since neither Docker nor a
   runtime were present on the dev machine) specifically so the
   Testcontainers-based integration tests - the ones that actually prove
   the concurrency guarantees - could be run and verified, not just
   compiled.
5. Iteratively debug and fix real environment issues surfaced by actually
   running the suite (colima/Testcontainers API-version and Ryuk
   incompatibilities, a Spring-context-caching/container-lifecycle bug) and
   real code bugs the tests caught (a `LazyInitializationException` in the
   async notification listeners; a detached-entity persistence bug after a
   bulk-update clear; a 401-vs-403 RBAC nuance around Spring Security's
   anonymous authentication) - all documented at the point they were fixed.
6. Run the `code-review` and `security-review` Claude Code skills over the
   complete implementation (parallel finder sub-agents, then independent
   verification of each candidate finding) before considering it done -
   this caught a critical privilege-escalation bug (self-registration could
   mint an ADMIN account) and a real gap in the partner-assignment
   concurrency guarantee (a partner could be double-booked across two
   orders, since only the order side of that race had been made atomic, not
   the partner's own status flip), among other fixes. Full findings and
   resolutions in `SKILLS.md`.
7. Write this README and `CLAUDE.md` last, once the implementation, test
   suite, and review fixes were all green, so they describe what was
   actually built rather than what was planned.

## Known limitations / explicitly out of scope

Per the assignment's own "Out of Scope" section: no UI/frontend, no
deployment/containerization/CI-CD for the app itself, no microservices, no
OAuth/SSO/MFA, no production-grade observability. Additionally, not
implemented (would be natural next steps, not attempted here): pagination on
list endpoints, refresh tokens / token revocation, idempotency keys on order
placement (a retried POST could currently double-order), geo-based partner
routing, and a real payment/notification provider integration.

Two lower-priority items surfaced by the code review in `SKILLS.md` were
deliberately left as follow-ups rather than fixed, to keep the change surface
focused on genuine defects: the `assertOwnership`-shaped ownership check is
hand-duplicated across three services instead of extracted into one shared
helper, and the order-list endpoints (`GET /api/orders/mine` and similar)
have an N+1 query pattern from unfetch-joined lazy associations - a real
performance concern under load, but not a correctness one.
