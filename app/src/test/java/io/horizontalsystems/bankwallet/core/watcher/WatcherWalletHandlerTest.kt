package io.horizontalsystems.bankwallet.core.watcher

import com.nhaarman.mockito_kotlin.*
import io.horizontalsystems.bankwallet.core.managers.WalletActivator
import io.horizontalsystems.bankwallet.entities.Account
import io.horizontalsystems.marketkit.models.CoinType
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class WatcherWalletHandlerTest {
    private lateinit var walletActivator: WalletActivator
    private lateinit var watcherWalletHandler: WatcherWalletHandler

    @Before
    fun setup() {
        walletActivator = mock()
        watcherWalletHandler = WatcherWalletHandler(walletActivator)
    }

    @Test
    fun test_enableWallets() {
        val account = mock<Account>()

        whenever(walletActivator.isEnabled(account, CoinType.Ethereum)).thenReturn(false)
        whenever(walletActivator.isEnabled(account, CoinType.BinanceSmartChain)).thenReturn(false)

        watcherWalletHandler.enableWallets(account)

        verify(walletActivator).activateWallets(account, listOf(CoinType.Ethereum, CoinType.BinanceSmartChain))
    }

    @Test
    fun test_enableWallets_ethAlreadyEnabled() {
        val account = mock<Account>()

        whenever(walletActivator.isEnabled(account, CoinType.Ethereum)).thenReturn(true)
        whenever(walletActivator.isEnabled(account, CoinType.BinanceSmartChain)).thenReturn(false)

        watcherWalletHandler.enableWallets(account)

        verify(walletActivator).activateWallets(account, listOf(CoinType.BinanceSmartChain))
    }

    @Test
    fun test_enableWallets_alreadyEnabled() {
        val account = mock<Account>()

        whenever(walletActivator.isEnabled(account, CoinType.Ethereum)).thenReturn(true)
        whenever(walletActivator.isEnabled(account, CoinType.BinanceSmartChain)).thenReturn(true)

        watcherWalletHandler.enableWallets(account)

        verify(walletActivator, never()).activateWallets(any(), Mockito.anyList())
    }
}