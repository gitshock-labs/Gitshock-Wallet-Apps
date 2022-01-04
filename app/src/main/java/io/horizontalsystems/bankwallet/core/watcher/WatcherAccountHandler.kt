package io.horizontalsystems.bankwallet.core.watcher

import io.horizontalsystems.bankwallet.entities.Account
import io.horizontalsystems.bankwallet.entities.AccountType

class WatcherAccountHandler(
    private val watcherWalletHandler: WatcherWalletHandler
) {
    fun handleActiveAccount(activeAccount: Account?) {
        if (activeAccount?.type is AccountType.Watch) {
            watcherWalletHandler.enableWallets(activeAccount)
        }
    }
}

