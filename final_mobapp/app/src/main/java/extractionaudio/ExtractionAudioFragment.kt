package extractionaudiopage

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
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
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage

class ExtractionAudioFragment : Fragment(R.layout.fragment_extraction_audio) {

    private lateinit var layoutStep1: LinearLayout
    private lateinit var audioPreviewStep1: ImageView
    private lateinit var layoutStep3: LinearLayout
    private lateinit var audioPreviewStep3: ImageView
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var transparent: ImageView
    private lateinit var recalldialog: FrameLayout

    private var selectedWatermarkUri: Uri? = null
    private var audioFileNameView: TextView? = null
    private var playButton: Button? = null
//    private var selectedMethod: String = "Method A"
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    //private var psnrValue: String = ""
    //private var payloadValue: String = ""
    private var watermarkedFileName: String? = null
    private var resultAudioUri: Uri? = null
    private var resultBer: String = ""
    private var isResultReady = false
    private var isReadyToProcess: Boolean = false
    private var extractionStartTime: Long = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        layoutStep1 = view.findViewById(R.id.layoutstep1exad)
        audioPreviewStep1 = view.findViewById(R.id.audioPreviewStep1Exad)
        layoutStep3 = view.findViewById(R.id.layoutstep3exad)
        audioPreviewStep3 = view.findViewById(R.id.audioPreviewStep3exad)
        loadingOverlay = view.findViewById(R.id.loadingOverlay)
        transparent = view.findViewById(R.id.transparent)
        recalldialog = view.findViewById(R.id.recalldialog)

        view.findViewById<View>(R.id.transparent).setOnClickListener {
            pauseIfPlaying()
            showAudioInputDialog("Step 1",  selectedWatermarkUri)
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


//        view.findViewById<View>(R.id.MethodExad).setOnClickListener {
//            pauseIfPlaying()
//            showInputMethodDialog()
//        }

        view.findViewById<View>(R.id.cardStep3).setOnClickListener {
            if (!resultBer.isNullOrEmpty()) {
                showResultDialog(resultBer)
            } else {
                Toast.makeText(requireContext(), "Hasil belum tersedia", Toast.LENGTH_SHORT).show()
            }
        }


        view.findViewById<Button>(R.id.btn_process).setOnClickListener {
            isReadyToProcess = selectedWatermarkUri != null //&& selectedMethod.isNotEmpty()

            if (!isReadyToProcess) {
                showToast("Pastikan audio dan metode sudah dipilih")
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
            .setTitle(getString(R.string.dialog_title_info_audio))
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Handle tombol OK di layout (bukan AlertDialog)
        dialogView.findViewById<Button>(R.id.btnCloseDialog)?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showAudioInputDialog(title: String, uriToShow: Uri?) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_input_audio, null)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.show()

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        audioFileNameView = dialogView.findViewById(R.id.tvAudioFileName)
        playButton = dialogView.findViewById(R.id.btnPlayAudio)

        tvTitle.text = title
        updateAudioDialogPreview(uriToShow)
        val uriToShow = selectedWatermarkUri
        updateAudioDialogPreview(uriToShow)

        dialogView.findViewById<Button>(R.id.btnChooseFromGallery).setOnClickListener {
            pauseIfPlaying()
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/*"
            }
            audioPickerLauncher.launch(intent)
        }

        dialogView.findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            pauseIfPlaying()
            dialog.dismiss()
            updateAudioPreview(selectedWatermarkUri)
            view?.findViewById<View>(R.id.transparent)?.setOnClickListener(null)

            // Ganti dengan click listener untuk recalldialog
            setupRecalldialogClick()
        }


        dialog.setOnDismissListener {
            pauseIfPlaying()
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    private val audioPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    // Simpan permission agar URI tetap bisa diakses
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                        selectedWatermarkUri = uri
                    }

                    updateAudioDialogPreview(uri)
            }
        }

    private fun updateAudioDialogPreview(uri: Uri?) {
        val name = getFileNameFromUri(requireContext(), uri)
        audioFileNameView?.text = name ?: "No file selected"
        playButton?.isEnabled = uri != null

        playButton?.text = if (isPlaying) "Pause Audio" else "Play Audio"

        playButton?.setOnClickListener {
            if (isPlaying) {
                pauseIfPlaying()
                playButton?.text = "Play Audio"
            } else {
                uri?.let {
                    mediaPlayer?.release()
                    mediaPlayer = MediaPlayer.create(requireContext(), it)
                    mediaPlayer?.setOnCompletionListener {
                        isPlaying = false
                        playButton?.text = "Play Audio"
                    }
                    mediaPlayer?.start()
                    isPlaying = true
                    playButton?.text = "Pause Audio"
                }
            }
        }
    }

    private fun pauseIfPlaying() {
        if (isPlaying) {
            mediaPlayer?.pause()
            mediaPlayer?.seekTo(0)
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying = false
            playButton?.text = "Play Audio"
        }
    }

    private fun updateAudioPreview(uri: Uri?) {
            transparent.visibility = View.GONE
            layoutStep1.visibility = View.GONE
            audioPreviewStep1.visibility = View.VISIBLE
            recalldialog.visibility = View.VISIBLE
    }

    private fun setupRecalldialogClick() {
        val recall = view?.findViewById<View>(R.id.recalldialog)
        recall?.setOnClickListener {
            pauseIfPlaying()
            showAudioInputDialog("Step 1", selectedWatermarkUri)
        }
    }
    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }


    private fun getFileNameFromUri(context: Context, uri: Uri?): String? {
        uri ?: return null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        val nameIndex = cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        cursor?.moveToFirst()
        val name = nameIndex?.let { cursor.getString(it) }
        cursor?.close()
        // Memotong bagian setelah "-" jika ada
        return name?.substringBeforeLast("-")?.plus(".wav") ?: name
    }

    private fun uploadAudioToFirebase(uri: Uri, folderName: String, onSuccess: (String) -> Unit) {
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
        if (selectedWatermarkUri == null) {
            showToast("watermark harus dipilih")
            return
        }

        // Mengupload file ke Firebase Storage
        uploadAudioToFirebase(selectedWatermarkUri!!, "attacked_audio") { uploadedFileName ->
            watermarkedFileName = uploadedFileName

            val data = hashMapOf(
                "attacked_file_name" to watermarkedFileName,
                "timestamp" to FieldValue.serverTimestamp(),
                "status" to "pending"
            )
            extractionStartTime = System.currentTimeMillis()
            Log.d("ExecutionTime", "Extraction audio process started at: $extractionStartTime")
            // Mengirim data ke Firestore
            FirebaseFirestore.getInstance()
                .collection("extraction_steps_audio")
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
        db.collection("processed_extraction_audio").document(docId)
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
        Log.d("ExecutionTime", "Extraction audio process finished at: $extractionEndTime")
        Log.d("ExecutionTime", "Total Extraction audio execution time: $duration ms")

        // Sekarang panggil showResultDialog untuk menampilkan hasil
        showResultDialog(ber)
    }

    // Fungsi untuk menampilkan hasil BER di dialog
    private fun showResultDialog(berValue: String) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_result_preview_audio, null)

        val dialogBuilder = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)

        val dialog = dialogBuilder.create()
        dialog.show()

        val berText = dialogView.findViewById<TextView>(R.id.textBer)
        val playButton = dialogView.findViewById<Button>(R.id.btnPlayAudioPreview)
        val downloadButton = dialogView.findViewById<Button>(R.id.btnDownload)
        val audioFileNameView = dialogView.findViewById<TextView>(R.id.audiofilenamepreview)
        audioFileNameView.visibility = View.GONE
        berText.visibility = View.VISIBLE



        // Set the BER text
        berText.text = "BER: $berValue"

        // Hide Play and Download buttons by setting visibility to GONE
        playButton.visibility = View.GONE
        downloadButton.visibility = View.GONE

        // Close button functionality
        dialogView.findViewById<Button>(R.id.btnClose).setOnClickListener {
            dialog.dismiss()
            showLoading(false)
        }
    }
}

