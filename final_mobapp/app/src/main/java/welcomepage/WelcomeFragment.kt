package welcomepage

import Main.LocaleHelper
import Main.MainActivity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.prototypeta.R
import java.util.Locale

class WelcomeFragment : Fragment(R.layout.fragment_welcome) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnLogin = view.findViewById<Button>(R.id.btnLogin)
        val btnRegister = view.findViewById<Button>(R.id.btnRegistera)

        btnLogin?.setOnClickListener {
            findNavController().navigate(R.id.action_welcomeFragment_to_loginFragment)
        }

        btnRegister?.setOnClickListener {
            findNavController().navigate(R.id.action_welcomeFragment_to_registerFragment)
        }

        val prefs = requireContext().getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val hasChosenLanguage = prefs.getBoolean("hasChosenLanguage", false)
        Log.d("LANG_TEST", "Current locale: ${resources.configuration.locales[0]}")


        if (!hasChosenLanguage) {
            showLanguageDialog(prefs)
        }

        val languageSelector = view.findViewById<LinearLayout>(R.id.languageSelector)
        languageSelector.setOnClickListener {
            showLanguageDialog(prefs)
        }
    }

    private fun showLanguageDialog(prefs: SharedPreferences) {
        val languages = arrayOf("English", "Bahasa Indonesia")
        val codes = arrayOf("en", "id")

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.choose_language))
            .setItems(languages) { _, which ->
                val selectedLang = codes[which]

                // Simpan pilihan ke SharedPreferences
                LocaleHelper.persistLanguage(requireContext(), selectedLang)
                prefs.edit().putBoolean("hasChosenLanguage", true).apply()

                // Recreate activity untuk menerapkan bahasa
                val intent = Intent(requireContext(), MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                requireActivity().finish()
            }
            .setCancelable(true) // Harus pilih salah satu
            .show()
    }
}
