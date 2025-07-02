package com.example.detector1

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.detector1.databinding.ActivityListGesturesBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ListGesturesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListGesturesBinding
    private lateinit var gesturesList: MutableList<GestureInfo>
    private lateinit var adapter: GestureAdapter
    private var mlModel: GestureRecognizerHelper.SimpleMLModel? = null
    
    private val TAG = "ListGesturesActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListGesturesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Inicializar la lista y el adaptador
        gesturesList = mutableListOf()
        adapter = GestureAdapter(this, gesturesList)
        binding.gesturesListView.adapter = adapter
        
        // Cargar el modelo ML existente
        loadMLModel()
        
        // Cargar la lista de señas
        loadGesturesList()
        
        // Configurar el botón de volver
        binding.backButton.setOnClickListener {
            finish()
        }
    }
    
    private fun loadMLModel() {
        try {
            mlModel = GestureRecognizerHelper.SimpleMLModel()
            val modelFile = File(filesDir, "gesture_model.ml")
            if (modelFile.exists()) {
                val loaded = mlModel?.loadModel(this, "gesture_model") ?: false
                if (loaded) {
                    Log.d(TAG, "Modelo ML cargado correctamente")
                } else {
                    Log.e(TAG, "Error al cargar el modelo ML")
                    Toast.makeText(this, "Error al cargar el modelo de señas", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.w(TAG, "No existe archivo de modelo ML")
                Toast.makeText(this, "No hay modelo de señas entrenado", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al inicializar el modelo ML: ${e.message}")
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadGesturesList() {
        try {
            // Limpiar la lista actual
            gesturesList.clear()
            
            // Obtener la carpeta de datos de gestos
            val gestureDataFolder = File(filesDir, "gesture_data")
            if (!gestureDataFolder.exists() || !gestureDataFolder.isDirectory) {
                binding.emptyView.visibility = View.VISIBLE
                adapter.notifyDataSetChanged()
                return
            }
            
            // Obtener todos los archivos de gestos
            val gestureFiles = gestureDataFolder.listFiles()
            if (gestureFiles.isNullOrEmpty()) {
                binding.emptyView.visibility = View.VISIBLE
                adapter.notifyDataSetChanged()
                return
            }
            
            // Ocultar el mensaje de lista vacía
            binding.emptyView.visibility = View.GONE
            
            // Añadir cada gesto a la lista
            for (file in gestureFiles) {
                if (file.isFile && file.name.endsWith(".json")) {
                    val gestureName = file.nameWithoutExtension
                    val lastModified = Date(file.lastModified())
                    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    val formattedDate = dateFormat.format(lastModified)
                    
                    gesturesList.add(GestureInfo(gestureName, formattedDate, file))
                }
            }
            
            // Ordenar por fecha de modificación (más reciente primero)
            gesturesList.sortByDescending { it.file.lastModified() }
            
            // Notificar al adaptador
            adapter.notifyDataSetChanged()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error al cargar la lista de señas: ${e.message}")
            Toast.makeText(this, "Error al cargar la lista de señas", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun deleteGesture(gestureInfo: GestureInfo) {
        try {
            // Mostrar diálogo de confirmación
            AlertDialog.Builder(this)
                .setTitle("Eliminar seña")
                .setMessage("¿Estás seguro de que deseas eliminar la seña '${gestureInfo.name}'? Esta acción no se puede deshacer y la seña ya no será reconocida.")
                .setPositiveButton("Eliminar") { _, _ ->
                    // 1. Eliminar el archivo de datos de la seña
                    if (gestureInfo.file.exists()) {
                        val deleted = gestureInfo.file.delete()
                        if (deleted) {
                            Log.d(TAG, "Archivo de seña eliminado: ${gestureInfo.file.name}")
                            
                            // 2. Eliminar la seña del modelo ML
                            removeGestureFromModel(gestureInfo.name)
                            
                            // 3. Actualizar la lista
                            gesturesList.remove(gestureInfo)
                            adapter.notifyDataSetChanged()
                            
                            // 4. Mostrar mensaje de éxito
                            Toast.makeText(this, "Seña '${gestureInfo.name}' eliminada correctamente", Toast.LENGTH_SHORT).show()
                            
                            // 5. Mostrar vista vacía si no hay más señas
                            if (gesturesList.isEmpty()) {
                                binding.emptyView.visibility = View.VISIBLE
                            }
                        } else {
                            Log.e(TAG, "No se pudo eliminar el archivo de seña: ${gestureInfo.file.name}")
                            Toast.makeText(this, "Error al eliminar la seña", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar la seña: ${e.message}")
            Toast.makeText(this, "Error al eliminar la seña: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun removeGestureFromModel(gestureName: String) {
        try {
            // Cargar todos los datos de entrenamiento excepto los de la seña a eliminar
            val context = this
            val tempModel = GestureRecognizerHelper.SimpleMLModel()
            
            // Recorrer todos los archivos de señas excepto el eliminado
            val gestureDataFolder = File(filesDir, "gesture_data")
            if (gestureDataFolder.exists() && gestureDataFolder.isDirectory) {
                val gestureFiles = gestureDataFolder.listFiles()
                if (!gestureFiles.isNullOrEmpty()) {
                    // Crear una lista para almacenar los datos de cada seña
                    val allGestureData = mutableListOf<GestureRecognizerHelper.GestureData>()
                    
                    // Reentrenar el modelo con todas las señas excepto la eliminada
                    for (file in gestureFiles) {
                        if (file.isFile && file.name.endsWith(".json") && file.nameWithoutExtension != gestureName) {
                            try {
                                // Leer el archivo JSON y extraer los datos
                                val fileContent = file.readText()
                                val gestureDataList = parseGestureDataFromJson(fileContent, file.nameWithoutExtension)
                                if (gestureDataList.isNotEmpty()) {
                                    // Añadir los datos al modelo
                                    tempModel.addTrainingData(file.nameWithoutExtension, gestureDataList)
                                    Log.d(TAG, "Datos de seña '${file.nameWithoutExtension}' añadidos al modelo")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error al cargar datos de seña '${file.nameWithoutExtension}': ${e.message}")
                            }
                        }
                    }
                    
                    // Entrenar el modelo con los datos cargados
                    tempModel.train()
                }
            }
            
            // Guardar el modelo actualizado (sin la seña eliminada)
            tempModel.saveModel(context, "gesture_model")
            
            Log.d(TAG, "Seña '$gestureName' eliminada del modelo ML")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar la seña del modelo ML: ${e.message}")
            // No mostramos Toast aquí porque ya se muestra en deleteGesture
        }
    }
    
    // Función para parsear los datos de gestos desde JSON
    private fun parseGestureDataFromJson(jsonString: String, gestureName: String): List<GestureRecognizerHelper.GestureData> {
        val result = mutableListOf<GestureRecognizerHelper.GestureData>()
        try {
            // Parseo manual básico del JSON
            val jsonArray = jsonString.trim().removePrefix("[").removeSuffix("]").split("},{").map { 
                it.removePrefix("{").removeSuffix("}") 
            }
            
            for (jsonObj in jsonArray) {
                // Extraer los landmarks
                val landmarksStart = jsonObj.indexOf("\"landmarks\":[")
                if (landmarksStart != -1) {
                    val landmarksJson = jsonObj.substring(landmarksStart + 13)
                    val landmarksEnd = findMatchingBracket(landmarksJson, 0)
                    if (landmarksEnd != -1) {
                        val landmarksContent = landmarksJson.substring(0, landmarksEnd)
                        
                        // Parsear los landmarks
                        val landmarks = parseLandmarks(landmarksContent)
                        if (landmarks.isNotEmpty()) {
                            result.add(GestureRecognizerHelper.GestureData(gestureName, landmarks))
                        }
                    }
                }
            }
            
            Log.d(TAG, "Parseados ${result.size} frames de datos para la seña '$gestureName'")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error al parsear JSON de seña: ${e.message}")
            return emptyList()
        }
    }
    
    // Encuentra el índice del corchete de cierre correspondiente
    private fun findMatchingBracket(str: String, startIndex: Int): Int {
        var count = 1
        var i = startIndex
        while (i < str.length) {
            when (str[i]) {
                '[' -> count++
                ']' -> {
                    count--
                    if (count == 0) return i
                }
            }
            i++
        }
        return -1
    }
    
    // Parsea los landmarks desde el JSON
    private fun parseLandmarks(landmarksJson: String): List<List<Float>> {
        val result = mutableListOf<List<Float>>()
        try {
            // Dividir por corchetes
            val landmarkArrays = landmarksJson.split("],[")
                .map { it.removePrefix("[").removeSuffix("]") }
            
            for (landmarkArray in landmarkArrays) {
                val coordinates = landmarkArray.split(",")
                    .map { it.trim().toFloat() }
                
                if (coordinates.size >= 3) {
                    result.add(coordinates)
                }
            }
            
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error al parsear landmarks: ${e.message}")
            return emptyList()
        }
    }
    
    // Clase para almacenar información de cada seña
    data class GestureInfo(
        val name: String,
        val date: String,
        val file: File
    )
    
    // Adaptador personalizado para la lista de señas
    inner class GestureAdapter(context: Context, private val gestures: List<GestureInfo>) :
        ArrayAdapter<GestureInfo>(context, 0, gestures) {
        
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_gesture, parent, false)
            
            val gesture = gestures[position]
            
            // Configurar las vistas del elemento
            val nameTextView = view.findViewById<TextView>(R.id.gestureNameTextView)
            val dateTextView = view.findViewById<TextView>(R.id.gestureDateTextView)
            val deleteButton = view.findViewById<View>(R.id.deleteGestureButton)
            
            nameTextView.text = gesture.name
            dateTextView.text = "Creada: ${gesture.date}"
            
            // Configurar el botón de eliminar
            deleteButton.setOnClickListener {
                deleteGesture(gesture)
            }
            
            return view
        }
    }
}