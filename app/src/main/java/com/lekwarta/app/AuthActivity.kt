package com.lekwarta.app

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.lekwarta.app.data.LeKwartaDb
import com.lekwarta.app.models.User

class AuthActivity : AppCompatActivity() {

    // ─── Views ───────────────────────────────────────────────────────────────
    private lateinit var layoutLogin: View
    private lateinit var layoutRegister: View

    private lateinit var etLoginEmail: EditText
    private lateinit var etLoginPassword: EditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var btnDemo: MaterialButton
    private lateinit var tvGoToRegister: TextView

    private lateinit var etRegName: EditText
    private lateinit var etRegEmail: EditText
    private lateinit var etRegPassword: EditText
    private lateinit var etRegConfirm: EditText
    private lateinit var btnRegister: MaterialButton
    private lateinit var tvGoToLogin: TextView

    private val db by lazy { LeKwartaDb.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If already logged in (and the user still exists), skip straight to main
        if (SessionManager.isLoggedIn(this) && db.getUser(SessionManager.getUserId(this)) != null) {
            goToMain()
            return
        }

        setContentView(R.layout.activity_auth)
        bindViews()
        etLoginEmail.setText(SessionManager.getLastEmail(this))
        showLogin()
    }

    // ─── Bind ─────────────────────────────────────────────────────────────────
    private fun bindViews() {
        layoutLogin    = findViewById(R.id.layoutLogin)
        layoutRegister = findViewById(R.id.layoutRegister)

        etLoginEmail    = findViewById(R.id.etLoginEmail)
        etLoginPassword = findViewById(R.id.etLoginPassword)
        btnLogin        = findViewById(R.id.btnLogin)
        btnDemo         = findViewById(R.id.btnDemo)
        tvGoToRegister  = findViewById(R.id.tvGoToRegister)

        etRegName     = findViewById(R.id.etRegName)
        etRegEmail    = findViewById(R.id.etRegEmail)
        etRegPassword = findViewById(R.id.etRegPassword)
        etRegConfirm  = findViewById(R.id.etRegConfirm)
        btnRegister   = findViewById(R.id.btnRegister)
        tvGoToLogin   = findViewById(R.id.tvGoToLogin)

        btnLogin.setOnClickListener { attemptLogin() }
        btnDemo.setOnClickListener { startDemo() }
        btnRegister.setOnClickListener { attemptRegister() }
        tvGoToRegister.setOnClickListener { showRegister() }
        tvGoToLogin.setOnClickListener { showLogin() }
    }

    private fun showLogin() {
        layoutLogin.visibility    = View.VISIBLE
        layoutRegister.visibility = View.GONE
    }

    private fun showRegister() {
        layoutLogin.visibility    = View.GONE
        layoutRegister.visibility = View.VISIBLE
    }

    // ─── Login ────────────────────────────────────────────────────────────────
    private fun attemptLogin() {
        val email    = etLoginEmail.text.toString().trim()
        val password = etLoginPassword.text.toString()

        if (email.isEmpty() || password.isEmpty()) {
            toast("Enter your email and password")
            return
        }
        val user = db.login(email, password)
        if (user == null) {
            etLoginPassword.error = "Wrong email or password"
            return
        }
        startSession(user)
    }

    private fun startDemo() {
        val user = db.loadDemoUser()
        toast("Demo account loaded with sample data")
        startSession(user)
    }

    // ─── Register ─────────────────────────────────────────────────────────────
    private fun attemptRegister() {
        val name     = etRegName.text.toString().trim()
        val email    = etRegEmail.text.toString().trim()
        val password = etRegPassword.text.toString()
        val confirm  = etRegConfirm.text.toString()

        when {
            name.isEmpty() -> { etRegName.error = "Enter your name"; return }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> { etRegEmail.error = "Enter a valid email"; return }
            password.length < 6 -> { etRegPassword.error = "Use at least 6 characters"; return }
            password != confirm -> { etRegConfirm.error = "Passwords don't match"; return }
        }

        val user = db.register(name, email, password)
        if (user == null) {
            etRegEmail.error = "That email is already registered"
            return
        }
        toast("Welcome to LeKwarta, ${name.substringBefore(' ')}")
        startSession(user)
    }

    private fun startSession(user: User) {
        SessionManager.saveSession(this, user.id, user.name, user.email)
        goToMain()
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
