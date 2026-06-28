package com.maliar.pro.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountingDao {
    
    // Income
    @Query("SELECT * FROM incomes ORDER BY date DESC")
    fun getAllIncomes(): Flow<List<Income>>
    
    @Query("SELECT * FROM incomes ORDER BY date DESC")
    suspend fun getAllIncomesList(): List<Income>
    
    @Query("SELECT SUM(amount) FROM incomes")
    suspend fun getTotalIncome(): Double?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncome(income: Income): Long
    
    @Update
    suspend fun updateIncome(income: Income)
    
    @Delete
    suspend fun deleteIncome(income: Income)
    
    @Query("DELETE FROM incomes WHERE id = :id")
    suspend fun deleteIncomeById(id: Long)
    
    // Expense
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<Expense>>
    
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    suspend fun getAllExpensesList(): List<Expense>
    
    @Query("SELECT SUM(amount) FROM expenses")
    suspend fun getTotalExpense(): Double?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long
    
    @Update
    suspend fun updateExpense(expense: Expense)
    
    @Delete
    suspend fun deleteExpense(expense: Expense)
    
    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: Long)
    
    // Check
    @Query("SELECT * FROM checks ORDER BY dueDate ASC")
    fun getAllChecks(): Flow<List<Check>>
    
    @Query("SELECT * FROM checks ORDER BY dueDate ASC")
    suspend fun getAllChecksList(): List<Check>
    
    @Query("SELECT * FROM checks WHERE isCashed = 0")
    suspend fun getUncashedChecks(): List<Check>
    
    @Query("SELECT * FROM checks WHERE dueDate <= :timestamp AND isCashed = 0")
    suspend fun getDueChecks(timestamp: Long): List<Check>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheck(check: Check): Long
    
    @Update
    suspend fun updateCheck(check: Check)
    
    @Delete
    suspend fun deleteCheck(check: Check)
    
    @Query("DELETE FROM checks WHERE id = :id")
    suspend fun deleteCheckById(id: Long)
    
    // Installment
    @Query("SELECT * FROM installments ORDER BY startDate DESC")
    fun getAllInstallments(): Flow<List<Installment>>
    
    @Query("SELECT * FROM installments ORDER BY startDate DESC")
    suspend fun getAllInstallmentsList(): List<Installment>
    
    @Query("SELECT * FROM installments WHERE paidInstallments < totalInstallments")
    suspend fun getActiveInstallments(): List<Installment>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstallment(installment: Installment): Long
    
    @Update
    suspend fun updateInstallment(installment: Installment)
    
    @Delete
    suspend fun deleteInstallment(installment: Installment)
    
    @Query("DELETE FROM installments WHERE id = :id")
    suspend fun deleteInstallmentById(id: Long)
}
