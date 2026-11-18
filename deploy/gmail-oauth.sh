#!/usr/bin/env bash
# One-time: obtain a Gmail refresh token for the outreach mailbox.
#
# 1. Google Cloud console -> APIs & Services -> Enable "Gmail API".
# 2. OAuth consent screen: External, add the mailbox as a test user (or publish).
# 3. Credentials -> Create OAuth client ID -> type "Desktop app"; note client id + secret.
# 4. Run:  bash deploy/gmail-oauth.sh <client_id> <client_secret>
#    Open the printed URL in a browser signed in as the outreach mailbox and approve.
#    The browser is then sent to http://localhost:8765/?code=... which will not load;
#    copy the value of the code= parameter from the address bar and paste it here.
#    The refresh token it prints goes into deploy/.env as OUTREACH_GMAIL_REFRESH_TOKEN
#    (never commit it). Add http://localhost:8765 as an authorised redirect URI if the
#    client type is "Web application"; a "Desktop app" client accepts any localhost port.
set -euo pipefail
CLIENT_ID=${1:?client id}; CLIENT_SECRET=${2:?client secret}
SCOPE="https://www.googleapis.com/auth/gmail.send https://www.googleapis.com/auth/gmail.readonly"
REDIRECT="http://localhost:8765"
ENC_SCOPE=$(python3 -c 'import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))' "$SCOPE")
echo "Open this URL, approve, and paste the code:"
echo "https://accounts.google.com/o/oauth2/v2/auth?client_id=${CLIENT_ID}&redirect_uri=${REDIRECT}&response_type=code&scope=${ENC_SCOPE}&access_type=offline&prompt=consent"
read -r -p "Code (from the address bar, URL-decoded if it contains %2F): " CODE
CODE=$(python3 -c 'import urllib.parse,sys;print(urllib.parse.unquote(sys.argv[1]))' "$CODE")
curl -s https://oauth2.googleapis.com/token \
  -d client_id="$CLIENT_ID" -d client_secret="$CLIENT_SECRET" \
  -d code="$CODE" -d grant_type=authorization_code -d redirect_uri="$REDIRECT" \
  | python3 -c 'import sys,json; d=json.load(sys.stdin); print("\nOUTREACH_GMAIL_REFRESH_TOKEN=" + d.get("refresh_token","<none: did you include prompt=consent?>"))'
