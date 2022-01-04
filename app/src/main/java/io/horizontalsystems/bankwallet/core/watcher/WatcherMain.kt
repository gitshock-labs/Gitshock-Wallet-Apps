package io.horizontalsystems.bankwallet.core.watcher

import io.horizontalsystems.bankwallet.core.IAccountManager
import io.horizontalsystems.bankwallet.core.orNull
import io.horizontalsystems.bankwallet.core.subscribeIO
import io.reactivex.disposables.CompositeDisposable

class WatcherMain(
    private val accountManager: IAccountManager,
    private val watcherAccountHandler: WatcherAccountHandler
) {
    private val disposables = CompositeDisposable()

    fun start() {
        watcherAccountHandler.handleActiveAccount(accountManager.activeAccount)

        accountManager.activeAccountObservable
            .subscribeIO {
                watcherAccountHandler.handleActiveAccount(it.orNull)
            }
            .let {
                disposables.add(it)
            }
    }

}