package com.example.detector1

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.detector1.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val PERMISSION_REQUEST_CODE = 1001
    private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
        )
    } else {
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Solicitar permisos al iniciar
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE)
        }
        
        // Configurar descripción
        binding.descriptionTextView.text = "Aplicación para reconocer señas de lengua de signos utilizando la cámara y técnicas de visión artificial."
        
        // Ocultar botones que no se usan en la versión pública
        binding.trainModelButton.visibility = android.view.View.GONE
        binding.exportDataButton.visibility = android.view.View.GONE
        
        // Configurar botones
        binding.recordGestureButton.setOnClickListener {
            if (allPermissionsGranted()) {
                startActivity(Intent(this, RecordGestureActivity::class.java))
            } else {
                requestPermissions()
            }
        }
        
        binding.evaluateGestureButton.setOnClickListener {
            if (allPermissionsGranted()) {
                startActivity(Intent(this, EvaluateGestureActivity::class.java))
            } else {
                requestPermissions()
            }
        }
        
        binding.listGesturesButton.setOnClickListener {
            startActivity(Intent(this, ListGesturesActivity::class.java))
        }
        
        binding.transcriptionButton.setOnClickListener {
            if (allPermissionsGranted()) {
                startActivity(Intent(this, TranscriptionActivity::class.java))
            } else {
                requestPermissions()
            }
        }
        
        binding.listTranscriptionsButton.setOnClickListener {
            startActivity(Intent(this, ListTranscriptionsActivity::class.java))
        }
        
        binding.instructionsButton.setOnClickListener {
            startActivity(Intent(this, InstructionsActivity::class.java))
        }
    }
    
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            REQUIRED_PERMISSIONS,
            PERMISSION_REQUEST_CODE
        )
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (allPermissionsGranted()) {
                // Permisos concedidos, no hacemos nada ya que los botones manejarán las acciones
            } else {
                Toast.makeText(
                    this,
                    "Se requieren permisos para usar esta aplicación",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}