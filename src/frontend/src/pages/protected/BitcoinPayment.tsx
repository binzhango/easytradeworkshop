import { useState } from "react"
import {
    Box,
    Button,
    Card,
    CardContent,
    CircularProgress,
    Container,
    Typography,
    TextField,
    Alert,
    Stepper,
    Step,
    StepLabel,
    Paper,
} from "@mui/material"
import { useAuth } from "../../contexts/AuthContext"
import { backends } from "../../api/backend"
import { BitcoinPaymentResponse } from "../../api/backend/bitcoin"

const steps = ["Enter Amount", "Scan QR Code", "Confirm Payment"]

export default function BitcoinPayment() {
    const { userId } = useAuth()
    const [activeStep, setActiveStep] = useState(0)
    const [amount, setAmount] = useState("")
    const [instrumentId, setInstrumentId] = useState("1") // Default to first instrument
    const [payment, setPayment] = useState<BitcoinPaymentResponse | null>(null)
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [checkingConfirmation, setCheckingConfirmation] = useState(false)

    const handleCreatePayment = async () => {
        if (!userId || !amount || parseFloat(amount) <= 0) {
            setError("Please enter a valid amount")
            return
        }

        setLoading(true)
        setError(null)

        try {
            const paymentResponse = await backends.bitcoin.createPayment(
                Number(userId),
                {
                    amount: parseFloat(amount),
                    instrumentId: Number(instrumentId),
                    purpose: "stock_purchase",
                }
            )

            setPayment(paymentResponse)
            setActiveStep(1)
        } catch (err) {
            console.error("Error creating payment:", err)
            setError("Failed to create Bitcoin payment. Please try again.")
        } finally {
            setLoading(false)
        }
    }

    const handleCheckConfirmation = async () => {
        if (!payment) return

        setCheckingConfirmation(true)
        setError(null)

        try {
            const status = await backends.bitcoin.getPaymentStatus(payment.paymentId)
            setPayment(status)

            if (status.status === "CONFIRMED") {
                setActiveStep(2)
            } else if (status.status === "FAILED" || status.status === "EXPIRED") {
                setError(`Payment ${status.status.toLowerCase()}. Please create a new payment.`)
            }
        } catch (err) {
            console.error("Error checking payment status:", err)
            setError("Failed to check payment status")
        } finally {
            setCheckingConfirmation(false)
        }
    }

    const handleReset = () => {
        setActiveStep(0)
        setAmount("")
        setPayment(null)
        setError(null)
    }

    const formatBitcoinAddress = (address: string) => {
        if (!address || address.length < 10) return address
        return `${address.substring(0, 8)}...${address.substring(address.length - 8)}`
    }

    return (
        <Container maxWidth="md" sx={{ py: 4 }}>
            <Typography variant="h4" gutterBottom>
                Bitcoin Payment
            </Typography>

            <Stepper activeStep={activeStep} sx={{ mb: 4 }}>
                {steps.map((label) => (
                    <Step key={label}>
                        <StepLabel>{label}</StepLabel>
                    </Step>
                ))}
            </Stepper>

            {error && (
                <Alert severity="error" sx={{ mb: 3 }} onClose={() => setError(null)}>
                    {error}
                </Alert>
            )}

            {/* Step 1: Enter Amount */}
            {activeStep === 0 && (
                <Card>
                    <CardContent>
                        <Typography variant="h6" gutterBottom>
                            Enter Payment Amount
                        </Typography>

                        <Box sx={{ mt: 2 }}>
                            <TextField
                                label="Amount (BTC)"
                                type="number"
                                fullWidth
                                value={amount}
                                onChange={(e) => setAmount(e.target.value)}
                                inputProps={{ step: "0.00000001", min: "0" }}
                                helperText="Minimum: 0.00000001 BTC"
                                sx={{ mb: 2 }}
                            />

                            <TextField
                                label="Instrument ID"
                                type="number"
                                fullWidth
                                value={instrumentId}
                                onChange={(e) => setInstrumentId(e.target.value)}
                                helperText="Stock/instrument you want to purchase"
                                sx={{ mb: 3 }}
                            />

                            <Button
                                variant="contained"
                                color="primary"
                                fullWidth
                                onClick={handleCreatePayment}
                                disabled={loading || !amount || parseFloat(amount) <= 0}
                            >
                                {loading ? <CircularProgress size={24} /> : "Create Payment"}
                            </Button>
                        </Box>
                    </CardContent>
                </Card>
            )}

            {/* Step 2: Scan QR Code */}
            {activeStep === 1 && payment && (
                <Card>
                    <CardContent>
                        <Typography variant="h6" gutterBottom>
                            Send Bitcoin to Complete Payment
                        </Typography>

                        <Box sx={{ textAlign: "center", my: 3 }}>
                            {payment.qrCode && (
                                <img
                                    src={payment.qrCode}
                                    alt="Bitcoin QR Code"
                                    style={{ maxWidth: "300px", width: "100%" }}
                                />
                            )}
                        </Box>

                        <Paper elevation={1} sx={{ p: 2, mb: 2, bgcolor: "grey.100" }}>
                            <Typography variant="body2" color="text.secondary" gutterBottom>
                                Wallet Address:
                            </Typography>
                            <Typography
                                variant="body1"
                                sx={{
                                    fontFamily: "monospace",
                                    wordBreak: "break-all",
                                    fontSize: "0.9rem",
                                }}
                            >
                                {payment.walletAddress}
                            </Typography>
                        </Paper>

                        <Paper elevation={1} sx={{ p: 2, mb: 2, bgcolor: "grey.100" }}>
                            <Typography variant="body2" color="text.secondary" gutterBottom>
                                Amount:
                            </Typography>
                            <Typography variant="h6">{payment.amount} BTC</Typography>
                        </Paper>

                        <Paper elevation={1} sx={{ p: 2, mb: 3, bgcolor: "grey.100" }}>
                            <Typography variant="body2" color="text.secondary" gutterBottom>
                                Status:
                            </Typography>
                            <Typography variant="body1" color={payment.status === "PENDING" ? "warning.main" : "success.main"}>
                                {payment.status}
                                {payment.confirmations > 0 && ` (${payment.confirmations} confirmations)`}
                            </Typography>
                        </Paper>

                        <Alert severity="info" sx={{ mb: 2 }}>
                            Send exactly <strong>{payment.amount} BTC</strong> to the address above. The payment
                            expires at {new Date(payment.expiresAt).toLocaleString()}.
                        </Alert>

                        <Box sx={{ display: "flex", gap: 2 }}>
                            <Button
                                variant="outlined"
                                fullWidth
                                onClick={handleReset}
                            >
                                Cancel
                            </Button>
                            <Button
                                variant="contained"
                                color="primary"
                                fullWidth
                                onClick={handleCheckConfirmation}
                                disabled={checkingConfirmation}
                            >
                                {checkingConfirmation ? <CircularProgress size={24} /> : "Check Status"}
                            </Button>
                        </Box>

                        {payment.transactionHash && (
                            <Paper elevation={1} sx={{ p: 2, mt: 2, bgcolor: "success.light" }}>
                                <Typography variant="body2" color="text.secondary" gutterBottom>
                                    Transaction Hash:
                                </Typography>
                                <Typography
                                    variant="body2"
                                    sx={{
                                        fontFamily: "monospace",
                                        wordBreak: "break-all",
                                        fontSize: "0.85rem",
                                    }}
                                >
                                    {payment.transactionHash}
                                </Typography>
                            </Paper>
                        )}
                    </CardContent>
                </Card>
            )}

            {/* Step 3: Payment Confirmed */}
            {activeStep === 2 && payment && (
                <Card>
                    <CardContent>
                        <Box sx={{ textAlign: "center", py: 3 }}>
                            <Typography variant="h5" color="success.main" gutterBottom>
                                ✓ Payment Confirmed!
                            </Typography>

                            <Typography variant="body1" sx={{ mb: 3 }}>
                                Your Bitcoin payment has been confirmed on the blockchain.
                            </Typography>

                            <Paper elevation={1} sx={{ p: 2, mb: 2, bgcolor: "grey.100" }}>
                                <Typography variant="body2" color="text.secondary" gutterBottom>
                                    Payment ID:
                                </Typography>
                                <Typography variant="body2" sx={{ fontFamily: "monospace" }}>
                                    {payment.paymentId}
                                </Typography>
                            </Paper>

                            <Paper elevation={1} sx={{ p: 2, mb: 2, bgcolor: "grey.100" }}>
                                <Typography variant="body2" color="text.secondary" gutterBottom>
                                    Amount:
                                </Typography>
                                <Typography variant="h6">{payment.amount} BTC</Typography>
                            </Paper>

                            <Paper elevation={1} sx={{ p: 2, mb: 3, bgcolor: "grey.100" }}>
                                <Typography variant="body2" color="text.secondary" gutterBottom>
                                    Confirmations:
                                </Typography>
                                <Typography variant="body1">{payment.confirmations}</Typography>
                            </Paper>

                            <Button variant="contained" color="primary" fullWidth onClick={handleReset}>
                                Make Another Payment
                            </Button>
                        </Box>
                    </CardContent>
                </Card>
            )}
        </Container>
    )
}
