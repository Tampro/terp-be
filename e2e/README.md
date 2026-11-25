# Live E2E suites

End-to-end checks against a running ERP stack, through the gateway. Each suite logs in as the super admin, creates
**two fresh test companies** (A and B) with obviously fake data, and checks one area of the system. That covers
business rules, company isolation (B must get 404 on A's data), permissions (403) and authentication (401).

They complement the unit tests (`mvn package`). Several real bugs were found only by these suites; unit tests with
mocked HTTP passed in each case:
- double URL encoding
- a missing `@Autowired` that stopped media-service from starting
- the AWS SDK checksum framing stored as file content
- a REPEATABLE READ race on contract signing

## Prerequisites
- The ERP stack is running: `docker compose -p erp up -d` (see the root `README.md` for the env vars).
- The super admin's credentials are in `~/.terp-e2e/erp-superadmin.txt`, three lines: a label, the username and the
  password. This file stays **outside the repo**; the suites never contain a secret.
  On a fresh stack (`down -v`, CI) `node e2e/bootstrap.mjs` creates it: it sets a random superadmin password in
  place of the temporary one and saves it there. CI (`.github/workflows/ci.yml`, job `e2e`) boots the stack with
  throwaway secrets and runs `hrm sales purchase acc sup seat` on every PR.
- Node 20+ (uses `fetch`).

## Run
```bash
node e2e/run.mjs                    # every suite except opt-in ones (~30 min)
node e2e/run.mjs acc sales own      # only these
node e2e/run.mjs --all              # also opt-in suites (s3 needs the seaweedfs container)
node e2e/run.mjs --verbose hrm      # print every PASS line
node e2e/run.mjs --keep acc         # keep the test companies afterwards
E2E_GATEWAY=http://localhost:18080 node e2e/run.mjs
```
The exit code is 0 only when every selected suite is fully green. Suites listed in `suites.json` are:
- **standalone:** the suite carries its own head
- **publicWrites:** it writes to public, rate-limited endpoints (burst 5, 10/min per IP); the runner waits before it
- **optIn:** skipped unless `--all` is given, or the suite is named

## Writing a suite
A suite in `suites/<name>.mjs` is the **body** that runs after `lib/head.mjs`. The head provides:
- `c(method, path, body, token)` → `{s, code, msg, data}`
- `ck(name, ok, info)`
- `pw()` → a random password
- `sa` (the super-admin token)
- `A` and `B` (`{id, admin, t}` for each company); `t` = A's token
- `x` (a random suffix for unique names)
- `G` (the gateway base)
- the counters `n` and `f`

End the suite with `console.log(`\n${n - f}/${n} passed`)`. Add it to `suites.json` with a two-to-four-letter
company prefix and a one-line description.

## Rules
- **Never send data off the machine.**
  - Use unresolvable hosts (`*.invalid`) for webhooks and SMTP.
  - Keep chat integrations' per-event switches off before enabling them.
  - Never enable a payment or AI provider while a request could pass its pre-checks; see `pp`, `ai` and `gc`.
- **Fake data only:** `@example.com` / `.invalid` addresses, `Test …` names. No real people, companies or keys.
- **Restore platform-wide settings in `finally`.** These are the storage driver (`s3`), the PayPal switch (`pp`) and
  the platform email toggles (`pl`).
- After the suites, `run.mjs` soft-deletes the test companies created during the run, through the super-admin API.
  Their Keycloak accounts are removed; business rows in the other services stay. Pass `--keep` to inspect them.
  Leftovers from earlier runs can be removed with `node e2e/cleanup.mjs [--since 2026-11-01] [--dry-run]`.
  Only companies whose code ends in `_` plus 6 hex characters and whose email is `@example.com` are touched.
