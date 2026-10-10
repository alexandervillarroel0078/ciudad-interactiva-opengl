package com.graphics.ciudad.iluminacion; // Agrupa el estado de las luces de la escena.

import com.graphics.ciudad.motor.Figuras; // Cilindro, esfera y cono (los mismos de los árboles) para las farolas.
import com.graphics.ciudad.motor.Malla; // Figura con la que se dibuja cada pieza de la farola.
import com.graphics.ciudad.motor.Shader; // Recibe los uniforms de iluminación.
import com.graphics.ciudad.mundo.Decoracion; // Pasos peatonales: las farolas no van sobre ellos.
import com.graphics.ciudad.mundo.Fachada; // Puertas y portones (ejes de acceso) y el eje u de cada cara.
import com.graphics.ciudad.mundo.Mapa; // Convierte celdas de manzana en coordenadas y da la dirección de cada lado.
import com.graphics.ciudad.mundo.Parque; // Luminarias globo de los parques (luces puntuales).
import com.graphics.ciudad.mundo.Senalizacion; // Semáforos, PARE y carteles: las farolas se alejan de ellos.
import com.graphics.ciudad.vehiculo.Auto; // Aporta la posición y el frente para los faros.
import java.util.ArrayList; // Lista de piezas de cada farola.
import java.util.List; // Tipo de esa lista.
import static org.lwjgl.glfw.GLFW.*; // Incluye las constantes de las teclas N y F.

/**
 * ILUMINACION: sol, farolas, luminarias de parque y focos del vehículo.
 * Responsable de: el estado día/noche (N) y faros (F), las ubicaciones LUCES de las farolas (13 con FAROLAS_POR_SECTOR), el envío de
 * esos datos al shader iluminacion.frag, el modelo y el dibujo de las farolas y el texto de estado para el título.
 * Se comunica con: Shader (uniforms uFactorNoche, uFaros, uEmision, uLuces, uGlobos, uAuto, uFrente, uRotacion), Figuras (farolas), Parque
 * (posición de las luminarias globo, que son luces PUNTUALES: van en uGlobos, no en uLuces), Auto
 * (posición y frente de los faros) y Juego (teclas, título y orden de dibujo). Decoracion usa esNoche().
 * El shader calcula iluminación local, sin sombras reales; Sombras agrega manchas oscuras (sombras falsas) bajo los objetos.
 *
 * FAROLAS EN LA VEREDA DE LOS EDIFICIOS: el poste se planta sobre la acera de una manzana con EDIFICIO, en la franja de
 * mobiliario junto al cordón del lado que da a la calle, y un brazo curvo lleva la bombilla BRAZO_FAROLA unidades hacia
 * la calle, como en las calles reales: la luz cae sobre la calzada y la vereda, pero nada de la farola se apoya en la
 * calzada ni participa en colisiones. Nunca van del lado de un parque: su acera mide solo 0.5 (el poste la ocupa
 * entera) y el parque ya se ilumina con sus propias luminarias globo (Parque.LUMINARIAS, en uGlobos).
 *
 * MODELO DE LA FAROLA (sección 4), de abajo hacia arriba, solo con las figuras de los árboles:
 *   base (cilindro corto y ancho) → poste (dos cilindros, el de arriba más delgado) → brazo curvo (tres cilindros
 *   inclinados con uRotacion, unidos por esferas chicas en los codos) → pantalla (cono oscuro) → bombilla (esfera).
 * La bombilla se dibuja EXACTAMENTE en BOMBILLAS[i], el mismo punto que recibe el shader como luz: si se mueve la
 * farola cambian las dos cosas juntas. De día la bombilla es gris claro; de noche es emisiva (blanco cálido).
 */
public class Iluminacion {

    // ==================== 1. ESTADO Y POSICIONES DE LAS LUCES ====================
    private final Shader shader; // Programa que recibe los datos de iluminación.
    private final Figuras figuras; // Cilindro, esfera y cono con los que se dibujan las farolas.
    public static final boolean NOCHE_AL_INICIAR = false; // La demo arranca de día (se ve todo); N cambia a noche.
    private boolean noche = NOCHE_AL_INICIAR; // Inicia la escena de día; la tecla N alterna la iluminación nocturna.
    private boolean faros = true; // Inicia los focos del auto encendidos.
    public static final float DURACION_TRANSICION = 2.0f; // Segundos que tarda el cielo en pasar de día a noche (y al revés).
    public static final float ELEVACION_SOL_NOCHE = (float) Math.toRadians(-12); // Hasta dónde baja el sol visual: bajo el horizonte.
    // Niebla exponencial: visibilidad = exp(-distancia · densidad). Teclas + y - cambian la densidad.
    public static final float DENSIDAD_NIEBLA_INICIAL = 0.004f; // A 100 de distancia se ve el 67 % del color real.
    public static final float DENSIDAD_NIEBLA_MAX = 0.03f; // Muy espesa: a 100 de distancia queda el 5 %.
    public static final float PASO_DENSIDAD_NIEBLA = 0.001f; // Lo que suma o resta cada pulsación.
    private float densidadNiebla = DENSIDAD_NIEBLA_INICIAL; // 0 = sin niebla.
    private float factorNoche = NOCHE_AL_INICIAR ? 1 : 0; // 0 = día, 1 = noche: lo comparten el cielo, la luz de la ciudad (uFactorNoche) y las sombras.
    public static final int MAX_LUCES = 16; // Tamaño del arreglo uLuces[16] del shader: no se pueden enviar más farolas.
    public static final int MAX_GLOBOS = 16; // Tamaño de uGlobos[16]: luces PUNTUALES de las luminarias de parque (Parque.LUMINARIAS).

    // ---- Forma y ubicación de las farolas (valores ajustables) ----
    public static final int NORTE = 0; // Lado de la manzana que da a la calle del norte (índice de Mapa.VECINOS).
    public static final int SUR = 1; // Lado que da a la calle del sur.
    public static final int OESTE = 2; // Lado que da a la calle del oeste.
    public static final int ESTE = 3; // Lado que da a la calle del este.
    // Distancia del cordón al poste, hacia adentro de la acera: la base (0.42) ocupa de 0.19 a 0.61 del cordón, dentro de
    // la franja de mobiliario de la vereda del edificio (del cordón hasta 0.8, donde empieza el toldo; la vereda mide 1.5).
    public static final float MARGEN_POSTE = 0.4f;
    public static final float BRAZO_FAROLA = 1.5f; // Largo del brazo horizontal: la bombilla sobresale 1.1 sobre el borde de la calzada.
    public static final float ALTURA_BOMBILLA = 4.5f; // Altura de la bombilla sobre el suelo; el brazo pasa justo por encima.
    public static final float ALTURA_ACERA = 0.3f; // El poste nace sobre la acera, que Ciudad dibuja con 0.3 de alto.

    // ---- Piezas del modelo (valores ajustables, en unidades del mundo) ----
    public static final float ANCHO_BASE = 0.42f; // Diámetro de la base: más ancha que el poste, como un zócalo.
    public static final float ALTO_BASE = 0.45f; // Alto de la base sobre la acera.
    public static final float ANCHO_POSTE_ABAJO = 0.2f; // Diámetro del tramo inferior del poste.
    public static final float ANCHO_POSTE_ARRIBA = 0.14f; // Diámetro del tramo superior: el poste se afina hacia arriba.
    public static final float GROSOR_BRAZO = 0.09f; // Diámetro de los tramos del brazo.
    public static final float ANCHO_PANTALLA = 0.8f; // Diámetro de la boca de la pantalla (base del cono).
    public static final float ALTO_PANTALLA = 0.3f; // Alto del cono de la pantalla.
    public static final float SEPARACION_PANTALLA = 0.05f; // La boca de la pantalla queda esto por encima del centro de la bombilla.
    public static final float DIAMETRO_BOMBILLA = 0.32f; // Esfera de la bombilla: asoma por debajo de la pantalla.
    /** Altura del tramo final del brazo: la punta del cono de la pantalla, que cuelga de él (4.85). */
    public static final float ALTURA_BRAZO = ALTURA_BOMBILLA + SEPARACION_PANTALLA + ALTO_PANTALLA;
    // PERFIL DEL BRAZO: puntos {avance, altura} vistos de costado. avance es la fracción de BRAZO_FAROLA recorrida desde
    // el poste hacia la calle (0 = poste, 1 = sobre la bombilla) y altura se mide desde ALTURA_BRAZO (negativa = más
    // abajo). El primer punto es la punta del poste; cada par de puntos seguidos es un tramo recto: uno empinado, uno
    // suave y uno horizontal, que juntos parecen una curva.
    public static final float[][] PERFIL_BRAZO = {{0, -0.7f}, {0.15f, -0.2f}, {0.5f, 0}, {1, 0}};
    public static final float[] COLOR_BASE = {0.18f, 0.19f, 0.21f}; // Gris oscuro.
    public static final float[] COLOR_POSTE = {0.46f, 0.49f, 0.53f}; // Gris metálico (poste, brazo y codos).
    public static final float[] COLOR_PANTALLA = {0.14f, 0.15f, 0.17f}; // Casi negro: tapa la bombilla por arriba.
    public static final float[] COLOR_BOMBILLA_DIA = {0.82f, 0.82f, 0.80f}; // Gris claro, apagada y sin emisión.
    public static final float[] COLOR_BOMBILLA_NOCHE = {1.0f, 0.92f, 0.72f}; // Blanco cálido, emisiva.

    // FORMATO DE LUCES: cada farola es {fila, columna, lado, sentido}. (fila, columna) es una celda de MANZANA CON
    // EDIFICIO y lado indica qué borde de esa manzana da a la calle donde va la farola: NORTE, SUR, OESTE o ESTE. El poste
    // se ubica MARGEN_POSTE hacia adentro de la acera, corrido DESPLAZAMIENTO_FAROLA del centro del borde a lo largo de la
    // cuadra, hacia un lado (sentido = -1) o el otro (sentido = 1), y la bombilla BRAZO_FAROLA hacia la calle.
    // Las ubicaciones no se escriben a mano: las elige calcularLuces() con los criterios de la sección 1b.
    public static final int[] FAROLAS_POR_SECTOR = {3, 3, 3, 2, 2}; // Centro, Barrio Norte, Parque Sur, Zona Oeste, Zona Este (13).
    public static final float SEPARACION_SENALES = 3; // Distancia mínima del poste a un semáforo, PARE o cartel.
    public static final float HOLGURA_POSTE_PASO = 0.3f; // Media base del poste (con margen): no toca un paso peatonal.
    public static final float HOLGURA_BOMBILLA_PASO = 0.35f; // Medio ancho de la pantalla (con margen): no cuelga sobre un paso.
    // FAROLAS FRENTE A LAS PUERTAS: la puerta de todo edificio está en el centro de la fachada (Fachada.ejesDeAcceso) y
    // la cuadra mide lo mismo que la manzana, así que una farola "a mitad de cuadra" quedaba justo delante de la puerta:
    // quien sale se lleva el poste por delante. Por eso el poste se corre DESPLAZAMIENTO_FAROLA a lo largo de la cuadra
    // y debe quedar al menos a DISTANCIA_MIN_PUERTA del eje de cada acceso de esa cara (la puerta de cualquier uso y el
    // portón de garaje de las casas). Con 2.0, la base (0.42) deja casi 1 libre hasta el marco de la puerta más ancha
    // (la doble del lobby, 1.6), y el poste sigue a 3 de la esquina, dentro de la franja de mobiliario.
    public static final float DESPLAZAMIENTO_FAROLA = 2.0f; // Del centro del borde al poste, a lo largo de la cuadra.
    public static final float DISTANCIA_MIN_PUERTA = 1.5f; // Mínimo entre el poste y el eje de una puerta o portón.
    public static final int[] SENTIDOS = {-1, 1}; // Hacia qué lado del centro se corre el poste (se prueban los dos).
    public static final int[][] LUCES = calcularLuces();
    /** Posición {x, z} de cada poste, calculada desde LUCES. */
    public static final float[][] POSTES = calcularPosiciones(MARGEN_POSTE);
    /** Posición {x, y, z} de cada bombilla (la que recibe el shader), calculada desde LUCES. */
    public static final float[][] BOMBILLAS = calcularBombillas();

    /**
     * Calcula {x, z} a una distancia "haciaAdentro" del cordón, medida desde el borde de la manzana que da a la calle:
     * positiva = sobre la acera; negativa = sobre la calle. El lado da la dirección hacia la calle (Mapa.VECINOS).
     */
    private static float[][] calcularPosiciones(float haciaAdentro) {
        float[][] posiciones = new float[LUCES.length][]; // Una posición por farola.
        for (int i = 0; i < LUCES.length; i++) { // Recorre las farolas.
            posiciones[i] = posicion(LUCES[i], haciaAdentro); // Punto sobre el borde, corrido a lo largo de la cuadra.
        }
        return posiciones; // Posiciones calculadas.
    }

    /** Posición u del poste a lo largo de la cara (mismo eje que Fachada.puntoEnCara): sentido · DESPLAZAMIENTO_FAROLA. */
    static float desplazamiento(int[] farola) {
        return farola[3] * DESPLAZAMIENTO_FAROLA;
    }

    /**
     * {x, z} sobre el borde "lado" de la manzana, a "haciaAdentro" del cordón (negativo = sobre la calle) y corrido
     * desplazamiento() a lo largo de la cuadra. Usa Fachada.puntoEnCara(), la misma cuenta con la que se ubican puertas
     * y portones: así la distancia a una puerta se mide en el mismo eje u, sin conversiones.
     */
    private static float[] posicion(int[] farola, float haciaAdentro) {
        float afuera = Mapa.TAM_CELDA / 2 - haciaAdentro; // Del centro de la manzana hacia la calle.
        return Fachada.puntoEnCara(Mapa.centro(farola[1]), Mapa.centro(farola[0]), farola[2], desplazamiento(farola), afuera);
    }

    // ==================== 1b. REGLAS PARA UBICAR LAS FAROLAS ====================
    // Todo se calcula desde Mapa y Senalizacion: con otro tamaño de MAPA las farolas se reubican con los mismos criterios.

    /**
     * FAROLA VÁLIDA: el borde "lado" de la manzana (fila, columna) sirve para una farola del sector si:
     *  - EN LA VEREDA DE UN EDIFICIO: la celda es una manzana con edificio y por ese lado hay una calle; el poste
     *    queda en la franja de mobiliario y solo la bombilla sobresale sobre la calzada (nunca el poste en la calle).
     *    Los parques quedan afuera: se iluminan con sus globos, y su acera de 0.5 es demasiado angosta. Una farola en
     *    la vereda de ENFRENTE de un parque sí vale: está sobre la acera de un edificio;
     *  - CERCA DE MITAD DE CUADRA, PERO NO FRENTE A UNA PUERTA: el poste se corre DESPLAZAMIENTO_FAROLA del centro del
     *    borde (hacia "sentido") y queda a DISTANCIA_MIN_PUERTA o más del eje de cada acceso de esa cara: la puerta
     *    centrada de cualquier uso de planta baja y, en las casas, el portón de garaje. Sigue lejos de las esquinas;
     *  - EN SU SECTOR: el poste cae dentro del sector que se está iluminando;
     *  - LEJOS DE LAS SEÑALES: a SEPARACION_SENALES o más de cada semáforo, PARE y cartel (no se tapan entre sí);
     *  - FUERA DE LOS PASOS PEATONALES: ni el poste ni la bombilla quedan sobre un paso. Los pasos ocupan de 1.5 a 4.5
     *    del centro de la cuadra, del lado de su cruce: con el poste corrido, la bombilla colgaría sobre el paso si lo
     *    hay de ese lado, así que en esas cuadras vale solo el sentido contrario.
     * Los basureros no se miran acá: Basureros se calcula después y esquiva los postes (Parque.cercaDeUnPoste()).
     */
    static boolean farolaValida(int fila, int columna, int lado, int sentido, int sector) {
        int[] haciaCalle = Mapa.VECINOS[lado]; // Dirección de la calle.
        if (Mapa.tipo(fila, columna) != Mapa.EDIFICIO || !Mapa.esCalleSegura(fila + haciaCalle[0], columna + haciaCalle[1])) {
            return false; // No es la vereda de un edificio (es calle o parque), o ese lado no da a una calle.
        }
        int[] farola = {fila, columna, lado, sentido};
        for (float eje : Fachada.ejesDeAcceso(fila, columna, lado)) { // Puerta centrada y, si hay, portón de garaje.
            if (Math.abs(desplazamiento(farola) - eje) < DISTANCIA_MIN_PUERTA) {
                return false; // El poste quedaría delante de una entrada.
            }
        }
        float[] poste = posicion(farola, MARGEN_POSTE); // Sobre la acera.
        float[] bombilla = posicion(farola, MARGEN_POSTE - BRAZO_FAROLA); // Punta del brazo, sobre la calzada.
        if (Mapa.sector(poste[0], poste[1]) != sector) {
            return false; // Pertenece a otro sector.
        }
        List<float[]> senales = new ArrayList<>(Senalizacion.SEMAFOROS); // {x, z, ...}.
        senales.addAll(Senalizacion.PARES); // {x, z, ángulo}.
        for (float[] c : Senalizacion.CARTELES_SECTOR) { // {sector, x, z, ángulo}: se pasa a {x, z}.
            senales.add(new float[] {c[1], c[2]});
        }
        for (float[] s : senales) {
            if (Math.hypot(poste[0] - s[0], poste[1] - s[1]) < SEPARACION_SENALES) {
                return false; // Demasiado cerca de una señal.
            }
        }
        return !Decoracion.hayPasoSobre(poste[0], poste[1], HOLGURA_POSTE_PASO, HOLGURA_POSTE_PASO)
            && !Decoracion.hayPasoSobre(bombilla[0], bombilla[1], HOLGURA_BOMBILLA_PASO, HOLGURA_BOMBILLA_PASO);
    }

    /**
     * REPARTO: para cada sector, FAROLAS_POR_SECTOR[sector] farolas elegidas entre las válidas para que queden lo MÁS
     * SEPARADAS POSIBLE de todas las ya ubicadas (en su sector y en los anteriores): cada vez se toma la candidata cuya
     * farola más cercana está más lejos ("punto más lejano"). La primera de todas es la más alejada del origen. Así la
     * luz se reparte por toda la ciudad, sin farolas amontonadas. En empates gana la primera en el orden fila, columna,
     * lado, sentido (siempre el mismo resultado). Cada borde aporta hasta dos candidatas, una por sentido (a 4 entre
     * sí); como se busca la más alejada de las ya elegidas, dos farolas en el mismo borde serían muy poco probables.
     */
    private static int[][] calcularLuces() {
        List<int[]> elegidas = new ArrayList<>(); // {fila, columna, lado, sentido}.
        List<float[]> postes = new ArrayList<>(); // Postes de las elegidas.
        for (int sector = 0; sector < FAROLAS_POR_SECTOR.length && sector < Mapa.SECTORES.length; sector++) {
            List<int[]> candidatas = new ArrayList<>(); // Bordes (y sentidos) válidos del sector.
            for (int fila = 0; fila < Mapa.MAPA.length; fila++) {
                for (int columna = 0; columna < Mapa.MAPA[fila].length; columna++) {
                    for (int lado = 0; lado < Mapa.VECINOS.length; lado++) { // NORTE, SUR, OESTE, ESTE.
                        for (int sentido : SENTIDOS) { // A un lado y al otro de la puerta.
                            if (farolaValida(fila, columna, lado, sentido, sector)) {
                                candidatas.add(new int[] {fila, columna, lado, sentido});
                            }
                        }
                    }
                }
            }
            for (int n = 0; n < FAROLAS_POR_SECTOR[sector] && !candidatas.isEmpty(); n++) { // Una farola por vuelta.
                int mejor = 0; // Índice de la candidata elegida.
                double mejorDistancia = -1; // Su distancia a la farola más cercana.
                for (int i = 0; i < candidatas.size(); i++) {
                    float[] p = posicion(candidatas.get(i), MARGEN_POSTE);
                    double distancia = postes.isEmpty() ? Math.hypot(p[0], p[1]) : Double.MAX_VALUE; // La primera: lejos del origen.
                    for (float[] q : postes) {
                        distancia = Math.min(distancia, Math.hypot(p[0] - q[0], p[1] - q[1])); // Farola más cercana.
                    }
                    if (distancia > mejorDistancia) { // Estrictamente mayor: en empates queda la primera.
                        mejor = i;
                        mejorDistancia = distancia;
                    }
                }
                int[] farola = candidatas.remove(mejor);
                elegidas.add(farola);
                postes.add(posicion(farola, MARGEN_POSTE));
            }
        }
        return elegidas.toArray(new int[0][]);
    }

    /** La bombilla está en la punta del brazo: BRAZO_FAROLA más allá del poste, hacia la calle, a ALTURA_BOMBILLA. */
    private static float[][] calcularBombillas() {
        float[][] puntas = calcularPosiciones(MARGEN_POSTE - BRAZO_FAROLA); // Poste + brazo: 1.1 sobre la calzada.
        float[][] bombillas = new float[puntas.length][]; // Agrega la altura.
        for (int i = 0; i < puntas.length; i++) { // Recorre las farolas.
            bombillas[i] = new float[] {puntas[i][0], ALTURA_BOMBILLA, puntas[i][1]}; // {x, y, z}.
        }
        return bombillas; // Posiciones de las bombillas.
    }

    /** Altura de la punta del poste, que es el primer punto del brazo (4.15). */
    public static float alturaTopePoste() {
        return ALTURA_BRAZO + PERFIL_BRAZO[0][1]; // Primer punto del perfil.
    }

    /** Recibe el shader y las figuras compartidas. */
    public Iluminacion(Shader shader, Figuras figuras) {
        this.shader = shader; // Guarda el programa que recibirá los uniforms.
        this.figuras = figuras; // Guarda la geometría de las farolas.
    }

    // ==================== 2. CONTROLES E INDICADORES ====================

    /** Añade los interruptores de iluminación a los controles de Juego. */
    public void tecla(int key) {
        if (key == GLFW_KEY_N) { // Comprueba la tecla de cambio de ambiente.
            noche = !noche; // Alterna entre noche y día.
        }
        if (key == GLFW_KEY_EQUAL || key == GLFW_KEY_KP_ADD) { // "+" (en el teclado principal es Shift y "=": la tecla es "=").
            densidadNiebla = Math.min(DENSIDAD_NIEBLA_MAX, densidadNiebla + PASO_DENSIDAD_NIEBLA);
        }
        if (key == GLFW_KEY_MINUS || key == GLFW_KEY_KP_SUBTRACT) { // "-".
            densidadNiebla = Math.max(0, densidadNiebla - PASO_DENSIDAD_NIEBLA);
        }
        if (key == GLFW_KEY_F) { // Comprueba el interruptor de los focos del auto.
            faros = !faros; // Enciende los faros si estaban apagados y viceversa.
        }
    }

    /** Acerca factorNoche a su destino (0 de día, 1 de noche) a razón de 1 / DURACION_TRANSICION por segundo. */
    public void avanzar(float deltaTime) {
        float destino = noche ? 1 : 0; // Hacia dónde va el cielo.
        float paso = deltaTime / DURACION_TRANSICION; // Cuánto puede moverse en este cuadro.
        if (factorNoche < destino) {
            factorNoche = Math.min(destino, factorNoche + paso);
        } else {
            factorNoche = Math.max(destino, factorNoche - paso);
        }
    }

    /**
     * Lo que se ve encendido o apagado de golpe (bombillas, ventanas, luces del tráfico, agua): cambia a mitad de la
     * transición, cuando la luz de la ciudad ya está más cerca de la noche que del día. esNoche() es lo que pidió la tecla N.
     */
    public boolean esNocheVisual() {
        return factorNoche >= 0.5f;
    }

    /** Densidad actual de la niebla (teclas + y -). */
    public float densidadNiebla() {
        return densidadNiebla;
    }

    /** Color de la niebla: el del horizonte del cielo en este momento (día, atardecer o noche, según la transición). */
    public float[] colorNiebla() {
        return Cielo.colorHorizonte(direccionSol()[1], factorNoche);
    }

    /** Transición del cielo: 0 = día pleno, 1 = noche plena (cielo.frag la usa para mezclar colores y encender estrellas). */
    public float factorNoche() {
        return factorNoche;
    }

    /**
     * Dirección unitaria hacia el sol que dibuja el cielo. De día es exactamente Sombras.DIRECCION_SOL_DIA (la misma
     * que usan la luz y las sombras); al anochecer baja por el mismo meridiano hasta ELEVACION_SOL_NOCHE, pasando por
     * el horizonte (ahí el cielo se vuelve atardecer). Solo es visual: la iluminación de la ciudad no la usa.
     */
    public float[] direccionSol() {
        float[] d = Sombras.DIRECCION_SOL_DIA; // Sol de día (sin normalizar).
        float largo = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        float elevacionDia = (float) Math.asin(d[1] / largo); // Altura angular del sol de día.
        float horizontal = (float) Math.hypot(d[0], d[2]); // Para conservar el rumbo.
        float elevacion = elevacionDia + (ELEVACION_SOL_NOCHE - elevacionDia) * factorNoche; // Baja linealmente con la transición.
        float cos = (float) Math.cos(elevacion);
        return new float[] {d[0] / horizontal * cos, (float) Math.sin(elevacion), d[2] / horizontal * cos};
    }

    /** Prepara el texto que Juego incorpora al título de la ventana. */
    public String estado() {
        String ambiente = "Día"; // Usa día como texto inicial.
        if (noche) { // Comprueba si está seleccionado el ambiente nocturno.
            ambiente = "Noche"; // Reemplaza el texto por el estado real.
        }
        String estadoFaros = "OFF"; // Usa apagado como texto inicial de los focos.
        if (faros) { // Comprueba si las luces del auto están activas.
            estadoFaros = "ON"; // Indica que los faros están encendidos.
        }
        return " | Luces: " + estadoFaros + " | " + ambiente; // Devuelve ambos indicadores con el formato del título.
    }

    /** Indica si los faros del auto están encendidos; lo muestra el HUD. */
    public boolean farosEncendidos() {
        return faros; // Estado elegido con la tecla F.
    }

    /** Indica si la escena está en modo nocturno; Decoracion lo usa para las ventanas. */
    public boolean esNoche() {
        return noche; // Estado elegido con la tecla N.
    }

    // ==================== 3. DATOS QUE RECIBE EL SHADER ====================

    /** Actualiza los uniforms de iluminación antes de dibujar la ciudad. */
    public void preparar(Auto auto) {
        float[] niebla = colorNiebla();
        shader.vector("uColorNiebla", niebla[0], niebla[1], niebla[2]); // Igual al horizonte de cielo.frag.
        shader.decimal("uDensidadNiebla", densidadNiebla); // 0 = sin niebla.
        shader.decimal("uFactorNoche", factorNoche); // 0 = ambiente y sol de día, 1 = de noche; el shader hace mix entre ambos (misma transición que el cielo).
        shader.entero("uFaros", 0); // Inicialmente desactiva los conos de luz del auto.
        if (faros) { // Revisa el interruptor de los faros.
            shader.entero("uFaros", 1); // Activa el cálculo de los dos focos en el shader.
        }
        shader.entero("uEmision", 0); // Hace que los objetos normales reciban iluminación.

        int cantidad = Math.min(LUCES.length, MAX_LUCES); // Nunca envía más posiciones de las que caben en uLuces.
        shader.entero("uNumLuces", cantidad); // Indica al shader cuántas farolas debe recorrer.
        for (int indice = 0; indice < cantidad; indice++) { // Recorre todas las bombillas definidas en LUCES.
            float x = BOMBILLAS[indice][0]; // Lee la coordenada horizontal de la bombilla (punta del brazo).
            float y = BOMBILLAS[indice][1]; // Lee su altura sobre el suelo.
            float z = BOMBILLAS[indice][2]; // Lee su coordenada en profundidad.
            shader.vector("uLuces[" + indice + "]", x, y, z); // Envía esa posición al arreglo GLSL.
        }
        // Luminarias globo de los parques: otro arreglo, porque son luces PUNTUALES (sin pantalla) y no focos.
        // Como las farolas, el shader solo las recorre de noche (de día quedan "apagadas").
        int globos = Math.min(Parque.LUMINARIAS.size(), MAX_GLOBOS); // Nunca más de las que caben en uGlobos.
        shader.entero("uNumGlobos", globos);
        for (int indice = 0; indice < globos; indice++) {
            float[] g = Parque.LUMINARIAS.get(indice); // Centro del globo: el mismo punto que dibuja Parque.
            shader.vector("uGlobos[" + indice + "]", g[0], g[1], g[2]);
        }

        float frenteX = -(float) Math.sin(auto.getAngulo()); // Calcula hacia dónde apunta el auto en X.
        float frenteZ = -(float) Math.cos(auto.getAngulo()); // Calcula hacia dónde apunta el auto en Z.
        shader.vector("uAuto", auto.getX(), 0.7f, auto.getZ()); // Envía el centro del vehículo a la altura de sus focos.
        shader.vector("uFrente", frenteX, 0, frenteZ); // Envía la dirección frontal del vehículo.
    }

    // ==================== 4. MODELOS DE LAS FAROLAS ====================

    /** Qué parte de la farola es una pieza; FarolasTest busca la BOMBILLA para compararla con la luz. */
    public enum Parte { BASE, POSTE, CODO, BRAZO, PANTALLA, BOMBILLA }

    /** Figura con la que se dibuja una pieza: las tres mallas redondeadas de Figuras. */
    public enum Forma { CILINDRO, ESFERA, CONO }

    /**
     * PIEZA: una figura ya ubicada en el mundo. (x, y, z) es su centro, (sx, sy, sz) su tamaño antes de rotar y
     * rotacion la matriz uRotacion (nueve números, columna por columna). El modelo se arma sin OpenGL, así una prueba
     * puede revisarlo; dibujarFarolas() solo recorre la lista y la envía a la GPU.
     */
    public static final class Pieza {
        public final Parte parte; // Base, poste, codo, brazo, pantalla o bombilla.
        public final Forma forma; // Cilindro, esfera o cono.
        public final float x, y, z; // Centro en el mundo.
        public final float sx, sy, sz; // Tamaño en cada eje; en los tramos del brazo, sy es el largo.
        public final float[] color; // RGB entre 0 y 1.
        public final float[] rotacion; // Matriz 3 × 3 por columnas para uRotacion.
        public final boolean emisiva; // true = conserva su color sin iluminación (la bombilla de noche).

        Pieza(Parte parte, Forma forma, float x, float y, float z, float sx, float sy, float sz,
              float[] color, float[] rotacion, boolean emisiva) {
            this.parte = parte; // Parte de la farola.
            this.forma = forma; // Figura a usar.
            this.x = x; // Centro X.
            this.y = y; // Centro Y.
            this.z = z; // Centro Z.
            this.sx = sx; // Ancho.
            this.sy = sy; // Alto.
            this.sz = sz; // Profundidad.
            this.color = color; // Color del material.
            this.rotacion = rotacion; // Orientación.
            this.emisiva = emisiva; // Brillo propio.
        }
    }

    /** Arma las piezas de la farola i, de abajo hacia arriba, en coordenadas del mundo. */
    public static List<Pieza> modeloFarola(int i, boolean noche) {
        List<Pieza> piezas = new ArrayList<>(); // Resultado.
        float[] vertical = Shader.IDENTIDAD_3X3; // Piezas verticales: sin rotación extra.
        float x = POSTES[i][0]; // Centro del poste en X.
        float z = POSTES[i][1]; // Centro del poste en Z.
        int[] haciaCalle = Mapa.VECINOS[LUCES[i][2]]; // {dFila, dColumna} del lado de la calle.
        float dx = haciaCalle[1]; // Dirección hacia la calle en X (las columnas son X).
        float dz = haciaCalle[0]; // Dirección hacia la calle en Z (las filas son Z).

        // Base: cilindro corto y ancho apoyado en la acera.
        piezas.add(new Pieza(Parte.BASE, Forma.CILINDRO, x, ALTURA_ACERA + ALTO_BASE / 2, z,
            ANCHO_BASE, ALTO_BASE, ANCHO_BASE, COLOR_BASE, vertical, false));
        // Poste: dos tramos de igual alto; el de arriba más delgado, así se afina hacia la punta.
        float pie = ALTURA_ACERA + ALTO_BASE; // Donde termina la base.
        float tramo = (alturaTopePoste() - pie) / 2; // Alto de cada tramo.
        piezas.add(new Pieza(Parte.POSTE, Forma.CILINDRO, x, pie + tramo / 2, z,
            ANCHO_POSTE_ABAJO, tramo, ANCHO_POSTE_ABAJO, COLOR_POSTE, vertical, false));
        piezas.add(new Pieza(Parte.POSTE, Forma.CILINDRO, x, pie + tramo * 1.5f, z,
            ANCHO_POSTE_ARRIBA, tramo, ANCHO_POSTE_ARRIBA, COLOR_POSTE, vertical, false));

        // Brazo: un cilindro entre cada par de puntos del perfil y una esfera en cada codo, que tapa la unión.
        for (int k = 0; k < PERFIL_BRAZO.length - 1; k++) { // Recorre los tramos (el último punto no abre ninguno).
            float ax = x + dx * PERFIL_BRAZO[k][0] * BRAZO_FAROLA; // Inicio del tramo, X.
            float ay = ALTURA_BRAZO + PERFIL_BRAZO[k][1]; // Inicio del tramo, altura.
            float az = z + dz * PERFIL_BRAZO[k][0] * BRAZO_FAROLA; // Inicio del tramo, Z.
            float bx = x + dx * PERFIL_BRAZO[k + 1][0] * BRAZO_FAROLA; // Fin del tramo, X.
            float by = ALTURA_BRAZO + PERFIL_BRAZO[k + 1][1]; // Fin del tramo, altura.
            float bz = z + dz * PERFIL_BRAZO[k + 1][0] * BRAZO_FAROLA; // Fin del tramo, Z.
            float codo = k == 0 ? ANCHO_POSTE_ARRIBA : GROSOR_BRAZO; // El primer codo también redondea la punta del poste.
            piezas.add(new Pieza(Parte.CODO, Forma.ESFERA, ax, ay, az, codo, codo, codo, COLOR_POSTE, vertical, false));
            float largo = (float) Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az)); // Largo del tramo.
            float[] giro = rotacionTramo(dx, dz, (bx - ax) / largo, (by - ay) / largo, (bz - az) / largo); // Acuesta el cilindro sobre el tramo.
            piezas.add(new Pieza(Parte.BRAZO, Forma.CILINDRO, (ax + bx) / 2, (ay + by) / 2, (az + bz) / 2,
                GROSOR_BRAZO, largo, GROSOR_BRAZO, COLOR_POSTE, giro, false));
        }

        // Luminaria: pantalla (cono con la boca hacia abajo) colgada del brazo y bombilla (esfera) asomando debajo.
        float[] luz = BOMBILLAS[i]; // El MISMO punto que preparar() envía como uLuces[i]: no se duplican coordenadas.
        piezas.add(new Pieza(Parte.PANTALLA, Forma.CONO, luz[0], luz[1] + SEPARACION_PANTALLA + ALTO_PANTALLA / 2, luz[2],
            ANCHO_PANTALLA, ALTO_PANTALLA, ANCHO_PANTALLA, COLOR_PANTALLA, vertical, false));
        float[] colorBombilla = noche ? COLOR_BOMBILLA_NOCHE : COLOR_BOMBILLA_DIA; // Encendida solo de noche.
        piezas.add(new Pieza(Parte.BOMBILLA, Forma.ESFERA, luz[0], luz[1], luz[2],
            DIAMETRO_BOMBILLA, DIAMETRO_BOMBILLA, DIAMETRO_BOMBILLA, colorBombilla, vertical, noche));
        return piezas; // Farola completa.
    }

    /**
     * Matriz que acuesta el cilindro (su eje es Y) sobre la dirección unitaria (ex, ey, ez) de un tramo del brazo.
     * Sus columnas dicen adónde va cada eje local: X = lateral horizontal (-dz, 0, dx), perpendicular a la calle;
     * Y = el tramo; Z = lateral × tramo (producto vectorial). Con Z calculada así es una rotación pura, sin espejar.
     */
    static float[] rotacionTramo(float dx, float dz, float ex, float ey, float ez) {
        float lx = -dz; // Eje lateral en X (su componente Y es 0).
        float lz = dx; // Eje lateral en Z.
        float nx = -lz * ey; // Producto vectorial (lx, 0, lz) × (ex, ey, ez), componente X.
        float ny = lz * ex - lx * ez; // Componente Y.
        float nz = lx * ey; // Componente Z.
        return new float[] {lx, 0, lz, ex, ey, ez, nx, ny, nz}; // Tres columnas.
    }

    /** Dibuja cada farola pieza por pieza; la bombilla está en la misma posición usada para calcular la luz. */
    public void dibujarFarolas() {
        for (int i = 0; i < LUCES.length; i++) { // Selecciona una farola a la vez.
            dibujarFarolaPiezas(i);
        }
        shader.matriz3("uRotacion", Shader.IDENTIDAD_3X3); // Restablece la rotación para el siguiente objeto.
        shader.entero("uEmision", 0); // Restablece el material normal para el siguiente objeto.
    }

    /**
     * Para la sombra planar (Sombras.dibujarPlanares): dibuja cada farola restringida a la huella de SU PROPIA
     * manzana (Sombras.manzana, la misma celda {fila, columna} de LUCES[i]), con el modo recibido (Sombras.HUELLA_DENTRO
     * en el pase de acera, Sombras.HUELLA_FUERA en el de calle). El poste de una farola se apoya en la acera, pero el
     * brazo y la bombilla sobresalen hacia la calle: sin esta restricción, la copia aplastada a nivel acera "flotaría"
     * sobre la calzada, igual que le pasaba a los edificios (ver el comentario de Sombras).
     */
    public void dibujarFarolasConHuella(int modoHuella) {
        for (int i = 0; i < LUCES.length; i++) {
            float[] r = Sombras.manzana(LUCES[i][0], LUCES[i][1]); // Manzana donde está plantado el poste de esta farola.
            Sombras.aplicarHuella(shader, modoHuella, r[0], r[1], r[2], r[3]);
            dibujarFarolaPiezas(i);
        }
        Sombras.aplicarHuella(shader, Sombras.HUELLA_SIN_FILTRO, 0, 0, 0, 0); // No deja el filtro puesto para el siguiente objeto.
        shader.matriz3("uRotacion", Shader.IDENTIDAD_3X3); // Restablece la rotación para el siguiente objeto.
        shader.entero("uEmision", 0); // Restablece el material normal para el siguiente objeto.
    }

    /** Piezas de la farola i, ya ubicadas en el mundo: lo comparten dibujarFarolas() y dibujarFarolasConHuella(). */
    private void dibujarFarolaPiezas(int i) {
        for (Pieza p : modeloFarola(i, esNocheVisual())) { // Recorre sus piezas.
            Malla malla = figuras.cilindro; // Figura de la pieza: cilindro por defecto...
            if (p.forma == Forma.ESFERA) {
                malla = figuras.esfera; // ...esfera para codos y bombilla...
            } else if (p.forma == Forma.CONO) {
                malla = figuras.cono; // ...cono para la pantalla.
            }
            shader.matriz3("uRotacion", p.rotacion); // Inclina los tramos del brazo; identidad para lo demás.
            shader.entero("uEmision", p.emisiva ? 1 : 0); // Solo la bombilla de noche ignora la iluminación.
            malla.dibujar(p.x, p.y, p.z, p.sx, p.sy, p.sz, p.color[0], p.color[1], p.color[2]); // Envía la pieza.
        }
    }
}
