package com.example.detector1

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.detector1.databinding.ActivityTranscriptionBinding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TranscriptionActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var binding: ActivityTranscriptionBinding
    private lateinit var speechRecognizer: SpeechRecognizer
    private var isRecording = false
    private var isCameraMode = false
    private var selectedMicrophone: String = "INTERNAL" // "BLUETOOTH" o "INTERNAL"
    
    // Lista de dispositivos Bluetooth disponibles (se actualiza dinámicamente)
    private val bluetoothDevices = mutableListOf<BluetoothDevice>()
    
    // Variables para Bluetooth
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var audioManager: AudioManager
    private var bluetoothHeadset: BluetoothHeadset? = null
    private var connectedBluetoothDevice: BluetoothDevice? = null
    
    // Variables para la cámara
    private var cameraDevice: CameraDevice? = null
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraId: String
    private lateinit var backgroundHandler: Handler
    private lateinit var backgroundThread: HandlerThread
    
    private val RECORD_AUDIO_PERMISSION_CODE = 1
    private val CAMERA_PERMISSION_CODE = 2
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    private var currentPartialText = ""
    private var finalizedText = ""
    private val MAX_SUBTITLE_WORDS = 20
    
    // Receptor para eventos Bluetooth
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothDevice.ACTION_ACL_CONNECTED == action) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (device != null) {
                    connectedBluetoothDevice = device
                    
                    // Añadir el dispositivo a la lista si no está ya
                    if (!bluetoothDevices.contains(device)) {
                        bluetoothDevices.add(device)
                    }
                    
                    Toast.makeText(
                        context,
                        "Micrófono Bluetooth conectado: ${device.name ?: "Dispositivo desconocido"}",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // Actualizar el estado del micrófono en la interfaz
                    updateMicrophoneStatus()
                }
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED == action) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                
                // Eliminar el dispositivo de la lista
                if (device != null) {
                    bluetoothDevices.remove(device)
                }
                
                // Si el dispositivo desconectado era el que estábamos usando, cambiar al micrófono interno
                if (device == connectedBluetoothDevice) {
                    connectedBluetoothDevice = null
                    if (selectedMicrophone == "BLUETOOTH") {
                        switchToInternalMicrophone()
                    }
                }
                
                Toast.makeText(context, "Micrófono Bluetooth desconectado", Toast.LENGTH_SHORT).show()
                
                // Actualizar el estado del micrófono en la interfaz
                updateMicrophoneStatus()
            }
        }
    }
    
    // Listener para conexión de perfil Bluetooth
    private val bluetoothProfileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = proxy as BluetoothHeadset
                
                // Verificar si hay dispositivos conectados
                val connectedDevices = bluetoothHeadset?.connectedDevices
                if (!connectedDevices.isNullOrEmpty()) {
                    // Actualizar la lista de dispositivos Bluetooth
                    bluetoothDevices.clear()
                    bluetoothDevices.addAll(connectedDevices)
                    
                    // Establecer el primer dispositivo como conectado
                    connectedBluetoothDevice = connectedDevices[0]
                    Toast.makeText(
                        this@TranscriptionActivity,
                        "Micrófono Bluetooth detectado: ${connectedBluetoothDevice?.name ?: "Dispositivo desconocido"}",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // Actualizar el estado del micrófono en la interfaz
                    updateMicrophoneStatus()
                }
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = null
                connectedBluetoothDevice = null
                bluetoothDevices.clear()
                
                // Actualizar el estado del micrófono en la interfaz
                updateMicrophoneStatus()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTranscriptionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Inicializar administrador de audio
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // Configurar botón de menú para selección de micrófono
        binding.menuButton.setOnClickListener {
            showMicrophoneOptions()
        }
        
        // Inicializar Bluetooth

        try {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null) {
                Toast.makeText(this, "Bluetooth no disponible", Toast.LENGTH_SHORT).show()
                // Ya no hay botón de Bluetooth que deshabilitar
            } else {
                // Verificar si el Bluetooth está activo ANTES de registrar receptores
                if (bluetoothAdapter.isEnabled) {
                    val filter = IntentFilter().apply {
                        addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                        addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                    }
                    registerReceiver(bluetoothReceiver, filter)
                    bluetoothAdapter.getProfileProxy(this, bluetoothProfileListener, BluetoothProfile.HEADSET)
                } else {
                    // Ya no hay botón de Bluetooth que deshabilitar
                }
            }
        } catch (e: SecurityException) { // <--- Capturar error de permisos
            Toast.makeText(this, "Permiso Bluetooth requerido", Toast.LENGTH_SHORT).show()
            requestPermissions()
        }
        
        // Inicializar reconocedor de voz
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        
        // Configurar reconocedor
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                binding.statusTextView.text = "Escuchando..."
            }

            override fun onBeginningOfSpeech() {}
            
            override fun onRmsChanged(rmsdB: Float) {}
            
            override fun onBufferReceived(buffer: ByteArray?) {}
            
            override fun onEndOfSpeech() {
                // No detener la grabación, reiniciar automáticamente para continuar escuchando
                if (isRecording) {
                    // Reiniciar el reconocimiento de voz para continuar escuchando
                    restartSpeechRecognition()
                }
            }
            
            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Error de audio"
                    SpeechRecognizer.ERROR_CLIENT -> "Error del cliente"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permisos insuficientes"
                    SpeechRecognizer.ERROR_NETWORK -> "Error de red"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tiempo de espera agotado"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No se encontró coincidencia"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconocedor ocupado"
                    SpeechRecognizer.ERROR_SERVER -> "Error del servidor"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se detectó habla"
                    else -> "Error desconocido"
                }
                binding.statusTextView.text = errorMessage
                
                // En lugar de detener la grabación, reintentar si seguimos en modo grabación
                // y si el error es de tipo que permite reintento
                if (isRecording && (error == SpeechRecognizer.ERROR_NO_MATCH || 
                                   error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                    // Reiniciar el reconocimiento de voz para continuar escuchando
                    restartSpeechRecognition()
                } else if (isRecording) {
                    // Para otros errores más graves, esperar un momento y reintentar
                    Handler(mainLooper).postDelayed({
                        if (isRecording) {
                            restartSpeechRecognition()
                        }
                    }, 1000) // Esperar 1 segundo antes de reintentar
                }
            }
            
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0]
                    
                    // Añadir el texto finalizado
                    if (finalizedText.isEmpty()) {
                        finalizedText = recognizedText
                    } else {
                        finalizedText = "$finalizedText $recognizedText"
                    }
                    
                    // Actualizar la vista con el texto completo
                    binding.transcriptionTextView.text = finalizedText
                    
                    // Actualizar subtítulos si estamos en modo cámara
                    if (isCameraMode) {
                        updateSubtitles(finalizedText)
                    }
                    
                    currentPartialText = ""
                }
                binding.statusTextView.text = "Presiona el botón para continuar grabando"
            }
            
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    currentPartialText = matches[0]
                    
                    // Mostrar el texto finalizado + el parcial actual
                    val displayText = if (finalizedText.isEmpty()) {
                        currentPartialText
                    } else {
                        "$finalizedText $currentPartialText"
                    }
                    
                    binding.transcriptionTextView.text = displayText
                    
                    // Actualizar subtítulos si estamos en modo cámara
                    if (isCameraMode) {
                        updateSubtitles(displayText)
                    }
                }
                binding.statusTextView.text = "Escuchando..."
            }
            
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        
        // Inicializar cámara
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        
        // Configurar botón de grabación
        binding.recordButton.setOnClickListener {
            if (allPermissionsGranted()) {
                toggleRecording()
            } else {
                requestPermissions()
            }
        }
        
        // Ya no hay botón de pausa, la funcionalidad se maneja desde el botón de grabación
        
        // Configurar botón para limpiar transcripción
        binding.clearButton.setOnClickListener {
            binding.transcriptionTextView.text = ""
            binding.subtitlesTextView.text = ""
            finalizedText = ""
            currentPartialText = ""
            binding.statusTextView.text = "Transcripción borrada"
        }
        
        // Configurar botón para guardar transcripción
        binding.saveButton.setOnClickListener {
            showSaveDialog()
        }
        
        // Mover el botón de cámara a la posición del botón de Bluetooth
        binding.cameraButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                toggleCameraMode()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    CAMERA_PERMISSION_CODE
                )
            }
        }
        
        // Configurar preview de cámara
        binding.cameraPreview.holder.addCallback(this)
        
        // Inicializar el estado del micrófono
        updateMicrophoneStatus()
    }
    
    private fun toggleRecording() {
        if (isRecording) {
            // Detener grabación
            speechRecognizer.stopListening()
            binding.recordButton.setImageResource(android.R.drawable.ic_btn_speak_now)
            // Ya no hay botón de pausa
            isRecording = false
        } else {
            // Iniciar grabación continua
            startSpeechRecognition()
        }
    }
    
    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        
        // Configurar el audio según el micrófono seleccionado
        if (selectedMicrophone == "BLUETOOTH" && connectedBluetoothDevice != null) {
            // Asegurar que el audio se capture desde el dispositivo Bluetooth
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
            audioManager.isSpeakerphoneOn = false
            
            // Pequeña pausa para que el sistema configure el audio Bluetooth
            Thread.sleep(500) // Reducido para mejor experiencia de usuario
        }
        
        try {
            speechRecognizer.startListening(intent)
            binding.recordButton.setImageResource(android.R.drawable.ic_media_pause)
            isRecording = true
        } catch (e: Exception) {
            Toast.makeText(this, "Error al iniciar el reconocimiento de voz: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun restartSpeechRecognition() {
        // Detener la escucha actual
        speechRecognizer.stopListening()
        
        // Pequeña pausa para asegurar que el reconocedor se reinicie correctamente
        Handler(mainLooper).postDelayed({
            if (isRecording) {
                // Iniciar una nueva sesión de reconocimiento
                startSpeechRecognition()
                binding.statusTextView.text = "Escuchando..."
            }
        }, 300) // Esperar 300ms antes de reiniciar
    }
    
    private fun toggleCameraMode() {
        isCameraMode = !isCameraMode
        
        if (isCameraMode) {
            // Cambiar a vista de cámara
            binding.viewSwitcher.showNext()
            startCameraPreview()
            // Actualizar subtítulos con el texto actual
            updateSubtitles(binding.transcriptionTextView.text.toString())
        } else {
            // Volver a vista normal
            binding.viewSwitcher.showPrevious()
            stopCameraPreview()
        }
    }
    
    // Método para mostrar el diálogo de opciones de micrófono
    private fun showMicrophoneOptions() {
        val options = arrayOf("Micrófono Interno", "Micrófono Bluetooth")
        AlertDialog.Builder(this)
            .setTitle("Seleccionar Micrófono")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> switchToInternalMicrophone()
                    1 -> switchToBluetoothMicrophone()
                }
            }
            .show()
    }
    
    // Método para cambiar al micrófono Bluetooth
    private fun switchToBluetoothMicrophone() {
        selectedMicrophone = "BLUETOOTH"
        updateMicrophoneStatus()
        if (isRecording) {
            restartSpeechRecognition()
        }
    }
    
    // Método para cambiar al micrófono interno
    private fun switchToInternalMicrophone() {
        selectedMicrophone = "INTERNAL"
        updateMicrophoneStatus()
        if (isRecording) {
            restartSpeechRecognition()
        }
    }
    
    // Método para actualizar el estado del micrófono en la interfaz
    private fun updateMicrophoneStatus() {
        val statusText = when {
            selectedMicrophone == "BLUETOOTH" && connectedBluetoothDevice != null -> 
                "Micrófono Bluetooth: ${connectedBluetoothDevice?.name ?: "Dispositivo desconocido"}"
            selectedMicrophone == "BLUETOOTH" && connectedBluetoothDevice == null -> 
                "Micrófono Bluetooth: No conectado"
            else -> "Micrófono Interno"
        }
        binding.microphoneStatusTextView.text = statusText
    }
    
    private fun updateSubtitles(text: String) {
        currentPartialText = text
        val words = text.split(" ")
        val startIndex = maxOf(0, words.size - MAX_SUBTITLE_WORDS)
        val subtitleText = words.subList(startIndex, words.size).joinToString(" ")
        binding.subtitlesTextView.text = subtitleText
    }
    
    private fun startCameraPreview() {
        if (!allPermissionsGranted()) {
            requestPermissions()
            return
        }
        backgroundThread = HandlerThread("CameraBackground").apply { start() }
        backgroundHandler = Handler(backgroundThread.looper)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = cameraManager.cameraIdList[0]
        openCamera()
    }
    
    private fun stopCameraPreview() {
        cameraDevice?.close()
        backgroundThread.quitSafely()
    }
    
    private fun openCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        cameraManager.openCamera(cameraId, cameraStateCallback, backgroundHandler)
    }
    
    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createCameraPreviewSession()
        }
        
        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            cameraDevice = null
        }
        
        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            cameraDevice = null
        }
    }
    
    private fun createCameraPreviewSession() {
        val surface = binding.cameraPreview.holder.surface
        val captureRequest = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequest?.addTarget(surface)
        
        cameraDevice?.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                session.setRepeatingRequest(captureRequest?.build()!!, null, backgroundHandler)
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                Toast.makeText(this@TranscriptionActivity, "Error al configurar la cámara", Toast.LENGTH_SHORT).show()
            }
        }, backgroundHandler)
    }
    
    private fun allPermissionsGranted(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, RECORD_AUDIO_PERMISSION_CODE)
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            RECORD_AUDIO_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (isCameraMode) {
                        startCameraPreview()
                    }
                } else {
                    Toast.makeText(this, "Se requieren permisos para grabar audio", Toast.LENGTH_SHORT).show()
                }
            }
            CAMERA_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (isCameraMode) {
                        startCameraPreview()
                    }
                } else {
                    Toast.makeText(this, "Se requieren permisos para usar la cámara", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Detener grabación y cámara al pausar la actividad
        if (isRecording) {
            toggleRecording()
        }
        if (isCameraMode) {
            stopCameraPreview()
        }
        
        // Si estábamos usando el micrófono Bluetooth, detenerlo
        if (connectedBluetoothDevice != null) {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Reiniciar cámara si estábamos en modo cámara
        if (isCameraMode) {
            startCameraPreview()
        }
        
        // Reactivar el micrófono Bluetooth si estaba conectado
        if (connectedBluetoothDevice != null) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
            audioManager.isSpeakerphoneOn = false
        }
    }
    
    override fun onBackPressed() {
        try {
            // Detener grabación si está activa
            if (isRecording) {
                speechRecognizer.stopListening()
                isRecording = false
            }
            
            // Detener preview de cámara si está activo
            if (isCameraMode) {
                stopCameraPreview()
                isCameraMode = false
            }
            
            // Detener el uso de Bluetooth SCO
            if (connectedBluetoothDevice != null) {
                try {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                    audioManager.mode = AudioManager.MODE_NORMAL
                } catch (e: Exception) {
                    // Ignorar errores al detener Bluetooth SCO
                }
            }
            
            // Liberar recursos del reconocedor de voz
            try {
                speechRecognizer.destroy()
            } catch (e: Exception) {
                // Ignorar errores al destruir el reconocedor
            }
            
            // Llamar al método de la clase base para finalizar la actividad
            super.onBackPressed()
        } catch (e: Exception) {
            // En caso de cualquier error, asegurar que la actividad se cierre correctamente
            finish()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()

        speechRecognizer.destroy()
        stopCameraPreview()
        
        // Detener el uso de Bluetooth SCO
        if (connectedBluetoothDevice != null) {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
        }
        
        // Desconectar perfil Bluetooth y desregistrar receptor
        if (::bluetoothAdapter.isInitialized) {
            try {
                bluetoothHeadset?.let { headset ->
                    bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, headset)
                }
                unregisterReceiver(bluetoothReceiver)
            } catch (e: Exception) {
                // Ignorar errores al desregistrar
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        // No es necesario hacer nada aquí
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // No es necesario hacer nada aquí
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // No es necesario hacer nada aquí
    }

    private fun showSaveDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_save_transcription, null)
        val editText = dialogView.findViewById<EditText>(R.id.filenameEditText)
        
        AlertDialog.Builder(this)
            .setTitle("Guardar Transcripción")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val filename = editText.text.toString()
                saveTranscription(filename)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun saveTranscription(filename: String) {
        try {
            val text = binding.transcriptionTextView.text.toString()
            if (text.isEmpty()) {
                Toast.makeText(this, "No hay texto para guardar", Toast.LENGTH_SHORT).show()
                return
            }

            val file = File(getExternalFilesDir(null), "$filename.txt")
            FileOutputStream(file).use { outputStream ->
                outputStream.write(text.toByteArray())
            }

            Toast.makeText(this, "Transcripción guardada en: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al guardar la transcripción: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}