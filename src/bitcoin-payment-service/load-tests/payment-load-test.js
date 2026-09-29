/**
 * Load Test for Bitcoin Payment Service
 * 
 * Tests the service's ability to handle 100x baseline traffic (650 req/min)
 * 
 * Run with k6:
 *   k6 run payment-load-test.js
 * 
 * Run with custom target:
 *   k6 run -e TARGET_URL=http://localhost:8080 payment-load-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const paymentCreationTime = new Trend('payment_creation_duration');
const paymentRetrievalTime = new Trend('payment_retrieval_duration');
const walletRetrievalTime = new Trend('wallet_retrieval_duration');
const paymentCreations = new Counter('payment_creations');
const paymentRetrievals = new Counter('payment_retrievals');

// Test configuration
export const options = {
  stages: [
    // Ramp up to baseline (6.5 req/min = ~0.1 req/sec)
    { duration: '1m', target: 2 },
    
    // Hold at baseline
    { duration: '2m', target: 2 },
    
    // Ramp up to 10x (65 req/min = ~1 req/sec)
    { duration: '2m', target: 20 },
    
    // Hold at 10x
    { duration: '3m', target: 20 },
    
    // Ramp up to 50x (325 req/min = ~5 req/sec)
    { duration: '2m', target: 100 },
    
    // Hold at 50x
    { duration: '3m', target: 100 },
    
    // Ramp up to 100x (650 req/min = ~11 req/sec)
    { duration: '3m', target: 220 },
    
    // Hold at 100x - STRESS TEST
    { duration: '5m', target: 220 },
    
    // Ramp down
    { duration: '2m', target: 0 },
  ],
  thresholds: {
    // 99.5% of requests should succeed (matches SLO)
    'errors': ['rate<0.005'],
    
    // P95 latency should be < 200ms (matches SLO)
    'http_req_duration': ['p(95)<200'],
    
    // Payment creation P95 < 200ms
    'payment_creation_duration': ['p(95)<200'],
    
    // Payment retrieval P95 < 100ms (should be faster, cached)
    'payment_retrieval_duration': ['p(95)<100'],
    
    // Wallet retrieval P95 < 100ms (should be faster, cached)
    'wallet_retrieval_duration': ['p(95)<100'],
    
    // No request should take longer than 2 seconds
    'http_req_duration': ['p(99)<2000'],
    
    // At least 95% of requests should succeed
    'http_req_failed': ['rate<0.05'],
  },
};

const BASE_URL = __ENV.TARGET_URL || 'http://localhost:8080';

// Generate unique account IDs for load test
function generateAccountId() {
  const vu = __VU;
  const iter = __ITER;
  return `LOAD-TEST-${vu}-${iter}`;
}

// Generate random BTC amount between 0.0001 and 0.01
function generateAmount() {
  return Math.random() * (0.01 - 0.0001) + 0.0001;
}

export default function() {
  const accountId = generateAccountId();
  const amount = generateAmount();
  
  // Test 1: Create a new payment
  const createPaymentPayload = JSON.stringify({
    accountId: accountId,
    amount: amount,
    currency: 'BTC'
  });
  
  const createHeaders = {
    'Content-Type': 'application/json',
  };
  
  const createStart = Date.now();
  const createResponse = http.post(
    `${BASE_URL}/v1/payments`,
    createPaymentPayload,
    { headers: createHeaders }
  );
  const createDuration = Date.now() - createStart;
  
  const createSuccess = check(createResponse, {
    'payment created successfully': (r) => r.status === 201,
    'payment has paymentId': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.paymentId !== undefined;
      } catch {
        return false;
      }
    },
    'payment has correct amount': (r) => {
      try {
        const body = JSON.parse(r.body);
        return Math.abs(body.amount - amount) < 0.0000001;
      } catch {
        return false;
      }
    },
    'payment has walletAddress': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.walletAddress && body.walletAddress.startsWith('bc1q');
      } catch {
        return false;
      }
    },
    'payment has qrCodeData': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.qrCodeData && body.qrCodeData.startsWith('bitcoin:');
      } catch {
        return false;
      }
    },
  });
  
  paymentCreationTime.add(createDuration);
  paymentCreations.add(1);
  errorRate.add(!createSuccess);
  
  if (!createSuccess) {
    console.error(`Payment creation failed: ${createResponse.status} - ${createResponse.body}`);
    sleep(1);
    return;
  }
  
  // Extract paymentId from response
  let paymentId;
  try {
    const createBody = JSON.parse(createResponse.body);
    paymentId = createBody.paymentId;
  } catch (e) {
    console.error(`Failed to parse create response: ${e}`);
    errorRate.add(1);
    sleep(1);
    return;
  }
  
  // Small delay to simulate user reading the QR code
  sleep(0.5);
  
  // Test 2: Retrieve the payment
  const retrieveStart = Date.now();
  const retrieveResponse = http.get(`${BASE_URL}/v1/payments/${paymentId}`);
  const retrieveDuration = Date.now() - retrieveStart;
  
  const retrieveSuccess = check(retrieveResponse, {
    'payment retrieved successfully': (r) => r.status === 200,
    'retrieved payment matches created': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.paymentId === paymentId;
      } catch {
        return false;
      }
    },
    'payment has status': (r) => {
      try {
        const body = JSON.parse(r.body);
        return ['PENDING', 'CONFIRMED', 'FAILED'].includes(body.status);
      } catch {
        return false;
      }
    },
  });
  
  paymentRetrievalTime.add(retrieveDuration);
  paymentRetrievals.add(1);
  errorRate.add(!retrieveSuccess);
  
  // Test 3: Retrieve wallet information
  const walletStart = Date.now();
  const walletResponse = http.get(`${BASE_URL}/v1/wallets/${accountId}`);
  const walletDuration = Date.now() - walletStart;
  
  const walletSuccess = check(walletResponse, {
    'wallet retrieved successfully': (r) => r.status === 200 || r.status === 404,
    'wallet has correct accountId': (r) => {
      if (r.status === 404) return true;
      try {
        const body = JSON.parse(r.body);
        return body.accountId === accountId;
      } catch {
        return false;
      }
    },
  });
  
  walletRetrievalTime.add(walletDuration);
  errorRate.add(!walletSuccess);
  
  // Random delay between iterations (0.5-2 seconds)
  sleep(Math.random() * 1.5 + 0.5);
}

export function handleSummary(data) {
  return {
    'load-test-summary.json': JSON.stringify(data, null, 2),
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
  };
}

function textSummary(data, options) {
  const indent = options.indent || '';
  const enableColors = options.enableColors || false;
  
  let summary = '\n';
  summary += `${indent}========================================\n`;
  summary += `${indent}Load Test Summary - Bitcoin Payment Service\n`;
  summary += `${indent}========================================\n\n`;
  
  summary += `${indent}Test Duration: ${data.state.testRunDurationMs / 1000}s\n`;
  summary += `${indent}VUs: ${data.metrics.vus.values.max} max\n`;
  summary += `${indent}Iterations: ${data.metrics.iterations.values.count}\n\n`;
  
  summary += `${indent}HTTP Requests:\n`;
  summary += `${indent}  Total: ${data.metrics.http_reqs.values.count}\n`;
  summary += `${indent}  Failed: ${(data.metrics.http_req_failed.values.rate * 100).toFixed(2)}%\n`;
  summary += `${indent}  Rate: ${data.metrics.http_reqs.values.rate.toFixed(2)} req/s\n\n`;
  
  summary += `${indent}Response Times:\n`;
  summary += `${indent}  Avg: ${data.metrics.http_req_duration.values.avg.toFixed(2)}ms\n`;
  summary += `${indent}  P95: ${data.metrics.http_req_duration.values['p(95)'].toFixed(2)}ms\n`;
  summary += `${indent}  P99: ${data.metrics.http_req_duration.values['p(99)'].toFixed(2)}ms\n`;
  summary += `${indent}  Max: ${data.metrics.http_req_duration.values.max.toFixed(2)}ms\n\n`;
  
  summary += `${indent}Custom Metrics:\n`;
  summary += `${indent}  Payment Creations: ${data.metrics.payment_creations.values.count}\n`;
  summary += `${indent}  Payment Creation P95: ${data.metrics.payment_creation_duration.values['p(95)'].toFixed(2)}ms\n`;
  summary += `${indent}  Payment Retrievals: ${data.metrics.payment_retrievals.values.count}\n`;
  summary += `${indent}  Payment Retrieval P95: ${data.metrics.payment_retrieval_duration.values['p(95)'].toFixed(2)}ms\n`;
  summary += `${indent}  Error Rate: ${(data.metrics.errors.values.rate * 100).toFixed(3)}%\n\n`;
  
  // SLO Compliance Check
  summary += `${indent}SLO Compliance:\n`;
  const errorRateOk = data.metrics.errors.values.rate < 0.005;
  const p95LatencyOk = data.metrics.http_req_duration.values['p(95)'] < 200;
  
  summary += `${indent}  Availability (99.5%): ${errorRateOk ? '✓ PASS' : '✗ FAIL'}\n`;
  summary += `${indent}  Latency P95 (<200ms): ${p95LatencyOk ? '✓ PASS' : '✗ FAIL'}\n\n`;
  
  if (errorRateOk && p95LatencyOk) {
    summary += `${indent}✓ All SLOs met! Service is ready for 100x scale.\n`;
  } else {
    summary += `${indent}✗ Some SLOs not met. Review configuration and scaling.\n`;
  }
  
  summary += `${indent}========================================\n`;
  
  return summary;
}
