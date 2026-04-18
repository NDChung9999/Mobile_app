package com.kinetic.fitness.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.music_app.databinding.FragmentDashboardBinding

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        val kcal = 500
        val steps = 8000
        val activeMinutes = 45


        binding.tvKcalValue.text = "$kcal kcal"
        binding.tvSteps.text = "$steps steps"
        binding.tvActiveMinutes.text = "$activeMinutes minutes"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}