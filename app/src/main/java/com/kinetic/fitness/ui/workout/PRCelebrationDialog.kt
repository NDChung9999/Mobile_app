// ui/workout/PRCelebrationDialog.kt
package com.kinetic.fitness.ui.workout

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.*
import androidx.fragment.app.DialogFragment
import com.kinetic.fitness.databinding.DialogPrCelebrationBinding

class PRCelebrationDialog : DialogFragment() {

    private var _binding: DialogPrCelebrationBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_EXERCISE_NAME = "exercise_name"

        fun newInstance(exerciseName: String): PRCelebrationDialog {
            val args = Bundle().apply {
                putString(ARG_EXERCISE_NAME, exerciseName)
            }
            val fragment = PRCelebrationDialog()
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogPrCelebrationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val exerciseName = arguments?.getString(ARG_EXERCISE_NAME) ?: ""

        binding.tvExerciseName.text = exerciseName
        binding.tvPrLabel.text = "🏆 KỶ LỤC CÁ NHÂN MỚI!"
        binding.tvSubLabel.text = "NEW PERSONAL RECORD"

        binding.btnContinue.setOnClickListener { dismiss() }
        binding.btnShare.setOnClickListener {
            shareAchievement(exerciseName)
            dismiss()
        }

        // Auto dismiss after 6 seconds
        view.postDelayed({ if (isAdded) dismiss() }, 6000)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.CENTER)
        }
    }

    private fun shareAchievement(exerciseName: String) {
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                android.content.Intent.EXTRA_TEXT,
                "🏋️ Tôi vừa phá kỷ lục cá nhân bài $exerciseName trên KINETIC! 💪 #KineticFitness"
            )
        }
        startActivity(android.content.Intent.createChooser(shareIntent, "Chia sẻ thành tích"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}