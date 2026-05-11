package com.example.smartupiannotation.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.Cursor
import android.graphics.PixelFormat
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.content.ContextCompat
import com.example.smartupiannotation.R
import com.example.smartupiannotation.data.local.ParticipantEntity
import com.example.smartupiannotation.databinding.ItemParticipantInputBinding
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.*

class OverlayManager(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var contactList = mutableListOf<String>()
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var isUpdatingSplit = false

    private var savedOnSaveLambda: ((Double, String, String, List<ParticipantEntity>) -> Unit)? = null
    private var savedOnDismissLambda: (() -> Unit)? = null

    fun isOverlayVisible(): Boolean = overlayView != null

    fun showOverlay(
        amount: Double,
        receiver: String,
        onSave: (Double, String, String, List<ParticipantEntity>) -> Unit,
        onDismiss: () -> Unit,
        existingNote: String = ""
    ) {
        if (overlayView != null) {
            Log.d("OverlayManager", "Overlay already visible, not showing again.")
            return
        }

        savedOnSaveLambda = onSave
        savedOnDismissLambda = onDismiss

        loadContacts()

        val currentConfig = Configuration(context.resources.configuration)
        val configContext = context.createConfigurationContext(currentConfig)
        val themedContext = ContextThemeWrapper(configContext, R.style.Theme_SmartUPIAnnotation)

        windowManager = themedContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val layoutInflater = LayoutInflater.from(themedContext)

        overlayView = layoutInflater.inflate(R.layout.layout_floating_overlay, null)

        // Modal LayoutParams: Captures focus and touches until dismissed via buttons
        // Removing FLAG_NOT_TOUCH_MODAL makes the window modal.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        )

        params.dimAmount = 0.6f
        params.gravity = Gravity.CENTER

        overlayView?.let { view ->
            val etAmount = view.findViewById<TextInputEditText>(R.id.etOverlayAmount)
            val etReceiver = view.findViewById<MaterialAutoCompleteTextView>(R.id.etOverlayReceiver)
            val etNote = view.findViewById<TextInputEditText>(R.id.etOverlayNote)
            val participantsContainer = view.findViewById<LinearLayout>(R.id.overlayParticipantsContainer)
            val btnAddParticipant = view.findViewById<Button>(R.id.btnOverlayAddParticipant)

            etAmount.setText(amount.toString())
            etReceiver.setText(receiver)
            etNote.setText(existingNote)
            setupAutoComplete(etReceiver)

            etAmount.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    updateAllSplitAmounts(etAmount, participantsContainer)
                }
            })

            btnAddParticipant.setOnClickListener {
                addParticipantField(themedContext, participantsContainer, etAmount)
            }

            view.findViewById<Button>(R.id.btnOverlaySave).setOnClickListener {
                val finalAmount = etAmount.text.toString().toDoubleOrNull() ?: amount
                val finalReceiver = etReceiver.text.toString().ifBlank { receiver }
                val note = etNote.text.toString()

                val participants = mutableListOf<ParticipantEntity>()
                for (i in 0 until participantsContainer.childCount) {
                    val pView = participantsContainer.getChildAt(i)
                    val pName = pView.findViewById<AutoCompleteTextView>(R.id.etParticipantName).text.toString()
                    val pAmt = pView.findViewById<EditText>(R.id.etParticipantAmount).text.toString().toDoubleOrNull() ?: 0.0
                    if (pName.isNotBlank()) {
                        participants.add(
                            ParticipantEntity(
                                transactionOwnerId = 0L,
                                participantName = pName,
                                amountOwed = pAmt,
                                isPaid = false
                            )
                        )
                    }
                }

                savedOnSaveLambda?.invoke(finalAmount, finalReceiver, note, participants)
                dismissOverlay()
            }

            view.findViewById<Button>(R.id.btnOverlayIgnore).setOnClickListener {
                savedOnDismissLambda?.invoke()
                dismissOverlay()
            }

            try {
                windowManager?.addView(view, params)
                Log.d("OverlayManager", "Overlay added successfully")
            } catch (e: Exception) {
                Log.e("OverlayManager", "Error showing overlay: ${e.message}")
            }
        }
    }

    fun refreshThemeIfVisible(newConfig: Configuration) {
        if (overlayView == null || windowManager == null) return
        val etAmount = overlayView?.findViewById<TextInputEditText>(R.id.etOverlayAmount)?.text.toString()
        val etReceiver = overlayView?.findViewById<MaterialAutoCompleteTextView>(R.id.etOverlayReceiver)?.text.toString()
        val etNote = overlayView?.findViewById<TextInputEditText>(R.id.etOverlayNote)?.text.toString()
        val tempSaveLambda = savedOnSaveLambda
        val tempDismissLambda = savedOnDismissLambda
        dismissOverlay()
        if (tempSaveLambda != null && tempDismissLambda != null) {
            showOverlay(
                amount = etAmount.toDoubleOrNull() ?: 0.0,
                receiver = etReceiver,
                onSave = tempSaveLambda,
                onDismiss = tempDismissLambda,
                existingNote = etNote
            )
        }
    }

    private fun addParticipantField(themedContext: Context, container: LinearLayout, etTotalAmount: TextInputEditText) {
        val binding = ItemParticipantInputBinding.inflate(LayoutInflater.from(themedContext), container, false)
        setupAutoComplete(binding.etParticipantName as AutoCompleteTextView)
        binding.btnRemoveParticipant.setOnClickListener {
            container.removeView(binding.root)
            updateAllSplitAmounts(etTotalAmount, container)
        }
        container.addView(binding.root)
        updateAllSplitAmounts(etTotalAmount, container)
    }

    private fun updateAllSplitAmounts(etTotalAmount: TextInputEditText, container: LinearLayout) {
        if (isUpdatingSplit) return
        isUpdatingSplit = true
        val totalAmount = etTotalAmount.text.toString().toDoubleOrNull() ?: 0.0
        val participantCount = container.childCount
        if (participantCount > 0) {
            val splitAmount = totalAmount / (participantCount + 1)
            val formattedAmount = String.format("%.2f", splitAmount)
            for (i in 0 until participantCount) {
                val pView = container.getChildAt(i)
                val etParticipantAmount = pView.findViewById<EditText>(R.id.etParticipantAmount)
                etParticipantAmount.setText(formattedAmount)
            }
        }
        isUpdatingSplit = false
    }

    private fun setupAutoComplete(view: AutoCompleteTextView) {
        if (contactList.isNotEmpty()) {
            val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, contactList)
            view.setAdapter(adapter)
        }
    }

    private fun loadContacts() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return
        scope.launch {
            val contacts = withContext(Dispatchers.IO) {
                val list = mutableListOf<String>()
                val cursor: Cursor? = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
                    null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                )
                cursor?.use {
                    val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    while (it.moveToNext()) {
                        val name = it.getString(nameIndex)
                        if (!list.contains(name)) list.add(name)
                    }
                }
                list
            }
            contactList = contacts
            overlayView?.let {
                val etReceiver = it.findViewById<MaterialAutoCompleteTextView>(R.id.etOverlayReceiver)
                setupAutoComplete(etReceiver)
            }
        }
    }

    fun dismissOverlay() {
        overlayView?.let { view ->
            try {
                if (view.isAttachedToWindow) {
                    windowManager?.removeView(view)
                    Log.d("OverlayManager", "Overlay removed successfully")
                }
            } catch (e: Exception) {
                Log.e("OverlayManager", "Error dismissing overlay: ${e.message}")
            }
            overlayView = null
        }
    }

    fun cleanup() {
        dismissOverlay()
        scope.cancel()
    }
}
