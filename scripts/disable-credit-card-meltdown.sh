#!/bin/bash
# Script to disable the credit_card_meltdown feature flag
# This fixes the division-by-zero error in credit card order status checks

set -e

# Configuration
FEATURE_FLAG_SERVICE_URL="${FEATURE_FLAG_SERVICE_URL:-http://localhost/feature-flag-service}"
FLAG_ID="credit_card_meltdown"

echo "=================================================="
echo "Disabling credit_card_meltdown feature flag"
echo "=================================================="
echo ""

# Check if the feature flag service is reachable
echo "Checking feature flag service availability..."
if ! curl -s --fail --connect-timeout 5 "${FEATURE_FLAG_SERVICE_URL}/v1/flags" > /dev/null 2>&1; then
    echo "❌ Error: Cannot reach feature flag service at ${FEATURE_FLAG_SERVICE_URL}"
    echo "   Please ensure the service is running or set FEATURE_FLAG_SERVICE_URL environment variable"
    echo ""
    echo "   Examples:"
    echo "   - Local: export FEATURE_FLAG_SERVICE_URL=http://localhost/feature-flag-service"
    echo "   - K8s: export FEATURE_FLAG_SERVICE_URL=http://<INGRESS_IP>/feature-flag-service"
    exit 1
fi
echo "✓ Feature flag service is reachable"
echo ""

# Get current flag status
echo "Current flag status:"
CURRENT_STATUS=$(curl -s "${FEATURE_FLAG_SERVICE_URL}/v1/flags/${FLAG_ID}")
echo "${CURRENT_STATUS}" | jq '.'
echo ""

CURRENT_ENABLED=$(echo "${CURRENT_STATUS}" | jq -r '.enabled')

if [ "${CURRENT_ENABLED}" == "false" ]; then
    echo "✓ Flag is already disabled. No action needed."
    exit 0
fi

# Disable the flag
echo "Disabling the flag..."
RESPONSE=$(curl -s -X PUT "${FEATURE_FLAG_SERVICE_URL}/v1/flags/${FLAG_ID}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d '{"enabled": false}')

echo "${RESPONSE}" | jq '.'
echo ""

# Verify the change
echo "Verifying the change..."
UPDATED_STATUS=$(curl -s "${FEATURE_FLAG_SERVICE_URL}/v1/flags/${FLAG_ID}")
UPDATED_ENABLED=$(echo "${UPDATED_STATUS}" | jq -r '.enabled')

if [ "${UPDATED_ENABLED}" == "false" ]; then
    echo "✓ Successfully disabled credit_card_meltdown feature flag"
    echo ""
    echo "=================================================="
    echo "Fix Applied Successfully!"
    echo "=================================================="
    echo "Credit card order status checks should now work without errors."
    echo ""
else
    echo "❌ Error: Failed to disable the feature flag"
    echo "Current status:"
    echo "${UPDATED_STATUS}" | jq '.'
    exit 1
fi
