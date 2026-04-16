# Smart UPI Transaction Annotation

## Overview
Smart UPI is an Android application that automatically detects UPI transactions and allows users to attach contextual notes and manage group payments.

---

## Features

- Automatic UPI transaction detection
- Floating overlay note wizard
- Auto-fill transaction details
- Group payment management
- Contact-based participant tagging
- Manual transaction entry
- Edit and delete transactions
- Search functionality
- Offline-first local database storage

---

## Architecture

The application follows MVVM Architecture:

Presentation Layer (UI & Overlay)  
ViewModel Layer  
Repository Layer  
Room Database (Data Layer)  
NotificationListenerService  
Foreground Service  

---

## Tech Stack

- Kotlin
- Android SDK
- XML Layouts
- NotificationListenerService
- Foreground Service
- Room Persistence Library
- SQLite
- ViewModel & LiveData
- RecyclerView
- AutoCompleteTextView

---

## Modules

### 1. Notification Monitoring Module
- Monitors UPI notifications
- Identifies successful transactions

### 2. Transaction Extraction Module
- Parses notification text
- Extracts amount, date, bank, masked account, etc.

### 3. Overlay Module
- Displays note wizard after UPI app closes
- Allows adding notes & category
- Supports group participants with payable amounts

### 4. Group Payment Module
- Add multiple participants
- Assign amount per participant
- Mark participants as paid

### 5. Transaction Management Module
- View transactions in table format
- Sort by transaction date
- Search by name or note
- Edit / Delete records
- Manual transaction entry

### 6. Data Storage Module
- Uses Room Database
- Offline-first
- No external data transmission

---

## Database Schema

### Table: Transactions
- transaction_id (Primary Key)
- amount
- date
- receiver_name
- bank_name
- masked_account_number
- note
- category

### Table: GroupParticipants
- participant_id (Primary Key)
- transaction_id (Foreign Key)
- participant_name
- amount_owed
- is_paid (Boolean)

Relationship:
One Transaction → Many Participants

---

## Permissions Required

- Notification Access
- Draw Over Other Apps
- Read Contacts

---

## Flow Summary

1. UPI Payment occurs
2. Notification detected
3. Transaction parsed
4. Wait until UPI app closes
5. Show floating overlay
6. User adds note or group participants
7. Save to Room Database
8. Display in table view

---

## Future Enhancements

- Analytics Dashboard
- Cloud Backup
- PDF/CSV Export
- AI-based categorization

---

## Compatibility

- Android 8.0 (Oreo) and above

---

## Privacy

- All data stored locally
- No financial data transmitted externally
