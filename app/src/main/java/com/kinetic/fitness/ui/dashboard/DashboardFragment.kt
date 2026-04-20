package com.kinetic.fitness.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import com.kinetic.fitness.databinding.FragmentDashboardBinding
import com.kinetic.fitness.data.api.RetrofitClient
import com.kinetic.fitness.data.models.*
import com.kinetic.fitness.ui.auth.AuthActivity
import com.kinetic.fitness.utils.SessionManager
import kotlinx.coroutines.*

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val loading = MutableLiveData(false)
    private val dashboard = MutableLiveData<DashboardData>()
    private val error = MutableLiveData<String>()

    private var api: com.kinetic.fitness.data.api.ApiService? = null
    private var sessionManager: SessionManager? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        api = RetrofitClient.getInstance(requireContext())
        sessionManager = SessionManager.getInstance(requireContext())

        observeData()
        loadDashboard()

        binding.swipeRefresh.setOnRefreshListener {
            loadDashboard()
        }

        binding.btnLogout.setOnClickListener {
            sessionManager?.clearSession()
            val intent = Intent(requireActivity(), AuthActivity::class.java)
            startActivity(intent)
            requireActivity().finish()
        }
    }

    private fun observeData() {
        loading.observe(viewLifecycleOwner) {
            binding.swipeRefresh.isRefreshing = it
        }

        dashboard.observe(viewLifecycleOwner) {
            bindDashboard(it)
        }

        error.observe(viewLifecycleOwner) {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadDashboard() {
        loading.value = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resp = api!!.getDashboard()

                withContext(Dispatchers.Main) {
                    if (resp.isSuccessful && resp.body()?.success == true) {
                        dashboard.value = resp.body()!!.data!!
                    } else {
                        error.value = "Không tải được dữ liệu"
                    }
                    loading.value = false
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error.value = "Lỗi: ${e.message}"
                    loading.value = false
                }
            }
        }
    }

    private fun bindDashboard(data: DashboardData) {
        val user = data.user
        val today = data.today

        binding.tvUserName.text = "Xin chào, ${user.name}"
        binding.tvKcalValue.text = today.kcalBurned.toString()
        binding.tvSteps.text = today.steps.toString()
        binding.tvActiveMinutes.text = today.activeMinutes.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}