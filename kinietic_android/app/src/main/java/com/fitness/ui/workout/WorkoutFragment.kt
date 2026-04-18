// ui/workout/WorkoutFragment.kt
package com.kinetic.fitness.ui.workout

import android.content.*
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.kinetic.fitness.data.api.RetrofitClient
import com.kinetic.fitness.data.models.*
import com.kinetic.fitness.databinding.FragmentWorkoutBinding
import com.kinetic.fitness.utils.RestTimerService
import kotlinx.coroutines.launch

class WorkoutFragment : Fragment() {

    private var _binding: FragmentWorkoutBinding? = null
    private val binding get() = _binding!!
    private val vm: WorkoutViewModel by viewModels()
    private lateinit var setsAdapter: WorkoutSetsAdapter

    private val timerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                RestTimerService.BROADCAST_TICK -> {
                    val remaining = intent.getIntExtra(RestTimerService.EXTRA_REMAINING, 0)
                    updateTimerUI(remaining * 1000L, vm.timerTotalMs)
                }
                RestTimerService.BROADCAST_DONE -> {
                    updateTimerUI(0L, vm.timerTotalMs)
                    binding.btnTimer.text = "Bắt đầu"
                    vm.onTimerStopped()
                    Toast.makeText(context, "⏱ Đã hết giờ nghỉ! Tiếp tục tập!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWorkoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm.initApi(requireContext())

        setsAdapter = WorkoutSetsAdapter(
            onCompleteSet = { index, weight, reps -> 
                if (index != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                    vm.completeSet(index, weight, reps)
                }
            },
            onWeightChange = { index, weight -> 
                if (index != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                    vm.updateSetWeight(index, weight)
                }
            },
            onRepsChange = { index, reps -> 
                if (index != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                    vm.updateSetReps(index, reps)
                }
            }
        )
        binding.rvSets.layoutManager = LinearLayoutManager(context)
        binding.rvSets.adapter = setsAdapter

        setupTimerUI()
        observeViewModel()

        binding.btnAddSet.setOnClickListener { vm.addSetToCurrentExercise() }
        binding.btnAddExercise.setOnClickListener {
            val picker = ExercisePickerBottomSheet { exercise ->
                vm.addExercise(exercise)
            }
            picker.show(parentFragmentManager, "exercise_picker")
        }
        binding.btnFinishWorkout.setOnClickListener { vm.finishWorkout() }
        binding.btnTimerMinus.setOnClickListener { adjustTimer(-30) }
        binding.btnTimerPlus.setOnClickListener { adjustTimer(30) }

        binding.tvWorkoutName.setOnClickListener { showRenameDialog() }
    }

    private fun showRenameDialog() {
        val input = EditText(requireContext())
        input.setText(vm.sessionName.value)
        AlertDialog.Builder(requireContext())
            .setTitle("Tên buổi tập")
            .setView(input)
            .setPositiveButton("Đổi tên") { _, _ ->
                vm.renameSession(input.text.toString())
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(RestTimerService.BROADCAST_TICK)
            addAction(RestTimerService.BROADCAST_DONE)
        }
        
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Context.RECEIVER_NOT_EXPORTED
        } else 0
        requireContext().registerReceiver(timerReceiver, filter, flags)
        
        syncTimerUIWithViewModel()
        if (!vm.hasActiveSession()) vm.startSession()
    }

    override fun onPause() {
        super.onPause()
        try {
            requireContext().unregisterReceiver(timerReceiver)
        } catch (e: Exception) {}
    }

    private fun setupTimerUI() {
        updateTimerUI(vm.timerTotalMs, vm.timerTotalMs)
        binding.btnTimer.setOnClickListener {
            if (vm.timerRunning) stopTimer() else startTimer(vm.timerTotalMs)
        }
    }

    private fun syncTimerUIWithViewModel() {
        binding.btnTimer.text = if (vm.timerRunning) "Dừng" else "Bắt đầu"
        if (!vm.timerRunning) {
            updateTimerUI(vm.timerTotalMs, vm.timerTotalMs)
        }
    }

    private fun startTimer(durationMs: Long) {
        vm.onTimerStarted()
        binding.btnTimer.text = "Dừng"
        val intent = Intent(requireContext(), RestTimerService::class.java).apply {
            action = RestTimerService.ACTION_START
            putExtra(RestTimerService.EXTRA_SECONDS, (durationMs / 1000).toInt())
        }
        requireContext().startService(intent)
    }

    private fun stopTimer() {
        vm.onTimerStopped()
        binding.btnTimer.text = "Bắt đầu"
        requireContext().startService(
            Intent(requireContext(), RestTimerService::class.java).apply {
                action = RestTimerService.ACTION_STOP
            }
        )
        updateTimerUI(vm.timerTotalMs, vm.timerTotalMs)
    }

    private fun adjustTimer(seconds: Int) {
        vm.adjustTimerTotal(seconds)
        if (vm.timerRunning) {
            startTimer(vm.timerTotalMs) 
        } else {
            updateTimerUI(vm.timerTotalMs, vm.timerTotalMs)
        }
    }

    private fun updateTimerUI(remainingMs: Long, totalMs: Long) {
        val secs = (remainingMs / 1000).toInt()
        val m = secs / 60
        val s = secs % 60
        binding.tvTimer.text = String.format("%02d:%02d", m, s)
        val progress = if (totalMs > 0) ((remainingMs.toFloat() / totalMs) * 100).toInt() else 0
        binding.progressTimer.progress = progress
    }

    private fun observeViewModel() {
        vm.sessionName.observe(viewLifecycleOwner) { binding.tvWorkoutName.text = it }
        vm.elapsedTime.observe(viewLifecycleOwner) { binding.tvElapsed.text = it }
        vm.sets.observe(viewLifecycleOwner) { 
            setsAdapter.submitList(it) 
        }
        vm.loading.observe(viewLifecycleOwner) { binding.btnFinishWorkout.isEnabled = !it }
        vm.isPR.observe(viewLifecycleOwner) { pr ->
            if (pr != null) {
                showPRDialog(pr)
                vm.clearPR()
            }
        }
        vm.workoutFinished.observe(viewLifecycleOwner) { finished ->
            if (finished) {
                Toast.makeText(context, "✅ Buổi tập đã lưu!", Toast.LENGTH_SHORT).show()
                requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(
                    com.kinetic.fitness.R.id.bottom_nav
                )?.selectedItemId = com.kinetic.fitness.R.id.nav_history
                vm.resetFinishedFlag()
            }
        }
        vm.error.observe(viewLifecycleOwner) {
            if (!it.isNullOrBlank()) {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPRDialog(exerciseName: String) {
        val dialog = PRCelebrationDialog.newInstance(exerciseName)
        dialog.show(parentFragmentManager, "pr_dialog")
        if (!vm.timerRunning) startTimer(120_000L)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class WorkoutViewModel : ViewModel() {

    private val _sessionName = androidx.lifecycle.MutableLiveData("Buổi tập mới")
    val sessionName: androidx.lifecycle.LiveData<String> = _sessionName

    private val _elapsedTime = androidx.lifecycle.MutableLiveData("00:00")
    val elapsedTime: androidx.lifecycle.LiveData<String> = _elapsedTime

    private val _sets = androidx.lifecycle.MutableLiveData<List<WorkoutSetUi>>(emptyList())
    val sets: androidx.lifecycle.LiveData<List<WorkoutSetUi>> = _sets

    private val _loading = androidx.lifecycle.MutableLiveData(false)
    val loading: androidx.lifecycle.LiveData<Boolean> = _loading

    private val _isPR = androidx.lifecycle.MutableLiveData<String?>(null)
    val isPR: androidx.lifecycle.LiveData<String?> = _isPR

    private val _workoutFinished = androidx.lifecycle.MutableLiveData(false)
    val workoutFinished: androidx.lifecycle.LiveData<Boolean> = _workoutFinished

    private val _error = androidx.lifecycle.MutableLiveData<String>()
    val error: androidx.lifecycle.LiveData<String> = _error

    private var api: com.kinetic.fitness.data.api.ApiService? = null
    private var sessionId: Int = -1
    private var startTimeMs = System.currentTimeMillis()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    var timerTotalMs: Long = 120_000L
        private set

    var timerRunning: Boolean = false
        private set

    fun initApi(context: android.content.Context) {
        if (api == null) api = RetrofitClient.getInstance(context)
    }

    fun hasActiveSession() = sessionId != -1

    fun onTimerStarted() { timerRunning = true }
    fun onTimerStopped() { timerRunning = false }

    fun adjustTimerTotal(seconds: Int) {
        timerTotalMs = (timerTotalMs + seconds * 1000L).coerceIn(10_000L, 600_000L)
    }

    fun startSession() = viewModelScope.launch {
        try {
            val name = _sessionName.value ?: "Buổi tập"
            val resp = api!!.startSession(StartSessionRequest(name))
            if (resp.isSuccessful && resp.body()?.success == true) {
                sessionId = resp.body()?.data?.id ?: -1
                _sets.value = emptyList()
                _workoutFinished.value = false
                startElapsedTimer()
            }
        } catch (e: Exception) {
            _error.value = "Không thể bắt đầu buổi tập: ${e.message}"
        }
    }

    private fun startElapsedTimer() {
        startTimeMs = System.currentTimeMillis()
        handler.removeCallbacksAndMessages(null)
        handler.post(object : Runnable {
            override fun run() {
                val elapsed = (System.currentTimeMillis() - startTimeMs) / 1000
                val m = elapsed / 60
                val s = elapsed % 60
                _elapsedTime.value = String.format("%02d:%02d", m, s)
                handler.postDelayed(this, 1000)
            }
        })
    }

    fun renameSession(newName: String) {
        _sessionName.value = newName
    }

    fun resetFinishedFlag() {
        _workoutFinished.value = false
    }

    fun clearPR() {
        _isPR.value = null
    }

    fun addExercise(ex: Exercise) {
        if (sessionId == -1) return
        val current = _sets.value.orEmpty().toMutableList()
        val occurrence = (current.filter { it.exerciseId == ex.id }.map { it.occurrenceIndex }.maxOrNull() ?: -1) + 1
        val newSet = WorkoutSetUi(sessionId, ex.id, ex.name, ex.muscleGroupVi(), 1, 0f, 0, occurrenceIndex = occurrence)
        current.add(newSet)
        _sets.value = current.toList()
        saveDraftSet(newSet)
    }

    fun addSetToCurrentExercise() {
        if (sessionId == -1) return
        val current = _sets.value.orEmpty().toMutableList()
        val lastExercise = current.lastOrNull() ?: return
        val setNum = current.count { it.exerciseId == lastExercise.exerciseId && it.occurrenceIndex == lastExercise.occurrenceIndex } + 1
        val newSet = WorkoutSetUi(sessionId, lastExercise.exerciseId,
            lastExercise.exerciseName, lastExercise.muscleLabel, setNum, 0f, 0, occurrenceIndex = lastExercise.occurrenceIndex)
        current.add(newSet)
        _sets.value = current.toList()
        saveDraftSet(newSet)
    }

    private fun saveDraftSet(set: WorkoutSetUi) = viewModelScope.launch {
        try {
            api?.addSet(AddSetRequest(sessionId, set.exerciseId, set.setNumber, set.weightKg, set.reps))
        } catch (e: Exception) {}
    }

    fun removeSet(index: Int) {
        val current = _sets.value.orEmpty().toMutableList()
        if (index in current.indices) {
            val removed = current.removeAt(index)
            var nextSetNum = removed.setNumber
            for (i in index until current.size) {
                if (current[i].exerciseId == removed.exerciseId && current[i].occurrenceIndex == removed.occurrenceIndex) {
                    current[i] = current[i].copy(setNumber = nextSetNum++)
                } else if (current[i].exerciseId != removed.exerciseId || current[i].occurrenceIndex != removed.occurrenceIndex) {
                    break
                }
            }
            _sets.value = current.toList()
        }
    }

    fun updateSetWeight(index: Int, weight: Float) {
        val current = _sets.value.orEmpty().toMutableList()
        current.getOrNull(index)?.let {
            current[index] = it.copy(weightKg = weight)
            _sets.value = current.toList()
        }
    }

    fun updateSetReps(index: Int, reps: Int) {
        val current = _sets.value.orEmpty().toMutableList()
        current.getOrNull(index)?.let {
            current[index] = it.copy(reps = reps)
            _sets.value = current.toList()
        }
    }

    fun completeSet(index: Int, weight: Float, reps: Int) = viewModelScope.launch {
        val setList = _sets.value.orEmpty().toMutableList()
        val set = setList.getOrNull(index) ?: return@launch

        try {
            val addResp = api?.addSet(AddSetRequest(sessionId, set.exerciseId, set.setNumber, weight, reps))
            if (addResp?.isSuccessful == true && addResp.body()?.success == true) {
                
                // FIX AN TOÀN: Lấy setId trực tiếp từ thuộc tính class kết quả
                val setId = addResp.body()?.data?.setId ?: 0

                val completeResp = api?.completeSet(
                    CompleteSetRequest(setId, set.exerciseId, weight, reps)
                )
                
                if (completeResp?.isSuccessful == true && completeResp.body()?.success == true) {
                    if (index < setList.size) {
                        setList[index] = setList[index].copy(isCompleted = true, weightKg = weight, reps = reps)
                        _sets.value = setList.toList()

                        val isPR = completeResp.body()?.data?.isPR ?: false
                        if (isPR) _isPR.value = set.exerciseName
                    }
                }
            }
        } catch (e: Exception) {
            _error.value = "Lỗi khi lưu hiệp: ${e.message}"
        }
    }

    fun finishWorkout() = viewModelScope.launch {
        _loading.value = true
        val elapsed = ((System.currentTimeMillis() - startTimeMs) / 1000).toInt()
        
        val completedSets = _sets.value.orEmpty().count { it.isCompleted }
        val kcal = (elapsed / 60.0 * 4.0 + completedSets * 5.0).toInt().coerceAtLeast(1)
        
        try {
            val resp = api?.finishSession(FinishSessionRequest(sessionId, elapsed, kcal))
            if (resp?.isSuccessful == true && resp.body()?.success == true) {
                _workoutFinished.value = true
                sessionId = -1
                handler.removeCallbacksAndMessages(null)
            } else {
                _error.value = resp?.body()?.message ?: "Lỗi lưu buổi tập"
            }
        } catch (e: Exception) {
            _error.value = "Lỗi kết nối: ${e.message}"
        } finally { _loading.value = false }
    }

    override fun onCleared() {
        super.onCleared()
        handler.removeCallbacksAndMessages(null)
    }
}
