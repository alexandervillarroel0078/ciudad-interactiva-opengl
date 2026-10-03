package com.graphics.ciudad.iluminacion; // Prueba la sombra planar desde su mismo paquete.

import com.graphics.ciudad.mundo.Mapa; // Celdas y medidas de las manzanas.
import com.graphics.ciudad.mundo.Parque; // Medida del césped de los parques.
import junit.framework.TestCase; // Proporciona las comprobaciones de JUnit usadas por Maven.

/**
 * Prueba Sombras.esHuellaAcera()/esHuellaParque(): la función PURA (sin OpenGL, sin shaders) que decide a qué piso
 * pertenece un punto (x, z) del mundo. dibujarEdificios(), dibujarArbolesParqueAro() y
 * Iluminacion.dibujarFarolasConHuella() aplican la misma regla con el rectángulo de una celda ya conocida
 * (manzana()/cesped()); esta prueba la verifica para cualquier punto, sin depender de qué celda se esté dibujando.
 */
public class SombrasHuellaTest extends TestCase {

    /** El centro, y hasta el borde, de cualquier manzana con edificio es acera; nunca parque. */
    public void testManzanaDeEdificioEsAcera() {
        float borde = Mapa.TAM_CELDA / 2 - 0.01f; // Justo antes del borde de la manzana (10 × 10).
        for (int fila = 0; fila < Mapa.MAPA.length; fila++) {
            for (int columna = 0; columna < Mapa.MAPA[fila].length; columna++) {
                if (Mapa.tipo(fila, columna) == Mapa.EDIFICIO) {
                    float cx = Mapa.centro(columna), cz = Mapa.centro(fila);
                    assertTrue(Sombras.esHuellaAcera(cx, cz)); // Centro: bajo el edificio, pero sigue siendo acera.
                    assertFalse(Sombras.esHuellaParque(cx, cz));
                    assertTrue(Sombras.esHuellaAcera(cx + borde, cz)); // Borde este: donde se apoya una farola.
                    assertTrue(Sombras.esHuellaAcera(cx, cz - borde)); // Borde norte.
                }
            }
        }
    }

    /** El césped de un parque es parque, nunca acera; el aro de 0.5 alrededor es acera, nunca césped. */
    public void testParqueCespedYAro() {
        for (int[] celda : Mapa.parques()) {
            float cx = Mapa.centro(celda[1]), cz = Mapa.centro(celda[0]);
            assertTrue(Sombras.esHuellaParque(cx, cz)); // Centro: césped.
            assertFalse(Sombras.esHuellaAcera(cx, cz));

            float dentroCesped = Parque.MITAD_CESPED - 0.01f; // Justo antes del borde del césped (9 × 9).
            assertTrue(Sombras.esHuellaParque(cx + dentroCesped, cz));
            assertFalse(Sombras.esHuellaAcera(cx + dentroCesped, cz));

            float enElAro = Parque.MITAD_CESPED + 0.1f; // Pasado el césped, todavía dentro de la manzana (10 × 10).
            assertTrue(Sombras.esHuellaAcera(cx + enElAro, cz)); // El aro es acera...
            assertFalse(Sombras.esHuellaParque(cx + enElAro, cz)); // ...nunca césped.
        }
    }

    /** Ningún punto de una celda de calle es acera ni parque. */
    public void testCalleNoEsHuella() {
        for (int fila = 0; fila < Mapa.MAPA.length; fila++) {
            for (int columna = 0; columna < Mapa.MAPA[fila].length; columna++) {
                if (Mapa.esCalle(fila, columna)) {
                    float cx = Mapa.centro(columna), cz = Mapa.centro(fila);
                    assertFalse(Sombras.esHuellaAcera(cx, cz));
                    assertFalse(Sombras.esHuellaParque(cx, cz));
                }
            }
        }
    }

    /** Fuera de la matriz del mapa, ningún punto es acera ni parque (y no revienta por índice fuera de rango). */
    public void testFueraDelMapaNoEsHuella() {
        float fuera = Mapa.LIMITE + 5;
        assertFalse(Sombras.esHuellaAcera(fuera, fuera));
        assertFalse(Sombras.esHuellaParque(fuera, fuera));
        assertFalse(Sombras.esHuellaAcera(-fuera, -fuera));
        assertFalse(Sombras.esHuellaParque(-fuera, -fuera));
    }

    /** Acera y parque son excluyentes en cualquier punto de la ciudad: nunca a la vez (recorre toda la grilla). */
    public void testAceraYParqueSonExcluyentes() {
        float paso = 1.3f; // No calza con el ancho de celda (10): cubre centros, bordes y esquinas por igual.
        for (float x = -Mapa.LIMITE; x <= Mapa.LIMITE; x += paso) {
            for (float z = -Mapa.LIMITE; z <= Mapa.LIMITE; z += paso) {
                assertFalse(Sombras.esHuellaAcera(x, z) && Sombras.esHuellaParque(x, z));
            }
        }
    }
}
