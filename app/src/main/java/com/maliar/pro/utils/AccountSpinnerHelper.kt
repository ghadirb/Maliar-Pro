package com.maliar.pro.utils

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Spinner
import com.maliar.pro.database.Asset
import com.maliar.pro.database.FinancialStatusManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Shared "which account should this affect?" picker used by every dialog that can link
 * a transaction to an account (expense, income, installment, debt, debtor payment).
 * Populates a plain [Spinner] asynchronously (Room's suspend queries are main-safe as of
 * 2.6.1, so a bare Dispatchers.Main scope is enough - no IO dispatcher needed) with
 * "بدون حساب مشخص" always first and selected by default, so every call site that doesn't
 * explicitly pre-select anything keeps behaving exactly as it did before this picker
 * existed (an unlinked transaction).
 */
object AccountSpinnerHelper {

    /** Loads "حساب‌های من" into [spinner]. If [preselectAccountId] matches a loaded
     *  account, that account is selected instead of the "بدون حساب مشخص" default -
     *  used when editing something already linked to an account. [onLoaded] receives the
     *  loaded account list so the caller can resolve the final selection back to an id
     *  at save time via [selectedAccountId]. */
    fun populate(
        context: Context,
        spinner: Spinner,
        preselectAccountId: Long? = null,
        onLoaded: (accounts: List<Asset>) -> Unit = {}
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            val accounts = try {
                FinancialStatusManager(context).getAllAssetsList()
            } catch (e: Exception) {
                emptyList()
            }
            spinner.adapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("بدون حساب مشخص") + accounts.map { it.title }
            )
            val preselectIndex = preselectAccountId
                ?.let { id -> accounts.indexOfFirst { it.id == id } }
                ?.takeIf { it >= 0 }
            if (preselectIndex != null) spinner.setSelection(preselectIndex + 1)
            onLoaded(accounts)
        }
    }

    /** Resolves [spinner]'s current selection (from a list populated by [populate]) back
     *  to an accountId - null for "بدون حساب مشخص", for a selection made before loading
     *  finished, or for an out-of-range position. */
    fun selectedAccountId(spinner: Spinner, accounts: List<Asset>): Long? {
        val position = spinner.selectedItemPosition
        if (position <= 0) return null
        return accounts.getOrNull(position - 1)?.id
    }
}
