package loginpage

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.prototypeta.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class LoginFragment : Fragment(R.layout.fragment_login) {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 1001

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val etUsernameOrEmail = view.findViewById<EditText>(R.id.etUsername)
        val etPassword = view.findViewById<EditText>(R.id.etPassword)
        val btnLogin = view.findViewById<Button>(R.id.btnLogin)
        val btnGoogle = view.findViewById<CardView>(R.id.googlelogin)
        val tvForgotPassword = view.findViewById<TextView>(R.id.tvForgotPassword)
        etUsernameOrEmail.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.black))
        view.findViewById<View>(R.id.ivBack).setOnClickListener {
            findNavController().popBackStack(R.id.welcomeFragment, false)
        }

        tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }

        etPassword.transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()

        // Setup Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // ambil dari google-services.json
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        btnLogin.setOnClickListener {
            val input = etUsernameOrEmail.text.toString().trim()
            val password = etPassword.text.toString()
            if (input.isNotEmpty() && password.isNotEmpty()) {
                val startTime = System.currentTimeMillis()
                Log.d("PerfLogin", "Login started at $startTime")
                if (input.contains("@")) {
                    loginWithEmail(input, password, startTime)
                } else {
                    firestore.collection("users")
                        .whereEqualTo("username", input)
                        .get()
                        .addOnSuccessListener { querySnapshot ->
                            if (!querySnapshot.isEmpty) {
                                val userDoc = querySnapshot.documents[0]
                                val email = userDoc.getString("email")
                                if (email != null) loginWithEmail(email, password, startTime)
                                else showToast("Email tidak ditemukan")
                            } else showToast("Username tidak ditemukan")
                        }
                        .addOnFailureListener { e ->
                            showToast("Gagal mengambil data: ${e.message}")
                        }
                }
            } else {
                showToast("Harap isi username/email dan password")
            }
        }

        btnGoogle.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }
    }

    private fun loginWithEmail(email: String, password: String, startTime: Long) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                val endTime = System.currentTimeMillis()
                Log.d("PerfLogin", "Login success at $endTime")
                Log.d("PerfLogin", "Login duration: ${endTime - startTime} ms")

                val user = auth.currentUser
                if (user != null) {
                    val sharedPref = requireContext().getSharedPreferences("UserSession", Context.MODE_PRIVATE)
                    sharedPref.edit().putString("uid", user.uid).apply()
                }
                showToast("Login berhasil!")
                findNavController().navigate(R.id.action_loginFragment_to_dashboardFragment)
            }
            .addOnFailureListener { e ->
                val endTime = System.currentTimeMillis()
                Log.d("PerfLogin", "Login failed at $endTime")
                Log.d("PerfLogin", "Login duration: ${endTime - startTime} ms")

                showToast("Login gagal: ${e.message}")
            }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            Log.d("GoogleLogin", "onActivityResult dipanggil. resultCode: $resultCode")

            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                Log.d("GoogleLogin", "GoogleSignIn berhasil. Account: ${account?.email}, ID Token: $idToken")

                if (idToken != null) {
                    firebaseAuthWithGoogle(idToken)
                } else {
                    Log.e("GoogleLogin", "ID Token null. Pastikan default_web_client_id valid.")
                    showToast("Google Sign-In gagal: Token kosong")
                }
            } catch (e: ApiException) {
                Log.e("GoogleLogin", "GoogleSignIn gagal. Code: ${e.statusCode}, Message: ${e.message}")
                showToast("Google Sign-In gagal: ${e.message}")
            }
        }
    }

    private fun showForgotPasswordDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_forgot_password, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        val etInput = dialogView.findViewById<EditText>(R.id.emailforgot)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmforgot)

        btnConfirm.setOnClickListener {
            val input = etInput.text.toString().trim()
            if (input.isEmpty()) {
                showToast("Input tidak boleh kosong.")
                return@setOnClickListener
            }

            if (input.contains("@")) {
                // langsung pakai sebagai email
                sendResetEmail(input)
                dialog.dismiss()
            } else {
                // cari username → ambil email
                firestore.collection("users")
                    .whereEqualTo("username", input)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        if (!snapshot.isEmpty) {
                            val email = snapshot.documents[0].getString("email")
                            if (email != null) {
                                sendResetEmail(email)
                                dialog.dismiss()
                            } else {
                                showToast("Email tidak ditemukan untuk username tersebut.")
                            }
                        } else {
                            showToast("Username tidak ditemukan.")
                        }
                    }
                    .addOnFailureListener {
                        showToast("Terjadi kesalahan: ${it.message}")
                    }
            }
        }

        dialog.show()
    }

    private fun sendResetEmail(email: String) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                showToast("Link reset telah dikirim ke $email")
            }
            .addOnFailureListener {
                showToast("Gagal mengirim email: ${it.message}")
            }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val startTime = System.currentTimeMillis()
        Log.d("PerfLoginGoogle", "Google login started at $startTime")
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                val endTime = System.currentTimeMillis()
                Log.d("PerfLoginGoogle", "Google login success at $endTime")
                Log.d("PerfLoginGoogle", "Google login duration: ${endTime - startTime} ms")
                val user = result.user
                Log.d(
                    "GoogleLogin",
                    "Firebase Auth Google berhasil. UID: ${user?.uid}, Email: ${user?.email}"
                )
                if (user != null) {
                    val sharedPref =
                        requireContext().getSharedPreferences("UserSession", Context.MODE_PRIVATE)
                    sharedPref.edit().putString("uid", user.uid).apply()
                    val userDoc = hashMapOf(
                        "uid" to user.uid,
                        "username" to user.displayName,
                        "email" to user.email
                    )
                    firestore.collection("users").document(user.uid)
                        .set(userDoc, SetOptions.merge())
                    showToast("Login Google berhasil!")
                    findNavController().navigate(R.id.action_loginFragment_to_dashboardFragment)
                }

            }
            .addOnFailureListener { e ->
                val endTime = System.currentTimeMillis()
                Log.d("PerfLoginGoogle", "Google login failed at $endTime")
                Log.d("PerfLoginGoogle", "Google login duration: ${endTime - startTime} ms")

                showToast("Firebase Auth gagal: ${e.message}")
            }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}

