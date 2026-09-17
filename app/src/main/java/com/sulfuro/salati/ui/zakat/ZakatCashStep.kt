package com.sulfuro.salati.ui.zakat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.sulfuro.salati.R
import com.sulfuro.salati.theme.SalatiSpacing

@Composable
internal fun ZakatCashStep(
    currencySymbol: String,
    currencyCode: String,
    cashOnHand: Double,
    bankBalance: Double,
    investments: Double,
    receivables: Double,
    businessInventory: Double,
    liabilities: Double,
    subtotalText: String,
    onCashOnHandChange: (Double) -> Unit,
    onBankBalanceChange: (Double) -> Unit,
    onInvestmentsChange: (Double) -> Unit,
    onReceivablesChange: (Double) -> Unit,
    onBusinessInventoryChange: (Double) -> Unit,
    onLiabilitiesChange: (Double) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(SalatiSpacing.sm)) {
        ZakatSectionHeading(
            title = stringResource(R.string.zakat_section_own),
            trailing = currencyCode
        )

        ZakatCard {
            MoneyRow(
                label = stringResource(R.string.zakat_cash_on_hand),
                initialValue = cashOnHand,
                currencySymbol = currencySymbol,
                onValueChange = onCashOnHandChange
            )
            ZakatRowDivider()
            MoneyRow(
                label = stringResource(R.string.zakat_bank_balance),
                initialValue = bankBalance,
                currencySymbol = currencySymbol,
                onValueChange = onBankBalanceChange
            )
            ZakatRowDivider()
            MoneyRow(
                label = stringResource(R.string.zakat_investments),
                initialValue = investments,
                currencySymbol = currencySymbol,
                onValueChange = onInvestmentsChange
            )
            ZakatRowDivider()
            MoneyRow(
                label = stringResource(R.string.zakat_receivables),
                initialValue = receivables,
                currencySymbol = currencySymbol,
                onValueChange = onReceivablesChange
            )
            ZakatRowDivider()
            // Trade goods belong with the cash they were bought with, and all four schools
            // agree they are zakatable - at cost, which is the number a shopkeeper actually
            // knows, rather than the margin they hope for.
            MoneyRow(
                label = stringResource(R.string.zakat_business_inventory),
                initialValue = businessInventory,
                currencySymbol = currencySymbol,
                supportingText = stringResource(R.string.zakat_business_inventory_helper),
                onValueChange = onBusinessInventoryChange
            )
            ZakatRowDivider()
            ZakatTotalRow(
                label = stringResource(R.string.zakat_cash_total),
                value = subtotalText
            )
        }

        Spacer(modifier = Modifier.height(SalatiSpacing.xxs))
        ZakatSectionHeading(title = stringResource(R.string.zakat_section_owe))

        ZakatCard {
            MoneyRow(
                label = stringResource(R.string.zakat_liabilities),
                initialValue = liabilities,
                currencySymbol = currencySymbol,
                supportingText = stringResource(R.string.zakat_liabilities_helper),
                onValueChange = onLiabilitiesChange,
                imeAction = ImeAction.Done
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Step 3 - multi-carat precious metals
// ---------------------------------------------------------------------------
