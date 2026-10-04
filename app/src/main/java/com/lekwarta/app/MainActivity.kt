package com.lekwarta.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.lekwarta.app.data.LeKwartaDb
import com.lekwarta.app.models.User

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (currentUser() == null) {
            logout()
            return
        }
        setContentView(R.layout.activity_main)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment

        bottomNav = findViewById(R.id.bottomNav)
        bottomNav.setupWithNavController(navHost.navController)
    }

    /** Switches tabs the same way tapping the bottom bar does. */
    fun navigateTo(destinationId: Int) {
        bottomNav.selectedItemId = destinationId
    }

    fun currentUser(): User? = LeKwartaDb.get(this).getUser(SessionManager.getUserId(this))

    fun logout() {
        SessionManager.clearSession(this)
        startActivity(Intent(this, AuthActivity::class.java))
        finish()
    }
}
