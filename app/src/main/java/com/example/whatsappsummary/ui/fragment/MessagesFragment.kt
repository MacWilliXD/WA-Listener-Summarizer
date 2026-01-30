package com.example.whatsappsummary.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.whatsappsummary.databinding.FragmentMessagesBinding
import com.example.whatsappsummary.ui.adapter.MessageAdapter
import com.example.whatsappsummary.viewmodel.ChatDetailViewModel

class MessagesFragment : Fragment() {
    
    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: ChatDetailViewModel by activityViewModels()
    private lateinit var adapter: MessageAdapter
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
        _binding = FragmentMessagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupObservers()
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter()
        
        binding.recyclerViewMessages.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                // show items stacked from end so newest messages appear at the bottom
                reverseLayout = false
                stackFromEnd = true
            }
            adapter = this@MessagesFragment.adapter
        }
    }

    private fun setupObservers() {
        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            // Ensure messages are in chronological order (oldest -> newest)
            val ordered = messages.sortedBy { it.timestamp }
            adapter.submitList(ordered)
            // Scroll to the last (most recent) message so view shows bottom initially
            binding.recyclerViewMessages.post {
                if (ordered.isNotEmpty()) binding.recyclerViewMessages.scrollToPosition(ordered.size - 1)
            }

            if (messages.isEmpty()) {
                binding.textViewEmpty.visibility = View.VISIBLE
                binding.recyclerViewMessages.visibility = View.GONE
            } else {
                binding.textViewEmpty.visibility = View.GONE
                binding.recyclerViewMessages.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CHAT_ID = "chat_id"
        
        fun newInstance(chatId: String): MessagesFragment {
            return MessagesFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CHAT_ID, chatId)
                }
            }
        }
    }
}
