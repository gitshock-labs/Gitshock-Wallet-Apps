package io.horizontalsystems.bankwallet.core.watcher

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.horizontalsystems.bankwallet.core.managers.AccountManager
import io.horizontalsystems.bankwallet.entities.Account
import io.reactivex.BackpressureStrategy
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import org.junit.Before
import org.junit.Test
import java.util.*

class WatcherMainTest {
    private lateinit var watcherAccountHandler: WatcherAccountHandler
    private lateinit var accountManager: AccountManager
    private lateinit var watcherMain: WatcherMain
    private lateinit var activeAccountSubject: PublishSubject<Optional<Account>>

    @Before
    fun setup() {
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }

        accountManager = mock()
        watcherAccountHandler = mock()
        activeAccountSubject = PublishSubject.create()

        whenever(accountManager.activeAccountObservable).thenReturn(activeAccountSubject.toFlowable(BackpressureStrategy.BUFFER))

        watcherMain = WatcherMain(accountManager, watcherAccountHandler)
    }

    @Test
    fun test_handleAccount_initial() {
        val account = mock<Account>()
        whenever(accountManager.activeAccount).thenReturn(account)

        watcherMain.start()

        verify(watcherAccountHandler).handleActiveAccount(account)
    }

    @Test
    fun test_handleAccount_forEachActiveAccountChange() {
        watcherMain.start()
        reset(watcherAccountHandler)

        val account = mock<Account>()
        activeAccountSubject.onNext(Optional.ofNullable(account))

        verify(watcherAccountHandler).handleActiveAccount(account)
    }
}