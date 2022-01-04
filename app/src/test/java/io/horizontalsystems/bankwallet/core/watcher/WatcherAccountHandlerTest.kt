package io.horizontalsystems.bankwallet.core.watcher

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import io.horizontalsystems.bankwallet.entities.Account
import io.horizontalsystems.bankwallet.entities.AccountType
import org.junit.Before
import org.junit.Test

class WatcherAccountHandlerTest {

    lateinit var watcherWalletHandler: WatcherWalletHandler
    lateinit var watcherAccountHandler: WatcherAccountHandler

    @Before
    fun setup() {
        watcherWalletHandler = mock()

        watcherAccountHandler = WatcherAccountHandler(watcherWalletHandler)
    }

    @Test
    fun test_start_enableWalletsToWatch() {
        val accountWatch = mock<Account> {
            on { type } doReturn AccountType.Watch("")
        }

        watcherAccountHandler.handleActiveAccount(accountWatch)

        verify(watcherWalletHandler).enableWallets(accountWatch)
    }

    @Test
    fun test_start_doNothing() {
        val accountMnemonic = mock<Account> {
            on { type } doReturn AccountType.Mnemonic(listOf(), "")
        }

        watcherAccountHandler.handleActiveAccount(accountMnemonic)

        verify(watcherWalletHandler, never()).enableWallets(accountMnemonic)
    }
}
