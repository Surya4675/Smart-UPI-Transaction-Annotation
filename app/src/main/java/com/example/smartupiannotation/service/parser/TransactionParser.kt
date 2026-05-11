package com.example.smartupiannotation.service.parser

import android.util.Log
import java.util.regex.Pattern

data class ParsedTransaction(
    val amount: Double,
    val receiverName: String,
    val bankName: String?,
    val maskedAccount: String?
)

object TransactionParser {
    private const val TAG = "TransactionParser"

    // Prioritized pattern for debited amount
    private val debitAmountPattern = Pattern.compile(
        "(?:debited with|paid|spent|transfer of|sent|amounting to|sum of|withdrawn|transfer of)\\s*(?:INR|Rs\\.?|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)",
        Pattern.CASE_INSENSITIVE
    )
    
    // General amount pattern (as fallback)
    private val generalAmountPattern = Pattern.compile(
        "(?:INR|Rs\\.?|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)",
        Pattern.CASE_INSENSITIVE
    )

    // Pattern to identify balance (to avoid picking it as transaction amount)
    private val balancePattern = Pattern.compile(
        "(?:bal|balance|available limit|bal:)\\s*(?:INR|Rs\\.?|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)",
        Pattern.CASE_INSENSITIVE
    )
    
    private val receiverPattern = Pattern.compile(
        "(?:to|at|paid to|transfer to|vpa|sent to|towards|info:)\\s+([^.,?!\\n\\r]{2,30})",
        Pattern.CASE_INSENSITIVE
    )
    
    private val accountPattern = Pattern.compile(
        "(?:A/c|account|xx|\\*|ending in)\\s*(\\d{3,4})",
        Pattern.CASE_INSENSITIVE
    )

    private val debitKeywords = listOf(
        "debited", "paid", "sent", "spent", "transfered", "txn", 
        "transaction", "amounting", "withdrawn", "transfer", "vpa"
    )

    fun parse(text: String): ParsedTransaction? {
        val lowerText = text.lowercase()
        Log.d(TAG, "Parsing text: $text")
        
        if (!debitKeywords.any { lowerText.contains(it) }) return null

        // 1. Extract Amount - Prioritize amount associated with debit
        var amount = 0.0
        val debitMatcher = debitAmountPattern.matcher(text)
        if (debitMatcher.find()) {
            amount = debitMatcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        }

        // 2. Fallback: If no direct debit amount found, try general amount but exclude balance matches
        if (amount == 0.0) {
            val balanceMatches = mutableListOf<String>()
            val balMatcher = balancePattern.matcher(text)
            while (balMatcher.find()) {
                balMatcher.group(1)?.let { balanceMatches.add(it.replace(",", "")) }
            }

            val genMatcher = generalAmountPattern.matcher(text)
            while (genMatcher.find()) {
                val foundAmtStr = genMatcher.group(1)?.replace(",", "") ?: "0"
                if (!balanceMatches.contains(foundAmtStr)) {
                    amount = foundAmtStr.toDoubleOrNull() ?: 0.0
                    if (amount > 0) break
                }
            }
        }

        if (amount <= 0) return null

        // 3. Extract Receiver
        val receiverMatcher = receiverPattern.matcher(text)
        var receiverName = "Unknown"
        if (receiverMatcher.find()) {
            var rawReceiver = receiverMatcher.group(1)?.trim() ?: "Unknown"
            // Clean up: remove info like "on date", "using app", "ref no"
            val cleanupRegex = Regex("\\s(on|using|at|ref|from|dated|current|bal|through|vpa|id)\\s.*", RegexOption.IGNORE_CASE)
            receiverName = rawReceiver.replace(cleanupRegex, "").trim()
            
            // Further cleanup: if it's just "UPI" or "TRANSFER", it's not a real name
            if (receiverName.equals("UPI", ignoreCase = true) || receiverName.equals("TRANSFER", ignoreCase = true)) {
                receiverName = "Unknown"
            }
        }

        // 4. Extract Account Info
        val accountMatcher = accountPattern.matcher(text)
        val maskedAccount = if (accountMatcher.find()) accountMatcher.group(1) else null

        Log.d(TAG, "SUCCESS Parsed -> Amt: $amount, Receiver: $receiverName, Acc: $maskedAccount")
        return ParsedTransaction(amount, receiverName, null, maskedAccount)
    }
}
