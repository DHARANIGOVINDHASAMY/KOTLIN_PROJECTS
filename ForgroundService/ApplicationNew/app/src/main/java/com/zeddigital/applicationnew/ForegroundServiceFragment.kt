package com.zeddigital.applicationnew

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.Intent
import android.widget.Button
import com.zeddigital.applicationnew.R;

import androidx.core.app.ServiceCompat.stopForeground
import androidx.core.content.ContextCompat
import com.zeddigital.applicationnew.databinding.FragmentForegroundServiceBinding


class ForegroundServiceFragment : Fragment() {
    private var binding: FragmentForegroundServiceBinding? = null
    private var isServiceRunning = false
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentForegroundServiceBinding.inflate(inflater, container, false)


        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
/*
        // Start the foreground service when the fragment is created
        startForegroundService()
        stopService()
        // Rest of your fragment code...*/


        val startButton = view.findViewById<Button>(R.id.start_button)
        val stopButton = view.findViewById<Button>(R.id.stop_button)

        startButton.setOnClickListener {
            startForegroundService()
        }

        stopButton.setOnClickListener {
            stopForegroundService()
        }

    }


  /*  private fun startForegroundService() {
      binding?.button?.setOnClickListener {
            val serviceIntent = Intent(requireContext(), MyForegroundService::class.java)
            serviceIntent.putExtra("notification", "301:: okk")
            ContextCompat.startForegroundService(requireContext(), serviceIntent)
        }
    }
    private fun stopService() {
        stopForeground(true)
        stopSelf()
    }*/

    private fun startForegroundService() {
        // responsible for starting the foreground service. It checks if the service is already running and starts it using ContextCompat.startForegroundService if it is not running.
        if (!isServiceRunning) {
            val intent = Intent(requireContext(), MyForegroundService::class.java)
            // Set the notification icon
            val notificationIcon = R.drawable.notification

            // Pass the notification icon as an extra to the foreground service
            intent.putExtra("notification_icon", notificationIcon)
            requireContext().startService(intent)

            isServiceRunning = true
        }
    }

    private fun stopForegroundService() {                 //  responsible for stopping the foreground service.
        if (isServiceRunning) {
            val intent = Intent(requireContext(), MyForegroundService::class.java)
            requireContext().stopService(intent)    //  if the service is running and stops it using requireContext().stopService if it is running.
            isServiceRunning = false
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        binding = null   //  to clean up the binding reference when the view is destroyed, preventing potential memory leaks.
    }
}
