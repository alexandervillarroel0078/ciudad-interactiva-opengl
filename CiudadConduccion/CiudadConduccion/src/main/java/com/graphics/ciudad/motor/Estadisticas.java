package com.graphics.ciudad.motor; // Agrupa las piezas técnicas: ventana, shaders, geometría, cámara y partículas.

/**
 * ESTADÍSTICAS: FPS y draw calls para el HUD.
 * Responsable de: contar cuántas llamadas glDrawArrays se hacen por cuadro (contador ESTÁTICO: Cubo, Malla y Dibujo2D
 * lo incrementan sin necesitar una referencia; Juego lo pone en cero al empezar cada cuadro) y de medir los FPS
 * promediando sobre una ventana de VENTANA_FPS segundos, no con el valor instantáneo de un solo cuadro.
 * Se comunica con: Cubo, Malla y Dibujo2D (cuentan), Juego (reinicia y registra cada cuadro) y el HUD (lee los valores).
 * No usa OpenGL, así que se prueba sin ventana.
 */
public class Estadisticas {

    public static final float VENTANA_FPS = 0.5f; // Segundos que se promedian antes de actualizar el valor mostrado.

    private static int llamadas = 0; // Draw calls acumulados desde el último reiniciarLlamadas().

    private float tiempoAcumulado = 0; // Segundos reales sumados en la ventana actual.
    private int cuadrosAcumulados = 0; // Cuadros dibujados en la ventana actual.
    private int fps = 0; // Último promedio calculado.

    /** Cuenta un draw call; lo llaman Cubo, Malla y Dibujo2D justo antes de cada glDrawArrays. */
    public static void contarLlamada() {
        llamadas++;
    }

    /** Pone el contador en cero; Juego lo llama al empezar cada cuadro. */
    public static void reiniciarLlamadas() {
        llamadas = 0;
    }

    /** Draw calls hechos desde el último reinicio. */
    public static int getLlamadas() {
        return llamadas;
    }

    /** Registra un cuadro de dt segundos reales; al completar la ventana de 0.5 s actualiza el promedio. */
    public void registrarCuadro(float dt) {
        tiempoAcumulado += dt; // Suma el tiempo de este cuadro.
        cuadrosAcumulados++; // Y el cuadro mismo.
        if (tiempoAcumulado >= VENTANA_FPS) { // Ventana completa: cuadros / segundos.
            fps = Math.round(cuadrosAcumulados / tiempoAcumulado);
            tiempoAcumulado = 0;
            cuadrosAcumulados = 0;
        }
    }

    /** FPS promediados sobre la última ventana completa (0 hasta que se completa la primera). */
    public int getFps() {
        return fps;
    }
}
