// ui/history/HistoryFragment.kt
package com.kinetic.fitness.ui.history

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kinetic.fitness.data.api.RetrofitClient
import com.kinetic.fitness.data.models.WorkoutSession
import com.kinetic.fitness.databinding.FragmentHistoryBinding
import com.kinetic.fitness.databinding.ItemSessionCardBinding
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val vm: HistoryViewModel by viewModels()
    private lateinit var adapter: SessionAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm.initApi(requireContext())

        adapter = SessionAdapter(
            onRepeat = { session ->

                Toast.makeText(context, "Đang chuẩn bị dữ liệu lặp lại cho: ${session.name}", Toast.LENGTH_SHORT).show()
            },
            onDetail = { session ->

                Toast.makeText(context, "Mở chi tiết buổi tập: ${session.name}", Toast.LENGTH_SHORT).show()
            }
        )
        val layoutManager = LinearLayoutManager(context)
        binding.rvSessions.layoutManager = layoutManager
        binding.rvSessions.adapter = adapter


        binding.rvSessions.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) { // scroll down
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val pastVisibleItems = layoutManager.findFirstVisibleItemPosition()


                    if (vm.loading.value == false && (visibleItemCount + pastVisibleItems) >= totalItemCount - 2) {
                        vm.loadSessions(reset = false)
                    }
                }
            }
        })

        observeViewModel()
        vm.loadSessions()
        binding.swipeRefresh.setOnRefreshListener { vm.loadSessions() }
    }

    private fun observeViewModel() {
        vm.loading.observe(viewLifecycleOwner) { binding.swipeRefresh.isRefreshing = it }
        vm.sessions.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }


        vm.stats.observe(viewLifecycleOwner) { stats ->
            binding.tvTotalSessions.text = stats.totalSessions.toString()
            val totalMins = stats.totalDurationSeconds / 60
            binding.tvTotalTime.text = if (totalMins >= 60) "${totalMins / 60}h ${totalMins % 60}m" else "${totalMins}m"
            binding.tvTotalKcal.text = "%,d".format(stats.totalKcal)
        }

        vm.error.observe(viewLifecycleOwner) { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

data class WorkoutStats(
    val totalSessions: Int = 0,
    val totalDurationSeconds: Int = 0,
    val totalKcal: Int = 0
)

class HistoryViewModel : ViewModel() {
    private val _loading = androidx.lifecycle.MutableLiveData(false)
    val loading: androidx.lifecycle.LiveData<Boolean> = _loading

    private val _sessions = androidx.lifecycle.MutableLiveData<List<WorkoutSession>>(emptyList())
    val sessions: androidx.lifecycle.LiveData<List<WorkoutSession>> = _sessions

    private val _stats = androidx.lifecycle.MutableLiveData<WorkoutStats>()
    val stats: androidx.lifecycle.LiveData<WorkoutStats> = _stats

    private val _error = androidx.lifecycle.MutableLiveData<String>()
    val error: androidx.lifecycle.LiveData<String> = _error

    private var api: com.kinetic.fitness.data.api.ApiService? = null
    private var page = 1
    private var canLoadMore = true
    private var isRequestInFlight = false

    fun initApi(context: android.content.Context) {
        if (api == null) api = RetrofitClient.getInstance(context)
    }

    fun loadSessions(reset: Boolean = true) = viewModelScope.launch {
        if (isRequestInFlight) return@launch
        if (reset) {
            page = 1
            canLoadMore = true
        } else if (!canLoadMore) return@launch

        isRequestInFlight = true
        _loading.value = true
        try {
            val resp = api!!.getSessions(page = page, limit = 20)
            if (resp.isSuccessful && resp.body()?.success == true) {
                val newList = resp.body()!!.data ?: emptyList()
                if (newList.isEmpty()) {
                    canLoadMore = false
                } else {
                    val combined = if (reset) newList else (_sessions.value ?: emptyList()) + newList
                    _sessions.value = combined


                    _stats.value = WorkoutStats(
                        totalSessions = combined.size,
                        totalDurationSeconds = combined.sumOf { it.durationSeconds },
                        totalKcal = combined.sumOf { it.totalKcal }
                    )
                    page++
                }
            } else {
                _error.value = resp.body()?.message ?: "Không tải được lịch sử"
            }
        } catch (e: Exception) {
            _error.value = "Lỗi kết nối: ${e.message}"
        } finally {
            _loading.value = false
            isRequestInFlight = false
        }
    }
}

class SessionAdapter(
    private val onRepeat: (WorkoutSession) -> Unit,
    private val onDetail: (WorkoutSession) -> Unit
) : androidx.recyclerview.widget.ListAdapter<WorkoutSession, SessionAdapter.VH>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<WorkoutSession>() {
        override fun areItemsTheSame(a: WorkoutSession, b: WorkoutSession) = a.id == b.id
        override fun areContentsTheSame(a: WorkoutSession, b: WorkoutSession) = a == b
    }
) {

    companion object {
        private val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val displayFormatter = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", Locale("vi"))
    }

    inner class VH(val binding: ItemSessionCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemSessionCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val session = getItem(position)
        holder.binding.apply {
            tvSessionName.text = session.name
            tvDuration.text = session.durationFormatted()
            tvExerciseCount.text = "${session.exerciseCount} bài tập"
            tvKcal.text = "${session.totalKcal} kcal"


            try {
                val temporal = inputFormatter.parse(session.startedAt)
                tvDate.text = displayFormatter.format(temporal)
            } catch (e: Exception) {
                tvDate.text = session.startedAt.substring(0, 10)
            }

            btnRepeat.setOnClickListener { onRepeat(session) }
            root.setOnClickListener { onDetail(session) }
        }
    }
}
