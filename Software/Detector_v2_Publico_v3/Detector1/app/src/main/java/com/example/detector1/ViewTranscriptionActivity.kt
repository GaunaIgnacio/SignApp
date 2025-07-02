package com.example.detector1

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.detector1.databinding.ActivityViewTranscriptionBinding
import java.io.File

class ViewTranscriptionActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityViewTranscriptionBinding
    private lateinit var transcriptionFile: File
    private var isEditMode = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewTranscriptionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        val filePath = intent.getStringExtra("file_path")
        if (filePath.isNullOrEmpty()) {
            Toast.makeText(this, "Error: No se pudo abrir el archivo", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        transcriptionFile = File(filePath)
        if (!transcriptionFile.exists() || !transcriptionFile.canRead()) {
            Toast.makeText(this, "Error: No se puede leer el archivo", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Obtener el nombre sin extensión para el título
        val displayName = transcriptionFile.name.substringBeforeLast(".")
        binding.titleTextView.text = displayName
        
        // Cargar el contenido del archivo
        try {
            val content = transcriptionFile.readText()
            binding.contentTextView.setText(content)
        } catch (e: Exception) {
            Toast.makeText(this, "Error al leer el archivo: ${e.message}", Toast.LENGTH_SHORT).show()
            binding.contentTextView.setText("No se pudo cargar el contenido")
        }
        
        // Configurar botón de compartir
        binding.shareButton.setOnClickListener {
            shareTranscription()
        }
        
        // Configurar botón de editar
        binding.editButton.setOnClickListener {
            toggleEditMode()
        }
        
        // Configurar botón de guardar
        binding.saveButton.setOnClickListener {
            saveChanges()
        }
        
        // Verificar si debe iniciar en modo edición
        val startEditMode = intent.getBooleanExtra("start_edit_mode", false)
        if (startEditMode) {
            // Usar un pequeño retraso para asegurar que la UI ya está completamente inicializada
            Handler(Looper.getMainLooper()).postDelayed({
                toggleEditMode()
            }, 100)
        }
    }
    
    private fun toggleEditMode() {
        isEditMode = !isEditMode
        
        if (isEditMode) {
            // Activar modo edición
            binding.contentTextView.isEnabled = true
            binding.contentTextView.requestFocus()
            binding.contentTextView.setSelection(binding.contentTextView.text.length)
            
            // Mostrar botón guardar y ocultar otros botones
            binding.buttonLayout.visibility = View.GONE
            binding.saveButton.visibility = View.VISIBLE
        } else {
            // Desactivar modo edición
            binding.contentTextView.isEnabled = false
            
            // Ocultar botón guardar y mostrar otros botones
            binding.buttonLayout.visibility = View.VISIBLE
            binding.saveButton.visibility = View.GONE
        }
    }
    
    private fun saveChanges() {
        val newContent = binding.contentTextView.text.toString()
        
        // Confirmar guardar cambios
        AlertDialog.Builder(this)
            .setTitle("Guardar cambios")
            .setMessage("¿Estás seguro de que deseas guardar los cambios realizados?")
            .setPositiveButton("Guardar") { _, _ ->
                try {
                    transcriptionFile.writeText(newContent)
                    Toast.makeText(this, "Cambios guardados con éxito", Toast.LENGTH_SHORT).show()
                    toggleEditMode() // Salir del modo edición
                } catch (e: Exception) {
                    Toast.makeText(this, "Error al guardar cambios: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun shareTranscription() {
        try {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, transcriptionFile.name)
                putExtra(Intent.EXTRA_TEXT, binding.contentTextView.text.toString())
            }
            startActivity(Intent.createChooser(shareIntent, "Compartir transcripción"))
        } catch (e: Exception) {
            Toast.makeText(this, "Error al compartir: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onBackPressed() {
        if (isEditMode) {
            // Si estamos en modo edición, preguntar si desea guardar cambios
            AlertDialog.Builder(this)
                .setTitle("Guardar cambios")
                .setMessage("¿Quieres guardar los cambios antes de salir?")
                .setPositiveButton("Guardar") { _, _ ->
                    saveChanges()
                    super.onBackPressed()
                }
                .setNegativeButton("Descartar") { _, _ ->
                    super.onBackPressed()
                }
                .show()
        } else {
            super.onBackPressed()
        }
    }
} 