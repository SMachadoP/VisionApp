// ─────────────────────────────────────────────────────────────────────────────
// native-lib.cpp  —  VERSIÓN COMPLETA (Parte 1.1 + Parte 1.2)
// Visión por Computador  |  UPS  |  Abril–Agosto 2026
//
// Contiene 4 funciones nativas llamadas desde Java vía JNI:
//   1. procesarFrame     → Efecto Karl Struss  (Parte 1.1)
//   2. aplicarChromaKey  → Eliminar fondo verde/azul  (Parte 1.2)
//   3. inyectarRuido     → Agregar ruido Gaussiano o Speckle  (Parte 1.2)
//   4. aplicarFiltro     → Suavizar ruido (Mediana o Gaussiano)  (Parte 1.2)
// ─────────────────────────────────────────────────────────────────────────────

#include <jni.h>
#include <opencv2/opencv.hpp>

extern "C" {

// ═════════════════════════════════════════════════════════════════════════════
// FUNCIÓN 1 — Karl Struss (Parte 1.1)
//
// Fórmula: Salida(x,y) = (1 - alpha) * B(x,y)  +  alpha * R(x,y)
//   alpha = 0.0 → imagen completamente azul   (marca azul DESAPARECE)
//   alpha = 1.0 → imagen completamente roja   (marca azul APARECE OSCURA)
//
// Entrada:  Mat BGR
// Salida:   Mat escala de grises (1 canal)
// ═════════════════════════════════════════════════════════════════════════════
JNIEXPORT void JNICALL
Java_com_ups_visionapp_MainActivity_procesarFrame(
        JNIEnv *env, jobject thiz,
        jlong  matBGRAddr,
        jlong  matSalidaAddr,
        jfloat alpha) {

    cv::Mat &bgr    = *(cv::Mat *) matBGRAddr;
    cv::Mat &salida = *(cv::Mat *) matSalidaAddr;

    // Separar los 3 canales BGR en matrices individuales
    std::vector<cv::Mat> canales(3);
    cv::split(bgr, canales);
    // canales[0] = Azul (B) | canales[1] = Verde (G) | canales[2] = Rojo (R)

    // Combinar canal Azul y Rojo con la fórmula de alpha blending
    cv::addWeighted(canales[0], 1.0 - alpha,
                    canales[2], alpha,
                    0.0, salida);
}

// ═════════════════════════════════════════════════════════════════════════════
// FUNCIÓN 2 — Chroma Key (Parte 1.2)
//
// Elimina el fondo verde o azul y lo reemplaza por la imagen backgroundAddr.
//
// Entrada:  Mat RGBA (frame de la cámara)
//           Mat BGR  (imagen de fondo a superponer)
// Salida:   Mat BGR  (persona sobre el nuevo fondo)
// ═════════════════════════════════════════════════════════════════════════════
JNIEXPORT void JNICALL
Java_com_ups_visionapp_MainActivity_aplicarChromaKey(
        JNIEnv *env, jobject thiz,
        jlong frameRGBAAddr,
        jlong backgroundBGRAddr,
        jlong salidaAddr,
        jint  usarVerde) {

    cv::Mat &rgba   = *(cv::Mat *) frameRGBAAddr;
    cv::Mat &fondo  = *(cv::Mat *) backgroundBGRAddr;
    cv::Mat &salida = *(cv::Mat *) salidaAddr;

    // 1. Convertir RGBA → BGR para usar las funciones de OpenCV
    cv::Mat bgr;
    cv::cvtColor(rgba, bgr, cv::COLOR_RGBA2BGR);

    // 2. Redimensionar el fondo al tamaño exacto del frame
    cv::Mat fondoResized;
    cv::resize(fondo, fondoResized, bgr.size());

    // 3. Convertir BGR → HSV para segmentar por color
    //    HSV es más robusto que BGR porque separa color (H) de brillo (V)
    cv::Mat hsv;
    cv::cvtColor(bgr, hsv, cv::COLOR_BGR2HSV);

    // 4. Rangos HSV del color a eliminar
    cv::Scalar colorBajo, colorAlto;
    if (usarVerde) {
        colorBajo = cv::Scalar(35, 40, 40);    // Verde: H=35–85
        colorAlto = cv::Scalar(85, 255, 255);
    } else {
        colorBajo = cv::Scalar(100, 40, 40);   // Azul: H=100–130
        colorAlto = cv::Scalar(130, 255, 255);
    }

    // 5. Crear máscara: blanco=fondo a eliminar, negro=persona
    cv::Mat mascara;
    cv::inRange(hsv, colorBajo, colorAlto, mascara);

    // 6. Invertir la máscara para aislar la persona
    cv::Mat mascaraPersona;
    cv::bitwise_not(mascara, mascaraPersona);

    // 7. Extraer persona y nuevo fondo por separado con sus máscaras
    cv::Mat persona, nuevoFondo;
    bgr.copyTo(persona, mascaraPersona);       // Persona: solo donde NO es el color
    fondoResized.copyTo(nuevoFondo, mascara);  // Fondo nuevo: solo donde SÍ era el color

    // 8. Combinar los dos resultados
    cv::add(persona, nuevoFondo, salida);
}

// ═════════════════════════════════════════════════════════════════════════════
// FUNCIÓN 3 — Inyectar Ruido (Parte 1.2)
//
// Agrega ruido Gaussiano o Speckle al frame.
//
// Entrada:  Mat BGR
// Salida:   Mat BGR con ruido
// ═════════════════════════════════════════════════════════════════════════════
JNIEXPORT void JNICALL
Java_com_ups_visionapp_MainActivity_inyectarRuido(
        JNIEnv *env, jobject thiz,
        jlong  frameAddr,
        jlong  salidaAddr,
        jfloat media,
        jfloat varianza,
        jint   tipoRuido) {

    cv::Mat &entrada = *(cv::Mat *) frameAddr;
    cv::Mat &salida  = *(cv::Mat *) salidaAddr;

    float sigma = std::sqrt(varianza);  // sigma = raíz cuadrada de la varianza

    // Crear imagen de ruido del mismo tamaño y tipo float
    cv::Mat ruido(entrada.size(), CV_32FC3);
    cv::randn(ruido, media, sigma);     // Distribución normal con media y sigma

    // Convertir el frame a float para poder operar sin overflow
    cv::Mat frameFloat;
    entrada.convertTo(frameFloat, CV_32F);

    cv::Mat resultado;
    if (tipoRuido == 0) {
        // ── Gaussiano: suma directa ──────────────────────────────────────────
        // Cada píxel = píxel_original + ruido
        resultado = frameFloat + ruido;
    } else {
        // ── Speckle: ruido multiplicativo ────────────────────────────────────
        // Cada píxel = píxel_original * (1 + ruido)
        // Simula el ruido granular de imágenes de radar / ultrasonido
        resultado = frameFloat.mul(1.0f + ruido);
    }

    // Recortar valores al rango [0, 255] y convertir de vuelta a uint8
    resultado = cv::max(resultado, 0.0f);
    resultado = cv::min(resultado, 255.0f);
    resultado.convertTo(salida, CV_8U);
}

// ═════════════════════════════════════════════════════════════════════════════
// FUNCIÓN 4 — Aplicar Filtro Espacial (Parte 1.2)
//
// Suaviza el ruido con filtro de Mediana o Gaussiano.
//
// Entrada:  Mat BGR con ruido
// Salida:   Mat BGR suavizada
// ═════════════════════════════════════════════════════════════════════════════
JNIEXPORT void JNICALL
Java_com_ups_visionapp_MainActivity_aplicarFiltro(
        JNIEnv *env, jobject thiz,
        jlong frameAddr,
        jlong salidaAddr,
        jint  kernelSize,
        jint  tipoFiltro) {

    cv::Mat &entrada = *(cv::Mat *) frameAddr;
    cv::Mat &salida  = *(cv::Mat *) salidaAddr;

    // El kernel debe ser impar (3, 5, 7, 9...). Si llega par, ajustar.
    int k = (kernelSize % 2 == 0) ? kernelSize + 1 : kernelSize;
    if (k < 3) k = 3;  // Mínimo válido

    if (tipoFiltro == 0) {
        // ── Filtro de Mediana ────────────────────────────────────────────────
        // Efectivo contra ruido impulsivo. Reemplaza cada píxel
        // por la mediana de sus vecinos en un área k×k
        cv::medianBlur(entrada, salida, k);
    } else {
        // ── Desenfoque Gaussiano ─────────────────────────────────────────────
        // Efectivo contra ruido continuo (Gaussiano, Speckle).
        // sigma=0 → OpenCV lo calcula en base al tamaño del kernel
        cv::GaussianBlur(entrada, salida, cv::Size(k, k), 0);
    }
}

} // extern "C"