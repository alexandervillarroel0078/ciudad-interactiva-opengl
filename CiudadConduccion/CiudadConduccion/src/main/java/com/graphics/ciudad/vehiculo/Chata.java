package com.graphics.ciudad.vehiculo; // Agrupa el vehículo del jugador, su remolque y sus colisiones.

import com.graphics.ciudad.motor.Cubo; // Dibuja las piezas de la chata.
import com.graphics.ciudad.motor.Figuras; // Cilindros de las ruedas (los dibuja Rueda).
import com.graphics.ciudad.motor.Shader; // Factor de sombra solar y rotación de las ruedas.

/**
 * CHATA: el remolque que arrastra el auto del jugador.
 * Responsable de: su pose (x, z, angulo) como una barra RÍGIDA con pivote en el enganche del auto, el límite del ángulo
 * relativo (sin jackknife en reversa), su hitbox de 2 círculos, la pregunta "¿el auto puede ir a esta posición sin que la
 * chata choque?" y su dibujo con cajas y 2 ruedas.
 * Se comunica con: Auto (lee su pose, no lo modifica), Colisiones (manzanas y bordes), Juego (la actualiza, la reinicia y
 * la combina con el resto de las colisiones), Sombras y Trafico (reciben sus círculos). No usa OpenGL salvo dibujar().
 *
 * GEOMETRÍA. El frente del auto es (-sen a, -cos a). El ENGANCHE es el punto a DIST_ENGANCHE detrás del centro del auto:
 * enganche = auto - frente · DIST_ENGANCHE. La chata cuelga de él: centro = enganche - frenteChata · LARGO.
 * ÁNGULO. Cambia solo cuando el auto se desplaza: dAng = (ds / LARGO) · sen(anguloAuto - anguloChata), con ds CON SIGNO
 * (positivo adelante, negativo en reversa) y la diferencia normalizada a [-π, π]. Adelante la chata queda con retraso al
 * girar y converge sola al avanzar recto. En reversa se abre (como un remolque empujado), atenuada por GANANCIA_REVERSA,
 * hasta el tope de 65°; ahí puedeMoverseA no deja seguir retrocediendo, pero sí avanzar para enderezar.
 * SIN VELOCIDAD PROPIA. Depende de la distancia recorrida, no de dt, y los desplazamientos largos se integran en subpasos
 * de PASO_MAX: el resultado casi no cambia con los FPS. LIMITE: el ángulo entre chata y auto nunca pasa de
 * ANGULO_RELATIVO_MAX (65°): la chata se recorta ahí y el auto no puede retroceder más.
 */
public class Chata {

    // ==================== 0. CONSTANTES (valores ajustables) ====================
    public static final float DIST_ENGANCHE = 1.5f; // Del centro del auto al enganche (la cola del auto está a 1.3).
    public static final float LARGO = 2.4f; // Del enganche al centro de la chata: la barra rígida.
    public static final float LARGO_CAJA = 2.0f; // Largo de la caja de carga.
    public static final float ANCHO = 1.5f; // Ancho de la caja (algo menos que el auto, que mide 1.65).
    public static final float RADIO = 0.9f; // Radio de cada uno de los 2 círculos de colisión.
    public static final float ANGULO_RELATIVO_MAX = (float) Math.toRadians(65); // Tope del ángulo entre chata y auto.
    public static final float PASO_MAX = 0.25f; // Mayor recorrido del auto que se integra de una vez; más se parte en subpasos.
    public static final float GANANCIA_REVERSA = 0.6f; // Cuánto se abre en reversa respecto de lo que se endereza adelante (1 = igual; más alto = más difícil).
    public static final float ALTO_PISO = 0.5f; // Altura del piso de la caja sobre el asfalto.
    public static final float[] COLOR_CAJA = {0.55f, 0.58f, 0.62f}; // Chapa gris de la caja.
    public static final float[] COLOR_BARRA = {0.20f, 0.20f, 0.22f}; // Barra y bola del enganche.

    // ==================== 1. ESTADO ====================
    float x; // Centro de la chata en X.
    float z; // Centro de la chata en Z.
    float angulo; // Orientación en radianes; igual que el auto, cero apunta hacia -Z (hacia donde está el enganche).
    float anguloRueda; // Cuánto giraron sus ruedas, en radianes.
    float ultimoAutoX; // Pose del auto la última vez que la chata lo siguió: de ahí se mide lo que recorrió.
    float ultimoAutoZ;
    float ultimoAutoAngulo;

    /** Crea la chata detrás del auto, en la salida. */
    public Chata() {
        reset();
    }

    /** Vuelve a ponerla recta detrás del auto en su punto de partida; Juego lo llama al presionar R. */
    public void reset() {
        float[] pose = poseDetras(Auto.X_INICIAL, Auto.Z_INICIAL, 0); // El auto arranca mirando al norte.
        x = pose[0];
        z = pose[1];
        angulo = pose[2];
        anguloRueda = 0;
        ultimoAutoX = Auto.X_INICIAL;
        ultimoAutoZ = Auto.Z_INICIAL;
        ultimoAutoAngulo = 0;
    }

    // ==================== 2. SEGUIMIENTO ====================

    /** Pose recta {x, z, angulo} de una chata que va exactamente detrás de un auto en (autoX, autoZ) con ese ángulo. */
    private static float[] poseDetras(float autoX, float autoZ, float autoAngulo) {
        float frenteX = -(float) Math.sin(autoAngulo); // Frente del auto (y de la chata, que está alineada).
        float frenteZ = -(float) Math.cos(autoAngulo);
        float distancia = DIST_ENGANCHE + LARGO; // Del centro del auto al centro de la chata.
        return new float[] {autoX - frenteX * distancia, autoZ - frenteZ * distancia, autoAngulo};
    }

    /**
     * Pose {x, z, angulo} que tendría la chata si el auto estuviera en (autoX, autoZ) con autoAngulo. NO modifica nada:
     * Juego la usa para probar una posición candidata del auto antes de moverlo, y actualizar() para moverla de verdad.
     */
    public float[] poseSiAutoEn(float autoX, float autoZ, float autoAngulo) {
        float[] c = calcular(autoX, autoZ, autoAngulo);
        return new float[] {c[0], c[1], c[2]};
    }

    /**
     * Cálculo completo: {x, z, angulo, relativoSinRecortar, ds}. El ángulo recortado al tope es el que se usa; el relativo
     * SIN recortar y el signo de ds sirven a puedeMoverseA para saber si el auto seguiría cerrando la chata en reversa.
     *
     * ARTICULADA. La chata cuelga del enganche y su ángulo cambia según lo que el auto RECORRIÓ desde el último cuadro:
     * dAng = (ds / LARGO) · sen(anguloAuto - anguloChata). ds lleva SIGNO: positivo si el auto avanzó a lo largo de su
     * frente, negativo si retrocedió. Adelante la chata se endereza hacia el auto (equilibrio estable); en reversa el
     * mismo giro va al revés y se ABRE (equilibrio inestable: el empuje de un remolque), atenuado por GANANCIA_REVERSA.
     * Con el auto quieto o bloqueado (|ds| ≈ 0) la chata se queda exactamente donde está: ni gira ni la arrastra el
     * enganche. Un recorrido largo se parte en subpasos de PASO_MAX (estable y casi igual con cualquier dt); el ángulo
     * del auto se interpola entre el del cuadro anterior y el candidato.
     */
    private float[] calcular(float autoX, float autoZ, float autoAngulo) {
        float dx = autoX - ultimoAutoX; // Desplazamiento del auto desde el último cuadro.
        float dz = autoZ - ultimoAutoZ;
        float distancia = (float) Math.hypot(dx, dz);
        if (distancia < 1e-6f) { // Quieto o bloqueado: la chata no se mueve, aunque el auto haya girado un poco.
            float relativoQuieto = normalizar(angulo - autoAngulo); // Solo se corrige si el giro del auto la dejó pasada del tope.
            float recortadoQuieto = Math.max(-ANGULO_RELATIVO_MAX, Math.min(ANGULO_RELATIVO_MAX, relativoQuieto));
            return new float[] {x, z, autoAngulo + recortadoQuieto, relativoQuieto, 0};
        }
        float avance = dx * -(float) Math.sin(autoAngulo) + dz * -(float) Math.cos(autoAngulo); // Recorrido a lo largo del frente.
        float ds = avance < 0 ? -distancia : distancia;
        float ganancia = ds < 0 ? GANANCIA_REVERSA : 1;
        float nuevoAngulo = angulo;
        int pasos = (int) Math.ceil(distancia / PASO_MAX); // Subpasos de a lo sumo PASO_MAX.
        float giroAuto = normalizar(autoAngulo - ultimoAutoAngulo); // Cuánto giró el auto, por el lado corto.
        for (int i = 1; i <= pasos; i++) {
            float anguloAuto = ultimoAutoAngulo + giroAuto * i / pasos; // Ángulo del auto en este subpaso.
            float dif = normalizar(anguloAuto - nuevoAngulo); // Diferencia angular en [-π, π].
            nuevoAngulo += (ds / pasos) / LARGO * (float) Math.sin(dif) * ganancia;
        }
        float relativo = normalizar(nuevoAngulo - autoAngulo); // Ángulo entre la chata y el auto, en [-π, π].
        float recortado = Math.max(-ANGULO_RELATIVO_MAX, Math.min(ANGULO_RELATIVO_MAX, relativo)); // Tope: sin jackknife.
        nuevoAngulo = autoAngulo + recortado;
        float engancheX = autoX + (float) Math.sin(autoAngulo) * DIST_ENGANCHE; // enganche = auto - frente · DIST.
        float engancheZ = autoZ + (float) Math.cos(autoAngulo) * DIST_ENGANCHE;
        float frenteX = -(float) Math.sin(nuevoAngulo); // Frente de la chata.
        float frenteZ = -(float) Math.cos(nuevoAngulo);
        return new float[] {engancheX - frenteX * LARGO, engancheZ - frenteZ * LARGO, nuevoAngulo, relativo, ds}; // A LARGO del enganche.
    }

    /** Mueve la chata detrás del auto; Juego lo llama una vez por cuadro, después de mover al auto. */
    public void actualizar(Auto auto) {
        float[] pose = poseSiAutoEn(auto.getX(), auto.getZ(), auto.getAngulo()); // Nueva pose.
        float frenteX = -(float) Math.sin(pose[2]); // Frente de la chata en su nueva orientación.
        float frenteZ = -(float) Math.cos(pose[2]);
        float avance = (pose[0] - x) * frenteX + (pose[1] - z) * frenteZ; // Lo recorrido a lo largo de su eje (con signo).
        anguloRueda += Rueda.giroPorDistancia(avance); // Las ruedas giran lo que avanzó la chata.
        x = pose[0];
        z = pose[1];
        angulo = pose[2];
        ultimoAutoX = auto.getX();
        ultimoAutoZ = auto.getZ();
        ultimoAutoAngulo = auto.getAngulo();
    }

    /** Lleva un ángulo al intervalo [-π, π] para medir siempre por el lado más corto. */
    private static float normalizar(float a) {
        while (a > Math.PI) {
            a -= (float) (2 * Math.PI);
        }
        while (a < -Math.PI) {
            a += (float) (2 * Math.PI);
        }
        return a;
    }

    // ==================== 3. HITBOX Y BLOQUEO ====================

    /** Centros {x1, z1, x2, z2} de los 2 círculos de una pose: a 1/3 y 2/3 del largo de la caja, contando desde el frente. */
    public static float[] circulos(float[] pose) {
        float frenteX = -(float) Math.sin(pose[2]); // Hacia el frente de la chata (hacia el auto).
        float frenteZ = -(float) Math.cos(pose[2]);
        float primero = LARGO_CAJA / 2 - LARGO_CAJA / 3; // Del centro al círculo delantero (1/3 desde el frente).
        float segundo = LARGO_CAJA / 2 - 2 * LARGO_CAJA / 3; // Y al trasero (2/3): negativo, hacia atrás.
        return new float[] {pose[0] + frenteX * primero, pose[1] + frenteZ * primero,
            pose[0] + frenteX * segundo, pose[1] + frenteZ * segundo};
    }

    /** Círculos de la chata en su pose actual. */
    public float[] circulos() {
        return circulos(new float[] {x, z, angulo});
    }

    /** Verdadero si el auto iría en reversa y la chata, sin recortar, pasaría del tope: seguiría cerrándose. */
    boolean bloqueaPorTope(float autoX, float autoZ, float autoAngulo) {
        float[] calculo = calcular(autoX, autoZ, autoAngulo);
        return calculo[4] < 0 && Math.abs(calculo[3]) > ANGULO_RELATIVO_MAX;
    }

    /**
     * Indica si el auto puede ir a (autoX, autoZ) con autoAngulo. No puede si va en reversa y el ángulo de la chata, sin
     * recortar, superaría ANGULO_RELATIVO_MAX (la chata está en el tope y retroceder la cerraría más). Tampoco si la
     * chata choca con manzanas o bordes. Si algún
     * círculo de la chata quedaría metido en un obstáculo, solo se rechaza cuando queda PEOR que ahora (menos holgura):
     * alejarse siempre se permite, como en Trafico.bloquea, así que el auto nunca queda atrapado.
     */
    public boolean puedeMoverseA(float autoX, float autoZ, float autoAngulo) {
        if (bloqueaPorTope(autoX, autoZ, autoAngulo)) {
            return false; // Retroceder se bloquea; avanzar nunca, así el auto puede enderezar y salir.
        }
        float[] calculo = calcular(autoX, autoZ, autoAngulo);
        float[] despues = circulos(new float[] {calculo[0], calculo[1], calculo[2]}); // Dónde quedarían los círculos.
        float[] antes = circulos(); // Dónde están ahora.
        for (int i = 0; i < despues.length; i += 2) { // Cada círculo.
            if (Colisiones.puedeCircular(despues[i], despues[i + 1], RADIO)) {
                continue; // Cabe: nada que comprobar.
            }
            float holguraDespues = Colisiones.holgura(despues[i], despues[i + 1], RADIO);
            float holguraAntes = Colisiones.holgura(antes[i], antes[i + 1], RADIO);
            if (holguraDespues < holguraAntes) { // Se mete más adentro del obstáculo.
                return false;
            }
        }
        return true;
    }

    // ==================== 4. DIBUJO ====================

    /**
     * Dibuja la chata con cajas y dos ruedas, más la barra y la bola del enganche que la unen al auto. Usa el mismo
     * shader y los mismos uniforms que el auto (iluminación y niebla normales); toma de él la sombra solar.
     */
    public void dibujar(Cubo cubo, Figuras figuras, Shader shader, Auto auto) {
        shader.decimal("uFactorSombraSol", auto.getFactorSombraSol()); // Misma sombra de edificios que el auto.
        float[] c = COLOR_CAJA;
        float[] b = COLOR_BARRA;
        float mitad = LARGO_CAJA / 2;
        pieza(cubo, 0, ALTO_PISO, 0, ANCHO, 0.10f, LARGO_CAJA, c[0], c[1], c[2]); // Piso.
        for (int lado = -1; lado <= 1; lado += 2) { // Paredes laterales bajas.
            pieza(cubo, lado * (ANCHO / 2 - 0.04f), ALTO_PISO + 0.25f, 0, 0.08f, 0.40f, LARGO_CAJA, c[0], c[1], c[2]);
        }
        pieza(cubo, 0, ALTO_PISO + 0.25f, -mitad + 0.04f, ANCHO, 0.40f, 0.08f, c[0], c[1], c[2]); // Pared delantera.
        pieza(cubo, 0, ALTO_PISO + 0.25f, mitad - 0.04f, ANCHO, 0.40f, 0.08f, c[0], c[1], c[2]); // Compuerta trasera.
        float largoBarra = LARGO - mitad; // Del enganche al frente de la caja.
        pieza(cubo, 0, ALTO_PISO, -(mitad + largoBarra / 2), 0.12f, 0.10f, largoBarra, b[0], b[1], b[2]); // Barra al auto.
        pieza(cubo, 0, ALTO_PISO + 0.05f, -LARGO, 0.2f, 0.2f, 0.2f, b[0], b[1], b[2]); // Bola del enganche.
        float largoSoporte = DIST_ENGANCHE - 1.2f; // Del paragolpes del auto (a 1.2 de su centro) al enganche.
        float centroSoporte = (DIST_ENGANCHE + 1.2f) / 2; // A mitad de ese tramo, detrás del auto.
        float seno = (float) Math.sin(auto.getAngulo());
        float coseno = (float) Math.cos(auto.getAngulo());
        cubo.cajaGirada(auto.getX() + seno * centroSoporte, ALTO_PISO, auto.getZ() + coseno * centroSoporte,
            0.12f, 0.10f, largoSoporte, b[0], b[1], b[2], auto.getAngulo()); // Soporte fijo al auto (gira con él).
        for (int lado = -1; lado <= 1; lado += 2) { // Dos ruedas en el centro, como las del auto.
            Rueda.dibujar(cubo, figuras, shader, x, z, angulo, lado * (ANCHO / 2 + 0.05f), 0, anguloRueda, 0);
        }
        shader.decimal("uFactorSombraSol", 1); // Vuelve a sol pleno para el siguiente objeto.
    }

    /** Transforma una pieza del espacio local de la chata al de la ciudad (igual que Auto.pieza). */
    private void pieza(Cubo cubo, float localX, float y, float localZ, float sx, float sy, float sz, float r, float g, float b) {
        float coseno = (float) Math.cos(angulo);
        float seno = (float) Math.sin(angulo);
        cubo.cajaGirada(x + coseno * localX + seno * localZ, y, z - seno * localX + coseno * localZ, sx, sy, sz, r, g, b, angulo);
    }

    // ==================== 5. CONSULTAS ====================

    /** Centro X de la chata. */
    public float getX() {
        return x;
    }

    /** Centro Z de la chata. */
    public float getZ() {
        return z;
    }

    /** Orientación en radianes; cero apunta hacia -Z. */
    public float getAngulo() {
        return angulo;
    }
}
