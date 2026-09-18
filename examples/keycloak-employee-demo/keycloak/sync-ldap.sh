#!/usr/bin/env bash
set -euo pipefail

KCADM=/opt/keycloak/bin/kcadm.sh
SERVER="${KEYCLOAK_URL:-http://keycloak:8080}"
REALM="${KEYCLOAK_REALM:-employee-demo}"
ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"

LDAP_PROVIDER_ID="a1111111-1111-4111-8111-111111111111"
LDAP_GROUP_MAPPER_ID="a2222222-2222-4222-8222-222222222222"
SYNC_CLIENT_ID="authorization-sync-service"
SYNC_SERVICE_ACCOUNT="service-account-$SYNC_CLIENT_ID"
SYNC_CLIENT_SECRET="${AUTHORIZATION_SYNC_CLIENT_SECRET:-authorization-sync-service-demo-secret}"
SYNC_KCADM_CONFIG=/tmp/authorization-sync-kcadm.config

echo "Waiting for Keycloak..."
until "$KCADM" config credentials \
    --server "$SERVER" \
    --realm master \
    --user "$ADMIN_USER" \
    --password "$ADMIN_PASSWORD" >/dev/null 2>&1; do
  sleep 3
done

echo "Waiting for realm '$REALM' to be imported..."
until "$KCADM" get "realms/$REALM" >/dev/null 2>&1; do
  sleep 2
done

echo "Configuring authorization synchronization service account..."
SYNC_CLIENT_UUID=$("$KCADM" get clients \
  -r "$REALM" \
  -q "clientId=$SYNC_CLIENT_ID" \
  --fields id \
  --format csv \
  --noquotes)
if [[ -z "$SYNC_CLIENT_UUID" || "$SYNC_CLIENT_UUID" == *$'\n'* ]]; then
  echo "Expected exactly one '$SYNC_CLIENT_ID' client, got: '$SYNC_CLIENT_UUID'" >&2
  exit 1
fi

# The demo service account owns only view-users. Full scope makes that assigned
# role effective in its client-credentials token; it does not grant extra roles.
"$KCADM" update "clients/$SYNC_CLIENT_UUID" \
  -r "$REALM" \
  -s fullScopeAllowed=true
"$KCADM" add-roles \
  -r "$REALM" \
  --uusername "$SYNC_SERVICE_ACCOUNT" \
  --cclientid realm-management \
  --rolename view-users

echo "Verifying authorization synchronization Admin API access..."
"$KCADM" config credentials \
  --config "$SYNC_KCADM_CONFIG" \
  --server "$SERVER" \
  --realm "$REALM" \
  --client "$SYNC_CLIENT_ID" \
  --secret "$SYNC_CLIENT_SECRET" >/dev/null
SYNC_TEST_USER_ID=$("$KCADM" get users \
  --config "$SYNC_KCADM_CONFIG" \
  -r "$REALM" \
  -q username=emma \
  -q exact=true \
  --fields id \
  --format csv \
  --noquotes)
if [[ -z "$SYNC_TEST_USER_ID" || "$SYNC_TEST_USER_ID" == *$'\n'* ]]; then
  echo "Expected exactly one Emma demo user, got: '$SYNC_TEST_USER_ID'" >&2
  exit 1
fi
"$KCADM" get "users/$SYNC_TEST_USER_ID" \
  --config "$SYNC_KCADM_CONFIG" \
  -r "$REALM" >/dev/null
"$KCADM" get "users/$SYNC_TEST_USER_ID/groups" \
  --config "$SYNC_KCADM_CONFIG" \
  -r "$REALM" >/dev/null
"$KCADM" get "users/$SYNC_TEST_USER_ID/role-mappings/realm" \
  --config "$SYNC_KCADM_CONFIG" \
  -r "$REALM" >/dev/null
echo "Authorization synchronization Admin API access verified."

echo "Waiting for LDAP connection..."
until "$KCADM" create testLDAPConnection -r "$REALM" \
    -s action=testAuthentication \
    -s bindCredential=admin \
    -s 'bindDn=cn=admin,dc=demo,dc=local' \
    -s 'connectionUrl=ldap://ldap:389' \
    -s useTruststoreSpi=always >/dev/null 2>&1; do
  sleep 3
done

echo "Synchronizing LDAP group definitions into Keycloak..."
"$KCADM" create \
  "user-storage/$LDAP_PROVIDER_ID/mappers/$LDAP_GROUP_MAPPER_ID/sync?direction=fedToKeycloak" \
  -r "$REALM"

echo "Synchronizing all LDAP users into Keycloak..."
"$KCADM" create \
  "user-storage/$LDAP_PROVIDER_ID/sync?action=triggerFullSync" \
  -r "$REALM"

# Run group sync once more now that all users exist locally, so imported
# memberships are immediately visible in the admin console.
echo "Refreshing LDAP group memberships..."
"$KCADM" create \
  "user-storage/$LDAP_PROVIDER_ID/mappers/$LDAP_GROUP_MAPPER_ID/sync?direction=fedToKeycloak" \
  -r "$REALM"

echo "LDAP federation demo is ready."
