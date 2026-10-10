package com.graphics.ciudad.vehiculo; // Prueba las marcas de frenado sin abrir una ventana OpenGL.

import com.graphics.ciudad.mundo.Mapa; // Para ubicar un punto fuera de las calles.
import junit.framework.TestCase; // Proporciona las comprobaciones de JUnit usadas por Maven.

/** Comprueba cuándo se agregan segmentos, el reciclaje, el desvanecimiento, el vaciado y la pausa. */
public class MarcasFrenadoTest extends TestCase {

    private static final float DT = 0.05f; // Paso de tiempo simulado.
    private static final float X = Auto.X_INICIAL; // Carril de la calle de salida (siempre es calle).
    private static final float Z0 = Auto.Z_INICIAL; // Punto de partida a lo largo de esa calle.
    private static final float RAPIDO = 15; // Velocidad alta, unidades/s.
    private static final float PASO = 0.5f; // Lo que avanza el auto por cuadro: más que DISTANCIA_MINIMA.

    /** Simula n cuadros del auto yendo hacia -Z desde el cuadro "desde", con los parámetros dados. */
    private static void conducir(MarcasFrenado m, int desde, int n, float velocidad, boolean frenando, float direccion) {
        for (int i = desde; i < desde + n; i++) {
            m.actualizar(DT, X, Z0 - i * PASO, 0, velocidad, frenando, direccion);
        }
    }

    /** Frenar fuerte (rápido y con el freno pulsado) deja segmentos unidos en una tira. */
    public void testSeAgregaAlFrenarFuerte() {
        MarcasFrenado m = new MarcasFrenado();
        conducir(m, 0, 5, RAPIDO, true, 0);
        assertEquals(5, m.cantidad());
        assertFalse(m.continua(0)); // El primero abre la tira.
        assertTrue(m.continua(1));
        assertTrue(m.continua(4));
    }

    /** Un giro cerrado a velocidad alta (derrape) también deja marca, sin freno. */
    public void testSeAgregaAlDerrapar() {
        MarcasFrenado m = new MarcasFrenado();
        conducir(m, 0, 4, RAPIDO, false, Rueda.ANGULO_MAX_DIRECCION);
        assertEquals(4, m.cantidad());
    }

    /** Ni frenando poco a poco (lento), ni rápido sin frenar ni girar, ni girando lento, deja marca. */
    public void testNoSeAgregaAlIrLento() {
        MarcasFrenado m = new MarcasFrenado();
        conducir(m, 0, 10, 3, true, 0); // Frena, pero va lento.
        conducir(m, 10, 10, RAPIDO, false, 0); // Rápido, pero sin frenar ni girar.
        conducir(m, 20, 10, 3, false, Rueda.ANGULO_MAX_DIRECCION); // Gira a fondo, pero lento.
        conducir(m, 30, 10, RAPIDO, false, 0.1f); // Rápido, pero con un giro suave.
        assertEquals(0, m.cantidad());
    }

    /** Si el auto no avanza la distancia mínima desde el último segmento, no se agrega otro. */
    public void testExigeDistanciaMinima() {
        MarcasFrenado m = new MarcasFrenado();
        m.actualizar(DT, X, Z0, 0, RAPIDO, true, 0);
        for (int i = 0; i < 10; i++) { // Se mueve 0.1 por cuadro: hasta 0.4 no hay segmento nuevo.
            m.actualizar(DT, X, Z0 - 0.1f * (i + 1) * 0.3f, 0, RAPIDO, true, 0);
        }
        assertEquals(1, m.cantidad()); // 10 · 0.03 = 0.3 < 0.4.
        m.actualizar(DT, X, Z0 - 1, 0, RAPIDO, true, 0);
        assertEquals(2, m.cantidad());
    }

    /** Al terminar la condición se corta la tira: la marca siguiente no se une a la anterior. */
    public void testSeCortaLaTira() {
        MarcasFrenado m = new MarcasFrenado();
        conducir(m, 0, 3, RAPIDO, true, 0);
        conducir(m, 3, 2, RAPIDO, false, 0); // Suelta el freno.
        conducir(m, 5, 3, RAPIDO, true, 0); // Vuelve a frenar.
        assertEquals(6, m.cantidad());
        assertFalse(m.continua(3)); // Primer segmento de la tira nueva.
        assertTrue(m.continua(4));
    }

    /** Un salto grande (el reinicio teletransporta al auto) también abre una tira nueva. */
    public void testUnSaltoAbreUnaTiraNueva() {
        MarcasFrenado m = new MarcasFrenado();
        conducir(m, 0, 2, RAPIDO, true, 0);
        m.actualizar(DT, X, Z0 - 30, 0, RAPIDO, true, 0);
        assertEquals(3, m.cantidad());
        assertFalse(m.continua(2));
    }

    /** Solo se marcan las calles: sobre una manzana (pasto, parque, edificio) no se agrega nada. */
    public void testNoSeMarcaFueraDeLasCalles() {
        MarcasFrenado m = new MarcasFrenado();
        float centroManzanaX = Mapa.centro(1); // Fila y columna 1 son manzana en cualquier mapa de la ciudad.
        float centroManzanaZ = Mapa.centro(1);
        assertFalse(Mapa.esCalleEn(centroManzanaX, centroManzanaZ));
        for (int i = 0; i < 5; i++) {
            m.actualizar(DT, centroManzanaX, centroManzanaZ - i * PASO, 0, RAPIDO, true, 0);
        }
        assertEquals(0, m.cantidad());
    }

    /** Con el buffer lleno, el segmento nuevo recicla al más viejo y el total no pasa de MAX_SEGMENTOS. */
    public void testReciclajeAlLlegarAlMaximo() {
        MarcasFrenado m = new MarcasFrenado();
        float dt = 0.0001f; // Casi sin envejecer: ningún segmento muere por tiempo, solo por reciclaje.
        int total = 0;
        for (int tira = 0; tira < MarcasFrenado.MAX_SEGMENTOS / 4 + 20; tira++) { // Tiras cortas de 4 segmentos.
            for (int i = 0; i < 4; i++) {
                m.actualizar(dt, X, Z0 - i * PASO, 0, RAPIDO, true, 0);
                total++;
            }
            m.actualizar(dt, X, Z0, 0, RAPIDO, false, 0); // Suelta el freno: corta la tira.
        }
        assertTrue(total > MarcasFrenado.MAX_SEGMENTOS);
        assertEquals(MarcasFrenado.MAX_SEGMENTOS, m.cantidad());
        assertTrue(m.vida(MarcasFrenado.MAX_SEGMENTOS - 1) > m.vida(0)); // El último es el más nuevo.
        assertFalse(m.continua(0)); // El más viejo no se une a nada.
        // Reciclar en medio de una tira: el que queda como más viejo pierde su unión con el anterior.
        MarcasFrenado n = new MarcasFrenado();
        for (int i = 0; i < MarcasFrenado.MAX_SEGMENTOS; i++) { // Una sola tira larga (en tramos de 1 unidad, con saltos en Z).
            n.actualizar(dt, X, Z0 - (i % 40) * PASO, 0, RAPIDO, true, 0);
        }
        assertEquals(MarcasFrenado.MAX_SEGMENTOS, n.cantidad());
        n.actualizar(dt, X, Z0 - 1, 0, RAPIDO, true, 0);
        assertEquals(MarcasFrenado.MAX_SEGMENTOS, n.cantidad()); // Pisó al más viejo.
        assertFalse(n.continua(0));
    }

    /** La vida y la opacidad bajan con dt, y al agotarse la vida el segmento desaparece. */
    public void testDesvanecimientoConDt() {
        MarcasFrenado m = new MarcasFrenado();
        conducir(m, 0, 3, RAPIDO, true, 0);
        assertEquals(MarcasFrenado.ALFA_MAXIMA, m.alfa(0), 1e-5f);
        for (int i = 0; i < 100; i++) { // 5 s con el auto parado y sin frenar: solo envejece.
            m.actualizar(DT, X, Z0, 0, 0, false, 0);
        }
        assertEquals(3, m.cantidad());
        assertEquals(MarcasFrenado.VIDA - 5 - 3 * 0, m.vida(0), 0.2f); // Le queda ≈ 3 s de 8.
        assertTrue(m.alfa(0) > 0 && m.alfa(0) < MarcasFrenado.ALFA_MAXIMA); // Ya se está desvaneciendo.
        float antes = m.alfa(0);
        m.actualizar(1, X, Z0, 0, 0, false, 0);
        assertTrue(m.alfa(0) < antes);
        for (int i = 0; i < 100; i++) { // Pasa el resto de la vida.
            m.actualizar(DT, X, Z0, 0, 0, false, 0);
        }
        assertEquals(0, m.cantidad());
    }

    /** La opacidad es máxima en la primera mitad de la vida y 0 al final. */
    public void testAlfaSegunVida() {
        assertEquals(MarcasFrenado.ALFA_MAXIMA, MarcasFrenado.alfaDeVida(MarcasFrenado.VIDA), 1e-6f);
        assertEquals(MarcasFrenado.ALFA_MAXIMA, MarcasFrenado.alfaDeVida(MarcasFrenado.VIDA / 2), 1e-6f);
        assertEquals(MarcasFrenado.ALFA_MAXIMA / 2, MarcasFrenado.alfaDeVida(MarcasFrenado.VIDA / 4), 1e-6f);
        assertEquals(0f, MarcasFrenado.alfaDeVida(0), 0f);
        assertEquals(0f, MarcasFrenado.alfaDeVida(-1), 0f);
    }

    /** Con dt = 0 (pausa) no se agrega nada ni cambia la vida. */
    public void testPausaNoCambiaNada() {
        MarcasFrenado m = new MarcasFrenado();
        conducir(m, 0, 3, RAPIDO, true, 0);
        float vida = m.vida(0);
        for (int i = 3; i < 13; i++) {
            m.actualizar(0, X, Z0 - i * PASO, 0, RAPIDO, true, 0);
        }
        assertEquals(3, m.cantidad());
        assertEquals(vida, m.vida(0), 0f);
    }

    /** Vaciar (tecla R) deja el buffer sin segmentos y la tira siguiente empieza limpia. */
    public void testVaciarAlReiniciar() {
        MarcasFrenado m = new MarcasFrenado();
        conducir(m, 0, 5, RAPIDO, true, 0);
        m.vaciar();
        assertEquals(0, m.cantidad());
        conducir(m, 0, 2, RAPIDO, true, 0);
        assertEquals(2, m.cantidad());
        assertFalse(m.continua(0)); // No se une a nada de antes del reinicio.
    }

    /** Las ruedas traseras quedan detrás del auto: con ángulo 0 (frente a -Z), a +EJE_RUEDA y a ±LADO_RUEDA. */
    public void testPuntosDeLasRuedasTraseras() {
        MarcasFrenado m = new MarcasFrenado();
        m.actualizar(DT, X, Z0, 0, RAPIDO, true, 0);
        assertEquals(X - Rueda.LADO_RUEDA, m.xIzquierda(0), 1e-4f);
        assertEquals(Z0 + Rueda.EJE_RUEDA, m.zIzquierda(0), 1e-4f); // Detrás del centro (+Z con ángulo 0).
    }
}
