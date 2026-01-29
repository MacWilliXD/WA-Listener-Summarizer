package com.example.whatsappsummary.ui.fragment

import android.app.DatePickerDialog
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
import java.text.SimpleDateFormat
import java.util.*

class SummariesFragment : Fragment() {
    
    private var _binding: FragmentSummariesBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: ChatDetailViewModel by activityViewModels()
    private lateinit var adapter: SummaryAdapter
    private var chatId: String? = null
    
    private var startDate: String? = null
    private var endDate: String? = null

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
        setupDateFilter()
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
            
            if (summaries.isEmpty()) {
                binding.textViewEmpty.visibility = View.VISIBLE
                binding.recyclerViewSummaries.visibility = View.GONE
            } else {
                binding.textViewEmpty.visibility = View.GONE
                binding.recyclerViewSummaries.visibility = View.VISIBLE
            }
        }
    }

    private fun setupDateFilter() {
        binding.buttonStartDate.setOnClickListener {
            showDatePicker { date ->
                startDate = date
                binding.buttonStartDate.text = "Desde: $date"
                applyFilter()
            }
        }
        
        binding.buttonEndDate.setOnClickListener {
            showDatePicker { date ->
                endDate = date
                binding.buttonEndDate.text = "Hasta: $date"
                applyFilter()
            }
        }
        
        binding.buttonClearFilter.setOnClickListener {
            startDate = null
            endDate = null
            binding.buttonStartDate.text = "Fecha inicio"
            binding.buttonEndDate.text = "Fecha fin"
            // Recargar todos los resúmenes
            chatId?.let { viewModel.loadChatData(it) }
        }
    }

    private fun showDatePicker(onDateSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                onDateSelected(dateFormat.format(calendar.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun applyFilter() {
        if (startDate != null && endDate != null) {
            chatId?.let { id ->
                viewModel.loadSummariesByDateRange(id, startDate!!, endDate!!)
            }
        } else if (startDate != null || endDate != null) {
            Toast.makeText(requireContext(), "Selecciona ambas fechas", Toast.LENGTH_SHORT).show()
        }
    }

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
