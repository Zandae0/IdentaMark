package extractionimagepage

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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage

class ExtractionImageFragment : Fragment(R.layout.fragment_extraction_image) {

    private lateinit var layoutStep1: LinearLayout
    private lateinit var imagePreviewStep1: ImageView
    private lateinit var layoutStep3: LinearLayout
    private lateinit var imagePreviewStep3: ImageView
    private lateinit var loadingOverlay: FrameLayout

    private var selectedWatermarkUri: Uri? = null // Untuk Step 2
    private var dialogImagePreview: ImageView? = null
    private var selectedMethod: String = "Method A"
    private var psnrValue: String = ""
    private var payloadValue: String = ""// Default awal
    private var watermarkedFileName: String? = null
    private var resultImageUri: Uri? = null
    private var resultBer: String = ""
    private var isResultReady = false
    private var isReadyToProcess: Boolean = false
    private var extractionStartTime: Long = 0




    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inisialisasi View
        layoutStep1 = view.findViewById(R.id.layoutstep1exim)
        imagePreviewStep1 = view.findViewById(R.id.imagePreviewStep1Exim)
        layoutStep3 = view.findViewById(R.id.layoutstep3exim)
        imagePreviewStep3 = view.findViewById(R.id.imagePreviewStep3exim)
        loadingOverlay = view.findViewById(R.id.loadingOverlay)

        // Klik CardView Step 1
        view.findViewById<View>(R.id.cardStep1).setOnClickListener {
            showImageInputDialog("Step 1")
        }

        view.findViewById<View>(R.id.ivBack).setOnClickListener {
            if (isResultReady) {
                val dialog = AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.dialog_exit_title))
                    .setMessage(getString(R.string.dialog_exit_message_extract))
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

        view.findViewById<View>(R.id.methodexim).setOnClickListener {
            showInputMethodDialog()
        }
        view.findViewById<View>(R.id.cardStep3).setOnClickListener {
            if (!resultBer.isNullOrEmpty()) {
                showResultDialog(resultBer)
            } else {
                Toast.makeText(requireContext(), "Hasil belum tersedia", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<Button>(R.id.btn_process).setOnClickListener {
            isReadyToProcess = selectedWatermarkUri != null && selectedMethod.isNotEmpty()

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

        // BER
        val berTitle = dialogView.findViewById<TextView>(R.id.tvBerTitle)
        val berDetail = dialogView.findViewById<TextView>(R.id.tvBerDetail)

        berTitle.visibility = View.VISIBLE
        berDetail.visibility = View.GONE

        berTitle.setOnClickListener {
            berDetail.visibility = if (berDetail.visibility == View.VISIBLE) View.GONE else View.VISIBLE
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

    // Fungsi untuk menampilkan dialog input gambar
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
        selectedWatermarkUri?.let { dialogImagePreview?.setImageURI(it) }

        // Tombol Pilih dari Galeri
        dialogView.findViewById<Button>(R.id.btnChooseFromGallery).setOnClickListener {
            pickImageFromGoogleDrive()
        }

        // Tombol Konfirmasi
        dialogView.findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            dialog.dismiss()
                updateImagePreview(selectedWatermarkUri)
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
                selectedWatermarkUri = uri
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

        val etPsnr = dialogView.findViewById<EditText>(R.id.etPsnr)
        val etPayload = dialogView.findViewById<EditText>(R.id.etPayload)

        etPsnr.visibility = View.GONE
        etPayload.visibility = View.GONE

        when (selectedMethod) {
            "BCH" -> rbMethodA.isChecked = true
            "Convolutional Code" -> rbMethodB.isChecked = true
        }

        dialogView.findViewById<Button>(R.id.btnConfirmMethod).setOnClickListener {
            selectedMethod = if (rbMethodA.isChecked) "BCH" else "Convolutional Code"

            // psnrValue = etPsnr.text.toString().trim()
            // payloadValue = etPayload.text.toString().trim()

            // if (psnrValue.isEmpty() || payloadValue.isEmpty()) {
            //     showToast("PSNR dan Payload harus diisi")
            //     return@setOnClickListener
            // }

            updateStep2Text("$selectedMethod")
            showToast("Metode disimpan, klik tombol 'Process' untuk mengunggah")
            dialog.dismiss()
        }
    }

    private fun updateStep2Text(selectedMethod: String) {
        val step2Text = view?.findViewById<TextView>(R.id.tvSelectMethodExim)
        step2Text?.text = selectedMethod
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
        val fileName = getFileNameFromUri(requireContext(), uri) ?: "default_image.png"  // Menggunakan nama file yang dikirimkan atau nama default

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

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun processAllData() {
        if (selectedWatermarkUri == null) {
            showToast("Gambar terwatermark harus dipilih")
            return
        }
        // Mengupload file ke Firebase Storage
        uploadImageToFirebase(selectedWatermarkUri!!, "attacked_image") { uploadedFileName ->
            watermarkedFileName = uploadedFileName

            val data = hashMapOf(
                "attacked_file_name" to watermarkedFileName,
                "timestamp" to FieldValue.serverTimestamp(),
                "status" to "pending"
            )
            extractionStartTime = System.currentTimeMillis()
            Log.d("ExecutionTime", "Extraction process started at: $extractionStartTime")
            // Mengirim data ke Firestore
            FirebaseFirestore.getInstance()
                .collection("extraction_steps")
                .add(data)
                .addOnSuccessListener { documentRef ->
                    showToast("Data berhasil dikirim ke Firebase")
                    val docId = documentRef.id
                    fetchProcessedResultAndShowDialog(docId)
                }
                .addOnFailureListener {
                    showToast("Gagal menyimpan data: ${it.message}")
                    showLoading(false)
                }
        }
    }

    private fun fetchProcessedResultAndShowDialog(docId: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("processed_extraction").document(docId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(requireContext(), "Gagal mengambil data hasil", Toast.LENGTH_SHORT).show()
                    showLoading(false)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val outputExtractPath = snapshot.getString("output_extract_path")

                    // Pastikan ada path untuk file .txt yang berisi hasil ekstraksi
                    if (!outputExtractPath.isNullOrEmpty()) {
                        val txtFileRef = FirebaseStorage.getInstance().reference.child(outputExtractPath)
                        txtFileRef.getBytes(Long.MAX_VALUE).addOnSuccessListener { bytes ->
                            val txtContent = String(bytes) // Mengubah byte array menjadi string

                            // Parsing isi file .txt menggunakan fungsi yang telah diberikan
                            parseAndShowResult(txtContent) // Menampilkan hasil BER dalam dialog
                        }.addOnFailureListener {
                            Toast.makeText(requireContext(), "Gagal mengambil file .txt", Toast.LENGTH_SHORT).show()
                            showLoading(false)
                        }
                    } else {
                        showLoading(false)
                    }
                }
            }
    }
    // Fungsi untuk memparse dan menampilkan hasil BER dari file .txt
    fun parseAndShowResult(fileContent: String) {
        // Misalnya fileContent adalah: "ber=sekian"
        val berRegex = Regex("BER: ([\\d.]+)") // regex untuk menemukan nilai ber

        val ber = berRegex.find(fileContent)?.groupValues?.get(1) ?: "Tidak ada data"
        resultBer = ber
        val extractionEndTime = System.currentTimeMillis()
        val duration = extractionEndTime - extractionStartTime
        Log.d("ExecutionTime", "Extraction process finished at: $extractionEndTime")
        Log.d("ExecutionTime", "Total Extraction execution time: $duration ms")

        // Sekarang panggil showResultDialog untuk menampilkan hasil
        showResultDialog(ber)
    }

    private fun showResultDialog(berValue: String) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_result_preview, null)

        val dialogBuilder = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)

        val dialog = dialogBuilder.create()
        dialog.show()

        val imageView = dialogView.findViewById<ImageView>(R.id.imageResultPreview)
        val berText = dialogView.findViewById<TextView>(R.id.textBer)
        val btnDownload = dialogView.findViewById<Button>(R.id.btnDownload)
        btnDownload.visibility = View.GONE
        berText.visibility = View.VISIBLE
        imageView.visibility = View.GONE

        berText.text = "BER: $berValue"
//        Glide.with(this).load(imageUri).into(imageView)
//        psnrText.text = "PSNR: $psnr"
//        payloadText.text = "Payload: $payload"
//
//        btnDownload.setOnClickListener {
//            downloadImage(imageUri, fileName)
//        }

        dialogView.findViewById<Button>(R.id.btnClose).setOnClickListener {
            dialog.dismiss()
            showLoading(false)
        }
        showLoading(false)
    }

    private fun downloadImage(uri: Uri, fileName: String) {
        val request = DownloadManager.Request(uri).apply {
            setTitle("Hasil Extract Image")
            setDescription("Mengunduh file hasil Extracting...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)

        Toast.makeText(requireContext(), "Mengunduh hasil ke folder Download", Toast.LENGTH_SHORT).show()
    }



}