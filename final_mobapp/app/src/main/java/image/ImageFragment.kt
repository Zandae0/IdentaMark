package imagepage

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.prototypeta.R

class ImageFragment : Fragment(R.layout.fragment_image) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up back button navigation
        view.findViewById<View>(R.id.ivBack).setOnClickListener {
            findNavController().navigateUp()
        }

        // Set up click listeners for navigating to other fragments
        view.findViewById<View>(R.id.cardEmbedding).setOnClickListener {
            val startTime = System.currentTimeMillis()
            Log.d("PerfImageFragment", "Embedding Fragment navigation started at: $startTime")

            findNavController().navigate(R.id.action_imageFragment_to_embeddingimageFragment)

            // Log completion time when fragment navigation finishes (optional: handle in the destination fragment)
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            Log.d("PerfImageFragment", "Navigation to EmbeddingFragment completed at: $endTime, Duration: $duration ms")
        }

        view.findViewById<View>(R.id.cardExtraction).setOnClickListener {
            val startTime = System.currentTimeMillis()
            Log.d("PerfImageFragment", "Extraction Fragment navigation started at: $startTime")

            findNavController().navigate(R.id.action_imageFragment_to_extractionimageFragment)

            // Log completion time when fragment navigation finishes (optional: handle in the destination fragment)
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            Log.d("PerfImageFragment", "Navigation to ExtractionFragment completed at: $endTime, Duration: $duration ms")
        }

        view.findViewById<View>(R.id.cardAttacking).setOnClickListener {
            val startTime = System.currentTimeMillis()
            Log.d("PerfImageFragment", "Attacking Fragment navigation started at: $startTime")

            findNavController().navigate(R.id.action_imageFragment_to_attackingimageFragment)

            // Log completion time when fragment navigation finishes (optional: handle in the destination fragment)
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            Log.d("PerfImageFragment", "Navigation to AttackingFragment completed at: $endTime, Duration: $duration ms")
        }
    }
}

