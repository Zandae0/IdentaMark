package attackingaudio

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaPlayer
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
import java.io.File

class AttackingAudioFragment : Fragment(R.layout.fragment_attacking_audio) {

    private lateinit var layoutStep1: LinearLayout
    private lateinit var audioPreviewStep1: ImageView
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var layoutStep4: FrameLayout
    private lateinit var AudioPreviewStep4: ImageView
    private lateinit var transparent: ImageView
    private lateinit var recalldialog: FrameLayout
    private lateinit var recalldialogstep4: FrameLayout

    private var selectedWatermarkUri: Uri? = null
    private var audioFileNameView: TextView? = null
    private var playButton: Button? = null
    private var dialogAudioPreview: ImageView? = null
    private var selectedAttackType: String = ""
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private val allMethods = listOf("")
    private var parameterValues = mutableListOf<String>()
    private var watermarkedFileName: String = ""
    private var resultAudioUri: Uri? = null
    private var resultPSNR: String = ""
    private var resultPayload: String = ""
    private var isResultReady = false
    private var isReadyToProcess: Boolean = false
    private var resultFileName: String = ""
    private var attackingStartTime: Long = 0

    // Map untuk jenis serangan dan parameter yang sesuai
    private val attackTypes = mapOf(
        "NoAttack" to listOf("0"),
        "LPF" to listOf("3000", "6000", "9000"),
        "BPF" to listOf("1", "2", "3", "4", "5"),
        "Requantization" to listOf("8"),
        "AdditiveWhite" to listOf("10", "20", "30"),
        "Resampling" to listOf("1", "2", "3", "4"),
        "MP3Compression" to listOf("32", "64", "96", "128", "192")
    )


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        layoutStep1 = view.findViewById(R.id.layoutstep1)
        audioPreviewStep1 = view.findViewById(R.id.audioPreviewStep1)
        layoutStep4 = view.findViewById(R.id.layoutstep4atad)
        AudioPreviewStep4 = view.findViewById(R.id.audiopreviewstep4atad)
        loadingOverlay = view.findViewById(R.id.loadingOverlay)
        transparent = view.findViewById(R.id.transparent)
        recalldialog = view.findViewById(R.id.recalldialog)
        recalldialogstep4 = view.findViewById(R.id.recalldialogstep4)

        view.findViewById<View>(R.id.transparent).setOnClickListener {
            pauseIfPlaying()
            showAudioInputDialog("Step 1")
        }

        view.findViewById<View>(R.id.methodstep2audio).setOnClickListener {
            pauseIfPlaying()
            showAttackPickerDialog()
        }

        view.findViewById<View>(R.id.parameterstep3audio).setOnClickListener {
            pauseIfPlaying()
            // Pastikan `selectedAttackType` sudah memiliki nilai yang benar sebelum dipanggil
            if (selectedAttackType.isNotEmpty()) {
                showParameterDialog(selectedAttackType) // Kirimkan attackType yang sudah dipilih
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

        view.findViewById<View>(R.id.audiopreviewstep4atad).setOnClickListener {
            if (resultAudioUri != null) {
                val simpleFileName = resultFileName.substringAfterLast("/") // Ambil setelah '/'
                showResultDialog(simpleFileName, resultAudioUri!!) //resultPSNR, )
            } else {
                Toast.makeText(requireContext(), "Hasil belum tersedia", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<Button>(R.id.btn_process).setOnClickListener {
            isReadyToProcess = selectedWatermarkUri != null && selectedAttackType.isNotEmpty() && parameterValues.isNotEmpty()

            if (!isReadyToProcess) {
                showToast("Pastikan audio dan metode sudah dipilih")
                return@setOnClickListener
            }

            showLoading(true)
            processAllData()
        }
        view.findViewById<ImageView>(R.id.ivInfo).setOnClickListener {
            showAudioAttackInfoExpandableDialog()
        }
    }

    private fun showLoading(show: Boolean) {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showAudioAttackInfoExpandableDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_info_attack, null)

        val attackContainer = dialogView.findViewById<LinearLayout>(R.id.attackContainer)

        val attackMap = mapOf(
            "LPF" to R.string.audio_attack_lpf,
            "BPF" to R.string.audio_attack_bpf,
            "Requantization" to R.string.audio_attack_requant,
            "AdditiveWhite" to R.string.audio_attack_additive,
            "Resampling" to R.string.audio_attack_resample,
            "MP3Compression" to R.string.audio_attack_mp3
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
            .setTitle(getString(R.string.dialog_title_attack_audio))
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Handle tombol OK di layout (bukan AlertDialog)
        dialogView.findViewById<Button>(R.id.btnCloseDialog)?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showAudioInputDialog(title: String) {
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
            showAudioInputDialog("Step 1")
        }
    }

    private fun showAttackPickerDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_select_method_scrollable, null)

        val methodContainer = dialogView.findViewById<RadioGroup>(R.id.methodContainer)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val allMethods = listOf(
            "NoAttack",
            "LPF",
            "BPF",
            "Requantization",
            "AdditiveWhite",
            "Resampling",
            "MP3Compression")

        // Menambahkan semua jenis serangan ke dalam RadioGroup
        allMethods.forEach { methodName ->
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
                parameterValues.clear()  // Reset parameter yang sudah dipilih
                view?.findViewById<TextView>(R.id.tvSelectParam)?.text = "Parameter"

                val textView = view?.findViewById<TextView>(R.id.tvSelectMethod)
                textView?.textSize = 15f
            } else {
                Toast.makeText(requireContext(), "Pilih salah satu jenis serangan", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }


    private fun showParameterDialog(attackType: String) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_input_parameters, null)

        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroupParams)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()
        // Reset pilihan parameter sebelumnya
        radioGroup.clearCheck()

        // Ambil parameter berdasarkan jenis serangan
        val parameters = attackTypes[attackType] ?: listOf()

        // Menghapus RadioButton yang sebelumnya
        radioGroup.removeAllViews()

        // Menambahkan RadioButton berdasarkan parameter yang sesuai
        parameters.forEach { param ->
            val radioButton = RadioButton(requireContext()).apply {
                text = param
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
            // Ambil nilai parameter yang dipilih dari RadioButton
            val selectedRadio = dialogView.findViewById<RadioButton>(radioGroup.checkedRadioButtonId)
            if (selectedRadio != null) {
                val selectedParameter = selectedRadio.text.toString()
                parameterValues = mutableListOf(selectedParameter)  // Menyimpan parameter yang dipilih

                // Jika parameter tidak kosong
                Toast.makeText(requireContext(), "Parameter berhasil disimpan", Toast.LENGTH_SHORT).show()
                view?.findViewById<TextView>(R.id.tvSelectParam)?.text = parameterValues.joinToString(", ")
                dialog.dismiss()

                val textView = view?.findViewById<TextView>(R.id.tvSelectParam)
                textView?.textSize = 15f
            } else {
                Toast.makeText(requireContext(), "Pilih salah satu parameter", Toast.LENGTH_SHORT).show()
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
            showToast("Gambar harus dipilih")
            return
        }

        if (selectedAttackType.isEmpty() || parameterValues.isEmpty()) {
            showToast("Jenis Serangan dan parameter harus diisi")
            return
        }

        // Menentukan nilai String untuk jenis serangan
        val attackValue = when (selectedAttackType) {
            "NoAttack" -> "0"
            "LPF" -> "1"
            "BPF" -> "2"
            "Requantization" -> "3"
            "AdditiveWhite" -> "5"
            "Resampling" -> "6"
            "MP3Compression" -> "13"
            else -> "-1" // Jika jenis serangan tidak valid
        }

        // Memastikan jenis serangan yang dipilih valid
        if (attackValue == "-1") {
            showToast("Jenis serangan tidak valid")
            return
        }

        // Mengupload file ke Firebase Storage
        uploadAudioToFirebase(selectedWatermarkUri!!, "watermarked_audio") { uploadedFileName ->
            watermarkedFileName = uploadedFileName

            val data = hashMapOf(
                "watermarked_file_name" to watermarkedFileName,
                "jenis" to attackValue,  // Mengirim nilai numerik dari jenis serangan
                "parameter" to parameterValues.joinToString(", "), // Mengirimkan parameter serangan
                "timestamp" to FieldValue.serverTimestamp(),
                "status" to "pending"
            )
            attackingStartTime = System.currentTimeMillis()
            Log.d("ExecutionTime", "Attacking audio process started at: $attackingStartTime")
            // Mengirim data ke Firestore
            FirebaseFirestore.getInstance()
                .collection("attacking_steps_audio")
                .add(data)
                .addOnSuccessListener { documentRef ->
                    showToast("Data berhasil dikirim ke Firebase")
                    val docId = documentRef.id
                    fetchProcessedResultAndShowDialog(docId, uploadedFileName)
                }
                .addOnFailureListener {
                    showToast("Gagal menyimpan data: ${it.message}")
                    showLoading(false)
                }
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun fetchProcessedResultAndShowDialog(docId: String, fileName: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("processed_attack_audio").document(docId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(requireContext(), "Gagal mengambil data hasil", Toast.LENGTH_SHORT).show()
                    showLoading(false)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val resultPath = snapshot.getString("attacked_audio_paths")


                    if (!resultPath.isNullOrEmpty()) {
                        val storageRef = FirebaseStorage.getInstance().reference.child(resultPath)
                        storageRef.downloadUrl.addOnSuccessListener { uri ->
                            resultAudioUri = uri
                            resultFileName = fileName
                            isResultReady = true

                            view?.findViewById<View>(R.id.audiopreviewstep4atad)?.setOnClickListener(null)

                            // Ganti dengan click listener untuk recalldialog
                            setupRecalldialogStep4Click()
                            AudioPreviewStep4.visibility = View.GONE
                            val successText = view?.findViewById<TextView>(R.id.output_attack_audio)
                            successText?.apply {
                                visibility = View.VISIBLE
                                alpha = 1f
                                isEnabled = true
                            }
                            recalldialogstep4.visibility = View.VISIBLE

                            showResultDialog(fileName, uri) //fileName)
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

    private fun showResultDialog(fileName: String, audioUri: Uri){ //psnr: String,) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_result_preview_audio, null)

        val attackingEndTime = System.currentTimeMillis()
        val duration = attackingEndTime - attackingStartTime
        Log.d("ExecutionTime", "Attacking audio process finished at: $attackingEndTime")
        Log.d("ExecutionTime", "Total attacking audio execution time: $duration ms")

        val dialogBuilder = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)

        val dialog = dialogBuilder.create()
        dialog.show()

        val simpleFileName = fileName.substringAfterLast("/") // Ambil setelah '/'
        dialogView.findViewById<TextView>(R.id.audiofilenamepreview)?.text = simpleFileName
        playButton = dialogView.findViewById(R.id.btnPlayAudioPreview)

        val btnDownload = dialogView.findViewById<Button>(R.id.btnDownload)

        updateAudioDialogPreview(audioUri)

        btnDownload.setOnClickListener {
            downloadAudio(audioUri, simpleFileName)
        }

        dialogView.findViewById<Button>(R.id.btnClose).setOnClickListener {
            pauseIfPlaying()
            dialog.dismiss()
            showLoading(false)
        }
    }


    private fun downloadAudio(uri: Uri, fileName: String) {
        val file = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)

        // Cek jika file sudah ada, hapus dulu
        if (file.exists()) {
            file.delete()
        }

        val request = DownloadManager.Request(uri).apply {
            setTitle("Hasil Attack Audio")
            setDescription("Mengunduh file hasil Attacking...")
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
            val simpleFileName = resultFileName.substringAfterLast("/") // Ambil setelah '/'
            showResultDialog(simpleFileName, resultAudioUri!!)//resultPSNR, )
        }
    }
}
