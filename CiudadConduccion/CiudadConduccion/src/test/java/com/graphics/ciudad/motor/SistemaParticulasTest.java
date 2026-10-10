package com.graphics.ciudad.motor; // Prueba el pool de partículas desde su mismo paquete.

import com.graphics.ciudad.mundo.Mapa; // Centros de los parques, donde están las fuentes.
import junit.framework.TestCase; // Proporciona las comprobaciones de JUnit usadas por Maven.
import static org.lwjgl.glfw.GLFW.GLFW_KEY_L; // Tecla de la lluvia.

/** Comprueba el pool, la vida, el vaciado y los emisores sin abrir una ventana OpenGL. */
public class SistemaParticulasTest extends TestCase {

    /** Nace una partícula quieta con la vida indicada. */
    private static boolean nace(SistemaParticulas p, float vida) {
        return p.emitir(SistemaParticulas.TIPO_HUMO, 1, 2, 3, 0, 0, 0, vida, 1, 1, 1, 0.2f);
    }

    /** El pool admite CAPACIDAD partículas; la siguiente se descarta y el conteo no pasa del tope. */
    public void testElPoolTieneCapacidadFija() {
        SistemaParticulas p = new SistemaParticulas();
        for (int i = 0; i < SistemaParticulas.CAPACIDAD; i++) {
            assertTrue(nace(p, 5));
        }
        assertEquals(3000, SistemaParticulas.CAPACIDAD);
        assertEquals(SistemaParticulas.CAPACIDAD, p.activas());
        assertFalse(nace(p, 5)); // Lleno: se descarta.
        assertEquals(SistemaParticulas.CAPACIDAD, p.activas());
    }

    /** Al morir, las posiciones se reutilizan: el pool se puede llenar otra vez y las vivas siguen compactas. */
    public void testLasPosicionesSeReutilizan() {
        SistemaParticulas p = new SistemaParticulas();
        for (int i = 0; i < SistemaParticulas.CAPACIDAD; i++) {
            nace(p, 1);
        }
        p.actualizar(1.5f); // Todas superan su vida.
        assertEquals(0, p.activas());
        for (int i = 0; i < SistemaParticulas.CAPACIDAD; i++) { // Otra tanda completa en el mismo pool.
            assertTrue(p.emitir(SistemaParticulas.TIPO_FUENTE, i, 0, 0, 0, 0, 0, 1, 1, 1, 1, 0.1f));
        }
        assertEquals(SistemaParticulas.CAPACIDAD, p.activas());
        assertEquals(0f, p.x(0), 0f); // La primera posición volvió a usarse.
        assertEquals(SistemaParticulas.CAPACIDAD - 1f, p.x(SistemaParticulas.CAPACIDAD - 1), 0f);
    }

    /** Cuando muere una del medio, la última ocupa su lugar y las demás conservan su estado. */
    public void testMatarCompactaElPool() {
        SistemaParticulas p = new SistemaParticulas();
        nace(p, 0.5f); // Posición 0: muere primero.
        p.emitir(SistemaParticulas.TIPO_FUENTE, 7, 0, 0, 0, 0, 0, 2, 1, 1, 1, 0.1f); // Posición 1: sobrevive.
        p.actualizar(1f);
        assertEquals(1, p.activas());
        assertEquals(7f, p.x(0), 0f); // La sobreviviente ocupa la posición liberada.
        assertEquals(1, p.activas(SistemaParticulas.TIPO_FUENTE));
        assertEquals(0, p.activas(SistemaParticulas.TIPO_HUMO));
    }

    /** La vida baja y la posición avanza con dt; al llegar a cero la partícula muere. */
    public void testLaVidaYLaPosicionAvanzanConDt() {
        SistemaParticulas p = new SistemaParticulas();
        p.emitir(SistemaParticulas.TIPO_HUMO, 0, 1, 0, 2, 0, -1, 1.0f, 1, 1, 1, 0.2f);
        p.actualizar(0.4f);
        assertEquals(1, p.activas());
        assertEquals(0.6f, p.vida(0), 1e-5f);
        assertEquals(0.8f, p.x(0), 1e-5f); // 2 u/s · 0.4 s.
        assertEquals(-0.4f, p.z(0), 1e-5f);
        p.actualizar(0.7f);
        assertEquals(0, p.activas()); // Superó su vida.
    }

    /** Una partícula con vida cero o negativa no nace. */
    public void testNoNaceSinVida() {
        SistemaParticulas p = new SistemaParticulas();
        assertFalse(nace(p, 0));
        assertFalse(nace(p, -1));
        assertEquals(0, p.activas());
    }

    /** Con dt = 0 (pausa o menú) no se mueve ni muere ninguna y los emisores no sacan nada. */
    public void testConDtCeroNoCambiaNada() {
        SistemaParticulas p = new SistemaParticulas();
        p.tecla(GLFW_KEY_L); // Lluvia activada.
        p.emitir(SistemaParticulas.TIPO_HUMO, 0, 1, 0, 3, 0, 0, 1.0f, 1, 1, 1, 0.2f);
        p.actualizar(0);
        p.emitirHumo(0, 0, 0, 0, 16);
        p.emitirFuentes(0);
        p.emitirLluvia(0, 0, 0);
        assertEquals(1, p.activas());
        assertEquals(1.0f, p.vida(0), 0f);
        assertEquals(0f, p.x(0), 0f);
    }

    /** Vaciar (tecla R) deja el pool sin partículas, con los conteos en cero, y se puede volver a usar. */
    public void testVaciarAlReiniciar() {
        SistemaParticulas p = new SistemaParticulas();
        p.tecla(GLFW_KEY_L);
        for (int i = 0; i < 20; i++) {
            p.emitirHumo(0.05f, 0, 0, 0, 10);
            p.emitirFuentes(0.05f);
            p.emitirLluvia(0.05f, 0, 0);
        }
        assertTrue(p.activas() > 0);
        p.vaciar();
        assertEquals(0, p.activas());
        assertEquals(0, p.activas(SistemaParticulas.TIPO_HUMO));
        assertEquals(0, p.activas(SistemaParticulas.TIPO_FUENTE));
        assertEquals(0, p.activas(SistemaParticulas.TIPO_LLUVIA));
        assertTrue(nace(p, 1)); // El pool sigue funcionando.
        assertEquals(1, p.activas());
        assertTrue(p.lluviaActiva()); // La tecla L conserva su estado.
    }

    /** La tasa del humo es proporcional a la velocidad y, detenido, no sale nada. */
    public void testHumoProporcionalALaVelocidad() {
        SistemaParticulas lento = new SistemaParticulas();
        SistemaParticulas rapido = new SistemaParticulas();
        SistemaParticulas quieto = new SistemaParticulas();
        for (int i = 0; i < 20; i++) { // Un segundo en cuadros de 0.05 s (nada muere: vida mínima 1 s).
            lento.emitirHumo(0.05f, 0, 0, 0, 4);
            rapido.emitirHumo(0.05f, 0, 0, 0, 16);
            quieto.emitirHumo(0.05f, 0, 0, 0, 0);
        }
        assertEquals(0, quieto.activas());
        assertTrue(lento.activas() > 0);
        assertEquals(4.0, (double) rapido.activas() / lento.activas(), 0.25); // 4 veces la velocidad, 4 veces el humo.
        assertEquals(40, rapido.activas(), 1); // 2.5 por unidad de velocidad por segundo.
        SistemaParticulas reversa = new SistemaParticulas();
        for (int i = 0; i < 20; i++) {
            reversa.emitirHumo(0.05f, 0, 0, 0, -16);
        }
        assertEquals(rapido.activas(), reversa.activas()); // Cuenta la rapidez, no el sentido.
    }

    /** El humo nace en la parte trasera del auto, a ras del suelo. */
    public void testElHumoSaleDetrasDelAuto() {
        SistemaParticulas p = new SistemaParticulas();
        p.emitirHumo(0.2f, 10, 20, 0, 16); // Ángulo 0: el frente apunta a -Z, la parte trasera a +Z.
        assertTrue(p.activas() > 0);
        for (int i = 0; i < p.activas(); i++) {
            assertTrue(p.z(i) > 20 + 1); // Detrás del auto.
            assertTrue(Math.abs(p.x(i) - 10) < 1.5f);
            assertTrue(p.y(i) < 1);
        }
    }

    /** Las fuentes sueltan gotas desde el centro de cada parque y cada gota vive lo que tarda en volver al agua. */
    public void testSalpicadurasDeLasFuentes() {
        SistemaParticulas p = new SistemaParticulas();
        assertEquals(Mapa.parques().size(), p.cantidadFuentes());
        p.emitirFuentes(0.1f); // 3 gotas por fuente.
        assertTrue(p.activas() > 0);
        assertEquals(p.activas(), p.activas(SistemaParticulas.TIPO_FUENTE));
        for (int i = 0; i < p.activas(); i++) {
            boolean enUnParque = false;
            for (int[] celda : Mapa.parques()) {
                if (Math.abs(p.x(i) - Mapa.centro(celda[1])) < 0.01f && Math.abs(p.z(i) - Mapa.centro(celda[0])) < 0.01f) {
                    enUnParque = true;
                }
            }
            assertTrue(enUnParque);
            assertTrue(p.vida(i) > 0.3f && p.vida(i) < 1.5f);
        }
        for (int i = 0; i < 40; i++) { // Con el tiempo las gotas caen y mueren: el pool no crece sin límite.
            p.emitirFuentes(0.05f);
            p.actualizar(0.05f);
        }
        assertTrue(p.activas() < 40 * p.cantidadFuentes() * 3);
    }

    /** La lluvia empieza apagada, la tecla L la alterna, cae alrededor de la cámara y respeta su tope. */
    public void testLluviaConTeclaYTope() {
        SistemaParticulas p = new SistemaParticulas();
        assertFalse(p.lluviaActiva());
        p.emitirLluvia(1, 0, 0);
        assertEquals(0, p.activas()); // Apagada: no llueve.
        p.tecla(GLFW_KEY_L);
        assertTrue(p.lluviaActiva());
        p.emitirLluvia(0.05f, 100, -50);
        assertTrue(p.activas(SistemaParticulas.TIPO_LLUVIA) > 0);
        for (int i = 0; i < p.activas(); i++) {
            assertTrue(Math.abs(p.x(i) - 100) <= 23.5f && Math.abs(p.z(i) + 50) <= 23.5f); // Alrededor de la cámara.
            assertTrue(p.y(i) >= 20);
        }
        for (int i = 0; i < 100; i++) { // Mucho tiempo emitiendo sin actualizar: llega al tope y no pasa.
            p.emitirLluvia(0.05f, 0, 0);
        }
        assertEquals(SistemaParticulas.MAX_LLUVIA, p.activas(SistemaParticulas.TIPO_LLUVIA));
        p.tecla(GLFW_KEY_L);
        assertFalse(p.lluviaActiva());
    }

    /** Las gotas de lluvia mueren al tocar el suelo, mucho antes de agotar su vida. */
    public void testLaLluviaMuereAlTocarElSuelo() {
        SistemaParticulas p = new SistemaParticulas();
        p.tecla(GLFW_KEY_L);
        p.emitirLluvia(0.05f, 0, 0);
        assertTrue(p.activas() > 0);
        for (int i = 0; i < 40; i++) { // 2 segundos sin nueva lluvia: caen de 22 a 0 en ≈ 1.2 s.
            p.actualizar(0.05f);
        }
        assertEquals(0, p.activas());
        assertEquals(0, p.activas(SistemaParticulas.TIPO_LLUVIA));
    }

    /** La lluvia no deja sin lugar al humo ni a las fuentes: el tope de lluvia es menor que la capacidad. */
    public void testLaLluviaNoLlenaElPool() {
        assertTrue(SistemaParticulas.MAX_LLUVIA < SistemaParticulas.CAPACIDAD);
        SistemaParticulas p = new SistemaParticulas();
        p.tecla(GLFW_KEY_L);
        for (int i = 0; i < 200; i++) {
            p.emitirLluvia(0.05f, 0, 0);
        }
        assertTrue(p.emitir(SistemaParticulas.TIPO_HUMO, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 0.2f));
    }
}
