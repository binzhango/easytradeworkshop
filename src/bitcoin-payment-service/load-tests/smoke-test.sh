#!/bin/bash
#
# Smoke Test for Bitcoin Payment Service
# Quick validation that all endpoints are responding correctly
#
# Usage:
#   ./smoke-test.sh [BASE_URL]
#
# Example:
#   ./smoke-test.sh http://localhost:8080
#

set -e

BASE_URL="${1:-http://localhost:8080}"
ACCOUNT_ID="SMOKE-TEST-$(date +%s)"
FAILURES=0

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "========================================"
echo "Bitcoin Payment Service - Smoke Test"
echo "========================================"
echo "Target: $BASE_URL"
echo "Account ID: $ACCOUNT_ID"
echo ""

# Test 1: Health Check
echo -n "Test 1: Health check... "
HEALTH_RESPONSE=$(curl -s -w "%{http_code}" -o /dev/null "$BASE_URL/actuator/health" || echo "000")
if [ "$HEALTH_RESPONSE" == "200" ]; then
  echo -e "${GREEN}✓ PASS${NC}"
else
  echo -e "${RED}✗ FAIL (HTTP $HEALTH_RESPONSE)${NC}"
  FAILURES=$((FAILURES + 1))
fi

# Test 2: Create Payment
echo -n "Test 2: Create payment... "
CREATE_PAYLOAD='{"accountId":"'$ACCOUNT_ID'","amount":0.001,"currency":"BTC"}'
CREATE_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/v1/payments" \
  -H "Content-Type: application/json" \
  -d "$CREATE_PAYLOAD")

HTTP_CODE=$(echo "$CREATE_RESPONSE" | tail -n1)
RESPONSE_BODY=$(echo "$CREATE_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" == "201" ]; then
  PAYMENT_ID=$(echo "$RESPONSE_BODY" | grep -o '"paymentId":"[^"]*"' | cut -d'"' -f4)
  if [ -n "$PAYMENT_ID" ]; then
    echo -e "${GREEN}✓ PASS${NC} (Payment ID: $PAYMENT_ID)"
  else
    echo -e "${YELLOW}✓ PASS (no payment ID in response)${NC}"
    FAILURES=$((FAILURES + 1))
  fi
else
  echo -e "${RED}✗ FAIL (HTTP $HTTP_CODE)${NC}"
  echo "Response: $RESPONSE_BODY"
  FAILURES=$((FAILURES + 1))
  PAYMENT_ID=""
fi

# Test 3: Retrieve Payment (if created)
if [ -n "$PAYMENT_ID" ]; then
  echo -n "Test 3: Retrieve payment... "
  GET_RESPONSE=$(curl -s -w "%{http_code}" -o /tmp/smoke-test-get.json "$BASE_URL/v1/payments/$PAYMENT_ID" || echo "000")
  if [ "$GET_RESPONSE" == "200" ]; then
    RETRIEVED_ID=$(cat /tmp/smoke-test-get.json | grep -o '"paymentId":"[^"]*"' | cut -d'"' -f4)
    if [ "$RETRIEVED_ID" == "$PAYMENT_ID" ]; then
      echo -e "${GREEN}✓ PASS${NC}"
    else
      echo -e "${YELLOW}✓ PASS (ID mismatch)${NC}"
      FAILURES=$((FAILURES + 1))
    fi
  else
    echo -e "${RED}✗ FAIL (HTTP $GET_RESPONSE)${NC}"
    FAILURES=$((FAILURES + 1))
  fi
  rm -f /tmp/smoke-test-get.json
else
  echo "Test 3: Retrieve payment... ${YELLOW}SKIPPED (no payment created)${NC}"
fi

# Test 4: Get Wallet
echo -n "Test 4: Get wallet... "
WALLET_RESPONSE=$(curl -s -w "%{http_code}" -o /tmp/smoke-test-wallet.json "$BASE_URL/v1/wallets/$ACCOUNT_ID" || echo "000")
if [ "$WALLET_RESPONSE" == "200" ] || [ "$WALLET_RESPONSE" == "404" ]; then
  if [ "$WALLET_RESPONSE" == "200" ]; then
    WALLET_ADDRESS=$(cat /tmp/smoke-test-wallet.json | grep -o '"walletAddress":"[^"]*"' | cut -d'"' -f4)
    if [[ "$WALLET_ADDRESS" == bc1q* ]]; then
      echo -e "${GREEN}✓ PASS${NC} (Address: $WALLET_ADDRESS)"
    else
      echo -e "${YELLOW}✓ PASS (invalid address format)${NC}"
      FAILURES=$((FAILURES + 1))
    fi
  else
    echo -e "${GREEN}✓ PASS${NC} (404 - wallet not found is acceptable)"
  fi
else
  echo -e "${RED}✗ FAIL (HTTP $WALLET_RESPONSE)${NC}"
  FAILURES=$((FAILURES + 1))
fi
rm -f /tmp/smoke-test-wallet.json

# Test 5: Get Transactions
echo -n "Test 5: Get transactions... "
if [ -n "$PAYMENT_ID" ]; then
  # Extract wallet ID from payment response if available
  WALLET_ID=$(echo "$RESPONSE_BODY" | grep -o '"walletId":"[^"]*"' | cut -d'"' -f4)
  if [ -n "$WALLET_ID" ]; then
    TXN_RESPONSE=$(curl -s -w "%{http_code}" -o /tmp/smoke-test-txn.json "$BASE_URL/v1/transactions?walletId=$WALLET_ID" || echo "000")
    if [ "$TXN_RESPONSE" == "200" ]; then
      echo -e "${GREEN}✓ PASS${NC}"
    else
      echo -e "${RED}✗ FAIL (HTTP $TXN_RESPONSE)${NC}"
      FAILURES=$((FAILURES + 1))
    fi
    rm -f /tmp/smoke-test-txn.json
  else
    echo -e "${YELLOW}SKIPPED (no wallet ID)${NC}"
  fi
else
  echo -e "${YELLOW}SKIPPED (no payment created)${NC}"
fi

# Test 6: Version Endpoint
echo -n "Test 6: Version endpoint... "
VERSION_RESPONSE=$(curl -s -w "%{http_code}" -o /tmp/smoke-test-version.txt "$BASE_URL/version" || echo "000")
if [ "$VERSION_RESPONSE" == "200" ]; then
  VERSION=$(cat /tmp/smoke-test-version.txt)
  echo -e "${GREEN}✓ PASS${NC} (Version: $VERSION)"
else
  echo -e "${RED}✗ FAIL (HTTP $VERSION_RESPONSE)${NC}"
  FAILURES=$((FAILURES + 1))
fi
rm -f /tmp/smoke-test-version.txt

# Summary
echo ""
echo "========================================"
echo "Smoke Test Summary"
echo "========================================"
echo "Tests run: 6"
echo "Failures: $FAILURES"

if [ $FAILURES -eq 0 ]; then
  echo -e "${GREEN}✓ All tests passed!${NC}"
  echo ""
  echo "Service is operational and ready for load testing."
  exit 0
else
  echo -e "${RED}✗ Some tests failed!${NC}"
  echo ""
  echo "Review the failures above and check:"
  echo "  - Service logs: kubectl logs -l app=bitcoin-payment-service"
  echo "  - Database connectivity"
  echo "  - Redis connectivity"
  echo "  - Service configuration"
  exit 1
fi
