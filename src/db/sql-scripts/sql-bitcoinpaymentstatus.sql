USE [TradeManagement]
GO
        CREATE TABLE [dbo].[BitcoinPaymentStatus] (
                [Id] INT IDENTITY(1, 1) NOT NULL,
                [TransactionId] nvarchar(36) NOT NULL,
                [Status] VARCHAR(20) NOT NULL CHECK(
                        Status IN('PENDING', 'CONFIRMING', 'CONFIRMED', 'FAILED', 'EXPIRED')
                ),
                [Timestamp] datetimeoffset(0) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
                [Details] nvarchar(500),
                CONSTRAINT [PK_BitcoinPaymentStatus] PRIMARY KEY CLUSTERED ([Id] ASC) ON [PRIMARY],
                CONSTRAINT [FK_BitcoinPaymentStatus_BitcoinTransactions] FOREIGN KEY ([TransactionId]) REFERENCES BitcoinTransactions([Id])
        )
GO

CREATE INDEX [IX_BitcoinPaymentStatus_TransactionId] ON [dbo].[BitcoinPaymentStatus] ([TransactionId])
GO

CREATE INDEX [IX_BitcoinPaymentStatus_Timestamp] ON [dbo].[BitcoinPaymentStatus] ([Timestamp])
GO

CREATE INDEX [IX_BitcoinPaymentStatus_Status] ON [dbo].[BitcoinPaymentStatus] ([Status])
GO
