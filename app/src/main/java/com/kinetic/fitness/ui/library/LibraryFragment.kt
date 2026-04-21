// ui/library/LibraryFragment.kt
package com.kinetic.fitness.ui.library

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.kinetic.fitness.R
import com.kinetic.fitness.data.api.RetrofitClient
import com.kinetic.fitness.data.models.Exercise
import com.kinetic.fitness.databinding.FragmentLibraryBinding
import com.kinetic.fitness.databinding.ItemExerciseCardBinding
import kotlinx.coroutines.*

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!
    private val vm: LibraryViewModel by viewModels()
    private lateinit var adapter: ExerciseCardAdapter

    private var selectedMuscle: String? = null
    private var selectedEquip: String? = null
    private var selectedLevel: String? = null
    private var searchJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm.initApi(requireContext())

        adapter = ExerciseCardAdapter { exercise ->
            // TODO: navigate to ExerciseDetailFragment
            Toast.makeText(context, exercise.name, Toast.LENGTH_SHORT).show()
        }

        val layoutManager = GridLayoutManager(context, 2)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (position == 0) 2 else 1
            }
        }
        binding.rvExercises.layoutManager = layoutManager
        binding.rvExercises.adapter = adapter

        setupSearchBar()
        setupFilterChips()
        observeViewModel()
        vm.loadExercises()
    }

    private fun setupSearchBar() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(400) // debounce 400ms
                    vm.loadExercises(
                        query = s?.toString()?.trim()?.takeIf { it.isNotEmpty() },
                        muscle = selectedMuscle,
                        equip = selectedEquip,
                        level = selectedLevel
                    )
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupFilterChips() {

        val muscles = mapOf(
            "Toàn thân" to null, "Ngực" to "chest", "Lưng" to "back",
            "Chân" to "legs", "Vai" to "shoulders", "Tay" to "arms"
        )
        muscles.forEach { (label, value) ->
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = true
                isChecked = value == null
                setChipBackgroundColorResource(R.color.chip_selector)
                setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.chip_text_selector))
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        selectedMuscle = value
                        reloadWithFilters()
                    }
                }
            }
            binding.chipGroupMuscle.addView(chip)
        }


        val equips = mapOf("Tạ đơn" to "dumbbell", "Tạ đòn" to "barbell", "Máy" to "machine", "Tự trọng" to "bodyweight")
        equips.forEach { (label, value) ->
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = true
                setChipBackgroundColorResource(R.color.chip_selector)
                setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.chip_text_selector))
                setOnCheckedChangeListener { _, checked ->
                    selectedEquip = if (checked) value else null
                    reloadWithFilters()
                }
            }
            binding.chipGroupEquip.addView(chip)
        }


        val levels = mapOf("Cơ bản" to "beginner", "Trung cấp" to "intermediate", "Nâng cao" to "advanced")
        levels.forEach { (label, value) ->
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = true
                setChipBackgroundColorResource(R.color.chip_selector)
                setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.chip_text_selector))
                setOnCheckedChangeListener { _, checked ->
                    selectedLevel = if (checked) value else null
                    reloadWithFilters()
                }
            }
            binding.chipGroupLevel.addView(chip)
        }
    }

    private fun reloadWithFilters() {
        vm.loadExercises(
            query = binding.etSearch.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            muscle = selectedMuscle,
            equip = selectedEquip,
            level = selectedLevel
        )
    }

    private fun observeViewModel() {
        vm.loading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }
        vm.exercises.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.tvNoResults.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
        vm.error.observe(viewLifecycleOwner) {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class LibraryViewModel : ViewModel() {
    private val _loading = androidx.lifecycle.MutableLiveData(false)
    val loading: androidx.lifecycle.LiveData<Boolean> = _loading

    private val _exercises = androidx.lifecycle.MutableLiveData<List<Exercise>>(emptyList())
    val exercises: androidx.lifecycle.LiveData<List<Exercise>> = _exercises

    private val _error = androidx.lifecycle.MutableLiveData<String>()
    val error: androidx.lifecycle.LiveData<String> = _error

    private var api: com.kinetic.fitness.data.api.ApiService? = null

    fun initApi(context: android.content.Context) {
        if (api == null) api = RetrofitClient.getInstance(context)
    }

    fun loadExercises(
        query: String? = null,
        muscle: String? = null,
        equip: String? = null,
        level: String? = null
    ) = viewModelScope.launch {
        _loading.value = true
        try {
            val resp = api!!.getExercises(muscle, equip, level, query)
            if (resp.isSuccessful && resp.body()?.success == true) {
                _exercises.value = resp.body()!!.data ?: emptyList()
            } else {
                _error.value = "Không tải được danh sách bài tập"
            }
        } catch (e: Exception) {
            _error.value = "Lỗi kết nối: ${e.message}"
        } finally { _loading.value = false }
    }
}

class ExerciseCardAdapter(
    private val onClick: (Exercise) -> Unit
) : ListAdapter<Exercise, ExerciseCardAdapter.VH>(object : DiffUtil.ItemCallback<Exercise>() {
    override fun areItemsTheSame(a: Exercise, b: Exercise) = a.id == b.id
    override fun areContentsTheSame(a: Exercise, b: Exercise) = a == b
}) {
    inner class VH(val binding: ItemExerciseCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemExerciseCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ex = getItem(position)
        holder.binding.apply {
            tvName.text = ex.name
            tvMuscle.text = "${ex.muscleGroupVi()} • ${ex.difficultyVi()}"
            tvDifficulty.text = ex.difficultyVi()

            // Color badge by difficulty
            val badgeColor = when (ex.difficulty) {
                "beginner" -> R.color.tertiary_20
                "intermediate" -> R.color.secondary_20
                "advanced" -> R.color.primary_20
                else -> R.color.surface_container_high
            }
            tvDifficulty.setBackgroundResource(badgeColor)

            if (!ex.imageUrl.isNullOrEmpty()) {
                Glide.with(ivExercise).load(ex.imageUrl).centerCrop().into(ivExercise)
            } else {
                ivExercise.setImageResource(R.drawable.ic_fitness_center)
            }

            root.setOnClickListener { onClick(ex) }

            btnAdd.setOnClickListener {

                onClick(ex)
            }
        }
    }
}
