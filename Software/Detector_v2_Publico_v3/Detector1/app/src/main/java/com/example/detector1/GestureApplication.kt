package com.example.detector1

import android.app.Application
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class GestureApplication : Application() {
    
    private val TAG = "GestureApplication"
    
    override fun onCreate() {
        super.onCreate()
        
        // Inicializar la aplicación copiando los archivos preexistentes
        initializePreexistingFiles()
    }
    
    private fun initializePreexistingFiles() {
        try {
            // Verificar si los archivos preexistentes están disponibles en assets
            val preexistingModelPath = "preexisting/gesture_model.ml"
            val preexistingDataPath = "preexisting/gesture_data"
            
            try {
                assets.open(preexistingModelPath).close()
                val gestureFiles = assets.list(preexistingDataPath)
                if (gestureFiles == null || gestureFiles.isEmpty()) {
                    throw IOException("No se encontraron archivos de señas en assets/$preexistingDataPath")
                }
                Log.d(TAG, "Archivos preexistentes verificados en assets: ${gestureFiles.size} señas")
            } catch (e: IOException) {
                Log.e(TAG, "Error al verificar archivos preexistentes en assets: ${e.message}")
                Toast.makeText(this, "Error: No se encontraron archivos preexistentes en assets", Toast.LENGTH_LONG).show()
                return
            }
            
            // Verificar archivos internos existentes
            val modelFile = File(filesDir, "gesture_model.ml")
            val gestureDataFolder = File(filesDir, "gesture_data")
            
            // Si no existe el modelo o la carpeta de datos, copiar los preexistentes
            if (!modelFile.exists() || !gestureDataFolder.exists() || gestureDataFolder.list()?.isEmpty() == true) {
                Log.i(TAG, "Iniciando carga de archivos preexistentes...")
                Toast.makeText(this, "Cargando modelo y señas preexistentes...", Toast.LENGTH_LONG).show()
                
                // Crear la carpeta de datos si no existe
                if (!gestureDataFolder.exists()) {
                    if (!gestureDataFolder.mkdirs()) {
                        throw IOException("No se pudo crear la carpeta de datos en: ${gestureDataFolder.absolutePath}")
                    }
                    Log.i(TAG, "Carpeta de datos creada en: ${gestureDataFolder.absolutePath}")
                }
                
                // Copiar el modelo preentrenado
                if (!modelFile.exists()) {
                    copyAssetToInternal(preexistingModelPath, modelFile)
                    if (!modelFile.exists() || modelFile.length() == 0L) {
                        throw IOException("Error al copiar el modelo: archivo no creado o vacío")
                    }
                    Log.i(TAG, "Modelo preentrenado copiado correctamente a: ${modelFile.absolutePath}")
                }
                
                // Copiar los archivos de señas preexistentes
                var gestureCount = 0
                val gestureFiles = assets.list(preexistingDataPath)
                if (gestureFiles != null) {
                    for (fileName in gestureFiles) {
                        val destFile = File(gestureDataFolder, fileName)
                        if (!destFile.exists()) {
                            copyAssetToInternal(
                                "$preexistingDataPath/$fileName",
                                destFile
                            )
                            if (!destFile.exists() || destFile.length() == 0L) {
                                Log.w(TAG, "Advertencia: Archivo de seña no copiado o vacío: $fileName")
                                continue
                            }
                            gestureCount++
                            Log.i(TAG, "Seña copiada: $fileName en ${destFile.absolutePath}")
                        }
                    }
                }
                
                if (gestureCount == 0) {
                    throw IOException("No se pudo copiar ningún archivo de seña")
                }
                
                val successMessage = "Archivos preexistentes cargados: $gestureCount señas y modelo"
                Log.i(TAG, successMessage)
                Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show()
            } else {
                // Verificar integridad de archivos existentes
                var needsReload = false
                if (!modelFile.exists() || modelFile.length() == 0L) {
                    Log.w(TAG, "Modelo existente corrupto o vacío, se recargará")
                    needsReload = true
                }
                
                val existingGestures = gestureDataFolder.list()
                if (existingGestures == null || existingGestures.isEmpty()) {
                    Log.w(TAG, "Carpeta de señas vacía, se recargarán los archivos")
                    needsReload = true
                }
                
                if (needsReload) {
                    Log.i(TAG, "Recargando archivos por inconsistencias detectadas")
                    modelFile.delete()
                    gestureDataFolder.deleteRecursively()
                    initializePreexistingFiles()
                    return
                }
                
                Log.i(TAG, "Archivos existentes verificados en: ${filesDir.absolutePath}")
                Log.i(TAG, "Estado de archivos - Modelo: ${modelFile.exists()}, Señas: ${existingGestures?.size ?: 0}")
            }
        } catch (e: Exception) {
            val errorMsg = "Error al inicializar archivos preexistentes: ${e.message}"
            Log.e(TAG, errorMsg)
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
        }
    }
    
    private fun copyAssetToInternal(assetPath: String, destFile: File) {
        try {
            assets.open(assetPath).use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error al copiar asset $assetPath: ${e.message}")
            throw e
        }
    }
}