package com.example.whatsappsummary.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.whatsappsummary.databinding.FragmentSummariesBinding
import com.example.whatsappsummary.ui.adapter.SummaryAdapter
import com.example.whatsappsummary.viewmodel.ChatDetailViewModel
import java.util.*

class SummariesFragment : Fragment() {
    
    private var _binding: FragmentSummariesBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: ChatDetailViewModel by activityViewModels()
    private lateinit var adapter: SummaryAdapter
    private var chatId: String? = null
    
    

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatId = arguments?.getString(ARG_CHAT_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSummariesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupObservers()
        setupControls()
    }

    private fun setupRecyclerView() {
        adapter = SummaryAdapter { summary ->
            // Click en resumen para expandir
        }
        
        binding.recyclerViewSummaries.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SummariesFragment.adapter
        }
    }

    private fun setupObservers() {
        viewModel.summaries.observe(viewLifecycleOwner) { summaries ->
            adapter.submitList(summaries)
            // ensure summaries show newest first and start at top
            binding.recyclerViewSummaries.post {
                if (summaries.isNotEmpty()) binding.recyclerViewSummaries.scrollToPosition(0)
            }

            if (summaries.isEmpty()) {
                binding.emptyContainer.visibility = View.VISIBLE
                binding.recyclerViewSummaries.visibility = View.GONE
            } else {
                binding.emptyContainer.visibility = View.GONE
                binding.recyclerViewSummaries.visibility = View.VISIBLE
            }
        }
        viewModel.isGenerating.observe(viewLifecycleOwner) { generating ->
            binding.progressGenerating.visibility = if (generating) View.VISIBLE else View.GONE
            binding.buttonGenerateSummary.isEnabled = !generating
        }
        viewModel.generationError.observe(viewLifecycleOwner) { err ->
            err?.let {
                Toast.makeText(requireContext(), "Error generando resumen: $it", Toast.LENGTH_LONG).show()
                viewModel.clearGenerationError()
            }
        }
    }

    private fun setupControls() {
        val prefs = requireContext().getSharedPreferences("whatsapp_prefs", 0)
        val key = "auto_summaries_${chatId ?: "global"}"
        val isAuto = prefs.getBoolean(key, false)
        binding.switchAutoSummaries.isChecked = isAuto

        binding.switchAutoSummaries.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(key, isChecked).apply()
            Toast.makeText(requireContext(), if (isChecked) "Auto activado" else "Auto desactivado", Toast.LENGTH_SHORT).show()
        }

        binding.buttonGenerateSummary.setOnClickListener {
            // Show options dialog to collect optional length/detail/prompt
            val optsView = layoutInflater.inflate(com.example.whatsappsummary.R.layout.dialog_summarize_options, null)
            val editLength = optsView.findViewById<android.widget.EditText>(com.example.whatsappsummary.R.id.editSummaryLength)
            val spinner = optsView.findViewById<android.widget.Spinner>(com.example.whatsappsummary.R.id.spinnerDetailLevel)
            val editExtra = optsView.findViewById<android.widget.EditText>(com.example.whatsappsummary.R.id.editExtraPrompt)
            val options = listOf("Resumido", "Intermedio", "Detallado")
            val spAdapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, options)
            spAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = spAdapter

            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Opciones de resumen")
                .setView(optsView)
                .setPositiveButton("Generar") { _, _ ->
                    // collect optional inputs from dialog
                    val length = editLength.text?.toString()?.trim()?.toIntOrNull()
                    val detail = spinner.selectedItem as? String ?: "Intermedio"
                    val extra = editExtra.text?.toString()?.takeIf { it.isNotBlank() }

                    // trigger manual generation via ViewModel with options
                    viewModel.generateManualSummary(length, detail, extra)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    // Date filtering is handled at activity level (ChatDetailActivity)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CHAT_ID = "chat_id"
        
        fun newInstance(chatId: String): SummariesFragment {
            return SummariesFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CHAT_ID, chatId)
                }
            }
        }
    }
}
