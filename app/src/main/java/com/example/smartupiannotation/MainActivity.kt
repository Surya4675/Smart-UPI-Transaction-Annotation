package com.example.smartupiannotation

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smartupiannotation.data.local.AppDatabase
import com.example.smartupiannotation.data.local.TransactionWithParticipants
import com.example.smartupiannotation.data.remote.AuthManager
import com.example.smartupiannotation.data.repository.TransactionRepository
import com.example.smartupiannotation.databinding.ActivityMainBinding
import com.example.smartupiannotation.ui.TransactionViewModel
import com.example.smartupiannotation.ui.TransactionViewModelFactory
import com.example.smartupiannotation.ui.dashboard.AddTransactionActivity
import com.example.smartupiannotation.ui.dashboard.TransactionAdapter
import com.example.smartupiannotation.ui.setup.AuthActivity
import com.example.smartupiannotation.ui.setup.SetupActivity
import com.example.smartupiannotation.utils.PermissionUtils
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var transactionAdapter: TransactionAdapter
    private val authManager by lazy { AuthManager() }

    private val viewModel: TransactionViewModel by viewModels {
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = TransactionRepository(database.transactionDao())
        TransactionViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check permissions and Auth status immediately
        if (!authManager.isUserLoggedIn()) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        if (!PermissionUtils.allPermissionsGranted(this)) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)

        setupRecyclerView()
        setupDashboard()
        observeViewModel()

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.fabAddManual.setOnClickListener {
            startActivity(Intent(this, AddTransactionActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter(
            onDeleteClick = { item ->
                showDeleteConfirmation(item)
            },
            onItemClick = { item ->
                val intent = Intent(this, AddTransactionActivity::class.java).apply {
                    putExtra("TRANSACTION_ID", item.transaction.transactionId)
                }
                startActivity(intent)
            }
        )

        binding.rvTransactions.apply {
            adapter = transactionAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
    }

    private fun setupDashboard() {
        // Setup Month Spinner
        val months = arrayOf("Last 6 Months", "Current Month")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, months)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMonth.adapter = adapter
    }

    private fun showDeleteConfirmation(item: TransactionWithParticipants) {
        AlertDialog.Builder(this)
            .setTitle("Delete Transaction")
            .setMessage("Are you sure you want to delete this transaction?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteTransaction(item.transaction)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.allTransactions.observe(this) { transactions ->
            if (transactions.isNullOrEmpty()) {
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.rvTransactions.visibility = View.GONE
            } else {
                binding.tvEmptyState.visibility = View.GONE
                binding.rvTransactions.visibility = View.VISIBLE
                transactionAdapter.submitList(transactions)
            }
        }

        viewModel.totalAmountSpent.observe(this) { total ->
            binding.tvTotalAmount.text = "₹%.2f".format(total)
        }

        viewModel.totalAmountOwed.observe(this) { owed ->
            binding.tvAmountOwed.text = "₹%.2f".format(owed)
        }

        viewModel.monthlyExpenses.observe(this) { expenses ->
            binding.barChart.setData(expenses)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.top_app_bar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SetupActivity::class.java))
                true
            }
            R.id.action_logout -> {
                authManager.logout()
                startActivity(Intent(this, AuthActivity::class.java))
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
