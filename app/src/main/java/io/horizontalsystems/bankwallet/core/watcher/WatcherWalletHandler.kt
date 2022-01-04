package io.horizontalsystems.bankwallet.core.watcher

import io.horizontalsystems.bankwallet.core.managers.WalletActivator
import io.horizontalsystems.bankwallet.entities.Account
import io.horizontalsystems.marketkit.models.CoinType

class WatcherWalletHandler(private val walletActivator: WalletActivator) {
    fun enableWallets(account: Account) {
        val coinTypes = mutableListOf<CoinType>()

        if (!walletActivator.isEnabled(account, CoinType.Ethereum)) {
            coinTypes.add(CoinType.Ethereum)
        }

        if (!walletActivator.isEnabled(account, CoinType.BinanceSmartChain)) {
            coinTypes.add(CoinType.BinanceSmartChain)
        }

        if (coinTypes.isNotEmpty()) {
            walletActivator.activateWallets(account, coinTypes)
        }
    }

}
