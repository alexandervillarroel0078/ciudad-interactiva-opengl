package com.graphics.ciudad.vehiculo; // Mismo paquete que Chata: lee y fija su estado sin abrir una ventana OpenGL.

import com.graphics.ciudad.mundo.Mapa; // Paredes de las manzanas.
import java.util.function.IntPredicate; // Teclas simuladas.
import junit.framework.TestCase; // Comprobaciones de JUnit usadas por Maven.
import static org.lwjgl.glfw.GLFW.GLFW_KEY_A; // Doblar a la izquierda.
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D; // Doblar a la derecha.
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S; // Retroceder.
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE; // Freno.
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W; // Acelerar.

/** Comprueba el seguimiento, el límite de ángulo, el bloqueo contra manzanas y el reinicio de la chata. */
public class ChataTest extends TestCase {

    private static final float DT = 1f / 60; // Paso fijo de simulación.

    private static IntPredicate teclas(int... presionadas) {
        return tecla -> { // Verdadero solo para las teclas indicadas.
            for (int p : presionadas) {
                if (p == tecla) {
                    return true;
                }
            }
            return false;
        };
    }

    /** Ángulo entre la chata y el auto, en [-π, π]. */
    private static float relativo(Chata chata, Auto auto) {
        double d = chata.getAngulo() - auto.getAngulo();
        while (d > Math.PI) {
            d -= 2 * Math.PI;
        }
        while (d < -Math.PI) {
            d += 2 * Math.PI;
        }
        return (float) d;
    }

    /** Distancia del enganche al centro de la chata. */
    private static float distanciaAlEnganche(Chata chata, Auto auto) {
        float engancheX = auto.getX() + (float) Math.sin(auto.getAngulo()) * Chata.DIST_ENGANCHE;
        float engancheZ = auto.getZ() + (float) Math.cos(auto.getAngulo()) * Chata.DIST_ENGANCHE;
        return (float) Math.hypot(chata.getX() - engancheX, chata.getZ() - engancheZ);
    }

    /** Al reiniciar queda recta, detrás del auto en la salida, con las ruedas quietas. */
    public void testReset() {
        Chata chata = new Chata();
        chata.x = 3;
        chata.z = -8;
        chata.angulo = 1.2f;
        chata.anguloRueda = 4;
        chata.reset();
        assertEquals(Auto.X_INICIAL, chata.getX(), 1e-4f); // Alineada con el auto (que mira al norte).
        assertEquals(Auto.Z_INICIAL + Chata.DIST_ENGANCHE + Chata.LARGO, chata.getZ(), 1e-4f); // Detrás de él.
        assertEquals(0f, chata.getAngulo(), 0f);
        assertEquals(0f, chata.anguloRueda, 0f);
        assertTrue(Colisiones.puedeCircular(chata.getX(), chata.getZ(), Chata.RADIO)); // La salida es transitable para ella.
    }

    /** Con el auto recto, la chata se alinea y mantiene la distancia fija al enganche. */
    public void testSigueRectaDetrasDelAuto() {
        Auto auto = new Auto();
        Chata chata = new Chata();
        for (int i = 0; i < 60; i++) {
            auto.actualizar(DT, teclas(GLFW_KEY_W), (x, z) -> true, true);
            chata.actualizar(auto);
        }
        assertTrue(auto.getZ() < Auto.Z_INICIAL - 3); // El auto avanzó.
        assertEquals(0f, relativo(chata, auto), 1e-3f); // Misma orientación.
        assertEquals(Auto.X_INICIAL, chata.getX(), 1e-3f); // Misma línea.
        assertEquals(Chata.LARGO, distanciaAlEnganche(chata, auto), 1e-3f); // Barra rígida.
    }

    /** Al girar, la chata pivota en el enganche: su ángulo se acerca al del auto sin pasarlo y la barra no cambia de largo. */
    public void testSigueAlGirar() {
        Auto auto = new Auto();
        Chata chata = new Chata();
        float maximo = 0;
        for (int i = 0; i < 90; i++) { // 1.5 s acelerando y doblando a la izquierda.
            auto.actualizar(DT, teclas(GLFW_KEY_W, GLFW_KEY_A), (x, z) -> true, true);
            chata.actualizar(auto);
            assertEquals(Chata.LARGO, distanciaAlEnganche(chata, auto), 1e-3f);
            maximo = Math.max(maximo, Math.abs(relativo(chata, auto)));
        }
        assertTrue("el auto giró " + auto.getAngulo(), auto.getAngulo() > 0.3f);
        assertTrue("la chata giró " + chata.getAngulo(), chata.getAngulo() > 0.1f); // Gira con él...
        assertTrue(chata.getAngulo() < auto.getAngulo()); // ...pero con retraso: se le acerca desde atrás.
        assertTrue(maximo > 0.01f && maximo < Chata.ANGULO_RELATIVO_MAX); // Quedó torcida, dentro del límite.
        float antes = chata.getAngulo();
        for (int i = 0; i < 60; i++) { // Enderezando el volante, la chata se alinea.
            auto.actualizar(DT, teclas(GLFW_KEY_W), (x, z) -> true, true);
            chata.actualizar(auto);
        }
        assertTrue(Math.abs(relativo(chata, auto)) < 0.05f);
        assertTrue(antes != chata.getAngulo());
    }

    /** La pose candidata se calcula sin modificar el estado. */
    public void testPoseCandidataNoModificaElEstado() {
        Chata chata = new Chata();
        float x = chata.getX();
        float z = chata.getZ();
        float angulo = chata.getAngulo();
        float[] pose = chata.poseSiAutoEn(Auto.X_INICIAL + 3, Auto.Z_INICIAL - 4, 0.7f);
        assertEquals(3, pose.length);
        assertTrue(pose[0] != x || pose[1] != z); // La pose candidata es otra...
        assertEquals(x, chata.getX(), 0f); // ...pero la chata no se movió.
        assertEquals(z, chata.getZ(), 0f);
        assertEquals(angulo, chata.getAngulo(), 0f);
    }

    /** Un giro brusco no dobla la chata más del límite, y en reversa con volante a fondo tampoco. */
    public void testLimiteDeAnguloEnReversa() {
        Auto base = new Auto();
        base.x = Auto.X_INICIAL; // En la calle de la salida, donde la chata también cabe.
        base.z = Auto.Z_INICIAL - 20;
        base.velocidad = -3;
        Chata chata = chataConRelativo(base, -Chata.ANGULO_RELATIVO_MAX); // Chata ya en el tope (abierta hacia el oeste).
        assertTrue(chata.bloqueaPorTope(base.x, base.z + 0.1f, 0)); // Retroceder la cerraría más: bloqueado.
        assertTrue(chata.puedeMoverseA(base.x, base.z - 0.1f, 0)); // Avanzar, en cambio, siempre se puede.
        float[] pose = chata.poseSiAutoEn(base.x, base.z + 0.1f, 0); // Y la pose que se calcula queda recortada al tope.
        assertEquals(Chata.ANGULO_RELATIVO_MAX, Math.abs(pose[2]), 1e-4f);

        Auto auto = new Auto();
        auto.z = 0; // Lejos de la salida; sin paredes porque la prueba usa libre = true.
        auto.x = 0;
        chata.reset();
        chata.x = 0;
        chata.z = Chata.DIST_ENGANCHE + Chata.LARGO;
        chata.ultimoAutoX = 0; // El auto está en (0, 0), mirando al norte.
        chata.ultimoAutoZ = 0;
        chata.ultimoAutoAngulo = 0;
        float maximo = 0;
        for (int i = 0; i < 600; i++) { // 10 s en reversa con el volante a un lado.
            auto.actualizar(DT, teclas(GLFW_KEY_S, GLFW_KEY_D), (x, z) -> true, true);
            chata.actualizar(auto);
            maximo = Math.max(maximo, Math.abs(relativo(chata, auto)));
            assertEquals(Chata.LARGO, distanciaAlEnganche(chata, auto), 1e-2f); // Sigue siendo una barra rígida.
        }
        assertTrue("reversa real", auto.getVelocidad() < -1);
        assertTrue("ángulo relativo máximo " + maximo, maximo <= Chata.ANGULO_RELATIVO_MAX + 1e-3f);
    }

    /**
     * Bloqueo: con la chata a un paso de rozar la cara oeste de una manzana, llevar el auto a una posición (libre para
     * él) que la arrastraría contra la pared se rechaza; alejarse, o seguir pisándola sin empeorar, se permite.
     */
    public void testBloqueaSiLaChataChocaConUnaManzana() {
        float pared = Mapa.centro(1) - Mapa.TAM_CELDA / 2; // Cara oeste de la manzana de la columna 1 (fila de la salida).
        Chata chata = new Chata();
        chata.x = pared - Chata.RADIO - 0.05f; // A 5 cm de tocar la pared.
        chata.z = Auto.Z_INICIAL;
        chata.angulo = 0;
        float autoX = pared - 2f; // Posición candidata: libre para el auto, con el enganche girado hacia la pared.
        float autoZ = Auto.Z_INICIAL - 3;
        float autoAngulo = (float) Math.toRadians(80);
        assertTrue(Colisiones.puedeCircular(autoX, autoZ)); // El auto cabe.
        assertTrue(Colisiones.puedeCircular(chata.circulos()[0], chata.circulos()[1], Chata.RADIO)); // La chata hoy está libre.
        float[] futuros = Chata.circulos(chata.poseSiAutoEn(autoX, autoZ, autoAngulo));
        assertFalse(Colisiones.puedeCircular(futuros[0], futuros[1], Chata.RADIO)
            && Colisiones.puedeCircular(futuros[2], futuros[3], Chata.RADIO)); // Pero ahí chocaría.
        assertFalse(chata.puedeMoverseA(autoX, autoZ, autoAngulo)); // El auto no puede ir.

        assertTrue(chata.puedeMoverseA(pared - 3, Auto.Z_INICIAL - 4, 0)); // Un avance que la aleja de la pared sí.

        chata.x = pared - 0.3f; // Ahora la chata ya está metida en la pared (por ejemplo, tras un giro).
        assertTrue(chata.puedeMoverseA(pared - 6, Auto.Z_INICIAL + 1, 0)); // Alejarse está permitido: no queda atrapada.
    }

    /** Conduciendo con W y D desde la salida, la chata nunca invade una manzana ni un borde y la barra sigue entera. */
    public void testConduciendoLaChataNoAtraviesaParedes() {
        Auto auto = new Auto();
        Chata chata = new Chata();
        Colisiones.PosicionLibre libre = (x, z) -> Colisiones.puedeCircular(x, z) && chata.puedeMoverseA(x, z, auto.getAngulo());
        for (int vuelta = 0; vuelta < 4; vuelta++) { // Varios tramos alternando el giro, también contra las paredes.
            IntPredicate t = vuelta % 2 == 0 ? teclas(GLFW_KEY_W, GLFW_KEY_D) : teclas(GLFW_KEY_W, GLFW_KEY_A);
            for (int i = 0; i < 180; i++) {
                auto.actualizar(DT, t, libre, true);
                chata.actualizar(auto);
                float[] c = chata.circulos();
                assertTrue(Colisiones.holgura(c[0], c[1], Chata.RADIO) > -0.2f);
                assertTrue(Colisiones.holgura(c[2], c[3], Chata.RADIO) > -0.2f);
                assertTrue(Colisiones.puedeCircular(auto.getX(), auto.getZ()));
            }
        }
    }

    /** Pone al auto en (0, 0) con ese ángulo y a la chata recta detrás de él, lejos de las paredes (las pruebas usan libre = true). */
    private static Chata chataDetras(Auto auto, float angulo, float velocidad) {
        auto.x = 0;
        auto.z = 0;
        auto.angulo = angulo;
        auto.velocidad = velocidad;
        Chata chata = new Chata();
        float distancia = Chata.DIST_ENGANCHE + Chata.LARGO;
        chata.x = (float) Math.sin(angulo) * distancia; // Detrás: auto - frente · distancia.
        chata.z = (float) Math.cos(angulo) * distancia;
        chata.angulo = angulo;
        chata.ultimoAutoX = auto.x; // El auto "viene" de donde está: el primer cuadro mide solo su recorrido real.
        chata.ultimoAutoZ = auto.z;
        chata.ultimoAutoAngulo = angulo;
        return chata;
    }

    /** Chata colgada del enganche del auto con ese ángulo relativo (chata - auto), con la pose anterior del auto = la actual. */
    private static Chata chataConRelativo(Auto auto, float relativo) {
        Chata chata = new Chata();
        float anguloChata = auto.angulo + relativo;
        float engancheX = auto.x + (float) Math.sin(auto.angulo) * Chata.DIST_ENGANCHE;
        float engancheZ = auto.z + (float) Math.cos(auto.angulo) * Chata.DIST_ENGANCHE;
        chata.x = engancheX + (float) Math.sin(anguloChata) * Chata.LARGO; // centro = enganche - frenteChata * LARGO.
        chata.z = engancheZ + (float) Math.cos(anguloChata) * Chata.LARGO;
        chata.angulo = anguloChata;
        chata.ultimoAutoX = auto.x;
        chata.ultimoAutoZ = auto.z;
        chata.ultimoAutoAngulo = auto.angulo;
        return chata;
    }

    /**
     * Cruce de ±180°: el auto gira pasando por π (y por -π). El ángulo de la chata nunca salta de un cuadro a otro (un
     * salto de 2π sería un giro de vuelta entera), el relativo sigue chico y la barra mantiene su largo.
     */
    public void testCruceDe180SinSaltos() {
        for (int sentido = -1; sentido <= 1; sentido += 2) { // Girando a la izquierda (+) y a la derecha (-).
            Auto auto = new Auto();
            float inicio = (float) (sentido * (Math.PI - 0.2)); // Cerca de ±180°, antes de cruzarlo.
            Chata chata = chataDetras(auto, inicio, 10);
            IntPredicate t = sentido > 0 ? teclas(GLFW_KEY_W, GLFW_KEY_A) : teclas(GLFW_KEY_W, GLFW_KEY_D);
            float anterior = chata.getAngulo();
            boolean cruzo = false;
            for (int i = 0; i < 240; i++) {
                auto.actualizar(DT, t, (x, z) -> true, true);
                chata.actualizar(auto);
                assertTrue("salto " + (chata.getAngulo() - anterior), Math.abs(chata.getAngulo() - anterior) < 0.1f);
                assertTrue(Math.abs(relativo(chata, auto)) < Chata.ANGULO_RELATIVO_MAX);
                assertEquals(Chata.LARGO, distanciaAlEnganche(chata, auto), 1e-2f);
                anterior = chata.getAngulo();
                cruzo |= Math.abs(auto.getAngulo()) > Math.PI + 0.1;
            }
            assertTrue("el auto debía pasar de ±180°", cruzo);
        }
    }

    /** Marcha adelante: la chata gira del MISMO lado que el auto, siempre con retraso (nunca al revés). */
    public void testAdelanteGiraDelMismoLado() {
        for (int sentido = -1; sentido <= 1; sentido += 2) {
            Auto auto = new Auto();
            Chata chata = chataDetras(auto, 0, 10);
            IntPredicate t = sentido > 0 ? teclas(GLFW_KEY_W, GLFW_KEY_A) : teclas(GLFW_KEY_W, GLFW_KEY_D);
            float anterior = chata.getAngulo();
            for (int i = 0; i < 120; i++) {
                auto.actualizar(DT, t, (x, z) -> true, true);
                chata.actualizar(auto);
                assertTrue("la chata debe girar hacia donde gira el auto", sentido * (chata.getAngulo() - anterior) >= -1e-5f);
                assertTrue("la chata va detrás del auto, no adelante", sentido * relativo(chata, auto) <= 1e-5f);
                anterior = chata.getAngulo();
            }
            assertTrue(sentido * chata.getAngulo() > 0.1f); // Y efectivamente giró.
        }
    }

    /** Reversa recta con la chata alineada (relativo 0): se mantiene alineada y el auto no se bloquea. */
    public void testReversaRectaConRelativoCeroSeMantiene() {
        Auto auto = new Auto();
        auto.velocidad = -3;
        Chata chata = chataDetras(auto, 0, -3);
        Colisiones.PosicionLibre libre = (x, z) -> !chata.bloqueaPorTope(x, z, auto.getAngulo());
        for (int i = 0; i < 120; i++) {
            auto.actualizar(DT, teclas(GLFW_KEY_S), libre, true);
            chata.actualizar(auto);
            assertEquals(0f, relativo(chata, auto), 1e-4f);
        }
        assertTrue(auto.getVelocidad() < -3); // Siguió retrocediendo.
        assertTrue(auto.getZ() > 5); // Y recorrió distancia.
    }

    /** Reversa con un error de 2°: el ángulo crece (se abre) y nunca converge, como un remolque empujado. */
    public void testReversaConErrorPequenoSeAbre() {
        Auto auto = new Auto();
        auto.velocidad = -3;
        float inicial = (float) Math.toRadians(2);
        Chata chata = chataConRelativo(auto, inicial);
        Colisiones.PosicionLibre libre = (x, z) -> !chata.bloqueaPorTope(x, z, auto.getAngulo());
        float anterior = inicial;
        for (int i = 0; i < 90; i++) { // 1.5 s en reversa recta (unas 7 unidades).
            auto.actualizar(DT, teclas(GLFW_KEY_S), libre, true);
            chata.actualizar(auto);
            float actual = relativo(chata, auto);
            assertTrue("se cierra, no converge: " + actual, actual >= anterior - 1e-6f);
            anterior = actual;
        }
        assertTrue("debía abrirse, relativo " + anterior, anterior > 3 * inicial);
        assertTrue(anterior < Chata.ANGULO_RELATIVO_MAX);
    }

    /**
     * Reversa con el volante sostenido a cada lado: la chata se abre hasta el tope de 65° y ahí el auto queda bloqueado
     * (no puede seguir retrocediendo). Desde el tope, avanzar recto la endereza del todo.
     */
    public void testReversaConVolanteLlegaAlTopeYElAutoQuedaBloqueado() {
        for (int tecla : new int[] {GLFW_KEY_A, GLFW_KEY_D}) {
            Auto auto = new Auto();
            auto.velocidad = -3;
            Chata chata = chataConRelativo(auto, (float) Math.toRadians(1)); // Un empujón mínimo para empezar.
            Colisiones.PosicionLibre libre = (x, z) -> !chata.bloqueaPorTope(x, z, auto.getAngulo());
            for (int i = 0; i < 900; i++) { // 15 s con S y el volante a un lado.
                auto.actualizar(DT, teclas(GLFW_KEY_S, tecla), libre, true);
                chata.actualizar(auto);
                assertTrue(Math.abs(relativo(chata, auto)) <= Chata.ANGULO_RELATIVO_MAX + 1e-3f);
            }
            assertTrue("llegó al tope: " + relativo(chata, auto), Math.abs(relativo(chata, auto)) > Chata.ANGULO_RELATIVO_MAX - 0.05f);
            assertEquals(0f, auto.getVelocidad(), 0.5f); // El auto no puede seguir retrocediendo.
            float x = auto.getX();
            float z = auto.getZ();
            for (int i = 0; i < 120; i++) { // Insistir con la reversa no lo mueve.
                auto.actualizar(DT, teclas(GLFW_KEY_S, tecla), libre, true);
                chata.actualizar(auto);
            }
            assertTrue("sigue retrocediendo", Math.hypot(auto.getX() - x, auto.getZ() - z) < 1);
            for (int i = 0; i < 600; i++) { // Avanzar recto siempre se permite y endereza.
                auto.actualizar(DT, teclas(GLFW_KEY_W), libre, true);
                chata.actualizar(auto);
            }
            assertTrue("se enderezó: " + relativo(chata, auto), Math.abs(relativo(chata, auto)) < 0.05f);
        }
    }

    /** El mismo recorrido en reversa (arco de radio 9 hacia atrás) da el mismo ángulo con 30, 90 o 360 pasos. */
    public void testReversaMismoResultadoConDistintosDt() {
        float radio = 9;
        float arco = -12; // Negativo: el auto retrocede por el arco.
        float[] finales = new float[3];
        int[] pasos = {30, 90, 360};
        for (int k = 0; k < pasos.length; k++) {
            Auto auto = new Auto();
            Chata chata = chataDetras(auto, 0, -3);
            for (int i = 1; i <= pasos[k]; i++) {
                float a = arco * i / pasos[k] / radio;
                auto.x = radio * ((float) Math.cos(a) - 1);
                auto.z = -radio * (float) Math.sin(a);
                auto.angulo = a;
                chata.actualizar(auto);
            }
            finales[k] = relativo(chata, auto);
            assertTrue("debe abrirse en reversa: " + finales[k], Math.abs(finales[k]) > Math.toRadians(2));
            assertTrue(Math.abs(finales[k]) <= Chata.ANGULO_RELATIVO_MAX + 1e-3f);
        }
        assertEquals(finales[0], finales[2], 0.05f);
        assertEquals(finales[1], finales[2], 0.05f);
    }

    /** Auto quieto: aunque gire el ángulo (no hay recorrido), la chata no cambia de ángulo. */
    public void testQuietoNoCambiaElAngulo() {
        Auto auto = new Auto();
        Chata chata = chataDetras(auto, 0.3f, 0);
        float antes = chata.getAngulo();
        auto.angulo = 0.9f; // Gira sin avanzar.
        chata.actualizar(auto);
        assertEquals(antes, chata.getAngulo(), 1e-6f);
        assertEquals(antes, chata.poseSiAutoEn(auto.getX(), auto.getZ(), 1.2f)[2], 1e-6f); // Ni siquiera como pose candidata.
    }

    /**
     * Articulada, no rígida: en una curva de radio 9 el auto recorre un arco de 15 unidades. La chata queda con retraso (se
     * abre varios grados, del lado contrario al giro) y el resultado es el mismo con 30, 90 o 360 pasos (distintos dt).
     */
    public void testMismoResultadoConDistintosDt() {
        float radio = 9;
        float arco = 15;
        float[] finales = new float[3];
        int[] pasos = {30, 90, 360};
        for (int k = 0; k < pasos.length; k++) {
            Auto auto = new Auto();
            Chata chata = chataDetras(auto, 0, 10);
            for (int i = 1; i <= pasos[k]; i++) { // El auto recorre el arco a velocidad constante, en i pasos iguales.
                float a = arco * i / pasos[k] / radio; // Ángulo girado a la izquierda.
                auto.x = radio * ((float) Math.cos(a) - 1);
                auto.z = -radio * (float) Math.sin(a);
                auto.angulo = a;
                chata.actualizar(auto);
            }
            finales[k] = relativo(chata, auto);
            assertTrue("debe quedar con retraso, relativo " + finales[k], finales[k] < -Math.toRadians(5));
            assertTrue(finales[k] > -Chata.ANGULO_RELATIVO_MAX);
            assertEquals(Chata.LARGO, distanciaAlEnganche(chata, auto), 1e-2f);
        }
        assertEquals(finales[0], finales[2], 0.03f);
        assertEquals(finales[1], finales[2], 0.03f);
    }
}
