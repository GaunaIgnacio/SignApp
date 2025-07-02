package com.example.detector1

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
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
import com.example.detector1.databinding.ActivityRecordGestureBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RecordGestureActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityRecordGestureBinding
    private lateinit var gestureRecognizerHelper: GestureRecognizerHelper
    private lateinit var cameraExecutor: ExecutorService
    private var isRecording = false
    private var capturedFrames = 0
    private val targetFrames = 30 // Número de frames a capturar para cada seña
    
    // Selector de cámara
    private var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    
    private val TAG = "RecordGestureActivity"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordGestureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        try {
            // Primero verificar que los archivos de modelos estén disponibles
            if (!checkModelsExist()) {
                showModelMissingError()
                return
            }
            
            // Inicializar el reconocedor de gestos
            gestureRecognizerHelper = GestureRecognizerHelper(
                context = this,
                runningMode = RunningMode.LIVE_STREAM,
                resultListener = object : GestureRecognizerHelper.GestureRecognizerListener {
                    override fun onResults(resultBundle: GestureRecognizerHelper.ResultBundle) {
                        // No necesitamos procesar los resultados durante la grabación
                    }
                    
                    override fun onTrainingDataStored(gestureName: String) {
                        runOnUiThread {
                            Toast.makeText(
                                this@RecordGestureActivity,
                                "Seña '$gestureName' guardada correctamente",
                                Toast.LENGTH_SHORT
                            ).show()
                            binding.saveButton.isEnabled = false
                            isRecording = false
                            binding.recordButton.text = getString(R.string.start_recording)
                        }
                    }
                    
                    override fun onError(error: String) {
                        runOnUiThread {
                            Toast.makeText(this@RecordGestureActivity, error, Toast.LENGTH_SHORT).show()
                            Log.e(TAG, "Error: $error")
                        }
                    }
                    
                    override fun onHandLandmarks(landmarks: List<List<Float>>) {
                        if (isRecording && landmarks.isNotEmpty()) {
                            capturedFrames++
                            runOnUiThread {
                                binding.infoTextView.text = "Grabando seña: ${binding.gestureNameEditText.text} - Frames: $capturedFrames/$targetFrames"
                                
                                // Si alcanzamos el objetivo de frames, detener automáticamente
                                if (capturedFrames >= targetFrames) {
                                    isRecording = false
                                    binding.recordButton.text = getString(R.string.start_recording)
                                    binding.saveButton.isEnabled = true
                                    gestureRecognizerHelper.stopRecording()
                                    binding.infoTextView.text = "Grabación completada. ¡Guarda la seña!"
                                }
                            }
                        }
                    }
                }
            )
            
            // Inicializar la cámara
            cameraExecutor = Executors.newSingleThreadExecutor()
            
            // Configurar botones
            binding.recordButton.setOnClickListener {
                val gestureName = binding.gestureNameEditText.text.toString().trim()
                if (gestureName.isEmpty()) {
                    Toast.makeText(
                        this,
                        "Por favor, ingresa un nombre para la seña",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                
                if (!isRecording) {
                    // Comenzar a grabar
                    isRecording = true
                    capturedFrames = 0
                    binding.recordButton.text = "Detener grabación"
                    binding.saveButton.isEnabled = false
                    gestureRecognizerHelper.startRecording(gestureName)
                    binding.infoTextView.text = "Grabando seña: $gestureName - Frames: $capturedFrames/$targetFrames"
                    binding.infoTextView.visibility = View.VISIBLE
                } else {
                    // Detener la grabación
                    isRecording = false
                    binding.recordButton.text = getString(R.string.start_recording)
                    binding.saveButton.isEnabled = true
                    gestureRecognizerHelper.stopRecording()
                    binding.infoTextView.text = "Grabación detenida. ¡Guarda la seña!"
                }
            }
            
            binding.saveButton.setOnClickListener {
                binding.saveButton.isEnabled = false
                binding.saveButton.text = "Guardando y entrenando..."
                gestureRecognizerHelper.saveTrainingData()
                binding.saveButton.text = "Guardar seña"
                
                // Mostrar mensaje de entrenamiento automático
                Toast.makeText(
                    this,
                    "Seña guardada y modelo reentrenado automáticamente",
                    Toast.LENGTH_SHORT
                ).show()
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
                    
                    // Si estamos grabando, procesar para entrenamiento
                    if (isRecording) {
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
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            cameraExecutor.shutdown()
            gestureRecognizerHelper.clearGestureRecognizer()
        } catch (e: Exception) {
            Log.e(TAG, "Error al liberar recursos", e)
        }
    }
}