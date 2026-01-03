package com.neubofy.reality.ui.fragments.installation

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.neubofy.reality.R
import com.neubofy.reality.databinding.FragmentWelcomeBinding

class WelcomeFragment : Fragment() {
    companion object {
        const val FRAGMENT_ID = "welcome_fragment"
    }

    private var _binding: FragmentWelcomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWelcomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.btnGetStarted.setOnClickListener {
            val name = binding.etUserName.text.toString().trim()
            if (name.isNotEmpty()) {
                val prefs = requireContext().getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
                prefs.edit().putString("user_name", name).apply()
            }
            
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_holder, PermissionsFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}