package com.example.smartupiannotation.service.parser

import java.util.regex.Pattern

data class ParsedTransaction(
    val amount: Double,
    val receiverName: String,
    val bankName: String? = null,
    val maskedAccount: String? = null
)

object TransactionParser {
    // Basic regex for Indian UPI notification patterns
    // Example: "Sent ₹500 to John Doe" or "Paid ₹1,234.50 to Starbucks"
    private val amountPattern = Pattern.compile("(?:Rs\\.|INR|₹)\\s?([\\d,]+\\.?\\d*)", Pattern.CASE_INSENSITIVE)
    private val receiverPattern = Pattern.compile("(?:to|at)\\s+([A-Z\\s]{2,})", Pattern.CASE_INSENSITIVE)

    fun parseNotification(title: String?, text: String?): ParsedTransaction? {
        val fullContent = "$title $text"
        
        val amountMatcher = amountPattern.matcher(fullContent)
        val amount = if (amountMatcher.find()) {
            amountMatcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        } else 0.0

        val receiverMatcher = receiverPattern.matcher(fullContent)
        val receiver = if (receiverMatcher.find()) {
            receiverMatcher.group(1)?.trim() ?: "Unknown"
        } else "Unknown"

        if (amount > 0) {
            return ParsedTransaction(amount, receiver)
        }
        return null
    }
}
