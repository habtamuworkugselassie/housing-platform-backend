#!/usr/bin/env bash
# Reset a platform account's password directly in the production database.
#
# Usage (on the droplet, as root or the deploy user):
#   bash deploy/reset-password.sh                         # superadmin@ethiobuildconnect.et
#   bash deploy/reset-password.sh someone@example.com     # any account
#
# The password is prompted for and never echoed, logged, or passed on a command line.
# Hashing happens inside the postgres container (pgcrypto, bcrypt cost 10), which is the
# same format Spring's BCryptPasswordEncoder verifies, so the new password works at once.
#
# Why this exists: V52 seeded the super admin with a hash whose plaintext is recorded
# nowhere, and bcrypt is one-way. There is nothing to "look up" — set a new one.
set -euo pipefail

EMAIL="${1:-superadmin@ethiobuildconnect.et}"
EMAIL="$(printf '%s' "$EMAIL" | tr '[:upper:]' '[:lower:]')"
COMPOSE_FILE="$(cd "$(dirname "$0")" && pwd)/docker-compose.prod.yml"

# Resolve the postgres container through compose so a renamed project still works.
CONTAINER="$(docker compose -f "$COMPOSE_FILE" ps -q postgres 2>/dev/null || true)"
if [ -z "$CONTAINER" ]; then
  echo "postgres container is not running (docker compose -f $COMPOSE_FILE ps -q postgres returned nothing)." >&2
  exit 1
fi

# The app's own policy (SetAccountPasswordRequest): >= 8 chars, one upper, one lower, one digit.
# Enforced here so the password can also be changed from the UI later.
while :; do
  read -r -s -p "New password for $EMAIL: " PW; echo
  read -r -s -p "Repeat it: " PW2; echo
  if [ "$PW" != "$PW2" ]; then echo "They do not match — try again."; continue; fi
  if [ "${#PW}" -lt 8 ] || ! [[ "$PW" =~ [a-z] ]] || ! [[ "$PW" =~ [A-Z] ]] || ! [[ "$PW" =~ [0-9] ]]; then
    echo "Must be at least 8 characters with an uppercase letter, a lowercase letter and a digit."; continue
  fi
  break
done
unset PW2

# :'pw' and :'email' are psql variables, quoted by psql itself — no shell or SQL
# interpolation of the password, so any character in it is safe.
RESULT="$(docker exec -i -e PW="$PW" -e EMAIL="$EMAIL" "$CONTAINER" sh -c \
  'psql -v ON_ERROR_STOP=1 -qAt -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v pw="$PW" -v email="$EMAIL"' <<'SQL'
CREATE EXTENSION IF NOT EXISTS pgcrypto;
UPDATE users
   SET password_hash = crypt(:'pw', gen_salt('bf', 10)),
       status        = 'ACTIVE',
       updated_at    = now(),
       updated_by    = 'deploy/reset-password.sh'
 WHERE email = :'email';
SELECT count(*) FROM users WHERE email = :'email' AND password_hash LIKE '$2a$10$%';
SQL
)"
unset PW

if [ "$(printf '%s' "$RESULT" | tail -n1)" = "1" ]; then
  echo "Password updated for $EMAIL. Sign in at https://ethiobuildconnect.et/login"
else
  echo "No account with email $EMAIL — nothing changed." >&2
  echo "Existing accounts can be listed with:" >&2
  echo "  docker exec -i $CONTAINER sh -c 'psql -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" -c \"select email, status from users order by created_at\"'" >&2
  exit 1
fi
