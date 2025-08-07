package dashboardpage

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.prototypeta.R

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Navigasi ke ImageFragment
        view.findViewById<View>(R.id.cardImage).setOnClickListener {
            val startTime = System.currentTimeMillis()
            Log.d("PerfDashboard", "Image Fragment navigation started at: $startTime")

            findNavController().navigate(R.id.action_dashboardFragment_to_imageFragment)

            // Optionally, log when navigation completes (on destination fragment's onViewCreated or onResume)
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            Log.d("PerfDashboard", "Image Fragment navigation completed at: $endTime, Duration: $duration ms")
        }


        // Navigasi ke AudioFragment
        view.findViewById<View>(R.id.cardAudio).setOnClickListener {
            val startTime = System.currentTimeMillis()
            Log.d("PerfDashboard", "Audio Fragment navigation started at: $startTime")
            findNavController().navigate(R.id.action_dashboardFragment_to_audioFragment)


            // Optionally, log when navigation completes (on destination fragment's onViewCreated or onResume)
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            Log.d("PerfDashboard", "Audio Fragment navigation completed at: $endTime, Duration: $duration ms")
        }
    }
}
