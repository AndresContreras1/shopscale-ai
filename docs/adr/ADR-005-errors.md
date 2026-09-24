# ADR-005: RFC 9457 problem details with a stable error catalog

**Status:** Accepted · **Date:** 2026-09-24

## Context

The API returned a custom error body: a status, a reason phrase and a message. A client that needs
to tell two different 409 responses apart has to match on English prose, which breaks the moment the
wording improves or the message gets translated.

## Decision

Every error is an RFC 9457 problem document served as `application/problem+json`, produced by one
`@RestControllerAdvice`. The filter chain writes the same shape by hand for the errors that happen
before a controller runs, so clients need one parser and not two.

The `ProblemType` enum is the catalog. Each entry owns a stable `type` URI under
`https://gamestore.co/problems/` and a stable `code`, and clients branch on the code.

| Member | Meaning |
|---|---|
| `type` | Stable URI identifying the kind of problem |
| `title` | Short human summary, safe to change |
| `status` | HTTP status |
| `detail` | What went wrong in this particular case |
| `instance` | The request path |
| `code` | Extension member: the stable slug clients match on |
| `fieldErrors` | Extension member: field name to message, for validation failures |

Internal details never reach the client. A stack trace goes to the log with the request path; the
response carries a code.

## Consequences

- Adding an error means adding a catalog entry, which makes the full list of failures visible in one
  file and documentable.
- The frontend reads `detail`, falls back to `title`, and renders `fieldErrors` next to the inputs.
- Status codes stay semantic, and the code distinguishes the cases that share one.
- Error messages can be translated without breaking a single client.
