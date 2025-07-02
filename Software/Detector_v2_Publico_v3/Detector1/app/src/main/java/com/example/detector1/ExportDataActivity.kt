package com.example.detector1

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.detector1.databinding.ActivityExportDataBinding
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportDataActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExportDataBinding
    private val TAG = "ExportDataActivity"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExportDataBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Verificar si hay datos para exportar
        checkAvailableData()
        
        // Configurar botones
        binding.exportModelButton.setOnClickListener {
            exportModel()
        }
        
        binding.exportDatasetButton.setOnClickListener {
            exportDataset()
        }
        
        binding.exportAllButton.setOnClickListener {
            exportAll()
        }
        
        binding.backButton.setOnClickListener {
            finish()
        }
    }
    
    private fun checkAvailableData() {
        // Verificar si existe el modelo entrenado
        val modelFile = File(filesDir, "gesture_model.ml")
        binding.exportModelButton.isEnabled = modelFile.exists()
        
        // Verificar si existen datos de señas
        val gestureDataFolder = File(filesDir, "gesture_data")
        val hasGestureData = gestureDataFolder.exists() && 
                            gestureDataFolder.isDirectory && 
                            gestureDataFolder.listFiles()?.isNotEmpty() == true
        
        binding.exportDatasetButton.isEnabled = hasGestureData
        binding.exportAllButton.isEnabled = modelFile.exists() && hasGestureData
        
        // Actualizar el estado
        updateStatus()
    }
    
    private fun updateStatus() {
        val modelFile = File(filesDir, "gesture_model.ml")
        val gestureDataFolder = File(filesDir, "gesture_data")
        
        val modelStatus = if (modelFile.exists()) {
            val lastModified = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(Date(modelFile.lastModified()))
            "Modelo disponible (última actualización: $lastModified)"
        } else {
            "No hay modelo entrenado disponible"
        }
        
        val datasetStatus = if (gestureDataFolder.exists() && gestureDataFolder.isDirectory) {
            val gestureFiles = gestureDataFolder.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }
            val gestureCount = gestureFiles?.size ?: 0
            
            if (gestureCount > 0) {
                "Dataset disponible: $gestureCount señas"
            } else {
                "No hay señas grabadas disponibles"
            }
        } else {
            "No hay señas grabadas disponibles"
        }
        
        binding.modelStatusTextView.text = modelStatus
        binding.datasetStatusTextView.text = datasetStatus
    }
    
    private fun exportModel() {
        binding.progressBar.visibility = View.VISIBLE
        binding.statusTextView.text = "Exportando modelo..."
        disableButtons()
        
        Executors.newSingleThreadExecutor().execute {
            try {
                val modelFile = File(filesDir, "gesture_model.ml")
                if (!modelFile.exists()) {
                    runOnUiThread {
                        binding.statusTextView.text = "Error: No se encontró el modelo"
                        binding.progressBar.visibility = View.GONE
                        enableButtons()
                    }
                    return@execute
                }
                
                // Crear carpeta de exportación si no existe
                val exportDir = File(getExternalFilesDir(null), "exports")
                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }
                
                // Crear nombre de archivo con timestamp
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val exportFile = File(exportDir, "gesture_model_$timestamp.ml")
                
                // Copiar el archivo
                FileInputStream(modelFile).use { input ->
                    FileOutputStream(exportFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                runOnUiThread {
                    binding.statusTextView.text = "Modelo exportado correctamente a:\n${exportFile.absolutePath}"
                    binding.progressBar.visibility = View.GONE
                    enableButtons()
                    Toast.makeText(this, "Modelo exportado correctamente", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al exportar modelo: ${e.message}")
                runOnUiThread {
                    binding.statusTextView.text = "Error al exportar modelo: ${e.message}"
                    binding.progressBar.visibility = View.GONE
                    enableButtons()
                    Toast.makeText(this, "Error al exportar modelo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun exportDataset() {
        binding.progressBar.visibility = View.VISIBLE
        binding.statusTextView.text = "Exportando dataset..."
        disableButtons()
        
        Executors.newSingleThreadExecutor().execute {
            try {
                val gestureDataFolder = File(filesDir, "gesture_data")
                if (!gestureDataFolder.exists() || !gestureDataFolder.isDirectory) {
                    runOnUiThread {
                        binding.statusTextView.text = "Error: No se encontraron datos de señas"
                        binding.progressBar.visibility = View.GONE
                        enableButtons()
                    }
                    return@execute
                }
                
                // Crear carpeta de exportación si no existe
                val exportDir = File(getExternalFilesDir(null), "exports")
                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }
                
                // Crear nombre de archivo con timestamp
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val exportFile = File(exportDir, "gesture_dataset_$timestamp.zip")
                
                // Crear archivo ZIP con todos los datos
                ZipOutputStream(FileOutputStream(exportFile)).use { zipOut ->
                    val gestureFiles = gestureDataFolder.listFiles()
                    if (gestureFiles != null) {
                        for (file in gestureFiles) {
                            if (file.isFile && file.name.endsWith(".json")) {
                                val zipEntry = ZipEntry(file.name)
                                zipOut.putNextEntry(zipEntry)
                                
                                FileInputStream(file).use { input ->
                                    input.copyTo(zipOut)
                                }
                                
                                zipOut.closeEntry()
                            }
                        }
                    }
                }
                
                // También exportar en formato CSV para compatibilidad
                val csvFile = File(exportDir, "gesture_dataset_$timestamp.csv")
                FileOutputStream(csvFile).use { output ->
                    // Escribir encabezado CSV
                    output.write("gesture_name,landmark_index,x,y,z\n".toByteArray())
                    
                    // Procesar cada archivo de seña
                    val gestureFiles = gestureDataFolder.listFiles()
                    if (gestureFiles != null) {
                        for (file in gestureFiles) {
                            if (file.isFile && file.name.endsWith(".json")) {
                                val gestureName = file.nameWithoutExtension
                                val fileContent = file.readText()
                                
                                // Parsear JSON y convertir a CSV
                                val gestureDataList = parseGestureDataFromJson(fileContent, gestureName)
                                for ((sampleIndex, sample) in gestureDataList.withIndex()) {
                                    for ((landmarkIndex, landmark) in sample.landmarks.withIndex()) {
                                        val x = landmark.getOrNull(0) ?: 0f
                                        val y = landmark.getOrNull(1) ?: 0f
                                        val z = landmark.getOrNull(2) ?: 0f
                                        
                                        val line = "$gestureName,$landmarkIndex,$x,$y,$z\n"
                                        output.write(line.toByteArray())
                                    }
                                }
                            }
                        }
                    }
                }
                
                runOnUiThread {
                    binding.statusTextView.text = "Dataset exportado correctamente a:\n${exportFile.absolutePath}\n${csvFile.absolutePath}"
                    binding.progressBar.visibility = View.GONE
                    enableButtons()
                    Toast.makeText(this, "Dataset exportado correctamente", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al exportar dataset: ${e.message}")
                runOnUiThread {
                    binding.statusTextView.text = "Error al exportar dataset: ${e.message}"
                    binding.progressBar.visibility = View.GONE
                    enableButtons()
                    Toast.makeText(this, "Error al exportar dataset: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun exportAll() {
        binding.progressBar.visibility = View.VISIBLE
        binding.statusTextView.text = "Exportando modelo y dataset..."
        disableButtons()
        
        Executors.newSingleThreadExecutor().execute {
            try {
                val modelFile = File(filesDir, "gesture_model.ml")
                val gestureDataFolder = File(filesDir, "gesture_data")
                
                if (!modelFile.exists() || !gestureDataFolder.exists() || !gestureDataFolder.isDirectory) {
                    runOnUiThread {
                        binding.statusTextView.text = "Error: No se encontraron todos los datos necesarios"
                        binding.progressBar.visibility = View.GONE
                        enableButtons()
                    }
                    return@execute
                }
                
                // Crear carpeta de exportación si no existe
                val exportDir = File(getExternalFilesDir(null), "exports")
                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }
                
                // Crear nombre de archivo con timestamp
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val exportFile = File(exportDir, "gesture_recognition_$timestamp.zip")
                
                // Crear archivo ZIP con todos los datos
                ZipOutputStream(FileOutputStream(exportFile)).use { zipOut ->
                    // Añadir el modelo
                    val modelEntry = ZipEntry("gesture_model.ml")
                    zipOut.putNextEntry(modelEntry)
                    
                    FileInputStream(modelFile).use { input ->
                        input.copyTo(zipOut)
                    }
                    
                    zipOut.closeEntry()
                    
                    // Añadir los archivos de señas
                    val gestureFiles = gestureDataFolder.listFiles()
                    if (gestureFiles != null) {
                        for (file in gestureFiles) {
                            if (file.isFile && file.name.endsWith(".json")) {
                                val zipEntry = ZipEntry("gesture_data/${file.name}")
                                zipOut.putNextEntry(zipEntry)
                                
                                FileInputStream(file).use { input ->
                                    input.copyTo(zipOut)
                                }
                                
                                zipOut.closeEntry()
                            }
                        }
                    }
                    
                    // Añadir un archivo README con instrucciones
                    val readmeEntry = ZipEntry("README.txt")
                    zipOut.putNextEntry(readmeEntry)
                    
                    val readmeContent = """DETECTOR DE SEÑAS - ARCHIVOS EXPORTADOS
                        |
                        |Este archivo contiene el modelo entrenado y los datos de señas utilizados para el entrenamiento.
                        |
                        |Contenido:
                        |1. gesture_model.ml: Modelo entrenado listo para usar en la aplicación.
                        |2. gesture_data/: Carpeta con los archivos JSON de cada seña grabada.
                        |
                        |Para usar estos archivos en la aplicación pública:
                        |1. Copie el archivo gesture_model.ml en la carpeta de archivos internos de la aplicación.
                        |2. La aplicación pública cargará automáticamente el modelo al iniciar.
                        |
                        |Fecha de exportación: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}
                        |
                        |NOTA: Este archivo es para uso exclusivo con la aplicación de reconocimiento de señas.
                    """.trimMargin()
                    
                    zipOut.write(readmeContent.toByteArray())
                    zipOut.closeEntry()
                }
                
                runOnUiThread {
                    binding.statusTextView.text = "Modelo y dataset exportados correctamente a:\n${exportFile.absolutePath}"
                    binding.progressBar.visibility = View.GONE
                    enableButtons()
                    Toast.makeText(this, "Exportación completada correctamente", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al exportar datos: ${e.message}")
                runOnUiThread {
                    binding.statusTextView.text = "Error al exportar datos: ${e.message}"
                    binding.progressBar.visibility = View.GONE
                    enableButtons()
                    Toast.makeText(this, "Error al exportar datos: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun disableButtons() {
        binding.exportModelButton.isEnabled = false
        binding.exportDatasetButton.isEnabled = false
        binding.exportAllButton.isEnabled = false
        binding.backButton.isEnabled = false
    }
    
    private fun enableButtons() {
        // Verificar disponibilidad de datos nuevamente
        checkAvailableData()
        binding.backButton.isEnabled = true
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