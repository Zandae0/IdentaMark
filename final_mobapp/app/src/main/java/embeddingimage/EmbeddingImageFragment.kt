package embeddingimagepage

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import com.example.prototypeta.R
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage

class EmbeddingImageFragment : Fragment(R.layout.fragment_embedding_image) {

    private lateinit var layoutStep1: LinearLayout
    private lateinit var imagePreviewStep1: ImageView
    private lateinit var layoutStep2: LinearLayout
    private lateinit var imagePreviewStep2: ImageView
    private lateinit var layoutStep4: LinearLayout
    private lateinit var imagePreviewStep4: ImageView
    private lateinit var loadingOverlay: FrameLayout

    private var selectedImageUri: Uri? = null // Untuk Step 1
//    private var selectedWatermarkUri: Uri? = null // Untuk Step 2
    private var isStep1: Boolean = true // Flag untuk menentukan gambar utama atau watermark
    private var dialogImagePreview: ImageView? = null
    private var selectedMethod: String = "Method A"
    //private var psnrValue: String = ""
    //private var payloadValue: String = ""// Default awal
    private var mainImageFileName: String? = null
    private var watermarkFileName: String? = null
    private var resultImageUri: Uri? = null
    private var resultPSNR: String = ""
    private var resultPayload: String = ""
    private var isResultReady = false
    private var isReadyToProcess: Boolean = false
    private var embeddingStartTime: Long = 0


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        layoutStep1 = view.findViewById(R.id.layoutstep1)
        imagePreviewStep1 = view.findViewById(R.id.imagePreviewStep1)
        layoutStep2 = view.findViewById(R.id.layoutstep2)
        imagePreviewStep2 = view.findViewById(R.id.imagePreviewStep2)
        layoutStep4 = view.findViewById(R.id.layoutstep4)
        imagePreviewStep4 = view.findViewById(R.id.imagePreviewStep4)
        loadingOverlay = view.findViewById(R.id.loadingOverlay)

        view.findViewById<View>(R.id.cardStep1).setOnClickListener {
            isStep1 = true
            showImageInputDialog("Step 1")
        }

        view.findViewById<View>(R.id.ivBack).setOnClickListener {
            if (isResultReady) {
                val dialog = AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.dialog_exit_title))
                    .setMessage(getString(R.string.dialog_exit_message_embed))
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


//        view.findViewById<View>(R.id.cardStep2).setOnClickListener {
//            isStep1 = false
//            showImageInputDialog("Step 2: Choose a Watermark")
//        }

        view.findViewById<View>(R.id.methodemad).setOnClickListener {
            showInputMethodDialog()
        }

        view.findViewById<View>(R.id.cardStep4).setOnClickListener {
            if (resultImageUri != null) {
                val fileName = resultImageUri?.lastPathSegment?.substringAfterLast("/") ?: "download.jpg"
                showResultDialog(resultImageUri!!, resultPSNR, resultPayload, fileName)
            } else {
                Toast.makeText(requireContext(), "Hasil belum tersedia", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<Button>(R.id.btn_process).setOnClickListener {
            isReadyToProcess = selectedImageUri != null &&  selectedMethod.isNotEmpty() //selectedWatermarkUri != null &&

            if (!isReadyToProcess) {
                showToast("Pastikan gambar dan metode sudah dipilih")
                return@setOnClickListener
            }

            showLoading(true)
            processAllData()
        }
        view.findViewById<ImageView>(R.id.ivInfo).setOnClickListener {
            showExpandableInfoDialog()
        }
    }

    private fun showLoading(show: Boolean) {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showExpandableInfoDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_info, null)

        // PSNR
        val psnrTitle = dialogView.findViewById<TextView>(R.id.tvPsnrTitle)
        val psnrDetail = dialogView.findViewById<TextView>(R.id.tvPsnrDetail)

        // Payload
        val payloadTitle = dialogView.findViewById<TextView>(R.id.tvPayloadTitle)
        val payloadDetail = dialogView.findViewById<TextView>(R.id.tvPayloadDetail)

        // BPP
        val bppTitle = dialogView.findViewById<TextView>(R.id.tvBppTitle)
        val bppDetail = dialogView.findViewById<TextView>(R.id.tvBppDetail)

        // ✅ Pastikan judul terlihat
        psnrTitle.visibility = View.VISIBLE
        payloadTitle.visibility = View.VISIBLE
        bppTitle.visibility = View.VISIBLE

        // Atur semua visibility jadi GONE (jaga-jaga)
        psnrDetail.visibility = View.GONE
        payloadDetail.visibility = View.GONE
        bppDetail.visibility = View.GONE

        // Setup toggle untuk setiap bagian
        psnrTitle.setOnClickListener {
            psnrDetail.visibility = if (psnrDetail.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        payloadTitle.setOnClickListener {
            payloadDetail.visibility = if (payloadDetail.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        bppTitle.setOnClickListener {
            bppDetail.visibility = if (bppDetail.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_title_info_image))
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

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        tvTitle.text = title
        dialogImagePreview = dialogView.findViewById(R.id.imagePreview)

        val uriToShow = selectedImageUri // else selectedWatermarkUri
        uriToShow?.let { dialogImagePreview?.setImageURI(it) }

        dialogView.findViewById<Button>(R.id.btnChooseFromGallery).setOnClickListener {
            pickImageFromGoogleDrive()
        }

        dialogView.findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            dialog.dismiss()
            if (isStep1) {
                updateImagePreview(selectedImageUri, true)
//            } else {
//                updateImagePreview(selectedWatermarkUri, false)
            }
        }
    }

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
                if (isStep1) {
                    selectedImageUri = uri
//                } else {
//                    selectedWatermarkUri = uri
                }
                dialogImagePreview?.setImageURI(uri)
            }
        }
    }

    private fun updateImagePreview(uri: Uri?, isStep1: Boolean) {
        if (isStep1) {
            layoutStep1.visibility = View.GONE
            imagePreviewStep1.visibility = View.VISIBLE
            imagePreviewStep1.setImageURI(uri)
        } else {
            layoutStep2.visibility = View.GONE
            imagePreviewStep2.visibility = View.VISIBLE
            imagePreviewStep2.setImageURI(uri)
        }
    }

    private fun showInputMethodDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_input_method_image, null)

        val dialogBuilder = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)

        val dialog = dialogBuilder.create()
        dialog.show()
        dialog.setCanceledOnTouchOutside(true)

        val rbMethodA = dialogView.findViewById<RadioButton>(R.id.radioMethodA)
        val rbMethodB = dialogView.findViewById<RadioButton>(R.id.radioMethodB)
        //val etPsnr = dialogView.findViewById<EditText>(R.id.etPsnr)
        //val etPayload = dialogView.findViewById<EditText>(R.id.etPayload)

        when (selectedMethod) {
            "BCH" -> rbMethodA.isChecked = true
            "Convolutional Code" -> rbMethodB.isChecked = true
        }

        dialogView.findViewById<Button>(R.id.btnConfirmMethod).setOnClickListener {
            selectedMethod = if (rbMethodA.isChecked) "BCH" else "Convolutional Code"
            //psnrValue = etPsnr.text.toString().trim()
            //payloadValue = etPayload.text.toString().trim()

//            if (psnrValue.isEmpty() || payloadValue.isEmpty()) {
//                showToast("PSNR dan Payload harus diisi")
//                return@setOnClickListener
//            }

            updateStep3Text("$selectedMethod") //\nPSNR: $psnrValue, Payload: $payloadValue")
            showToast("Metode disimpan, klik tombol 'Process' untuk mengunggah")
            dialog.dismiss()
        }
    }

    private fun updateStep3Text(selectedMethod: String) {
        val step3Text = view?.findViewById<TextView>(R.id.tvSelectMethod)
        step3Text?.text = selectedMethod
    }

    private fun uploadImageToFirebase(uri: Uri?, folderName: String, onSuccess: (String) -> Unit) {
        if (uri == null) {
            Toast.makeText(requireContext(), "Tidak ada gambar yang dipilih", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown"
        val db = FirebaseFirestore.getInstance()

        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                val username = document.getString("username") ?: "user_$uid"
                val originalFileName = getFileNameFromUri(requireContext(), uri) ?: "default_image.jpg"
                val baseFileName = "${username}_$originalFileName"

                generateUniqueFileName(folderName, baseFileName) { finalFileName ->
                    val fileRef = Firebase.storage.reference.child("$folderName/$finalFileName")

                    fileRef.putFile(uri)
                        .addOnSuccessListener {
                            fileRef.downloadUrl.addOnSuccessListener {
                                onSuccess(finalFileName)
                            }
                        }
                        .addOnFailureListener {
                            showToast("Gagal mengunggah ke $folderName: ${it.message}")
                        }
                }
            }

    }

    private fun generateUniqueFileName(folderName: String, baseFileName: String, callback: (String) -> Unit) {
        val storageRef = Firebase.storage.reference.child(folderName)
        val nameWithoutExt = baseFileName.substringBeforeLast(".")
        val ext = baseFileName.substringAfterLast(".", "")

        var counter = 0
        var candidateName = baseFileName

        fun checkNext() {
            val fileRef = storageRef.child(candidateName)
            fileRef.metadata
                .addOnSuccessListener {
                    // File exists, increment and try again
                    counter++
                    candidateName = "${nameWithoutExt}_$counter.$ext"
                    checkNext()
                }
                .addOnFailureListener {
                    // File does not exist, use this name
                    callback(candidateName)
                }
        }

        checkNext()
    }


    fun getFileNameFromUri(context: Context, uri: Uri?): String? {
        if (uri == null) return null

        // Jika URI memiliki path file (langsung di storage), ambil nama file menggunakan lastPathSegment
        if (uri.scheme.equals("file", ignoreCase = true)) {
            return uri.lastPathSegment
        }

        // Jika URI bukan file yang langsung, maka query content provider untuk nama file
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst()) {
                return it.getString(nameIndex)
            }
        }
        return null
    }

    private fun processAllData() {


        if (selectedImageUri == null ) { //|| selectedWatermarkUri == null
            showToast("Gambar dan watermark harus dipilih")
            return
        }
        // Tentukan nilai yang akan dikirim berdasarkan selectedMethod
        val methodValue = when (selectedMethod) {
            "BCH" -> "1"  // BCH = 1
            "Convolutional Code" -> "0"  // Convolutional Code = 0
            else -> "-1" // Untuk kasus yang tidak valid (optional)
        }

        // Pastikan nilai methodValue valid sebelum lanjut
        if (methodValue == "-1") {
            showToast("Metode tidak valid")
            return
        }

        uploadImageToFirebase(selectedImageUri!!, "image_embedding") { imageFileName ->
            mainImageFileName = imageFileName
//            uploadImageToFirebase(selectedWatermarkUri!!, "image_watermark") { watermarkFileName ->
//                this.watermarkFileName = watermarkFileName

                val data = hashMapOf(
                    "method" to methodValue,
                    "main_file_name" to mainImageFileName,
                    "status" to "pending",
                    "timestamp" to FieldValue.serverTimestamp()
                )
                embeddingStartTime = System.currentTimeMillis()
                Log.d("ExecutionTime", "Embedding process started at: $embeddingStartTime")
                FirebaseFirestore.getInstance()
                    .collection("embedding_steps")
                    .add(data)
                    .addOnSuccessListener { documentRef ->
                        showToast("Semua data berhasil dikirim ke Firebase")
                        val docId = documentRef.id
                        fetchProcessedResultAndShowDialog(docId, imageFileName)
                    }
                    .addOnFailureListener {
                        showToast("Gagal menyimpan data: ${it.message}")
                        showLoading(false)
                    }

        }
    }

    private fun fetchProcessedResultAndShowDialog(docId: String, finalFileName: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("processed_embedding").document(docId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(requireContext(), "Gagal mengambil data hasil", Toast.LENGTH_SHORT).show()
                    showLoading(false)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val resultPath = snapshot.getString("watermarked_image_path")
                    val outputEmbedPath = snapshot.getString("output_embed_path")

                    if (!resultPath.isNullOrEmpty()) {
                        val storageRef = FirebaseStorage.getInstance().reference.child(resultPath)
                        storageRef.downloadUrl.addOnSuccessListener { uri ->
                            resultImageUri = uri
                            isResultReady = true

                            view?.findViewById<TextView>(R.id.success_embedding)?.visibility = View.VISIBLE
                            view?.findViewById<ImageView>(R.id.imagePreviewStep4)?.apply {
                                visibility = View.VISIBLE
                                Glide.with(this@EmbeddingImageFragment).load(uri).into(this)
                            }

                            // Ambil file .txt (output_embed_path) dan parse isinya
                            if (!outputEmbedPath.isNullOrEmpty()) {
                                val txtFileRef =
                                    FirebaseStorage.getInstance().reference.child(outputEmbedPath)
                                txtFileRef.getBytes(Long.MAX_VALUE).addOnSuccessListener { bytes ->
                                    val txtContent =
                                        String(bytes) // Mengubah byte array menjadi string
                                    parseAndShowResult(txtContent, uri, finalFileName)
                                }.addOnFailureListener {
                                    Toast.makeText(
                                        requireContext(),
                                        "Gagal mengambil file .txt",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    showLoading(false)
                                }
                            } else {
                                showLoading(false)
                            }
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

    fun parseAndShowResult(fileContent: String, imageUri: Uri, fileName: String) {
        // Misalnya fileContent adalah:
        // "PSNR: sekian\nPayload: sekian"

        val psnrRegex = Regex("PSNR : (-?[\\d.]+)")  // regex untuk menemukan nilai PSNR
        val payloadRegex = Regex("Payload : ([\\d.]+)")  // regex untuk menemukan nilai Payload

        // Menemukan nilai PSNR dan Payload
        val psnr = psnrRegex.find(fileContent)?.groupValues?.get(1) ?: "Tidak ada data"
        val payload = payloadRegex.find(fileContent)?.groupValues?.get(1) ?: "Tidak ada data"

        resultPSNR = psnr
        resultPayload = payload
        resultImageUri = imageUri

        val embeddingEndTime = System.currentTimeMillis()
        val duration = embeddingEndTime - embeddingStartTime
        Log.d("ExecutionTime", "Embedding process finished at: $embeddingEndTime")
        Log.d("ExecutionTime", "Total Embedding execution time: $duration ms")

        // Sekarang panggil showResultDialog untuk menampilkan hasil
        showResultDialog(imageUri, psnr, payload, fileName)
    }

    private fun showResultDialog(imageUri: Uri, psnr: String, payload: String, fileName: String) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_result_preview, null)

        val dialogBuilder = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)

        val dialog = dialogBuilder.create()
        dialog.show()

        val imageView = dialogView.findViewById<ImageView>(R.id.imageResultPreview)
        val psnrText = dialogView.findViewById<TextView>(R.id.textPsnr)
        val payloadText = dialogView.findViewById<TextView>(R.id.textPayload)
        val btnDownload = dialogView.findViewById<Button>(R.id.btnDownload)
        psnrText.visibility = View.VISIBLE
        payloadText.visibility = View.VISIBLE

        Glide.with(this).load(imageUri).into(imageView)
        psnrText.text = "PSNR: $psnr dB"
        payloadText.text = "Payload: $payload bpp"

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

        // Hapus ekstensi .bmp jika ada, biarkan ekstensi terakhir (misalnya .png) tetap
        val cleanedFileName = rawFileName
            .removeSuffix(".bmp")  // menghapus .bmp jika ada

        // Tambahkan prefix
        val finalFileName = "Iw_reversible_$cleanedFileName.bmp"

        val request = DownloadManager.Request(uri).apply {
            setTitle("Hasil Embed Image")
            setDescription("Mengunduh file hasil Embedding...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, finalFileName)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)

        Toast.makeText(requireContext(), "Mengunduh hasil ke folder Download", Toast.LENGTH_SHORT).show()
    }


    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}