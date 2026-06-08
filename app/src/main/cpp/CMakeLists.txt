# ─────────────────────────────────────────────────────────────────────────────
# CMakeLists.txt  —  Configuración de compilación C++ para Android + OpenCV
# ─────────────────────────────────────────────────────────────────────────────

# Versión mínima de CMake requerida por Android Studio
cmake_minimum_required(VERSION 3.22.1)

# Nombre del proyecto C++
project("visionapp")

# ── Ruta al SDK de OpenCV para Android ───────────────────────────────────────
# Esta ruta apunta a la carpeta que copiaste dentro del proyecto.
# CMAKE_CURRENT_SOURCE_DIR = app/src/main/cpp/
# Entonces esta ruta busca:  app/OpenCV-android-sdk/sdk/native/jni
set(OpenCV_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../opencv-4.10.0-android-sdk/OpenCV-android-sdk/sdk/native/jni")
# Buscar OpenCV. Lanza error si no lo encuentra en la ruta de arriba.
find_package(OpenCV REQUIRED)

# ── Crear la librería compartida (.so) que Android cargará vía JNI ────────────
# SHARED = librería dinámica (.so), requerida para JNI en Android
add_library(native-lib SHARED native-lib.cpp)

# Librería de logs de Android (para usar __android_log_print si se necesita)
find_library(log-lib log)

# ── Enlazar OpenCV y Android log a nuestra librería ───────────────────────────
target_link_libraries(
        native-lib
        ${OpenCV_LIBS}   # todas las librerías de OpenCV
        ${log-lib}       # android/log.h
)