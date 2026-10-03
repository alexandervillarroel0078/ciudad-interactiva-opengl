package com.graphics.ciudad.mundo; // Agrupa lo que forma la ciudad: mapa, edificios, decoración y semáforos.

import com.graphics.ciudad.motor.Cubo; // Dibuja cada elemento como una caja transformada.
import com.graphics.ciudad.motor.Figuras; // Cilindro y prisma para los techos de los edificios.
import java.util.ArrayList; // Lista de marcas viales.
import java.util.List; // Tipo de la lista de marcas viales.

/**
 * CIUDAD: transforma el Mapa en geometría.
 * Responsable de: dibujar la base de asfalto, las marcas viales de las calles, las aceras, los edificios
 * (su forma según el tipo la arma Edificio) y el césped de los parques.
 * Se comunica con: Mapa (lee celdas y centros), Edificio (volúmenes y techos) y Cubo (dibuja cada pieza). Juego la dibuja
 * primero en cada pase, antes del auto, las farolas y la decoración.
 */
public class Ciudad {

    private final Cubo cubo; // Geometría compartida con la que se construye toda la ciudad.
    private final Edificio edificio; // Arma y dibuja cada edificio según su tipo.

    /** Recibe el cubo y las figuras con los que se dibujará la ciudad. */
    public Ciudad(Cubo cubo, Figuras figuras) {
        this.cubo = cubo; // Guarda la referencia para usarla en cada cuadro.
        this.edificio = new Edificio(cubo, figuras); // Calcula una vez la forma de todos los edificios.
    }

    /** El edificio de cada manzana; Sombras lo reutiliza para proyectar su sombra planar con la misma geometría. */
    public Edificio getEdificio() {
        return edificio;
    }

    // ==================== CIUDAD A PARTIR DE UNA MATRIZ ====================

    /** Recorre el mapa y transforma cada celda en geometría. */
    public void dibujar() {
        cubo.caja(0, -0.25f, 0, Mapa.TAMANO, 0.5f, Mapa.TAMANO, 0.16f, 0.19f, 0.23f); // Dibuja la base de asfalto con su cara superior en Y=0.
        for (int fila = 0; fila < Mapa.MAPA.length; fila++) { // Recorre el mapa de norte a sur.
            for (int columna = 0; columna < Mapa.MAPA[fila].length; columna++) { // Recorre cada fila de izquierda a derecha.
                float x = Mapa.centro(columna); // Convierte la columna a posición X.
                float z = Mapa.centro(fila); // Convierte la fila a posición Z.
                int tipo = Mapa.tipo(fila, columna); // Lee si la celda es calle, edificio o parque.
                if (tipo == Mapa.CALLE) { // Selecciona las celdas transitables.
                    dibujarMarcasCalle(fila, columna, x, z); // Añade líneas amarillas entre intersecciones.
                } else { // Las demás celdas representan manzanas completas.
                    cubo.caja(x, 0.15f, z, Mapa.TAM_CELDA, 0.3f, Mapa.TAM_CELDA, 0.60f, 0.64f, 0.66f); // Dibuja la acera elevada sobre el asfalto.
                    if (tipo == Mapa.EDIFICIO) { // Selecciona una manzana ocupada por un edificio.
                        edificio.dibujar(fila, columna); // Torre, bloque, escalonado, casa baja o doble, con su techo.
                    } else { // El tipo 2 representa un parque.
                        cubo.caja(x, 0.32f, z, 9, 0.1f, 9, 0.20f, 0.45f, 0.28f); // Cubre la parcela con césped verde.
                    }
                }
            }
        }
    }

    /** Dibuja las líneas discontinuas de las calles dejando los cruces despejados. */
    private void dibujarMarcasCalle(int fila, int columna, float x, float z) {
        for (float[] marca : marcasDeCelda(fila, columna, x, z)) { // Marcas de esta celda, ya sin las que pisan un paso.
            cubo.caja(marca[0], 0.025f, marca[1], marca[2], 0.03f, marca[3], 1, 0.84f, 0.35f); // Dibuja la línea amarilla.
        }
    }

    /**
     * Calcula las marcas amarillas de una celda como rectángulos {x, z, anchoX, anchoZ}. La línea central se corta
     * donde hay un paso peatonal (Decoracion.hayPasoSobre): la pintura amarilla no queda debajo de las franjas.
     */
    static List<float[]> marcasDeCelda(int fila, int columna, float x, float z) {
        List<float[]> marcas = new ArrayList<>(); // Marcas visibles de la celda.
        if (fila % 2 == 0 && columna % 2 == 1) { // Identifica un tramo horizontal situado entre cruces.
            for (int desplazamiento = -3; desplazamiento <= 3; desplazamiento += 3) { // Coloca tres marcas en la celda.
                agregarSiNoHayPaso(marcas, x + desplazamiento, z, 1.6f, 0.13f); // Una línea alargada en X.
            }
        }
        if (columna % 2 == 0 && fila % 2 == 1) { // Identifica un tramo vertical situado entre cruces.
            for (int desplazamiento = -3; desplazamiento <= 3; desplazamiento += 3) { // Repite las marcas sobre ese tramo.
                agregarSiNoHayPaso(marcas, x, z + desplazamiento, 0.13f, 1.6f); // Una línea alargada en Z.
            }
        }
        return marcas; // Marcas que se deben pintar.
    }

    /** Agrega una marca solo si no toca ningún paso peatonal. */
    private static void agregarSiNoHayPaso(List<float[]> marcas, float x, float z, float anchoX, float anchoZ) {
        if (!Decoracion.hayPasoSobre(x, z, anchoX / 2, anchoZ / 2)) { // Corta la línea central en el paso.
            marcas.add(new float[] {x, z, anchoX, anchoZ}); // La marca queda libre: se pinta.
        }
    }
}
