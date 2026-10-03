package com.graphics.ciudad.vehiculo; // Agrupa el vehículo del jugador y sus colisiones.

import com.graphics.ciudad.iluminacion.Sombras; // Sombra de los edificios sobre el auto: objetivo y suavizado del factor solar.
import com.graphics.ciudad.motor.Cubo; // Dibuja cada pieza del vehículo.
import com.graphics.ciudad.motor.Figuras; // Cilindros de las ruedas (los dibuja Rueda).
import com.graphics.ciudad.motor.Shader; // Emisión de las luces de freno y reversa, y rotación de las ruedas (uRotacion).
import com.graphics.ciudad.mundo.Mapa; // Centro de la calle de salida y ancho de celda.
import java.util.function.IntPredicate; // Pregunta si una tecla está presionada sin depender de GLFW.
import static org.lwjgl.glfw.GLFW.*; // Incluye las constantes de las teclas de conducción.

/**
 * AUTO: el vehículo del jugador.
 * Responsable de: su estado (posición, orientación y velocidad), la física por cuadro con deltaTime
 * (aceleración, resistencia, freno, límites y giro), el reinicio con R (reset()) y el dibujo con piezas locales.
 * Se comunica con: Colisiones (valida cada posición propuesta), Cubo (dibuja las piezas), Juego (lo actualiza
 * y lo reinicia); Camara, Iluminacion, Entregas y Minimapa leen su estado con los getters.
 * Además: ruedas redondas (vehiculo/Rueda, compartidas con el tráfico) que giran según la distancia recorrida,
 * delanteras que doblan con la dirección, luces de freno (al frenar) y de reversa (al ir hacia atrás), de día y de noche.
 * FAROS: el auto no guarda si están encendidos; Juego le pasa Iluminacion.farosEncendidos() (tecla F) al dibujar.
 * Con F encendido, los faros son bombillas blancas emisivas y las traseras, luces de posición (rojo tenue emisivo);
 * con F apagado, faros gris oscuro y traseras rojo oscuro. El freno (rojo intenso) tiene prioridad en ambos casos.
 * Orden de lectura: variables, reinicio, movimiento, dibujo.
 */
public class Auto {

    // ==================== 0. CONSTANTES DE LA FÍSICA (valores ajustables) ====================
    public static final float VELOCIDAD_MAX = 16; // Velocidad máxima hacia delante, en unidades por segundo (≈ 58 km/h).
    public static final float VELOCIDAD_REVERSA = 6; // Velocidad máxima en reversa, en unidades por segundo (se aplica como -6).
    public static final float ACELERACION = 9; // Aceleración del motor, en unidades por segundo cuadrado.
    public static final float RESISTENCIA = 0.7f; // Pérdida de velocidad normal al rodar, por segundo (decaimiento exponencial).
    public static final float FRENO = 7; // Pérdida de velocidad mientras se mantiene Espacio: diez veces la resistencia normal.
    public static final float FRICCION_ROCE = 30; // Desaceleración (u/s²) al rozar una pared de frente; se escala por la fracción bloqueada.
    public static final float FRACCION_MINIMA_DESLIZAMIENTO = 0.1f; // Con menos frente en el eje libre (≈ 84° contra la pared) se detiene.
    public static final float VELOCIDAD_GIRO = 0.11f; // Radianes girados por segundo y por unidad de velocidad: el giro crece con la velocidad.

    // ==================== 0b. RUEDAS, DIRECCIÓN Y LUCES (valores ajustables) ====================
    // Medidas, colores, ubicación y dirección de las ruedas: en vehiculo/Rueda, compartidas con el tráfico.
    public static final float UMBRAL_MOVIMIENTO = 0.1f; // Por debajo de esta velocidad (unidades/s) el auto se considera detenido.
    // Faros y luces traseras (posición y freno) usan los colores de LucesVehiculo, compartidos con el tráfico.
    public static final float[] COLOR_CARROCERIA = {0.95f, 0.24f, 0.12f}; // Rojo del auto: carrocería y cabina (techo y parantes).
    public static final float[] COLOR_REVERSA_APAGADA = {0.75f, 0.75f, 0.72f}; // Luz de reversa apagada: plástico claro.
    public static final float[] COLOR_REVERSA = {1.00f, 1.00f, 0.95f}; // Luz de reversa encendida: blanca, emisiva.

    // ==================== 1. VARIABLES DEL AUTO ====================
    public static final float RADIO_AUTO = 1.65f; // Radio que contiene al vehículo para las colisiones.
    // Salida: calle del borde oeste (columna 0), en la última cuadra antes de la esquina suroeste, mirando al norte.
    // FILA_SALIDA = penúltima fila: es impar, así que queda a mitad de cuadra, entre dos cruces, lejos de los pasos
    // peatonales (que están pegados a los cruces). Se circula por la derecha, así que el auto no parte sobre la línea
    // amarilla (el centro de la calle, X = -50) sino en el centro del carril derecho: mirando al norte, la derecha es +X.
    // Es el mismo desplazamiento que usa el tráfico (Vehiculo.DESPLAZAMIENTO_CARRIL). Todo sale de Mapa: con otro tamaño
    // de MAPA la salida se acomoda sola.
    public static final int COLUMNA_SALIDA = 0; // Calle del borde oeste.
    public static final int FILA_SALIDA = Mapa.MAPA.length - 2; // Penúltima fila: mitad de la última cuadra.
    public static final float CARRIL_SALIDA = Mapa.TAM_CELDA / 4; // 2.5: del centro de la calle al centro del carril derecho.
    public static final float X_INICIAL = Mapa.centro(COLUMNA_SALIDA) + CARRIL_SALIDA; // -47.5: carril derecho de la calle de la columna 0.
    public static final float Z_INICIAL = Mapa.centro(FILA_SALIDA); // 40 con el mapa 11 × 11: fila 9, a mitad de cuadra.
    float x = X_INICIAL; // Posición horizontal inicial: carril derecho de la calle.
    float z = Z_INICIAL; // Posición inicial sobre el eje que recorre el fondo de la ciudad.
    float angulo = 0; // Orientación en radianes; cero apunta hacia -Z.
    float velocidad = 0; // Unidades por segundo; un valor negativo significa reversa.
    float anguloRueda = 0; // Cuánto giró cada rueda sobre su eje, en radianes (crece hacia adelante, decrece en reversa).
    float anguloDireccion = 0; // Giro de las ruedas delanteras respecto del auto, en radianes (positivo = hacia la izquierda).
    boolean frenando = false; // true mientras se frena: S con el auto yendo hacia adelante, o Espacio.
    boolean enReversa = false; // true mientras el auto se mueve hacia atrás.
    float factorSombraSol = 1f; // 1 = sol pleno; baja hacia Sombras.FACTOR_SOMBRA_SOL bajo la sombra de un edificio (suavizado en actualizar()).

    // ==================== 2. REINICIO ====================

    /** Coloca nuevamente el auto en su punto de partida; Juego lo llama al presionar R. */
    public void reset() {
        x = X_INICIAL; // Recupera la coordenada X de inicio.
        z = Z_INICIAL; // Recupera la coordenada Z de inicio.
        angulo = 0; // Orienta el frente hacia -Z.
        velocidad = 0; // Detiene cualquier movimiento previo.
        anguloRueda = 0; // Ruedas en su posición inicial.
        anguloDireccion = 0; // Ruedas delanteras derechas.
        frenando = false; // Sin luces de freno.
        enReversa = false; // Sin luz de reversa.
        factorSombraSol = 1f; // Vuelve a sol pleno: la posición de salida no está bajo ningún edificio.
    }

    // ==================== 3. MOVIMIENTO POR CUADRO ====================

    /** Actualiza la conducción chocando solo contra manzanas y bordes (sin tráfico); la usan las pruebas. */
    public void actualizar(float deltaTime, IntPredicate pulsada) {
        actualizar(deltaTime, pulsada, Colisiones::puedeCircular, true); // Misma física, con la ciudad como único obstáculo, de día.
    }

    /**
     * Actualiza la conducción; deltaTime contiene los segundos transcurridos entre cuadros y libre dice si el auto
     * cabe en una posición (Juego combina manzanas, bordes y tráfico). esDeDia habilita la sombra de los edificios
     * sobre el auto (Sombras.objetivoFactorSombraSol): de noche no tiene efecto.
     *
     * DESLIZAMIENTO POR EJES. Si el paso completo (dx, dz) choca, no se descarta entero: se prueba cada eje por
     * separado, primero el de mayor desplazamiento. Una pared de la ciudad es paralela a X o a Z, así que cuando el
     * auto la roza en diagonal solo UNO de los dos componentes la atraviesa: se anula ese y se conserva el otro, y el
     * auto "resbala" a lo largo de la pared en lugar de quedar pegado. Solo si ningún eje está libre se detiene.
     * REPARTO DE LA VELOCIDAD. La velocidad es un número a lo largo del frente (-sen a, -cos a). Al deslizar, el
     * desplazamiento del cuadro usa solo la componente del eje libre, así que el auto avanza velocidad · |frente del eje
     * libre| a lo largo de la pared. La velocidad, además, pierde por ROCE una desaceleración FRICCION_ROCE escalada por
     * la fracción bloqueada (1 − |frente del eje libre|): rozando casi en paralelo casi no frena; cuanto más de frente,
     * más frena. Con la pared de frente (fracción libre < FRACCION_MINIMA_DESLIZAMIENTO) se detiene, como antes.
     * Al no quedar en cero, el giro (proporcional a la velocidad) sigue funcionando y el jugador puede separarse de la
     * pared sin dar marcha atrás.
     * POR QUÉ RESTAR Y NO MULTIPLICAR. Multiplicar la velocidad por un factor fijo (por ejemplo 0.9) en CADA cuadro
     * depende de los FPS: a 30 FPS se multiplica 30 veces por segundo (0.9³⁰ ≈ 0.04) y a 144 FPS, 144 veces
     * (0.9¹⁴⁴ ≈ 0.0000003): el mismo segundo de roce frena distinto según la pantalla. Restar desaceleración · dt no
     * depende: en un segundo la suma de los dt es 1 con cualquier FPS, así que siempre se pierde FRICCION_ROCE · fracción
     * por segundo (la resistencia de arriba también es independiente: e^(−k·dt) repetido da e^(−k·1) en un segundo).
     */
    public void actualizar(float deltaTime, IntPredicate pulsada, Colisiones.PosicionLibre libre, boolean esDeDia) {
        float velocidadAntes = velocidad; // Velocidad al empezar el cuadro: decide si S frena o acelera en reversa.
        float acelerador = 0; // Sin teclas pulsadas no se aplica aceleración del motor.
        boolean teclaAtras = pulsada.test(GLFW_KEY_S) || pulsada.test(GLFW_KEY_DOWN); // S o flecha abajo.
        boolean teclaFreno = pulsada.test(GLFW_KEY_SPACE); // Espacio.

        if (pulsada.test(GLFW_KEY_W) || pulsada.test(GLFW_KEY_UP)) { // Acepta W o flecha arriba para avanzar.
            acelerador += 1; // Solicita aceleración hacia delante.
        }

        if (pulsada.test(GLFW_KEY_S) || pulsada.test(GLFW_KEY_DOWN)) { // Acepta S o flecha abajo para retroceder.
            acelerador -= 1; // Primero reduce la velocidad positiva y después entra en reversa.
        }

        velocidad += acelerador * ACELERACION * deltaTime; // Integra la aceleración de 9 unidades por segundo cuadrado.
        float resistencia = RESISTENCIA; // Define la pérdida de velocidad normal al rodar.

        if (pulsada.test(GLFW_KEY_SPACE)) { // Detecta si el usuario mantiene presionado el freno.
            resistencia = FRENO; // Aumenta la pérdida de velocidad para detenerse rápidamente.
        }

        float factorFrenado = (float) Math.exp(-resistencia * deltaTime); // Calcula la fracción de velocidad conservada.
        velocidad *= factorFrenado; // Aplica resistencia de forma proporcional al tiempo transcurrido.
        velocidad = Math.max(-VELOCIDAD_REVERSA, Math.min(VELOCIDAD_MAX, velocidad)); // Limita la reversa a -6 y el avance a 16.
        float direccion = 0; // Sin dirección presionada el volante permanece recto.

        if (pulsada.test(GLFW_KEY_A) || pulsada.test(GLFW_KEY_LEFT)) { // Comprueba el giro hacia la izquierda.
            direccion += 1; // Selecciona el sentido positivo de rotación.
        }

        if (pulsada.test(GLFW_KEY_D) || pulsada.test(GLFW_KEY_RIGHT)) { // Comprueba el giro hacia la derecha.
            direccion -= 1; // Selecciona el sentido negativo de rotación.
        }

        angulo += direccion * velocidad * VELOCIDAD_GIRO * deltaTime; // Gira según la velocidad; en reversa invierte el giro.

        // Ruedas delanteras: se acercan al ángulo pedido (±ANGULO_MAX_DIRECCION o 0 al soltar) a VELOCIDAD_DIRECCION
        // radianes por segundo, así doblan y vuelven al centro suavemente en lugar de saltar.
        float objetivoDireccion = direccion * Rueda.ANGULO_MAX_DIRECCION; // Hacia dónde deberían apuntar las ruedas.
        anguloDireccion = Rueda.acercarDireccion(anguloDireccion, objetivoDireccion, deltaTime); // Paso limitado, tope de 30°.

        // Luces: se frena con S mientras el auto todavía va hacia adelante, o con Espacio en cualquier momento.
        frenando = teclaFreno || (teclaAtras && velocidadAntes > UMBRAL_MOVIMIENTO); // Enciende las luces de freno.
        float frenteX = -(float) Math.sin(angulo); // Obtiene la componente X del frente del vehículo.
        float frenteZ = -(float) Math.cos(angulo); // Obtiene la componente Z; con ángulo cero vale -1.
        float siguienteX = x + frenteX * velocidad * deltaTime; // Propone la nueva posición X.
        float siguienteZ = z + frenteZ * velocidad * deltaTime; // Propone la nueva posición Z.

        float antesX = x; // Posición al empezar el cuadro: sirve para medir lo que avanzó de verdad.
        float antesZ = z;

        if (libre.libre(siguienteX, siguienteZ)) { // Paso completo: ningún eje choca.
            x = siguienteX; // Acepta el desplazamiento horizontal.
            z = siguienteZ; // Acepta el desplazamiento en profundidad.
        } else { // El paso completo chocaría: se intenta deslizar por un solo eje.
            float fraccionLibre = -1; // Parte del frente que va en el eje libre; -1 = ningún eje libre.
            boolean xPrimero = Math.abs(siguienteX - x) >= Math.abs(siguienteZ - z); // Primero el eje que más se mueve.
            if (xPrimero && libre.libre(siguienteX, z)) { // Solo X: la pared está en la dirección Z.
                x = siguienteX; // Conserva el avance en X y anula el de Z.
                fraccionLibre = Math.abs(frenteX);
            } else if (libre.libre(x, siguienteZ)) { // Solo Z: la pared está en la dirección X.
                z = siguienteZ; // Conserva el avance en Z y anula el de X.
                fraccionLibre = Math.abs(frenteZ);
            } else if (!xPrimero && libre.libre(siguienteX, z)) { // Z estaba bloqueado: último intento con X.
                x = siguienteX; // Conserva el avance en X.
                fraccionLibre = Math.abs(frenteX);
            }
            if (fraccionLibre < FRACCION_MINIMA_DESLIZAMIENTO) { // Esquina, o pared de frente: no hay por dónde resbalar.
                velocidad = 0; // Se detiene en su última posición válida, sin rebote.
            } else { // Desliza: el roce le quita velocidad en proporción al tiempo, no a los cuadros.
                float perdida = FRICCION_ROCE * (1 - fraccionLibre) * deltaTime; // Desaceleración · dt.
                velocidad = Math.signum(velocidad) * Math.max(0, Math.abs(velocidad) - perdida); // Frena sin cambiar de sentido.
            }
        }
        float avance = (x - antesX) * frenteX + (z - antesZ) * frenteZ; // Lo recorrido a lo largo del frente (con signo).
        anguloRueda += Rueda.giroPorDistancia(avance); // Las ruedas giran lo que avanzó el auto: menos si deslizó.
        // Luz de reversa: mientras el auto va hacia atrás, o mientras S pide reversa con el auto detenido (lo contrario de
        // frenar). Sin la segunda parte, al empujar marcha atrás contra una pared la velocidad pasa de −0.15 a 0 en
        // cuadros alternados y la luz parpadearía.
        enReversa = velocidad < -UMBRAL_MOVIMIENTO || (teclaAtras && velocidadAntes <= UMBRAL_MOVIMIENTO);

        float objetivoSombra = Sombras.objetivoFactorSombraSol(x, z, esDeDia); // 1, o FACTOR_SOMBRA_SOL bajo un edificio.
        factorSombraSol = Sombras.suavizarFactorSombraSol(factorSombraSol, objetivoSombra, deltaTime); // Sin saltos al entrar o salir.
    }

    // ==================== 4. DIBUJO DEL AUTO ====================

    /**
     * Construye el auto con cajas, cilindros y la cabina extruida; argumentos de pieza: posición XYZ, tamaño XYZ y color RGB.
     * farosEncendidos es el estado de la tecla F, leído de Iluminacion por Juego.
     */
    public void dibujar(Cubo cubo, Figuras figuras, Shader shader, Cabina cabina, boolean farosEncendidos) {
        shader.decimal("uFactorSombraSol", factorSombraSol); // Baja solo la luz direccional del sol si está bajo un edificio.
        float[] c = COLOR_CARROCERIA; // Color del auto.
        pieza(cubo, 0, 0.65f, 0, 1.65f, 0.55f, 2.6f, c[0], c[1], c[2]); // Dibuja la carrocería roja.
        cabina.dibujar(x, z, angulo, c[0], c[1], c[2]); // Cabina trapezoidal del color del auto, con vidrios (antes un bloque celeste).
        Rueda.dibujarCuatro(cubo, figuras, shader, x, z, angulo, anguloRueda, anguloDireccion); // Ruedas redondas; las delanteras doblan.

        float[] ladosFaros = {-LucesVehiculo.LADO_LUZ, LucesVehiculo.LADO_LUZ}; // Define la separación lateral de las luces.
        float[] faro = LucesVehiculo.colorFaro(farosEncendidos); // Con F: blanco cálido emisivo; sin F: gris oscuro.
        float[] trasera = LucesVehiculo.colorTrasera(farosEncendidos, frenando); // Freno > posición > apagada.
        float[] tf = LucesVehiculo.TAMANO_FARO; // Medidas del faro (iguales a las del tráfico).
        float[] tt = LucesVehiculo.TAMANO_TRASERA; // Medidas de la luz trasera.
        float y = LucesVehiculo.ALTURA_LUZ; // Altura de las luces.
        for (float ladoX : ladosFaros) { // Repite el dibujo para ambos lados.
            shader.entero("uEmision", (int) faro[3]); // Encendido: la bombilla brilla con su propio color.
            pieza(cubo, ladoX, y, LucesVehiculo.FRENTE_LUZ, tf[0], tf[1], tf[2], faro[0], faro[1], faro[2]); // Dibuja un faro delantero.
            shader.entero("uEmision", (int) trasera[3]); // Posición o freno: emisiva; apagada: recibe luz normal.
            pieza(cubo, ladoX, y, LucesVehiculo.TRASERA_LUZ, tt[0], tt[1], tt[2], trasera[0], trasera[1], trasera[2]); // Dibuja una luz trasera roja.
            float[] reversa = enReversa ? COLOR_REVERSA : COLOR_REVERSA_APAGADA; // Blanca encendida en reversa.
            shader.entero("uEmision", enReversa ? 1 : 0); // Brilla solo mientras el auto va hacia atrás (no depende de F ni de N).
            pieza(cubo, ladoX * 0.4f, y, LucesVehiculo.TRASERA_LUZ + 0.01f, 0.16f, 0.12f, 0.06f, reversa[0], reversa[1], reversa[2]); // Luz de reversa, hacia el centro.
        }
        shader.entero("uEmision", 0); // Restablece el material normal para el siguiente objeto.
        shader.decimal("uFactorSombraSol", 1); // Vuelve a sol pleno para el siguiente objeto.
    }

    /** Transforma una pieza del espacio local del auto al espacio de la ciudad. */
    private void pieza(Cubo cubo, float localX, float y, float localZ, float sx, float sy, float sz, float r, float g, float b) {
        float coseno = (float) Math.cos(angulo); // Calcula el coseno de la orientación del auto.
        float seno = (float) Math.sin(angulo); // Calcula el seno de la misma orientación.
        float mundoX = x + coseno * localX + seno * localZ; // Gira la posición local y suma la posición X del auto.
        float mundoZ = z - seno * localX + coseno * localZ; // Gira la posición local y suma la posición Z del auto.
        cubo.cajaGirada(mundoX, y, mundoZ, sx, sy, sz, r, g, b, angulo); // Dibuja la pieza con la orientación del vehículo.
    }

    // ==================== 5. CONSULTAS DEL ESTADO ====================

    /** Devuelve la posición horizontal del centro del auto. */
    public float getX() {
        return x; // Coordenada X actual.
    }

    /** Devuelve la posición en profundidad del centro del auto. */
    public float getZ() {
        return z; // Coordenada Z actual.
    }

    /** Devuelve la orientación en radianes; cero apunta hacia -Z. */
    public float getAngulo() {
        return angulo; // Ángulo actual.
    }

    /** Devuelve la velocidad en unidades por segundo; negativa en reversa. */
    public float getVelocidad() {
        return velocidad; // Velocidad actual.
    }

    /** Indica si las luces de freno están encendidas (S yendo hacia adelante, o Espacio). */
    public boolean frenando() {
        return frenando; // Estado calculado en actualizar().
    }

    /** Indica si el auto va hacia atrás (luz de reversa encendida). */
    public boolean enReversa() {
        return enReversa; // Estado calculado en actualizar().
    }

    /** Factor de luz solar directa actual: 1 con sol pleno, más bajo bajo la sombra de un edificio (ya suavizado). */
    public float getFactorSombraSol() {
        return factorSombraSol; // Estado calculado en actualizar().
    }

    /** Ángulo girado por las ruedas sobre su eje, en radianes. */
    public float getAnguloRueda() {
        return anguloRueda; // Crece al avanzar y decrece en reversa.
    }

    /** Giro actual de las ruedas delanteras, en radianes (positivo = izquierda). */
    public float getAnguloDireccion() {
        return anguloDireccion; // Entre -ANGULO_MAX_DIRECCION y ANGULO_MAX_DIRECCION.
    }
}
