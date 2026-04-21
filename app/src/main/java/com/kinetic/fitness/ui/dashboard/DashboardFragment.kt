// C:/Users/User/Downloads/kinetic_fitness_android/kinetic_android/app/src/main/java/com/kinetic/fitness/ui/dashboard/DashboardFragment.kt
package com.kinetic.fitness.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.data.*
import com.kinetic.fitness.data.api.RetrofitClient
import com.kinetic.fitness.data.models.*
import com.kinetic.fitness.databinding.FragmentDashboardBinding
import com.kinetic.fitness.ui.auth.AuthActivity
import com.kinetic.fitness.utils.SessionManager
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val vm: DashboardViewModel by viewModels()
    private lateinit var prAdapter: PersonalRecordAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm.initApi(requireContext())

        prAdapter = PersonalRecordAdapter()
        binding.rvPersonalRecords.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.rvPersonalRecords.adapter = prAdapter

        observeViewModel()
        vm.loadDashboard()


        binding.swipeRefresh.setOnRefreshListener {
            vm.loadDashboard()
        }

        binding.btnStartWorkout.setOnClickListener {
            requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(
                com.kinetic.fitness.R.id.bottom_nav
            )?.selectedItemId = com.kinetic.fitness.R.id.nav_workout
        }

        // Logout logic
        binding.btnLogout.setOnClickListener {
            vm.logout {
                val intent = Intent(requireActivity(), AuthActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                requireActivity().finish()
            }
        }
    }

    private fun observeViewModel() {
        vm.loading.observe(viewLifecycleOwner) { loading ->
            binding.swipeRefresh.isRefreshing = loading
        }
        vm.dashboard.observe(viewLifecycleOwner) { data ->
            bindDashboard(data)
        }
        vm.error.observe(viewLifecycleOwner) {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindDashboard(data: DashboardData) {
        val user = data.user
        val today = data.today

        binding.tvUserName.text = "Xin chào, ${user.name.split(" ").last()} 💪"

        val goal = user.dailyKcalGoal
        val burned = today.kcalBurned
        binding.tvKcalValue.text = "%,d".format(burned)
        binding.tvKcalGoal.text = "${(burned * 100 / goal.coerceAtLeast(1))}% mục tiêu ngày"
        binding.progressKcal.progress = (burned * 100 / goal.coerceAtLeast(1)).coerceIn(0, 100)

        binding.tvSteps.text = "%,d".format(today.steps)
        binding.tvActiveMinutes.text = "${today.activeMinutes}"

        setupVolumeChart(data.volumeTrend)
        prAdapter.submitList(data.personalRecords)
    }

    private fun setupVolumeChart(trend: List<VolumeTrend>) {
        val entries = trend.mapIndexed { i, v -> BarEntry(i.toFloat(), v.volume) }
        val set = BarDataSet(entries, "Khối lượng (kg)").apply {
            color = android.graphics.Color.parseColor("#8eff71")
            setDrawValues(false)
        }
        binding.barChart.apply {
            this.data = BarData(set)
            description.isEnabled = false
            legend.isEnabled = false
            axisRight.isEnabled = false
            axisLeft.textColor = android.graphics.Color.parseColor("#ababab")
            xAxis.isEnabled = false
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}



class DashboardViewModel : ViewModel() {
    private val _loading = androidx.lifecycle.MutableLiveData(false)
    val loading: androidx.lifecycle.LiveData<Boolean> = _loading

    private val _dashboard = androidx.lifecycle.MutableLiveData<DashboardData>()
    val dashboard: androidx.lifecycle.LiveData<DashboardData> = _dashboard

    private val _error = androidx.lifecycle.MutableLiveData<String>()
    val error: androidx.lifecycle.LiveData<String> = _error

    private var api: com.kinetic.fitness.data.api.ApiService? = null
    private var sessionManager: SessionManager? = null

    fun initApi(context: android.content.Context) {
        if (api == null) api = RetrofitClient.getInstance(context)
        if (sessionManager == null) sessionManager = SessionManager.getInstance(context)
    }

    fun loadDashboard() = viewModelScope.launch {
        _loading.value = true
        try {
            val resp = api!!.getDashboard()
            if (resp.isSuccessful && resp.body()?.success == true) {
                _dashboard.value = resp.body()!!.data!!
            } else {
                _error.value = resp.body()?.message ?: "Không tải được dữ liệu"
            }
        } catch (e: Exception) {
            _error.value = "Lỗi kết nối: ${e.message}"
        } finally { _loading.value = false }
    }

    fun logout(onSuccess: () -> Unit) = viewModelScope.launch {
        try {
            api?.logout()
        } catch (e: Exception) {}
        sessionManager?.clearSession()
        onSuccess()
    }
}


class PersonalRecordAdapter :
    androidx.recyclerview.widget.ListAdapter<PersonalRecord,
            PersonalRecordAdapter.VH>(object : androidx.recyclerview.widget.DiffUtil.ItemCallback<PersonalRecord>() {
        override fun areItemsTheSame(a: PersonalRecord, b: PersonalRecord) = a.id == b.id
        override fun areContentsTheSame(a: PersonalRecord, b: PersonalRecord) = a == b
    }) {

    inner class VH(val binding: com.kinetic.fitness.databinding.ItemPersonalRecordBinding) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        com.kinetic.fitness.databinding.ItemPersonalRecordBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val pr = getItem(position)
        holder.binding.apply {
            tvExerciseName.text = pr.exerciseName
            tvWeight.text = pr.weightKg.toString()
            tvReps.text = "× ${pr.reps} lần"
            tvDate.text = pr.achievedAt.substring(0, 10)
        }
    }
}
