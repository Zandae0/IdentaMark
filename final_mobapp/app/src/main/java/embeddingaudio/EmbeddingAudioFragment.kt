package embeddingaudiopage

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
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
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import java.util.UUID

class EmbeddingAudioFragment : Fragment(R.layout.fragment_embedding_audio) {

    private lateinit var layoutStep1: LinearLayout
    private lateinit var audioPreviewStep1: ImageView
    private lateinit var layoutStep2: LinearLayout
    private lateinit var audioPreviewStep2: ImageView
    private lateinit var layoutStep4: FrameLayout
    private lateinit var audioPreviewStep4: ImageView
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var transparent: ImageView
    private lateinit var recalldialog: FrameLayout
    private lateinit var recalldialogstep4: FrameLayout


    private var selectedAudioUri: Uri? = null
    private var isStep1: Boolean = true
    private var audioFileNameView: TextView? = null
    private var dialogImagePreview: ImageView? = null
    private var playButton: Button? = null
    private var selectedMethod: String = "Method A"
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var mainAudioFileName: String? = null
    private var watermarkFileName: String? = null
    private var resultAudioUri: Uri? = null
    private var resultPayload: String = ""
    private var isResultReady = false
    private var isReadyToProcess: Boolean = false
    private var resultODG: String = ""
    private var resultSNR: String = ""
    private var resultFileName: String = "download.wav"
    private var embeddingStartTime: Long = 0



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        layoutStep1 = view.findViewById(R.id.layoutstep1audio)
        audioPreviewStep1 = view.findViewById(R.id.audioPreviewStep1)
        layoutStep2 = view.findViewById(R.id.layoutstep2audio)
        audioPreviewStep2 = view.findViewById(R.id.audioPreviewStep2)
        layoutStep4 = view.findViewById(R.id.layoutstep4audio)
        audioPreviewStep4 = view.findViewById(R.id.audioPreviewStep4)
        loadingOverlay = view.findViewById(R.id.loadingOverlay)
        transparent = view.findViewById(R.id.transparent)
        recalldialog = view.findViewById(R.id.recalldialog)
        recalldialogstep4 = view.findViewById(R.id.recalldialogstep4)

        view.findViewById<View>(R.id.transparent).setOnClickListener {
            pauseIfPlaying()
            isStep1 = true
            showAudioInputDialog("Step 1",  selectedAudioUri)
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


        view.findViewById<View>(R.id.selectmethodstep3).setOnClickListener {
            pauseIfPlaying()
            showInputMethodDialog()
        }

        view.findViewById<View>(R.id.audioPreviewStep4).setOnClickListener {
            if (resultAudioUri != null) {
                showResultDialog(resultFileName, resultAudioUri!!, resultPayload, resultSNR)//resultPSNR, )
            } else {
                Toast.makeText(requireContext(), "Hasil belum tersedia", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<Button>(R.id.btn_process).setOnClickListener {
            isReadyToProcess = selectedAudioUri != null && selectedMethod.isNotEmpty() //&& selectedWatermarkUri != null

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

        // SNR
        val snrTitle = dialogView.findViewById<TextView>(R.id.tvSnrTitle)
        val snrDetail = dialogView.findViewById<TextView>(R.id.tvSnrDetail)

        // Payload
        val payloadTitle = dialogView.findViewById<TextView>(R.id.tvPayloadTitle)
        val payloadDetail = dialogView.findViewById<TextView>(R.id.tvPayloadDetail)

        // BPS
        val bpsTitle = dialogView.findViewById<TextView>(R.id.tvBpsTitle)
        val bpsDetail = dialogView.findViewById<TextView>(R.id.tvBpsDetail)

        // ✅ Pastikan judul terlihat
        snrTitle.visibility = View.VISIBLE
        payloadTitle.visibility = View.VISIBLE
        bpsTitle.visibility = View.VISIBLE

        // Atur semua visibility jadi GONE (jaga-jaga)
        snrDetail.visibility = View.GONE
        payloadDetail.visibility = View.GONE
        bpsDetail.visibility = View.GONE

        // Setup toggle untuk setiap bagian
        snrTitle.setOnClickListener {
            snrDetail.visibility = if (snrDetail.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        payloadTitle.setOnClickListener {
            payloadDetail.visibility = if (payloadDetail.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        bpsTitle.setOnClickListener {
            bpsDetail.visibility = if (bpsDetail.visibility == View.VISIBLE) View.GONE else View.VISIBLE
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
        dialogImagePreview = dialogView.findViewById(R.id.imageWatermarkPreview)

        tvTitle.text = title
        updateAudioDialogPreview(uriToShow)
        val uriToShow = selectedAudioUri // else selectedWatermarkUri


//        if (isStep1) {
//            updateAudioDialogPreview(uriToShow) // khusus untuk audio
//        } else {
//            // Untuk gambar, tampilkan preview gambar
//            dialogImagePreview?.visibility = View.VISIBLE
//            dialogImagePreview?.setImageURI(uriToShow)
//
//            // Sembunyikan elemen audio
//            playButton?.visibility = View.GONE
//            audioFileNameView?.visibility = View.GONE
//            audioFileNameView?.text = getFileNameFromUri(requireContext(), uriToShow)
//        }

        dialogView.findViewById<Button>(R.id.btnChooseFromGallery).setOnClickListener {
            pauseIfPlaying()
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (isStep1) "audio/*" else "image/*"
            }
            audioPickerLauncher.launch(intent)
        }

        dialogView.findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            pauseIfPlaying()
            dialog.dismiss()
            if (isStep1) {
                updateAudioPreview(selectedAudioUri, true)

                view?.findViewById<View>(R.id.transparent)?.setOnClickListener(null)

                // Ganti dengan click listener untuk recalldialog
                setupRecalldialogClick()
            }
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

                    if (isStep1) {
                        selectedAudioUri = uri
                    }

                    updateAudioDialogPreview(uri)
                }
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

    private fun updateAudioPreview(uri: Uri?, isStep1: Boolean) {
        if (isStep1) {
            transparent.visibility = View.GONE
            layoutStep1.visibility = View.GONE
            audioPreviewStep1.visibility = View.VISIBLE
            recalldialog.visibility = View.VISIBLE
        }
    }

    private fun setupRecalldialogClick() {
        val recall = view?.findViewById<View>(R.id.recalldialog)
        recall?.setOnClickListener {
            pauseIfPlaying()
            isStep1 = true
            showAudioInputDialog("Step 1", selectedAudioUri)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun showInputMethodDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_input_method_audio, null)

        val dialogBuilder = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)

        val dialog = dialogBuilder.create()
        dialog.show()
        dialog.setCanceledOnTouchOutside(true)

        val rbMethodA = dialogView.findViewById<RadioButton>(R.id.radioMethodA)
        val rbMethodB = dialogView.findViewById<RadioButton>(R.id.radioMethodB)

        when (selectedMethod) {
            "BCH" -> rbMethodA.isChecked = true
            "Convolutional Code" -> rbMethodB.isChecked = true
        }

        dialogView.findViewById<Button>(R.id.btnConfirmMethod).setOnClickListener {
            selectedMethod = if (rbMethodA.isChecked) "BCH" else "Convolutional Code"
            updateStep3Text("$selectedMethod")
            showToast("Metode disimpan, klik tombol 'Process' untuk mengunggah")
            dialog.dismiss()
        }
    }
    private fun updateStep3Text(selectedMethod: String) {
        val step3Text = view?.findViewById<TextView>(R.id.tvSelectMethodAudio)
        step3Text?.text = selectedMethod
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

    private fun uploadAudioToFirebase(uri: Uri?, folderName: String, onSuccess: (String) -> Unit) {
        if (uri == null) {
            Toast.makeText(requireContext(), "Tidak ada gambar yang dipilih", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown"
        val db = FirebaseFirestore.getInstance()

        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                val username = document.getString("username") ?: "user_$uid"
                val originalFileName = getFileNameFromUri(requireContext(), uri) ?: "default_audio.wav"
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

    private fun processAllData() {
        if (selectedAudioUri == null ) { //|| selectedWatermarkUri == null
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

        uploadAudioToFirebase(selectedAudioUri!!, "audio_embedding") { audioFileName ->
            mainAudioFileName = audioFileName

                val data = hashMapOf(
                    "method" to methodValue,
                    "main_file_name" to mainAudioFileName,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "status" to "pending"
                )
                embeddingStartTime = System.currentTimeMillis()
                Log.d("ExecutionTime", "Embedding audio process started at: $embeddingStartTime")
                FirebaseFirestore.getInstance()
                    .collection("embedding_steps_audio")
                    .add(data)
                    .addOnSuccessListener { documentRef ->
                        showToast("Semua data berhasil dikirim ke Firebase")
                        val docId = documentRef.id
                        fetchProcessedResultAndShowDialog(docId, audioFileName)
                    }
                    .addOnFailureListener {
                        showToast("Gagal menyimpan data: ${it.message}")
                        showLoading(false)
                    }


        }
    }

    private fun fetchProcessedResultAndShowDialog(docId: String, finalFileName: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("processed_embedding_audio").document(docId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(requireContext(), "Gagal mengambil data hasil", Toast.LENGTH_SHORT).show()
                    showLoading(false)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val resultPath = snapshot.getString("watermarked_audio_paths")
                    val outputEmbedPath = snapshot.getString("output_embed_path")


                    if (!resultPath.isNullOrEmpty()) {
                        val storageRef = FirebaseStorage.getInstance().reference.child(resultPath)
                        storageRef.downloadUrl.addOnSuccessListener { uri ->
                            resultAudioUri = uri
                            isResultReady = true

                            view?.findViewById<View>(R.id.audioPreviewStep4)
                                ?.setOnClickListener(null)

                            // Ganti dengan click listener untuk recalldialog
                            setupRecalldialogStep4Click()
                            audioPreviewStep4.visibility = View.GONE
                            val successText =
                                view?.findViewById<TextView>(R.id.success_embedding_audio)
                            successText?.apply {
                                visibility = View.VISIBLE
                                alpha = 1f
                                isEnabled = true
                            }
                            recalldialogstep4.visibility = View.VISIBLE

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

    fun parseAndShowResult(fileContent: String, audioUri: Uri, fileName: String) {
        // Misalnya fileContent adalah:
        // "odg=sekian\nsnr=sekian\npayload=sekian"

        val odgRegex = Regex("ODG: (-?[\\d.]+)") // regex untuk menemukan nilai odg
        val snrRegex = Regex("SNR: ([\\d.]+)") // regex untuk menemukan nilai snr
        val payloadRegex = Regex("Payload: ([\\d.]+)") // regex untuk menemukan nilai payload

        val odg = odgRegex.find(fileContent)?.groupValues?.get(1) ?: "Tidak ada data"
        val snr = snrRegex.find(fileContent)?.groupValues?.get(1) ?: "Tidak ada data"
        val payload = payloadRegex.find(fileContent)?.groupValues?.get(1) ?: "Tidak ada data"

        resultODG = odg
        resultSNR = snr
        resultPayload = payload
        resultAudioUri = audioUri
        resultFileName = fileName

        val embeddingEndTime = System.currentTimeMillis()
        val duration = embeddingEndTime - embeddingStartTime
        Log.d("ExecutionTime", "Embedding audio process finished at: $embeddingEndTime")
        Log.d("ExecutionTime", "Total Embedding audio execution time: $duration ms")

        // Sekarang panggil showResultDialog untuk menampilkan hasil
        showResultDialog(resultFileName, audioUri, snr, payload)
    }

    private fun showResultDialog(fileName: String, audioUri: Uri, snr: String, payload: String) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_result_preview_audio, null)

        val dialogBuilder = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)

        val dialog = dialogBuilder.create()
        dialog.show()

        // Ambil nama file saja (misalnya: "tmphmizhzxa.wav")
        val simpleFileName = fileName.substringAfterLast("/") // Ambil setelah '/'
        dialogView.findViewById<TextView>(R.id.audiofilenamepreview)?.text = simpleFileName

        // Menampilkan tombol Play untuk preview audio
        playButton = dialogView.findViewById(R.id.btnPlayAudioPreview)

        // Menampilkan nilai PSNR, Payload, dan ODG
        val odgText = dialogView.findViewById<TextView>(R.id.textOdg)  // TextView untuk ODG
        val snrText = dialogView.findViewById<TextView>(R.id.textSnr)  // TextView untuk SNR
        val payloadText = dialogView.findViewById<TextView>(R.id.textPayload)  // TextView untuk Payload
        snrText.visibility = View.VISIBLE
        payloadText.visibility = View.VISIBLE
        val btnDownload = dialogView.findViewById<Button>(R.id.btnDownload)

        // Set value for ODG, SNR, Payload
        snrText.text = "SNR: $snr dB"
        payloadText.text = "Payload: $payload bps"

        // Update audio preview (audio file preview)
        updateAudioDialogPreview(audioUri)

        // Set listener untuk tombol download
        btnDownload.setOnClickListener {
            downloadAudio(audioUri, simpleFileName)
        }

        // Set listener untuk tombol Close dialog
        dialogView.findViewById<Button>(R.id.btnClose).setOnClickListener {
            pauseIfPlaying()
            dialog.dismiss()
            showLoading(false)
        }
    }


    private fun downloadAudio(uri: Uri, fileName: String) {
        val request = DownloadManager.Request(uri).apply {
            setTitle("Hasil Embed Audio")
            setDescription("Mengunduh file hasil Embedding...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)

        Toast.makeText(requireContext(), "Mengunduh hasil ke folder Download", Toast.LENGTH_SHORT).show()
    }

    private fun setupRecalldialogStep4Click() {
        val recall = view?.findViewById<View>(R.id.recalldialogstep4)
        recall?.setOnClickListener {
            pauseIfPlaying()
            showResultDialog(resultFileName, resultAudioUri!!, resultPayload, resultSNR)//resultPSNR, )
        }
    }
}
