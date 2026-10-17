package com.graphics.ciudad.juego; // Prueba las reglas de la partida desde su mismo paquete.

import com.graphics.ciudad.motor.Cubo; // Se crea sin tocar la GPU: solo guarda referencias.
import com.graphics.ciudad.motor.Shader; // Se crea sin tocar la GPU: OpenGL se usa recién en crear().
import com.graphics.ciudad.mundo.Mapa; // Centros de celda y tamaño del mapa.
import junit.framework.TestCase; // Proporciona las comprobaciones de JUnit usadas por Maven.

/** Comprueba el progreso de las entregas y su reinicio sin abrir una ventana OpenGL. */
public class EntregasTest extends TestCase {

    /** Crea un objeto Entregas cuyo dibujo no se usa en la prueba. */
    private Entregas nuevasEntregas() {
        Shader shader = new Shader(); // Programa sin compilar: la prueba no dibuja.
        return new Entregas(shader, new Cubo(shader)); // Las reglas no necesitan OpenGL.
    }

    /** Completa las tres entregas, reinicia y verifica progreso 0 y el primer destino activo. */
    public void testCompletarYReiniciar() {
        Entregas entregas = nuevasEntregas(); // Partida nueva.
        assertEquals(0, entregas.getEntregas()); // Empieza sin entregas.
        entregas.actualizar(0.1f, 0, 0, 0); // Detenido lejos del destino: no cuenta.
        assertEquals(0, entregas.getEntregas()); // La entrega exige estar cerca.
        float[] primero = Entregas.DESTINOS[0]; // Primera parada.
        entregas.actualizar(0.1f, primero[0], primero[1], 5); // Llega a la parada sin frenar.
        assertEquals(0, entregas.getEntregas()); // La entrega exige velocidad menor que 1.

        for (int indice = 0; indice < Entregas.DESTINOS.length; indice++) { // Visita cada parada en orden.
            float[] destino = entregas.destinoActual(); // Destino activo en este momento.
            assertSame(Entregas.DESTINOS[indice], destino); // Las paradas se activan en el orden de DESTINOS.
            entregas.actualizar(0.1f, destino[0] + 1, destino[1] - 1, 0.5f); // Frena a menos de 3 unidades de la marca.
            assertEquals(indice + 1, entregas.getEntregas()); // Cada llegada suma una entrega.
        }
        assertFalse(entregas.quedanEntregas()); // Tras la tercera entrega la partida terminó.
        assertNull(entregas.destinoActual()); // Ya no hay destino activo.
        assertTrue(entregas.estado().contains("GANASTE")); // El título anuncia la victoria.
        assertEquals(Entregas.DESTINOS.length, entregas.getEntregas()); // 3/3: el HUD muestra el cartel de victoria.
        float tiempoFinal = entregas.getTiempo();
        entregas.actualizar(5, 0, 0, 0);
        assertEquals(tiempoFinal, entregas.getTiempo(), 0f); // El tiempo queda congelado tras ganar.

        entregas.reset(); // Mismo reinicio que la tecla R.
        assertEquals(0, entregas.getEntregas()); // El progreso vuelve a cero.
        assertSame(Entregas.DESTINOS[0], entregas.destinoActual()); // El destino activo vuelve a ser el primero.
        assertEquals(0f, entregas.getTiempo(), 0f); // El cronómetro de la partida vuelve a cero.
        assertTrue(entregas.estado().startsWith("Entregas: 0/" + Entregas.DESTINOS.length + " | Destino: ")); // El título vuelve al formato de partida en curso.
    }

    /** Cada parada es una celda de calle {fila, columna} convertida con Mapa.centro(), como las rutas de Trafico. */
    public void testDestinosDesdeCeldas() {
        assertEquals(Entregas.CELDAS_DESTINOS.length, Entregas.DESTINOS.length); // Una posición por celda.
        for (int i = 0; i < Entregas.CELDAS_DESTINOS.length; i++) {
            int[] celda = Entregas.CELDAS_DESTINOS[i];
            assertTrue(Mapa.esCalleSegura(celda[0], celda[1])); // Dentro de la matriz y sobre la calle.
            assertEquals(Mapa.centro(celda[1]), Entregas.DESTINOS[i][0], 0f); // Columna → X.
            assertEquals(Mapa.centro(celda[0]), Entregas.DESTINOS[i][1], 0f); // Fila → Z.
        }
        float esquina = Mapa.LIMITE - Mapa.TAM_CELDA / 2; // Centro de la calle del borde.
        assertEquals(esquina, Entregas.DESTINOS[0][0], 1e-4f); // La primera sigue en la esquina noreste...
        assertEquals(-esquina, Entregas.DESTINOS[0][1], 1e-4f); // ...con cualquier tamaño de MAPA.
    }
}
