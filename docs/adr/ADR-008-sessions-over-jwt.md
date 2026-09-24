# ADR-008: Server sessions in a cookie instead of a JWT in the browser

**Status:** Accepted · **Date:** 2026-09-24

## Context

The demo authenticated with a JSON Web Token kept in `localStorage` and sent as a bearer header. That
is what the course taught and it works, but it has one property that a real store cannot accept: any
JavaScript running on the page can read `localStorage`. A single cross-site scripting hole, in our
code or in any dependency, hands over a token that is valid for two hours and cannot be revoked.

A stateless token also cannot be taken back. Signing someone out, disabling an account or blocking a
session means waiting for the expiry, or building the server-side list that a session already is.

## Decision

Authentication is a server session stored in Redis, addressed by a cookie.

| Property | Value | Reason |
|---|---|---|
| Name | `__Host-SID` | The prefix is a rule the browser enforces: Secure, `Path=/`, no `Domain`, so a sibling subdomain cannot overwrite it |
| `HttpOnly` | yes | Script on the page cannot read it, so XSS cannot steal the session |
| `Secure` | yes | Never sent over plain HTTP |
| `SameSite` | `Lax` | Not sent on cross-site POSTs, while normal navigation still works |
| Lifetime | 8 hours idle | Long enough for a working day |

Because cookies are attached by the browser to any request, CSRF comes back as a risk, so it is
turned on: `csrf.spa()` issues a readable `XSRF-TOKEN` cookie that the page copies into the
`X-XSRF-TOKEN` header. An attacker's site can make the browser send the session cookie, but the same
origin policy stops it reading the token cookie, so the header cannot be forged.

The session lives in Redis, not in a replica's memory, so any replica can serve any request and a
deploy does not sign everybody out.

## Consequences

- Signing out is real: the session is deleted and the cookie resolves to nothing. Blocking a single
  session or every session of one account becomes possible.
- Redis is now required to serve authenticated traffic. It was already required for the shared cache
  and the rate limiter, so this adds no new component.
- The frontend no longer stores anything about the user. It asks `/api/auth/me` on load, which means
  a tampered `localStorage` entry cannot grant a role.
- Every state-changing request needs the CSRF header. Angular does this by itself for same-origin
  requests once the cookie exists, and the cookie is issued on the first GET.
- The JWT knowledge is not wasted: tokens are still the right answer for server-to-server calls and
  for third-party API access, which arrive in a later phase.
