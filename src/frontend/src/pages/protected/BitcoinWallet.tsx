import { useEffect, useState } from "react"
import {
    Box,
    Card,
    CardContent,
    Container,
    Typography,
    CircularProgress,
    Alert,
    Paper,
    Button,
} from "@mui/material"
import { AccountBalanceWallet as WalletIcon } from "@mui/icons-material"
import { useAuth } from "../../contexts/AuthContext"
import { backends } from "../../api/backend"
import { BitcoinWalletResponse } from "../../api/backend/bitcoin"

export default function BitcoinWallet() {
    const { userId } = useAuth()
    const [wallet, setWallet] = useState<BitcoinWalletResponse | null>(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)

    const loadWallet = async () => {
        if (!userId) return

        setLoading(true)
        setError(null)

        try {
            const walletData = await backends.bitcoin.getWallet(Number(userId))
            setWallet(walletData)
        } catch (err) {
            console.error("Error loading wallet:", err)
            setError("Failed to load Bitcoin wallet")
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        loadWallet()
    }, [userId])

    if (loading) {
        return (
            <Container maxWidth="md" sx={{ py: 4, textAlign: "center" }}>
                <CircularProgress />
            </Container>
        )
    }

    return (
        <Container maxWidth="md" sx={{ py: 4 }}>
            <Box sx={{ display: "flex", alignItems: "center", mb: 3 }}>
                <WalletIcon sx={{ fontSize: 40, mr: 2 }} />
                <Typography variant="h4">Bitcoin Wallet</Typography>
            </Box>

            {error && (
                <Alert severity="error" sx={{ mb: 3 }} onClose={() => setError(null)}>
                    {error}
                </Alert>
            )}

            {!wallet || !wallet.walletAddress ? (
                <Card>
                    <CardContent>
                        <Typography variant="h6" gutterBottom>
                            No Wallet Yet
                        </Typography>
                        <Typography variant="body1" color="text.secondary" sx={{ mb: 2 }}>
                            Your Bitcoin wallet will be automatically created when you make your first payment.
                        </Typography>
                        <Button variant="contained" color="primary" href="/protected/bitcoin-payment">
                            Create First Payment
                        </Button>
                    </CardContent>
                </Card>
            ) : (
                <Card>
                    <CardContent>
                        <Typography variant="h6" gutterBottom>
                            Your Bitcoin Wallet
                        </Typography>

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
                                {wallet.walletAddress}
                            </Typography>
                        </Paper>

                        <Paper elevation={1} sx={{ p: 2, mb: 2, bgcolor: "grey.100" }}>
                            <Typography variant="body2" color="text.secondary" gutterBottom>
                                Balance:
                            </Typography>
                            <Typography variant="h5">{wallet.balance.toFixed(8)} BTC</Typography>
                        </Paper>

                        <Paper elevation={1} sx={{ p: 2, mb: 3, bgcolor: "grey.100" }}>
                            <Typography variant="body2" color="text.secondary" gutterBottom>
                                Created:
                            </Typography>
                            <Typography variant="body1">
                                {new Date(wallet.createdAt).toLocaleString()}
                            </Typography>
                        </Paper>

                        <Alert severity="info">
                            This wallet is managed by EasyTrade. You can use it to make payments for stock purchases.
                        </Alert>

                        <Box sx={{ mt: 2, display: "flex", gap: 2 }}>
                            <Button variant="outlined" fullWidth onClick={loadWallet}>
                                Refresh
                            </Button>
                            <Button variant="contained" color="primary" fullWidth href="/protected/bitcoin-payment">
                                Make Payment
                            </Button>
                        </Box>
                    </CardContent>
                </Card>
            )}
        </Container>
    )
}
