package Main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import com.example.prototypeta.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import dashboardpage.DashboardFragment
import loginpage.LoginFragment
import registerpage.RegisterFragment
import welcomepage.WelcomeFragment

class MainActivity : AppCompatActivity() {
    private var backPressedTime: Long = 0
    private lateinit var backToast: Toast
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleHelper.getPersistedLanguage(newBase)
        Log.d("MainActivity", "Language applied in attachBaseContext: $lang")
        val context = LocaleHelper.setLocale(newBase, lang)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // ✅ Force set locale & resources BEFORE layout inflate
        val lang = LocaleHelper.getPersistedLanguage(this)
        LocaleHelper.setLocale(this, lang)
        LocaleHelper.forceUpdateResources(this, lang)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // ✅ Setup GoogleSignInClient
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // Harus sama seperti di LoginFragment
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
        // ✅ NavHostFragment setup tetap dipertahankan
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
    }
    @Suppress("MissingSuperCall")
    override fun onBackPressed() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val currentFragment = navHostFragment.childFragmentManager.fragments.firstOrNull()

        // Jika fragment bisa handle back sendiri, silakan handle dulu di fragment
        if (currentFragment is WelcomeFragment) {
            finishAffinity()
            return
        }

        if (currentFragment is LoginFragment) {
            finishAffinity()
            return
        }

        if (currentFragment is RegisterFragment) {
            finishAffinity()
            return
        }

        if (currentFragment is OnBackPressedListener && currentFragment.onBackPressed()) {
            return
        }

        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            backToast.cancel()
            showLogoutDialog() // Keluar dari semua activity
        } else {
            backToast = Toast.makeText(this, "Tekan sekali lagi untuk keluar", Toast.LENGTH_SHORT)
            backToast.show()
            backPressedTime = System.currentTimeMillis()
        }
    }
    interface OnBackPressedListener {
        fun onBackPressed(): Boolean
    }
    private fun showLogoutDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Apakah kamu ingin logout?")
            .setPositiveButton("Ya") { _, _ ->
                val sharedPref = getSharedPreferences("UserSession", Context.MODE_PRIVATE)
                sharedPref.edit().clear().apply()

                // ✅ Firebase logout
                FirebaseAuth.getInstance().signOut()

                // ✅ Google logout
                googleSignInClient.signOut().addOnCompleteListener {
                    Log.d("Logout", "Google Sign-Out berhasil")
                    finish()
                }
            }
            .setNegativeButton("Tidak") { _, _ ->
                finishAffinity()
            }
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            ?.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            ?.setTextColor(ContextCompat.getColor(this, android.R.color.black))
    }
}
