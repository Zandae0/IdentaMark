package registerpage

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.prototypeta.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterFragment : Fragment(R.layout.fragment_register) {

    // Firebase Authentication dan Firestore
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inisialisasi instance Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Ambil referensi ke EditText dan Button dari layout
        val etUsername = view.findViewById<EditText>(R.id.etUsername)
        val etEmail = view.findViewById<EditText>(R.id.etEmail)
        val etPassword = view.findViewById<EditText>(R.id.etPassword)
        val etConfirmPassword = view.findViewById<EditText>(R.id.etConfirmPassword)
        val btnRegister = view.findViewById<Button>(R.id.btnRegister)
        view.findViewById<View>(R.id.ivBack).setOnClickListener {
            findNavController().navigateUp()
        }

        // Biar password tidak terlihat saat diketik
        etPassword.transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
        etConfirmPassword.transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()

        // Saat tombol Register ditekan
        btnRegister.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()
            // Validasi input tidak kosong
            if (username.isNotEmpty() && email.isNotEmpty() && password.isNotEmpty() && confirmPassword.isNotEmpty()) {
                // Validasi password cocok
                if (password == confirmPassword) {
                    // Buat user di Firebase Auth

                    val clickTime = System.currentTimeMillis()
                    Log.d("PerfRegister", "User clicked register at: $clickTime")

                    val startTime = System.currentTimeMillis()
                    Log.d("PerfRegister", "Register started at $startTime")
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val userId = auth.currentUser?.uid ?: return@addOnCompleteListener

                                // Data tambahan yang ingin disimpan di Firestore
                                val userMap = hashMapOf(
                                    "uid" to userId,
                                    "username" to username,
                                    "email" to email,
                                    "role" to "user",        // default role
                                    "status" to "active"     // default status
                                )

                                // Simpan ke koleksi 'users' di Firestore
                                firestore.collection("users").document(userId)
                                    .set(userMap)
                                    .addOnSuccessListener {
                                        val endTime = System.currentTimeMillis()
                                        val duration = endTime - startTime
                                        val uiRespons = startTime - clickTime
                                        Log.d("PerfRegister", "Register completed at $endTime")
                                        Log.d("PerfRegister", "Register duration: $duration ms")
                                        Log.d("PerfRegister", "UI Respons Time: $uiRespons ms")
                                        Toast.makeText(requireContext(), "Registrasi berhasil!", Toast.LENGTH_SHORT).show()
                                        // Navigasi ke LoginFragment
                                        // Sign out supaya user login manual lewat LoginFragment
                                        auth.signOut()
                                        findNavController().navigate(R.id.action_registerFragment_to_loginfragment)
                                    }
                                    .addOnFailureListener { e ->
                                        // Gagal menyimpan ke Firestore
                                        Toast.makeText(requireContext(), "Gagal simpan data: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                            } else {
                                // Gagal register ke Firebase Auth
                                Toast.makeText(requireContext(), "Registrasi gagal: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                } else {
                    // Password dan konfirmasi tidak cocok
                    Toast.makeText(requireContext(), "Password dan konfirmasi tidak cocok", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Ada input yang kosong
                Toast.makeText(requireContext(), "Harap isi semua data", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
