#!/usr/bin/env bash
set -euo pipefail

# ---- Config (edit if needed) ----------------------------------------------
KC_IMAGE="quay.io/keycloak/keycloak:24.0"
KC_CONTAINER="infra-keycloak-1"
KC_HOST_PORT=8081
ADMIN_USER="admin"
ADMIN_PASS="admin"

REALM="dle"
CLIENT_API="dle-api"
CLIENT_FE="dle-frontend"
UI_ORIGIN="http://localhost:5173"   # React dev origin
UI_REDIRECT="${UI_ORIGIN}/*"
SILENT_CHECK="${UI_ORIGIN}/silent-check-sso.html"

USER1="alice"       # triager
USER1_PASS="pass"
USER2="bob"         # viewer
USER2_PASS="pass"

USER_EMAIL_DOMAIN="example.test"
# ---------------------------------------------------------------------------

echo ">>> Starting Keycloak container (${KC_IMAGE}) on port ${KC_HOST_PORT}…"
if docker ps -a --format '{{.Names}}' | grep -q "^${KC_CONTAINER}$"; then
  docker start "${KC_CONTAINER}" >/dev/null || true
else
  docker run -d --name "${KC_CONTAINER}" -p "${KC_HOST_PORT}:8080" \
    -e KEYCLOAK_ADMIN="${ADMIN_USER}" -e KEYCLOAK_ADMIN_PASSWORD="${ADMIN_PASS}" \
    "${KC_IMAGE}" start-dev >/dev/null
fi

echo ">>> Waiting for Keycloak to be ready…"
for i in {1..60}; do
  if curl -fsS "http://localhost:${KC_HOST_PORT}/realms/master/.well-known/openid-configuration" >/dev/null; then break; fi
  sleep 1
  [[ $i -eq 60 ]] && { echo "Keycloak did not become ready in time." >&2; exit 1; }
done
echo "Keycloak is up."

kcadm() { MSYS_NO_PATHCONV=1 docker exec -i "${KC_CONTAINER}" /opt/keycloak/bin/kcadm.sh "$@"; }

echo ">>> Logging into kcadm…"
kcadm config credentials --server http://localhost:8080 --realm master --user "${ADMIN_USER}" --password "${ADMIN_PASS}"

# Realm
if kcadm get "realms/${REALM}" >/dev/null 2>&1; then
  echo "Realm '${REALM}' already exists."
else
  echo ">>> Creating realm '${REALM}'…"
  kcadm create realms -s "realm=${REALM}" -s enabled=true >/dev/null
fi

# API client (confidential; roles live here)
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

# Frontend client (public; Auth Code + PKCE; no forced login — the SPA decides)
CID_FE=$(kcadm get clients -r "${REALM}" -q "clientId=${CLIENT_FE}" --fields id --format csv --noquotes 2>/dev/null || true)
if [[ -z "${CID_FE}" ]]; then
  echo ">>> Creating client '${CLIENT_FE}'…"
  kcadm create clients -r "${REALM}" \
    -s "clientId=${CLIENT_FE}" \
    -s "protocol=openid-connect" \
    -s "publicClient=true" \
    -s "standardFlowEnabled=true" \
    -s "directAccessGrantsEnabled=false" \
    -s "rootUrl=${UI_ORIGIN}" \
    -s "baseUrl=${UI_ORIGIN}/" \
    -s "redirectUris=[\"${UI_REDIRECT}\",\"${SILENT_CHECK}\"]" \
    -s "webOrigins=[\"${UI_ORIGIN}\"]" \
    -s "frontchannelLogout=true" \
    -s 'attributes."post.logout.redirect.uris"="'"${UI_ORIGIN}"'/*"' \
    -s 'attributes."pkce.code.challenge.method"="S256"' >/dev/null
  CID_FE=$(kcadm get clients -r "${REALM}" -q "clientId=${CLIENT_FE}" --fields id --format csv --noquotes)
else
  echo "Client '${CLIENT_FE}' already exists. Updating SPA settings…"
  kcadm update "clients/${CID_FE}" -r "${REALM}" \
    -s "protocol=openid-connect" \
    -s "publicClient=true" \
    -s "standardFlowEnabled=true" \
    -s "directAccessGrantsEnabled=false" \
    -s "rootUrl=${UI_ORIGIN}" \
    -s "baseUrl=${UI_ORIGIN}/" \
    -s "redirectUris=[\"${UI_REDIRECT}\",\"${SILENT_CHECK}\"]" \
    -s "webOrigins=[\"${UI_ORIGIN}\"]" \
    -s "frontchannelLogout=true" \
    -s 'attributes."post.logout.redirect.uris"="'"${UI_ORIGIN}"'/*"' \
    -s 'attributes."pkce.code.challenge.method"="S256"' >/dev/null
fi

# Audience mapper so SPA tokens have aud: dle-api
echo ">>> Ensuring audience mapper on '${CLIENT_FE}' (audience=${CLIENT_API})…"
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

# Ensure default client scopes (profile, email, roles)
echo ">>> Ensuring default client scopes on '${CLIENT_FE}'…"
ensure_scope () {
  local scope="$1"
  local SID
  SID=$(kcadm get client-scopes -r "${REALM}" -q "name=${scope}" | jq -r '.[0].id // empty')
  if [[ -n "${SID}" ]]; then
    if ! kcadm get "clients/${CID_FE}/default-client-scopes" -r "${REALM}" | jq -e '.[].name | select(.=="'"${scope}"'")' >/dev/null; then
      kcadm update "clients/${CID_FE}/default-client-scopes/${SID}" -r "${REALM}" >/dev/null
    fi
  fi
}
ensure_scope "profile"
ensure_scope "email"
ensure_scope "roles"

# Roles on API client
echo ">>> Creating roles on '${CLIENT_API}'…"
for r in viewer triager replayer; do
  if ! kcadm get "clients/${CID_API}/roles/${r}" -r "${REALM}" >/dev/null 2>&1; then
    kcadm create "clients/${CID_API}/roles" -r "${REALM}" -s "name=${r}" >/dev/null
  fi
done

# Users fully set up (no required actions)
create_or_update_user() {
  local uname="$1" upass="$2" roles_csv="$3"
  local USER_ID
  USER_ID=$(kcadm get users -r "${REALM}" -q "username=${uname}" | jq -r '.[0].id // empty')
  if [[ -z "${USER_ID}" ]]; then
    echo ">>> Creating user ${uname}…"
    kcadm create users -r "${REALM}" -s "username=${uname}" -s enabled=true >/dev/null
    USER_ID=$(kcadm get users -r "${REALM}" -q "username=${uname}" | jq -r '.[0].id')
  else
    echo "User ${uname} exists."
    kcadm update "users/${USER_ID}" -r "${REALM}" -s 'enabled=true' >/dev/null
  fi

  kcadm set-password -r "${REALM}" --userid "${USER_ID}" --new-password "${upass}" --temporary=false >/dev/null

  kcadm update "users/${USER_ID}" -r "${REALM}" \
    -s "email=${uname}@${USER_EMAIL_DOMAIN}" \
    -s "emailVerified=true" \
    -s 'firstName='"${uname^}" \
    -s 'lastName=User' \
    -s 'requiredActions=[]' \
    -s 'totp=false' >/dev/null

  IFS=',' read -r -a roles <<< "${roles_csv}"
  for role in "${roles[@]}"; do
    echo "Assigning role ${role} to user ${uname}…"
    kcadm add-roles -r "${REALM}" --uusername "${uname}" --cclientid "${CLIENT_API}" --rolename "${role}" >/dev/null
  done

  echo ">>> ${uname} summary:"
  kcadm get "users/${USER_ID}" -r "${REALM}" --fields username,enabled,email,emailVerified,requiredActions | jq .
}

create_or_update_user "${USER1}" "${USER1_PASS}" "viewer,triager"
create_or_update_user "${USER2}" "${USER2_PASS}" "viewer"

echo
echo "✅ Done."
echo "Realm:         ${REALM}"
echo "Frontend:      ${CLIENT_FE} (public, Auth Code + PKCE; SPA controls login)"
echo "API:           ${CLIENT_API} (confidential)"
echo "Users:         ${USER1}/${USER1_PASS} (triager), ${USER2}/${USER2_PASS} (viewer)"
echo
echo "Test openid-config:"
echo "  curl -s http://localhost:${KC_HOST_PORT}/realms/${REALM}/.well-known/openid-configuration | jq .issuer"
