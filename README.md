# VisionApp — Visión por Computador
Sebastian Verdugo y Sebastian Machado

Aplicación Android nativa que procesa video en tiempo real usando OpenCV y C++ mediante JNI.

## Funcionalidades
- **Karl Struss:** Separación de canales RGB y alpha blending entre canal Azul y Rojo controlado por slider (α = 0 a 1)
- **Chroma Key:** Segmentación de fondo verde mediante umbralización HSV, inyección de ruido Gaussiano y Speckle con parámetros ajustables, y atenuación mediante filtros de Mediana o Gaussiano con kernel variable
- **FPS en tiempo real** para medir el impacto del procesamiento nativo en el rendimiento del dispositivo
