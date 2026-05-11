package com.example.smartupiannotation.ui.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.smartupiannotation.data.local.AppDatabase
import com.example.smartupiannotation.data.local.ParticipantEntity
import com.example.smartupiannotation.data.local.TransactionEntity
import com.example.smartupiannotation.data.repository.TransactionRepository
import com.example.smartupiannotation.databinding.ActivityAddTransactionBinding
import com.example.smartupiannotation.databinding.ItemParticipantInputBinding
import com.example.smartupiannotation.ui.TransactionViewModel
import com.example.smartupiannotation.ui.TransactionViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddTransactionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTransactionBinding
    private var editingTransactionId: Long = -1L
    private var contactList = mutableListOf<String>()
    
    private val viewModel: TransactionViewModel by viewModels {
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = TransactionRepository(database.transactionDao())
        TransactionViewModelFactory(repository)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            loadContacts()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTransactionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        editingTransactionId = intent.getLongExtra("TRANSACTION_ID", -1L)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        if (editingTransactionId != -1L) {
            loadTransactionData()
            binding.toolbar.title = "Edit Transaction"
            binding.btnSave.text = "Update Transaction"
        }

        binding.btnAddParticipant.setOnClickListener {
            addParticipantField()
        }

        binding.btnSave.setOnClickListener {
            saveTransaction()
        }

        checkContactPermission()
    }

    private fun checkContactPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED -> {
                loadContacts()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }

    private fun loadContacts() {
        lifecycleScope.launch(Dispatchers.IO) {
            val contacts = mutableListOf<String>()
            try {
                val cursor: Cursor? = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
                    null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                )
                cursor?.use {
                    val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    while (it.moveToNext()) {
                        val name = it.getString(nameIndex)
                        if (!contacts.contains(name)) contacts.add(name)
                    }
                }
                withContext(Dispatchers.Main) {
                    contactList = contacts
                    setupAutoComplete(binding.etReceiver as AutoCompleteTextView)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupAutoComplete(view: AutoCompleteTextView) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, contactList)
        view.setAdapter(adapter)
        view.threshold = 1
    }

    private fun addParticipantField(name: String = "", amount: String = "") {
        val participantBinding = ItemParticipantInputBinding.inflate(layoutInflater, binding.participantsContainer, false)
        participantBinding.etParticipantName.setText(name)
        participantBinding.etParticipantAmount.setText(amount)
        
        setupAutoComplete(participantBinding.etParticipantName as AutoCompleteTextView)

        participantBinding.btnRemoveParticipant.setOnClickListener {
            binding.participantsContainer.removeView(participantBinding.root)
        }
        binding.participantsContainer.addView(participantBinding.root)
    }

    private fun loadTransactionData() {
        lifecycleScope.launch {
            val transactionWithParticipants = viewModel.getTransactionById(editingTransactionId)
            transactionWithParticipants?.let { data ->
                binding.etAmount.setText(data.transaction.amount.toString())
                binding.etReceiver.setText(data.transaction.receiverName)
                binding.etNote.setText(data.transaction.note)
                
                binding.participantsContainer.removeAllViews()
                data.participants.forEach { p ->
                    addParticipantField(p.participantName, p.amountOwed.toString())
                }
            }
        }
    }

    private fun saveTransaction() {
        val amountStr = binding.etAmount.text.toString()
        val receiver = binding.etReceiver.text.toString()
        val note = binding.etNote.text.toString()

        if (amountStr.isBlank() || receiver.isBlank()) {
            Toast.makeText(this, "Please enter amount and receiver", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = amountStr.toDoubleOrNull() ?: 0.0
        val transaction = TransactionEntity(
            transactionId = if (editingTransactionId != -1L) editingTransactionId else 0,
            amount = amount,
            receiverName = receiver,
            transactionDate = System.currentTimeMillis(),
            bankName = "Manual",
            maskedAccount = null,
            category = "Manual",
            note = note
        )

        val participants = mutableListOf<ParticipantEntity>()
        for (i in 0 until binding.participantsContainer.childCount) {
            val view = binding.participantsContainer.getChildAt(i)
            val pName = view.findViewById<AutoCompleteTextView>(com.example.smartupiannotation.R.id.etParticipantName).text.toString()
            val pAmountStr = view.findViewById<com.google.android.material.textfield.TextInputEditText>(com.example.smartupiannotation.R.id.etParticipantAmount).text.toString()
            
            if (pName.isNotBlank()) {
                participants.add(ParticipantEntity(
                    transactionOwnerId = transaction.transactionId,
                    participantName = pName,
                    amountOwed = pAmountStr.toDoubleOrNull() ?: 0.0,
                    isPaid = false
                ))
            }
        }

        lifecycleScope.launch {
            try {
                viewModel.saveTransaction(transaction, participants)
                Toast.makeText(this@AddTransactionActivity, "Transaction saved", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@AddTransactionActivity, "Error saving: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
