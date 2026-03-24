package com.example.smartupiannotation.ui.dashboard

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.smartupiannotation.data.local.AppDatabase
import com.example.smartupiannotation.data.local.entity.ParticipantEntity
import com.example.smartupiannotation.data.local.entity.TransactionEntity
import com.example.smartupiannotation.data.repository.TransactionRepository
import com.example.smartupiannotation.databinding.ActivityAddTransactionBinding
import com.example.smartupiannotation.databinding.ItemParticipantInputBinding
import com.example.smartupiannotation.ui.TransactionViewModel
import com.example.smartupiannotation.ui.TransactionViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AddTransactionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTransactionBinding
    private var editingTransactionId: Long = -1L
    
    private val viewModel: TransactionViewModel by viewModels {
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = TransactionRepository(database.transactionDao(), database.participantDao())
        TransactionViewModelFactory(repository)
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
    }

    private fun addParticipantField(name: String = "", amount: String = "") {
        val participantBinding = ItemParticipantInputBinding.inflate(layoutInflater, binding.participantsContainer, false)
        participantBinding.etParticipantName.setText(name)
        participantBinding.etParticipantAmount.setText(amount)
        participantBinding.btnRemoveParticipant.setOnClickListener {
            binding.participantsContainer.removeView(participantBinding.root)
        }
        binding.participantsContainer.addView(participantBinding.root)
    }

    private fun loadTransactionData() {
        CoroutineScope(Dispatchers.Main).launch {
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
            val pName = view.findViewById<com.google.android.material.textfield.TextInputEditText>(com.example.smartupiannotation.R.id.etParticipantName).text.toString()
            val pAmountStr = view.findViewById<com.google.android.material.textfield.TextInputEditText>(com.example.smartupiannotation.R.id.etParticipantAmount).text.toString()
            
            if (pName.isNotBlank()) {
                participants.add(ParticipantEntity(
                    transactionOwnerId = transaction.transactionId,
                    participantName = pName,
                    amountOwed = pAmountStr.toDoubleOrNull() ?: 0.0
                ))
            }
        }

        viewModel.insertTransaction(transaction, participants)
        Toast.makeText(this, "Transaction saved", Toast.LENGTH_SHORT).show()
        finish()
    }
}
