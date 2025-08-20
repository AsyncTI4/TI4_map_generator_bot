# Spring Module

This package exposes the REST API used by the TI4 map generator bot. Controllers live under
`ti4.spring.controller` and are accessed via `/api/**` paths.

## Request flow

1. **CircuitBreakerFilter** – runs first (`@Order(1)`). If the global circuit breaker
   is open it throws `ServiceUnavailableException`, returning a `503` response.
2. **Spring Security** – authenticates the request using `DiscordOpaqueTokenIntrospector`
   and builds the security context.
3. **ErrorLoggingFilter** – executes after authentication (`@Order(3)`) and logs any
   exception or non-success status code returned by downstream handlers.
4. **GameLockAndRequestContextInterceptor** – applied to `/api/game/{gameName}/**` paths.
   It locks the game for read or write based on the HTTP method, sets up
   `RequestContext` with the game and player, and releases the lock after the request
   completes (saving the game on successful writes).

After these steps the request reaches the controller method.

## Access control

Game endpoints annotate controller methods with
`@PreAuthorize("@security.canAccessGame(#gameName)")`. The accompanying
`GameSecurityService` verifies that the authenticated user belongs to the
requested game and throws `UserNotInGameForbiddenException` when they do not.
