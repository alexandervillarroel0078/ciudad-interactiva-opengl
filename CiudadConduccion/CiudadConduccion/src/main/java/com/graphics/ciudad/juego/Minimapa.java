package com.graphics.ciudad.juego; // Agrupa las reglas de la partida y el minimapa.

import com.graphics.ciudad.motor.Cubo; // Dibuja el indicador del auto.
import com.graphics.ciudad.motor.Estadisticas; // Cuenta sus draw calls como MINIMAPA.
import com.graphics.ciudad.motor.Shader; // Cambia la proyección del shader con uMapa.
import com.graphics.ciudad.mundo.Edificio; // Altura del edificio más alto: las marcas van por encima.
import com.graphics.ciudad.mundo.Mapa; // Aporta el límite de la ciudad para encuadrar el minimapa.
import com.graphics.ciudad.vehiculo.Auto; // Aporta posición y orientación del indicador.
import static org.lwjgl.glfw.GLFW.*; // Permite consultar la tecla que controla el minimapa.
import static org.lwjgl.opengl.GL33.*; // Permite cambiar viewport, recorte y buffers de dibujo.

/**
 * MINIMAPA: segundo pase de dibujo, visto desde arriba en un recuadro.
 * Responsable de: mostrar u ocultar el mapa (M), reservar el recuadro con glScissor y glViewport, activar la
 * proyección ortográfica (uMapa = 1), pedir a Juego que dibuje otra vez la escena, dibujar el indicador del auto
 * y restaurar viewport, scissor y uMapa al terminar. El minimapa mantiene el norte (-Z) arriba.
 * Se comunica con: Shader (uMapa), Cubo (indicador), Auto (posición y ángulo) y Juego, que le pasa la escena como
 * Runnable y consulta enVistaMapa() para omitir detalles pequeños. También marca las divisiones de los sectores de
 * Mapa y ofrece aPantalla() para que el HUD escriba sus nombres encima del recuadro. El destino activo lo dibuja Entregas dentro de
 * la escena (cuadrado dorado elevado). Al redimensionar, recibe el tamaño actual del framebuffer en cada cuadro:
 * el recuadro sigue en la esquina superior derecha con hasta 260 px de lado, y la vista principal no se altera.
 */
public class Minimapa {

    private final Shader shader; // Programa cuyo modo de proyección se cambia.
    private final Cubo cubo; // Geometría con la que se dibuja el indicador.
    private boolean mostrarMapa = true; // Muestra el minimapa desde el inicio.
    private boolean vistaMapa = false; // Minimapa lo activa mientras dibuja la vista superior.
    private static final int TAMANO_MAX_MINIMAPA = 260; // Lado máximo del recuadro, en píxeles del framebuffer.
    private static final int FRACCION_MINIMAPA = 3; // En ventanas pequeñas el lado se limita a 1/3 de la dimensión menor.
    private static final int MARGEN_MINIMAPA = 18; // Separación máxima entre el recuadro y los bordes de la ventana, en píxeles.
    private static final int FRACCION_MARGEN = 20; // En ventanas pequeñas el margen se limita a 1/20 de la dimensión menor.
    private static final int BORDE_MINIMAPA = 3; // Grosor del marco claro alrededor del mapa, en píxeles.
    private static final float ESCALA_INDICADOR = 1.5f; // Agranda el indicador del auto: la ciudad 11 × 11 ocupa más espacio en el recuadro.
    private static final float MARGEN_MAPA = 2; // Unidades de mundo que se dejan alrededor de la ciudad dentro del recuadro.
    private static final float GROSOR_DIVISION = 0.7f; // Ancho, en unidades de mundo, de las líneas que separan los sectores.
    // ALTURAS DE LAS MARCAS: en la vista desde arriba lo más alto tapa lo más bajo (ciudad.vert ordena la profundidad
    // por Y). Las marcas van por encima del edificio más alto de la ciudad (Edificio.ALTURA_MAXIMA, ≈ 37 con las
    // torres de 8 a 11 pisos): antes estaban fijas en 22-26 y una torre más alta las habría tapado. Todas deben quedar
    // por debajo de ESCALA_ALTURA_MAPA (100) del shader, que es la profundidad máxima del minimapa.
    public static final float ALTURA_DIVISION = Edificio.ALTURA_MAXIMA + 1; // Líneas de los sectores.
    public static final float ALTURA_DESTINO = Edificio.ALTURA_MAXIMA + 2; // Cuadrado dorado del destino (lo usa Entregas).
    public static final float ALTURA_INDICADOR = Edificio.ALTURA_MAXIMA + 3; // Marca cian del auto.
    public static final float ALTURA_PUNTA = Edificio.ALTURA_MAXIMA + 4; // Punta blanca, encima de todo.
    private int recuadroX; // Último recuadro dibujado: X de su esquina inferior izquierda, en píxeles (OpenGL).
    private int recuadroY; // Y de su esquina inferior izquierda, en píxeles medidos desde abajo.
    private int recuadroLado; // Lado del recuadro en píxeles; 0 si el minimapa no se dibujó.

    /** Recibe el shader y el cubo compartidos. */
    public Minimapa(Shader shader, Cubo cubo) {
        this.shader = shader; // Guarda el programa para cambiar uMapa.
        this.cubo = cubo; // Guarda la geometría compartida.
    }

    /** Añade el interruptor del minimapa a los controles de Juego. */
    public void tecla(int key) {
        if (key == GLFW_KEY_M) { // Comprueba si se pulsó la tecla del mapa.
            mostrarMapa = !mostrarMapa; // Alterna entre mostrar y ocultar la vista superior.
        }
    }

    /** Indica si se está dibujando el segundo pase; Juego omite entonces la decoración y la baliza. */
    public boolean enVistaMapa() {
        return vistaMapa; // true solo durante dibujar().
    }

    /**
     * Recuadro del minimapa para una ventana de ancho × alto píxeles, sin OpenGL: {x, y, lado, margen}, con (x, y) la
     * esquina inferior izquierda medida desde abajo (como OpenGL). Queda arriba a la derecha, a "margen" de ambos bordes.
     */
    static int[] calcularRecuadro(int ancho, int alto) {
        int dimensionMenor = Math.min(ancho, alto); // Busca la dimensión que limita el espacio disponible.
        int lado = Math.min(TAMANO_MAX_MINIMAPA, dimensionMenor / FRACCION_MINIMAPA); // Limita el mapa a 260 píxeles y a un tercio de la ventana.
        int margen = Math.min(MARGEN_MINIMAPA, dimensionMenor / FRACCION_MARGEN); // Calcula una separación adaptable respecto a los bordes.
        int x = ancho - lado - margen; // Ubica el recuadro cerca del borde derecho.
        int y = alto - lado - margen; // Ubica el recuadro arriba; OpenGL mide Y desde abajo.
        return new int[] {x, y, lado, margen}; // Todo lo que necesita dibujar().
    }

    // ==================== MINIMAPA: SEGUNDO PASE DE DIBUJO ====================

    /** Dibuja la misma ciudad desde arriba, en un recuadro; Juego ya dibujó antes la escena principal. */
    public void dibujar(int ancho, int alto, Auto auto, Runnable escena) {
        recuadroLado = 0; // Hasta dibujarlo, no hay recuadro visible (así el HUD no escribe nombres si está oculto).
        if (!mostrarMapa) { // Comprueba si el usuario ocultó el minimapa con M.
            return; // Conserva únicamente la imagen principal.
        }
        int[] recuadro = calcularRecuadro(ancho, alto); // {x, y, lado, margen} para el tamaño actual de la ventana.
        int x = recuadro[0]; // Esquina inferior izquierda en X.
        int y = recuadro[1]; // Esquina inferior izquierda en Y (OpenGL mide desde abajo).
        int lado = recuadro[2]; // Lado del recuadro.
        glEnable(GL_SCISSOR_TEST); // Activa el recorte para no borrar el resto de la escena.
        glScissor(x - BORDE_MINIMAPA, y - BORDE_MINIMAPA, lado + 2 * BORDE_MINIMAPA, lado + 2 * BORDE_MINIMAPA); // Selecciona el mapa más un borde de tres píxeles.
        glClearColor(0.8f, 0.87f, 0.94f, 1); // Define un color claro para el marco.
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // Borra solo el recuadro exterior gracias al scissor.
        glScissor(x, y, lado, lado); // Reduce el recorte al interior del minimapa.
        glClearColor(0.06f, 0.10f, 0.15f, 1); // Define el fondo oscuro del mapa.
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // Limpia color y profundidad dentro del mapa.
        glViewport(x, y, lado, lado); // Redirige la proyección al recuadro cuadrado.
        recuadroX = x; // Recuerda dónde quedó el recuadro para ubicar después los nombres de los sectores.
        recuadroY = y; // Esquina inferior del recuadro.
        recuadroLado = lado; // Tamaño del recuadro.
        shader.entero("uMapa", 1); // Selecciona la proyección ortográfica del shader ciudad.vert.
        // La proyección del minimapa (x, y, z) → (x, -z, -y) es un ESPEJO (determinante -1): invierte el sentido de giro de
        // todos los triángulos en pantalla, así que las caras frontales pasan a verse en sentido horario.
        glFrontFace(GL_CW);
        shader.decimal("uMitadMapa", Mapa.LIMITE + MARGEN_MAPA); // Encuadra la ciudad completa, sea cual sea el tamaño de MAPA.
        Estadisticas.forzarCategoria(Estadisticas.MINIMAPA); // Todo el segundo pase cuenta como minimapa.
        vistaMapa = true; // Indica a Juego.escena() que omita decoración pequeña y baliza flotante.
        try { // Asegura que el estado de dibujo se restaure incluso si el segundo pase falla.
            escena.run(); // Dibuja otra vez la misma ciudad, ahora vista desde arriba.
            dibujarDivisionesSectores(); // Marca los límites de los sectores con nombre definidos en Mapa.
            dibujarIndicadorAuto(auto); // Resalta la posición y el frente del jugador en el mapa.
        } finally { // El siguiente cuadro debe volver a la configuración de pantalla completa.
            Estadisticas.liberarCategoria();
            vistaMapa = false; // Reactiva los detalles de la escena principal.
            shader.entero("uMapa", 0); // Recupera la proyección en perspectiva.
            glFrontFace(GL_CCW); // Y el sentido de giro normal de la vista principal.
            glDisable(GL_SCISSOR_TEST); // Permite que la próxima limpieza abarque toda la pantalla.
            glViewport(0, 0, ancho, alto); // Recupera el área de dibujo de la ventana completa.
        }
        if (glGetError() != GL_NO_ERROR) { // Comprueba que el pase del mapa no haya generado errores OpenGL.
            throw new IllegalStateException("Error OpenGL en minimapa"); // Expone el error en la consola.
        }
    }

    /** Dibuja el contorno de cada sector de Mapa.SECTORES con líneas finas y claras. */
    private void dibujarDivisionesSectores() {
        for (float[] r : Mapa.SECTORES) { // Cada sector es un rectángulo {xMin, xMax, zMin, zMax}.
            float centroX = (r[0] + r[1]) / 2; // Centro horizontal del rectángulo.
            float centroZ = (r[2] + r[3]) / 2; // Centro en profundidad del rectángulo.
            float ancho = r[1] - r[0]; // Ancho del rectángulo en X.
            float profundo = r[3] - r[2]; // Largo del rectángulo en Z.
            cubo.caja(centroX, ALTURA_DIVISION, r[2], ancho, 0.1f, GROSOR_DIVISION, 0.9f, 0.9f, 0.6f); // Borde norte.
            cubo.caja(centroX, ALTURA_DIVISION, r[3], ancho, 0.1f, GROSOR_DIVISION, 0.9f, 0.9f, 0.6f); // Borde sur.
            cubo.caja(r[0], ALTURA_DIVISION, centroZ, GROSOR_DIVISION, 0.1f, profundo, 0.9f, 0.9f, 0.6f); // Borde oeste.
            cubo.caja(r[1], ALTURA_DIVISION, centroZ, GROSOR_DIVISION, 0.1f, profundo, 0.9f, 0.9f, 0.6f); // Borde este.
        }
    }

    /**
     * Convierte un punto del mundo (x, z) en píxeles de pantalla dentro del último minimapa dibujado, con el origen
     * arriba a la izquierda (como usa el HUD). Devuelve null si el minimapa está oculto.
     * Es la misma cuenta que hace ciudad.vert con uMapa = 1, pero en Java: mundo → -1..1 → píxeles del recuadro.
     */
    public float[] aPantalla(float x, float z, int altoVentana) {
        if (recuadroLado == 0) { // El minimapa no se dibujó en este cuadro.
            return null; // No hay dónde ubicar el punto.
        }
        float mitad = Mapa.LIMITE + MARGEN_MAPA; // Media anchura visible, igual que uMitadMapa.
        float ndcX = x / mitad; // -1 en el borde oeste, 1 en el borde este.
        float ndcY = -z / mitad; // 1 en el borde norte (arriba), -1 en el sur.
        float pixelX = recuadroX + (ndcX * 0.5f + 0.5f) * recuadroLado; // Píxel horizontal desde la izquierda.
        float pixelYDesdeAbajo = recuadroY + (ndcY * 0.5f + 0.5f) * recuadroLado; // Píxel vertical, como lo mide OpenGL.
        return new float[] {pixelX, altoVentana - pixelYDesdeAbajo}; // El HUD mide Y desde arriba: se invierte.
    }

    /** Dibuja una marca cian y una punta blanca por encima de los edificios del minimapa. */
    private void dibujarIndicadorAuto(Auto auto) {
        float autoX = auto.getX(); // Lee la posición X del vehículo.
        float autoZ = auto.getZ(); // Lee la posición Z del vehículo.
        float angulo = auto.getAngulo(); // Lee la orientación del vehículo.
        float e = ESCALA_INDICADOR; // Nombre corto para multiplicar los tamaños del indicador.
        cubo.cajaGirada(autoX, ALTURA_INDICADOR, autoZ, 2.2f * e, 0.1f, 3.2f * e, 0.1f, 1, 1, angulo); // Marca la posición con un rectángulo cian orientado.
        float frenteX = -(float) Math.sin(angulo); // Calcula la dirección frontal en el eje X.
        float frenteZ = -(float) Math.cos(angulo); // Calcula la dirección frontal en el eje Z.
        float puntaX = autoX + frenteX * 2 * e; // Desplaza la punta hacia delante en X (dos unidades por la escala).
        float puntaZ = autoZ + frenteZ * 2 * e; // Desplaza la punta hacia delante en Z (dos unidades por la escala).
        cubo.caja(puntaX, ALTURA_PUNTA, puntaZ, 0.9f * e, 0.1f, 0.9f * e, 1, 1, 1); // Dibuja la punta blanca encima del indicador cian.
    }
}
