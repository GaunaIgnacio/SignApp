package com.example.detector1

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream

class GestureRecognizerHelper(
    private val context: Context,
    private val runningMode: RunningMode,
    private val resultListener: GestureRecognizerListener
) {
    
    // Etiquetas para el registro de logs
    private val TAG = "GestureRecognizerHelper"
    
    // Opciones para el reconocedor de gestos
    private var gestureRecognizer: GestureRecognizer? = null
    private var handLandmarker: HandLandmarker? = null
    private var lastProcessTimeMs: Long = 0
    
    // Para almacenamiento de datos
    private val gestureDataList = CopyOnWriteArrayList<GestureData>()
    private var isRecording = false
    private var currentGestureName = ""
    
    // Para ML
    private var _mlModel: SimpleMLModel? = null
    
    // Getter y Setter públicos para mlModel
    var mlModel: SimpleMLModel?
        get() = _mlModel
        set(value) {
            _mlModel = value
        }
        
    // Variable para rastrear la última actualización del modelo
    var lastModelUpdateTime: Long = 0
    
    // Interfaz para manejar los resultados
    interface GestureRecognizerListener {
        fun onResults(resultBundle: ResultBundle)
        fun onTrainingDataStored(gestureName: String)
        fun onError(error: String)
        fun onHandLandmarks(landmarks: List<List<Float>>) {}
        fun onCustomGestureRecognized(gesture: String, confidence: Float) {}
    }
    
    // Clase para almacenar datos de un gesto
    data class GestureData(
        val gestureName: String,
        val landmarks: List<List<Float>>
    )
    
    // Clase para almacenar resultados del reconocimiento
    data class ResultBundle(
        val results: GestureRecognizerResult,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
        val inferenceTime: Long
    )
    
    // Clase para ML simple basada en KNN
    class SimpleMLModel {
        private val LANDMARKS_COUNT = 21 // Número de landmarks en una mano
        private val COORDS_PER_LANDMARK = 3 // x, y, z por cada landmark
        private val trainingData = mutableListOf<TrainingSample>()
        private val random = Random(System.currentTimeMillis())
        
        data class TrainingSample(
            val features: FloatArray,
            val label: String
        ) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false
                
                other as TrainingSample
                
                if (!features.contentEquals(other.features)) return false
                if (label != other.label) return false
                
                return true
            }
            
            override fun hashCode(): Int {
                var result = features.contentHashCode()
                result = 31 * result + label.hashCode()
                return result
            }
        }
        
        // Añadir datos de entrenamiento
        fun addTrainingData(gestureName: String, landmarksData: List<GestureData>) {
            landmarksData.forEach { data ->
                val features = flattenLandmarks(data.landmarks)
                trainingData.add(TrainingSample(features, gestureName))
            }
            Log.d("SimpleMLModel", "Añadidos ${landmarksData.size} ejemplos para $gestureName. Total: ${trainingData.size}")
        }
        
        // Convertir landmarks a array plano
        private fun flattenLandmarks(landmarks: List<List<Float>>): FloatArray {
            val flattenedFeatures = FloatArray(LANDMARKS_COUNT * COORDS_PER_LANDMARK)
            
            for (i in landmarks.indices) {
                if (i < LANDMARKS_COUNT) {
                    val landmark = landmarks[i]
                    for (j in 0 until minOf(COORDS_PER_LANDMARK, landmark.size)) {
                        flattenedFeatures[i * COORDS_PER_LANDMARK + j] = landmark[j]
                    }
                }
            }
            
            return normalizeFeatures(flattenedFeatures)
        }
        
        // Normalizar características para mejorar la precisión
        private fun normalizeFeatures(features: FloatArray): FloatArray {
            // Normalización simple: centrar en [0,1]
            var minX = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var minY = Float.MAX_VALUE
            var maxY = Float.MIN_VALUE
            
            // Encontrar min/max para normalización
            for (i in 0 until LANDMARKS_COUNT) {
                val x = features[i * COORDS_PER_LANDMARK]
                val y = features[i * COORDS_PER_LANDMARK + 1]
                
                minX = minOf(minX, x)
                maxX = maxOf(maxX, x)
                minY = minOf(minY, y)
                maxY = maxOf(maxY, y)
            }
            
            // Normalizar
            val normalized = features.clone()
            for (i in 0 until LANDMARKS_COUNT) {
                normalized[i * COORDS_PER_LANDMARK] = (features[i * COORDS_PER_LANDMARK] - minX) / (maxX - minX + 0.0001f)
                normalized[i * COORDS_PER_LANDMARK + 1] = (features[i * COORDS_PER_LANDMARK + 1] - minY) / (maxY - minY + 0.0001f)
            }
            
            return normalized
        }
        
        // Entrenar el modelo (KNN implícito)
        fun train() {
            // No es necesario entrenar explícitamente en KNN, solo guardamos los datos
            Log.d("SimpleMLModel", "Modelo entrenado con ${trainingData.size} muestras")
        }
        
        // Predecir utilizando KNN con ponderación por distancia
        fun predict(landmarks: List<List<Float>>, k: Int = 5): Pair<String, Float> {
            if (trainingData.isEmpty()) {
                return Pair("No hay datos de entrenamiento", 0.0f)
            }
            
            val features = flattenLandmarks(landmarks)
            val distances = trainingData.map { 
                Pair(euclideanDistance(features, it.features), it.label)
            }.sortedBy { it.first }
            
            val kNearest = distances.take(k)
            val maxDistance = kNearest.maxOf { it.first }
            val weights = mutableMapOf<String, Float>()
            
            // Calcular pesos ponderados por distancia inversa
            kNearest.forEach { (distance, label) ->
                val weight = 1.0f - (distance / (maxDistance + 0.0001f))
                weights[label] = (weights[label] ?: 0f) + weight
            }
            
            // Encontrar la predicción con mayor peso
            val prediction = weights.maxByOrNull { it.value }?.key ?: "Desconocido"
            
            // Calcular confianza normalizada
            val totalWeight = weights.values.sum()
            val confidence = if (totalWeight > 0) weights[prediction]!! / totalWeight else 0f
            
            // Log para depuración
            Log.d("SimpleMLModel", "Predicción: $prediction (confianza: ${confidence * 100}%)")
            Log.d("SimpleMLModel", "Pesos por clase: ${weights.map { "${it.key}: ${it.value}" }.joinToString()}")
            
            return Pair(prediction, confidence)
        }
        
        // Calcular distancia euclidiana entre dos vectores
        private fun euclideanDistance(a: FloatArray, b: FloatArray): Float {
            var sum = 0.0f
            for (i in a.indices) {
                if (i < b.size) {
                    val diff = a[i] - b[i]
                    sum += diff * diff
                }
            }
            return sum
        }
        
        // Guardar el modelo (los datos de entrenamiento)
        fun saveModel(context: Context, filename: String) {
            try {
                val file = File(context.filesDir, "$filename.ml")
                
                // Formato: número de muestras, tamaño de características, luego cada muestra (features+label)
                val outputStream = FileOutputStream(file)
                val bufferedOutputStream = BufferedOutputStream(outputStream)
                DataOutputStream(bufferedOutputStream).use { dos ->
                    // Primero escribir número de muestras y tamaño de características
                    // Usamos writeInt en lugar de write para soportar valores mayores a 255
                    dos.writeInt(trainingData.size)
                    dos.writeInt(LANDMARKS_COUNT)
                    dos.writeInt(COORDS_PER_LANDMARK)
                    
                    // Luego escribir cada muestra
                    trainingData.forEach { sample ->
                        // Escribir features usando writeFloat para mayor eficiencia
                        for (feature in sample.features) {
                            dos.writeFloat(feature)
                        }
                        
                        // Escribir longitud de la etiqueta y la etiqueta
                        val labelBytes = sample.label.toByteArray()
                        dos.writeInt(labelBytes.size)
                        dos.write(labelBytes)
                    }
                    
                    // Asegurar que todos los datos se escriban
                    dos.flush()
                }
                
                Log.d("SimpleMLModel", "Modelo guardado en $filename.ml con ${trainingData.size} muestras")
            } catch (e: Exception) {
                Log.e("SimpleMLModel", "Error al guardar modelo: ${e.message}")
                e.printStackTrace()
            }
        }
        
        // Cargar modelo
        fun loadModel(context: Context, filename: String): Boolean {
            try {
                val file = File(context.filesDir, "$filename.ml")
                if (!file.exists()) {
                    Log.e("SimpleMLModel", "Archivo de modelo no encontrado: $filename.ml")
                    return false
                }
                
                // Limpiar datos existentes antes de cargar
                trainingData.clear()
                
                val inputStream = FileInputStream(file)
                val bufferedInputStream = BufferedInputStream(inputStream)
                DataInputStream(bufferedInputStream).use { dis ->
                    // Leer metadata usando readInt para soportar valores mayores a 255
                    val numSamples = dis.readInt()
                    val numLandmarks = dis.readInt()
                    val coordsPerLandmark = dis.readInt()
                    
                    if (numSamples <= 0 || numLandmarks != LANDMARKS_COUNT || coordsPerLandmark != COORDS_PER_LANDMARK) {
                        throw IOException("Formato de modelo inválido: muestras=$numSamples, landmarks=$numLandmarks, coords=$coordsPerLandmark")
                    }
                    
                    Log.d("SimpleMLModel", "Cargando modelo: $numSamples muestras, $numLandmarks landmarks, $coordsPerLandmark coordenadas")
                    
                    // Leer cada muestra
                    for (i in 0 until numSamples) {
                        // Leer features usando readFloat para mayor eficiencia
                        val features = FloatArray(numLandmarks * coordsPerLandmark)
                        for (j in features.indices) {
                            features[j] = dis.readFloat()
                        }
                        
                        // Leer etiqueta
                        val labelLength = dis.readInt()
                        if (labelLength <= 0) {
                            throw IOException("Longitud de etiqueta inválida: $labelLength")
                        }
                        val labelBytes = ByteArray(labelLength)
                        dis.readFully(labelBytes)
                        val label = String(labelBytes)
                        
                        // Verificar que la etiqueta no esté vacía
                        if (label.isBlank()) {
                            throw IOException("Etiqueta vacía encontrada en muestra $i")
                        }
                        
                        trainingData.add(TrainingSample(features, label))
                        Log.d("SimpleMLModel", "Muestra $i cargada: $label")
                    }
                }
                
                Log.d("SimpleMLModel", "Modelo cargado exitosamente con ${trainingData.size} muestras")
                // Imprimir las etiquetas únicas cargadas
                val uniqueLabels = trainingData.map { it.label }.distinct()
                Log.d("SimpleMLModel", "Señas cargadas: ${uniqueLabels.joinToString(", ")}")
                return true
            } catch (e: Exception) {
                Log.e("SimpleMLModel", "Error al cargar modelo: ${e.message}")
                e.printStackTrace()
                trainingData.clear() // Limpiar datos en caso de error
                return false
            }
        }
    }
    
    init {
        try {
            // Crear o cargar el modelo
            mlModel = SimpleMLModel()
            mlModel?.loadModel(context, "gesture_model")
            
            // Configurar reconocedor
            if (checkModelExists()) {
                setupGestureRecognizer()
            } else {
                resultListener.onError("El modelo gesture_recognizer.task no existe en assets o no se puede acceder a él.")
                Log.e(TAG, "No se pudo encontrar o acceder al modelo en assets")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error durante la inicialización", e)
            resultListener.onError("Error al inicializar: ${e.message}")
        }
    }
    
    // Verifica si el modelo existe en assets
    private fun checkModelExists(): Boolean {
        try {
            // Verificar que existan todos los modelos necesarios
            val assetFiles = context.assets.list("")
            if (assetFiles != null) {
                val requiredModels = listOf(
                    "gesture_recognizer.task",
                    "hand_landmarker.task"
                )
                
                val missingModels = requiredModels.filter { !assetFiles.contains(it) }
                
                if (missingModels.isNotEmpty()) {
                    val errorMsg = "Los siguientes modelos no se encuentran en assets: ${missingModels.joinToString(", ")}"
                    Log.e(TAG, errorMsg)
                    resultListener.onError(errorMsg)
                    return false
                }
                
                Log.d(TAG, "Todos los modelos necesarios existen en assets")
                return true
            }
            return false
        } catch (e: IOException) {
            Log.e(TAG, "Error al verificar los modelos en assets", e)
            resultListener.onError("Error al acceder a los modelos: ${e.message}")
            return false
        }
    }
    
    // Configura el reconocedor de gestos de MediaPipe
    private fun setupGestureRecognizer() {
        try {
            // Configuración básica para el reconocedor
            val gestureBaseOptionsBuilder = BaseOptions.builder()
                .setDelegate(Delegate.CPU)
                .setModelAssetPath("gesture_recognizer.task")
            
            // Configuración para el detector de landmarks de manos
            val handLandmarkerBaseOptionsBuilder = BaseOptions.builder()
                .setDelegate(Delegate.CPU)
                .setModelAssetPath("hand_landmarker.task")
            
            // Opciones para el reconocedor de gestos
            val gestureRecognizerOptions = GestureRecognizer.GestureRecognizerOptions.builder()
                .setBaseOptions(gestureBaseOptionsBuilder.build())
                .setNumHands(1)
                .setRunningMode(runningMode)
                .setResultListener { result, mpImage ->
                    try {
                        val inferenceTime = SystemClock.uptimeMillis() - lastProcessTimeMs
                        lastProcessTimeMs = SystemClock.uptimeMillis()
                        returnLiveStreamResult(result, mpImage, inferenceTime)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error en el callback del reconocedor de gestos", e)
                        resultListener.onError("Error al procesar resultados: ${e.message}")
                    }
                }
                .setErrorListener { error -> 
                    resultListener.onError(error.message ?: "Error desconocido")
                    Log.e(TAG, "Error del reconocedor de gestos: ${error.message}")
                }
                .build()
            
            // Crear el reconocedor
            gestureRecognizer = GestureRecognizer.createFromOptions(context, gestureRecognizerOptions)
            
            // Configurar también un detector de landmarks de manos para recolectar datos
            val handLandmarkerOptions = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(handLandmarkerBaseOptionsBuilder.build())
                .setNumHands(1)
                .setRunningMode(runningMode)
                .setResultListener { result, mpImage ->
                    try {
                        // Añadir logs para entender el flujo
                        Log.d(TAG, "HandLandmarker recibió resultado. isRecording: $isRecording, landmarks vacíos: ${result.landmarks().isEmpty()}")
                        returnHandLandmarkerResult(result, mpImage)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error en callback de landmark", e)
                        resultListener.onError("Error procesando landmarks: ${e.message}")
                    }
                }
                .setErrorListener { error -> 
                    resultListener.onError(error.message ?: "Error desconocido")
                    Log.e(TAG, "Error del detector de landmarks: ${error.message}")
                }
                .build()
            
            handLandmarker = HandLandmarker.createFromOptions(context, handLandmarkerOptions)
            
            // Añadir logs para debug
            Log.d(TAG, "Configurando reconocedor para modo: $runningMode")
            
        } catch (e: IOException) {
            resultListener.onError("Error al cargar el modelo: ${e.message}")
            Log.e(TAG, "Error al configurar el reconocedor de gestos", e)
        } catch (e: IllegalStateException) {
            resultListener.onError("MediaPipe falló al inicializar: ${e.message}")
            Log.e(TAG, "Error de inicialización de MediaPipe", e)
        } catch (e: Exception) {
            resultListener.onError("Error inesperado: ${e.message}")
            Log.e(TAG, "Error inesperado durante la configuración", e)
        }
    }
    
    // Procesa una imagen para detección de gestos
    fun recognizeGesture(imageBitmap: Bitmap) {
        if (gestureRecognizer == null) {
            try {
                setupGestureRecognizer()
                if (gestureRecognizer == null) {
                    resultListener.onError("No se pudo inicializar el reconocedor de gestos")
                    return
                }
            } catch (e: Exception) {
                resultListener.onError("Error al reiniciar el reconocedor: ${e.message}")
                return
            }
        }
        
        // Tiempo de inicio para medir el tiempo de inferencia
        lastProcessTimeMs = SystemClock.uptimeMillis()
        
        // Opciones de procesamiento de imagen
        val imageProcessingOptions = ImageProcessingOptions.builder()
            .build()
        
        try {
            // Convertir Bitmap a MPImage para MediaPipe
            val mpImage = BitmapImageBuilder(imageBitmap).build()
            
            // Procesar la imagen con el reconocedor de gestos
            if (runningMode == RunningMode.LIVE_STREAM) {
                gestureRecognizer?.recognizeAsync(
                    mpImage,
                    imageProcessingOptions,
                    lastProcessTimeMs
                )
            } else {
                val result = gestureRecognizer?.recognize(mpImage, imageProcessingOptions)
                result?.let {
                    val inferenceTimeMs = SystemClock.uptimeMillis() - lastProcessTimeMs
                    resultListener.onResults(
                        ResultBundle(
                            it,
                            imageBitmap.height,
                            imageBitmap.width,
                            inferenceTimeMs
                        )
                    )
                }
            }
        } catch (e: Exception) {
            resultListener.onError("Error al procesar la imagen: ${e.message}")
            Log.e(TAG, "Error al procesar la imagen con el reconocedor de gestos", e)
        }
    }
    
    // Para recolectar datos de entrenamiento
    fun startRecording(gestureName: String) {
        currentGestureName = gestureName
        isRecording = true
        gestureDataList.clear()
    }
    
    fun stopRecording() {
        isRecording = false
    }
    
    fun processForTraining(imageBitmap: Bitmap) {
        if (handLandmarker == null) {
            Log.e(TAG, "handLandmarker no está inicializado para entrenamiento")
            return
        }
        
        Log.d(TAG, "Procesando imagen para entrenamiento. Grabando: $isRecording")
        
        val imageProcessingOptions = ImageProcessingOptions.builder()
            .build()
        
        try {
            // Convertir Bitmap a MPImage para MediaPipe
            val mpImage = BitmapImageBuilder(imageBitmap).build()
            
            // Usar el detector de landmarks para obtener la posición de las manos
            // Cambiar a LIVE_STREAM puede ayudar con la detección continua
            lastProcessTimeMs = SystemClock.uptimeMillis()
            handLandmarker?.detectAsync(
                mpImage,
                imageProcessingOptions,
                lastProcessTimeMs
            )
        } catch (e: Exception) {
            resultListener.onError("Error al procesar la imagen para entrenamiento: ${e.message}")
            Log.e(TAG, "Error al procesar la imagen para entrenamiento", e)
        }
    }
    
    fun saveTrainingData() {
        Log.d(TAG, "Intentando guardar datos de entrenamiento. Frames guardados: ${gestureDataList.size}")
        
        if (gestureDataList.isEmpty()) {
            resultListener.onError("No hay datos para guardar. No se detectaron manos durante la grabación.")
            return
        }
        
        try {
            // Guardar los datos en JSON como antes
            val dataFolder = File(context.filesDir, "gesture_data")
            if (!dataFolder.exists()) {
                dataFolder.mkdirs()
            }
            
            val gestureFile = File(dataFolder, "${currentGestureName}.json")
            val json = gestureDataToJson(gestureDataList)
            
            FileOutputStream(gestureFile).use { output ->
                output.write(json.toByteArray())
            }
            
            // Cargar el modelo existente (si existe)
            if (mlModel == null) {
                mlModel = SimpleMLModel()
                val modelFile = File(context.filesDir, "gesture_model.ml")
                if (modelFile.exists()) {
                    Log.d(TAG, "Cargando modelo existente...")
                    mlModel?.loadModel(context, "gesture_model")
                }
            }
            
            // Agregar los nuevos datos de entrenamiento al modelo
            Log.d(TAG, "Agregando nuevos datos de entrenamiento al modelo...")
            mlModel?.addTrainingData(currentGestureName, gestureDataList)
            
            // Entrenar el modelo con todos los datos
            Log.d(TAG, "Entrenando modelo...")
            mlModel?.train()
            
            // Guardar el modelo actualizado
            Log.d(TAG, "Guardando modelo actualizado...")
            mlModel?.saveModel(context, "gesture_model")
            
            // Evaluar el modelo recién entrenado
            evaluateModel()
            
            resultListener.onTrainingDataStored(currentGestureName)
        } catch (e: Exception) {
            resultListener.onError("Error al guardar los datos: ${e.message}")
            Log.e(TAG, "Error al guardar los datos de entrenamiento", e)
        }
    }
    
    // Nueva función para evaluar el modelo después de entrenarlo
    private fun evaluateModel() {
        try {
            // Obtener los últimos datos guardados
            val lastGestureData = gestureDataList.lastOrNull()
            
            if (lastGestureData != null) {
                Log.d(TAG, "Evaluando modelo con la última seña grabada: ${lastGestureData.gestureName}")
                
                // Realizar predicción con el modelo recién entrenado
                val prediction = recognizeWithTrainedModel(lastGestureData.landmarks)
                
                Log.d(TAG, "Evaluación del modelo - Gesto: ${lastGestureData.gestureName}, Predicción: ${prediction.first} (confianza: ${prediction.second})")
                
                // Notificar el resultado de la evaluación
                resultListener.onCustomGestureRecognized(prediction.first, prediction.second)
            } else {
                Log.w(TAG, "No hay datos para evaluar el modelo.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al evaluar el modelo", e)
        }
    }
    
    // Convierte datos de gestos a JSON
    private fun gestureDataToJson(dataList: List<GestureData>): String {
        val builder = StringBuilder("[")
        for ((index, data) in dataList.withIndex()) {
            builder.append("{\"gesture_name\":\"${data.gestureName}\",")
            builder.append("\"landmarks\":[")
            
            for ((lmIndex, landmark) in data.landmarks.withIndex()) {
                builder.append("[")
                for ((coordIndex, coord) in landmark.withIndex()) {
                    builder.append(coord)
                    if (coordIndex < landmark.size - 1) {
                        builder.append(",")
                    }
                }
                builder.append("]")
                if (lmIndex < data.landmarks.size - 1) {
                    builder.append(",")
                }
            }
            
            builder.append("]}")
            if (index < dataList.size - 1) {
                builder.append(",")
            }
        }
        builder.append("]")
        return builder.toString()
    }
    
    // Callbacks para resultados de MediaPipe
    private fun returnLiveStreamResult(
        result: GestureRecognizerResult,
        mpImage: MPImage,
        inferenceTime: Long
    ) {
        resultListener.onResults(
            ResultBundle(
                result,
                mpImage.height,
                mpImage.width,
                inferenceTime
            )
        )
    }
    
    private fun returnHandLandmarkerResult(
        result: HandLandmarkerResult,
        mpImage: MPImage
    ) {
        // Log detallado del estado actual
        Log.d(TAG, "returnHandLandmarkerResult - isRecording: $isRecording, tiene landmarks: ${!result.landmarks().isEmpty()}")
        
        if (result.landmarks().isEmpty()) {
            Log.d(TAG, "No se detectaron landmarks de manos")
            return
        }
        
        try {
            // Extraer los landmarks en formato plano
            val handLandmarks = result.landmarks()[0]
            val flattenedLandmarks = mutableListOf<List<Float>>()
            
            for (landmark in handLandmarks) {
                flattenedLandmarks.add(listOf(landmark.x(), landmark.y(), landmark.z()))
            }
            
            // Realizar predicción con el modelo entrenado si no estamos grabando
            if (!isRecording) {
                val prediction = recognizeWithTrainedModel(flattenedLandmarks)
                Log.d(TAG, "Predicción: ${prediction.first} (confianza: ${prediction.second})")
                
                // Solo mostramos la predicción si tiene una confianza mínima
                if (prediction.second > 0.6f) {
                    resultListener.onCustomGestureRecognized(prediction.first, prediction.second)
                }
            }
            
            // Enviar landmarks para visualización
            resultListener.onHandLandmarks(flattenedLandmarks)
            
            // Solo guardar si estamos grabando
            if (isRecording) {
                gestureDataList.add(GestureData(currentGestureName, flattenedLandmarks))
                Log.d(TAG, "Se añadió un nuevo frame con ${flattenedLandmarks.size} landmarks a gestureDataList")
                Log.d(TAG, "Total de frames guardados hasta ahora: ${gestureDataList.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al procesar landmarks", e)
        }
    }
    
    fun clearGestureRecognizer() {
        try {
            gestureRecognizer?.close()
            gestureRecognizer = null
            handLandmarker?.close()
            handLandmarker = null
        } catch (e: Exception) {
            Log.e(TAG, "Error al liberar recursos", e)
        }
    }
    
    // Para entrenar modelo ML
    fun trainModel() {
        if (gestureDataList.isEmpty()) {
            resultListener.onError("No hay datos para entrenar el modelo.")
            return
        }
        
        try {
            if (mlModel == null) {
                mlModel = SimpleMLModel()
            }
            
            // Añadir datos actuales al modelo
            mlModel?.addTrainingData(currentGestureName, gestureDataList)
            
            // Entrenar el modelo
            Log.d(TAG, "Iniciando entrenamiento del modelo...")
            mlModel?.train()
            Log.d(TAG, "Entrenamiento del modelo completado.")
            
            // Guardar el modelo
            Log.d(TAG, "Guardando modelo entrenado...")
            mlModel?.saveModel(context, "gesture_model")
            Log.d(TAG, "Modelo guardado exitosamente.")
            
            resultListener.onTrainingDataStored(currentGestureName)
            Log.d(TAG, "Modelo entrenado con ${gestureDataList.size} ejemplos para $currentGestureName")
        } catch (e: Exception) {
            resultListener.onError("Error al entrenar el modelo: ${e.message}")
            Log.e(TAG, "Error al entrenar el modelo", e)
        }
    }
    
    // Para reconocer un gesto usando el modelo entrenado
    fun recognizeWithTrainedModel(landmarks: List<List<Float>>): Pair<String, Float> {
        if (mlModel == null) {
            mlModel = SimpleMLModel()
            val loaded = mlModel?.loadModel(context, "gesture_model") ?: false
            if (!loaded) {
                return Pair("Modelo no cargado", 0.0f)
            }
        }
        
        return mlModel?.predict(landmarks, 3) ?: Pair("Error de predicción", 0.0f)
    }
}