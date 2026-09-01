package com.maliar.pro.ui.car

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import com.maliar.pro.R
import com.maliar.pro.database.Car
import com.maliar.pro.database.CarManager
import com.maliar.pro.databinding.FragmentCarListBinding
import com.maliar.pro.dialogs.AddCarDialog
import com.maliar.pro.viewmodels.CarViewModel
import com.maliar.pro.viewmodels.CarViewModelFactory
import kotlinx.coroutines.launch

/** "خودروهای من": every registered car, tap to open its detail/service screen. Cards are
 *  built dynamically (not a RecyclerView) since the person will realistically have one to
 *  a handful of cars, not a scrolling list worth the adapter machinery. */
class CarListFragment : Fragment() {

    private lateinit var binding: FragmentCarListBinding
    private val viewModel: CarViewModel by viewModels {
        CarViewModelFactory(CarManager(requireContext()))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentCarListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.addCarButton.setOnClickListener {
            AddCarDialog(requireContext()) { car -> viewModel.addCar(car) }.show()
        }

        lifecycleScope.launch {
            viewModel.cars.collect { cars -> render(cars) }
        }
    }

    private fun render(cars: List<Car>) {
        binding.carsContainer.removeAllViews()
        binding.emptyStateText.visibility = if (cars.isEmpty()) View.VISIBLE else View.GONE

        cars.forEach { car ->
            val card = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_car_summary, binding.carsContainer, false) as MaterialCardView

            card.findViewById<android.widget.TextView>(R.id.carNameText).text = car.name
            val subtitle = listOfNotNull(
                car.brand.takeIf { it.isNotBlank() },
                car.model.takeIf { it.isNotBlank() },
                car.year?.toString()
            ).joinToString(" · ")
            card.findViewById<android.widget.TextView>(R.id.carSubtitleText).apply {
                text = subtitle
                visibility = if (subtitle.isBlank()) View.GONE else View.VISIBLE
            }
            card.findViewById<android.widget.TextView>(R.id.carOdometerText).text =
                String.format("%,d km", car.currentOdometerKm)

            card.setOnClickListener {
                val bundle = Bundle().apply { putLong("carId", car.id) }
                findNavController().navigate(R.id.action_carListFragment_to_carDetailFragment, bundle)
            }
            card.setOnLongClickListener {
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("حذف خودرو")
                    .setMessage("خودروی «${car.name}» و تمام سوابق آن حذف شود؟")
                    .setPositiveButton("حذف") { _, _ -> viewModel.deleteCar(car) }
                    .setNegativeButton("لغو", null)
                    .show()
                true
            }

            binding.carsContainer.addView(card)
        }
    }
}
