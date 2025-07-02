# Detector de Señas - Versión Pública

## Descripción

Esta es la versión pública de la aplicación "Detector de Señas", que incluye un modelo preentrenado y un conjunto de señas predefinidas. A diferencia de la versión propietaria, esta versión está diseñada para que los usuarios puedan partir de un modelo ya entrenado y añadir nuevas señas sin necesidad de configuraciones adicionales.

## Características principales

- **Modelo preentrenado**: La aplicación incluye un modelo de reconocimiento de señas ya entrenado.
- **Señas predefinidas**: Incluye un conjunto de señas predefinidas listas para ser reconocidas.
- **Reentrenamiento automático**: Al grabar nuevas señas, el modelo se reentrena automáticamente.
- **Interfaz simplificada**: Se han eliminado las opciones de "Entrenar Modelo" y "Exportar Datos".

## Funcionalidades disponibles

1. **Evaluación**: Permite evaluar el reconocimiento de señas en tiempo real.
2. **Recolección**: Permite grabar nuevas señas que se añaden a las existentes y reentrenan el modelo automáticamente.
3. **Listado de Señas**: Muestra todas las señas disponibles, tanto las predefinidas como las añadidas por el usuario.

## Cómo funciona

Al instalar la aplicación por primera vez, el modelo preentrenado y las señas predefinidas se cargan automáticamente. Cuando el usuario graba una nueva seña:

1. La seña se guarda junto con las predefinidas.
2. El modelo se reentrena automáticamente incluyendo todas las señas (predefinidas y nuevas).
3. El modelo actualizado se utiliza inmediatamente para el reconocimiento.

## Preparación de la versión pública

Para preparar la versión pública a partir de la versión propietaria:

1. Ejecute el script `prepare_public_version.bat` que copiará los archivos necesarios.
2. Compile la aplicación normalmente.

El script copiará el modelo entrenado (`gesture_model.ml`) y los archivos de señas (`gesture_data/*.json`) a la carpeta de assets para que se incluyan en la APK.

## Notas técnicas

- Los archivos preexistentes se almacenan en `app/src/main/assets/preexisting/`.
- Al iniciar la aplicación por primera vez, estos archivos se copian a la carpeta de archivos internos de la aplicación.
- El reentrenamiento del modelo ocurre automáticamente después de guardar una nueva seña.