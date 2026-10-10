package com.graphics.ciudad; // Prueba la integración de las marcas de frenado en Juego, desde su mismo paquete.

import junit.framework.TestCase; // Proporciona las comprobaciones de JUnit usadas por Maven.
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE; // Freno.
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W; // Acelerar.

/** Comprueba que el auto deja marcas al frenar, que la pausa las congela y que R las borra. */
public class JuegoMarcasTest extends TestCase {

    private static final float DT = 0.05f; // Paso de tiempo de cada actualización simulada.

    public void testFrenarDejaMarcasPausaYReinicio() {
        Juego juego = new Juego();
        juego.getEstado().empezar();
        juego.usarTeclado(tecla -> tecla == GLFW_KEY_W);
        for (int i = 0; i < 100; i++) { // Acelera hasta alta velocidad por la calle de salida.
            juego.actualizar(DT);
        }
        assertEquals(0, juego.getMarcas().cantidad()); // Acelerar no deja marcas.
        assertTrue(juego.getAuto().getVelocidad() > 11);
        juego.usarTeclado(tecla -> tecla == GLFW_KEY_SPACE); // Freno a fondo.
        for (int i = 0; i < 8; i++) {
            juego.actualizar(DT);
        }
        int marcas = juego.getMarcas().cantidad();
        assertTrue(marcas > 0);

        juego.getEstado().alternarPausa();
        for (int i = 0; i < 20; i++) {
            juego.actualizar(DT);
        }
        assertEquals(marcas, juego.getMarcas().cantidad()); // Congeladas.

        juego.reiniciar(); // Tecla R.
        assertEquals(0, juego.getMarcas().cantidad());
    }
}
