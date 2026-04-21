package com.kinetic.fitness.ui.workout

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.kinetic.fitness.data.models.Exercise
import com.kinetic.fitness.databinding.LayoutExercisePickerBottomSheetBinding
import com.kinetic.fitness.ui.library.ExerciseCardAdapter
import com.kinetic.fitness.ui.library.LibraryViewModel

class ExercisePickerBottomSheet(
    private val onExerciseSelected: (Exercise) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: LayoutExercisePickerBottomSheetBinding? = null
    private val binding get() = _binding!!
    private val vm: LibraryViewModel by viewModels()
    private lateinit var adapter: ExerciseCardAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = LayoutExercisePickerBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm.initApi(requireContext())

        adapter = ExerciseCardAdapter { exercise ->
            onExerciseSelected(exercise)
            dismiss()
        }

        binding.rvExercises.layoutManager = LinearLayoutManager(context)
        binding.rvExercises.adapter = adapter

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                vm.loadExercises(query = s?.toString()?.trim())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        vm.exercises.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
        }

        vm.loadExercises()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
