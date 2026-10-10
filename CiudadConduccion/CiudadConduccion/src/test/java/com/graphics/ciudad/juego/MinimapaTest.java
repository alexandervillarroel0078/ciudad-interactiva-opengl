package com.graphics.ciudad.juego; // Prueba el minimapa desde su mismo paquete.

import com.graphics.ciudad.mundo.Mapa; // Aporta el límite de la ciudad.
import java.io.InputStream; // Lee el shader de vértices.
import java.lang.reflect.Field; // Accede al margen y al recuadro, que son privados.
import java.nio.charset.StandardCharsets; // Codificación del shader.
import junit.framework.TestCase; // Proporciona las comprobaciones de JUnit usadas por Maven.

/** Comprueba, sin abrir una ventana OpenGL, que la escala del minimapa sale del tamaño del mapa y que el norte queda arriba. */
public class MinimapaTest extends TestCase {

    private static final int LADO = 260; // Lado del recuadro simulado, en píxeles.
    private static final int ALTO_VENTANA = 720; // Alto de la ventana simulada.

    /** Crea un minimapa con un recuadro en (0, 0) de LADO píxeles, como si ya se hubiera dibujado. */
    private static Minimapa minimapaDibujado() throws Exception {
        Minimapa minimapa = new Minimapa(null, null); // El constructor solo guarda las referencias.
        Field lado = Minimapa.class.getDeclaredField("recuadroLado"); // Lo fija dibujar(), que necesita OpenGL.
        lado.setAccessible(true);
        lado.setInt(minimapa, LADO);
        return minimapa;
    }

    /** Media anchura visible = Mapa.LIMITE + MARGEN_MAPA: la escala depende del tamaño de la matriz. */
    public void testEscalaDerivadaDelMapa() throws Exception {
        Field campoMargen = Minimapa.class.getDeclaredField("MARGEN_MAPA"); // Margen alrededor de la ciudad.
        campoMargen.setAccessible(true);
        float margen = campoMargen.getFloat(null);
        assertTrue(margen >= 0 && margen < Mapa.TAM_CELDA); // Un margen chico: no es un límite escondido.
        float mitad = Mapa.MAPA.length * Mapa.TAM_CELDA / 2 + margen; // Media anchura esperada, desde la matriz.
        Minimapa minimapa = minimapaDibujado();
        float[] esteBorde = minimapa.aPantalla(mitad, 0, ALTO_VENTANA); // Borde visible este.
        float[] oesteBorde = minimapa.aPantalla(-mitad, 0, ALTO_VENTANA); // Borde visible oeste.
        assertEquals(LADO, esteBorde[0], 1e-3f); // Toca el borde derecho del recuadro.
        assertEquals(0, oesteBorde[0], 1e-3f); // Toca el borde izquierdo.
    }

    /** Las cuatro esquinas de la ciudad caen dentro del recuadro, y el norte (-Z) queda arriba. */
    public void testMuestraTodaLaCiudadConElNorteArriba() throws Exception {
        Minimapa minimapa = minimapaDibujado();
        float l = Mapa.LIMITE; // Borde de la ciudad.
        float[][] esquinas = {{-l, -l}, {l, -l}, {-l, l}, {l, l}}; // Noroeste, noreste, suroeste y sureste.
        for (float[] esquina : esquinas) { // Cada esquina de la matriz completa.
            float[] p = minimapa.aPantalla(esquina[0], esquina[1], ALTO_VENTANA);
            assertTrue(esquina[0] + "," + esquina[1], p[0] >= 0 && p[0] <= LADO); // Dentro en horizontal.
            float yDesdeArriba = p[1] - (ALTO_VENTANA - LADO); // El recuadro ocupa las últimas LADO filas desde arriba.
            assertTrue(esquina[0] + "," + esquina[1], yDesdeArriba >= 0 && yDesdeArriba <= LADO); // Dentro en vertical.
        }
        float[] norte = minimapa.aPantalla(0, -l, ALTO_VENTANA); // Borde norte.
        float[] sur = minimapa.aPantalla(0, l, ALTO_VENTANA); // Borde sur.
        assertTrue(norte[1] < sur[1]); // El HUD mide Y desde arriba: el norte tiene Y menor.
        float[] oeste = minimapa.aPantalla(-l, 0, ALTO_VENTANA);
        float[] este = minimapa.aPantalla(l, 0, ALTO_VENTANA);
        assertTrue(oeste[0] < este[0]); // El oeste a la izquierda.
    }

    /** De 400x400 a 3840x2160 (y ventanas anchas o altas) el recuadro cabe en la ventana, con su marco, anclado arriba a la derecha. */
    public void testRecuadroDentroDeLaVentanaYArribaADerecha() {
        int[][] tamanos = {{400, 400}, {640, 480}, {1100, 760}, {1920, 1080}, {3840, 2160}, {1600, 400}, {400, 1600}};
        for (int[] t : tamanos) {
            int[] r = Minimapa.calcularRecuadro(t[0], t[1]); // {x, y, lado, margen}; Y medida desde abajo.
            String donde = t[0] + "x" + t[1];
            assertTrue(donde, r[2] > 0 && r[2] <= 260); // Lado positivo y con tope.
            assertTrue(donde, r[2] <= Math.min(t[0], t[1]) / 3); // Nunca más de un tercio de la dimensión menor.
            assertTrue(donde, r[0] - 3 >= 0 && r[1] - 3 >= 0); // El marco de 3 px también cabe.
            assertEquals(donde, t[0] - r[3], r[0] + r[2]); // Pegado a la derecha, a "margen" del borde.
            assertEquals(donde, t[1] - r[3], r[1] + r[2]); // Pegado arriba (Y desde abajo), a "margen" del borde.
            assertTrue(donde, r[0] + r[2] <= t[0] && r[1] + r[2] <= t[1]); // Dentro de la ventana.
        }
    }

    /** El shader usa la misma cuenta: el norte (-Z) hacia arriba y uMitadMapa como escala, sin números fijos. */
    public void testShaderUsaLaMismaEscala() throws Exception {
        String codigo;
        try (InputStream entrada = Minimapa.class.getResourceAsStream("/shaders/ciudad.vert")) {
            assertNotNull(entrada);
            codigo = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(codigo.contains("vMundo.x / uMitadMapa")); // X escalado por la media anchura que envía Minimapa.
        assertTrue(codigo.contains("-vMundo.z / uMitadMapa")); // Z invertido: el norte arriba.
    }
}
