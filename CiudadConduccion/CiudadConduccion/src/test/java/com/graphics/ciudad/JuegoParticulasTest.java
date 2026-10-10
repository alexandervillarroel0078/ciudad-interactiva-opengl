package com.graphics.ciudad; // Prueba la integración de las partículas en Juego, desde su mismo paquete.

import com.graphics.ciudad.motor.SistemaParticulas; // Tipos de partícula.
import junit.framework.TestCase; // Proporciona las comprobaciones de JUnit usadas por Maven.
import static org.lwjgl.glfw.GLFW.GLFW_KEY_L; // Tecla de la lluvia.
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W; // Tecla de acelerar, simulada.

/** Comprueba que las partículas respetan la pausa y el reinicio dentro del juego, sin ventana OpenGL. */
public class JuegoParticulasTest extends TestCase {

    private static final float DT = 0.05f; // Paso de tiempo de cada actualización simulada.

    /** En el menú y en pausa no nace nada; jugando sí, y R vacía el sistema. */
    public void testPausaYReinicio() {
        Juego juego = new Juego();
        juego.usarTeclado(tecla -> tecla == GLFW_KEY_W);
        for (int i = 0; i < 20; i++) {
            juego.actualizar(DT); // En el menú dt efectivo = 0.
        }
        assertEquals(0, juego.getParticulas().activas());

        juego.getEstado().empezar();
        for (int i = 0; i < 30; i++) {
            juego.actualizar(DT); // El auto acelera y las fuentes salpican.
        }
        int vivas = juego.getParticulas().activas();
        assertTrue(vivas > 0);

        juego.getEstado().alternarPausa();
        for (int i = 0; i < 30; i++) {
            juego.actualizar(DT);
        }
        assertEquals(vivas, juego.getParticulas().activas()); // Congeladas.

        juego.getEstado().alternarPausa();
        juego.getParticulas().tecla(GLFW_KEY_L);
        for (int i = 0; i < 10; i++) {
            juego.actualizar(DT);
        }
        assertTrue(juego.getParticulas().activas(SistemaParticulas.TIPO_LLUVIA) > 0);

        juego.reiniciar(); // Tecla R.
        assertEquals(0, juego.getParticulas().activas());
    }
}
