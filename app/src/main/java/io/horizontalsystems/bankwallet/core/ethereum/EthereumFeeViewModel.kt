package io.horizontalsystems.bankwallet.core.ethereum

import androidx.core.util.toRange
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.horizontalsystems.bankwallet.R
import io.horizontalsystems.bankwallet.core.App
import io.horizontalsystems.bankwallet.core.ethereum.EvmTransactionFeeService.GasData
import io.horizontalsystems.bankwallet.core.ethereum.EvmTransactionFeeService.GasPrice
import io.horizontalsystems.bankwallet.core.ethereum.EvmTransactionFeeService.Unit
import io.horizontalsystems.bankwallet.core.providers.Translator
import io.horizontalsystems.bankwallet.entities.DataState
import io.horizontalsystems.bankwallet.entities.ViewState
import io.horizontalsystems.core.SingleLiveEvent
import io.reactivex.disposables.CompositeDisposable
import java.math.BigDecimal

class EthereumFeeViewModel(
    private val feeService: IEvmTransactionFeeService,
    private val coinService: EvmCoinService
) : ViewModel(), ISendFeeViewModel, ISendFeePriorityViewModel {

    override val hasEstimatedFee: Boolean = feeService.hasEstimatedFee

    enum class Priority(val description: String) {
        Recommended(Translator.getString(R.string.Send_TxSpeed_Recommended)),
        Custom(Translator.getString(R.string.Send_TxSpeed_Custom))
    }

    override val estimatedFeeLiveData = MutableLiveData("")
    override val feeLiveData = MutableLiveData("")

    override val priorityLiveData = MutableLiveData("")
    override val openSelectPriorityLiveEvent = SingleLiveEvent<List<SendPriorityViewItem>>()
    override val feeSliderLiveData = MutableLiveData<SendFeeSliderViewItem?>(null)
    override val warningOfStuckLiveData = MutableLiveData<Boolean>()

    val viewStateLiveData = MutableLiveData<ViewState>()
    val gasDataLiveData = MutableLiveData<GasData>()

    private val customFeeUnit = "gwei"
    private val disposable = CompositeDisposable()

    init {
        syncTransactionStatus(feeService.transactionStatus)
        syncGasPriceType(feeService.gasPriceType)

        feeService.transactionStatusObservable
            .subscribe { transactionStatus ->
                syncTransactionStatus(transactionStatus)
            }
            .let {
                disposable.add(it)
            }

        feeService.gasPriceTypeObservable
            .subscribe { gasPriceType ->
                syncGasPriceType(gasPriceType)
            }
            .let {
                disposable.add(it)
            }

        feeService.warningOfStuckObservable
            .subscribe { warningOfStuck ->
                warningOfStuckLiveData.postValue(warningOfStuck)
            }
            .let {
                disposable.add(it)
            }
    }

    override fun openSelectPriority() {
        val currentPriority = getPriority(feeService.gasPriceType)

        val viewItems = Priority.values().map {
            SendPriorityViewItem(it.description, currentPriority == it)
        }

        openSelectPriorityLiveEvent.postValue(viewItems)
    }

    override fun selectPriority(index: Int) {
        val selectedPriority = Priority.values()[index]
        val currentPriority = getPriority(feeService.gasPriceType)

        if (selectedPriority == currentPriority) return

        feeService.gasPriceType = when (selectedPriority) {
            Priority.Recommended -> {
                EvmTransactionFeeService.GasPriceType.Recommended
            }
            Priority.Custom -> {
                val transaction = feeService.transactionStatus.dataOrNull
                val gasPrice = transaction?.gasData?.gasPrice
                    ?: GasPrice.Legacy(
                        EvmTransactionFeeService.BoundedValue(
                            feeService.customFeeRange.first,
                            feeService.customFeeRange.toRange()
                        ),
                        Unit.WEI
                    )

                EvmTransactionFeeService.GasPriceType.Custom(gasPrice)
            }
        }
    }

    override fun changeCustomPriority(value: Long) {
        feeService.gasPriceType =
            EvmTransactionFeeService.GasPriceType.Custom(
                GasPrice.Legacy(
                    EvmTransactionFeeService.BoundedValue(
                        convert(value, Unit.GWEI, Unit.WEI).toLong(),
                        feeService.customFeeRange.toRange()
                    ),
                    Unit.WEI
                )
            )
    }

    fun formatGasLimit(gasLimit: Long): String {
        return App.numberFormatter.format(gasLimit.toBigDecimal(), 0, 0)
    }

    private fun syncTransactionStatus(transactionStatus: DataState<EvmTransactionFeeService.Transaction>) {
        estimatedFeeLiveData.postValue(estimatedFeeStatus(transactionStatus))
        feeLiveData.postValue(feeStatus(transactionStatus))

        viewStateLiveData.postValue(transactionStatus.viewState)
        transactionStatus.dataOrNull?.let { transaction ->
            gasDataLiveData.postValue(transaction.gasData)
        }
    }

    private fun syncGasPriceType(gasPriceType: EvmTransactionFeeService.GasPriceType) {
        priorityLiveData.postValue(getPriority(gasPriceType).description)

        when (gasPriceType) {
            EvmTransactionFeeService.GasPriceType.Recommended -> {
                feeSliderLiveData.postValue(null)
            }
            is EvmTransactionFeeService.GasPriceType.Custom -> {
                if (feeSliderLiveData.value == null) {
                    val gasPrice = gasPriceType.gasPrice.value
                    feeSliderLiveData.postValue(
                        SendFeeSliderViewItem(
                            initialValue = gwei(wei = gasPrice),
                            range = gwei(feeService.customFeeRange),
                            unit = customFeeUnit
                        )
                    )
                }
            }
        }
    }

    fun convert(value: Long, fromUnit: Unit, toUnit: Unit): BigDecimal {
        return BigDecimal(value * fromUnit.factor) / toUnit.factor.toBigDecimal()
    }

    private fun gwei(wei: Long): Long {
        return wei / 1_000_000_000
    }

    private fun gwei(range: LongRange): LongRange {
        return LongRange(gwei(range.first), gwei(range.last))
    }

    private fun wei(gwei: Long): Long {
        return gwei * 1_000_000_000
    }

    private fun getPriority(gasPriceType: EvmTransactionFeeService.GasPriceType): Priority {
        return when (gasPriceType) {
            EvmTransactionFeeService.GasPriceType.Recommended -> Priority.Recommended
            is EvmTransactionFeeService.GasPriceType.Custom -> Priority.Custom
        }
    }

    private fun estimatedFeeStatus(transactionStatus: DataState<EvmTransactionFeeService.Transaction>): String {
        return when (transactionStatus) {
            DataState.Loading -> {
                Translator.getString(R.string.Alert_Loading)
            }
            is DataState.Error -> {
                Translator.getString(R.string.NotAvailable)
            }
            is DataState.Success -> {
                coinService.amountData(transactionStatus.data.gasData.estimatedFee).getFormatted()
            }
        }
    }

    private fun feeStatus(transactionStatus: DataState<EvmTransactionFeeService.Transaction>): String {
        return when (transactionStatus) {
            DataState.Loading -> {
                Translator.getString(R.string.Alert_Loading)
            }
            is DataState.Error -> {
                Translator.getString(R.string.NotAvailable)
            }
            is DataState.Success -> {
                coinService.amountData(transactionStatus.data.gasData.fee).getFormatted()
            }
        }
    }

}

data class SendFeeSliderViewItem(val initialValue: Long, val range: LongRange, val unit: String)
data class SendPriorityViewItem(val title: String, val selected: Boolean)

//TODO remove unused fields after refactoring
interface ISendFeeViewModel {
    val hasEstimatedFee: Boolean
    val estimatedFeeLiveData: MutableLiveData<String>
    val feeLiveData: LiveData<String>
    val warningOfStuckLiveData: LiveData<Boolean>
}

interface ISendFeePriorityViewModel {
    val priorityLiveData: LiveData<String>
    val openSelectPriorityLiveEvent: SingleLiveEvent<List<SendPriorityViewItem>>
    val feeSliderLiveData: LiveData<SendFeeSliderViewItem?>

    fun openSelectPriority()
    fun selectPriority(index: Int)
    fun changeCustomPriority(value: Long)
}
