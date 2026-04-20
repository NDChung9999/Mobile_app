// ui/workout/WorkoutSetsAdapter.kt
package com.kinetic.fitness.ui.workout

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kinetic.fitness.R
import com.kinetic.fitness.databinding.ItemWorkoutSetBinding

class WorkoutSetsAdapter(
    private val onCompleteSet: (index: Int, weight: Float, reps: Int) -> Unit,
    private val onWeightChange: (index: Int, weight: Float) -> Unit,
    private val onRepsChange: (index: Int, reps: Int) -> Unit
) : ListAdapter<WorkoutSetUi, WorkoutSetsAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<WorkoutSetUi>() {
            override fun areItemsTheSame(a: WorkoutSetUi, b: WorkoutSetUi) =
                a.stableId == b.stableId

            override fun areContentsTheSame(a: WorkoutSetUi, b: WorkoutSetUi) = a == b
        }
    }

    inner class VH(val binding: ItemWorkoutSetBinding) : RecyclerView.ViewHolder(binding.root) {
        var weightWatcher: TextWatcher? = null
        var repsWatcher: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemWorkoutSetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val set = getItem(position)
        val ctx = holder.itemView.context

        val showHeader = position == 0 || getItem(position - 1).exerciseId != set.exerciseId || getItem(position - 1).occurrenceIndex != set.occurrenceIndex

        holder.binding.apply {
            layoutExerciseHeader.visibility = if (showHeader) android.view.View.VISIBLE else android.view.View.GONE
            if (showHeader) {
                tvExerciseHeader.text = set.exerciseName
                tvMuscleLabel.text = set.muscleLabel
            }

            tvSetNumber.text = set.setNumber.toString().padStart(2, '0')

            holder.weightWatcher?.let { etWeight.removeTextChangedListener(it) }
            holder.repsWatcher?.let { etReps.removeTextChangedListener(it) }

            etWeight.setText(if (set.weightKg > 0) set.weightKg.toString() else "")
            etReps.setText(if (set.reps > 0) set.reps.toString() else "")

            holder.weightWatcher = object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val w = s?.toString()?.toFloatOrNull() ?: 0f
                    if (w != set.weightKg) onWeightChange(holder.bindingAdapterPosition, w)
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            }.also { etWeight.addTextChangedListener(it) }

            holder.repsWatcher = object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val r = s?.toString()?.toIntOrNull() ?: 0
                    if (r != set.reps) onRepsChange(holder.bindingAdapterPosition, r)
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            }.also { etReps.addTextChangedListener(it) }

            if (set.isCompleted) {
                btnComplete.setImageResource(R.drawable.ic_check_filled)
                btnComplete.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.primary)
                rowContainer.setBackgroundColor(ContextCompat.getColor(ctx, R.color.surface_container_highest))
                etWeight.isEnabled = false
                etReps.isEnabled = false
            } else {
                btnComplete.setImageResource(R.drawable.ic_check)
                btnComplete.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.surface_container_high)
                rowContainer.setBackgroundColor(ContextCompat.getColor(ctx, R.color.surface_container_low))
                etWeight.isEnabled = true
                etReps.isEnabled = true
            }

            btnComplete.setOnClickListener {
                if (!set.isCompleted) {
                    val weight = etWeight.text.toString().toFloatOrNull() ?: 0f
                    val reps = etReps.text.toString().toIntOrNull() ?: 0
                    if (weight <= 0 || reps <= 0) {
                        Toast.makeText(ctx, "Vui lòng nhập kg và số lần", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    onCompleteSet(holder.bindingAdapterPosition, weight, reps)
                }
            }
        }
    }
}

data class WorkoutSetUi(
    val sessionId: Int,
    val exerciseId: Int,
    val exerciseName: String,
    val muscleLabel: String,
    val setNumber: Int,
    val weightKg: Float,
    val reps: Int,
    val isCompleted: Boolean = false,
    val occurrenceIndex: Int = 0
) {
    val stableId: String get() = "${exerciseId}_${occurrenceIndex}_${setNumber}"
}
