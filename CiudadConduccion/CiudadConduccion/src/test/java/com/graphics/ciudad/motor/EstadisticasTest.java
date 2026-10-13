package com.graphics.ciudad.motor; // Prueba el contador de draw calls y el promedio de FPS sin OpenGL.

import junit.framework.TestCase; // Proporciona las comprobaciones de JUnit usadas por Maven.

/** Comprueba el promedio de FPS y de ms por cuadro, y el desglose de draw calls por categoría. */
public class EstadisticasTest extends TestCase {

    /** Los FPS y los ms por cuadro se calculan al completar la ventana de 0.5 s, no con un cuadro suelto. */
    public void testPromedioSobreMedioSegundo() {
        Estadisticas e = new Estadisticas();
        for (int i = 0; i < 29; i++) {
            e.registrarCuadro(1 / 60f); // 29 cuadros = 0.483 s: la ventana todavía no se completó.
        }
        assertEquals(0, e.getFps());
        e.registrarCuadro(1 / 60f); // El cuadro 30 completa 0.5 s.
        assertEquals(60, e.getFps());
        assertEquals(16.67f, e.getFrameMs(), 0.01f);
        e.registrarCuadro(0.2f); // Un cuadro lento aislado no cambia el valor mostrado.
        assertEquals(60, e.getFps());
    }

    /** Cada draw call se suma a la categoría vigente y el total coincide con la suma. */
    public void testDesglosePorCategoria() {
        Estadisticas.reiniciarLlamadas();
        Estadisticas.fijarCategoria(Estadisticas.EDIFICIOS);
        Estadisticas.contarLlamada();
        Estadisticas.contarLlamada();
        Estadisticas.fijarCategoria(Estadisticas.AUTOS);
        Estadisticas.contarLlamada();
        Estadisticas.cerrarCuadro();
        int[] d = Estadisticas.getDesglose();
        assertEquals(2, d[Estadisticas.EDIFICIOS]);
        assertEquals(1, d[Estadisticas.AUTOS]);
        assertEquals(3, Estadisticas.getLlamadas());
    }

    /** Un pase forzado se queda con todo lo que se dibuje dentro y al liberarse vuelve a la categoría anterior. */
    public void testCategoriaForzada() {
        Estadisticas.reiniciarLlamadas();
        Estadisticas.fijarCategoria(Estadisticas.CALLES);
        Estadisticas.forzarCategoria(Estadisticas.MINIMAPA);
        Estadisticas.fijarCategoria(Estadisticas.EDIFICIOS); // Se ignora: manda el pase.
        Estadisticas.contarLlamada();
        Estadisticas.liberarCategoria();
        Estadisticas.contarLlamada(); // Vuelve a CALLES.
        Estadisticas.cerrarCuadro();
        int[] d = Estadisticas.getDesglose();
        assertEquals(1, d[Estadisticas.MINIMAPA]);
        assertEquals(1, d[Estadisticas.CALLES]);
        assertEquals(0, d[Estadisticas.EDIFICIOS]);
    }
}
