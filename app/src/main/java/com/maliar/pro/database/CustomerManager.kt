package com.maliar.pro.database

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class CustomerBalance(
    val customer: Customer,
    val sales: Double,
    val payments: Double,
    val adjustments: Double,
    val balance: Double,
    val lastTransactionAt: Long?
)

/**
 * Customer ledger that delegates money and stock side effects to existing managers.
 * A credit sale is represented by the existing Income/business-sale record, while a payment
 * only credits the selected cash/bank asset; revenue is never counted twice.
 */
class CustomerManager(context: Context) {
    private val appContext = context.applicationContext
    private val dao = AppDatabase.getDatabase(appContext).customerDao()
    private val accounting = AccountingManager(appContext)
    private val accounts = FinancialStatusManager(appContext)

    fun getAll(): Flow<List<Customer>> = dao.getAll()
    fun getCustomer(id: Long): Flow<Customer?> = dao.getByIdFlow(id)
    fun getEntries(customerId: Long): Flow<List<CustomerLedgerEntry>> = dao.getEntries(customerId)

    /** Lightweight per-customer reactive summary used by the detail screen. */
    fun getBalance(customerId: Long): Flow<CustomerBalance?> = combine(getCustomer(customerId), getEntries(customerId)) { customer, entries ->
        customer?.let { summarize(it, entries) }
    }

    suspend fun getBalancesList(): List<CustomerBalance> {
        val customers = getAll().first()
        return customers.map { customer -> summarize(customer, dao.getEntriesList(customer.id)) }
    }

    suspend fun add(customer: Customer): Long = dao.insert(customer)
    suspend fun update(customer: Customer) = dao.update(customer.copy(updatedAt = System.currentTimeMillis()))

    /** A customer may only be removed before ledger activity exists, preserving audit data. */
    suspend fun delete(customer: Customer): Boolean {
        if (dao.getEntriesList(customer.id).isNotEmpty()) return false
        dao.delete(customer)
        return true
    }

    suspend fun addCreditSale(customerId: Long, income: Income, note: String, dueDate: Long?): Long {
        require(dao.getById(customerId) != null) { "Customer not found" }
        require(income.amount > 0.0) { "Sale amount must be positive" }
        // Credit sales do not choose an account: no cash has arrived yet. AccountingManager
        // still performs its established cost-of-goods and inventory update for product sales.
        val incomeId = accounting.addIncome(income.copy(accountId = null))
        val entryId = dao.insertEntry(CustomerLedgerEntry(
            customerId = customerId,
            type = CustomerLedgerType.CREDIT_SALE,
            amount = income.amount,
            dueDate = dueDate,
            note = note,
            linkedIncomeId = incomeId
        ))
        touch(customerId)
        return entryId
    }

    suspend fun receivePayment(customerId: Long, amount: Double, accountId: Long?, note: String): Long {
        require(dao.getById(customerId) != null) { "Customer not found" }
        require(amount > 0.0) { "Payment amount must be positive" }
        val balance = summarize(dao.getById(customerId)!!, dao.getEntriesList(customerId)).balance
        require(amount <= balance) { "Payment cannot exceed customer balance" }
        val id = dao.insertEntry(CustomerLedgerEntry(customerId = customerId, type = CustomerLedgerType.PAYMENT, amount = amount, note = note, accountId = accountId))
        accounts.adjustAssetBalance(accountId, amount)
        touch(customerId)
        return id
    }

    suspend fun addAdjustment(customerId: Long, amount: Double, note: String): Long {
        require(dao.getById(customerId) != null) { "Customer not found" }
        require(amount != 0.0) { "Adjustment cannot be zero" }
        val id = dao.insertEntry(CustomerLedgerEntry(customerId = customerId, type = CustomerLedgerType.ADJUSTMENT, amount = amount, note = note))
        touch(customerId)
        return id
    }

    suspend fun deleteEntry(entry: CustomerLedgerEntry) {
        when (entry.type) {
            CustomerLedgerType.CREDIT_SALE -> entry.linkedIncomeId?.let { id ->
                accounting.getAllIncomesList().firstOrNull { it.id == id }?.let { accounting.deleteIncome(it) }
            }
            CustomerLedgerType.PAYMENT -> accounts.adjustAssetBalance(entry.accountId, -entry.amount)
            CustomerLedgerType.ADJUSTMENT -> Unit
        }
        dao.deleteEntry(entry)
        touch(entry.customerId)
    }

    private suspend fun touch(customerId: Long) {
        dao.getById(customerId)?.let { dao.update(it.copy(updatedAt = System.currentTimeMillis())) }
    }

    private fun summarize(customer: Customer, entries: List<CustomerLedgerEntry>): CustomerBalance {
        val sales = entries.filter { it.type == CustomerLedgerType.CREDIT_SALE }.sumOf { it.amount }
        val payments = entries.filter { it.type == CustomerLedgerType.PAYMENT }.sumOf { it.amount }
        val adjustments = entries.filter { it.type == CustomerLedgerType.ADJUSTMENT }.sumOf { it.amount }
        return CustomerBalance(customer, sales, payments, adjustments, sales + adjustments - payments, entries.maxOfOrNull { it.date })
    }
}
