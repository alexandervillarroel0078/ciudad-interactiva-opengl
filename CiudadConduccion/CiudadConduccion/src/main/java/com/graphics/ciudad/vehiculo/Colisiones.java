package com.graphics.ciudad.vehiculo; // Agrupa el vehículo del jugador y sus colisiones.

import com.graphics.ciudad.mundo.Mapa; // Proporciona las manzanas y los límites de la ciudad.

/**
 * COLISIONES: decide si el auto cabe en una posición.
 * Responsable de: la prueba círculo contra rectángulo entre el auto (radio RADIO_AUTO) y cada manzana,
 * usando el punto más cercano y la distancia al cuadrado, y de impedir salir por los bordes del mapa.
 * También ofrece la prueba círculo contra círculo que usa Trafico para que el jugador no atraviese otros vehículos.
 * Se comunica con: Mapa (celdas y límites), Auto (lo consulta antes de moverse) y Trafico. No usa OpenGL.
 * El círculo de colisión es conservador para contener todas las piezas. Al chocar, Auto prueba cada eje por separado
 * y desliza por el que está libre (ver Auto.actualizar); solo se detiene si ambos ejes están bloqueados.
 */
public final class Colisiones {

    /**
     * Pregunta "¿el auto cabe en (x, z)?". Auto la recibe ya armada: por defecto es puedeCircular (manzanas y bordes);
     * Juego le suma el tráfico. Así el deslizamiento por ejes funciona igual contra cualquier obstáculo.
     */
    @FunctionalInterface
    public interface PosicionLibre {
        /** Devuelve true si el círculo del auto puede ocupar (x, z). */
        boolean libre(float x, float z);
    }

    // ==================== COLISIONES CON LA CIUDAD ====================

    /** Impide crear objetos: la prueba no guarda estado. */
    private Colisiones() {
    }

    /** Comprueba si el círculo del auto cabe en una posición sin tocar manzanas ni bordes. */
    public static boolean puedeCircular(float x, float z) {
        return puedeCircular(x, z, Auto.RADIO_AUTO); // El auto, con su radio de siempre.
    }

    /** Igual que puedeCircular(x, z) pero para un círculo de cualquier radio (la chata usa uno más chico que el auto). */
    public static boolean puedeCircular(float x, float z, float radio) {
        float limitePermitido = Mapa.LIMITE - radio; // Reserva espacio para que el círculo completo quede dentro.

        if (Math.abs(x) > limitePermitido || Math.abs(z) > limitePermitido) { // Detecta salida por cualquier borde.
            return false; // Rechaza la posición exterior.
        }

        for (int fila = 0; fila < Mapa.MAPA.length; fila++) { // Recorre las filas del mapa.
            for (int columna = 0; columna < Mapa.MAPA[fila].length; columna++) { // Recorre las celdas de cada fila.
                if (Mapa.esCalle(fila, columna)) { // Una celda con cero representa calle.
                    continue; // Omite la calle porque no es un obstáculo.
                }

                float centroX = Mapa.centro(columna); // Convierte la columna al centro X de la manzana.
                float centroZ = Mapa.centro(fila); // Convierte la fila al centro Z de la manzana.
                float mitadCelda = Mapa.TAM_CELDA / 2; // La manzana ocupa la celda completa: 5 unidades a cada lado de su centro.
                float cercaX = Math.max(centroX - mitadCelda, Math.min(x, centroX + mitadCelda)); // Busca el X más cercano dentro de la acera.
                float cercaZ = Math.max(centroZ - mitadCelda, Math.min(z, centroZ + mitadCelda)); // Busca el Z más cercano dentro de la acera.
                float distanciaX = x - cercaX; // Calcula la separación horizontal al rectángulo.
                float distanciaZ = z - cercaZ; // Calcula la separación en profundidad al rectángulo.
                float distanciaCuadrada = distanciaX * distanciaX + distanciaZ * distanciaZ; // Aplica Pitágoras sin raíz.

                if (distanciaCuadrada < radio * radio) { // Comprueba si el círculo invade la manzana.
                    return false; // Rechaza el movimiento que produciría una colisión.
                }
            }
        }

        return true; // Acepta la posición porque no se encontró ningún obstáculo.
    }

    /**
     * Cuánto sobra (positivo) o falta (negativo) para que el círculo toque un obstáculo de la ciudad: la menor distancia
     * a un borde o una manzana menos el radio. Sirve para comparar dos posiciones y dejar que el remolque se aleje de
     * una pared aunque todavía la esté pisando (ver Chata.puedeMoverseA).
     */
    public static float holgura(float x, float z, float radio) {
        float holgura = Mapa.LIMITE - radio - Math.max(Math.abs(x), Math.abs(z)); // Lo que falta para el borde más cercano.
        float mitadCelda = Mapa.TAM_CELDA / 2; // Medio ancho de una manzana.
        for (int fila = 0; fila < Mapa.MAPA.length; fila++) { // Recorre las filas del mapa.
            for (int columna = 0; columna < Mapa.MAPA[fila].length; columna++) { // Recorre las celdas de cada fila.
                if (Mapa.esCalle(fila, columna)) { // La calle no es obstáculo.
                    continue;
                }
                float centroX = Mapa.centro(columna); // Centro de la manzana.
                float centroZ = Mapa.centro(fila);
                float distanciaX = x - Math.max(centroX - mitadCelda, Math.min(x, centroX + mitadCelda)); // Hasta el punto más cercano.
                float distanciaZ = z - Math.max(centroZ - mitadCelda, Math.min(z, centroZ + mitadCelda));
                float distancia = (float) Math.sqrt(distanciaX * distanciaX + distanciaZ * distanciaZ); // Distancia al rectángulo.
                holgura = Math.min(holgura, distancia - radio); // Se queda con el obstáculo más comprometido.
            }
        }
        return holgura; // Negativa: el círculo está metido en un obstáculo.
    }

    /** Círculo contra círculo: dos objetos se tocan si la distancia entre centros es menor que la suma de sus radios. */
    public static boolean circulosSeSolapan(float x1, float z1, float radio1, float x2, float z2, float radio2) {
        float distanciaX = x1 - x2; // Separación horizontal entre los centros.
        float distanciaZ = z1 - z2; // Separación en profundidad entre los centros.
        float sumaRadios = radio1 + radio2; // Distancia mínima permitida entre centros.
        return distanciaX * distanciaX + distanciaZ * distanciaZ < sumaRadios * sumaRadios; // Compara al cuadrado para evitar la raíz.
    }
}
