package io.horizontalsystems.bankwallet.core.ethereum

import android.util.Log
import android.util.Range
import io.horizontalsystems.bankwallet.core.FeeRatePriority
import io.horizontalsystems.bankwallet.core.ICustomRangedFeeProvider
import io.horizontalsystems.bankwallet.core.subscribeIO
import io.horizontalsystems.bankwallet.entities.DataState
import io.horizontalsystems.bankwallet.modules.send.submodules.fee.CustomPriorityUnit
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.models.TransactionData
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.PublishSubject
import java.math.BigInteger

interface IEvmTransactionFeeService {
    val customFeeRange: LongRange
    val hasEstimatedFee: Boolean

    val transactionStatus: DataState<EvmTransactionFeeService.Transaction>
    val transactionStatusObservable: Observable<DataState<EvmTransactionFeeService.Transaction>>

    var gasPriceType: EvmTransactionFeeService.GasPriceType
    val gasPriceTypeObservable: Observable<EvmTransactionFeeService.GasPriceType>

    val warningOfStuckObservable: Flowable<Boolean>

    val cautions: List<FeeSettingsCaution>
    val cautionsObservable: Observable<List<FeeSettingsCaution>>

    fun setTransactionData(transactionData: TransactionData)
    fun setMaxFee(value: Long)
    fun setMaxPriorityFee(value: Long)
//    fun set
}

sealed class FeeSettingsCaution
abstract class FeeSettingsWarning : FeeSettingsCaution()
abstract class FeeSettingsError : FeeSettingsCaution()

object HighBaseFeeWarning : FeeSettingsWarning()
object RiskOfGettingStuck : FeeSettingsWarning()

object InsufficientBalance : FeeSettingsError()
object LowBaseFee : FeeSettingsError()

interface EvmGasPriceService {
    fun getRecommendedGasPrice(): Single<EvmTransactionFeeService.GasPrice>
//    fun get

}

class EvmTransactionFeeService(
    private val evmKit: EthereumKit,
    private val feeRateProvider: ICustomRangedFeeProvider,
    private val gasLimitSurchargePercent: Int = 0
) : IEvmTransactionFeeService {

    private var recommendedGasPrice: BigInteger? = null
    private var warningOfStuckSubject = PublishSubject.create<Boolean>()
    override val warningOfStuckObservable: Flowable<Boolean>
        get() = warningOfStuckSubject.toFlowable(BackpressureStrategy.BUFFER)

    override val hasEstimatedFee: Boolean = gasLimitSurchargePercent != 0

    private var transactionData: TransactionData? = null

    override var gasPriceType: GasPriceType = GasPriceType.Recommended
        set(value) {
            field = value
            gasPriceTypeSubject.onNext(value)
            sync()
        }

    private val gasPriceTypeSubject = PublishSubject.create<GasPriceType>()
    override val gasPriceTypeObservable: Observable<GasPriceType> = gasPriceTypeSubject

    override var transactionStatus: DataState<Transaction> = DataState.Error(GasDataError.NoTransactionData)
        private set(value) {
            field = value
            transactionStatusSubject.onNext(value)
        }
    private val transactionStatusSubject = PublishSubject.create<DataState<Transaction>>()
    override val transactionStatusObservable: Observable<DataState<Transaction>> = transactionStatusSubject

    override var cautions: List<FeeSettingsCaution> = listOf()
        private set(value) {
            field = value
            cautionsSubject.onNext(value)
        }

    private val cautionsSubject = PublishSubject.create<List<FeeSettingsCaution>>()
    override val cautionsObservable: Observable<List<FeeSettingsCaution>>
        get() = cautionsSubject

    val disposable = CompositeDisposable()

    private val evmBalance: BigInteger
        get() = evmKit.accountState?.balance ?: BigInteger.ZERO

    override val customFeeRange: LongRange
        get() = feeRateProvider.customFeeRange

    override fun setTransactionData(transactionData: TransactionData) {
        this.transactionData = transactionData
        sync()
    }

    override fun setMaxFee(value: Long) {
        TODO("not implemented")
    }

    override fun setMaxPriorityFee(value: Long) {
        TODO("not implemented")
    }

    fun onCleared() {
        disposable.clear()
    }

    private fun sync() {
        disposable.clear()

        val transactionData = this.transactionData
        if (transactionData == null) {
            transactionStatus = DataState.Error(GasDataError.NoTransactionData)
            return
        }

        transactionStatus = DataState.Loading

        getGasPriceAsync(gasPriceType)
            .flatMap { gasPrice ->
                getTransactionAsync(gasPrice, transactionData)
            }
            .subscribeIO({
                transactionStatus = DataState.Success(it)
            }, {
                transactionStatus = DataState.Error(it)
            })
            .let {
                disposable.add(it)
            }
    }

    private fun getTransactionAsync(gasPrice: BigInteger, transactionData: TransactionData): Single<Transaction> {
        return getAdjustedTransactionDataAsync(gasPrice, transactionData)
            .flatMap { adjustedTransactionData ->
                getGasLimitAsync(gasPrice, adjustedTransactionData)
                    .map { estimatedGasLimit ->
                        val gasLimit = getSurchargedGasLimit(estimatedGasLimit)
                        Transaction(
                            adjustedTransactionData,
                            GasData(
                                estimatedGasLimit,
                                gasLimit,
                                GasPrice.Legacy(
                                    gasPrice.toLong(),
                                    customFeeRange
                                )
                            )
                        )
                    }
            }
    }

    private fun getAdjustedTransactionDataAsync(
        gasPrice: BigInteger,
        transactionData: TransactionData
    ): Single<TransactionData> {
        if (transactionData.input.isEmpty() && transactionData.value == evmBalance) {
            val stubTransactionData = TransactionData(transactionData.to, BigInteger.ONE, byteArrayOf())
            return getGasLimitAsync(gasPrice, stubTransactionData)
                .flatMap { estimatedGasLimit ->
                    val gasLimit = getSurchargedGasLimit(estimatedGasLimit)
                    val adjustedValue = transactionData.value - gasLimit.toBigInteger() * gasPrice

                    if (adjustedValue <= BigInteger.ZERO) {
                        Single.error(GasDataError.InsufficientBalance)
                    } else {
                        val adjustedTransactionData = TransactionData(transactionData.to, adjustedValue, byteArrayOf())
                        Single.just(adjustedTransactionData)
                    }
                }
        } else {
            return Single.just(transactionData)
        }
    }

    private fun getGasPriceAsync(gasPriceType: GasPriceType): Single<BigInteger> {
        var recommendedGasPriceSingle = feeRateProvider.feeRate(FeeRatePriority.RECOMMENDED)
            .doOnSuccess { gasPrice ->
                Log.e("AAA", "recommendedGasPrice: $gasPrice")
                recommendedGasPrice = gasPrice
            }

//        return Single.error(Throwable("error"))

        return when (gasPriceType) {
            is GasPriceType.Recommended -> {
                warningOfStuckSubject.onNext(false)
                recommendedGasPriceSingle
            }
            is GasPriceType.Custom -> {
                recommendedGasPrice?.let {
                    recommendedGasPriceSingle = Single.just(it)
                }
                recommendedGasPriceSingle.map { recommended ->
                    val customGasPrice = gasPriceType.gasPrice.value.toBigInteger()

                    val customGasPriceInGwei = CustomPriorityUnit.Gwei.fromBaseUnit(gasPriceType.gasPrice.value)
                    val recommendedInGwei = CustomPriorityUnit.Gwei.fromBaseUnit(recommended.toLong())
                    warningOfStuckSubject.onNext(customGasPriceInGwei < recommendedInGwei)

                    customGasPrice
                }
            }
        }

    }

    private fun getGasLimitAsync(gasPrice: BigInteger, transactionData: TransactionData): Single<Long> {
        return evmKit.estimateGas(transactionData, gasPrice.toLong())
    }

    private fun getSurchargedGasLimit(estimatedGasLimit: Long): Long {
        return (estimatedGasLimit + estimatedGasLimit / 100.0 * gasLimitSurchargePercent).toLong()
    }

    // types

    data class GasData(
        val estimatedGasLimit: Long,
        val gasLimit: Long,
        val gasPrice: GasPrice
    ) {
        val estimatedFee: BigInteger
            get() = estimatedGasLimit.toBigInteger() * gasPrice.value.toBigInteger()

        val fee: BigInteger
            get() = gasLimit.toBigInteger() * gasPrice.value.toBigInteger()
    }

    data class Transaction(
        val transactionData: TransactionData,
        val gasData: GasData
    ) {
        val totalAmount: BigInteger
            get() = transactionData.value + gasData.fee
    }

    sealed class GasPrice {
        class Legacy(
            val gasPrice: Long,
            val gasPriceRange: LongRange
        ) : GasPrice()

        class Eip1559(
            val baseFee: Long,
            val maxFeePerGas: Long,
            val maxFeePerGasRange: LongRange,
            val maxPriorityFeePerGas: Long,
            val maxPriorityFeePerGasRange: LongRange,
        ) : GasPrice()

        val value: Long
            get() = when (this) {
                is Eip1559 -> maxFeePerGas + maxPriorityFeePerGas
                is Legacy -> gasPrice
            }
    }

    enum class Unit(val title: String, val factor: Int) {
        WEI("wei", 1),
        GWEI("gwei", 1_000_000_000)
    }

    sealed class GasPriceType {
        object Recommended : GasPriceType()
        class Custom(val gasPrice: GasPrice) : GasPriceType()
    }

    sealed class GasDataError : Error() {
        object NoTransactionData : GasDataError()
        object InsufficientBalance : GasDataError()
    }

}
