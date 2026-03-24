package com.example.smartupiannotation.ui

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.example.smartupiannotation.R

class OverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null

    fun showOverlay(amount: Double, receiver: String, onDismiss: () -> Unit) {
        if (overlayView != null) return

        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.layout_floating_overlay, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = 100 // Offset from top
        }

        overlayView?.let { view ->
            // Initialize UI components
            val tvInfo = view.findViewById<TextView>(R.id.tvTransactionInfo)
            val btnClose = view.findViewById<Button>(R.id.btnCloseOverlay)
            val btnAnnotate = view.findViewById<Button>(R.id.btnAnnotate)

            tvInfo.text = "Paid ₹$amount to $receiver"

            btnClose.setOnClickListener {
                dismissOverlay()
                onDismiss()
            }

            btnAnnotate.setOnClickListener {
                // Here you would typically open an Activity to add notes/participants
                // For now, just dismiss
                dismissOverlay()
                onDismiss()
            }

            windowManager.addView(view, params)
        }
    }

    fun dismissOverlay() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }
}
