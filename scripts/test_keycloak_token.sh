#!/usr/bin/env bash
set -euo pipefail

# --- Config (match your setup) ---
KC_CONTAINER="infra-keycloak-1"
KC_HOST_PORT=8081
ADMIN_USER="admin"
ADMIN_PASS="admin"

REALM="dle"
CLIENT_FE="dle-frontend"
CLIENT_API="dle-api"

USER="alice"
PASS="pass"
# ---------------------------------

kcadm() {
  # Git Bash safe (prevents path mangling)
  MSYS_NO_PATHCONV=1 docker exec -i "${KC_CONTAINER}" /opt/keycloak/bin/kcadm.sh "$@"
}

echo ">>> kcadm login…"
kcadm config credentials --server http://localhost:8080 --realm master \
  --user "${ADMIN_USER}" --password "${ADMIN_PASS}"

# Resolve client IDs
CID_FE=$(kcadm get clients -r "${REALM}" -q "clientId=${CLIENT_FE}" --fields id --format csv --noquotes)
if [[ -z "${CID_FE}" ]]; then echo "dle-frontend not found"; exit 1; fi

echo ">>> Enable Direct Access Grants on ${CLIENT_FE} (temp)…"
kcadm update "clients/${CID_FE}" -r "${REALM}" -s directAccessGrantsEnabled=true >/dev/null

TOKEN_ENDPOINT="http://localhost:${KC_HOST_PORT}/realms/${REALM}/protocol/openid-connect/token"

echo ">>> Request token for user '${USER}' (password grant)…"
RAW_JSON=$(curl -s -X POST "${TOKEN_ENDPOINT}" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=${CLIENT_FE}" \
  -d "username=${USER}" \
  -d "password=${PASS}" \
  -d "scope=openid profile email")

echo "${RAW_JSON}" > token_${USER}.json
ACCESS_TOKEN=$(echo "${RAW_JSON}" | jq -r '.access_token // empty')

if [[ -z "${ACCESS_TOKEN}" || "${ACCESS_TOKEN}" == "null" ]]; then
  echo "❌ Failed to get access token. See token_${USER}.json"
  echo ">>> Disabling Direct Access Grants again…"
  kcadm update "clients/${CID_FE}" -r "${REALM}" -s directAccessGrantsEnabled=false >/dev/null || true
  exit 1
fi

printf "%s" "${ACCESS_TOKEN}" > token_${USER}.txt

# Decode JWT payload (portable)
decode_jwt_payload() {
  local jwt="$1"
  local payload_b64
  payload_b64=$(printf "%s" "$jwt" | awk -F. '{print $2}')
  local pad=$(( (4 - ${#payload_b64} % 4) % 4 ))
  [[ $pad -gt 0 ]] && payload_b64="${payload_b64}$(printf '=%.0s' $(seq 1 $pad))"
  printf "%s" "${payload_b64}" | base64 -d 2>/dev/null
}
CLAIMS_JSON=$(decode_jwt_payload "${ACCESS_TOKEN}")
echo "${CLAIMS_JSON}" | jq . > token_${USER}_claims.json

ISSUER=$(echo "${CLAIMS_JSON}" | jq -r '.iss')
AUD=$(echo "${CLAIMS_JSON}" | jq -r '.aud | if type=="array" then join(",") else . end')
ROLES=$(echo "${CLAIMS_JSON}" | jq -r '.resource_access["'"${CLIENT_API}"'"].roles // [] | join(",")')

echo "issuer: ${ISSUER}"
echo "aud:    ${AUD}"
echo "roles:  ${ROLES}"

FAIL=0
[[ "${ISSUER}" != "http://localhost:${KC_HOST_PORT}/realms/${REALM}" ]] && echo "❌ issuer mismatch" && FAIL=1
echo "${AUD}"   | grep -q "${CLIENT_API}" || { echo "❌ 'aud' missing ${CLIENT_API}"; FAIL=1; }
echo "${ROLES}" | grep -q "triager"       || { echo "❌ roles missing 'triager'"; FAIL=1; }

[[ $FAIL -eq 0 ]] && echo "✅ Token OK (aud includes ${CLIENT_API}, roles include triager)."

echo ">>> Disable Direct Access Grants on ${CLIENT_FE} (restore)…"
kcadm update "clients/${CID_FE}" -r "${REALM}" -s directAccessGrantsEnabled=false >/dev/null
