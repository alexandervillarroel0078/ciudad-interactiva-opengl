package com.graphics.ciudad.interfaz; // Prueba el HUD sin OpenGL.

import com.graphics.ciudad.juego.EstadoPartida; // Estados menú, jugando y pausa.
import java.util.ArrayList; // Registro de lo que el HUD intenta dibujar.
import java.util.List; // Tipo del registro.
import junit.framework.TestCase; // Proporciona las comprobaciones de JUnit usadas por Maven.

/** Comprueba que el HUD calcula su diseño a partir del tamaño actual de la ventana y no se sale de ella. */
public class HudTest extends TestCase {

    private static final String[] ESTADO = {"Velocidad: 120 km/h", "Faros: ON", "Ambiente: Noche", "Entregas: 0/3",
        "Destino: Panaderia", "Sector: Barrio Norte", "Camara: seguimiento", "H: ayuda   P: pausa"};
    private static final int[][] TAMANOS = {{1100, 760}, {800, 600}, {640, 480}, {400, 400}, {1920, 1080}, {3840, 2160}, {1600, 400}};

    /** Dibujo2D que anota los rectángulos {x, y, ancho, alto} en vez de enviarlos a la GPU. */
    private static class Registro extends Dibujo2D {
        final List<float[]> rectangulos = new ArrayList<>();
        @Override public void rectangulo(float x, float y, float ancho, float alto, float r, float g, float b, float a) {
            rectangulos.add(new float[] {x, y, ancho, alto});
        }
        @Override public void texto(float x, float y, float escala, String texto, float r, float g, float b, float a) {
        }
    }

    private static Registro dibujar(int ancho, int alto, boolean ayuda, EstadoPartida.Estado estado) {
        Registro registro = new Registro();
        Hud hud = new Hud();
        if (ayuda) {
            hud.alternarAyuda();
        }
        hud.dibujar(registro, ancho, alto, ESTADO, estado);
        return registro;
    }

    /** Con la ayuda abierta, ningún panel se sale de la ventana en ninguno de los tamaños. */
    public void testPanelesDentroDeLaVentana() {
        for (int[] t : TAMANOS) {
            for (float[] r : dibujar(t[0], t[1], true, EstadoPartida.Estado.JUGANDO).rectangulos) {
                String donde = t[0] + "x" + t[1] + " panel " + java.util.Arrays.toString(r);
                assertTrue(donde, r[0] >= 0 && r[1] >= 0);
                assertTrue(donde, r[0] + r[2] <= t[0] && r[1] + r[3] <= t[1]);
            }
        }
    }

    /** El panel de estado queda arriba a la izquierda y la ayuda abajo a la izquierda, a MARGEN del borde, siempre. */
    public void testAnclajesSiguenAlBordeAlRedimensionar() {
        for (int[] t : TAMANOS) {
            List<float[]> r = dibujar(t[0], t[1], true, EstadoPartida.Estado.JUGANDO).rectangulos;
            assertEquals(2, r.size());
            assertEquals(Hud.MARGEN, r.get(0)[0], 1e-3f); // Estado: pegado a la izquierda...
            assertEquals(Hud.MARGEN, r.get(0)[1], 1e-3f); // ...y arriba.
            assertEquals(Hud.MARGEN, r.get(1)[0], 1e-3f); // Ayuda: a la izquierda...
            assertEquals(t[1] - Hud.MARGEN, r.get(1)[1] + r.get(1)[3], 1e-2f); // ...y su borde inferior a MARGEN del fondo.
        }
    }

    /** El menú cubre exactamente la ventana actual y la escala del texto está acotada. */
    public void testMenuCubreLaVentanaYEscalaAcotada() {
        for (int[] t : TAMANOS) {
            List<float[]> r = dibujar(t[0], t[1], false, EstadoPartida.Estado.MENU).rectangulos;
            float[] fondo = r.get(r.size() - 1);
            assertEquals(t[0], fondo[2], 0f);
            assertEquals(t[1], fondo[3], 0f);
            float escala = Hud.escala(t[0], t[1]);
            assertTrue(escala >= Hud.ESCALA_MINIMA && escala <= Hud.ESCALA_MAXIMA);
        }
    }

    /** Con la ayuda abierta, el panel de estado y el de ayuda no se pisan mientras la ventana mida al menos 320 px de alto. */
    public void testEstadoYAyudaNoSePisan() {
        for (int alto = 320; alto <= 1200; alto += 40) {
            List<float[]> r = dibujar(Math.max(alto, 400), alto, true, EstadoPartida.Estado.JUGANDO).rectangulos;
            float finEstado = r.get(0)[1] + r.get(0)[3];
            assertTrue("alto " + alto + ": estado termina en " + finEstado + ", ayuda empieza en " + r.get(1)[1], finEstado <= r.get(1)[1]);
        }
    }
}
