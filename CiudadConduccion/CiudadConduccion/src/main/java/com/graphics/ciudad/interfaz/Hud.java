package com.graphics.ciudad.interfaz; // Agrupa la interfaz dentro de la ventana: dibujo 2D y HUD.

import com.graphics.ciudad.juego.EstadoPartida; // Indica si hay que mostrar el menú de inicio o el cartel de pausa.

/**
 * HUD: interfaz dibujada dentro de la ventana, encima de la escena.
 * Responsable de: el panel semitransparente de estado (velocidad, faros, día/noche, entregas, destino y sector),
 * la ayuda de controles (tecla H), el cartel de PAUSA, el menú de inicio y los nombres de sectores sobre el minimapa.
 * Todos los tamaños se calculan en cada cuadro a partir del ancho y alto actuales, así se adapta al redimensionar.
 * Se comunica con: Dibujo2D (rectángulos y texto), EstadoPartida (menú / pausa) y Juego (le pasa las líneas de estado,
 * las etiquetas del minimapa y la tecla H).
 */
public class Hud {

    // ==================== 1. CONSTANTES DE TAMAÑO Y ESTILO (valores ajustables) ====================
    public static final float ALTO_REFERENCIA = 380; // Con una ventana de 760 px de alto, la escala del texto es 2.
    public static final float ESCALA_MINIMA = 1; // El texto nunca se achica por debajo de su tamaño base (≈7 px).
    public static final float ESCALA_MAXIMA = 3; // Ni crece más de tres veces en pantallas grandes.
    public static final float MARGEN = 12; // Separación del panel respecto al borde de la ventana, en píxeles.
    public static final float RELLENO = 8; // Espacio interior entre el borde del panel y el texto, en píxeles.
    public static final float ALTO_LINEA = 11; // Separación entre líneas en unidades de texto (se multiplica por la escala).
    public static final float ALFA_PANEL = 0.55f; // Transparencia del fondo de los paneles: 0 invisible, 1 opaco.
    public static final float ALFA_MENU = 0.7f; // Oscurecimiento de la escena detrás del menú de inicio.
    public static final String[] AYUDA = { // Líneas de la ayuda de controles (tecla H).
        "CONTROLES",
        "W / Flecha arriba: acelerar",
        "S / Flecha abajo: frenar y retroceder",
        "A D / Flechas: girar",
        "Espacio: freno",
        "C: camara seguimiento / orbital / aerea",
        "Orbital/aerea: boton derecho = mouse-look",
        "   (cursor oculto); mover = girar,",
        "   Shift = desplazar (aerea)",
        "   ruedita = acercar / alejar",
        "N: dia / noche     F: faros",
        "+ / -: niebla     L: lluvia",
        "F1 sombras F2 niebla F3 FPS F4 VSync",
        "M: minimapa        H: esta ayuda",
        "P: pausa           R: reiniciar",
        "ESC: salir"
    };

    private boolean mostrarAyuda = false; // La ayuda empieza oculta; H la muestra.

    /** Muestra u oculta la ayuda de controles; Juego lo llama al presionar H. */
    public void alternarAyuda() {
        mostrarAyuda = !mostrarAyuda; // Invierte la visibilidad.
    }

    /** Calcula la escala del texto según el tamaño actual de la ventana. */
    public static float escala(int ancho, int alto) {
        float segunAlto = Math.min(ancho, alto) / ALTO_REFERENCIA; // Proporcional a la dimensión menor.
        return Math.max(ESCALA_MINIMA, Math.min(ESCALA_MAXIMA, segunAlto)); // Limitada entre el mínimo y el máximo.
    }

    // ==================== 2. DIBUJO ====================

    /** Dibuja el HUD completo; lineasEstado son los textos del panel (velocidad, faros, etc.). */
    public void dibujar(Dibujo2D d, int ancho, int alto, String[] lineasEstado, EstadoPartida.Estado estado) {
        float escala = escala(ancho, alto); // Tamaño del texto para esta ventana.
        dibujarPanel(d, MARGEN, MARGEN, escala, lineasEstado, 1, 1, 1); // Panel de estado en la esquina superior izquierda.
        if (mostrarAyuda) { // La ayuda se dibuja solo si el usuario la pidió.
            float altoAyuda = altoPanel(AYUDA.length, escala); // Alto total del panel de ayuda.
            dibujarPanel(d, MARGEN, alto - MARGEN - altoAyuda, escala, AYUDA, 1, 0.9f, 0.55f); // Abajo a la izquierda.
        }
        if (estado == EstadoPartida.Estado.PAUSA) { // Cartel de pausa en el centro.
            dibujarCentrado(d, ancho, alto / 2f - 20 * escala, escala * 3, "PAUSA", 1, 0.85f, 0.2f); // Texto grande.
            dibujarCentrado(d, ancho, alto / 2f + 12 * escala, escala, "P: continuar", 1, 1, 1); // Indica cómo seguir.
        }
        if (estado == EstadoPartida.Estado.MENU) { // Menú de inicio: cubre la escena.
            d.rectangulo(0, 0, ancho, alto, 0.02f, 0.03f, 0.06f, ALFA_MENU); // Oscurece toda la ventana.
            dibujarCentrado(d, ancho, alto / 2f - 45 * escala, escala * 3, "CIUDAD INTERACTIVA", 1, 0.8f, 0.15f); // Título.
            dibujarCentrado(d, ancho, alto / 2f, escala * 1.4f, "Presioná ENTER para empezar", 1, 1, 1); // Instrucción principal.
            dibujarCentrado(d, ancho, alto / 2f + 25 * escala, escala, "H: ver controles   ESC: salir", 0.8f, 0.85f, 0.9f); // Ayuda.
        }
    }

    /** Escribe los nombres de los sectores sobre el minimapa; cada etiqueta es {x, y} en píxeles de pantalla. */
    public void dibujarEtiquetasMapa(Dibujo2D d, int ancho, int alto, float[][] posiciones, String[] nombres) {
        float escala = escala(ancho, alto) * 0.5f; // Letra pequeña: el minimapa ocupa poco espacio.
        for (int i = 0; i < posiciones.length; i++) { // Recorre cada sector.
            float anchoTexto = d.anchoTexto(nombres[i], escala); // Ancho del nombre, para centrarlo.
            float x = posiciones[i][0] - anchoTexto / 2; // Centra el texto horizontalmente en el punto.
            float y = posiciones[i][1] - 3.5f * escala; // Centra verticalmente (la letra mide ≈ 7 unidades).
            d.rectangulo(x - 2, y - 2, anchoTexto + 4, 7 * escala + 4, 0, 0, 0, ALFA_PANEL); // Fondo para leerlo sobre el mapa.
            d.texto(x, y, escala, nombres[i], 1, 1, 0.8f, 1); // Nombre del sector.
        }
    }

    /** Dibuja un panel semitransparente con varias líneas de texto; (x, y) es su esquina superior izquierda. */
    private void dibujarPanel(Dibujo2D d, float x, float y, float escala, String[] lineas, float r, float g, float b) {
        float anchoMaximo = 0; // Ancho de la línea más larga.
        for (String linea : lineas) { // Busca la línea más larga.
            anchoMaximo = Math.max(anchoMaximo, d.anchoTexto(linea, escala)); // Conserva el mayor ancho.
        }
        float anchoPanel = anchoMaximo + 2 * RELLENO; // El panel se ajusta al texto más largo.
        d.rectangulo(x, y, anchoPanel, altoPanel(lineas.length, escala), 0.03f, 0.05f, 0.09f, ALFA_PANEL); // Fondo.
        for (int i = 0; i < lineas.length; i++) { // Escribe cada línea.
            float lineaY = y + RELLENO + i * ALTO_LINEA * escala; // Baja una línea por cada texto.
            d.texto(x + RELLENO, lineaY, escala, lineas[i], r, g, b, 1); // Texto opaco sobre el fondo.
        }
    }

    /** Alto en píxeles de un panel con la cantidad de líneas indicada. */
    private float altoPanel(int cantidadLineas, float escala) {
        return cantidadLineas * ALTO_LINEA * escala + 2 * RELLENO - (ALTO_LINEA - 7) * escala; // La última línea no necesita interlineado.
    }

    /** Dibuja un texto centrado horizontalmente en la ventana, a la altura y indicada. */
    private void dibujarCentrado(Dibujo2D d, int ancho, float y, float escala, String texto, float r, float g, float b) {
        float anchoTexto = d.anchoTexto(texto, escala); // Mide el texto.
        d.texto((ancho - anchoTexto) / 2, y, escala, texto, r, g, b, 1); // Lo ubica en el centro.
    }
}
