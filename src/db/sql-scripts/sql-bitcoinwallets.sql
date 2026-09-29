USE [TradeManagement]
GO
        CREATE TABLE [dbo].[BitcoinWallets] (
                [Id] nvarchar(36) NOT NULL DEFAULT NEWID(),
                [AccountId] INT NOT NULL UNIQUE,
                [WalletAddress] nvarchar(100) NOT NULL UNIQUE,
                [Balance] DECIMAL(18, 8) NOT NULL DEFAULT 0,
                [CreatedAt] datetimeoffset(0) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
                [UpdatedAt] datetimeoffset(0) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
                CONSTRAINT [PK_BitcoinWallets] PRIMARY KEY ([Id]),
                CONSTRAINT [FK_BitcoinWallets_Accounts] FOREIGN KEY ([AccountId]) REFERENCES Accounts([Id])
        )
GO

CREATE INDEX [IX_BitcoinWallets_AccountId] ON [dbo].[BitcoinWallets] ([AccountId])
GO

CREATE INDEX [IX_BitcoinWallets_WalletAddress] ON [dbo].[BitcoinWallets] ([WalletAddress])
GO
