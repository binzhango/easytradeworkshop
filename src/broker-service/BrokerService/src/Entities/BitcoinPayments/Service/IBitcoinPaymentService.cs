using EasyTrade.BrokerService.Entities.BitcoinPayments.DTO;

namespace EasyTrade.BrokerService.Entities.BitcoinPayments.Service;

public interface IBitcoinPaymentService
{
    Task<BitcoinPaymentResponse> CreatePayment(int accountId, CreateBitcoinPaymentDTO paymentDTO);
    Task<BitcoinPaymentResponse> GetPaymentStatus(string paymentId);
    Task<BitcoinWalletResponse> GetWallet(int accountId);
    Task<bool> IsPaymentConfirmed(string paymentId);
}
