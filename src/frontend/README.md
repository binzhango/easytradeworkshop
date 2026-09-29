# easyTradeFrontend

A frontend for easyTrade written in react. It has been refreshed and uses material UI

## Technologies used

- Docker
- React.js
- TypeScript

## Local build instructions

```bash
docker build -t IMAGE_NAME .
docker run -d --name SERVICE_NAME IMAGE_NAME
```

## Features

The new fronted has the following features:

- 2 UI color schemes - light and dark - can be switched in the top left corner
- problem pattern management - if enabled, then you can enable/disable feature flags that control problem patterns
- buy/sell stocks at the current price
- long buy/sell disposition - set the price and time for the trade and check later if it succeeded
- order/delete a credit card for your account
- **Bitcoin payments** - create Bitcoin payment requests with QR codes for stock purchases
- **Bitcoin wallet** - view your Bitcoin wallet address and balance

### Bitcoin Payment Integration

The frontend now supports Bitcoin payments as an alternative payment method. Users can:
- Create Bitcoin payment requests with QR codes
- View their Bitcoin wallet and balance  
- Track payment confirmations on the blockchain
- Use Bitcoin to purchase stocks

See [BITCOIN_INTEGRATION.md](./BITCOIN_INTEGRATION.md) for detailed Bitcoin integration documentation.
