package com.graphics.ciudad.mundo; // Agrupa lo que forma la ciudad: mapa, edificios, decoración y semáforos.

import com.graphics.ciudad.motor.Cubo; // Dibuja ventanas y franjas.
import com.graphics.ciudad.motor.Estadisticas; // Categoría de cada draw call (desglose de F5).
import com.graphics.ciudad.motor.Figuras; // Esfera, cilindro y cono para los parques.
import com.graphics.ciudad.motor.Shader; // Activa la emisión de las ventanas nocturnas.
import java.util.ArrayList; // Lista de ubicaciones de los pasos peatonales.
import java.util.Collections; // Publica la lista sin permitir modificarla desde afuera.
import java.util.List; // Tipo de la lista de ubicaciones.

/**
 * DECORACION: detalles urbanos de la ciudad terminada.
 * Responsable de: decidir qué decoración corresponde a cada parcela del Mapa y dibujar parques (delegados en Parque:
 * senderos, fuente, árboles y bancos), fachadas de los edificios (delegadas en Fachada: planta baja comercial y
 * ventanas según día/noche) y pasos peatonales; delega en Senalizacion los semáforos, PARE y carteles, y en Basureros
 * los cestos de basura.
 * Se comunica con: Mapa (celdas, intersecciones y parques), Cubo y Shader (dibujo y emisión), Senalizacion (señales y
 * cruces con semáforo) y Ciudad (que corta la línea amarilla con hayPasoSobre()).
 * Juego la dibuja solo en la vista principal; en el minimapa se omite, y le pasa el estado de noche de
 * Iluminacion y el reloj global de Juego (los semáforos siguen ciclando aunque la partida termine).
 *
 * PASOS PEATONALES: van donde el peatón realmente cruza y el conductor espera encontrarlo:
 *  - en CADA acceso de los cruces CONTROLADOS: los que tienen semáforo (Senalizacion.INTERSECCIONES_SEMAFORO) y los
 *    que tienen PARE (Senalizacion.UBICACIONES_PARE). Ahí el auto se detiene, así que el peatón cruza seguro; el
 *    semáforo y el PARE están justo detrás de las franjas, donde el auto frena;
 *  - junto a cada parque: en la calle vecina al norte y en la vecina al oeste, pegados al cruce siguiente, porque
 *    los parques atraen peatones.
 * Cada paso ocupa la celda de calle vecina a la intersección, a SEPARACION_CRUCE del borde del cruce (como en la vida
 * real: la esquina queda libre para doblar y el peatón cruza un poco antes), mide LARGO_PASO en el sentido de
 * circulación (corto) y cruza la calle en perpendicular, de vereda a vereda. Hay pasos en calles norte-sur y este-oeste.
 * Las franjas son paralelas al sentido de circulación (senda de cebra) y Ciudad corta la línea amarilla donde hay un
 * paso. Todo sale del Mapa: con 13 × 13 los cruces controlados y sus pasos se recalculan solos.
 */
public class Decoracion {

    // ==================== PASOS PEATONALES (valores ajustables) ====================
    public static final float LARGO_PASO = 3; // Largo del paso en el sentido de circulación: es el largo de cada franja.
    public static final float SEPARACION_CRUCE = 0.5f; // Asfalto libre entre el borde del cruce y el paso: no tapa la esquina.
    public static final int FRANJAS_PASO = 6; // Franjas blancas por paso.
    public static final float SEPARACION_FRANJAS = 1.65f; // Distancia entre centros de franjas vecinas (uniforme).
    public static final float ANCHO_FRANJA = 0.9f; // Ancho de cada franja; 5 · 1.65 + 0.9 = 9.15 de los 10 de la calle: de vereda a vereda.
    public static final float ALTURA_FRANJA = 0.03f; // Centro de la pintura: la franja va de 0.02 a 0.04, apenas sobre el asfalto (Y = 0).
    public static final float GROSOR_FRANJA = 0.02f; // Espesor de la pintura; separada del asfalto para evitar z-fighting.
    // Cada ubicación es {centroX, centroZ, ejeX}: ejeX = 1 si la calle va de oeste a este (franjas a lo largo de X),
    // 0 si va de norte a sur (franjas a lo largo de Z). Se genera desde los cruces controlados y la lista de parques.
    public static final List<float[]> UBICACIONES_PASOS = Collections.unmodifiableList(calcularUbicaciones());

    private final Cubo cubo; // Geometría con la que se construyen los detalles.
    private final Senalizacion senalizacion; // Dibuja semáforos, PARE y carteles de sector.
    private final Parque parque; // Dibuja senderos, fuente, árboles y bancos de cada parque.
    private final Fachada fachada; // Dibuja puertas, vidrieras, toldos y ventanas de los edificios.
    private final Basureros basureros; // Cestos de los parques, las esquinas y algunos negocios.

    /** Recibe el shader, el cubo y las figuras compartidas, y prepara la señalización y los parques. */
    public Decoracion(Shader shader, Cubo cubo, Figuras figuras) {
        this.cubo = cubo; // Guarda la geometría compartida.
        this.senalizacion = new Senalizacion(shader, cubo, figuras); // Un mismo objeto dibuja todas las señales.
        this.parque = new Parque(shader, cubo, figuras); // Calcula una vez la disposición de todos los parques.
        this.fachada = new Fachada(shader, cubo); // Un mismo objeto dibuja las fachadas de todos los edificios.
        this.basureros = new Basureros(figuras); // Ubicaciones ya calculadas en Basureros.UBICACIONES.
    }

    // ==================== UBICACIÓN DE LOS PASOS PEATONALES ====================

    /** Genera los pasos: uno por acceso de cada cruce controlado y hasta dos junto a cada parque, sin repetir. */
    private static List<float[]> calcularUbicaciones() {
        List<float[]> pasos = new ArrayList<>(); // Ubicaciones encontradas.
        for (int[] cruce : crucesControlados()) { // Cruces con semáforo o con PARE.
            for (int[] acceso : Mapa.accesos(cruce[0], cruce[1])) { // Cada calle que llega.
                agregarSinRepetir(pasos, pasoEnAcceso(cruce[0], cruce[1], acceso[0], acceso[1])); // Paso en ese acceso.
            }
        }
        for (int[] parque : Mapa.parques()) { // Parques del mapa.
            int fila = parque[0]; // Fila del parque.
            int columna = parque[1]; // Columna del parque.
            // Calle al norte del parque (fila - 1), pegado al cruce del este: acceso "desde el oeste" de ese cruce.
            if (Mapa.esInterseccion(fila - 1, columna + 1) && Mapa.esCalleSegura(fila - 1, columna)) { // Existe ese tramo.
                agregarSinRepetir(pasos, pasoEnAcceso(fila - 1, columna + 1, 0, -1)); // Paso en la calle norte.
            }
            // Calle al oeste del parque (columna - 1), pegado al cruce del sur: acceso "desde el norte" de ese cruce.
            if (Mapa.esInterseccion(fila + 1, columna - 1) && Mapa.esCalleSegura(fila, columna - 1)) { // Existe ese tramo.
                agregarSinRepetir(pasos, pasoEnAcceso(fila + 1, columna - 1, -1, 0)); // Paso en la calle oeste.
            }
        }
        return pasos; // Lista completa de pasos.
    }

    /**
     * Cruces {fila, columna} con semáforo o con PARE, sin repetir: los semáforos del Centro y las intersecciones de
     * UBICACIONES_PARE (una intersección aparece una sola vez aunque tenga más de un PARE).
     */
    public static List<int[]> crucesControlados() {
        List<int[]> cruces = new ArrayList<>(Senalizacion.INTERSECCIONES_SEMAFORO); // Primero los de semáforo.
        for (int[] pare : Senalizacion.UBICACIONES_PARE) { // {fila, columna, dFila, dColumna}.
            boolean repetido = false;
            for (int[] c : cruces) {
                repetido |= c[0] == pare[0] && c[1] == pare[1]; // Mismo cruce.
            }
            if (!repetido) {
                cruces.add(new int[] {pare[0], pare[1]});
            }
        }
        return cruces;
    }

    /** Paso {x, z, ejeX} en el acceso {dFila, dColumna} de un cruce: en la celda vecina, a SEPARACION_CRUCE del cruce. */
    public static float[] pasoEnAcceso(int fila, int columna, int dFila, int dColumna) {
        float distancia = Mapa.TAM_CELDA / 2 + SEPARACION_CRUCE + LARGO_PASO / 2; // Del centro del cruce al del paso: 5 + 0.5 + 1.5.
        float x = Mapa.centro(columna) + dColumna * distancia; // Se aleja del cruce por la calle del acceso (columnas = X).
        float z = Mapa.centro(fila) + dFila * distancia; // Igual en Z (filas = Z).
        float ejeX = dColumna != 0 ? 1 : 0; // Si el acceso cambia de columna, la calle va de oeste a este.
        return new float[] {x, z, ejeX}; // Ubicación del paso.
    }

    /** Agrega un paso a la lista si no hay otro en el mismo lugar (un cruce con semáforo puede estar junto a un parque). */
    private static void agregarSinRepetir(List<float[]> pasos, float[] nuevo) {
        for (float[] paso : pasos) { // Revisa los pasos existentes.
            if (Math.abs(paso[0] - nuevo[0]) < 0.01f && Math.abs(paso[1] - nuevo[1]) < 0.01f) { // Misma posición.
                return; // Ya existe: no se duplica.
            }
        }
        pasos.add(nuevo); // Paso nuevo.
    }

    /** Media extensión {mitadX, mitadZ} del rectángulo que ocupa un paso con el eje indicado. */
    public static float[] mitadesPaso(boolean ejeX) {
        float largo = LARGO_PASO / 2; // Mitad del largo del paso en el sentido de circulación.
        float travesia = ((FRANJAS_PASO - 1) * SEPARACION_FRANJAS + ANCHO_FRANJA) / 2; // Mitad de lo que cubren las franjas a lo ancho de la calle.
        return ejeX ? new float[] {largo, travesia} : new float[] {travesia, largo}; // Intercambia según la orientación.
    }

    /** Indica si un rectángulo centrado en (x, z) con medias medidas (mitadX, mitadZ) toca algún paso peatonal. */
    public static boolean hayPasoSobre(float x, float z, float mitadX, float mitadZ) {
        for (float[] paso : UBICACIONES_PASOS) { // Revisa cada paso.
            float[] mitad = mitadesPaso(paso[2] == 1); // Tamaño del paso según su orientación.
            boolean solapaX = Math.abs(x - paso[0]) < mitad[0] + mitadX; // Los intervalos en X se superponen.
            boolean solapaZ = Math.abs(z - paso[1]) < mitad[1] + mitadZ; // Los intervalos en Z se superponen.
            if (solapaX && solapaZ) { // Dos rectángulos se tocan si se superponen en ambos ejes.
                return true; // Hay un paso debajo.
            }
        }
        return false; // Ningún paso toca el rectángulo.
    }

    // ==================== DECORACIÓN DE LAS MANZANAS ====================

    /** Decide qué decoración corresponde a cada tipo de parcela. */
    public void dibujar(boolean noche, float tiempo) {
        for (int fila = 0; fila < Mapa.MAPA.length; fila++) { // Recorre las filas del mapa.
            for (int columna = 0; columna < Mapa.MAPA[fila].length; columna++) { // Recorre las columnas de esa fila.
                float x = Mapa.centro(columna); // Obtiene el centro horizontal de la parcela.
                float z = Mapa.centro(fila); // Obtiene el centro de la parcela en profundidad.
                int tipo = Mapa.tipo(fila, columna); // Lee el contenido de la celda.
                if (tipo == Mapa.PARQUE) { // Detecta una parcela de parque.
                    Estadisticas.fijarCategoria(Estadisticas.ARBOLES_PARQUES); // Senderos, fuente, árboles y bancos.
                    parque.dibujar(fila, columna, noche); // Añade senderos, fuente, árboles y bancos propios de este parque.
                }
                if (tipo == Mapa.EDIFICIO) { // Detecta una parcela con edificio.
                    fachada.dibujar(fila, columna, x, z, noche); // Planta baja según el uso (negocio, hall, departamentos o casa) y ventanas según el tipo (vidrio de día, variadas de noche).
                }
            }
        }
        Estadisticas.fijarCategoria(Estadisticas.FAROLAS_SENALES); // Semáforos, PARE y carteles.
        senalizacion.dibujar(tiempo); // Semáforos del Centro, PARE y carteles de sector, en sus ubicaciones calculadas.
        for (float[] paso : UBICACIONES_PASOS) { // Los pasos se dibujan una vez cada uno, desde la lista.
            Estadisticas.fijarCategoria(Estadisticas.CALLES); // Pintura del paso sobre el asfalto.
            dibujarPasoPeatonal(paso[0], paso[1], paso[2] == 1); // Añade el cruce pintado de vereda a vereda.
        }
        Estadisticas.fijarCategoria(Estadisticas.OTROS); // Cestos de basura.
        basureros.dibujar(); // Cestos de basura: parques, esquinas con paso y negocios.
    }

    /** Dibuja las franjas blancas de un paso: paralelas al sentido de circulación y repartidas a lo ancho de la calle. */
    private void dibujarPasoPeatonal(float x, float z, boolean ejeX) {
        for (int i = 0; i < FRANJAS_PASO; i++) { // Coloca FRANJAS_PASO franjas paralelas.
            float desplazamiento = (i - (FRANJAS_PASO - 1) / 2f) * SEPARACION_FRANJAS; // Centradas en el eje de la calle, separación uniforme.
            if (ejeX) { // Calle de oeste a este: la franja es larga en X y se reparten a lo ancho (Z).
                cubo.caja(x, ALTURA_FRANJA, z + desplazamiento, LARGO_PASO, GROSOR_FRANJA, ANCHO_FRANJA, 0.85f, 0.87f, 0.83f); // Eleva la pintura un poco sobre el suelo.
            } else { // Calle de norte a sur: la franja es larga en Z y se reparten a lo ancho (X).
                cubo.caja(x + desplazamiento, ALTURA_FRANJA, z, ANCHO_FRANJA, GROSOR_FRANJA, LARGO_PASO, 0.85f, 0.87f, 0.83f); // Misma pintura, girada.
            }
        }
    }
}
