package com.lekwarta.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.lekwarta.app.models.Account
import com.lekwarta.app.models.Transaction
import com.lekwarta.app.models.User
import java.security.SecureRandom
import java.util.Calendar
import java.util.Date
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * On-device database that replaces the old XAMPP/PHP backend.
 * Everything (users, accounts, transactions) lives in SQLite, so the app works offline.
 */
class LeKwartaDb private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "lekwarta.db"
        private const val DB_VERSION = 1

        const val DEMO_EMAIL = "juan@lekwarta.app"

        @Volatile private var instance: LeKwartaDb? = null
        fun get(context: Context): LeKwartaDb =
            instance ?: synchronized(this) { instance ?: LeKwartaDb(context).also { instance = it } }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                email TEXT NOT NULL UNIQUE COLLATE NOCASE,
                password_hash TEXT NOT NULL,
                salt TEXT NOT NULL,
                daily_budget REAL NOT NULL DEFAULT 500,
                created_at INTEGER NOT NULL
            )""")
        db.execSQL("""
            CREATE TABLE accounts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                balance REAL NOT NULL,
                color INTEGER NOT NULL
            )""")
        db.execSQL("""
            CREATE TABLE transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                type TEXT NOT NULL,
                amount REAL NOT NULL,
                category TEXT NOT NULL,
                description TEXT NOT NULL,
                account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
                to_account_id INTEGER REFERENCES accounts(id) ON DELETE CASCADE,
                date INTEGER NOT NULL
            )""")
        db.execSQL("CREATE INDEX idx_txn_user_date ON transactions(user_id, date)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No migrations yet (version 1).
    }

    // ─── Users ────────────────────────────────────────────────────────────────

    /** Returns the new user, or null if the email is already registered. */
    fun register(name: String, email: String, password: String): User? {
        if (findUserByEmail(email) != null) return null
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        val now = System.currentTimeMillis()
        val id = writableDatabase.insert("users", null, ContentValues().apply {
            put("name", name)
            put("email", email.trim())
            put("password_hash", hash(password, salt))
            put("salt", salt)
            put("created_at", now)
        })
        if (id == -1L) return null
        // Every new user starts with a cash wallet they can rename or reset.
        addAccount(id.toInt(), "Cash", Account.TYPE_CASH, 0.0, Account.COLORS[1])
        return getUser(id.toInt())
    }

    /** Returns the user if the email and password match. */
    fun login(email: String, password: String): User? {
        readableDatabase.rawQuery(
            "SELECT id, password_hash, salt FROM users WHERE email = ?", arrayOf(email.trim())
        ).use { c ->
            if (!c.moveToFirst()) return null
            val ok = hash(password, c.getString(2)) == c.getString(1)
            return if (ok) getUser(c.getInt(0)) else null
        }
    }

    fun getUser(id: Int): User? =
        readableDatabase.rawQuery(
            "SELECT id, name, email, daily_budget, created_at FROM users WHERE id = ?", arrayOf(id.toString())
        ).use { c ->
            if (c.moveToFirst()) User(c.getInt(0), c.getString(1), c.getString(2), c.getDouble(3), c.getLong(4)) else null
        }

    private fun findUserByEmail(email: String): Int? =
        readableDatabase.rawQuery("SELECT id FROM users WHERE email = ?", arrayOf(email.trim())).use { c ->
            if (c.moveToFirst()) c.getInt(0) else null
        }

    fun setDailyBudget(userId: Int, budget: Double) {
        writableDatabase.update("users", ContentValues().apply { put("daily_budget", budget) },
            "id = ?", arrayOf(userId.toString()))
    }

    // ─── Accounts ─────────────────────────────────────────────────────────────

    fun getAccounts(userId: Int): List<Account> =
        readableDatabase.rawQuery(
            "SELECT id, name, type, balance, color FROM accounts WHERE user_id = ? ORDER BY id", arrayOf(userId.toString())
        ).use { c ->
            buildList { while (c.moveToNext()) add(Account(c.getInt(0), c.getString(1), c.getString(2), c.getDouble(3), c.getInt(4))) }
        }

    fun addAccount(userId: Int, name: String, type: String, balance: Double, color: Int): Long =
        writableDatabase.insert("accounts", null, ContentValues().apply {
            put("user_id", userId)
            put("name", name)
            put("type", type)
            put("balance", balance)
            put("color", color)
        })

    /** Renames an account and resets its balance to [balance]. */
    fun updateAccount(accountId: Int, name: String, type: String, balance: Double, color: Int) {
        writableDatabase.update("accounts", ContentValues().apply {
            put("name", name)
            put("type", type)
            put("balance", balance)
            put("color", color)
        }, "id = ?", arrayOf(accountId.toString()))
    }

    /** Deletes the account and every transaction that used it. */
    fun deleteAccount(accountId: Int) {
        writableDatabase.delete("accounts", "id = ?", arrayOf(accountId.toString()))
    }

    fun countTransactionsForAccount(accountId: Int): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM transactions WHERE account_id = ? OR to_account_id = ?",
            arrayOf(accountId.toString(), accountId.toString())
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    private fun adjustBalance(db: SQLiteDatabase, accountId: Int, delta: Double) {
        db.execSQL("UPDATE accounts SET balance = balance + ? WHERE id = ?", arrayOf(delta, accountId))
    }

    // ─── Transactions ─────────────────────────────────────────────────────────

    /**
     * Saves a transaction and updates account balances in one step.
     * Expenses deduct from the account, income adds to it, transfers move money between two accounts.
     */
    fun addTransaction(t: Transaction): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val id = db.insert("transactions", null, ContentValues().apply {
                put("user_id", t.userId)
                put("type", t.type)
                put("amount", t.amount)
                put("category", t.category)
                put("description", t.description)
                put("account_id", t.accountId)
                if (t.toAccountId != null) put("to_account_id", t.toAccountId) else putNull("to_account_id")
                put("date", t.date.time)
            })
            applyBalanceEffect(db, t, sign = 1)
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    /** Deletes a transaction and reverses its effect on account balances. */
    fun deleteTransaction(t: Transaction) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("transactions", "id = ?", arrayOf(t.id.toString()))
            applyBalanceEffect(db, t, sign = -1)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun applyBalanceEffect(db: SQLiteDatabase, t: Transaction, sign: Int) {
        when (t.type) {
            Transaction.EXPENSE -> adjustBalance(db, t.accountId, -t.amount * sign)
            Transaction.INCOME -> adjustBalance(db, t.accountId, t.amount * sign)
            Transaction.TRANSFER -> {
                adjustBalance(db, t.accountId, -t.amount * sign)
                t.toAccountId?.let { adjustBalance(db, it, t.amount * sign) }
            }
        }
    }

    /** Transactions with date in [from, to), newest first. */
    fun getTransactions(userId: Int, from: Long, to: Long): List<Transaction> =
        readableDatabase.rawQuery("""
            SELECT t.id, t.user_id, t.type, t.amount, t.category, t.description, t.account_id,
                   a.name, t.to_account_id, b.name, t.date
            FROM transactions t
            JOIN accounts a ON a.id = t.account_id
            LEFT JOIN accounts b ON b.id = t.to_account_id
            WHERE t.user_id = ? AND t.date >= ? AND t.date < ?
            ORDER BY t.date DESC, t.id DESC""",
            arrayOf(userId.toString(), from.toString(), to.toString())
        ).use { c -> buildList { while (c.moveToNext()) add(c.toTransaction()) } }

    private fun Cursor.toTransaction() = Transaction(
        id = getInt(0), userId = getInt(1), type = getString(2), amount = getDouble(3),
        category = getString(4), description = getString(5), accountId = getInt(6),
        accountName = getString(7),
        toAccountId = if (isNull(8)) null else getInt(8),
        toAccountName = if (isNull(9)) null else getString(9),
        date = Date(getLong(10))
    )

    /** Expense and income totals per day of the month: day -> (expense, income). */
    fun dailyTotals(userId: Int, year: Int, month: Int): Map<Int, Pair<Double, Double>> {
        val (from, to) = Periods.month(year, month)
        val out = mutableMapOf<Int, Pair<Double, Double>>()
        val cal = Calendar.getInstance()
        getTransactions(userId, from, to).forEach { t ->
            cal.time = t.date
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val (e, i) = out[day] ?: (0.0 to 0.0)
            out[day] = when (t.type) {
                Transaction.EXPENSE -> (e + t.amount) to i
                Transaction.INCOME -> e to (i + t.amount)
                else -> e to i
            }
        }
        return out
    }

    fun expenseBetween(userId: Int, from: Long, to: Long): Double =
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE user_id = ? AND type = 'expense' AND date >= ? AND date < ?",
            arrayOf(userId.toString(), from.toString(), to.toString())
        ).use { c -> if (c.moveToFirst()) c.getDouble(0) else 0.0 }

    /**
     * Days in a row (ending yesterday, plus today if already under budget) where spending stayed
     * within the daily budget. Only counts days since the user joined.
     */
    fun budgetStreak(user: User): Int {
        if (user.dailyBudget <= 0) return 0
        val joined = Periods.startOfDay(user.createdAt)
        val day = Calendar.getInstance()
        var streak = 0
        val (todayFrom, todayTo) = Periods.day(day.timeInMillis)
        if (expenseBetween(user.id, todayFrom, todayTo) <= user.dailyBudget) streak++
        for (i in 1..365) {
            day.add(Calendar.DAY_OF_MONTH, -1)
            val (from, to) = Periods.day(day.timeInMillis)
            if (from < joined) break
            if (expenseBetween(user.id, from, to) > user.dailyBudget) break
            streak++
        }
        return streak
    }

    // ─── Demo account ─────────────────────────────────────────────────────────

    /** Creates (or resets) a demo user filled with sample data relative to today. */
    fun loadDemoUser(): User {
        writableDatabase.delete("users", "email = ?", arrayOf(DEMO_EMAIL))
        val user = register("Juan dela Cruz", DEMO_EMAIL, "lebron23")!!
        // Backdate the join date so the streak and calendar have history.
        writableDatabase.update("users", ContentValues().apply {
            put("created_at", System.currentTimeMillis() - 45L * 24 * 60 * 60 * 1000)
            put("daily_budget", 500.0)
        }, "id = ?", arrayOf(user.id.toString()))

        val cash = getAccounts(user.id).first().id
        updateAccount(cash, "Cash", Account.TYPE_CASH, 2500.0, Account.COLORS[1])
        val bpi = addAccount(user.id, "BPI Savings", Account.TYPE_BANK, 18000.0, Account.COLORS[3]).toInt()
        val gcash = addAccount(user.id, "GCash", Account.TYPE_EWALLET, 1200.0, Account.COLORS[2]).toInt()

        data class S(val ago: Int, val type: String, val amt: Double, val cat: String, val desc: String, val acc: Int, val to: Int? = null)
        listOf(
            S(34, Transaction.INCOME, 3000.0, "income", "Weekly allowance", bpi),
            S(30, Transaction.EXPENSE, 650.0, "shopping", "School supplies", bpi),
            S(27, Transaction.INCOME, 3000.0, "income", "Weekly allowance", bpi),
            S(24, Transaction.EXPENSE, 275.0, "food", "Milk tea run", cash),
            S(20, Transaction.INCOME, 3000.0, "income", "Weekly allowance", bpi),
            S(19, Transaction.EXPENSE, 499.0, "bills", "Mobile load", gcash),
            S(16, Transaction.EXPENSE, 60.0, "transport", "Tricycle", cash),
            S(13, Transaction.INCOME, 3000.0, "income", "Weekly allowance", bpi),
            S(12, Transaction.EXPENSE, 230.0, "food", "Groceries", cash),
            S(10, Transaction.INCOME, 1500.0, "income", "Freelance logo", gcash),
            S(8, Transaction.EXPENSE, 145.0, "health", "Vitamins", cash),
            S(6, Transaction.EXPENSE, 320.0, "entertainment", "Movie night", cash),
            S(6, Transaction.INCOME, 3000.0, "income", "Weekly allowance", bpi),
            S(5, Transaction.TRANSFER, 1000.0, "transfer", "Top up GCash", bpi, gcash),
            S(4, Transaction.EXPENSE, 899.0, "shopping", "Running shoes", gcash),
            S(3, Transaction.EXPENSE, 1450.0, "bills", "Electric bill", gcash),
            S(2, Transaction.EXPENSE, 180.0, "food", "Lugaw and coffee", cash),
            S(1, Transaction.EXPENSE, 95.0, "transport", "Jeepney fares", cash),
            S(1, Transaction.INCOME, 450.0, "income", "Sold old textbooks", cash),
            S(0, Transaction.EXPENSE, 189.0, "food", "Jollibee lunch", cash),
            S(0, Transaction.EXPENSE, 26.0, "transport", "Jeepney fare", cash),
        ).forEachIndexed { i, s ->
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, -s.ago)
                set(Calendar.HOUR_OF_DAY, 8 + i % 10); set(Calendar.MINUTE, 0)
            }
            if (cal.timeInMillis > System.currentTimeMillis()) cal.timeInMillis = System.currentTimeMillis() - 60_000
            addTransaction(Transaction(
                userId = user.id, type = s.type, amount = s.amt, category = s.cat, description = s.desc,
                accountId = s.acc, accountName = "", toAccountId = s.to, date = cal.time
            ))
        }
        return getUser(user.id)!!
    }

    // ─── Password hashing ─────────────────────────────────────────────────────

    private fun hash(password: String, saltHex: String): String {
        val spec = PBEKeySpec(password.toCharArray(), saltHex.fromHex(), 10_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded.toHex()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.fromHex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
