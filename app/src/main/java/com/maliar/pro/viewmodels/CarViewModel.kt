package com.maliar.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.maliar.pro.database.Car
import com.maliar.pro.database.CarManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CarViewModel(private val carManager: CarManager) : ViewModel() {

    val cars = carManager.getAllCars()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCar(car: Car) {
        viewModelScope.launch { carManager.addCar(car) }
    }

    fun updateCar(car: Car) {
        viewModelScope.launch { carManager.updateCar(car) }
    }

    fun deleteCar(car: Car) {
        viewModelScope.launch { carManager.deleteCar(car) }
    }
}

class CarViewModelFactory(private val carManager: CarManager) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = CarViewModel(carManager) as T
}
