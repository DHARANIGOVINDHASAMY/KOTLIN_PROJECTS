package com.zeddigital.applicationnew



//noinspection SuspiciousImport
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.zeddigital.applicationnew.databinding.FragmentHandlerBinding

class HandlerFragment : Fragment() {
    private var binding: FragmentHandlerBinding?=null


    private lateinit var handler: Handler
    private lateinit var runnable: Runnable

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHandlerBinding.inflate(inflater, container, false)

        super.onCreate(savedInstanceState)

        handler = Handler(Looper.getMainLooper())
        runnable = object : Runnable {
            override fun run() {
                // Your code logic goes here
                Log.d("TAG001", "DHARANI")


                // Call the handler again after 10 seconds
                handler.postDelayed(this, 10000)
            }
        }

        // Start the handler initially
        handler.postDelayed(runnable, 10000)




        return binding!!.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        handler.removeCallbacks(runnable) // to remove any pending callbacks associated with the runnable from the handler.
        //to cancel any pending callbacks and ensure proper cleanup when the view associated with the fragment is destroyed.
    }




}

