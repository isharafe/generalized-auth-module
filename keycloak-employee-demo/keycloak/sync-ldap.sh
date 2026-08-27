#!/usr/bin/env bash
set -euo pipefail

KCADM=/opt/keycloak/bin/kcadm.sh
SERVER="${KEYCLOAK_URL:-http://keycloak:8080}"
REALM="${KEYCLOAK_REALM:-employee-demo}"
ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"

LDAP_PROVIDER_ID="a1111111-1111-4111-8111-111111111111"
LDAP_GROUP_MAPPER_ID="a2222222-2222-4222-8222-222222222222"

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
