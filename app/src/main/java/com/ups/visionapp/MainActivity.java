package com.ups.visionapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraActivity;
import org.opencv.android.JavaCameraView;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.Collections;
import java.util.List;

import org.opencv.android.OpenCVLoader;

public class MainActivity extends CameraActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
    static { System.loadLibrary("native-lib"); }

    private JavaCameraView cameraView;
    private Button         btnModoKarl, btnModoChroma;
    private LinearLayout   panelKarl, panelChroma;
    private TextView       tvAlpha, tvFps, tvEstado;
    private SeekBar        seekAlpha, seekMedia, seekVarianza, seekKernel;
    private Switch         switchRuido, switchFiltro, switchFondo;

    private int modoActual = 1;

    private volatile float alpha    = 0.5f;
    private volatile float media    = 0f;
    private volatile float varianza = 500f;
    private volatile int   kernel   = 5;
    private volatile int   tipoRuido  = 0;
    private volatile int   tipoFiltro = 0;
    private volatile int   usarVerde  = 1;

    private Mat  matFondo;
    private long tiempoUltimoFrame = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Inicializar OpenCV PRIMERO
        if (!OpenCVLoader.initLocal()) {
            return;
        }

        setContentView(R.layout.activity_main);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cameraView = findViewById(R.id.camera_view);
        cameraView.setCvCameraViewListener(this);
        cameraView.enableView();

        btnModoKarl   = findViewById(R.id.btn_modo_karl);
        btnModoChroma = findViewById(R.id.btn_modo_chroma);
        panelKarl     = findViewById(R.id.panel_karl);
        panelChroma   = findViewById(R.id.panel_chroma);
        tvAlpha       = findViewById(R.id.tv_alpha);
        tvFps         = findViewById(R.id.tv_fps);
        tvEstado      = findViewById(R.id.tv_estado);
        seekAlpha     = findViewById(R.id.seek_alpha);
        seekMedia     = findViewById(R.id.seek_media);
        seekVarianza  = findViewById(R.id.seek_varianza);
        seekKernel    = findViewById(R.id.seek_kernel);
        switchRuido   = findViewById(R.id.switch_ruido);
        switchFiltro  = findViewById(R.id.switch_filtro);
        switchFondo   = findViewById(R.id.switch_fondo);


        btnModoKarl.setOnClickListener(v -> activarModo(1));
        btnModoChroma.setOnClickListener(v -> activarModo(2));

        seekAlpha.setMax(100);
        seekAlpha.setProgress(50);
        seekAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                alpha = progress / 100.0f;
                tvAlpha.setText(String.format("α = %.2f", alpha));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar)   {}
        });

        seekMedia.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                media = progress; actualizarEtiquetaEstado();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar)   {}
        });

        seekVarianza.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                varianza = progress; actualizarEtiquetaEstado();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar)   {}
        });

        seekKernel.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                kernel = Math.max(3, progress); actualizarEtiquetaEstado();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar)   {}
        });

        switchRuido.setOnCheckedChangeListener((btn, checked)  -> { tipoRuido  = checked ? 1 : 0; actualizarEtiquetaEstado(); });
        switchFiltro.setOnCheckedChangeListener((btn, checked) -> { tipoFiltro = checked ? 1 : 0; actualizarEtiquetaEstado(); });
        switchFondo.setOnCheckedChangeListener((btn, checked)  -> { usarVerde  = checked ? 0 : 1; actualizarEtiquetaEstado(); });

        try {
            Bitmap bmp = BitmapFactory.decodeStream(getAssets().open("fondo.jpeg"));
            matFondo = new Mat();
            Utils.bitmapToMat(bmp, matFondo);
            Imgproc.cvtColor(matFondo, matFondo, Imgproc.COLOR_RGBA2BGR);
        } catch (Exception e) {
            matFondo = new Mat(480, 640, CvType.CV_8UC3, new Scalar(0, 180, 0));
        }

        activarModo(1);
    }

    private void activarModo(int modo) {
        modoActual = modo;
        if (modo == 1) {
            panelKarl.setVisibility(View.VISIBLE);
            panelChroma.setVisibility(View.GONE);
            btnModoKarl.setBackgroundColor(0xFF1565C0);
            btnModoChroma.setBackgroundColor(0xFF424242);
        } else {
            panelKarl.setVisibility(View.GONE);
            panelChroma.setVisibility(View.VISIBLE);
            btnModoKarl.setBackgroundColor(0xFF424242);
            btnModoChroma.setBackgroundColor(0xFF1565C0);
        }
        actualizarEtiquetaEstado();
    }

    private void actualizarEtiquetaEstado() {
        if (modoActual == 1) {
            tvEstado.setText(String.format("Azul(%.2f) + Rojo(%.2f)", 1 - alpha, alpha));
        } else {
            tvEstado.setText(String.format("Fondo:%s Ruido:%s Media:%.0f Var:%.0f K:%d Filtro:%s",
                    usarVerde == 1 ? "Verde" : "Azul",
                    tipoRuido == 0 ? "Gauss" : "Speckle",
                    media, varianza, kernel,
                    tipoFiltro == 0 ? "Mediana" : "Gauss"));
        }
    }

    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(cameraView);
    }

    public void onCameraViewStarted(int width, int height) {}
    public void onCameraViewStopped() {}

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        long ahora = System.currentTimeMillis();
        if (tiempoUltimoFrame != 0) {
            final double fps = 1000.0 / (ahora - tiempoUltimoFrame);
            runOnUiThread(() -> tvFps.setText(String.format("FPS: %.1f", fps)));
        }
        tiempoUltimoFrame = ahora;

        if (modoActual == 1) {
            Mat rgba   = inputFrame.rgba();
            Mat bgr    = new Mat();
            Mat salida = new Mat(rgba.rows(), rgba.cols(), CvType.CV_8UC1);
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR);
            procesarFrame(bgr.getNativeObjAddr(), salida.getNativeObjAddr(), alpha);
            bgr.release();
            rgba.release();
            return salida;

        } else {
            Mat rgba  = inputFrame.rgba();
            Mat paso1 = new Mat();
            Mat paso2 = new Mat();
            Mat paso3 = new Mat();
            Mat salidaRGBA = new Mat();

            aplicarChromaKey(rgba.getNativeObjAddr(), matFondo.getNativeObjAddr(), paso1.getNativeObjAddr(), usarVerde);
            inyectarRuido(paso1.getNativeObjAddr(), paso2.getNativeObjAddr(), media, varianza, tipoRuido);
            aplicarFiltro(paso2.getNativeObjAddr(), paso3.getNativeObjAddr(), kernel, tipoFiltro);

            Imgproc.cvtColor(paso3, salidaRGBA, Imgproc.COLOR_BGR2RGBA);

            rgba.release();
            paso1.release();
            paso2.release();
            paso3.release();
            return salidaRGBA;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (matFondo != null) matFondo.release();
    }

    public native void procesarFrame   (long bgrAddr,  long outAddr, float alpha);
    public native void aplicarChromaKey(long rgbaAddr, long bgAddr,  long outAddr, int verde);
    public native void inyectarRuido   (long inAddr,   long outAddr, float media, float var, int tipo);
    public native void aplicarFiltro   (long inAddr,   long outAddr, int kernel, int tipo);
}