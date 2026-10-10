package com.graphics.ciudad.juego; // Agrupa las reglas de la partida y el minimapa.

import com.graphics.ciudad.motor.Cubo; // Dibuja la marca y la baliza del destino.
import com.graphics.ciudad.motor.Shader; // Activa la emisión del objetivo.
import com.graphics.ciudad.mundo.Mapa; // Convierte las celdas de las paradas en coordenadas.
import com.graphics.ciudad.vehiculo.Auto; // Aporta posición y velocidad para comprobar la llegada.

/**
 * ENTREGAS: el objetivo del juego.
 * Responsable de: los DESTINOS, el progreso (entregas completadas), el cronómetro de la partida, la regla de
 * llegada (acercarse a menos de 3 unidades y frenar a menos de 1 unidad/segundo), el reinicio, el texto del
 * título y el dibujo de la marca dorada y la baliza del destino activo.
 * Se comunica con: Auto (lee su estado), Cubo y Shader (dibujo y emisión), Juego (lo actualiza, lo reinicia y
 * pide su texto). Su tiempo anima la baliza; los semáforos usan el reloj global de Juego.
 */
public class Entregas {

    // ==================== 1. ESTADO DEL JUEGO ====================
    private final Shader shader; // Programa que recibe el interruptor de emisión.
    private final Cubo cubo; // Geometría con la que se dibuja el destino.
    private int entregas = 0; // Cuenta las entregas completadas; también identifica el siguiente destino.
    private float tiempo = 0; // Acumula los segundos de la partida hasta completar el recorrido.
    // Paradas como celdas {fila, columna} de calle (fila y columna pares), igual que las rutas de Trafico, convertidas a
    // coordenadas con Mapa.centro(). ULTIMA es la última fila/columna: así las paradas siguen en los bordes y en las
    // esquinas aunque cambie el tamaño de MAPA. El auto parte cerca de la esquina suroeste, cruza hasta el noreste,
    // vuelve al oeste y termina en el sur.
    public static final float RADIO_LLEGADA = 3; // Distancia máxima a la marca para que cuente la entrega, en unidades.
    public static final float VELOCIDAD_LLEGADA = 1; // El auto debe ir más lento que esto (unidades/s) para completar la entrega.
    private static final float ALTURA_BALIZA = 3.5f; // Altura media del cubo dorado que flota sobre el destino.
    private static final float AMPLITUD_BALIZA = 0.3f; // Cuánto sube y baja la baliza respecto a su altura media.
    private static final float FRECUENCIA_BALIZA = 2; // Rapidez de la oscilación: multiplica el tiempo dentro del seno.
    private static final int ULTIMA = Mapa.MAPA.length - 1; // Índice de la última fila y columna (10 con el mapa 11 × 11).
    public static final int[][] CELDAS_DESTINOS = { // Cada fila contiene {fila, columna} de una parada sobre la calle.
        {0, ULTIMA}, // Primera entrega: esquina noreste, (50, -50) con el mapa 11 × 11.
        {4, 0}, // Segunda entrega: calle del borde oeste, a la altura de la tercera avenida: (-50, -10).
        {ULTIMA, ULTIMA - 2} // Tercera entrega: calle del borde sur, sector este: (30, 50).
    };
    public static final float[][] DESTINOS = aCoordenadas(CELDAS_DESTINOS); // Cada fila contiene X y Z de una parada.
    public static final String[] NOMBRES_DESTINOS = { // Nombre de cada parada, en el mismo orden que DESTINOS.
        "esquina noreste", // Nombre que muestra el título para la primera entrega.
        "borde oeste", // Nombre de la segunda entrega.
        "borde sur (este)" // Nombre de la tercera entrega.
    };

    /** Convierte celdas {fila, columna} en posiciones {x, z} con Mapa.centro(): columna → X, fila → Z. */
    static float[][] aCoordenadas(int[][] celdas) {
        float[][] posiciones = new float[celdas.length][]; // Una posición por celda.
        for (int i = 0; i < celdas.length; i++) { // Recorre las paradas.
            posiciones[i] = new float[] {Mapa.centro(celdas[i][1]), Mapa.centro(celdas[i][0])}; // Centro de la celda.
        }
        return posiciones; // Paradas en coordenadas del mundo.
    }

    /** Recibe el shader y el cubo compartidos. */
    public Entregas(Shader shader, Cubo cubo) {
        this.shader = shader; // Guarda el programa para cambiar uEmision.
        this.cubo = cubo; // Guarda la geometría compartida.
        validarDestinos(); // Falla al arrancar si un destino no es calle o queda aislado.
    }

    /** Comprueba con BFS desde la salida del auto que cada celda de CELDAS_DESTINOS es calle y alcanzable. */
    static void validarDestinos() {
        boolean[][] alcanzada = Mapa.alcanzables(Mapa.indiceCelda(Auto.Z_INICIAL), Mapa.indiceCelda(Auto.X_INICIAL));
        for (int i = 0; i < CELDAS_DESTINOS.length; i++) { // Revisa cada parada.
            int fila = CELDAS_DESTINOS[i][0];
            int columna = CELDAS_DESTINOS[i][1];
            if (!Mapa.esCalleSegura(fila, columna)) {
                throw new IllegalStateException("Destino " + i + " (" + NOMBRES_DESTINOS[i] + ") en la celda " + fila + "," + columna + " no es una calle");
            }
            if (!alcanzada[fila][columna]) {
                throw new IllegalStateException("Destino " + i + " (" + NOMBRES_DESTINOS[i] + ") en la celda " + fila + "," + columna + " no es alcanzable desde la salida del auto");
            }
        }
    }

    // ==================== 2. REINICIO ====================

    /** Reinicia el progreso de las entregas para completarlas de nuevo; Juego reinicia además el Auto con R. */
    public void reset() {
        entregas = 0; // Vuelve a seleccionar la primera parada.
        tiempo = 0; // Reinicia el cronómetro de la partida.
    }

    // ==================== 3. REGLAS DE LAS ENTREGAS ====================

    /** Comprueba si el auto llegó y frenó en el destino activo; Juego lo llama después de mover el Auto. */
    public void actualizar(float deltaTime, Auto auto) {
        actualizar(deltaTime, auto.getX(), auto.getZ(), auto.getVelocidad()); // Extrae del Auto solo los datos que usa la regla.
    }

    /** Aplica la regla de llegada con la posición (autoX, autoZ) y la velocidad; separada del Auto para poder probarla. */
    public void actualizar(float deltaTime, float autoX, float autoZ, float velocidad) {
        if (entregas >= DESTINOS.length) { // Comprueba si ya se completaron todas las paradas.
            return; // Conserva el tiempo final y evita leer fuera del arreglo.
        }
        tiempo += deltaTime; // Suma los segundos de este cuadro al cronómetro.
        float distanciaX = autoX - DESTINOS[entregas][0]; // Calcula la separación horizontal al destino activo.
        float distanciaZ = autoZ - DESTINOS[entregas][1]; // Calcula la separación en profundidad al destino.
        float distanciaCuadrada = distanciaX * distanciaX + distanciaZ * distanciaZ; // Mide cercanía sin calcular raíz cuadrada.
        boolean estaCerca = distanciaCuadrada < RADIO_LLEGADA * RADIO_LLEGADA; // Acepta un radio de llegada de tres unidades.
        boolean estaFrenando = Math.abs(velocidad) < VELOCIDAD_LLEGADA; // Exige circular a menos de una unidad por segundo.
        if (estaCerca && estaFrenando) { // Solo completa la entrega si ambas condiciones se cumplen.
            entregas++; // Selecciona la siguiente parada o completa el juego.
        }
    }

    /** Compone el progreso que se añade al título de la ventana. */
    public String estado() {
        String mensaje = ""; // Juego antepone la velocidad y los indicadores de iluminación.
        mensaje += "Entregas: " + entregas + "/" + DESTINOS.length; // Indica cuántas paradas se completaron.
        if (entregas == DESTINOS.length) { // Selecciona el texto de victoria al completar las tres paradas.
            mensaje += " | Destino: GANASTE en " + (int) tiempo + " s! R: jugar otra vez"; // Muestra tiempo final y opción de reinicio.
        } else { // Durante el recorrido muestra hacia dónde ir.
            mensaje += " | Destino: " + NOMBRES_DESTINOS[entregas]; // Nombra la parada activa (marca dorada).
        }
        return mensaje; // Entrega el texto a actualizarTitulo() de Juego.
    }

    /** Devuelve cuántas entregas se completaron (0 a DESTINOS.length). */
    public int getEntregas() {
        return entregas; // Progreso actual de la partida.
    }

    /** Devuelve la posición {X, Z} de la parada activa, o null si ya se completaron todas. */
    public float[] destinoActual() {
        if (entregas >= DESTINOS.length) { // Después de la última entrega no hay destino.
            return null; // Indica que la partida terminó.
        }
        return DESTINOS[entregas]; // La siguiente parada coincide con el número de entregas hechas.
    }

    /** Devuelve el nombre del destino activo, o "completado" si ya no quedan paradas; lo muestra el HUD. */
    public String nombreDestino() {
        if (entregas >= DESTINOS.length) { // Después de la última entrega no hay destino.
            return "completado (GANASTE en " + (int) tiempo + " s)"; // Muestra el tiempo final.
        }
        return NOMBRES_DESTINOS[entregas]; // Nombre de la parada activa.
    }

    /** Indica si todavía hay una parada activa. */
    public boolean quedanEntregas() {
        return entregas < DESTINOS.length; // Falso después de la tercera entrega.
    }

    /** Devuelve los segundos de partida; también animan la baliza del destino. */
    public float getTiempo() {
        return tiempo; // Cronómetro actual.
    }

    // ==================== 4. ESCENA FINAL Y DESTINO ====================

    /** Marca la próxima parada con una plataforma y una baliza flotante. */
    public void dibujarDestino(boolean vistaMapa) {
        float x = DESTINOS[entregas][0]; // Lee el X de la próxima entrega.
        float z = DESTINOS[entregas][1]; // Lee el Z de la próxima entrega.
        shader.entero("uEmision", 1); // Hace que el objetivo sea visible incluso de noche.
        cubo.caja(x, 0.06f, z, 5, 0.08f, 5, 1, 0.72f, 0.12f); // Dibuja una marca dorada sobre el asfalto.
        if (!vistaMapa) { // Evita añadir una baliza tridimensional al mapa pequeño.
            float alturaBaliza = ALTURA_BALIZA + (float) Math.sin(tiempo * FRECUENCIA_BALIZA) * AMPLITUD_BALIZA; // Hace oscilar la baliza suavemente.
            cubo.cajaGirada(x, alturaBaliza, z, 0.8f, 0.8f, 0.8f, 1, 0.8f, 0.15f, tiempo); // Dibuja el cubo giratorio del objetivo.
        } else { // En el minimapa la marca del suelo quedaría pequeña: se agrega un cuadrado dorado bien visible.
            cubo.caja(x, Minimapa.ALTURA_DESTINO, z, 7, 0.1f, 7, 1, 0.8f, 0.15f); // Por encima de los edificios y debajo del indicador del auto.
        }
        shader.entero("uEmision", 0); // Devuelve a los siguientes objetos su iluminación normal.
    }
}
