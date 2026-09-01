package com.maliar.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.maliar.pro.database.MealPlanEntry
import com.maliar.pro.database.MealPlanManager
import com.maliar.pro.database.ShoppingList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

val PERSIAN_WEEKDAY_NAMES = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه")

/** Midnight of the Saturday that starts the (Gregorian-week-aligned, since Iran's week is
 *  already Sat-Fri regardless of calendar system) week containing [reference]. */
fun weekStartMillis(reference: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = reference
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    val daysSinceSaturday = cal.get(Calendar.DAY_OF_WEEK) % 7 // Calendar: SUNDAY=1..SATURDAY=7
    cal.add(Calendar.DAY_OF_YEAR, -daysSinceSaturday)
    return cal.timeInMillis
}

class MealPlanViewModel(private val mealPlanManager: MealPlanManager) : ViewModel() {

    val latestPlan = mealPlanManager.getLatestPlan()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _entries = MutableStateFlow<List<MealPlanEntry>>(emptyList())
    val entries: StateFlow<List<MealPlanEntry>> = _entries.asStateFlow()

    private val _shoppingList = MutableStateFlow<ShoppingList?>(null)
    val shoppingList: StateFlow<ShoppingList?> = _shoppingList.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private var entriesCollectorPlanId: Long? = null

    init {
        viewModelScope.launch {
            latestPlan.collect { plan ->
                if (plan != null) observeEntries(plan.id)
            }
        }
    }

    private fun observeEntries(planId: Long) {
        if (entriesCollectorPlanId == planId) return
        entriesCollectorPlanId = planId
        viewModelScope.launch {
            mealPlanManager.getEntries(planId).collect { list -> _entries.value = list }
        }
    }

    /** Generates (or regenerates) the plan for the week containing [weekStart], within
     *  [budget] (0 = no budget constraint). Also clears any previously shown shopping list
     *  since it would now be stale. */
    fun generatePlan(weekStart: Long, budget: Double) {
        _isGenerating.value = true
        viewModelScope.launch {
            mealPlanManager.generateWeeklyPlan(weekStart, budget)
            _shoppingList.value = null
            _isGenerating.value = false
        }
    }

    fun loadShoppingList(planId: Long) {
        viewModelScope.launch {
            _shoppingList.value = mealPlanManager.getShoppingList(planId)
        }
    }

    fun hideShoppingList() {
        _shoppingList.value = null
    }
}

class MealPlanViewModelFactory(private val mealPlanManager: MealPlanManager) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MealPlanViewModel(mealPlanManager) as T
}
