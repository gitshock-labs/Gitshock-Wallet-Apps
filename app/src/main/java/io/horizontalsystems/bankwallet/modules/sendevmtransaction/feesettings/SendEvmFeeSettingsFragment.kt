package io.horizontalsystems.bankwallet.modules.sendevmtransaction.feesettings

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import io.horizontalsystems.bankwallet.R
import io.horizontalsystems.bankwallet.core.BaseFragment
import io.horizontalsystems.bankwallet.core.ethereum.EthereumFeeViewModel
import io.horizontalsystems.bankwallet.modules.sendevmtransaction.FeeCell
import io.horizontalsystems.bankwallet.ui.compose.ComposeAppTheme
import io.horizontalsystems.bankwallet.ui.compose.TranslatableString
import io.horizontalsystems.bankwallet.ui.compose.components.AppBar
import io.horizontalsystems.bankwallet.ui.compose.components.CellSingleLineLawrenceSection

class SendEvmFeeSettingsFragment : BaseFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val feeViewModel by navGraphViewModels<EthereumFeeViewModel>(requireArguments().getInt(NAV_GRAPH_ID))

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnLifecycleDestroyed(viewLifecycleOwner)
            )
            setContent {
                ComposeAppTheme {
                    SendEvmFeeSettingsScreen(
                        feeViewModel,
                        onClickNavigation = {
                            findNavController().popBackStack()
                        }
                    )
                }
            }
        }
    }

    companion object {
        private const val NAV_GRAPH_ID = "nav_graph_id"

        fun prepareParams(@IdRes navGraphId: Int) =
            bundleOf(NAV_GRAPH_ID to navGraphId)
    }

}

@Composable
fun SendEvmFeeSettingsScreen(
    viewModel: EthereumFeeViewModel,
    onClickNavigation: () -> Unit
) {
    val fee by viewModel.feeLiveData.observeAsState()
    val gasData by viewModel.gasDataLiveData.observeAsState()
    val viewItem by viewModel.viewItemLiveData.observeAsState()
    val viewState by viewModel.viewStateLiveData.observeAsState()

//    val feeSlider by viewModel.feeSliderLiveData.observeAsState()

    Column(modifier = Modifier.background(color = ComposeAppTheme.colors.tyler)) {
        AppBar(
            title = TranslatableString.ResString(R.string.FeeSettings_Title),
            navigationIcon = {
                IconButton(onClick = onClickNavigation) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_back),
                        contentDescription = "back button",
                        tint = ComposeAppTheme.colors.jacob
                    )
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        FeeCell(title = stringResource(R.string.FeeSettings_Fee), fee)
        Spacer(modifier = Modifier.height(8.dp))


        when (val viewItem = viewItem) {
            is FeeSettingsViewItem.Eip1559FeeSettingsViewItem -> {
                Eip1559FeeSettings(
                    viewItem = viewItem,
                    onSelectMaxFee = { maxFee ->
                        viewModel.onChangeMaxFee(maxFee)
                    },
                    onSelectMaxPriorityFee = { maxPriorityFee ->
                        viewModel.onChangeMaxPriorityFee(maxPriorityFee)
                    }
                )
            }
            is FeeSettingsViewItem.LegacyFeeSettingsViewItem -> {
                LegacyFeeSettings(viewItem) {
                    Log.e("AAA", "onSelectGasPrice: $it")

                    viewModel.changeCustomPriority(it)
                }
            }
        }

        viewModel.cautionsLiveData // TODO HANDLE

    }
}

sealed class FeeSettingsViewItem {

    data class LegacyFeeSettingsViewItem(
        val gasLimit: String,
        val gasPrice: Long,
        val gasPriceRange: LongRange,
        val unit: String
    ) : FeeSettingsViewItem()

    data class Eip1559FeeSettingsViewItem(
        val gasLimit: String,
        val baseFee: Long,
        val maxFee: Long,
        val maxFeeRange: LongRange,
        val maxPriorityFee: Long,
        val maxPriorityFeeRange: LongRange,
        val unit: String
    ) : FeeSettingsViewItem()

}

@Composable
fun Eip1559FeeSettings(
    viewItem: FeeSettingsViewItem.Eip1559FeeSettingsViewItem,
    onSelectMaxFee: (value: Long) -> Unit,
    onSelectMaxPriorityFee: (value: Long) -> Unit
) {

}

@Composable
fun LegacyFeeSettings(
    viewItem: FeeSettingsViewItem.LegacyFeeSettingsViewItem,
    onSelectGasPrice: (value: Long) -> Unit
) {
    val settingsViewItems = mutableListOf<@Composable () -> Unit>()
    var selectedGasPrice by remember { mutableStateOf(viewItem.gasPrice) }

    Log.e("AAA", "LegacyFeeSettings")

    settingsViewItems.add {
        FeeInfoCell(
            title = stringResource(R.string.FeeSettings_GasLimit),
            value = viewItem.gasLimit
        ) {
            //Open Gas Limit info
        }
    }

    settingsViewItems.add {
        FeeInfoCell(
            title = stringResource(R.string.FeeSettings_GasPrice),
            value = "$selectedGasPrice ${viewItem.unit}"
        ) {
            //Open Gas Price info
        }
    }

    settingsViewItems.add {
        HsSlider(
            value = viewItem.gasPrice.toInt(),
            onValueChange = { selectedGasPrice = it.toLong() },
            valueRange = viewItem.gasPriceRange.first.toInt()..viewItem.gasPriceRange.last.toInt(),
            onValueChangeFinished = { onSelectGasPrice(selectedGasPrice) }
        )
    }

    CellSingleLineLawrenceSection(settingsViewItems)
}

@Composable
private fun HsSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: ClosedRange<Int>,
    onValueChangeFinished: (() -> Unit)
) {
    var selectedValue: Float by remember { mutableStateOf(value.toFloat()) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            modifier = Modifier.clickable {
                selectedValue--
                onValueChange(selectedValue.toInt())
                onValueChangeFinished()
            },
            painter = painterResource(id = R.drawable.ic_minus_20),
            contentDescription = ""
        )
        Slider(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            value = selectedValue,
            onValueChange = {
                Log.e("AAA", "onValueChange: $it")
                selectedValue = it
                onValueChange(selectedValue.toInt())
            },
            valueRange = valueRange.start.toFloat()..valueRange.endInclusive.toFloat(),
            onValueChangeFinished = onValueChangeFinished,
            steps = valueRange.endInclusive - valueRange.start,
            colors = SliderDefaults.colors(
                thumbColor = ComposeAppTheme.colors.grey,
                activeTickColor = ComposeAppTheme.colors.transparent,
                inactiveTickColor = ComposeAppTheme.colors.transparent,
                activeTrackColor = ComposeAppTheme.colors.steel20,
                inactiveTrackColor = ComposeAppTheme.colors.steel20,
                disabledActiveTickColor = ComposeAppTheme.colors.transparent,
                disabledInactiveTrackColor = ComposeAppTheme.colors.steel20
            )
        )
        Image(
            modifier = Modifier.clickable {
                selectedValue++
                onValueChange(selectedValue.toInt())
                onValueChangeFinished()
            },
            painter = painterResource(id = R.drawable.ic_plus_20),
            contentDescription = ""
        )
    }
}

@Composable
fun FeeInfoCell(title: String, value: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painter = painterResource(id = R.drawable.ic_info_20), contentDescription = "")
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = ComposeAppTheme.typography.subhead2,
            color = ComposeAppTheme.colors.grey
        )
        Text(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            text = value ?: "",
            style = ComposeAppTheme.typography.subhead1,
            color = ComposeAppTheme.colors.leah,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
