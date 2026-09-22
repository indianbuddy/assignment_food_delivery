# CLAUDE.md

Guidance for Claude Code (or any agent) working in this repository.

## What this is

A Spring Boot 3.3 / Java 17 backend for a food delivery order management
system, built as a take-home assessment. Full design rationale, assumptions,
and API reference live in `README.md` - read that first for *why* things are
built the way they are. This file is about *how* to work in the repo day to
day.

## Build & test commands

```bash
./gradlew compileJava compileTestJava   # fast compile check
./gradlew test --tests "com.qryde.fooddelivery.service.*" --tests "com.qryde.fooddelivery.security.*"
                                         # unit tests only (Mockito, no Docker, seconds)
./gradlew test                          # full suite, needs a working Docker daemon
./gradlew bootRun                       # run the app (needs Postgres - see README)
```

Always run the unit-test subset first when iterating - it's fast and doesn't
need Docker. Only run the full suite (which spins up Postgres via
Testcontainers) when you need to verify integration/concurrency behavior.

### If Docker isn't already working here

Check `docker ps` first. If there's no Docker daemon, this repo was
developed against colima (`brew install docker colima && colima start`).
Colima needs `DOCKER_HOST` pointed at its socket
(`unix://$HOME/.colima/default/docker.sock`) - either export it or put it in
`~/.testcontainers.properties` as `docker.host=...`. See the "Testing"
section of README.md for two colima-specific Testcontainers gotchas
(API-version negotiation, Ryuk) and how they're already worked around.

## Architecture at a glance

Layered by concern, feature-grouped within each layer:
`domain/ -> repository/ -> service/ -> controller/`, plus `security/`,
`event/`, `dto/`, `exception/` cutting across. See README's "Architecture"
section for the full picture. The two things NOT to casually refactor
without understanding first:

1. **The conditional-`UPDATE` repository methods**
   (`MenuItemRepository#decrementStock`, `OrderRepository#assignPartnerIfUnassigned`,
   `OrderRepository#updateStatusIfCurrent`,
   `DeliveryPartnerRepository#updateStatusIfCurrent`). These are
   single-statement, race-safe-by-construction. Do not "simplify" these into
   a read-then-check-then-write pattern in the service layer - that
   reintroduces exactly the race conditions they exist to prevent. This
   already happened once: `DeliveryPartnerService#acceptAssignment`
   originally used an atomic UPDATE for the *order* side of the "accept an
   assignment" race but a plain read-then-write for the *partner's own*
   `AVAILABLE -> BUSY` flip, which let the same partner win two different
   orders at once - code review caught it, `updateStatusIfCurrent` on
   `DeliveryPartnerRepository` fixed it. If you touch any of these four
   methods, rerun `ConcurrentOrderPlacementTest` and
   `ConcurrentPartnerAssignmentTest` (both tests in the latter file, not just
   one) - they will actually catch a regression, not just report green
   because a mock said so.
2. **`clearAutomatically` on `@Modifying` queries.** It's deliberately
   `false` on the `MenuItem` stock queries (they run mid-transaction inside
   `OrderService#placeOrder`, and clearing the persistence context there
   would detach the `Order`/`OrderItem` entities still being built in the
   same transaction) and `true` on the `Order` status/assignment queries
   (which are always immediately followed by a fresh `findById` because the
   caller needs to see the post-update state). If you add a new
   `@Modifying` query, think through which behavior you actually need
   before copying one or the other.

## Conventions

- DTOs are Java `records`; entities never cross the controller boundary.
- Every service method that mutates state re-derives authorization from the
  authenticated user's id (via `@AuthenticationPrincipal UserPrincipal`
  passed down from the controller), not just from `@PreAuthorize`'s role
  check - see `RestaurantService#assertOwnership`,
  `OrderService#assertAssignedPartner`/`getForViewer`. Role alone doesn't
  prove ownership of a specific row.
- Domain exceptions (`InsufficientStockException`, `ConflictException`,
  `InvalidStateTransitionException`, `AccessDeniedBusinessException`,
  `ResourceNotFoundException`) are mapped centrally in
  `GlobalExceptionHandler` - throw the specific domain exception from
  services, don't return ad hoc `ResponseEntity` error bodies from
  controllers.
- Package-private helper methods (e.g. `OrderService#findEntity`,
  `RestaurantService#findEntity`) are intentionally not `public` - they're
  shared within `service/` (e.g. `RatingService` calls
  `OrderService#findEntity`) but not meant to be called from `controller/`.
  If a controller needs one, that's a sign to add a proper public method
  instead of just widening the access modifier.
- Order-linked or restaurant-linked data (ratings, order details) is only
  ever exposed through the same viewer-authorization logic as the underlying
  order/restaurant (`OrderService#getForViewer`,
  `RestaurantService#assertOwnership`) - never a bare `findById` with no
  ownership check. A missing check exactly like this (on the rating
  endpoints) was a real IDOR bug caught by code review; don't reintroduce
  the pattern on a new endpoint.
- `AuthService#register` refuses `Role.ADMIN` - self-service signup can only
  create `CUSTOMER`/`RESTAURANT_OWNER`/`DELIVERY_PARTNER` accounts. Don't
  relax this; it was a live privilege-escalation bug before it was fixed. The
  only path to an ADMIN account is `AdminBootstrapRunner` (env-var gated,
  see README's "Running locally").

## Testing philosophy

- Unit tests (Mockito) for business-logic sequencing and error handling in
  isolation - fast feedback, no infra.
- Integration tests (Testcontainers + real Postgres) specifically for
  anything where real database semantics matter: the two concurrency
  guarantees (genuinely can't be proven against mocks or H2), the full order
  state machine, async notification fan-out timing, and RBAC over real HTTP.
- `AbstractIntegrationTest` uses a manually-managed singleton container
  (started once, never stopped) rather than `@Testcontainers`/`@Container` -
  see the comment on that class and README's "Testing" section before
  changing it; the more idiomatic-looking annotation-based approach breaks
  under Spring's context caching across multiple test classes.

## Things this project deliberately does not have

No CI/CD config, no Dockerfile/docker-compose for the app itself, no
OAuth/SSO. These are explicitly out of scope per the assignment - don't add
them "for completeness."

`src/main/resources/static/` (`index.html`/`app.js`/`styles.css`) is a
deliberate exception: a UI/frontend is also explicitly out of scope, but a
minimal one was added anyway at the requester's explicit ask, purely to make
the recorded walkthrough demoable - see README's "Demo UI (bonus, out of
scope)" section. It's not held to the same bar as the rest of the codebase
(no tests, no design polish) and calls the same `/api/**` endpoints as any
other client with no special access. Don't let its existence set a precedent
for adding more UI - it's demo scaffolding, not a direction to keep building
in.
