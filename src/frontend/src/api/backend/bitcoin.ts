export interface BitcoinPaymentRequest {
    amount: number
    instrumentId: number
    purpose?: string
}

export interface BitcoinPaymentResponse {
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

export interface BitcoinWalletResponse {
    accountId: number
    walletAddress: string
    balance: number
    createdAt: string
}

export class BitcoinBackend {
    private readonly brokerServiceUrl: string

    constructor(brokerServiceUrl: string) {
        this.brokerServiceUrl = brokerServiceUrl
    }

    async createPayment(
        accountId: number,
        request: BitcoinPaymentRequest
    ): Promise<BitcoinPaymentResponse> {
        const url = `${this.brokerServiceUrl}/v1/bitcoin/${accountId}/payment`
        const response = await fetch(url, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify(request),
        })

        if (!response.ok) {
            const error = await response.text()
            console.error("Bitcoin payment creation failed:", error)
            throw new Error(`Failed to create Bitcoin payment: ${response.statusText}`)
        }

        return await response.json()
    }

    async getPaymentStatus(paymentId: string): Promise<BitcoinPaymentResponse> {
        const url = `${this.brokerServiceUrl}/v1/bitcoin/payment/${paymentId}`
        const response = await fetch(url)

        if (!response.ok) {
            const error = await response.text()
            console.error("Failed to get payment status:", error)
            throw new Error(`Failed to get payment status: ${response.statusText}`)
        }

        return await response.json()
    }

    async getWallet(accountId: number): Promise<BitcoinWalletResponse> {
        const url = `${this.brokerServiceUrl}/v1/bitcoin/${accountId}/wallet`
        const response = await fetch(url)

        if (!response.ok) {
            // 404 is expected if wallet doesn't exist yet
            if (response.status === 404) {
                return {
                    accountId,
                    walletAddress: "",
                    balance: 0,
                    createdAt: new Date().toISOString(),
                }
            }
            const error = await response.text()
            console.error("Failed to get wallet:", error)
            throw new Error(`Failed to get wallet: ${response.statusText}`)
        }

        return await response.json()
    }

    async isPaymentConfirmed(paymentId: string): Promise<boolean> {
        const url = `${this.brokerServiceUrl}/v1/bitcoin/payment/${paymentId}/confirmed`
        const response = await fetch(url)

        if (!response.ok) {
            return false
        }

        return await response.json()
    }
}
