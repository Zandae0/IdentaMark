package attackingimage

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.prototypeta.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class AttackingImageFragment : Fragment(R.layout.fragment_attacking_image) {

    private lateinit var layoutStep1: LinearLayout
    private lateinit var imagePreviewStep1: ImageView
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var layoutStep4: LinearLayout
    private lateinit var imagePreviewStep4: ImageView

    private var selectedImageUri: Uri? = null
    private var dialogImagePreview: ImageView? = null
    private var selectedAttackType: String = ""
//    private val allMethods = listOf("gaussian_noise", "rotate", "resize")
    private var parameterValues = mutableListOf<String>()
    private var watermarkedFileName: String = ""
    private var resultImageUri: Uri? = null
//    private var resultPSNR: String = ""
//    private var resultPayload: String = ""
    private var isResultReady = false
    private var isReadyToProcess: Boolean = false
    private var attackingStartTime: Long = 0

    private val imageAttackTypes = mapOf(
        "NoAttack" to listOf("0"),                             // 1
        "JPEGCompression" to listOf("50", "70", "90"),         // 2
        "Rotation" to listOf("2", "5", "8"),                   // 3
        "UniformScaling" to listOf("0.8", "0.9", "1.2"),       // 6
        "NonUniformScaling" to listOf("{[0.8 1.0]}", "{[0.9 0.7]}", "{[1.0 1.2]}"         // 7
        ),
        "CombinedTransform" to listOf("{[5 0.85]}", "{[8 0.95]}", "{[10 0.80]}"           // 9
        ),
        "GeometricDistortionJPEG" to listOf("40", "60", "80"), // 11
        "LPF" to listOf("3", "5", "7"),                         // 12
        "AWGN" to listOf("0.001", "0.005", "0.01"),             // 17
        "MedianFilter" to listOf("3", "5", "7")                 // 21
    )




    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        layoutStep1 = view.findViewById(R.id.layoutstep1)
        imagePreviewStep1 = view.findViewById(R.id.imagePreviewStep1)
        layoutStep4 = view.findViewById(R.id.layoutstep4atim)
        imagePreviewStep4 = view.findViewById(R.id.imagepreviewstep4atim)
        loadingOverlay = view.findViewById(R.id.loadingOverlay)

        view.findViewById<View>(R.id.cardStep1).setOnClickListener {
            showImageInputDialog("Step 1")
        }

        view.findViewById<View>(R.id.methodatim).setOnClickListener {
            showImageAttackPickerDialog()
        }

        view.findViewById<View>(R.id.parameteratim).setOnClickListener {
            if (selectedAttackType.isNotEmpty()) {
                showImageParameterDialog(selectedAttackType) // Kirimkan attackType yang sudah dipilih
            } else {
                Toast.makeText(requireContext(), "Pilih jenis serangan terlebih dahulu", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<View>(R.id.ivBack).setOnClickListener {
            if (isResultReady) {
                val dialog = AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.dialog_exit_title))
                    .setMessage(getString(R.string.dialog_exit_message_attack))
                    .setPositiveButton(getString(R.string.dialog_button_exit)) { _, _ ->
                        findNavController().navigateUp()
                    }
                    .setNegativeButton(getString(R.string.dialog_button_cancel), null)
                    .create()

                dialog.show()

                val font = ResourcesCompat.getFont(requireContext(), R.font.archivonarrowbold)
                val black = ContextCompat.getColor(requireContext(), android.R.color.black)

                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { btn ->
                    btn.setTextColor(black)
                    btn.typeface = font
                }
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.let { btn ->
                    btn.setTextColor(black)
                    btn.typeface = font
                }
            } else {
                findNavController().navigateUp()
            }
        }

        view.findViewById<View>(R.id.cardStep4).setOnClickListener {
            if (resultImageUri != null) {
                val fileName = resultImageUri?.lastPathSegment?.substringAfterLast("/") ?: "download.jpg"
                showResultDialog(resultImageUri!!, fileName)
            } else {
                Toast.makeText(requireContext(), "Hasil belum tersedia", Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<Button>(R.id.btn_process).setOnClickListener {
            isReadyToProcess = selectedImageUri != null && selectedAttackType.isNotEmpty() && parameterValues.isNotEmpty()

            if (!isReadyToProcess) {
                showToast("Pastikan gambar dan tipe serangan sudah dipilih")
                return@setOnClickListener
            }

            showLoading(true)
            processAllData()
        }
        view.findViewById<ImageView>(R.id.ivInfo).setOnClickListener {
            showImageAttackInfoDialog()
        }
    }

    private fun showLoading(show: Boolean) {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showImageAttackInfoDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_info_attack, null)

        val attackContainer = dialogView.findViewById<LinearLayout>(R.id.attackContainer)

        val attackMap = mapOf(
            "NoAttack" to R.string.attack_no_attack,
            "JPEGCompression" to R.string.attack_jpeg,
            "Rotation" to R.string.attack_rotation,
            "UniformScaling" to R.string.attack_uniform,
            "NonUniformScaling" to R.string.attack_nonuniform,
            "CombinedTransform" to R.string.attack_rotation_resize,
            "GeometricDistortionJPEG" to R.string.attack_geo_jpeg,
            "LPF" to R.string.attack_lpf,
            "AWGN" to R.string.attack_noise,
            "MedianFilter" to R.string.attack_median
        )

        attackMap.forEach { (attackName, stringId)  ->
            val titleView = TextView(requireContext()).apply {
                text = attackName
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.BLACK)
                textSize = 16f
                setPadding(0, 16, 0, 8)
            }

            val detailView = TextView(requireContext()).apply {
                text = getString(stringId)
                setTextColor(Color.DKGRAY)
                textSize = 14f
                visibility = View.GONE
            }

            titleView.setOnClickListener {
                detailView.visibility =
                    if (detailView.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }

            attackContainer.addView(titleView)
            attackContainer.addView(detailView)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_title_attack_info))
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Handle tombol OK di layout (bukan AlertDialog)
        dialogView.findViewById<Button>(R.id.btnCloseDialog)?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }


    private fun showImageInputDialog(title: String) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_input_image, null)

        val dialogBuilder = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)

        val dialog = dialogBuilder.create()
        dialog.show()

        // Atur elemen dialog
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        tvTitle.text = title

        // Simpan referensi ke ImageView dalam dialog agar bisa diperbarui setelah memilih gambar
        dialogImagePreview = dialogView.findViewById(R.id.imagePreview)

        // Jika sudah ada gambar sebelumnya, langsung ditampilkan di preview
        selectedImageUri?.let { dialogImagePreview?.setImageURI(it) }

        // Tombol Pilih dari Galeri
        dialogView.findViewById<Button>(R.id.btnChooseFromGallery).setOnClickListener {
            pickImageFromGoogleDrive()
        }

        // Tombol Konfirmasi
        dialogView.findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            dialog.dismiss()
            updateImagePreview(selectedImageUri)
        }
    }

    // Launcher untuk memilih gambar dari galeri
    private fun pickImageFromGoogleDrive() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        googleDrivePickerLauncher.launch(intent)
    }

    private val googleDrivePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                selectedImageUri = uri
                dialogImagePreview?.setImageURI(uri)
            }
        }
    }

    // Fungsi untuk menampilkan gambar yang dipilih di CardView utama setelah Confirm
    private fun updateImagePreview(uri: Uri?) {
        layoutStep1.visibility = View.GONE
        imagePreviewStep1.visibility = View.VISIBLE
        imagePreviewStep1.setImageURI(uri)
    }

    private fun showImageAttackPickerDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_select_method_scrollable, null)

        val methodContainer = dialogView.findViewById<RadioGroup>(R.id.methodContainer)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Daftar serangan image sesuai dengan urutan dan mapping
        val imageAttackMethods = listOf(
            "NoAttack",
            "JPEGCompression",
            "Rotation",
            "UniformScaling",
            "NonUniformScaling",
            "CombinedTransform",
            "GeometricDistortionJPEG",
            "LPF",
            "AWGN",
            "MedianFilter"
        )

        // Tambahkan semua serangan ke RadioGroup
        imageAttackMethods.forEach { methodName ->
            val radioButton = RadioButton(requireContext()).apply {
                text = methodName
                setTextColor(Color.BLACK)
                typeface = ResourcesCompat.getFont(requireContext(), R.font.archivonarrowbold)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(16, 16, 16, 16)
            }
            methodContainer.addView(radioButton)
        }

        dialogView.findViewById<Button>(R.id.btnConfirmMethod).setOnClickListener {
            val selectedRadio = methodContainer.findViewById<RadioButton>(methodContainer.checkedRadioButtonId)
            if (selectedRadio != null) {
                selectedAttackType = selectedRadio.text.toString()
                view?.findViewById<TextView>(R.id.tvSelectMethod)?.text = selectedAttackType
                dialog.dismiss()

                // Reset parameter yang sudah dipilih
                parameterValues.clear()
                view?.findViewById<TextView>(R.id.tvSelectParam)?.text = "Parameter"

                val textView = view?.findViewById<TextView>(R.id.tvSelectMethod)
                textView?.textSize = 15f
            } else {
                Toast.makeText(requireContext(), "Pilih salah satu jenis serangan gambar", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }


    private fun showImageParameterDialog(attackType: String) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_input_parameters, null)

        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroupParams)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        val parameters = imageAttackTypes[attackType] ?: listOf()

        // Tambahkan di dalam fungsi showImageParameterDialog
        val paramDisplayMap = mutableMapOf<String, String>()
        parameters.forEach { originalParam ->
            val displayParam = originalParam.replace("{[", "")
                .replace("]}", "")
                .replace("{", "")
                .replace("}", "")
                .trim()
            paramDisplayMap[displayParam] = originalParam
        }

        radioGroup.clearCheck()
        radioGroup.removeAllViews()

        paramDisplayMap.forEach { (displayParam, _) ->
            val radioButton = RadioButton(requireContext()).apply {
                text = displayParam
                setTextColor(Color.BLACK)
                typeface = ResourcesCompat.getFont(requireContext(), R.font.archivonarrowbold)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(16, 16, 16, 16)
            }
            radioGroup.addView(radioButton)
        }


        dialogView.findViewById<Button>(R.id.btnConfirmParams).setOnClickListener {
            val selectedRadio =
                dialogView.findViewById<RadioButton>(radioGroup.checkedRadioButtonId)
            if (selectedRadio != null) {
                val selectedDisplay = selectedRadio.text.toString()
                val originalValue = paramDisplayMap[selectedDisplay] ?: selectedDisplay
                parameterValues = mutableListOf(originalValue)

                Toast.makeText(requireContext(), "Parameter berhasil disimpan", Toast.LENGTH_SHORT)
                    .show()
                view?.findViewById<TextView>(R.id.tvSelectParam)?.text = selectedDisplay
                dialog.dismiss()

                val textView = view?.findViewById<TextView>(R.id.tvSelectParam)
                textView?.textSize = 15f
            } else {
                Toast.makeText(requireContext(), "Pilih salah satu parameter", Toast.LENGTH_SHORT)
                    .show()
            }
        }

            dialog.show()
    }

    private fun getFileNameFromUri(context: Context, uri: Uri?): String? {
        uri ?: return null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        val nameIndex = cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        cursor?.moveToFirst()
        val name = nameIndex?.let { cursor.getString(it) }
        cursor?.close()
        return name
    }

    private fun uploadImageToFirebase(uri: Uri, folderName: String, onSuccess: (String) -> Unit) {
        // Mengambil nama file asli dari URI
        val fileName = getFileNameFromUri(requireContext(), uri) ?: "default_audio.wav"  // Menggunakan nama file yang dikirimkan atau nama default

        val fileRef = FirebaseStorage.getInstance().reference.child("$folderName/$fileName")

        fileRef.putFile(uri)
            .addOnSuccessListener {
                fileRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    onSuccess(fileName)  // Kembalikan nama file yang telah diupload
                }
            }
            .addOnFailureListener {
                showToast("Gagal mengunggah ke $folderName: ${it.message}")
            }
    }


    private fun processAllData() {
        if (selectedImageUri == null) {
            showToast("Gambar harus dipilih")
            return
        }

        if (selectedAttackType.isEmpty() || parameterValues.isEmpty()) {
            showToast("Jenis serangan dan parameter harus diisi")
            return
        }

        // Mapping jenis serangan image ke nilai numerik seperti dalam data MATLAB kamu
        val attackValue = getImageAttackId(selectedAttackType)

        if (attackValue == "-1") {
            showToast("Jenis serangan tidak valid")
            return
        }

        // Upload file ke Firebase Storage
        uploadImageToFirebase(selectedImageUri!!, "watermarked_image") { uploadedFileName ->
            watermarkedFileName = uploadedFileName

            val data = hashMapOf(
                "watermarked_file_name" to watermarkedFileName,
                "jenis" to attackValue,
                "parameter" to parameterValues.joinToString(", "),
                "timestamp" to FieldValue.serverTimestamp(),
                "status" to "pending"
            )
            attackingStartTime = System.currentTimeMillis()
            Log.d("ExecutionTime", "Attacking process started at: $attackingStartTime")
            FirebaseFirestore.getInstance()
                .collection("attacking_steps")
                .add(data)
                .addOnSuccessListener { documentRef ->
                    showToast("Data berhasil dikirim ke Firebase")
                    val docId = documentRef.id
                    fetchProcessedResultAndShowDialog(docId, uploadedFileName) // Buat fungsi ini jika diperlukan
                }
                .addOnFailureListener {
                    showToast("Gagal menyimpan data: ${it.message}")
                    showLoading(false)
                }
        }
    }

    private fun getImageAttackId(attackType: String): String {
        return when (attackType) {
            "NoAttack" -> "1"
            "JPEGCompression" -> "2"
            "Rotation" -> "3"
            "UniformScaling" -> "6"
            "NonUniformScaling" -> "7"
            "CombinedTransform" -> "9"
            "GeoDistortionJPEG" -> "11"
            "LPF" -> "12"
            "AWGN" -> "17"
            "MedianFilter" -> "21"
            else -> "-1"
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun fetchProcessedResultAndShowDialog(docId: String, fileName: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("processed_attack").document(docId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(requireContext(), "Gagal mengambil data hasil", Toast.LENGTH_SHORT).show()
                    showLoading(false)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val resultPath = snapshot.getString("attacked_image_paths")

                    if (!resultPath.isNullOrEmpty()) {
                        val storageRef = FirebaseStorage.getInstance().reference.child(resultPath)
                        storageRef.downloadUrl.addOnSuccessListener { uri ->
                            resultImageUri = uri
                            isResultReady = true

                            view?.findViewById<TextView>(R.id.output_attack_image)?.visibility = View.VISIBLE
                            view?.findViewById<ImageView>(R.id.imagepreviewstep4atim)?.apply {
                                visibility = View.VISIBLE
                                Glide.with(this@AttackingImageFragment).load(uri).into(this)
                            }

                            showResultDialog(uri, fileName)
                            showLoading(false)
                        }
                            .addOnFailureListener {
                                Toast.makeText(requireContext(), "Gagal mengambil gambar hasil", Toast.LENGTH_SHORT).show()
                                showLoading(false)
                            }
                    } else {
                        showLoading(false)
                    }
                }
            }

    }


    private fun showResultDialog(imageUri: Uri, fileName: String) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_result_preview, null)

        val attackingEndTime = System.currentTimeMillis()
        val duration = attackingEndTime - attackingStartTime
        Log.d("ExecutionTime", "Attacking process finished at: $attackingEndTime")
        Log.d("ExecutionTime", "Total attacking execution time: $duration ms")

        val dialogBuilder = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)

        val dialog = dialogBuilder.create()
        dialog.show()

        val imageView = dialogView.findViewById<ImageView>(R.id.imageResultPreview)
        val btnDownload = dialogView.findViewById<Button>(R.id.btnDownload)

        Glide.with(this).load(imageUri).into(imageView)

        btnDownload.setOnClickListener {
            downloadImage(imageUri, fileName)
        }

        dialogView.findViewById<Button>(R.id.btnClose).setOnClickListener {
            dialog.dismiss()
            showLoading(false)
        }
        showLoading(false)
    }

    private fun downloadImage(uri: Uri, filePath: String) {
        // Ambil nama file setelah "/"
        val rawFileName = filePath
            .substringAfterLast("/")
            .substringAfterLast("output_")

        // Gunakan selectedAttackType dan parameterValues yang sudah dipilih sebelumnya
        val attackId = getImageAttackId(selectedAttackType)  // misalnya "2" untuk JPEGCompression
        val param = parameterValues.joinToString("_")         // misalnya "10"

        val finalFileName = "Iw3_attacked_${rawFileName.removeSuffix(".bmp")}-${attackId}_${param}.bmp"

        val request = DownloadManager.Request(uri).apply {
            setTitle("Hasil Attack Image")
            setDescription("Mengunduh file hasil Attacking...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, finalFileName)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)

        Toast.makeText(requireContext(), "Mengunduh hasil ke folder Download", Toast.LENGTH_SHORT).show()
    }
}


