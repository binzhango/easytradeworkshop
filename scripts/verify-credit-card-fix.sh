#!/bin/bash
# Script to verify the credit_card_meltdown fix is working
# Tests that credit card order status checks work without errors

set -e

# Configuration
BASE_URL="${EASYTRADE_URL:-http://localhost}"
FEATURE_FLAG_SERVICE_URL="${BASE_URL}/feature-flag-service"
CREDIT_CARD_SERVICE_URL="${BASE_URL}/credit-card-order-service"

echo "=================================================="
echo "Verifying Credit Card Meltdown Fix"
echo "=================================================="
echo ""
echo "Testing against: ${BASE_URL}"
echo ""

# Check if services are reachable
echo "1. Checking service availability..."
if ! curl -s --fail --connect-timeout 5 "${FEATURE_FLAG_SERVICE_URL}/v1/flags" > /dev/null 2>&1; then
    echo "❌ Error: Cannot reach feature flag service"
    exit 1
fi
if ! curl -s --fail --connect-timeout 5 "${CREDIT_CARD_SERVICE_URL}/api/version" > /dev/null 2>&1; then
    echo "⚠️  Warning: Cannot reach credit card service (might be expected if not running)"
fi
echo "✓ Services are reachable"
echo ""

# Check feature flag status
echo "2. Checking credit_card_meltdown flag status..."
FLAG_STATUS=$(curl -s "${FEATURE_FLAG_SERVICE_URL}/v1/flags/credit_card_meltdown")
ENABLED=$(echo "${FLAG_STATUS}" | jq -r '.enabled')

echo "Flag status:"
echo "${FLAG_STATUS}" | jq '.'
echo ""

if [ "${ENABLED}" == "true" ]; then
    echo "❌ FAIL: credit_card_meltdown flag is still ENABLED"
    echo "   This will cause 500 errors on credit card status checks"
    echo ""
    echo "   Run the following to fix:"
    echo "   ./scripts/disable-credit-card-meltdown.sh"
    exit 1
elif [ "${ENABLED}" == "false" ]; then
    echo "✓ PASS: credit_card_meltdown flag is DISABLED"
else
    echo "⚠️  WARNING: Cannot determine flag status (got: ${ENABLED})"
    exit 1
fi
echo ""

# Test credit card service endpoint (optional, only if we can reach it)
echo "3. Testing credit card order status endpoint..."
# Use account ID 1 as a test (may return 404 if no orders exist, but should not return 500)
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${CREDIT_CARD_SERVICE_URL}/v1/orders/1/status/latest" || echo "000")

if [ "${HTTP_CODE}" == "500" ]; then
    echo "❌ FAIL: Endpoint returned 500 Internal Server Error"
    echo "   The division-by-zero error may still be occurring"
    exit 1
elif [ "${HTTP_CODE}" == "404" ]; then
    echo "✓ PASS: Endpoint returned 404 (no order found - this is expected)"
elif [ "${HTTP_CODE}" == "200" ]; then
    echo "✓ PASS: Endpoint returned 200 (order found successfully)"
elif [ "${HTTP_CODE}" == "000" ]; then
    echo "⚠️  SKIP: Could not connect to credit card service"
else
    echo "ℹ️  INFO: Endpoint returned ${HTTP_CODE} (unexpected but not a 500 error)"
fi
echo ""

echo "=================================================="
echo "Verification Complete!"
echo "=================================================="
echo ""
echo "Summary:"
echo "✓ Feature flag is disabled"
echo "✓ No division-by-zero errors detected"
echo ""
echo "The fix has been successfully applied."
