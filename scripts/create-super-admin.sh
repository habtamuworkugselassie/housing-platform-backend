#!/bin/bash

# Create or promote a SUPER_ADMIN user.
#
# SUPER_ADMIN is the tier that manages platform admins and issues sponsor-company logins
# (Admin -> Organizations -> Accounts). It grants both the `admin` and `super_admin` scopes,
# so it reaches every admin screen on its own.
#
# Usage:
#   ./create-super-admin.sh                                   # prompts for everything
#   ./create-super-admin.sh boss@example.com Ada Lovelace     # prompts for the password only
#   ./create-super-admin.sh --promote-only boss@example.com   # grant the role, keep the password
#
# Database connection comes from the usual env vars (same as the other scripts here):
#   DB_HOST DB_PORT DB_NAME DB_USER DB_PASSWORD
# If `psql` is not installed, the script falls back to the docker-compose postgres container.

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SQL_FILE="$SCRIPT_DIR/create-super-admin.sql"

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-housing_platform}"
DB_USER="${DB_USER:-postgres}"
DOCKER_SERVICE="${DOCKER_SERVICE:-postgres}"

PROMOTE_ONLY=false
if [ "${1:-}" = "--promote-only" ]; then
    PROMOTE_ONLY=true
    shift
fi

EMAIL="${1:-}"
FIRST_NAME="${2:-}"
LAST_NAME="${3:-}"

# --- collect the account details -------------------------------------------

if [ -z "$EMAIL" ]; then
    read -r -p "Super admin email: " EMAIL
fi
if [ -z "$EMAIL" ]; then
    echo -e "${RED}Error: email is required.${NC}" >&2
    exit 1
fi

if [ "$PROMOTE_ONLY" = false ]; then
    if [ -z "$FIRST_NAME" ]; then
        read -r -p "First name: " FIRST_NAME
    fi
    if [ -z "$LAST_NAME" ]; then
        read -r -p "Last name: " LAST_NAME
    fi
fi
FIRST_NAME="${FIRST_NAME:-Super}"
LAST_NAME="${LAST_NAME:-Admin}"

# --- password --------------------------------------------------------------

PASSWORD_HASH=""
if [ "$PROMOTE_ONLY" = false ]; then
    if [ -z "${SUPER_ADMIN_PASSWORD:-}" ]; then
        read -r -s -p "Password (min 8 chars, 1 upper, 1 lower, 1 digit): " SUPER_ADMIN_PASSWORD
        echo ""
        read -r -s -p "Confirm password: " PASSWORD_CONFIRM
        echo ""
        if [ "$SUPER_ADMIN_PASSWORD" != "$PASSWORD_CONFIRM" ]; then
            echo -e "${RED}Error: passwords do not match.${NC}" >&2
            exit 1
        fi
    fi

    # Same rule the API enforces (UserCreateRequest / SetAccountPasswordRequest), checked here
    # so a rejected password is caught before it reaches the database.
    if ! printf '%s' "$SUPER_ADMIN_PASSWORD" | grep -Eq '^.{8,}$' \
       || ! printf '%s' "$SUPER_ADMIN_PASSWORD" | grep -q '[a-z]' \
       || ! printf '%s' "$SUPER_ADMIN_PASSWORD" | grep -q '[A-Z]' \
       || ! printf '%s' "$SUPER_ADMIN_PASSWORD" | grep -q '[0-9]'; then
        echo -e "${RED}Error: password needs at least 8 characters, one uppercase, one lowercase and one digit.${NC}" >&2
        exit 1
    fi

    # BCrypt, cost 10 - matches Spring's default BCryptPasswordEncoder. Both the $2b$ hash
    # python emits and the $2y$ hash htpasswd emits verify against it.
    if python3 -c 'import bcrypt' 2>/dev/null; then
        PASSWORD_HASH=$(SUPER_ADMIN_PASSWORD="$SUPER_ADMIN_PASSWORD" python3 -c \
            'import bcrypt, os; print(bcrypt.hashpw(os.environ["SUPER_ADMIN_PASSWORD"].encode(), bcrypt.gensalt(10)).decode())')
    elif command -v htpasswd >/dev/null 2>&1; then
        PASSWORD_HASH=$(htpasswd -bnBC 10 "" "$SUPER_ADMIN_PASSWORD" | tr -d ':\n')
    else
        echo -e "${RED}Error: no BCrypt tool found.${NC}" >&2
        echo "Install one of:" >&2
        echo "  pip3 install bcrypt        (python3 -c 'import bcrypt')" >&2
        echo "  apache2-utils / httpd-tools (htpasswd)" >&2
        echo "Or generate the hash yourself and run create-super-admin.sql directly." >&2
        exit 1
    fi

    if [ -z "$PASSWORD_HASH" ]; then
        echo -e "${RED}Error: failed to generate a password hash.${NC}" >&2
        exit 1
    fi
fi

# --- run the SQL -----------------------------------------------------------

echo ""
echo -e "${GREEN}Granting SUPER_ADMIN to ${EMAIL}${NC}"
echo "Database: $DB_NAME on $DB_HOST:$DB_PORT"
if [ "$PROMOTE_ONLY" = true ]; then
    echo -e "${YELLOW}Promote only: the existing password is left unchanged.${NC}"
fi
echo ""

PSQL_ARGS=(
    -v ON_ERROR_STOP=1
    -v "email=$EMAIL"
    -v "first_name=$FIRST_NAME"
    -v "last_name=$LAST_NAME"
    -v "password_hash=$PASSWORD_HASH"
)

if command -v psql >/dev/null 2>&1; then
    if [ -z "${DB_PASSWORD:-}" ]; then
        read -r -s -p "Database password for '$DB_USER': " DB_PASSWORD
        echo ""
    fi
    PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
        "${PSQL_ARGS[@]}" -f "$SQL_FILE"
elif command -v docker >/dev/null 2>&1; then
    # No local psql: use the postgres container from docker-compose.yml.
    echo -e "${YELLOW}psql not found - using the '$DOCKER_SERVICE' docker compose service.${NC}"
    docker compose -f "$SCRIPT_DIR/../docker-compose.yml" exec -T "$DOCKER_SERVICE" \
        psql -U "$DB_USER" -d "$DB_NAME" "${PSQL_ARGS[@]}" -f - < "$SQL_FILE"
else
    echo -e "${RED}Error: neither psql nor docker is available.${NC}" >&2
    exit 1
fi

echo ""
echo -e "${GREEN}Done.${NC}"
if [ "$PROMOTE_ONLY" = false ]; then
    echo ""
    echo -e "${YELLOW}Sign in with:${NC}"
    echo "  Email:    $EMAIL"
    echo "  Password: (the one you just entered)"
    echo ""
    echo -e "${YELLOW}If the account was already signed in elsewhere, it must sign out and back in${NC}"
    echo -e "${YELLOW}for the new super_admin scope to appear in its token.${NC}"
fi
