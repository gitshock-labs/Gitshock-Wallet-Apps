package io.horizontalsystems.bankwallet.core.ethereum

import io.horizontalsystems.bankwallet.entities.DataState
import io.horizontalsystems.ethereumkit.models.TransactionData
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject

class Eip1559TransactionFeeService: IEvmTransactionFeeService {

    override val customFeeRange: LongRange // TODO will be removed
        get() = 1L..2L
    override val hasEstimatedFee: Boolean // TODO will be removed
        get() = false

    override val transactionStatus: DataState<EvmTransactionFeeService.Transaction>
        get() = TODO("Not yet implemented")

    override val transactionStatusObservable: Observable<DataState<EvmTransactionFeeService.Transaction>>
        get() = TODO("Not yet implemented")

    override var gasPriceType: EvmTransactionFeeService.GasPriceType
        get() = TODO("Not yet implemented")
        set(value) {}
    override val gasPriceTypeObservable: Observable<EvmTransactionFeeService.GasPriceType>
        get() = TODO("Not yet implemented")
    override val warningOfStuckObservable: Flowable<Boolean>
        get() = TODO("Not yet implemented")

    override var cautions: List<FeeSettingsCaution> = listOf()
        private set(value) {
            field = value
            messagesSubject.onNext(value)
        }
    private val messagesSubject = PublishSubject.create<List<FeeSettingsCaution>>()
    override val cautionsObservable: Observable<List<FeeSettingsCaution>>
        get() = messagesSubject

    override fun setTransactionData(transactionData: TransactionData) {
        TODO("not implemented")
    }

    override fun setMaxFee(value: Long) {
        TODO("not implemented")
    }

    override fun setMaxPriorityFee(value: Long) {
        TODO("not implemented")
    }
}
