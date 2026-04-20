package com.kinetic.fitness.ui.history

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.recyclerview.widget.LinearLayoutManager
import com.kinetic.fitness.databinding.FragmentHistoryBinding
import com.kinetic.fitness.data.api.RetrofitClient
import com.kinetic.fitness.data.models.Session
import kotlinx.coroutines.*

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val sessions = MutableLiveData<List<Session>>(emptyList())
    private val loading = MutableLiveData(false)
    private val error = MutableLiveData<String>()

    private lateinit var adapter: SessionAdapter

    private var api: com.kinetic.fitness.data.api.ApiService? = null
    private var page = 1
    private var isLoading = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        api = RetrofitClient.getInstance(requireContext())

        adapter = SessionAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = adapter

        observeData()
        loadSessions()

        binding.swipeRefresh.setOnRefreshListener {
            page = 1
            sessions.value = emptyList()
            loadSessions()
        }
    }

    private fun observeData() {
        sessions.observe(viewLifecycleOwner) {
            adapter.submitList(it)
        }

        loading.observe(viewLifecycleOwner) {
            binding.swipeRefresh.isRefreshing = it
        }

        error.observe(viewLifecycleOwner) {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSessions() {
        if (isLoading) return

        isLoading = true
        loading.value = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val res = api!!.getSessions(page)

                withContext(Dispatchers.Main) {
                    if (res.isSuccessful) {
                        val newList = sessions.value!! + (res.body()?.data ?: emptyList())
                        sessions.value = newList
                        page++
                    } else {
                        error.value = "Load thất bại"
                    }
                    loading.value = false
                    isLoading = false
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error.value = e.message
                    loading.value = false
                    isLoading = false
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}