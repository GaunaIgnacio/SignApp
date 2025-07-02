package com.example.detector1

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.detector1.databinding.ActivityInstructionsBinding

class InstructionsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityInstructionsBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInstructionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Configurar el texto de instrucciones
        binding.instructionsTextView.text = """
            Esta aplicación te permite recolectar y reconocer señas de lengua de signos utilizando la cámara y técnicas de visión artificial.
            
            1. Graba señas: Registra tus propias señas para añadirlas al modelo.
            2. Evalúa señas: Prueba el reconocimiento en tiempo real con la cámara.
            3. Transcripción: Usa el micrófono para transcribir voz a texto.
            4. Mis Transcripciones: Accede a tus transcripciones guardadas.
            
            Para utilizar la aplicación correctamente:
            
            - Asegúrate de conceder los permisos necesarios (cámara, micrófono, almacenamiento).
            - Al grabar señas, mantén tu mano visible dentro del recuadro de la cámara.
            - Para mejores resultados, graba en un ambiente bien iluminado.
            - Realiza los gestos de manera clara y consistente.
            - Las transcripciones se guardan automáticamente y puedes acceder a ellas desde la sección "Mis Transcripciones".
        """.trimIndent()
        
        // Configurar el botón de volver
        binding.backButton.setOnClickListener {
            finish()
        }
    }
}