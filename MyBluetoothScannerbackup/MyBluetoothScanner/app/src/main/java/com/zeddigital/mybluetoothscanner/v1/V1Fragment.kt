package com.zeddigital.mybluetoothscanner.v1

import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.zeddigital.mybluetoothscanner.R
import com.zeddigital.mybluetoothscanner.databinding.FragmentV1Binding

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"


class V1Fragment : Fragment() {
    private var binding: FragmentV1Binding?=null
    private var dialog: Dialog? = null
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentV1Binding.inflate(inflater, container, false)

     binding!!.myButton1.setOnClickListener {
         send301()
     }


        return binding!!.root
    }


        private fun send301() {

            val intent = Intent(requireContext(), AlertSOS::class.java)
            startActivity(intent)
            //startActivityForResult(intent, 444)

        }


    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }




}

fun PackageManager.missingSystemFeature(name: String): Boolean = !hasSystemFeature(name)
