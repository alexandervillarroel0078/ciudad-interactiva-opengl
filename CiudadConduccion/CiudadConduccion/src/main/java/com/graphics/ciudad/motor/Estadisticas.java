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

    // CATEGORÍAS: de dónde viene cada draw call. Quien dibuja un grupo llama a fijarCategoria() justo antes; los pases que
    // vuelven a dibujar geometría de otras categorías (sombras, minimapa, HUD) usan forzarCategoria() para que TODO lo
    // suyo cuente como suyo, y liberarCategoria() al terminar.
    public static final int EDIFICIOS = 0; // Aceras, volúmenes, techos y fachadas (puertas, vidrieras, toldos).
    public static final int VENTANAS = 1; // Las cajas de las ventanas de cada edificio.
    public static final int AUTOS = 2; // Jugador y tráfico: carrocería, cabina, ruedas y luces.
    public static final int ARBOLES_PARQUES = 3; // Árboles, bancos, fuentes, senderos y el campo de afuera.
    public static final int CALLES = 4; // Asfalto, líneas, pasos peatonales y marcas de frenado.
    public static final int FAROLAS_SENALES = 5; // Farolas, semáforos, PARE y carteles.
    public static final int SOMBRAS = 6; // Manchas y sombra planar proyectada.
    public static final int MINIMAPA = 7; // Todo el segundo pase de la vista superior.
    public static final int HUD = 8; // Paneles y texto 2D.
    public static final int OTROS = 9; // Cielo, partículas, basureros, destino, indicador.
    public static final String[] NOMBRES = {"Edificios y fachadas", "Ventanas", "Autos y tráfico", "Árboles y parques",
        "Calles y marcas", "Farolas y señales", "Sombras", "Minimapa", "HUD y texto", "Otros"};

    private static int llamadas = 0; // Draw calls acumulados desde el último reiniciarLlamadas().
    private static final int[] porCategoria = new int[NOMBRES.length]; // Draw calls del cuadro en curso, por categoría.
    private static final int[] ultimoCuadro = new int[NOMBRES.length]; // Desglose del último cuadro terminado.
    private static int categoria = OTROS; // Categoría a la que se suman los draw calls ahora.
    private static boolean forzada = false; // true mientras un pase (sombras, minimapa, HUD) manda sobre las categorías.
    private static int categoriaAntesDeForzar = OTROS; // A dónde vuelve liberarCategoria().

    private float tiempoAcumulado = 0; // Segundos reales sumados en la ventana actual.
    private int cuadrosAcumulados = 0; // Cuadros dibujados en la ventana actual.
    private int fps = 0; // Último promedio calculado.
    private float frameMs = 0; // Tiempo medio por cuadro, en milisegundos, sobre la misma ventana que los FPS.

    /** Cuenta un draw call; lo llaman Cubo, Malla y Dibujo2D justo antes de cada glDrawArrays. */
    public static void contarLlamada() {
        llamadas++;
        porCategoria[categoria]++;
    }

    /** Fija la categoría de los próximos draw calls; no hace nada mientras un pase la tiene forzada. */
    public static void fijarCategoria(int nueva) {
        if (!forzada) {
            categoria = nueva;
        }
    }

    /** Pase que reúne todo lo suyo bajo una categoría (sombras, minimapa, HUD): ignora fijarCategoria() hasta liberarCategoria(). */
    public static void forzarCategoria(int nueva) {
        categoriaAntesDeForzar = forzada ? categoriaAntesDeForzar : categoria;
        categoria = nueva;
        forzada = true;
    }

    /** Termina el pase forzado y vuelve a la categoría que había antes. */
    public static void liberarCategoria() {
        forzada = false;
        categoria = categoriaAntesDeForzar;
    }

    /** Copia el desglose del cuadro en curso; Juego lo llama al terminar de dibujar. */
    public static void cerrarCuadro() {
        System.arraycopy(porCategoria, 0, ultimoCuadro, 0, porCategoria.length);
    }

    /** Desglose por categoría del último cuadro cerrado (copia). */
    public static int[] getDesglose() {
        return ultimoCuadro.clone();
    }

    /** Tabla de texto con el desglose del último cuadro cerrado, para la consola (tecla F5). */
    public static String textoDesglose() {
        StringBuilder sb = new StringBuilder(String.format("--- Draw calls por origen (último cuadro) ---%n"));
        int total = 0;
        for (int i = 0; i < NOMBRES.length; i++) {
            sb.append(String.format(java.util.Locale.ROOT, "%-22s %6d%n", NOMBRES[i], ultimoCuadro[i]));
            total += ultimoCuadro[i];
        }
        return sb.append(String.format(java.util.Locale.ROOT, "%-22s %6d", "TOTAL", total)).toString();
    }

    /** Pone el contador en cero; Juego lo llama al empezar cada cuadro. */
    public static void reiniciarLlamadas() {
        llamadas = 0;
        java.util.Arrays.fill(porCategoria, 0);
        forzada = false; // Un pase que falló a medias no deja la categoría trabada.
        categoria = OTROS;
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
            frameMs = 1000f * tiempoAcumulado / cuadrosAcumulados; // Segundos por cuadro a milisegundos.
            tiempoAcumulado = 0;
            cuadrosAcumulados = 0;
        }
    }

    /** Milisegundos por cuadro promediados en la última ventana completa (incluye la espera del V-Sync). */
    public float getFrameMs() {
        return frameMs;
    }

    /** FPS promediados sobre la última ventana completa (0 hasta que se completa la primera). */
    public int getFps() {
        return fps;
    }
}
