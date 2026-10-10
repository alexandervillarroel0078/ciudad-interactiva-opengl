package com.graphics.ciudad.iluminacion; // Prueba el cielo y las sombras falsas desde su mismo paquete.

import com.graphics.ciudad.mundo.Entorno; // Árboles del campo.
import com.graphics.ciudad.mundo.Mapa; // Celdas y medidas de las manzanas.
import com.graphics.ciudad.mundo.Parque; // Árboles, bancos y césped de los parques.
import junit.framework.TestCase; // Proporciona las comprobaciones de JUnit usadas por Maven.

/** Comprueba el cielo procedural y las sombras falsas sin abrir una ventana OpenGL. */
public class AmbienteTest extends TestCase {

    /** El cubo del cielo (esquina incluida) queda dentro del plano lejano (uPlanoLejano): si no, se recortaría. */
    public void testCieloDentroDelPlanoLejano() throws Exception {
        float planoLejano = new com.graphics.ciudad.motor.Camara(Mapa.LIMITE).getPlanoLejano(); // El que recibe ciudad.vert.
        assertTrue(Cielo.RADIO_CIELO < planoLejano);
        assertTrue(Cielo.LADO_CUBO * Math.sqrt(3) / 2 < planoLejano); // La esquina del cubo, lo más lejano que dibuja.
        assertTrue(Cielo.direccionLuna()[1] > 0); // La luna está en el cielo, no bajo el suelo.
    }

    /** El sol del cielo es el de la luz de día al empezar, y baja bajo el horizonte al pasar a noche. */
    public void testTransicionDiaNoche() {
        Iluminacion iluminacion = new Iluminacion(null, null);
        assertEquals(0f, iluminacion.factorNoche(), 0f); // Arranca de día.
        float[] sol = iluminacion.direccionSol();
        float largo = (float) Math.sqrt(sol[0] * sol[0] + sol[1] * sol[1] + sol[2] * sol[2]);
        assertEquals(1f, largo, 1e-4f); // Dirección unitaria.
        float[] dia = Sombras.DIRECCION_SOL_DIA;
        float largoDia = (float) Math.sqrt(dia[0] * dia[0] + dia[1] * dia[1] + dia[2] * dia[2]);
        assertEquals(dia[1] / largoDia, sol[1], 1e-4f); // Misma altura que el sol de la luz y las sombras.
        iluminacion.tecla(org.lwjgl.glfw.GLFW.GLFW_KEY_N); // Pide noche.
        iluminacion.avanzar(Iluminacion.DURACION_TRANSICION / 2);
        assertEquals(0.5f, iluminacion.factorNoche(), 1e-4f); // Transición gradual, no instantánea.
        assertTrue(iluminacion.esNocheVisual()); // A mitad de camino lo discreto (bombillas, ventanas) ya cambió.
        assertTrue(iluminacion.esNoche()); // Y esNoche() es lo que se pidió.
        iluminacion.avanzar(10);
        assertEquals(1f, iluminacion.factorNoche(), 0f); // No pasa de 1.
        assertTrue(iluminacion.direccionSol()[1] < 0); // Sol bajo el horizonte.
        iluminacion.tecla(org.lwjgl.glfw.GLFW.GLFW_KEY_N); // Vuelve el día.
        iluminacion.avanzar(10);
        assertEquals(0f, iluminacion.factorNoche(), 0f);
        assertFalse(iluminacion.esNocheVisual());
    }

    /** Compara dos colores RGB componente a componente. */
    private static void assertArrayEquals(float[] esperado, float[] real, float margen) {
        assertEquals(esperado.length, real.length);
        for (int i = 0; i < esperado.length; i++) {
            assertEquals(esperado[i], real[i], margen);
        }
    }

    /** La niebla tiene el color del horizonte del cielo: el de día, el de noche, y una mezcla entre ambos. */
    public void testColorNieblaIgualAlHorizonte() {
        Iluminacion iluminacion = new Iluminacion(null, null);
        assertArrayEquals(Cielo.HORIZONTE_DIA, iluminacion.colorNiebla(), 1e-4f); // Día pleno.
        iluminacion.tecla(org.lwjgl.glfw.GLFW.GLFW_KEY_N);
        iluminacion.avanzar(Iluminacion.DURACION_TRANSICION / 2);
        float[] medio = iluminacion.colorNiebla();
        for (int i = 0; i < 3; i++) { // En plena transición no es ni el de día ni el de noche.
            assertTrue(medio[i] != Cielo.HORIZONTE_DIA[i] && medio[i] != Cielo.HORIZONTE_NOCHE[i]);
        }
        iluminacion.avanzar(10);
        assertArrayEquals(Cielo.HORIZONTE_NOCHE, iluminacion.colorNiebla(), 1e-4f); // Noche plena.
        float[] pesos = Cielo.pesos(0.1f, 0.5f);
        assertEquals(1f, pesos[0] + pesos[1] + pesos[2], 1e-5f); // Los pesos suman 1.
    }

    /** + y - cambian la densidad de a PASO_DENSIDAD_NIEBLA, entre 0 y DENSIDAD_NIEBLA_MAX. */
    public void testTeclasDeNiebla() {
        Iluminacion iluminacion = new Iluminacion(null, null);
        assertEquals(Iluminacion.DENSIDAD_NIEBLA_INICIAL, iluminacion.densidadNiebla(), 0f);
        iluminacion.tecla(org.lwjgl.glfw.GLFW.GLFW_KEY_EQUAL); // "+".
        assertEquals(Iluminacion.DENSIDAD_NIEBLA_INICIAL + Iluminacion.PASO_DENSIDAD_NIEBLA, iluminacion.densidadNiebla(), 1e-6f);
        for (int i = 0; i < 100; i++) {
            iluminacion.tecla(org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ADD);
        }
        assertEquals(Iluminacion.DENSIDAD_NIEBLA_MAX, iluminacion.densidadNiebla(), 0f); // Tope.
        for (int i = 0; i < 100; i++) {
            iluminacion.tecla(org.lwjgl.glfw.GLFW.GLFW_KEY_MINUS);
        }
        assertEquals(0f, iluminacion.densidadNiebla(), 0f); // Sin niebla, nunca negativa.
    }

    /**
     * Hay una sombra fija por edificio, árbol, banco, luminaria de parque y basurero, y ninguna sale de la superficie
     * donde se apoya.
     */
    public void testSombrasFijas() {
        int esperadas = Entorno.ARBOLES.size(); // Árboles del campo.
        for (int fila = 0; fila < Mapa.MAPA.length; fila++) {
            for (int columna = 0; columna < Mapa.MAPA[fila].length; columna++) {
                if (Mapa.tipo(fila, columna) == Mapa.EDIFICIO) {
                    esperadas++; // Un edificio.
                }
            }
        }
        for (int[] p : Mapa.parques()) {
            esperadas += Parque.arboles(p[0], p[1]).size() + Parque.bancos(p[0], p[1]).size(); // Árboles y bancos.
            esperadas += Parque.luminarias(p[0], p[1]).size(); // Luminarias globo.
        }
        esperadas += com.graphics.ciudad.mundo.Basureros.UBICACIONES.size(); // Basureros de parques y veredas.
        assertEquals(esperadas, Sombras.FIJAS.size());
        for (float[] s : Sombras.FIJAS) {
            int fila = Mapa.indiceCelda(s[2]); // Celda donde cae la sombra.
            int columna = Mapa.indiceCelda(s[0]);
            boolean enCiudad = fila >= 0 && fila < Mapa.MAPA.length && columna >= 0 && columna < Mapa.MAPA[0].length;
            if (!enCiudad) { // Árbol del campo: sobre el pasto.
                assertEquals(Entorno.ALTURA_CAMPO + Sombras.ELEVACION_SOMBRA, s[1], 1e-5f);
                continue;
            }
            float cx = Mapa.centro(columna); // Centro de la manzana.
            float cz = Mapa.centro(fila);
            assertTrue(!Mapa.esCalle(fila, columna)); // Las sombras fijas nunca caen en la calle.
            float medio = Mapa.tipo(fila, columna) == Mapa.PARQUE ? Parque.MITAD_CESPED : Mapa.TAM_CELDA / 2; // Césped o acera.
            float radio = Math.max(s[3], s[4]) / 2; // Mitad de la mancha (cota que vale con cualquier giro).
            if (s[5] == 0) { // Sin giro (edificios, árboles, luminarias y basureros): la mancha entera queda sobre su superficie.
                assertTrue("sombra en " + s[0] + "," + s[2], Math.abs(s[0] - cx) + s[3] / 2 <= medio + 1e-4f);
                assertTrue(Math.abs(s[2] - cz) + s[4] / 2 <= medio + 1e-4f);
            } else { // Bancos: giran, alcanza con el radio.
                assertTrue(Math.hypot(s[0] - cx, s[2] - cz) + radio <= medio);
            }
        }
    }

    /** Apenas por encima del suelo (sin z-fighting) y más tenue de noche. */
    public void testElevacionYOpacidad() {
        assertTrue(Sombras.ELEVACION_SOMBRA > 0.04f); // Sobre la pintura vial (0.04) y los senderos del parque.
        assertTrue(Sombras.ELEVACION_SOMBRA < 0.2f); // Pero pegada al suelo.
        assertTrue(Sombras.ALFA_SOMBRA_NOCHE < Sombras.ALFA_SOMBRA_DIA); // De noche, más tenue.
        assertTrue(Sombras.ALFA_SOMBRA_DIA < 1); // Siempre semitransparente.
    }
}
