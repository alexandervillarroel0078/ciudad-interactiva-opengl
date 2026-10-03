package com.graphics.ciudad.trafico; // Agrupa el tráfico autónomo: vehículos y sus rutas.

import com.graphics.ciudad.iluminacion.Sombras; // Sombra de los edificios sobre el vehículo: objetivo y suavizado del factor solar.
import com.graphics.ciudad.mundo.Mapa; // Aporta el ancho de la calle para calcular el carril.
import com.graphics.ciudad.motor.Cubo; // Dibuja cada pieza del vehículo.
import com.graphics.ciudad.motor.Figuras; // Cilindros de las ruedas (los dibuja Rueda).
import com.graphics.ciudad.motor.Shader; // Activa la emisión de las luces traseras de noche y gira las ruedas (uRotacion).
import com.graphics.ciudad.vehiculo.Cabina; // Cabina trapezoidal compartida con el auto del jugador.
import com.graphics.ciudad.vehiculo.LucesVehiculo; // Ubicación y colores de luces, compartidos con el auto del jugador.
import com.graphics.ciudad.vehiculo.Rueda; // Ruedas redondas compartidas con el auto del jugador.
import static com.graphics.ciudad.vehiculo.LucesVehiculo.ALTURA_LUZ; // Altura de las luces.
import static com.graphics.ciudad.vehiculo.LucesVehiculo.FRENTE_LUZ; // Posición local de los faros.
import static com.graphics.ciudad.vehiculo.LucesVehiculo.LADO_LUZ; // Separación lateral de las luces.
import static com.graphics.ciudad.vehiculo.LucesVehiculo.TRASERA_LUZ; // Posición local de las luces traseras.
import java.util.function.BooleanSupplier; // Pregunta a Iluminacion si es de noche, sin copiar esa variable.

/**
 * VEHICULO: un auto autónomo que recorre una ruta cíclica de waypoints.
 * Responsable de: su estado (posición, orientación, velocidad y color), seguir la ruta girando suavemente hacia el
 * próximo waypoint, frenar en las curvas y ante un obstáculo en su carril, reiniciarse y dibujarse con piezas.
 * Se comunica con: Trafico (lo crea con una ruta ya validada y lo actualiza con dt), Cubo y Shader (dibujo).
 * LUCES: se encienden solas de noche y se apagan de día. El vehículo no guarda si es de noche: consulta a
 * Iluminacion.esNoche() a través de un BooleanSupplier, así hay una única variable día/noche en todo el juego.
 * La tecla F no las afecta: F solo controla los faros del jugador.
 * Orientación igual que Auto: ángulo en radianes, cero apunta hacia -Z y el frente es (-sen(ángulo), -cos(ángulo)).
 * CARRILES (circulación por la derecha): la ruta llega en centros de calle, pero el vehículo no circula sobre la
 * línea amarilla. Cada tramo se desplaza DESPLAZAMIENTO_CARRIL hacia la DERECHA de su dirección de avance.
 * Con esta convención de ejes, la derecha de una dirección (dx, dz) es (-dz, dx): mirando al norte (0, -1) la derecha
 * es (1, 0) = este, y mirando al oeste (-1, 0) la derecha es (0, -1) = norte. Equivale a (cos(ángulo), -sen(ángulo)).
 * FRENADO (regla del corredor): un obstáculo (el jugador u otro vehículo) solo cuenta si está ADELANTE, dentro del
 * corredor de su carril (|lateral| < MEDIO_CORREDOR) y a menos de DISTANCIA_FRENADO. Ante un obstáculo la velocidad
 * no salta: cambia como máximo DESACELERACION al frenar y ACELERACION_TRAFICO al arrancar (la reducción por curva
 * sigue siendo inmediata, para no abrir el giro). Con otro vehículo se mira además su trayectoria de los próximos
 * PREVISION segundos. Mientras la velocidad baja, las luces de freno se encienden con las mismas reglas que el jugador
 * (LucesVehiculo: el freno domina).
 * RUEDAS: las mismas que el jugador (vehiculo/Rueda). Giran sobre su eje lo que el vehículo avanzó DE VERDAD en el
 * cuadro (anguloRueda += paso / RADIO_RUEDA): si frena, giran más lento, y detenido no giran. Las delanteras doblan
 * según el giro que el vehículo hace en ese momento (ver direccionPorGiro): en recta vuelven a 0.
 */
public class Vehiculo {

    // ==================== 1. CONSTANTES DE CONDUCCIÓN (valores ajustables) ====================
    public static final float RADIO_VEHICULO = 1.65f; // Radio del círculo de colisión: el mismo tamaño que el auto del jugador.
    public static final float VELOCIDAD_GIRO = 2.2f; // Máximo giro por segundo, en radianes: limita qué tan rápido cambia de dirección.
    public static final float RADIO_WAYPOINT = 2; // Al acercarse a menos de esta distancia, pasa al siguiente waypoint.
    public static final float FRACCION_MINIMA_CURVA = 0.3f; // En una curva cerrada circula al 30 % de su velocidad de crucero.
    public static final float DESPLAZAMIENTO_CARRIL = Mapa.TAM_CELDA / 4; // 1/4 del ancho de la calle (2.5): centro del carril derecho.
    public static final float DISTANCIA_ANTICIPACION = 4; // Mira este tramo por delante sobre su carril: corrige desvíos sin zigzaguear.

    // ==================== 1a. FRENADO ANTE OBSTÁCULOS (valores ajustables) ====================
    // "Hueco" = espacio libre entre los dos círculos a lo largo del frente: distancia longitudinal menos ambos radios.
    public static final float DISTANCIA_FRENADO = 8; // Con un hueco menor que esto empieza a frenar.
    public static final float DISTANCIA_DETENCION = 1; // Con un hueco menor que esto quiere estar detenido.
    public static final float MEDIO_CORREDOR = 2.2f; // Medio ancho del corredor: medio auto + medio obstáculo + margen. El carril contrario está a 5.
    public static final float DESACELERACION = 8; // Máxima pérdida de velocidad, en unidades por segundo cuadrado.
    public static final float ACELERACION_TRAFICO = 4; // Máxima ganancia de velocidad al volver a arrancar, en u/s².
    public static final float SIN_OBSTACULO = Float.POSITIVE_INFINITY; // Hueco cuando no hay nada que lo haga frenar.
    public static final float UMBRAL_LUZ_FRENO = 0.5f; // Desaceleración mínima (u/s²) que enciende las luces de freno.
    public static final float UMBRAL_DETENIDO = 0.05f; // Por debajo de esta velocidad (u/s) se considera detenido.
    public static final float PREVISION = 1.5f; // Segundos hacia adelante que se mira la trayectoria de OTRO vehículo.
    public static final int MUESTRAS_PREVISION = 6; // Puntos en que se divide ese intervalo (cada 0.25 s).

    // ==================== 1b. LUCES DEL VEHÍCULO (valores ajustables) ====================
    // Ubicación, tamaño y colores de faros y luces traseras: están en vehiculo/LucesVehiculo, compartidos con el auto
    // del jugador. De noche muestra luces de posición; al frenar, rojo intenso de día y de noche (el freno domina).

    // ==================== 2. ESTADO ====================
    private final float[][] ruta; // Waypoints {x, z} en centros de celdas de calle; la ruta es cíclica.
    private final float[][] carriles; // Esquinas del carril derecho: cada waypoint desplazado hacia la derecha de sus tramos.
    private final float velocidadCrucero; // Velocidad en recta, en unidades por segundo.
    private final float rojo; // Componente roja del color de la carrocería.
    private final float verde; // Componente verde del color de la carrocería.
    private final float azul; // Componente azul del color de la carrocería.
    private final BooleanSupplier esNoche; // Consulta el estado día/noche de Iluminacion (tecla N).
    float x; // Posición horizontal actual.
    float z; // Posición en profundidad actual.
    float angulo; // Orientación en radianes; cero apunta hacia -Z.
    float velocidad; // Velocidad actual en unidades por segundo.
    int siguiente; // Índice del waypoint hacia el que se dirige.
    float velocidadPermitida; // Velocidad que deja el obstáculo más cercano; cambia de a poco (frenado gradual).
    boolean frenando; // true mientras la velocidad baja (o espera detenido ante un obstáculo): luces de freno.
    float anguloRueda; // Cuánto giró cada rueda sobre su eje, en radianes (crece al avanzar).
    float anguloDireccion; // Giro de las ruedas delanteras respecto del vehículo (positivo = izquierda).
    float factorSombraSol = 1f; // 1 = sol pleno; baja hacia Sombras.FACTOR_SOMBRA_SOL bajo la sombra de un edificio (suavizado en actualizar()).

    /** Crea el vehículo con su ruta, su velocidad de crucero y su color, y lo coloca al inicio de la ruta. */
    public Vehiculo(float[][] ruta, float velocidadCrucero, float rojo, float verde, float azul, BooleanSupplier esNoche) {
        this.esNoche = esNoche; // Guarda la consulta de día/noche; las luces dependen solo de ella.
        this.ruta = ruta; // Guarda los waypoints que recorrerá en bucle.
        this.carriles = calcularCarriles(ruta, DESPLAZAMIENTO_CARRIL); // Traslada la ruta al carril derecho.
        this.velocidadCrucero = velocidadCrucero; // Guarda la velocidad en recta.
        this.rojo = rojo; // Guarda el color de la carrocería.
        this.verde = verde; // Guarda el color de la carrocería.
        this.azul = azul; // Guarda el color de la carrocería.
        reset(); // Ubica el vehículo en su primer waypoint.
    }

    /**
     * Calcula las esquinas del carril derecho. Cada tramo i va de ruta[i] a ruta[i+1]; su carril es la misma recta
     * desplazada d hacia la derecha. En cada waypoint se juntan el carril del tramo que llega y el del que sale: la
     * esquina es el punto donde se cortan esas dos rectas, W + d · (derechaEntrada + derechaSalida) / (1 + cos θ),
     * con θ el ángulo entre ambos tramos. En un giro de 90° (cos θ = 0) queda W + d · (derechaEntrada + derechaSalida):
     * en un giro a la derecha la esquina queda del lado interior sin llegar a la vereda, y en uno a la izquierda del
     * lado exterior, así el vehículo cambia de carril recién al doblar y no invade la mano contraria antes del cruce.
     */
    static float[][] calcularCarriles(float[][] ruta, float d) {
        int n = ruta.length; // Cantidad de waypoints.
        float[][] esquinas = new float[n][]; // Resultado: una esquina de carril por waypoint.
        for (int i = 0; i < n; i++) { // Recorre cada waypoint.
            float[] anterior = ruta[(i - 1 + n) % n]; // Waypoint previo: el tramo de entrada viene desde aquí.
            float[] actual = ruta[i]; // Waypoint donde se unen los dos tramos.
            float[] posterior = ruta[(i + 1) % n]; // Waypoint siguiente: el tramo de salida va hacia aquí.
            float[] entrada = direccion(anterior, actual); // Dirección unitaria del tramo que llega.
            float[] salida = direccion(actual, posterior); // Dirección unitaria del tramo que sale.
            float derechaEntradaX = -entrada[1]; // Derecha de (dx, dz) = (-dz, dx): componente X.
            float derechaEntradaZ = entrada[0]; // Componente Z de la derecha del tramo de entrada.
            float derechaSalidaX = -salida[1]; // Derecha del tramo de salida, componente X.
            float derechaSalidaZ = salida[0]; // Derecha del tramo de salida, componente Z.
            float coseno = entrada[0] * salida[0] + entrada[1] * salida[1]; // cos θ = producto escalar de las direcciones.
            float divisor = Math.max(1 + coseno, 0.1f); // Evita dividir por cero si la ruta hiciera una vuelta en U.
            float esquinaX = actual[0] + d * (derechaEntradaX + derechaSalidaX) / divisor; // Corte de los dos carriles en X.
            float esquinaZ = actual[1] + d * (derechaEntradaZ + derechaSalidaZ) / divisor; // Corte de los dos carriles en Z.
            esquinas[i] = new float[] {esquinaX, esquinaZ}; // Guarda la esquina del carril.
        }
        return esquinas; // Ruta trasladada al carril derecho.
    }

    /** Dirección unitaria {dx, dz} que va del punto desde al punto hasta. */
    private static float[] direccion(float[] desde, float[] hasta) {
        float dx = hasta[0] - desde[0]; // Avance en X.
        float dz = hasta[1] - desde[1]; // Avance en Z.
        float largo = (float) Math.hypot(dx, dz); // Longitud del tramo.
        if (largo == 0) { // Dos waypoints iguales no definen dirección.
            return new float[] {0, -1}; // Usa el norte para no dividir por cero.
        }
        return new float[] {dx / largo, dz / largo}; // Normaliza: largo 1.
    }

    /** Coloca el vehículo en el primer waypoint, mirando hacia el segundo; Trafico lo llama al presionar R. */
    public void reset() {
        x = carriles[0][0]; // Parte del primer waypoint en X (ya sobre su carril derecho).
        z = carriles[0][1]; // Parte del primer waypoint en Z.
        siguiente = 1 % ruta.length; // Se dirige al segundo waypoint (o al mismo si la ruta tuviera uno solo).
        angulo = anguloHacia(carriles[siguiente][0], carriles[siguiente][1]); // Arranca ya orientado hacia su destino.
        velocidad = velocidadCrucero; // Arranca a velocidad de crucero.
        velocidadPermitida = velocidadCrucero; // Sin obstáculos al empezar.
        frenando = false; // Sin luces de freno.
        anguloRueda = 0; // Ruedas en su posición inicial.
        anguloDireccion = 0; // Delanteras derechas.
        factorSombraSol = 1f; // Vuelve a sol pleno: el primer waypoint no está bajo ningún edificio.
    }

    // ==================== 3. MOVIMIENTO POR CUADRO ====================

    /**
     * Avanza el vehículo deltaTime segundos. hueco es el espacio libre hasta el obstáculo más cercano de su corredor
     * (ver huecoHasta), o SIN_OBSTACULO. Trafico lo calcula para todos antes de mover a ninguno.
     */
    public void actualizar(float deltaTime, float hueco) {
        float[] tramo = tramoCarril(); // {inicioX, inicioZ, dirX, dirZ, largo, avance} del tramo actual del carril.
        if (tramo[4] - tramo[5] < RADIO_WAYPOINT) { // Comprueba si ya llegó: le falta menos que RADIO_WAYPOINT.
            siguiente = (siguiente + 1) % ruta.length; // Pasa al siguiente waypoint; el módulo hace la ruta cíclica.
            tramo = tramoCarril(); // Lee el nuevo tramo: aquí empieza el giro hacia el carril siguiente.
        }
        // Persecución sobre la línea del carril: el objetivo es un punto del carril DISTANCIA_ANTICIPACION más adelante
        // de donde está el vehículo proyectado. Si se desvió, apunta de vuelta al carril; si no, sigue derecho.
        float avanceObjetivo = Math.min(tramo[5] + DISTANCIA_ANTICIPACION, tramo[4]); // Nunca más allá de la esquina.
        float objetivoX = tramo[0] + tramo[2] * avanceObjetivo; // Lee el X del punto objetivo sobre el carril.
        float objetivoZ = tramo[1] + tramo[3] * avanceObjetivo; // Lee el Z del punto objetivo sobre el carril.

        float deseado = anguloHacia(objetivoX, objetivoZ); // Orientación que apuntaría directo al punto objetivo.
        float diferencia = normalizar(deseado - angulo); // Cuánto falta girar, entre -π y π (por el lado más corto).
        float giroMaximo = VELOCIDAD_GIRO * deltaTime; // Lo máximo que puede girar en este cuadro.
        float giro = Math.max(-giroMaximo, Math.min(giroMaximo, diferencia)); // Giro de este cuadro: nunca más que giroMaximo.
        angulo += giro; // Gira suavemente.

        // En recta (diferencia ≈ 0) el coseno vale 1 y circula a velocidad de crucero; en una curva cerrada frena.
        float factorCurva = Math.max(FRACCION_MINIMA_CURVA, (float) Math.cos(diferencia)); // Nunca baja del mínimo.
        float objetivo = velocidadCrucero * factorPorHueco(hueco); // Velocidad que permite el obstáculo.

        // Frenado gradual ante obstáculos: velocidadPermitida se acerca al objetivo sin cambiar más que DESACELERACION
        // (al frenar) o ACELERACION_TRAFICO (al arrancar) por segundo. Así no frena en seco ni sale disparado.
        if (objetivo < velocidadPermitida) { // Tiene que perder velocidad.
            velocidadPermitida = Math.max(objetivo, velocidadPermitida - DESACELERACION * deltaTime); // Baja lo permitido.
        } else { // Puede ganar velocidad.
            velocidadPermitida = Math.min(objetivo, velocidadPermitida + ACELERACION_TRAFICO * deltaTime); // Sube lo permitido.
        }
        // La curva, en cambio, sigue limitando al instante (como antes): si entrara rápido abriría el giro y pisaría
        // el carril contrario o la vereda. La velocidad final es la menor de las dos.
        float velocidadAntes = velocidad; // Para saber si está frenando.
        velocidad = Math.min(velocidadPermitida, velocidadCrucero * factorCurva); // Obstáculo gradual, curva inmediata.

        // Nunca atraviesa al obstáculo: si el paso de este cuadro fuera más largo que el hueco (alguien se le cruzó
        // demasiado cerca para frenar a tiempo), avanza solo hasta tocarlo y se detiene, como el jugador contra una pared.
        float paso = velocidad * deltaTime; // Distancia que recorrería en este cuadro.
        if (paso > hueco) { // El obstáculo está más cerca que el paso.
            paso = Math.max(0, hueco); // Avanza hasta el contacto (o nada, si ya lo toca).
            velocidad = 0; // El contacto lo detiene.
            velocidadPermitida = 0; // Y al volver a arrancar lo hace desde cero, de a poco.
        }

        // Luces de freno: encendidas mientras la velocidad baja con cierta intensidad, o mientras espera detenido
        // ante un obstáculo (con el pie en el freno). En pausa (deltaTime = 0) conservan su estado.
        if (deltaTime > 0) { // Con el tiempo detenido no hay cambio de velocidad que medir.
            boolean bajando = velocidadAntes - velocidad > UMBRAL_LUZ_FRENO * deltaTime; // Desacelera de verdad.
            boolean esperando = velocidad < UMBRAL_DETENIDO && hueco < DISTANCIA_FRENADO; // Detenido por un obstáculo.
            frenando = bajando || esperando; // Cualquiera de las dos enciende el freno.
        }

        float frenteX = -(float) Math.sin(angulo); // Obtiene la componente X del frente del vehículo.
        float frenteZ = -(float) Math.cos(angulo); // Obtiene la componente Z; con ángulo cero vale -1.
        x += frenteX * paso; // Avanza en X lo permitido en este cuadro.
        z += frenteZ * paso; // Avanza en Z lo permitido en este cuadro.

        // Ruedas: giran lo que avanzó de verdad (paso, ya recortado por el obstáculo; 0 si está detenido) y las
        // delanteras se acercan, sin saltos, al ángulo que corresponde al giro de este cuadro. En pausa no cambian.
        anguloRueda += Rueda.giroPorDistancia(paso); // Ángulo = distancia / radio.
        if (deltaTime > 0) { // Con el tiempo detenido no hay giro que medir.
            float objetivoDireccion = direccionPorGiro(giro / deltaTime, velocidad); // Según el giro actual.
            anguloDireccion = Rueda.acercarDireccion(anguloDireccion, objetivoDireccion, deltaTime); // Tope ±30°.
        }

        float objetivoSombra = Sombras.objetivoFactorSombraSol(x, z, !esNoche.getAsBoolean()); // 1, o FACTOR_SOMBRA_SOL bajo un edificio.
        factorSombraSol = Sombras.suavizarFactorSombraSol(factorSombraSol, objetivoSombra, deltaTime); // Sin saltos al entrar o salir.
    }

    /**
     * Ángulo de las ruedas delanteras que produce un giro de "velocidadAngular" rad/s yendo a "velocidad" u/s, según el
     * MODELO DE BICICLETA: un auto con distancia entre ejes L y ruedas delanteras dobladas δ recorre un círculo de radio
     * R = L / tan δ; como velocidad = velocidadAngular · R, despejando queda tan δ = L · velocidadAngular / velocidad.
     * atan2 evita dividir por cero: detenido y girando da ±90°, que el tope deja en ±ANGULO_MAX_DIRECCION. En recta
     * (velocidadAngular = 0) da 0, y las ruedas vuelven al centro. Positivo = izquierda, igual que el ángulo del vehículo.
     */
    static float direccionPorGiro(float velocidadAngular, float velocidad) {
        float delta = (float) Math.atan2(Rueda.DISTANCIA_EJES * velocidadAngular, Math.abs(velocidad)); // tan δ = L · ω / v.
        return Math.max(-Rueda.ANGULO_MAX_DIRECCION, Math.min(Rueda.ANGULO_MAX_DIRECCION, delta)); // Nunca más de 30°.
    }

    /**
     * Fracción de la velocidad de crucero que permite el hueco: 1 con el camino libre (hueco ≥ DISTANCIA_FRENADO),
     * 0 con el obstáculo a DISTANCIA_DETENCION o menos, y en el medio crece en línea recta. Como la velocidad deseada
     * baja a medida que se acerca, el vehículo va frenando cada vez más suave y queda detenido a DISTANCIA_DETENCION.
     */
    static float factorPorHueco(float hueco) {
        float fraccion = (hueco - DISTANCIA_DETENCION) / (DISTANCIA_FRENADO - DISTANCIA_DETENCION); // 0 a 1 en la zona de frenado.
        return Math.max(0, Math.min(1, fraccion)); // Recorta: nunca negativa ni mayor que 1 (infinito da 1).
    }

    /** Tramo actual del carril: {inicioX, inicioZ, dirX, dirZ, largo, avance}; avance es la proyección del vehículo. */
    private float[] tramoCarril() {
        float[] inicio = carriles[(siguiente - 1 + ruta.length) % ruta.length]; // Esquina de carril de la que viene.
        float[] fin = carriles[siguiente]; // Esquina de carril hacia la que va.
        float[] dir = direccion(inicio, fin); // Dirección unitaria del tramo.
        float largo = (float) Math.hypot(fin[0] - inicio[0], fin[1] - inicio[1]); // Longitud del tramo del carril.
        float avance = (x - inicio[0]) * dir[0] + (z - inicio[1]) * dir[1]; // Producto escalar: cuánto avanzó sobre el tramo.
        return new float[] {inicio[0], inicio[1], dir[0], dir[1], largo, avance}; // Todo lo que necesita actualizar().
    }

    /** Devuelve el tramo actual por el CENTRO de la calle, {desdeX, desdeZ, hastaX, hastaZ}; lo usa TraficoTest. */
    float[] tramoCentral() {
        float[] desde = ruta[(siguiente - 1 + ruta.length) % ruta.length]; // Waypoint (centro de calle) de partida.
        float[] hasta = ruta[siguiente]; // Waypoint (centro de calle) de llegada.
        return new float[] {desde[0], desde[1], hasta[0], hasta[1]}; // Extremos de la línea central del tramo.
    }

    /**
     * Hueco hasta un obstáculo circular de centro (px, pz) y radio radioObstaculo, o SIN_OBSTACULO si no lo afecta.
     * Regla del corredor: el vector hacia el obstáculo se descompone en coordenadas del vehículo,
     *   longitudinal = hacia · frente  (cuánto está por delante) y  lateral = hacia · derecha  (cuánto está al costado),
     * con frente = (-sen a, -cos a) y derecha = (cos a, -sen a). Cuenta solo si longitudinal > 0 (ADELANTE),
     * |lateral| < MEDIO_CORREDOR (en su MISMO carril; el carril contrario está a 2 · DESPLAZAMIENTO_CARRIL = 5) y el
     * hueco (longitudinal − ambos radios) es menor que DISTANCIA_FRENADO. Puede ser negativo si ya se tocan.
     */
    /**
     * Hueco hasta otro vehículo contando dónde está AHORA y dónde estará en los próximos PREVISION segundos si sigue
     * derecho a su velocidad actual. Es la misma regla del corredor aplicada a varios puntos de su camino.
     * Por qué: en un cruce los dos se acercan en perpendicular; ninguno entra en el corredor del otro hasta que ya es
     * tarde para frenar de a poco. Mirando la trayectoria, el que cede ve venir al otro con tiempo.
     */
    public float huecoHastaTrayectoria(Vehiculo otro) {
        float frenteX = -(float) Math.sin(otro.angulo); // Hacia dónde avanza el otro.
        float frenteZ = -(float) Math.cos(otro.angulo);
        float hueco = SIN_OBSTACULO; // Nada a la vista, por ahora.
        for (int k = 0; k <= MUESTRAS_PREVISION; k++) { // k = 0 es la posición actual.
            float t = PREVISION * k / MUESTRAS_PREVISION; // Segundos hacia el futuro.
            float px = otro.x + frenteX * otro.velocidad * t; // Dónde estará el otro, en X.
            float pz = otro.z + frenteZ * otro.velocidad * t; // Y en Z.
            hueco = Math.min(hueco, huecoHasta(px, pz, RADIO_VEHICULO)); // El punto más comprometido.
        }
        return hueco;
    }

    /** Distancia al costado de (px, pz), con signo (positiva = a la derecha): hacia · derecha. */
    public float lateralHasta(float px, float pz) {
        float angulo = this.angulo; // Orientación del vehículo.
        return (px - x) * (float) Math.cos(angulo) - (pz - z) * (float) Math.sin(angulo); // derecha = (cos a, -sen a).
    }

    public float huecoHasta(float px, float pz, float radioObstaculo) {
        float haciaX = px - x; // Vector del vehículo al obstáculo, en X.
        float haciaZ = pz - z; // Vector del vehículo al obstáculo, en Z.
        float seno = (float) Math.sin(angulo); // Orientación del vehículo.
        float coseno = (float) Math.cos(angulo);
        float longitudinal = haciaX * -seno + haciaZ * -coseno; // Proyección sobre el frente.
        float lateral = lateralHasta(px, pz); // Proyección sobre la derecha.
        if (longitudinal <= 0 || Math.abs(lateral) >= MEDIO_CORREDOR) { // Detrás, o en otro carril / al costado.
            return SIN_OBSTACULO; // No lo hace frenar.
        }
        float hueco = longitudinal - RADIO_VEHICULO - radioObstaculo; // Espacio libre entre los dos círculos.
        return hueco < DISTANCIA_FRENADO ? hueco : SIN_OBSTACULO; // Lejos: todavía no importa.
    }

    /** Calcula el ángulo que apunta desde la posición actual hacia (destinoX, destinoZ). */
    private float anguloHacia(float destinoX, float destinoZ) {
        // El frente es (-sen, -cos): despejando, el ángulo que mira hacia (dx, dz) es atan2(-dx, -dz).
        return (float) Math.atan2(-(destinoX - x), -(destinoZ - z)); // Devuelve radianes entre -π y π.
    }

    /** Lleva un ángulo al intervalo [-π, π] para girar siempre por el lado más corto. */
    private static float normalizar(float angulo) {
        while (angulo > Math.PI) { // Si se pasó de media vuelta positiva...
            angulo -= (float) (2 * Math.PI); // ...resta una vuelta completa.
        }
        while (angulo < -Math.PI) { // Si se pasó de media vuelta negativa...
            angulo += (float) (2 * Math.PI); // ...suma una vuelta completa.
        }
        return angulo; // Ángulo equivalente dentro del intervalo.
    }

    // ==================== 4. DIBUJO ====================

    /** Indica si las luces del vehículo están encendidas: sí de noche y no de día (independiente de la tecla F). */
    public boolean lucesEncendidas() {
        return esNoche.getAsBoolean(); // Lee el estado actual de Iluminacion en cada consulta.
    }

    /** Factor de luz solar directa actual: 1 con sol pleno, más bajo bajo la sombra de un edificio (ya suavizado). */
    public float getFactorSombraSol() {
        return factorSombraSol; // Estado calculado en actualizar().
    }

    /** Posición en el mundo {x, y, z} del faro izquierdo (lado 0) o derecho (lado 1): origen de su foco. */
    public float[] posicionFaro(int lado) {
        float localX = lado == 0 ? -LADO_LUZ : LADO_LUZ; // Izquierda o derecha en coordenadas locales.
        float coseno = (float) Math.cos(angulo); // Misma transformación que pieza(): gira con el vehículo.
        float seno = (float) Math.sin(angulo); // Seno de la orientación.
        float mundoX = x + coseno * localX + seno * FRENTE_LUZ; // Posición X del faro en la ciudad.
        float mundoZ = z - seno * localX + coseno * FRENTE_LUZ; // Posición Z del faro en la ciudad.
        return new float[] {mundoX, ALTURA_LUZ, mundoZ}; // Punto desde donde nace el haz.
    }

    /** Construye el vehículo en el mismo estilo que Auto (carrocería, cabina, ruedas redondas y luces), con su color. */
    public void dibujar(Cubo cubo, Figuras figuras, Shader shader, Cabina cabina) {
        shader.decimal("uFactorSombraSol", factorSombraSol); // Baja solo la luz direccional del sol si está bajo un edificio.
        pieza(cubo, 0, 0.65f, 0, 1.65f, 0.55f, 2.6f, rojo, verde, azul); // Dibuja la carrocería con el color del vehículo.
        cabina.dibujar(x, z, angulo, rojo, verde, azul); // La misma cabina que el jugador, con el color de este vehículo.
        Rueda.dibujarCuatro(cubo, figuras, shader, x, z, angulo, anguloRueda, anguloDireccion); // Las mismas ruedas que el jugador.
        float[] ladosLuces = {-LADO_LUZ, LADO_LUZ}; // Define la separación lateral de las luces.
        boolean encendidas = lucesEncendidas(); // Se consulta una vez por dibujo: de noche encendidas, de día apagadas.
        float[] faro = LucesVehiculo.colorFaro(encendidas); // Blanco emisivo encendido o gris oscuro apagado.
        float[] trasera = LucesVehiculo.colorTrasera(encendidas, frenando); // Freno > posición de noche > apagada.
        float[] tf = LucesVehiculo.TAMANO_FARO; // Medidas del faro.
        float[] tt = LucesVehiculo.TAMANO_TRASERA; // Medidas de la luz trasera.
        for (float ladoX : ladosLuces) { // Repite el dibujo para ambos lados.
            shader.entero("uEmision", (int) faro[3]); // Emisivo solo si está encendido: brilla con su propio color.
            pieza(cubo, ladoX, ALTURA_LUZ, FRENTE_LUZ, tf[0], tf[1], tf[2], faro[0], faro[1], faro[2]); // Dibuja un faro delantero.
            shader.entero("uEmision", (int) trasera[3]); // Evita que la iluminación oscurezca la luz de posición.
            pieza(cubo, ladoX, ALTURA_LUZ, TRASERA_LUZ, tt[0], tt[1], tt[2], trasera[0], trasera[1], trasera[2]); // Dibuja una luz trasera roja.
        }
        shader.entero("uEmision", 0); // Restablece el material normal para el siguiente objeto.
        shader.decimal("uFactorSombraSol", 1); // Vuelve a sol pleno para el siguiente objeto.
    }

    /** Transforma una pieza del espacio local del vehículo al espacio de la ciudad (igual que Auto.pieza). */
    private void pieza(Cubo cubo, float localX, float y, float localZ, float sx, float sy, float sz, float r, float g, float b) {
        float coseno = (float) Math.cos(angulo); // Calcula el coseno de la orientación.
        float seno = (float) Math.sin(angulo); // Calcula el seno de la misma orientación.
        float mundoX = x + coseno * localX + seno * localZ; // Gira la posición local y suma la posición X del vehículo.
        float mundoZ = z - seno * localX + coseno * localZ; // Gira la posición local y suma la posición Z del vehículo.
        cubo.cajaGirada(mundoX, y, mundoZ, sx, sy, sz, r, g, b, angulo); // Dibuja la pieza con la orientación del vehículo.
    }

    // ==================== 5. CONSULTAS DEL ESTADO ====================

    /** Devuelve la posición horizontal del centro del vehículo. */
    public float getX() {
        return x; // Coordenada X actual.
    }

    /** Devuelve la posición en profundidad del centro del vehículo. */
    public float getZ() {
        return z; // Coordenada Z actual.
    }

    /** Devuelve la orientación en radianes. */
    public float getAngulo() {
        return angulo; // Ángulo actual.
    }

    /** Devuelve la velocidad actual en unidades por segundo. */
    public float getVelocidad() {
        return velocidad; // Velocidad actual.
    }

    /** Ángulo girado por las ruedas sobre su eje, en radianes. */
    public float getAnguloRueda() {
        return anguloRueda; // Crece con la distancia recorrida.
    }

    /** Giro actual de las ruedas delanteras, en radianes (positivo = izquierda). */
    public float getAnguloDireccion() {
        return anguloDireccion; // Entre -ANGULO_MAX_DIRECCION y ANGULO_MAX_DIRECCION.
    }

    /** Indica si las luces de freno están encendidas: la velocidad baja o espera detenido ante un obstáculo. */
    public boolean frenando() {
        return frenando; // Estado calculado en actualizar().
    }
}
