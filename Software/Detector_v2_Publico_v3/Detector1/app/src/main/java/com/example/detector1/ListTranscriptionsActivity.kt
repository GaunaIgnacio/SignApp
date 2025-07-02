package com.example.detector1

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.detector1.databinding.ActivityListTranscriptionsBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ListTranscriptionsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityListTranscriptionsBinding
    private lateinit var adapter: TranscriptionAdapter
    private var transcriptionFiles = mutableListOf<File>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListTranscriptionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupRecyclerView()
        loadTranscriptionFiles()
    }
    
    private fun setupRecyclerView() {
        adapter = TranscriptionAdapter(transcriptionFiles, 
            onViewClick = { file -> 
                openTranscription(file, false)
            },
            onEditClick = { file ->
                openTranscription(file, true)
            },
            onDeleteClick = { file, position -> 
                confirmDeleteFile(file, position)
            }
        )
        
        binding.transcriptionsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.transcriptionsRecyclerView.adapter = adapter
    }
    
    private fun openTranscription(file: File, startEditMode: Boolean) {
        val intent = Intent(this, ViewTranscriptionActivity::class.java)
        intent.putExtra("file_path", file.absolutePath)
        intent.putExtra("start_edit_mode", startEditMode)
        startActivity(intent)
    }
    
    private fun loadTranscriptionFiles() {
        val transcriptionDir = getExternalFilesDir(null)
        val files = transcriptionDir?.listFiles { file -> 
            file.isFile && file.name.endsWith(".txt")
        }
        
        if (files == null || files.isEmpty()) {
            binding.emptyListTextView.visibility = View.VISIBLE
            binding.transcriptionsRecyclerView.visibility = View.GONE
        } else {
            binding.emptyListTextView.visibility = View.GONE
            binding.transcriptionsRecyclerView.visibility = View.VISIBLE
            
            transcriptionFiles.clear()
            transcriptionFiles.addAll(files.sortedByDescending { it.lastModified() })
            adapter.notifyDataSetChanged()
        }
    }
    
    private fun confirmDeleteFile(file: File, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar transcripción")
            .setMessage("¿Estás seguro de que deseas eliminar '${file.name}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                if (file.delete()) {
                    transcriptionFiles.removeAt(position)
                    adapter.notifyItemRemoved(position)
                    
                    if (transcriptionFiles.isEmpty()) {
                        binding.emptyListTextView.visibility = View.VISIBLE
                        binding.transcriptionsRecyclerView.visibility = View.GONE
                    }
                    
                    Toast.makeText(this, "Transcripción eliminada", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "No se pudo eliminar el archivo", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    override fun onResume() {
        super.onResume()
        loadTranscriptionFiles() // Recargar archivos por si hay cambios
    }
    
    // Adaptador para la RecyclerView
    inner class TranscriptionAdapter(
        private val files: List<File>,
        private val onViewClick: (File) -> Unit,
        private val onEditClick: (File) -> Unit,
        private val onDeleteClick: (File, Int) -> Unit
    ) : RecyclerView.Adapter<TranscriptionAdapter.ViewHolder>() {
        
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val fileNameTextView: TextView = view.findViewById(R.id.fileNameTextView)
            val fileDateTextView: TextView = view.findViewById(R.id.fileDateTextView)
            val viewButton: Button = view.findViewById(R.id.viewButton)
            val editButton: Button = view.findViewById(R.id.editButton)
            val deleteButton: Button = view.findViewById(R.id.deleteButton)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_transcription, parent, false)
            return ViewHolder(view)
        }
        
        override fun getItemCount() = files.size
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = files[position]
            
            // Mostrar nombre sin la extensión
            val displayName = file.name.substringBeforeLast(".")
            holder.fileNameTextView.text = displayName
            
            // Formatear la fecha de modificación
            val lastModified = Date(file.lastModified())
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            holder.fileDateTextView.text = dateFormat.format(lastModified)
            
            // Configurar botones
            holder.viewButton.setOnClickListener { onViewClick(file) }
            holder.editButton.setOnClickListener { onEditClick(file) }
            holder.deleteButton.setOnClickListener { onDeleteClick(file, position) }
        }
    }
} 