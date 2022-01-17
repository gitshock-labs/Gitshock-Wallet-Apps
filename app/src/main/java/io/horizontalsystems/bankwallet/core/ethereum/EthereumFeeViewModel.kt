package io.horizontalsystems.bankwallet.core.ethereum

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
import io.horizontalsystems.bankwallet.modules.sendevmtransaction.feesettings.FeeSettingsViewItem
import io.horizontalsystems.bankwallet.modules.swap.settings.Caution
import io.horizontalsystems.core.SingleLiveEvent
import io.reactivex.disposables.CompositeDisposable
import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.let
import kotlin.toBigDecimal

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
    val viewItemLiveData = MutableLiveData<FeeSettingsViewItem>()
    val cautionsLiveData = MutableLiveData<List<Caution>>()

    private val customFeeUnit = "gwei"
    private val disposable = CompositeDisposable()

    init {
        syncTransactionStatus(feeService.transactionStatus)
        syncGasPriceType(feeService.gasPriceType)
        syncMessages(feeService.cautions)

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

        feeService.cautionsObservable
            .subscribe { messages ->
                val cautions = messages.map {
                    when (it) {
                        is FeeSettingsError -> Caution(it.javaClass.simpleName, Caution.Type.Error)
                        is FeeSettingsWarning -> Caution(it.javaClass.simpleName, Caution.Type.Warning)
                    }
                }
                cautionsLiveData.postValue(cautions)
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
                        feeService.customFeeRange.first,
                        feeService.customFeeRange
                    )

                EvmTransactionFeeService.GasPriceType.Custom(gasPrice)
            }
        }
    }

    override fun changeCustomPriority(value: Long) {
        feeService.gasPriceType =
            EvmTransactionFeeService.GasPriceType.Custom(
                GasPrice.Legacy(
                    wei(value),
                    feeService.customFeeRange
                )
            )
    }

    fun onChangeMaxFee(value: Long) {
//        feeService.gasPriceType = EvmTransactionFeeService.GasPriceType.Custom(
//            GasPrice.Eip1559(
//
//            )
//        )
        feeService.setMaxFee(value)
    }

    fun onChangeMaxPriorityFee(value: Long) {
        feeService.setMaxPriorityFee(value)
    }

    private fun syncTransactionStatus(transactionStatus: DataState<EvmTransactionFeeService.Transaction>) {
        estimatedFeeLiveData.postValue(estimatedFeeStatus(transactionStatus))
        feeLiveData.postValue(feeStatus(transactionStatus))

        viewStateLiveData.postValue(transactionStatus.viewState)

        transactionStatus.dataOrNull?.let { transaction ->
            val viewItem = when (val gasPrice = transaction.gasData.gasPrice) {
                is GasPrice.Eip1559 -> {
                    FeeSettingsViewItem.Eip1559FeeSettingsViewItem(
                        gasLimit = App.numberFormatter.format(transaction.gasData.gasLimit.toBigDecimal(), 0, 0),
                        baseFee = gwei(gasPrice.baseFee),
                        maxFee = gwei(gasPrice.maxFeePerGas),
                        maxFeeRange = gwei(gasPrice.maxFeePerGasRange),
                        maxPriorityFee = gwei(gasPrice.maxPriorityFeePerGas),
                        maxPriorityFeeRange = gwei(gasPrice.maxPriorityFeePerGasRange),
                        unit = Unit.GWEI.title
                    )
                }
                is GasPrice.Legacy -> {
                    FeeSettingsViewItem.LegacyFeeSettingsViewItem(
                        gasLimit = App.numberFormatter.format(transaction.gasData.gasLimit.toBigDecimal(), 0, 0),
                        gasPrice = gwei(gasPrice.gasPrice),
                        gasPriceRange = gwei(gasPrice.gasPriceRange),
                        unit = Unit.GWEI.title
                    )
                }
            }
            viewItemLiveData.postValue(viewItem)

//            gasDataLiveData.postValue(transaction.gasData)
        }
    }

    private fun syncMessages(cautions: List<FeeSettingsCaution>) {

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

//    fun convert(value: Long, fromUnit: Unit, toUnit: Unit = Unit.GWEI): Int {
//        return (value * fromUnit.factor / toUnit.factor).toInt()
//    }
//
//    fun convert(range: Range<Long>, fromUnit: Unit, toUnit: Unit = Unit.GWEI): IntRange {
//        return convert(range.lower, fromUnit, toUnit)..convert(range.upper, fromUnit, toUnit)
//    }

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
