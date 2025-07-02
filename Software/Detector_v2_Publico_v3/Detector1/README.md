# Detector de Lengua de Señas

Esta aplicación Android permite reconocer gestos de lengua de señas utilizando MediaPipe para la detección de manos y el reconocimiento de gestos.

## Funcionalidades

La aplicación cuenta con dos modos principales:

1. **Recolectar Seña**:
   - Permite grabar los movimientos de la mano para una seña específica
   - Almacena los datos de los landmarks de la mano para entrenamiento
   - Etiqueta cada seña con un nombre proporcionado por el usuario

2. **Evaluación**:
   - Activa la cámara en tiempo real
   - Reconoce señas previamente guardadas
   - Muestra en pantalla el nombre de la seña reconocida

## Requisitos

- Android 7.0 (API 24) o superior
- Cámara frontal
- Permisos de cámara y almacenamiento

## Configuración inicial

Para utilizar la aplicación, necesitas descargar el modelo de reconocimiento de gestos de MediaPipe y colocarlo en la carpeta `app/src/main/assets/` con el nombre `gesture_recognizer.task`.

Puedes descargar el modelo desde:
https://storage.googleapis.com/mediapipe-models/gesture_recognizer/gesture_recognizer/float16/1/gesture_recognizer.task

## Uso

1. **Recolectar Seña**:
   - Ingresa el nombre de la seña (por ejemplo, "Hola")
   - Presiona "Iniciar grabación"
   - Realiza la seña frente a la cámara
   - Presiona "Detener grabación"
   - Presiona "Guardar" para almacenar los datos

2. **Evaluación**:
   - Presiona "Iniciar evaluación"
   - Realiza las señas frente a la cámara
   - La aplicación mostrará el nombre de la seña detectada

## Tecnologías utilizadas

- Kotlin
- CameraX
- MediaPipe (GestureRecognizer y HandLandmarker)
- Android Jetpack

## Adaptación para lengua de señas

La aplicación permite recolectar y reconocer señas personalizadas, ideal para crear un conjunto de datos para un lenguaje de señas específico. 