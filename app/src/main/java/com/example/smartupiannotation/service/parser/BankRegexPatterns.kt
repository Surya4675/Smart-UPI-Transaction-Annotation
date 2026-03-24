package com.example.smartupiannotation.service.parser

object BankRegexPatterns {
    /**
     * Common UPI App Package Names
     */
    const val GOOGLE_PAY = "com.google.android.apps.nbu.paisa.user"
    const val PHONEPE = "com.phonepe.app"
    const val PAYTM = "net.one97.paytm"
    const val BHIM = "in.org.npci.upiapp"

    /**
     * Regex patterns for different transaction scenarios
     */
    // Matches "Paid ₹500 to John Doe" or "Sent Rs. 200 to Starbucks"
    val AMOUNT_RECEIVER_PATTERN = Regex("(?:Rs\\.|INR|₹)\\s?([\\d,]+\\.?\\d*)\\s+(?:to|at)\\s+([A-Z\\s]{2,})", RegexOption.IGNORE_CASE)

    // Matches "Debited from A/c XX1234"
    val MASKED_ACCOUNT_PATTERN = Regex("(?:A/c|Account)\\s+X*(\\d{4})", RegexOption.IGNORE_CASE)
    
    // Matches bank names in titles like "HDFC Bank"
    val BANK_NAME_PATTERN = Regex("(HDFC|ICICI|SBI|AXIS|KOTAK|PNB|BOB)", RegexOption.IGNORE_CASE)
}
