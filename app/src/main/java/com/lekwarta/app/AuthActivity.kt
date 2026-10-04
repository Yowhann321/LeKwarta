package com.lekwarta.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class AuthActivity : AppCompatActivity() {

    // ─── Views ───────────────────────────────────────────────────────────────
    private lateinit var layoutLogin: View
    private lateinit var layoutRegister: View

    // Login views
    private lateinit var etLoginEmail: TextInputEditText
    private lateinit var etLoginPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var tvGoToRegister: TextView
    private lateinit var pbLogin: ProgressBar

    // Register views
    private lateinit var etRegName: TextInputEditText
    private lateinit var etRegEmail: TextInputEditText
    private lateinit var etRegPassword: TextInputEditText
    private lateinit var etRegConfirm: TextInputEditText
    private lateinit var btnRegister: MaterialButton
    private lateinit var tvGoToLogin: TextView
    private lateinit var pbRegister: ProgressBar

    // ─── HTTP ─────────────────────────────────────────────────────────────────
    private val client = OkHttpClient()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If already logged in, skip straight to main
        if (SessionManager.isLoggedIn(this)) {
            goToMain()
            return
        }

        setContentView(R.layout.activity_auth)
        bindViews()
        showLogin()
    }

    // ─── Bind ─────────────────────────────────────────────────────────────────
    private fun bindViews() {
        layoutLogin    = findViewById(R.id.layoutLogin)
        layoutRegister = findViewById(R.id.layoutRegister)

        // Login
        etLoginEmail    = findViewById(R.id.etLoginEmail)
        etLoginPassword = findViewById(R.id.etLoginPassword)
        btnLogin        = findViewById(R.id.btnLogin)
        tvGoToRegister  = findViewById(R.id.tvGoToRegister)
        pbLogin         = findViewById(R.id.pbLogin)

        // Register
        etRegName     = findViewById(R.id.etRegName)
        etRegEmail    = findViewById(R.id.etRegEmail)
        etRegPassword = findViewById(R.id.etRegPassword)
        etRegConfirm  = findViewById(R.id.etRegConfirm)
        btnRegister   = findViewById(R.id.btnRegister)
        tvGoToLogin   = findViewById(R.id.tvGoToLogin)
        pbRegister    = findViewById(R.id.pbRegister)

        btnLogin.setOnClickListener { attemptLogin() }
        btnRegister.setOnClickListener { attemptRegister() }
        tvGoToRegister.setOnClickListener { showRegister() }
        tvGoToLogin.setOnClickListener { showLogin() }
    }

    // ─── Toggle views ─────────────────────────────────────────────────────────
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
        val password = etLoginPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            toast("Please fill in all fields")
            return
        }

        // ── TEST ACCOUNT (remove before final demo) ──────────────
        if (email == "test@lekwarta.com" && password == "test123") {
            SessionManager.saveSession(this, "test_token", 1, "Juan dela Cruz")
            goToMain()
            return
        }
        // ─────────────────────────────────────────────────────────

        setLoginLoading(true)

        val body = JSONObject().apply {
            put("email", email)
            put("password", password)
        }.toString().toRequestBody(JSON)

        val request = Request.Builder()
            .url("${ApiClient.BASE_URL}/login.php")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    setLoginLoading(false)
                    toast("Network error: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                runOnUiThread {
                    setLoginLoading(false)
                    try {
                        val json = JSONObject(responseBody)
                        if (json.getBoolean("success")) {
                            val token  = json.getString("token")
                            val userId = json.getInt("user_id")
                            val name   = json.getString("name")
                            SessionManager.saveSession(this@AuthActivity, token, userId, name)
                            goToMain()
                        } else {
                            toast(json.getString("message"))
                        }
                    } catch (e: Exception) {
                        toast("Unexpected response from server")
                    }
                }
            }
        })
    }

    // ─── Register ─────────────────────────────────────────────────────────────
    private fun attemptRegister() {
        val name     = etRegName.text.toString().trim()
        val email    = etRegEmail.text.toString().trim()
        val password = etRegPassword.text.toString().trim()
        val confirm  = etRegConfirm.text.toString().trim()

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            toast("Please fill in all fields")
            return
        }
        if (password != confirm) {
            toast("Passwords do not match")
            return
        }
        if (password.length < 6) {
            toast("Password must be at least 6 characters")
            return
        }

        setRegisterLoading(true)

        val body = JSONObject().apply {
            put("name", name)
            put("email", email)
            put("password", password)
        }.toString().toRequestBody(JSON)

        val request = Request.Builder()
            .url("${ApiClient.BASE_URL}/register.php")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    setRegisterLoading(false)
                    toast("Network error: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                runOnUiThread {
                    setRegisterLoading(false)
                    try {
                        val json = JSONObject(responseBody)
                        if (json.getBoolean("success")) {
                            toast("Account created! Please log in.")
                            showLogin()
                        } else {
                            toast(json.getString("message"))
                        }
                    } catch (e: Exception) {
                        toast("Unexpected response from server")
                    }
                }
            }
        })
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    private fun setLoginLoading(loading: Boolean) {
        pbLogin.visibility = if (loading) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !loading
    }

    private fun setRegisterLoading(loading: Boolean) {
        pbRegister.visibility = if (loading) View.VISIBLE else View.GONE
        btnRegister.isEnabled = !loading
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}