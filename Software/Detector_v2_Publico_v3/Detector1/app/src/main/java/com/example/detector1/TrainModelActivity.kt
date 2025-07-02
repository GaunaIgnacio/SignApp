package com.example.detector1

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.detector1.databinding.ActivityTrainModelBinding
import java.io.File
import java.util.concurrent.Executors

class TrainModelActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrainModelBinding
    private var mlModel: GestureRecognizerHelper.SimpleMLModel? = null
    private val TAG = "TrainModelActivity"
    
    // Para métricas de evaluación
    private data class EvaluationMetrics(
        var totalSamples: Int = 0,
        var correctPredictions: Int = 0,
        var confusionMatrix: MutableMap<String, MutableMap<String, Int>> = mutableMapOf()
    ) {
        fun accuracy(): Float = if (totalSamples > 0) correctPredictions.toFloat() / totalSamples else 0f
        
        fun precision(className: String): Float {
            val predicted = confusionMatrix.values.sumOf { it[className] ?: 0 }
            val truePositives = confusionMatrix[className]?.get(className) ?: 0
            return if (predicted > 0) truePositives.toFloat() / predicted else 0f
        }
        
        fun recall(className: String): Float {
            val actual = confusionMatrix[className]?.values?.sum() ?: 0
            val truePositives = confusionMatrix[className]?.get(className) ?: 0
            return if (actual > 0) truePositives.toFloat() / actual else 0f
        }
        
        fun f1Score(className: String): Float {
            val prec = precision(className)
            val rec = recall(className)
            return if (prec + rec > 0) 2 * prec * rec / (prec + rec) else 0f
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrainModelBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Inicializar el modelo ML
        initializeModel()
        
        // Configurar botones
        binding.trainButton.setOnClickListener {
            trainModel()
        }
        
        binding.evaluateButton.setOnClickListener {
            evaluateModel()
        }
        
        binding.backButton.setOnClickListener {
            finish()
        }
    }
    
    private fun initializeModel() {
        try {
            mlModel = GestureRecognizerHelper.SimpleMLModel()
            val modelFile = File(filesDir, "gesture_model.ml")
            if (modelFile.exists()) {
                val loaded = mlModel?.loadModel(this, "gesture_model") ?: false
                if (loaded) {
                    Log.d(TAG, "Modelo ML cargado correctamente")
                    binding.statusTextView.text = "Modelo cargado: ${getGestureCount()} señas disponibles"
                    binding.trainButton.isEnabled = true
                    binding.evaluateButton.isEnabled = true
                } else {
                    Log.e(TAG, "Error al cargar el modelo ML")
                    binding.statusTextView.text = "Error al cargar el modelo"
                    Toast.makeText(this, "Error al cargar el modelo de señas", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.w(TAG, "No existe archivo de modelo ML")
                binding.statusTextView.text = "No hay modelo entrenado. Grabe señas primero."
                Toast.makeText(this, "No hay modelo de señas entrenado", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al inicializar el modelo ML: ${e.message}")
            binding.statusTextView.text = "Error: ${e.message}"
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun getGestureCount(): Int {
        val gestureDataFolder = File(filesDir, "gesture_data")
        if (!gestureDataFolder.exists() || !gestureDataFolder.isDirectory) {
            return 0
        }
        
        val gestureFiles = gestureDataFolder.listFiles()
        return gestureFiles?.filter { it.isFile && it.name.endsWith(".json") }?.size ?: 0
    }
    
    private fun trainModel() {
        binding.progressBar.visibility = View.VISIBLE
        binding.statusTextView.text = "Entrenando modelo..."
        binding.trainButton.isEnabled = false
        binding.evaluateButton.isEnabled = false
        
        // Usar un hilo separado para no bloquear la UI
        Executors.newSingleThreadExecutor().execute {
            try {
                // Cargar todos los datos de entrenamiento
                val gestureDataFolder = File(filesDir, "gesture_data")
                if (!gestureDataFolder.exists() || !gestureDataFolder.isDirectory) {
                    runOnUiThread {
                        binding.statusTextView.text = "No hay datos de entrenamiento disponibles"
                        binding.progressBar.visibility = View.GONE
                        binding.trainButton.isEnabled = true
                    }
                    return@execute
                }
                
                // Crear un nuevo modelo
                val newModel = GestureRecognizerHelper.SimpleMLModel()
                
                // Cargar todos los archivos de señas
                val gestureFiles = gestureDataFolder.listFiles()
                if (gestureFiles.isNullOrEmpty()) {
                    runOnUiThread {
                        binding.statusTextView.text = "No hay archivos de señas disponibles"
                        binding.progressBar.visibility = View.GONE
                        binding.trainButton.isEnabled = true
                    }
                    return@execute
                }
                
                // Procesar cada archivo de seña
                var totalSamples = 0
                for (file in gestureFiles) {
                    if (file.isFile && file.name.endsWith(".json")) {
                        val gestureName = file.nameWithoutExtension
                        try {
                            // Leer el archivo JSON y extraer los datos
                            val fileContent = file.readText()
                            val gestureDataList = parseGestureDataFromJson(fileContent, gestureName)
                            if (gestureDataList.isNotEmpty()) {
                                // Añadir los datos al modelo
                                newModel.addTrainingData(gestureName, gestureDataList)
                                totalSamples += gestureDataList.size
                                Log.d(TAG, "Datos de seña '$gestureName' añadidos al modelo (${gestureDataList.size} muestras)")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error al cargar datos de seña '$gestureName': ${e.message}")
                        }
                    }
                }
                
                // Entrenar el modelo con los datos cargados
                newModel.train()
                
                // Guardar el modelo entrenado
                newModel.saveModel(this, "gesture_model")
                
                // Actualizar la UI
                runOnUiThread {
                    binding.statusTextView.text = "Modelo entrenado con $totalSamples muestras de ${gestureFiles.size} señas"
                    binding.progressBar.visibility = View.GONE
                    binding.trainButton.isEnabled = true
                    binding.evaluateButton.isEnabled = true
                    
                    // Actualizar el modelo actual
                    mlModel = newModel
                    
                    Toast.makeText(this, "Modelo entrenado correctamente", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al entrenar el modelo: ${e.message}")
                runOnUiThread {
                    binding.statusTextView.text = "Error al entrenar: ${e.message}"
                    binding.progressBar.visibility = View.GONE
                    binding.trainButton.isEnabled = true
                    binding.evaluateButton.isEnabled = true
                    Toast.makeText(this, "Error al entrenar el modelo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun evaluateModel() {
        binding.progressBar.visibility = View.VISIBLE
        binding.statusTextView.text = "Evaluando modelo..."
        binding.trainButton.isEnabled = false
        binding.evaluateButton.isEnabled = false
        binding.metricsLayout.visibility = View.GONE
        
        // Usar un hilo separado para no bloquear la UI
        Executors.newSingleThreadExecutor().execute {
            try {
                // Verificar que el modelo esté cargado
                if (mlModel == null) {
                    runOnUiThread {
                        binding.statusTextView.text = "No hay modelo para evaluar"
                        binding.progressBar.visibility = View.GONE
                        binding.trainButton.isEnabled = true
                        binding.evaluateButton.isEnabled = true
                    }
                    return@execute
                }
                
                // Cargar datos para evaluación
                val gestureDataFolder = File(filesDir, "gesture_data")
                if (!gestureDataFolder.exists() || !gestureDataFolder.isDirectory) {
                    runOnUiThread {
                        binding.statusTextView.text = "No hay datos para evaluar"
                        binding.progressBar.visibility = View.GONE
                        binding.trainButton.isEnabled = true
                        binding.evaluateButton.isEnabled = true
                    }
                    return@execute
                }
                
                // Inicializar métricas
                val metrics = EvaluationMetrics()
                val gestureClasses = mutableSetOf<String>()
                
                // Cargar todos los archivos de señas para evaluación
                val gestureFiles = gestureDataFolder.listFiles()
                if (gestureFiles.isNullOrEmpty()) {
                    runOnUiThread {
                        binding.statusTextView.text = "No hay archivos de señas para evaluar"
                        binding.progressBar.visibility = View.GONE
                        binding.trainButton.isEnabled = true
                        binding.evaluateButton.isEnabled = true
                    }
                    return@execute
                }
                
                // Procesar cada archivo de seña para evaluación
                for (file in gestureFiles) {
                    if (file.isFile && file.name.endsWith(".json")) {
                        val gestureName = file.nameWithoutExtension
                        gestureClasses.add(gestureName)
                        
                        // Inicializar matriz de confusión para esta clase
                        if (!metrics.confusionMatrix.containsKey(gestureName)) {
                            metrics.confusionMatrix[gestureName] = mutableMapOf()
                        }
                        
                        try {
                            // Leer el archivo JSON y extraer los datos
                            val fileContent = file.readText()
                            val gestureDataList = parseGestureDataFromJson(fileContent, gestureName)
                            
                            // Usar un subconjunto para evaluación (30% de los datos)
                            val testSamples = gestureDataList.shuffled().take(gestureDataList.size * 3 / 10)
                            
                            // Evaluar cada muestra
                            for (sample in testSamples) {
                                metrics.totalSamples++
                                val prediction = mlModel?.predict(sample.landmarks) ?: Pair("error", 0f)
                                val predictedClass = prediction.first
                                
                                // Actualizar matriz de confusión
                                metrics.confusionMatrix[gestureName]?.put(
                                    predictedClass, 
                                    (metrics.confusionMatrix[gestureName]?.get(predictedClass) ?: 0) + 1
                                )
                                
                                // Actualizar conteo de predicciones correctas
                                if (predictedClass == gestureName) {
                                    metrics.correctPredictions++
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error al evaluar datos de seña '$gestureName': ${e.message}")
                        }
                    }
                }
                
                // Asegurar que todas las clases estén en la matriz de confusión
                for (className in gestureClasses) {
                    if (!metrics.confusionMatrix.containsKey(className)) {
                        metrics.confusionMatrix[className] = mutableMapOf()
                    }
                    
                    for (predictedClass in gestureClasses) {
                        if (!metrics.confusionMatrix[className]!!.containsKey(predictedClass)) {
                            metrics.confusionMatrix[className]!![predictedClass] = 0
                        }
                    }
                }
                
                // Calcular métricas globales
                val accuracy = metrics.accuracy() * 100
                
                // Calcular métricas por clase
                val classMetrics = gestureClasses.map { className ->
                    val precision = metrics.precision(className) * 100
                    val recall = metrics.recall(className) * 100
                    val f1 = metrics.f1Score(className) * 100
                    
                    "$className: Precisión=${String.format("%.1f", precision)}%, " +
                    "Recall=${String.format("%.1f", recall)}%, " +
                    "F1=${String.format("%.1f", f1)}%"
                }
                
                // Actualizar la UI con los resultados
                runOnUiThread {
                    binding.accuracyTextView.text = "Precisión global: ${String.format("%.1f", accuracy)}%"
                    binding.samplesTextView.text = "Muestras evaluadas: ${metrics.totalSamples}"
                    
                    // Mostrar métricas por clase
                    binding.classMetricsTextView.text = classMetrics.joinToString("\n")
                    
                    binding.statusTextView.text = "Evaluación completada"
                    binding.progressBar.visibility = View.GONE
                    binding.metricsLayout.visibility = View.VISIBLE
                    binding.trainButton.isEnabled = true
                    binding.evaluateButton.isEnabled = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al evaluar el modelo: ${e.message}")
                runOnUiThread {
                    binding.statusTextView.text = "Error al evaluar: ${e.message}"
                    binding.progressBar.visibility = View.GONE
                    binding.trainButton.isEnabled = true
                    binding.evaluateButton.isEnabled = true
                    Toast.makeText(this, "Error al evaluar el modelo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    // Función para parsear datos de gestos desde JSON
    private fun parseGestureDataFromJson(json: String, gestureName: String): List<GestureRecognizerHelper.GestureData> {
        try {
            val result = mutableListOf<GestureRecognizerHelper.GestureData>()
            
            // Parseo manual básico del JSON
            val jsonArray = json.trim().removePrefix("[").removeSuffix("]").split("},{").map { 
                it.replace("{", "").replace("}", "") 
            }
            
            for (jsonObj in jsonArray) {
                val landmarksStr = jsonObj.substringAfter("\"landmarks\":").trim().removePrefix("[").removeSuffix("]").trim()
                
                if (landmarksStr.isNotEmpty()) {
                    val landmarksList = mutableListOf<List<Float>>()
                    
                    // Parsear cada landmark
                    val landmarksArray = landmarksStr.split("],[")
                    for (landmarkStr in landmarksArray) {
                        val coords = landmarkStr.replace("[", "").replace("]", "").split(",")
                            .map { it.trim().toFloat() }
                        landmarksList.add(coords)
                    }
                    
                    if (landmarksList.isNotEmpty()) {
                        result.add(GestureRecognizerHelper.GestureData(gestureName, landmarksList))
                    }
                }
            }
            
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error al parsear JSON: ${e.message}")
            return emptyList()
        }
    }
}