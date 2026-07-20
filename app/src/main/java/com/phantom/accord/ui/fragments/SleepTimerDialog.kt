package com.phantom.accord.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.phantom.accord.R
import com.phantom.accord.logic.setTimer
import com.phantom.accord.ui.MainActivity

class SleepTimerDialog : BottomSheetDialogFragment() {

    private var selectedOptionId = R.id.sleep_off

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_sleep_timer, container, false)
        
        val options = listOf(
            R.id.sleep_off to R.id.check_off,
            R.id.sleep_15 to R.id.check_15,
            R.id.sleep_30 to R.id.check_30,
            R.id.sleep_45 to R.id.check_45,
            R.id.sleep_60 to R.id.check_60,
            R.id.sleep_end to R.id.check_end
        )

        fun updateChecks(selected: Int) {
            options.forEach { (layoutId, checkId) ->
                view.findViewById<ImageView>(checkId).visibility = 
                    if (layoutId == selected) View.VISIBLE else View.INVISIBLE
            }
        }

        options.forEach { (layoutId, _) ->
            view.findViewById<View>(layoutId).setOnClickListener {
                selectedOptionId = layoutId
                updateChecks(layoutId)
                
                val player = (activity as? MainActivity)?.getPlayer()
                if (player != null) {
                    var timerMs = 0
                    var message = ""
                    when (layoutId) {
                        R.id.sleep_15 -> { timerMs = 15 * 60 * 1000; message = "Sleep timer set for 15 Minutes" }
                        R.id.sleep_30 -> { timerMs = 30 * 60 * 1000; message = "Sleep timer set for 30 Minutes" }
                        R.id.sleep_45 -> { timerMs = 45 * 60 * 1000; message = "Sleep timer set for 45 Minutes" }
                        R.id.sleep_60 -> { timerMs = 60 * 60 * 1000; message = "Sleep timer set for 1 Hour" }
                        R.id.sleep_end -> { 
                            timerMs = -1
                            val title = player.currentMediaItem?.mediaMetadata?.title?.toString() ?: "current song"
                            message = "End of $title"
                        }
                        R.id.sleep_off -> {
                            timerMs = 0
                            message = "Sleep timer ended"
                        }
                    }
                    
                    player.setTimer(timerMs)
                    
                    val root = activity?.findViewById<View>(android.R.id.content) ?: view
                    val snackbar = Snackbar.make(root, message, Snackbar.LENGTH_SHORT)
                    snackbar.view.setBackgroundColor(android.graphics.Color.parseColor("#000000"))
                    val tv = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                    tv.setTextColor(android.graphics.Color.WHITE)
                    
                    val checkDrawable = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.ic_check)?.mutate()
                    checkDrawable?.setTint(android.graphics.Color.WHITE)
                    tv.setCompoundDrawablesWithIntrinsicBounds(checkDrawable, null, null, null)
                    tv.compoundDrawablePadding = (16 * resources.displayMetrics.density).toInt()
                    snackbar.show()
                }
                
                dismiss()
            }
        }

        updateChecks(selectedOptionId)
        
        return view
    }
    
    companion object {
        const val TAG = "SleepTimerDialog"
    }
}
