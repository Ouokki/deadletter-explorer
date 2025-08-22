#!/usr/bin/env bash
set -euo pipefail

# ---- Config (edit if needed) ----------------------------------------------
KC_IMAGE="quay.io/keycloak/keycloak:24.0"
KC_CONTAINER="infra-keycloak-1"
KC_HOST_PORT=8081                   # Keycloak available at http://localhost:8081
ADMIN_USER="admin"
ADMIN_PASS="admin"

REALM="dle"
CLIENT_API="dle-api"
CLIENT_FE="dle-frontend"
UI_REDIRECT="http://localhost:5173/*"
UI_ORIGIN="http://localhost:5173"

USER1="alice"       # triager
USER1_PASS="pass"
USER2="bob"         # viewer
USER2_PASS="pass"
# ---------------------------------------------------------------------------

echo ">>> Starting Keycloak container (${KC_IMAGE}) on port ${KC_HOST_PORT}…"
if docker ps -a --format '{{.Names}}' | grep -q "^${KC_CONTAINER}$"; then
  echo "Container ${KC_CONTAINER} already exists. Starting (or restarting)…"
  docker start "${KC_CONTAINER}" >/dev/null || true
else
  docker run -d --name "${KC_CONTAINER}" -p "${KC_HOST_PORT}:8080" \
    -e KEYCLOAK_ADMIN="${ADMIN_USER}" -e KEYCLOAK_ADMIN_PASSWORD="${ADMIN_PASS}" \
    "${KC_IMAGE}" start-dev >/dev/null
fi

# Wait for KC to be up
echo ">>> Waiting for Keycloak to be ready…"
for i in {1..60}; do
  if curl -fsS "http://localhost:${KC_HOST_PORT}/realms/master/.well-known/openid-configuration" >/dev/null; then
    break
  fi
  sleep 1
  if [[ $i -eq 60 ]]; then
    echo "Keycloak did not become ready in time." >&2
    exit 1
  fi
done
echo "Keycloak is up."

# Helper to run kcadm inside container
kcadm() {
  MSYS_NO_PATHCONV=1 docker exec -i "${KC_CONTAINER}" /opt/keycloak/bin/kcadm.sh "$@"
}

# Login admin (inside container, KC listens on 8080)
echo ">>> Logging into kcadm…"
kcadm config credentials --server http://localhost:8080 --realm master \
  --user "${ADMIN_USER}" --password "${ADMIN_PASS}"

# Create realm (idempotent)
if kcadm get "realms/${REALM}" >/dev/null 2>&1; then
  echo "Realm '${REALM}' already exists."
else
  echo ">>> Creating realm '${REALM}'…"
  kcadm create realms -s "realm=${REALM}" -s enabled=true >/dev/null
fi

# Create API client (Confidential; roles live here)
CID_API=$(kcadm get clients -r "${REALM}" -q "clientId=${CLIENT_API}" --fields id --format csv --noquotes 2>/dev/null || true)
if [[ -z "${CID_API}" ]]; then
  echo ">>> Creating client '${CLIENT_API}'…"
  kcadm create clients -r "${REALM}" \
    -s "clientId=${CLIENT_API}" \
    -s "protocol=openid-connect" \
    -s "publicClient=false" \
    -s "standardFlowEnabled=false" \
    -s "directAccessGrantsEnabled=false" \
    -s "serviceAccountsEnabled=false" >/dev/null
  CID_API=$(kcadm get clients -r "${REALM}" -q "clientId=${CLIENT_API}" --fields id --format csv --noquotes)
else
  echo "Client '${CLIENT_API}' already exists."
fi

# Create FE client (Public; Auth Code + PKCE implied)
CID_FE=$(kcadm get clients -r "${REALM}" -q "clientId=${CLIENT_FE}" --fields id --format csv --noquotes 2>/dev/null || true)
if [[ -z "${CID_FE}" ]]; then
  echo ">>> Creating client '${CLIENT_FE}'…"
  kcadm create clients -r "${REALM}" \
    -s "clientId=${CLIENT_FE}" \
    -s "protocol=openid-connect" \
    -s "publicClient=true" \
    -s "standardFlowEnabled=true" \
    -s "directAccessGrantsEnabled=false" \
    -s "redirectUris=[\"${UI_REDIRECT}\"]" \
    -s "webOrigins=[\"${UI_ORIGIN}\"]" >/dev/null
  CID_FE=$(kcadm get clients -r "${REALM}" -q "clientId=${CLIENT_FE}" --fields id --format csv --noquotes)
else
  echo "Client '${CLIENT_FE}' already exists. Updating redirect/web origins…"
  kcadm update "clients/${CID_FE}" -r "${REALM}" \
    -s "redirectUris=[\"${UI_REDIRECT}\"]" \
    -s "webOrigins=[\"${UI_ORIGIN}\"]" >/dev/null
fi

# Add Audience mapper to FE so tokens include aud: dle-api
echo ">>> Ensuring Audience mapper on '${CLIENT_FE}' (audience=${CLIENT_API})…"
# Check if mapper exists
MAPPER_EXISTS=$(kcadm get "clients/${CID_FE}/protocol-mappers/models" -r "${REALM}" | jq -r '.[] | select(.name=="audience-dle-api") | .id' || true)
if [[ -z "${MAPPER_EXISTS}" ]]; then
  kcadm create "clients/${CID_FE}/protocol-mappers/models" -r "${REALM}" \
    -s name="audience-dle-api" \
    -s protocol="openid-connect" \
    -s protocolMapper="oidc-audience-mapper" \
    -s 'config."included.client.audience"='"${CLIENT_API}"'' \
    -s 'config."access.token.claim"=true' \
    -s 'config."id.token.claim"=false' >/dev/null
else
  echo "Audience mapper already present."
fi

# Create API client roles: viewer, triager, replayer
echo ">>> Creating roles on '${CLIENT_API}'…"
for r in viewer triager replayer; do
  if ! kcadm get "clients/${CID_API}/roles/${r}" -r "${REALM}" >/dev/null 2>&1; then
    kcadm create "clients/${CID_API}/roles" -r "${REALM}" -s "name=${r}" >/dev/null
  fi
done

# Create/Update users and assign roles directly (simpler than groups via CLI)
create_or_update_user() {
  local uname="$1" upass="$2" roles_csv="$3"
  local USER_ID
  USER_ID=$(kcadm get users -r "${REALM}" -q "username=${uname}" --fields id --format csv --noquotes 2>/dev/null || true)
  if [[ -z "${USER_ID}" ]]; then
    echo ">>> Creating user ${uname}…"
    kcadm create users -r "${REALM}" -s "username=${uname}" -s enabled=true >/dev/null
    USER_ID=$(kcadm get users -r "${REALM}" -q "username=${uname}" --fields id --format csv --noquotes)
  else
    echo "User ${uname} exists."
  fi
  # Set password
  kcadm set-password -r "${REALM}" --userid "${USER_ID}" --new-password "${upass}" >/dev/null

  # Clear any existing client role mappings for this client
  # (skip if you want additive behavior)
  :
  # Assign roles
  IFS=',' read -r -a roles <<< "${roles_csv}"
  for role in "${roles[@]}"; do
    echo "Assigning role ${role} to user ${uname}…"
    kcadm add-roles -r "${REALM}" --uusername "${uname}" --cclientid "${CLIENT_API}" --rolename "${role}" >/dev/null
  done
}

# alice = triager (+ viewer)
create_or_update_user "${USER1}" "${USER1_PASS}" "viewer,triager"
# bob = viewer
create_or_update_user "${USER2}" "${USER2_PASS}" "viewer"

echo
echo "✅ Done."
echo "Realm:         ${REALM}"
echo "Frontend:      ${CLIENT_FE} (public, Auth Code)"
echo "API:           ${CLIENT_API} (confidential)"
echo "Users:         ${USER1}/${USER1_PASS} (triager), ${USER2}/${USER2_PASS} (viewer)"
echo
echo "Test openid-config:"
echo "  curl -s http://localhost:${KC_HOST_PORT}/realms/${REALM}/.well-known/openid-configuration | jq .issuer"
echo
echo "Next:"
echo "  • Log in to http://localhost:${KC_HOST_PORT}/ as admin/admin"
echo "  • Decode an access token in jwt.io and check:"
echo "      aud includes '${CLIENT_API}'"
echo "      resource_access.${CLIENT_API}.roles contains expected roles"
