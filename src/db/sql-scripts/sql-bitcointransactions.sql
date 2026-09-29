USE [TradeManagement]
GO
        CREATE TABLE [dbo].[BitcoinTransactions] (
                [Id] nvarchar(36) NOT NULL DEFAULT NEWID(),
                [AccountId] INT NOT NULL,
                [WalletAddress] nvarchar(100) NOT NULL,
                [TransactionHash] nvarchar(100) UNIQUE,
                [Amount] DECIMAL(18, 8) NOT NULL,
                [Type] VARCHAR(20) NOT NULL CHECK(
                        Type IN('PAYMENT', 'REFUND', 'WITHDRAWAL', 'DEPOSIT')
                ),
                [Status] VARCHAR(20) NOT NULL CHECK(
                        Status IN('PENDING', 'CONFIRMING', 'CONFIRMED', 'FAILED', 'EXPIRED')
                ),
                [Confirmations] INT NOT NULL DEFAULT 0,
                [Purpose] VARCHAR(50),
                [Metadata] nvarchar(MAX),
                [CreatedAt] datetimeoffset(0) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
                [UpdatedAt] datetimeoffset(0) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
                [ConfirmedAt] datetimeoffset(0),
                [ExpiresAt] datetimeoffset(0),
                CONSTRAINT [PK_BitcoinTransactions] PRIMARY KEY ([Id]),
                CONSTRAINT [FK_BitcoinTransactions_Accounts] FOREIGN KEY ([AccountId]) REFERENCES Accounts([Id])
        )
GO

CREATE INDEX [IX_BitcoinTransactions_AccountId] ON [dbo].[BitcoinTransactions] ([AccountId])
GO

CREATE INDEX [IX_BitcoinTransactions_WalletAddress] ON [dbo].[BitcoinTransactions] ([WalletAddress])
GO

CREATE INDEX [IX_BitcoinTransactions_TransactionHash] ON [dbo].[BitcoinTransactions] ([TransactionHash])
GO

CREATE INDEX [IX_BitcoinTransactions_Status] ON [dbo].[BitcoinTransactions] ([Status])
GO

CREATE INDEX [IX_BitcoinTransactions_CreatedAt] ON [dbo].[BitcoinTransactions] ([CreatedAt])
GO

CREATE INDEX [IX_BitcoinTransactions_Type_Status] ON [dbo].[BitcoinTransactions] ([Type], [Status])
GO
