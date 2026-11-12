# Arbiter Console Assistant Runbook

## Service status

Use `GET /api/v1/status` or the service status tool to check whether the server is UP.
The response includes the server version and server time.

## User store

The encrypted user store is `conf/security/users.dat`.
The crypto key ring is `conf/security/crypto.key`.
Back up both files together because the user store cannot be recovered without its key.

## Initial administrator

On first start, read `conf/security/initial-admin.txt` once, log in as the generated
administrator, and then delete that file. Do not place the password in project configuration.

## Console access

Open `/login.html`, authenticate, and use the browser session. The token is stored in
sessionStorage and sent with the `ARBITER_TOKEN` header.

## Agent assistant

The Console assistant is read-only. It may inspect status, health, redacted recent logs,
sample resources, and this runbook. It must not execute user administration or configuration
changes. For a state-changing request, produce an implementation plan for administrator approval.

## Troubleshooting logs

Server logs are under `logs/console.log`, with rotated archives under the same directory.
When sharing logs with a model, use a small redacted excerpt and exclude tokens, passwords,
API keys, TLS material, and full user records.

## Audit guidance

A diagnostic answer should identify observations, evidence, possible cause, confidence,
recommended next checks, and whether any state was changed. The read-only assistant never
changes state.
