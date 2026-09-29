# Bitcoin Payment Frontend Integration

This document describes the Bitcoin payment functionality added to the EasyTrade frontend.

## Overview

The Bitcoin payment integration provides users with:
- Bitcoin wallet management
- Payment creation with QR codes
- Real-time payment status tracking
- Blockchain confirmation monitoring

## Files Added

### API Layer
- `src/api/backend/bitcoin.ts` - Bitcoin backend API client with TypeScript interfaces

### Components
- `src/pages/protected/BitcoinPayment.tsx` - Payment creation and tracking page
- `src/pages/protected/BitcoinWallet.tsx` - Wallet information display

### Updates
- `src/api/backend/index.ts` - Added BitcoinBackend to backends export

## Features

### 1. Bitcoin Payment Creation
**Location:** `/protected/bitcoin-payment`

Features:
- 3-step wizard interface (Enter Amount → Scan QR → Confirm)
- Input validation for BTC amounts
- QR code display for easy mobile scanning
- Wallet address display with copy functionality
- Payment expiration timer
- Real-time status checking

### 2. Bitcoin Wallet View
**Location:** `/protected/bitcoin-wallet`

Features:
- Display wallet address
- Show current BTC balance
- Wallet creation date
- Refresh functionality
- Link to payment creation

## User Flow

### Creating a Payment

1. **Navigate to Payment Page**
   - User clicks "Bitcoin Payment" in navigation
   - Or accesses `/protected/bitcoin-payment`

2. **Enter Amount**
   - User enters BTC amount (e.g., 0.005)
   - User selects instrument ID (stock to purchase)
   - Clicks "Create Payment"

3. **Scan QR Code**
   - System generates payment with unique wallet address
   - QR code displayed for mobile wallet scanning
   - Wallet address shown for manual copy
   - Amount and expiration time displayed
   - User sends Bitcoin from their external wallet

4. **Check Confirmation**
   - User clicks "Check Status" button
   - System polls blockchain for confirmations
   - Status updates: PENDING → CONFIRMING → CONFIRMED
   - After confirmation, user proceeds to stock purchase

### Viewing Wallet

1. **Navigate to Wallet Page**
   - User clicks "Bitcoin Wallet" in navigation
   - Or accesses `/protected/bitcoin-wallet`

2. **View Wallet Information**
   - Wallet address displayed
   - Current balance shown
   - Creation date visible
   - Can refresh to update balance
   - Link to create new payment

## API Integration

### Backend Endpoints Used

1. **Create Payment**
   ```
   POST /broker-service/v1/bitcoin/{accountId}/payment
   Body: { amount, instrumentId, purpose }
   ```

2. **Get Payment Status**
   ```
   GET /broker-service/v1/bitcoin/payment/{paymentId}
   ```

3. **Get Wallet**
   ```
   GET /broker-service/v1/bitcoin/{accountId}/wallet
   ```

4. **Check Confirmation**
   ```
   GET /broker-service/v1/bitcoin/payment/{paymentId}/confirmed
   ```

## Configuration

### Environment Variables
The Bitcoin service URL is configured through the broker service URL:
- Development: Set in `EnvProxy.getBrokerServiceUrl()`
- Production: Configured via Kubernetes ConfigMap

### Routing
Add to router configuration:

```typescript
{
  path: "bitcoin-payment",
  element: <BitcoinPayment />
},
{
  path: "bitcoin-wallet",
  element: <BitcoinWallet />
}
```

### Navigation
Add to protected navigation menu:

```typescript
{
  title: "Bitcoin Wallet",
  path: "/protected/bitcoin-wallet",
  icon: <AccountBalanceWallet />
},
{
  title: "Bitcoin Payment",
  path: "/protected/bitcoin-payment",
  icon: <Payment />
}
```

## TypeScript Interfaces

### BitcoinPaymentRequest
```typescript
interface BitcoinPaymentRequest {
    amount: number
    instrumentId: number
    purpose?: string
}
```

### BitcoinPaymentResponse
```typescript
interface BitcoinPaymentResponse {
    paymentId: string
    walletAddress: string
    amount: number
    currency: string
    status: string
    expiresAt: string
    qrCode: string
    confirmations: number
    transactionHash?: string
    createdAt: string
    confirmedAt?: string
}
```

### BitcoinWalletResponse
```typescript
interface BitcoinWalletResponse {
    accountId: number
    walletAddress: string
    balance: number
    createdAt: string
}
```

## Error Handling

### Network Errors
- Display user-friendly error messages
- Retry button for transient failures
- Fallback to manual wallet address entry if QR code fails

### Validation Errors
- Minimum amount validation (0.00000001 BTC)
- Positive number validation
- Required field validation

### Payment Status Errors
- Handle expired payments with option to create new one
- Handle failed payments with error explanation
- Handle network timeouts gracefully

## Styling

Uses Material-UI (MUI) components:
- Cards for content containers
- Steppers for multi-step flow
- Alerts for notifications
- Papers for information display
- Buttons with loading states
- CircularProgress for loading indicators

## Responsive Design

- Mobile-friendly QR code sizing
- Responsive container widths (maxWidth="md")
- Touch-friendly button sizes
- Readable font sizes on all devices
- Proper spacing for mobile and desktop

## Testing

### Manual Testing Checklist
- [ ] Create payment with valid amount
- [ ] Verify QR code displays correctly
- [ ] Copy wallet address works
- [ ] Check status button updates payment
- [ ] Confirmed payment shows success message
- [ ] Wallet page loads without errors
- [ ] Wallet displays correct balance
- [ ] Navigation between pages works
- [ ] Error messages display correctly
- [ ] Loading states show appropriately

### Test Scenarios
1. **Happy Path**
   - Create payment → Send BTC → Check status → Confirm

2. **Error Cases**
   - Invalid amount (negative, zero, non-numeric)
   - Expired payment
   - Failed payment
   - Network error during creation
   - Network error during status check

3. **Edge Cases**
   - Very small amounts (0.00000001 BTC)
   - Very large amounts
   - New user with no wallet
   - Multiple payments in progress

## Performance Considerations

### API Calls
- Only load wallet on page mount, not on every render
- Use loading states to prevent duplicate API calls
- Debounce status check button to prevent spam

### State Management
- Minimal state updates to prevent unnecessary re-renders
- Clear error states appropriately
- Reset form state after successful payment

## Security

### Client-Side
- No private keys handled in frontend
- No sensitive data stored in localStorage
- All API calls through HTTPS in production
- Input sanitization for amounts and IDs

### User Education
- Clear messaging about payment expiration
- Instructions for sending exact amount
- Warning about blockchain confirmation times
- Explanation of wallet security model

## Future Enhancements

### Planned Features
- Transaction history page
- Push notifications for confirmations
- Multi-currency support (USD → BTC conversion)
- Batch payment support
- CSV export of transactions
- Advanced wallet management

### Nice-to-Have
- WebSocket for real-time status updates
- Progressive web app support for mobile
- In-app Bitcoin price charts
- Payment scheduling
- Recurring payments

## Troubleshooting

### QR Code Not Displaying
**Cause:** Base64 image data not loading

**Solution:**
1. Check network tab for API response
2. Verify QR code data in payment response
3. Check browser console for image errors

### Payment Status Not Updating
**Cause:** Blockchain confirmation delay or API error

**Solution:**
1. Wait 10-30 minutes for blockchain confirmations
2. Check Bitcoin network status
3. Verify payment was sent to correct address
4. Check browser console for API errors

### Wallet Not Loading
**Cause:** User doesn't have wallet yet or API error

**Solution:**
1. Check if user has made any payments
2. Create first payment to generate wallet
3. Check network tab for 404 responses
4. Verify broker-service is running

## Support

For issues or questions:
- Check browser console for errors
- Review network tab for failed API calls
- Check broker-service logs
- Verify bitcoin-payment-service is running
- Review documentation: `src/broker-service/BITCOIN_INTEGRATION.md`

## Related Documentation

- Backend Integration: `src/broker-service/BITCOIN_INTEGRATION.md`
- Bitcoin Service: `src/bitcoin-payment-service/README.md`
- Architecture: `docs/BITCOIN_ARCHITECTURE.md`
- Database Schema: `src/db/sql-scripts/BITCOIN_SCHEMA.md`
