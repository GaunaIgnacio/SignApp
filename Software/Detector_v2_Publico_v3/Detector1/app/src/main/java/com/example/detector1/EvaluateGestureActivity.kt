package com.example.detector1

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.detector1.databinding.ActivityEvaluateGestureBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.Locale

class EvaluateGestureActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    
    private lateinit var binding: ActivityEvaluateGestureBinding
    private lateinit var gestureRecognizerHelper: GestureRecognizerHelper
    private lateinit var cameraExecutor: ExecutorService
    private var isEvaluating = false
    
    // Selector de cámara
    private var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    
    // Para síntesis de voz
    private lateinit var textToSpeech: TextToSpeech
    private var ttsReady = false
    
    // Para mantener fija la seña detectada
    private var fixedGestureName: String? = null
    private val CONFIDENCE_THRESHOLD = 0.85f // Umbral de confianza para considerar detección fija
    private var lastDetectedGesture: String? = null
    
    private val TAG = "EvaluateGestureActivity"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEvaluateGestureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Inicializar TextToSpeech
        textToSpeech = TextToSpeech(this, this)
        
        try {
            // Primero verificar que los archivos de modelos estén disponibles
            if (!checkModelsExist()) {
                showModelMissingError()
                return
            }
            
            // Inicializar el reconocedor de gestos primero
            gestureRecognizerHelper = GestureRecognizerHelper(
                context = this,
                runningMode = RunningMode.LIVE_STREAM,
                resultListener = object : GestureRecognizerHelper.GestureRecognizerListener {
                    override fun onResults(resultBundle: GestureRecognizerHelper.ResultBundle) {
                        // No usamos los resultados de MediaPipe, solo el modelo personalizado
                    }
                    
                    override fun onTrainingDataStored(gestureName: String) {
                        // No se utiliza en la evaluación
                    }
                    
                    override fun onError(error: String) {
                        runOnUiThread {
                            Toast.makeText(this@EvaluateGestureActivity, error, Toast.LENGTH_SHORT).show()
                            Log.e(TAG, "Error: $error")
                        }
                    }
                    
                    override fun onHandLandmarks(landmarks: List<List<Float>>) {
                        // No necesitamos los landmarks para visualizar
                    }
                    
                    override fun onCustomGestureRecognized(gesture: String, confidence: Float) {
                        runOnUiThread {
                            // Mostrar siempre la confianza actual
                            binding.infoTextView.text = "Confianza: ${String.format("%.2f", confidence * 100)}%"
                            
                            // Si la detección es con confianza alta
                            if (confidence >= CONFIDENCE_THRESHOLD) {
                                // Si ya tenemos una seña fijada
                                if (fixedGestureName != null) {
                                    // Si detectamos una nueva seña diferente a la fijada actual
                                    if (fixedGestureName != gesture) {
                                        // Verificar si es la misma que la última detectada para confirmar
                                        if (lastDetectedGesture == gesture) {
                                            // Es una nueva seña confirmada, actualizamos
                                            fixedGestureName = gesture
                                            binding.detectedGestureTextView.text = "DETECTADO: $gesture"
                                            binding.infoTextView.text = "Nueva seña reconocida"
                                            speakGestureName(gesture)
                                            
                                            // Verificar si corresponde a la última seña grabada
                                            val lastRecordedGesture = getLastRecordedGesture()
                                            if (lastRecordedGesture != null) {
                                                Log.d(TAG, "Última seña grabada: $lastRecordedGesture")
                                                if (gesture == lastRecordedGesture) {
                                                    binding.infoTextView.text = "¡Detectada la última seña grabada!"
                                                    Log.d(TAG, "Detectada la última seña grabada: $gesture")
                                                } else {
                                                    Log.d(TAG, "La seña detectada ($gesture) no coincide con la última grabada ($lastRecordedGesture)")
                                                }
                                            } else {
                                                Log.d(TAG, "No se pudo obtener la última seña grabada")
                                            }
                                        } else {
                                            // Primera detección de una nueva seña - la recordamos
                                            lastDetectedGesture = gesture
                                            // Indicamos que hay candidata sin cambiar la fija
                                            binding.infoTextView.text = "Posible nueva seña: $gesture"
                                        }
                                    }
                                    // Si es la misma que ya está fija, no hacemos nada
                                } else {
                                    // No hay seña fija todavía
                                    if (lastDetectedGesture == gesture) {
                                        // Si se confirma la misma seña por segunda vez, la fijamos
                                        fixedGestureName = gesture
                                        binding.detectedGestureTextView.text = "DETECTADO: $gesture"
                                        binding.infoTextView.text = "Seña reconocida con alta confianza"
                                        speakGestureName(gesture)
                                        
                                        // Verificar si corresponde a la última seña grabada 
                                        val lastRecordedGesture = getLastRecordedGesture()
                                        if (lastRecordedGesture != null) {
                                            Log.d(TAG, "Última seña grabada: $lastRecordedGesture")
                                            if (gesture == lastRecordedGesture) {
                                                binding.infoTextView.text = "¡Detectada la última seña grabada!"
                                                Log.d(TAG, "Detectada la última seña grabada: $gesture")
                                            } else {
                                                Log.d(TAG, "La seña detectada ($gesture) no coincide con la última grabada ($lastRecordedGesture)")
                                            }
                                        } else {
                                            Log.d(TAG, "No se pudo obtener la última seña grabada")
                                        }
                                    } else {
                                        // Primera detección de esta seña
                                        lastDetectedGesture = gesture
                                        binding.detectedGestureTextView.text = getString(R.string.detected_gesture, gesture)
                                    }
                                }
                                
                                // Verificar si el modelo ha sido actualizado
                                val modelFile = File(filesDir, "gesture_model.ml")
                                if (modelFile.exists() && modelFile.lastModified() > gestureRecognizerHelper.lastModelUpdateTime) {
                                    Log.d(TAG, "Modelo actualizado detectado. Recargando...")
                                    gestureRecognizerHelper.mlModel?.loadModel(this@EvaluateGestureActivity, "gesture_model")
                                    gestureRecognizerHelper.lastModelUpdateTime = modelFile.lastModified()
                                }
                            } else if (fixedGestureName == null) {
                                // Confianza baja y no hay seña fija, mostramos la detección actual
                                binding.detectedGestureTextView.text = getString(R.string.detected_gesture, gesture)
                                lastDetectedGesture = null // Resetear última seña si la confianza es baja
                            }
                            // Si hay una seña fija y la confianza es baja, mantenemos la seña fija
                        }
                    }
                }
            )
            
            // Cargar el modelo guardado (si existe)
            val modelFile = File(filesDir, "gesture_model.ml")
            if (modelFile.exists()) {
                Log.d(TAG, "Cargando modelo guardado...")
                gestureRecognizerHelper.mlModel?.loadModel(this, "gesture_model")
                gestureRecognizerHelper.lastModelUpdateTime = modelFile.lastModified()
            } else {
                Log.d(TAG, "No se encontró modelo guardado. Usando modelo por defecto.")
            }
            
            // Inicializar la cámara
            cameraExecutor = Executors.newSingleThreadExecutor()
            
            // Inicializar la cámara
            cameraExecutor = Executors.newSingleThreadExecutor()
            
            // Configurar botón de evaluación
            binding.startEvaluationButton.setOnClickListener {
                if (!isEvaluating) {
                    isEvaluating = true
                    fixedGestureName = null // Resetear la seña fija al iniciar evaluación
                    lastDetectedGesture = null // Resetear la última seña detectada
                    binding.startEvaluationButton.text = "Detener evaluación"
                    binding.resetDetectionButton.isEnabled = true
                    binding.infoTextView.visibility = View.VISIBLE
                    binding.infoTextView.text = "Evaluando señas..."
                    binding.detectedGestureTextView.text = "Esperando detectar seña..."
                } else {
                    isEvaluating = false
                    binding.startEvaluationButton.text = getString(R.string.start_evaluation)
                    binding.resetDetectionButton.isEnabled = false
                    binding.infoTextView.visibility = View.GONE
                    binding.detectedGestureTextView.text = getString(R.string.detected_gesture, "-")
                }
            }
            
            // Botón para reiniciar la detección sin detener la evaluación
            binding.resetDetectionButton.setOnClickListener {
                fixedGestureName = null
                lastDetectedGesture = null
                binding.detectedGestureTextView.text = "Esperando detectar seña..."
                binding.infoTextView.text = "Evaluando señas..."
                
                // Indicación visual de que se ha reiniciado
                Toast.makeText(this, "Detección reiniciada", Toast.LENGTH_SHORT).show()
            }
            
            // Botón para alternar cámara
            binding.switchCameraButton.setOnClickListener {
                cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }
                startCamera() // Reiniciar la cámara con el nuevo selector
            }
            
            startCamera()
        } catch (e: Exception) {
            Log.e(TAG, "Error al inicializar la actividad", e)
            Toast.makeText(
                this,
                "Error al inicializar: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }
    
    // Función para decir el nombre de la seña en voz alta
    private fun speakGestureName(gestureName: String) {
        if (ttsReady) {
            textToSpeech.speak(gestureName, TextToSpeech.QUEUE_FLUSH, null, "gesture_id")
        }
    }
    
    // Inicialización del TextToSpeech
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale("es", "ES"))
            
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Idioma no soportado. Usando idioma por defecto.")
                textToSpeech.setLanguage(Locale.getDefault())
            }
            
            ttsReady = true
        } else {
            Log.e(TAG, "Error al inicializar TextToSpeech")
        }
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                
                // Configurar el preview
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                    }
                
                // Configurar el analizador de imágenes
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, ImageAnalyzer())
                    }
                
                try {
                    // Eliminar bindings anteriores
                    cameraProvider.unbindAll()
                    
                    // Vincular las cases a la cámara
                    cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector, // Usar la cámara seleccionada
                        preview,
                        imageAnalyzer
                    )
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error al iniciar la cámara", e)
                    Toast.makeText(
                        this,
                        "Error al iniciar la cámara: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al obtener el proveedor de cámara", e)
                Toast.makeText(
                    this,
                    "Error al inicializar la cámara: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private inner class ImageAnalyzer : ImageAnalysis.Analyzer {
        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            try {
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val bitmap = mediaImageToBitmap(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    
                    // Si estamos evaluando, procesar para reconocimiento
                    if (isEvaluating) {
                        // Solo usamos el detector de landmarks para el modelo personalizado
                        gestureRecognizerHelper.processForTraining(bitmap)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al analizar imagen", e)
            } finally {
                imageProxy.close()
            }
        }
    }
    
    // Convierte una imagen de la cámara a bitmap
    private fun mediaImageToBitmap(mediaImage: android.media.Image, rotation: Int): Bitmap {
        try {
            val yBuffer = mediaImage.planes[0].buffer
            val uBuffer = mediaImage.planes[1].buffer
            val vBuffer = mediaImage.planes[2].buffer
            
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            
            val nv21 = ByteArray(ySize + uSize + vSize)
            
            // U and V are swapped
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)
            
            val yuvImage = android.graphics.YuvImage(
                nv21, android.graphics.ImageFormat.NV21, mediaImage.width, mediaImage.height, null
            )
            val out = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                android.graphics.Rect(0, 0, yuvImage.width, yuvImage.height),
                100,
                out
            )
            val imageBytes = out.toByteArray()
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            
            // Rotar el bitmap si es necesario
            return if (rotation != 0) {
                val matrix = android.graphics.Matrix()
                matrix.postRotate(rotation.toFloat())
                android.graphics.Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al convertir imagen a bitmap", e)
            throw e
        }
    }
    
    private fun checkModelsExist(): Boolean {
        try {
            val assetFiles = assets.list("")
            if (assetFiles != null) {
                val requiredModels = listOf(
                    "gesture_recognizer.task",
                    "hand_landmarker.task"
                )
                
                for (model in requiredModels) {
                    if (!assetFiles.contains(model)) {
                        Log.e(TAG, "Modelo no encontrado: $model")
                        return false
                    }
                }
                return true
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error al verificar modelos", e)
            return false
        }
    }
    
    private fun showModelMissingError() {
        val errorDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Modelos faltantes")
            .setMessage("No se encuentran todos los modelos necesarios en la carpeta assets. " +
                    "Por favor, consulta el archivo 'instrucciones_para_descargar_modelos.txt' para más información.")
            .setPositiveButton("Entendido") { _, _ -> finish() }
            .setCancelable(false)
            .create()
        
        errorDialog.show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            cameraExecutor.shutdown()
            gestureRecognizerHelper.clearGestureRecognizer()
            
            // Liberar recursos de TextToSpeech
            if (::textToSpeech.isInitialized) {
                textToSpeech.stop()
                textToSpeech.shutdown()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al liberar recursos", e)
        }
    }
    
    // Nueva función para obtener el nombre de la última seña grabada
    private fun getLastRecordedGesture(): String? {
        val gestureDataFolder = File(filesDir, "gesture_data")
        if (!gestureDataFolder.exists()) {
            Log.w(TAG, "La carpeta de datos de gestos no existe")
            return null
        }
        
        val gestureFiles = gestureDataFolder.listFiles()
        if (gestureFiles.isNullOrEmpty()) {
            Log.w(TAG, "No se encontraron archivos de gestos")
            return null
        }
        
        var lastModifiedGesture: File? = null
        for (file in gestureFiles) {
            if (lastModifiedGesture == null || file.lastModified() > lastModifiedGesture.lastModified()) {
                lastModifiedGesture = file
            }
        }
        
        return lastModifiedGesture?.nameWithoutExtension
    }
}