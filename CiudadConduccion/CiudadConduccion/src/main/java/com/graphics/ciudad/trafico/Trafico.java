package com.graphics.ciudad.trafico; // Agrupa el tráfico autónomo: vehículos y sus rutas.

import com.graphics.ciudad.motor.Cubo; // Dibuja los vehículos.
import com.graphics.ciudad.motor.Figuras; // Cilindros de las ruedas.
import com.graphics.ciudad.motor.Shader; // Activa la emisión de las luces traseras.
import com.graphics.ciudad.mundo.Mapa; // Convierte celdas en coordenadas y comprueba que las rutas usen calles.
import com.graphics.ciudad.vehiculo.Auto; // Radio del círculo del jugador.
import com.graphics.ciudad.vehiculo.Cabina; // Cabina compartida con el auto del jugador.
import com.graphics.ciudad.vehiculo.Colisiones; // Prueba círculo contra círculo con el auto del jugador.
import java.util.ArrayList; // Lista de vehículos creados.
import java.util.Collections; // Devuelve la lista de vehículos sin permitir modificarla.
import java.util.List; // Tipo de la lista de vehículos.
import java.util.function.BooleanSupplier; // Consulta de día/noche que se entrega a cada vehículo.

/**
 * TRAFICO: conjunto de vehículos autónomos que recorren la ciudad.
 * Responsable de: definir las rutas (RUTAS_CELDAS), validarlas contra el Mapa, crear un Vehiculo por ruta,
 * actualizarlos con dt, hacerlos frenar ante el jugador u otro vehículo en su carril, impedir que el jugador los atraviese,
 * hacerlos respetar los semáforos (frenan ante la línea con rojo o amarillo; ver Vehiculo.huecoSemaforo),
 * reiniciarlos con R, dibujarlos y enviar al shader los focos de sus faros (solo de noche).
 * Día/noche no se guarda aquí: se recibe como BooleanSupplier (Iluminacion::esNoche) y se pasa a cada Vehiculo.
 * Se comunica con: Mapa (celdas y coordenadas), Vehiculo (cada auto), Colisiones (círculo contra círculo), Cubo y
 * Shader (dibujo) y Juego (lo actualiza, lo reinicia y le pregunta si un movimiento del jugador choca).
 * Los vehículos no chocan entre sí: cada uno circula por el carril derecho de su sentido y frena ante otro que tenga
 * adelante en su corredor (la misma regla que con el jugador; ver actualizar()). Las rutas 1 y 4 comparten
 * la calle de la fila 4 (Z = -10) en sentidos opuestos, así se ve que usan carriles distintos; el resto de las rutas
 * solo se cruzan en intersecciones.
 */
public class Trafico {

    // ==================== 1. RUTAS (valores ajustables) ====================
    // Cada ruta es una lista cíclica de celdas {fila, columna}. Todas deben ser cruces (fila y columna pares) y cada
    // tramo entre dos waypoints consecutivos debe ir en línea recta por calle; validarRuta() lo comprueba al arrancar.
    public static final int[][][] RUTAS_CELDAS = {
        {{0, 0}, {4, 0}, {4, 4}, {0, 4}}, // Ruta 1: vuelta al sector noroeste; por la fila 4 va hacia el ESTE.
        {{0, 10}, {4, 10}, {4, 6}, {0, 6}}, // Ruta 2: vuelta al sector noreste, en sentido contrario.
        {{6, 6}, {6, 10}, {10, 10}, {10, 8}, {8, 8}, {8, 6}}, // Ruta 3: sureste con forma de L (seis giros).
        {{4, 4}, {4, 0}, {10, 0}, {10, 4}} // Ruta 4: suroeste; por la fila 4 va hacia el OESTE (carril opuesto a la ruta 1).
    };
    public static final float[] VELOCIDADES = {7, 6, 8, 6.5f}; // Velocidad de crucero de cada vehículo, en unidades por segundo.
    public static final float[][] COLORES = { // Color de la carrocería de cada vehículo (RGB entre 0 y 1).
        {0.95f, 0.78f, 0.10f}, // Vehículo 1: amarillo, como un taxi.
        {0.15f, 0.40f, 0.85f}, // Vehículo 2: azul.
        {0.20f, 0.65f, 0.30f}, // Vehículo 3: verde.
        {0.90f, 0.90f, 0.92f} // Vehículo 4: blanco.
    };

    public static final int MAX_FAROS_TRAFICO = 16; // Focos que admite iluminacion.frag (uFarosTrafico[16]): hasta 8 vehículos.
    public static final int FAROS_POR_VEHICULO = 2; // Cada vehículo proyecta un foco por faro delantero.
    public static final float RADIO_JUGADOR = Auto.RADIO_AUTO; // Círculo del jugador como obstáculo del tráfico.

    // ==================== 2. ESTADO ====================
    private final Shader shader; // Programa que recibe el interruptor de emisión.
    private final Cubo cubo; // Geometría con la que se dibujan los vehículos.
    private final List<Vehiculo> vehiculos = new ArrayList<>(); // Un vehículo por ruta.
    private final BooleanSupplier esNoche; // Estado día/noche de Iluminacion; decide si hay focos de tráfico.
    private float reloj = 0; // Segundos acumulados; solo lo usa actualizar() sin reloj global. No se reinicia con R.

    /** Valida las rutas contra el Mapa y crea un vehículo por cada una; esNoche suele ser Iluminacion::esNoche. No usa OpenGL. */
    public Trafico(Shader shader, Cubo cubo, BooleanSupplier esNoche) {
        this.shader = shader; // Guarda el programa para cambiar uEmision.
        this.esNoche = esNoche; // Guarda la consulta de día/noche.
        this.cubo = cubo; // Guarda la geometría compartida.
        for (int indice = 0; indice < RUTAS_CELDAS.length; indice++) { // Recorre las rutas definidas.
            float[][] ruta = validarRuta(RUTAS_CELDAS[indice]); // Comprueba la ruta y la convierte a coordenadas.
            float[] color = COLORES[indice % COLORES.length]; // Elige el color; se repiten si hay más rutas que colores.
            float velocidadCrucero = VELOCIDADES[indice % VELOCIDADES.length]; // Elige la velocidad de este vehículo.
            vehiculos.add(new Vehiculo(ruta, velocidadCrucero, color[0], color[1], color[2], esNoche)); // Crea el vehículo.
        }
    }

    /** Comprueba que la ruta solo use calles y la convierte de celdas a coordenadas del mundo con Mapa.centro(). */
    static float[][] validarRuta(int[][] celdas) {
        float[][] ruta = new float[celdas.length][]; // Waypoints convertidos a {x, z}.
        for (int indice = 0; indice < celdas.length; indice++) { // Recorre los waypoints.
            int[] desde = celdas[indice]; // Waypoint actual {fila, columna}.
            int[] hasta = celdas[(indice + 1) % celdas.length]; // Waypoint siguiente; el último conecta con el primero.
            if (desde[0] != hasta[0] && desde[1] != hasta[1]) { // Un tramo diagonal cruzaría manzanas.
                throw new IllegalStateException("Tramo diagonal en la ruta: " + desde[0] + "," + desde[1]); // Detiene el arranque.
            }
            int pasoFila = Integer.signum(hasta[0] - desde[0]); // -1, 0 o 1: sentido del avance por filas.
            int pasoColumna = Integer.signum(hasta[1] - desde[1]); // -1, 0 o 1: sentido del avance por columnas.
            int fila = desde[0]; // Celda que se revisa, empezando por el waypoint.
            int columna = desde[1]; // Celda que se revisa, empezando por el waypoint.
            while (true) { // Recorre todas las celdas del tramo, incluidos ambos extremos.
                if (!Mapa.esCalle(fila, columna)) { // Cualquier celda que no sea calle haría atravesar una manzana.
                    throw new IllegalStateException("La ruta pasa por una manzana en " + fila + "," + columna); // Informa el error.
                }
                if (fila == hasta[0] && columna == hasta[1]) { // Llegó al final del tramo.
                    break; // Pasa al siguiente tramo.
                }
                fila += pasoFila; // Avanza una celda en la dirección del tramo.
                columna += pasoColumna; // Avanza una celda en la dirección del tramo.
            }
            ruta[indice] = new float[] {Mapa.centro(desde[1]), Mapa.centro(desde[0])}; // Columna → X, fila → Z.
        }
        return ruta; // Devuelve la ruta lista para el vehículo.
    }

    // ==================== 3. ACTUALIZACIÓN, CHOQUES Y REINICIO ====================

    /**
     * Mueve todos los vehículos. Cada uno frena por el obstáculo más cercano de su corredor (Vehiculo.huecoHasta):
     * el jugador (jugadorX, jugadorZ) o cualquier otro vehículo, con la misma regla. Para otro vehículo se mira también
     * su trayectoria de los próximos segundos (Vehiculo.huecoHastaTrayectoria), así los cruces se ven con tiempo.
     * Primero se calculan todos los huecos y después se mueven todos, así el orden de la lista no cambia el resultado.
     * tiempo es el reloj global de Juego (el que anima los semáforos): con él cada vehículo sabe qué luz ve su acceso y
     * frena ante la línea de detención (Vehiculo.huecoSemaforo). El jugador no se ve afectado por los semáforos.
     */
    public void actualizar(float deltaTime, float jugadorX, float jugadorZ, float tiempo) {
        actualizar(deltaTime, jugadorX, jugadorZ, tiempo, new float[0]); // Sin obstáculos extra.
    }

    /**
     * Igual que la anterior, y además cada vehículo frena ante los círculos extra del jugador (su chata). extras trae
     * {x1, z1, radio1, x2, z2, radio2, ...}; se tratan con la misma regla que el círculo del jugador.
     */
    public void actualizar(float deltaTime, float jugadorX, float jugadorZ, float tiempo, float[] extras) {
        int cantidad = vehiculos.size(); // Número de vehículos.
        float[] huecos = new float[cantidad]; // Hueco de cada vehículo hasta su obstáculo más cercano.
        for (int i = 0; i < cantidad; i++) { // Vehículo que decide si frena.
            Vehiculo vehiculo = vehiculos.get(i);
            float hueco = vehiculo.huecoHasta(jugadorX, jugadorZ, RADIO_JUGADOR); // El jugador, con su círculo.
            for (int k = 0; k + 2 < extras.length; k += 3) { // Y los círculos de su chata.
                hueco = Math.min(hueco, vehiculo.huecoHasta(extras[k], extras[k + 1], extras[k + 2]));
            }
            for (int j = 0; j < cantidad; j++) { // Posibles obstáculos: los demás vehículos.
                if (j != i && cede(i, j)) { // Solo frena por el otro si en ese par le toca ceder.
                    hueco = Math.min(hueco, vehiculo.huecoHastaTrayectoria(vehiculos.get(j))); // El más cercano.
                }
            }
            huecos[i] = hueco;
        }
        for (int i = 0; i < cantidad; i++) { // Ahora sí, mueve a todos.
            Vehiculo vehiculo = vehiculos.get(i);
            vehiculo.actualizar(deltaTime, huecos[i], vehiculo.huecoSemaforo(tiempo)); // Avanza por su ruta, frenando si hace falta.
        }
    }

    /** Igual que la anterior con un reloj propio (suma deltaTime); sirve cuando no hay reloj global, como en las pruebas. */
    public void actualizar(float deltaTime, float jugadorX, float jugadorZ) {
        reloj += deltaTime; // Mismo avance que el reloj global de Juego.
        actualizar(deltaTime, jugadorX, jugadorZ, reloj);
    }

    /**
     * Indica si el vehículo i debe ceder ante el j. Si solo uno ve al otro, cede ese. Si se ven los DOS, esperarían los
     * dos para siempre (espera mutua); entonces cede uno solo:
     *  - si uno tiene al otro físicamente adelante en su carril y el otro no, cede el de atrás (el de adelante sigue
     *    y se aleja, como en una fila);
     *  - si no (se cruzan, o los dos se tienen adelante), cede el de índice mayor. Es una prioridad fija: no cambia de
     *    un cuadro al otro, así nunca pasa que los dos crean tener paso al mismo tiempo.
     */
    private boolean cede(int i, int j) {
        Vehiculo a = vehiculos.get(i);
        Vehiculo b = vehiculos.get(j);
        if (a.huecoHastaTrayectoria(b) == Vehiculo.SIN_OBSTACULO) { // i no ve a j: no hay nada que ceder.
            return false;
        }
        if (b.huecoHastaTrayectoria(a) == Vehiculo.SIN_OBSTACULO) { // Solo i ve a j: cede i.
            return true;
        }
        boolean aTieneAdelanteB = a.huecoHasta(b.getX(), b.getZ(), Vehiculo.RADIO_VEHICULO) != Vehiculo.SIN_OBSTACULO;
        boolean bTieneAdelanteA = b.huecoHasta(a.getX(), a.getZ(), Vehiculo.RADIO_VEHICULO) != Vehiculo.SIN_OBSTACULO;
        if (aTieneAdelanteB != bTieneAdelanteA) { // Uno está detrás del otro.
            return aTieneAdelanteB; // Cede el de atrás.
        }
        return i > j; // Cruce: prioridad fija por índice.
    }

    /** Indica si mover al jugador de (antesX, antesZ) a (despuesX, despuesZ) lo haría atravesar un vehículo. */
    public boolean bloquea(float antesX, float antesZ, float despuesX, float despuesZ, float radioJugador) {
        for (Vehiculo vehiculo : vehiculos) { // Revisa cada vehículo.
            float vx = vehiculo.getX(); // Centro X del vehículo.
            float vz = vehiculo.getZ(); // Centro Z del vehículo.
            boolean seSolapa = Colisiones.circulosSeSolapan(despuesX, despuesZ, radioJugador, vx, vz, Vehiculo.RADIO_VEHICULO); // Choque en la posición nueva.
            float distanciaAntes = distanciaCuadrada(antesX, antesZ, vx, vz); // Separación antes de moverse.
            float distanciaDespues = distanciaCuadrada(despuesX, despuesZ, vx, vz); // Separación después de moverse.
            if (seSolapa && distanciaDespues < distanciaAntes) { // Solo bloquea si el jugador se mete más adentro.
                return true; // Alejarse siempre está permitido, así el jugador nunca queda atrapado.
            }
        }
        return false; // Ningún vehículo impide el movimiento.
    }

    /** Distancia al cuadrado entre dos puntos del plano XZ. */
    private static float distanciaCuadrada(float x1, float z1, float x2, float z2) {
        float dx = x1 - x2; // Diferencia en X.
        float dz = z1 - z2; // Diferencia en Z.
        return dx * dx + dz * dz; // Pitágoras sin raíz.
    }

    /** Devuelve todos los vehículos al inicio de sus rutas; Juego lo llama al presionar R. */
    public void reset() {
        for (Vehiculo vehiculo : vehiculos) { // Recorre los vehículos.
            vehiculo.reset(); // Primer waypoint, orientado al segundo, a velocidad de crucero.
        }
    }

    // ==================== 4. DIBUJO Y CONSULTAS ====================

    /** Dibuja todos los vehículos; de noche sus faros y luces traseras brillan. */
    public void dibujar(Figuras figuras, Cabina cabina) {
        for (Vehiculo vehiculo : vehiculos) { // Recorre los vehículos.
            vehiculo.dibujar(cubo, figuras, shader, cabina); // Cada uno con sus piezas, su color, su cabina, sus ruedas y sus luces.
        }
    }

    /**
     * Envía al shader la posición y la dirección de cada foco de los faros del tráfico (uFarosTrafico,
     * uDireccionFarosTrafico) y cuántos hay (uNumFarosTrafico). De día envía 0: el shader no calcula ningún foco.
     * Juego lo llama en cada cuadro, junto con Iluminacion.preparar(), antes de dibujar la escena.
     */
    public void prepararFaros() {
        int cantidad = 0; // Focos enviados hasta ahora.
        if (esNoche.getAsBoolean()) { // Los faros del tráfico solo se encienden de noche.
            for (Vehiculo vehiculo : vehiculos) { // Recorre los vehículos.
                float frenteX = -(float) Math.sin(vehiculo.getAngulo()); // Dirección de avance en X.
                float frenteZ = -(float) Math.cos(vehiculo.getAngulo()); // Dirección de avance en Z.
                for (int lado = 0; lado < FAROS_POR_VEHICULO && cantidad < MAX_FAROS_TRAFICO; lado++) { // Faro izquierdo y derecho.
                    float[] origen = vehiculo.posicionFaro(lado); // Punto del mundo donde nace el haz.
                    shader.vector("uFarosTrafico[" + cantidad + "]", origen[0], origen[1], origen[2]); // Posición del foco.
                    shader.vector("uDireccionFarosTrafico[" + cantidad + "]", frenteX, 0, frenteZ); // Hacia dónde apunta.
                    cantidad++; // Un foco más en uso.
                }
            }
        }
        shader.entero("uNumFarosTrafico", cantidad); // De día queda en 0.
    }

    /** Devuelve la lista de vehículos (solo lectura), para pruebas y para el minimapa. */
    public List<Vehiculo> getVehiculos() {
        return Collections.unmodifiableList(vehiculos); // Impide agregar o quitar vehículos desde afuera.
    }
}
