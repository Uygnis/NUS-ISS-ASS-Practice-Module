#!/bin/sh
# RentEZ — create the three DynamoDB tables in DynamoDB Local.
# rentez_startup_project_v1_1_20260810_1421SGT
#
# Key schemas match the PRDs exactly (PRD 01 M1.4, PRD 02 M2.4, PRD 06 M6.4).
# This matters: DynamoDB partition and sort keys are IMMUTABLE. Getting them
# right locally now is free; changing them after data exists means creating a
# new table and migrating.
#
# Idempotent — re-running is a no-op for tables that already exist.
set -eu

DDB="aws dynamodb --endpoint-url ${DDB_ENDPOINT:-http://dynamodb:8000}"

# DynamoDB Local ships without curl or nc, so it cannot carry a Docker
# healthcheck. Poll the API instead.
echo "==> waiting for DynamoDB Local at ${DDB_ENDPOINT:-http://dynamodb:8000}"
i=0
until $DDB list-tables >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -gt 30 ]; then
    echo "    DynamoDB Local did not become ready after 60s" >&2
    exit 1
  fi
  sleep 2
done
echo "    ready"

exists() { $DDB describe-table --table-name "$1" >/dev/null 2>&1; }

# ---------------------------------------------------- rentez-sessions (M1)
# PK jti (the JWT ID). TTL attribute `ttl` so expired sessions self-delete.
# GSI userPublicId-index makes "revoke all sessions for this user" a single
# query rather than a table scan (PRD 01 FR-M1-15/20/24).
if exists rentez-sessions; then
  echo "    rentez-sessions already exists"
else
  $DDB create-table \
    --table-name rentez-sessions \
    --attribute-definitions \
        AttributeName=jti,AttributeType=S \
        AttributeName=userPublicId,AttributeType=S \
        AttributeName=issuedAt,AttributeType=N \
    --key-schema AttributeName=jti,KeyType=HASH \
    --global-secondary-indexes '[{
        "IndexName": "userPublicId-index",
        "KeySchema": [
          {"AttributeName": "userPublicId", "KeyType": "HASH"},
          {"AttributeName": "issuedAt",     "KeyType": "RANGE"}
        ],
        "Projection": {"ProjectionType": "ALL"}
      }]' \
    --billing-mode PAY_PER_REQUEST >/dev/null
  # DynamoDB Local accepts the TTL call but does not actually expire items.
  $DDB update-time-to-live --table-name rentez-sessions \
    --time-to-live-specification "Enabled=true,AttributeName=ttl" >/dev/null 2>&1 || true
  echo "    created rentez-sessions (PK jti, GSI userPublicId-index)"
fi

# ------------------------------------------------ rentez-availability (M2)
# A read-optimised projection of the next 90 days, maintained by consuming
# booking events. THIS IS A CACHE, NOT A SOURCE OF TRUTH — the authoritative
# answer to "is this vehicle free" lives in reservation-service.
if exists rentez-availability; then
  echo "    rentez-availability already exists"
else
  $DDB create-table \
    --table-name rentez-availability \
    --attribute-definitions \
        AttributeName=vehiclePublicId,AttributeType=S \
        AttributeName=date,AttributeType=S \
    --key-schema \
        AttributeName=vehiclePublicId,KeyType=HASH \
        AttributeName=date,KeyType=RANGE \
    --billing-mode PAY_PER_REQUEST >/dev/null
  $DDB update-time-to-live --table-name rentez-availability \
    --time-to-live-specification "Enabled=true,AttributeName=ttl" >/dev/null 2>&1 || true
  echo "    created rentez-availability (PK vehiclePublicId, SK date)"
fi

# ------------------------------------------------------ rentez-audit (M6)
# PK entityKey = {entityType}#{entityId}, so one entity's whole history is a
# single query. SK occurredAtEventId = {ISO-8601}#{eventId}, which is unique
# and chronologically ordered — including the event ID guarantees PutItem
# never silently overwrites a record that shares a millisecond.
if exists rentez-audit; then
  echo "    rentez-audit already exists"
else
  $DDB create-table \
    --table-name rentez-audit \
    --attribute-definitions \
        AttributeName=entityKey,AttributeType=S \
        AttributeName=occurredAtEventId,AttributeType=S \
        AttributeName=actorUserId,AttributeType=S \
        AttributeName=action,AttributeType=S \
        AttributeName=correlationId,AttributeType=S \
        AttributeName=occurredAt,AttributeType=S \
    --key-schema \
        AttributeName=entityKey,KeyType=HASH \
        AttributeName=occurredAtEventId,KeyType=RANGE \
    --global-secondary-indexes '[
      {"IndexName": "actor-index",
       "KeySchema": [{"AttributeName":"actorUserId","KeyType":"HASH"},
                     {"AttributeName":"occurredAt","KeyType":"RANGE"}],
       "Projection": {"ProjectionType":"ALL"}},
      {"IndexName": "action-index",
       "KeySchema": [{"AttributeName":"action","KeyType":"HASH"},
                     {"AttributeName":"occurredAt","KeyType":"RANGE"}],
       "Projection": {"ProjectionType":"ALL"}},
      {"IndexName": "correlation-index",
       "KeySchema": [{"AttributeName":"correlationId","KeyType":"HASH"},
                     {"AttributeName":"occurredAt","KeyType":"RANGE"}],
       "Projection": {"ProjectionType":"ALL"}}
    ]' \
    --billing-mode PAY_PER_REQUEST >/dev/null
  echo "    created rentez-audit (PK entityKey, SK occurredAtEventId, 3 GSIs)"
fi

echo "==> DynamoDB ready:"
$DDB list-tables --output text | sed 's/^/    /'
