package audiopage

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.prototypeta.R

class AudioFragment : Fragment(R.layout.fragment_audio) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up back button navigation
        view.findViewById<View>(R.id.ivBack).setOnClickListener {
            findNavController().navigateUp()
        }

        // Navigasi ke EmbeddingAudioFragment
        view.findViewById<View>(R.id.cardEmbeddingaudio).setOnClickListener {
            val startTime = System.currentTimeMillis()
            Log.d("PerfAudioFragment", "Embedding Audio Fragment navigation started at: $startTime")

            findNavController().navigate(R.id.action_audioFragment_to_embeddingaudioFragment)

            // Log completion time when fragment navigation finishes (optional: handle in the destination fragment)
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            Log.d("PerfAudioFragment", "Navigation to EmbeddingAudioFragment completed at: $endTime, Duration: $duration ms")
        }

        // Navigasi ke ExtractionAudioFragment
        view.findViewById<View>(R.id.cardExtractionaudio).setOnClickListener {
            val startTime = System.currentTimeMillis()
            Log.d("PerfAudioFragment", "Extraction Audio Fragment navigation started at: $startTime")

            findNavController().navigate(R.id.action_audioFragment_to_extractionaudioFragment)

            // Log completion time when fragment navigation finishes (optional: handle in the destination fragment)
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            Log.d("PerfAudioFragment", "Navigation to ExtractionAudioFragment completed at: $endTime, Duration: $duration ms")
        }

        // Navigasi ke AttackingAudioFragment
        view.findViewById<View>(R.id.cardAttacking).setOnClickListener {
            val startTime = System.currentTimeMillis()
            Log.d("PerfAudioFragment", "Attacking Audio Fragment navigation started at: $startTime")

            findNavController().navigate(R.id.action_audioFragment_to_attackingaudioFragment)

            // Log completion time when fragment navigation finishes (optional: handle in the destination fragment)
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            Log.d("PerfAudioFragment", "Navigation to AttackingAudioFragment completed at: $endTime, Duration: $duration ms")
        }
    }
}

