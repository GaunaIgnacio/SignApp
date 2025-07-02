@echo off
echo Preparando version publica de la aplicacion de reconocimiento de senas
echo =====================================================================

:: Crear directorios necesarios en assets
mkdir "app\src\main\assets\preexisting\gesture_data" 2>nul

:: Copiar el modelo preentrenado
echo Copiando modelo preentrenado...
copy "gesture_recognition_20250501_123115\gesture_model.ml" "app\src\main\assets\preexisting\" /Y

:: Copiar los archivos de senas
echo Copiando archivos de senas...
copy "gesture_recognition_20250501_123115\gesture_data\*.json" "app\src\main\assets\preexisting\gesture_data\" /Y

echo.
echo Archivos copiados correctamente.
echo La version publica esta lista para ser compilada.
echo.
echo NOTA: Recuerde que la version publica no incluira los botones
echo       "Entrenar Modelo" y "Exportar Datos".
echo.
pause