package com.graphics.ciudad.mundo; // Agrupa lo que forma la ciudad: mapa, edificios, decoración y señales.

import com.graphics.ciudad.motor.Cubo; // Ventanas, puertas, vidrieras y toldos son cajas finas.
import com.graphics.ciudad.motor.Estadisticas; // Categoría de cada draw call (desglose de F5).
import com.graphics.ciudad.motor.Shader; // Emisión de las ventanas encendidas y las vidrieras de noche.
import java.util.ArrayList; // Lista de ventanas de cada edificio.
import java.util.Collections; // Publica las listas sin permitir modificarlas.
import java.util.List; // Tipo de esas listas.

/**
 * FACHADA: todo lo que se ve pegado a las paredes de un edificio.
 * Responsable de:
 *  - PLANTA BAJA en cada cara que da a una calle, según el USO del edificio (UsoPlantaBaja.de: torre → lobby, casa baja
 *    → casa; los demás, negocio en el Centro y sobre las avenidas principales, departamentos en los barrios). En todos
 *    los usos la puerta va CENTRADA: ninguna queda junto a una esquina y dos caras vecinas nunca tienen puertas pegadas.
 *     · COMERCIAL: "vidriera | puerta | vidriera", con un toldo de color inclinado sobre cada vidriera (sin tapar la
 *       puerta) que sobresale un poco hacia la vereda. Entre la última vidriera y la esquina queda MARGEN_ESQUINA. El
 *       color del toldo (rojo, verde, azul o naranja, a veces a rayas) se elige por celda con Variacion.
 *     · LOBBY_OFICINAS: vidrio de piso a techo con parantes, puerta doble de vidrio oscuro y marquesina plana. De noche
 *       el vidrio es emisivo pero suave (el hall iluminado se ve desde la calle, sin competir con las vidrieras).
 *     · RESIDENCIAL: pared con dos ventanas comunes, puerta sobre un escalón y un alero chico encima de la puerta.
 *     · CASA: puerta de madera sobre un escalón y dos ventanas; en algunas casas (Variacion) una de las ventanas de
 *       una cara se reemplaza por un portón de garaje.
 *    Las ventanas de planta baja de RESIDENCIAL y CASA son ventanas comunes (piso 0): de noche se encienden o no como
 *    todas las demás.
 *  - VENTANAS en cada cara de cada volumen del edificio (Edificio.volumenes), con el patrón de su TipoEdificio: muchas
 *    y chicas en la torre, anchas en el bloque, pocas en la casa baja. Una ventana no se pone si la tapa otro volumen
 *    (la parte baja de un edificio doble, el nivel de abajo de un escalonado) ni en la planta baja de una cara a la
 *    calle: ahí manda el uso (vidriera, hall o las ventanas propias de la planta baja, que esquivan la puerta).
 *    De DÍA son vidrio claro (blanco-celeste grisáceo) y reciben la luz del sol como cualquier superficie; de NOCHE
 *    cerca de PORCENTAJE_VENTANAS_ENCENDIDAS están encendidas (emisivas, con tonos de TONOS_VENTANA) y el resto apagadas
 *    (azul-gris muy oscuro). Cada ventana decide con un hash de edificio, volumen, cara, piso y columna: la misma
 *    ventana siempre está igual, sin parpadeos.
 * El estado día/noche no se guarda aquí: Decoracion lo recibe de Juego, que lo lee de Iluminacion.esNoche().
 * Todo va sobre la pared o, como el toldo, sobre la vereda (VUELO_TOLDO): nada llega a la calzada ni cambia colisiones.
 * Se comunica con: Decoracion (la llama para cada edificio), Mapa (celdas, vecinos, ancho del edificio), Edificio y
 * TipoEdificio (volúmenes y patrón de ventanas), UsoPlantaBaja, Variacion, Cubo y Shader.
 * Coordenadas de una cara: "u" recorre la pared de izquierda a derecha y "afuera" es la distancia desde el centro del
 * edificio en la dirección de la calle; ANCHO_EDIFICIO / 2 = 3.5 es el plano de la pared.
 */
public class Fachada {

    // ==================== 1. VENTANAS (valores ajustables) ====================
    public static final float PORCENTAJE_VENTANAS_ENCENDIDAS = 0.65f; // Fracción de ventanas encendidas de noche (65 %).
    public static final float[][] TONOS_VENTANA = { // Colores de las ventanas encendidas (emisivos).
        {1.00f, 0.80f, 0.42f}, // Amarillo cálido (lámpara incandescente).
        {1.00f, 0.92f, 0.74f}, // Blanco cálido.
        {1.00f, 0.66f, 0.36f}, // Anaranjado suave.
        {0.80f, 0.90f, 1.00f} // Blanco frío (tubo fluorescente o pantalla).
    };
    public static final float[] PESOS_TONOS = {0.40f, 0.30f, 0.20f, 0.10f}; // Qué tan frecuente es cada tono (suman 1).
    public static final float[] COLOR_VIDRIO_DIA = {0.72f, 0.80f, 0.86f}; // Vidrio de día: blanco-celeste grisáceo, sin emisión.
    public static final float[] COLOR_VENTANA_APAGADA = {0.06f, 0.08f, 0.13f}; // Ventana apagada de noche: azul-gris muy oscuro.
    public static final float PRIMER_PISO_Y = 1.9f; // Centro de la fila de la planta baja; cada piso suma ALTO_PISO (4.9, 7.9...): antepecho de ≈ 0.7 sobre cada piso.
    // Cuántas ventanas, de qué tamaño y cada cuánto: TipoEdificio (columnasVentanas, separacionVentanas, anchoVentana,
    // altoVentana, alturaPiso). Los pisos se cuentan desde la acera para todo el edificio, así las filas de ventanas
    // de volúmenes vecinos quedan alineadas.
    public static final float MARGEN_VERTICAL = 0.35f; // Pared libre entre una ventana y la base o el tope de su volumen (y su losa).
    public static final float MARGEN_LATERAL = 0.3f; // Pared libre entre la última ventana y la esquina de su cara.
    public static final float TOPE_PLANTA_BAJA = 2.8f; // En una cara a la calle, ninguna ventana del patrón baja de acá (el toldo está en 2.45).

    // ==================== 2. PLANTA BAJA (valores ajustables) ====================
    public static final float[] COLOR_PUERTA = {0.14f, 0.11f, 0.09f}; // Madera muy oscura.
    public static final float ANCHO_PUERTA = 1.0f; // Ancho de la puerta.
    public static final float ALTO_PUERTA = 2.0f; // Alto de la puerta: más que el auto (≈1.4), menos que un piso.
    public static final float MARGEN_ESQUINA = 0.4f; // Pared libre entre cada esquina del edificio y la vidriera más cercana.
    public static final float SEPARACION_PUERTA = 0.2f; // Pared libre entre la puerta y cada vidriera (el marco).
    // Una cara mide ANCHO_EDIFICIO = 7. Desde el centro hacia cada esquina: media puerta (0.5), separación (0.2), vidriera
    // y margen (0.4). La vidriera ocupa lo que queda: 3.5 - 0.4 - 0.5 - 0.2 = 2.4.
    public static final float ANCHO_VIDRIERA = Mapa.ANCHO_EDIFICIO / 2 - MARGEN_ESQUINA - ANCHO_PUERTA / 2 - SEPARACION_PUERTA; // 2.4.
    public static final float CENTRO_VIDRIERA = ANCHO_PUERTA / 2 + SEPARACION_PUERTA + ANCHO_VIDRIERA / 2; // 1.9: centro de cada vidriera sobre el eje u.
    public static final float EXCESO_TOLDO = 0.05f; // El toldo sobresale esto a cada lado de su vidriera (sin llegar a la puerta ni al margen).
    public static final float ALTO_VIDRIERA = 1.4f; // Alto del vidrio.
    public static final float BASE_VIDRIERA = 0.6f; // Altura del borde inferior del vidrio (zócalo).
    public static final float[] COLOR_VIDRIERA_DIA = {0.55f, 0.70f, 0.80f}; // Vidrio de día, sin emisión.
    // De noche la vidriera es vidrio iluminado desde adentro: más clara abajo (donde está la mercadería bajo las
    // lámparas) y más apagada arriba. Como Cubo pinta cada caja de un solo color, el degradado se arma con
    // FRANJAS_VIDRIERA franjas horizontales cuyo color va de COLOR_VIDRIERA_NOCHE_ABAJO a COLOR_VIDRIERA_NOCHE_ARRIBA.
    public static final float[] COLOR_VIDRIERA_NOCHE_ABAJO = {0.86f, 0.70f, 0.46f}; // Franja inferior: cálida, emisiva y menos intensa que antes (1, 0.84, 0.58).
    public static final float[] COLOR_VIDRIERA_NOCHE_ARRIBA = {0.58f, 0.46f, 0.32f}; // Franja superior: la más tenue.
    public static final int FRANJAS_VIDRIERA = 6; // Más franjas = degradado más suave (y más cajas por vidriera).
    public static final float[][] COLORES_TOLDO = { // Colores posibles del toldo.
        {0.78f, 0.12f, 0.10f}, // Rojo.
        {0.10f, 0.50f, 0.22f}, // Verde.
        {0.12f, 0.28f, 0.70f}, // Azul.
        {0.95f, 0.50f, 0.10f} // Naranja.
    };
    public static final float PROBABILIDAD_RAYAS = 0.35f; // Fracción de edificios con toldo a rayas (color y blanco).
    public static final float ANCHO_RAYA = 0.6f; // Ancho de cada raya del toldo.
    public static final float VUELO_TOLDO = 0.7f; // Cuánto sobresale el toldo de la pared: queda sobre la vereda (1.5 de ancho).
    public static final float ALTURA_TOLDO = 2.45f; // Altura del borde del toldo pegado a la pared.
    public static final float CAIDA_TOLDO = 0.3f; // Cuánto baja el toldo desde la pared hasta su borde exterior.
    public static final int ESCALONES_TOLDO = 3; // Cubo solo gira en Y: la inclinación se arma con escalones que bajan.

    // ==================== 2b. PLANTA BAJA NO COMERCIAL (valores ajustables) ====================
    // ---- Lobby de oficinas (torres): vidrio de piso a techo, puerta doble y marquesina ----
    public static final float ANCHO_PUERTA_DOBLE = 1.6f; // Dos hojas de 0.8, centradas.
    public static final float ALTO_PUERTA_LOBBY = 2.3f; // Más alta que la de un negocio: es la entrada de la torre.
    public static final float SEPARACION_PUERTA_LOBBY = 0.1f; // Marco entre la puerta y cada paño de vidrio.
    public static final float ALTO_VIDRIO_LOBBY = 2.9f; // De la acera (0.3) a 3.2: casi hasta la losa del podio (3.5).
    public static final float SEPARACION_PARANTES = 1.0f; // Distancia máxima entre parantes (perfiles verticales) del vidrio.
    public static final float ANCHO_PARANTE = 0.08f; // Ancho de cada parante.
    public static final float[] COLOR_PARANTE = {0.22f, 0.23f, 0.25f}; // Aluminio oscuro.
    public static final float[] COLOR_PUERTA_LOBBY = {0.10f, 0.12f, 0.14f}; // Vidrio oscuro de las hojas.
    public static final float[] COLOR_VIDRIO_LOBBY_DIA = {0.42f, 0.55f, 0.62f}; // Vidrio azulado, sin emisión.
    public static final float[] COLOR_VIDRIO_LOBBY_NOCHE = {0.50f, 0.54f, 0.55f}; // Emisivo SUAVE: más tenue que la vidriera (0.86).
    public static final float ANCHO_MARQUESINA = 2.4f; // Losa plana sobre la puerta doble.
    public static final float VUELO_MARQUESINA = 0.7f; // Igual que el toldo: mantener ≤ 0.8 para no tocar los postes de semáforo.
    public static final float GROSOR_MARQUESINA = 0.12f;
    public static final float[] COLOR_MARQUESINA = {0.30f, 0.31f, 0.33f}; // Hormigón oscuro.
    // ---- Entrada residencial (departamentos) y de casa ----
    public static final float ANCHO_VENTANA_PLANTA_BAJA = 1.4f; // Dos ventanas comunes, una a cada lado de la puerta.
    public static final float CENTRO_VENTANA_PLANTA_BAJA = CENTRO_VIDRIERA; // 1.9: donde iría la vidriera; ventana de 1.2 a 2.6.
    public static final float ALTO_ESCALON = 0.15f; // Escalón de la puerta: la puerta arranca sobre él.
    public static final float FONDO_ESCALON = 0.35f; // Cuánto sale hacia la vereda.
    public static final float EXCESO_ESCALON = 0.15f; // El escalón es esto más ancho que la puerta a cada lado.
    public static final float[] COLOR_ESCALON = {0.62f, 0.60f, 0.56f}; // Hormigón claro.
    public static final float ANCHO_ALERO = 1.6f; // Alero chico sobre la puerta (solo RESIDENCIAL).
    public static final float VUELO_ALERO = 0.5f;
    public static final float GROSOR_ALERO = 0.1f;
    public static final float[] COLOR_ALERO = {0.35f, 0.33f, 0.31f};
    public static final float[] COLOR_PUERTA_CASA = {0.42f, 0.26f, 0.14f}; // Madera más clara que la de un negocio.
    public static final float PROBABILIDAD_GARAJE = 0.5f; // Fracción de casas con portón de garaje (en una sola cara).
    public static final int SEMILLA_GARAJE = 29; // Elegida para que en este MAPA 2 de las 4 casas tengan garaje (5,1 y 7,1).
    public static final float ANCHO_GARAJE = ANCHO_VIDRIERA; // 2.4: ocupa el lugar de una ventana, sin tocar la puerta.
    public static final float ALTO_GARAJE = 2.2f; // Desde la acera: entra un auto (≈ 1.4).
    public static final float[] COLOR_GARAJE = {0.70f, 0.71f, 0.72f}; // Chapa clara.
    public static final int LISTONES_GARAJE = 5; // Líneas horizontales del portón (secciones).
    public static final float[] COLOR_LISTON = {0.50f, 0.51f, 0.53f}; // Junta entre secciones, más oscura.
    private static final float ALTURA_ACERA = 0.3f; // El edificio se apoya sobre la acera (0.3 de alto).
    private static final float SEPARACION_PARED = 0.01f; // Todo lo pegado a la pared se separa 0.01 para que no parpadee.
    private static final float GROSOR_PEGADO = 0.04f; // Espesor de ventanas, puertas y vidrieras.

    // ==================== 3. DECISIONES DETERMINÍSTICAS (sin azar por cuadro) ====================

    /** Una ventana ya ubicada: a qué volumen, cara, piso y columna pertenece, su centro en el mundo y su tamaño. */
    public static final class Ventana {
        public final int volumen, cara, piso, columna; // Identidad dentro del edificio.
        public final float x, z, y, ancho, alto; // Centro sobre la pared y medidas.

        Ventana(int volumen, int cara, int piso, int columna, float x, float z, float y, float ancho, float alto) {
            this.volumen = volumen;
            this.cara = cara;
            this.piso = piso;
            this.columna = columna;
            this.x = x;
            this.z = z;
            this.y = y;
            this.ancho = ancho;
            this.alto = alto;
        }

        /** Índice único dentro del edificio: combina volumen, cara, piso y columna (cada combinación da otro número). */
        public int indice() {
            return volumen * 10000 + cara * 1000 + piso * 10 + columna; // Menos de 10 columnas y de 100 pisos.
        }
    }

    /** Cuántas columnas del patrón entran en una cara de ese ancho, dejando MARGEN_LATERAL a cada lado (al menos una). */
    public static int columnasQueEntran(TipoEdificio tipo, float anchoCara) {
        int n = tipo.columnasVentanas; // Lo que pide el patrón.
        while (n > 1 && (n - 1) * tipo.separacionVentanas + tipo.anchoVentana > anchoCara - 2 * MARGEN_LATERAL) { // No entran.
            n--; // Una columna menos.
        }
        return n; // Columnas que se dibujan.
    }

    /**
     * Todas las ventanas del edificio de la celda: recorre cada volumen, cada cara y cada piso, y descarta las que
     * no entran en el volumen, las que quedan sobre el negocio de la planta baja y las que tapa otro volumen.
     */
    public static List<Ventana> ventanas(int fila, int columna) {
        TipoEdificio tipo = Edificio.tipo(fila, columna); // Patrón de ventanas.
        List<Edificio.Volumen> volumenes = Edificio.volumenes(fila, columna); // Cajas con paredes.
        float centroX = Mapa.centro(columna); // Centro de la manzana.
        float centroZ = Mapa.centro(fila);
        List<Ventana> lista = new ArrayList<>(); // Resultado.
        for (int iv = 0; iv < volumenes.size(); iv++) { // Cada volumen.
            Edificio.Volumen vol = volumenes.get(iv);
            for (int cara = 0; cara < Mapa.VECINOS.length; cara++) { // Norte, sur, oeste y este.
                int[] dir = Mapa.VECINOS[cara]; // {dFila, dColumna}: la columna es X y la fila es Z.
                boolean normalEnX = dir[1] != 0; // La cara mira al oeste o al este.
                float mitad = normalEnX ? vol.anchoX / 2 : vol.anchoZ / 2; // Del centro del volumen a esta pared.
                float anchoCara = normalEnX ? vol.anchoZ : vol.anchoX; // Largo de la pared.
                float hastaCentro = normalEnX ? (vol.x - centroX) * dir[1] : (vol.z - centroZ) * dir[0]; // Corrimiento del volumen hacia afuera.
                boolean enBorde = Math.abs(hastaCentro + mitad - Mapa.ANCHO_EDIFICIO / 2) < 1e-3f; // La pared está en el borde de la huella.
                boolean conPlantaBaja = enBorde && Mapa.esCalleSegura(fila + dir[0], columna + dir[1]); // Planta baja según el uso.
                int n = columnasQueEntran(tipo, anchoCara); // Columnas de esta cara.
                for (int piso = 0; ; piso++) { // Pisos desde la acera.
                    float y = PRIMER_PISO_Y + piso * tipo.alturaPiso; // Centro de las ventanas de este piso.
                    float abajo = y - tipo.altoVentana / 2; // Borde inferior.
                    float arriba = y + tipo.altoVentana / 2; // Borde superior.
                    if (arriba > vol.yTope - MARGEN_VERTICAL) { // Ya no entra: los pisos siguientes tampoco.
                        break;
                    }
                    if (abajo < vol.yBase + MARGEN_VERTICAL || (conPlantaBaja && abajo < TOPE_PLANTA_BAJA)) { // Debajo del volumen o en la planta baja.
                        continue;
                    }
                    for (int col = 0; col < n; col++) { // Columnas centradas en la cara.
                        float u = (col - (n - 1) / 2f) * tipo.separacionVentanas; // Posición a lo largo de la pared.
                        float[] p = puntoEnCara(vol.x, vol.z, cara, u, mitad + SEPARACION_PARED); // Apenas delante de la pared.
                        if (!tapada(volumenes, iv, p, abajo, arriba)) { // Solo las que se ven.
                            lista.add(new Ventana(iv, cara, piso, col, p[0], p[1], y, tipo.anchoVentana, tipo.altoVentana));
                        }
                    }
                }
            }
        }
        if (!UsoPlantaBaja.de(fila, columna).esVidriada()) { // Departamentos o casa: ventanas propias en la planta baja.
            agregarVentanasPlantaBaja(fila, columna, tipo, volumenes, lista);
        }
        return lista; // Ventanas del edificio.
    }

    /**
     * Ventanas de la planta baja de RESIDENCIAL y CASA: en cada cara a la calle, una a cada lado de la puerta (en
     * ±CENTRO_VENTANA_PLANTA_BAJA), salvo donde está el portón de garaje. Son del piso 0 del volumen que ocupa ese
     * tramo de la pared (en un doble, la parte alta o la baja), con el alto de ventana del tipo.
     */
    private static void agregarVentanasPlantaBaja(int fila, int columna, TipoEdificio tipo, List<Edificio.Volumen> volumenes, List<Ventana> lista) {
        float centroX = Mapa.centro(columna); // Centro de la manzana.
        float centroZ = Mapa.centro(fila);
        int[] garaje = garaje(fila, columna); // {cara, lado} o null.
        for (int cara = 0; cara < Mapa.VECINOS.length; cara++) { // Norte, sur, oeste y este.
            int[] dir = Mapa.VECINOS[cara];
            if (!Mapa.esCalleSegura(fila + dir[0], columna + dir[1])) { // Solo las caras a la calle.
                continue;
            }
            for (int col = 0; col < 2; col++) { // Izquierda (0) y derecha (1) de la puerta.
                int lado = col == 0 ? -1 : 1;
                if (garaje != null && garaje[0] == cara && garaje[1] == lado) { // Ahí va el portón.
                    continue;
                }
                float u = lado * CENTRO_VENTANA_PLANTA_BAJA; // Posición a lo largo de la pared.
                float[] p = puntoEnCara(centroX, centroZ, cara, u, Mapa.ANCHO_EDIFICIO / 2 + SEPARACION_PARED); // Delante de la pared.
                float[] adentro = puntoEnCara(centroX, centroZ, cara, u, Mapa.ANCHO_EDIFICIO / 2 - 0.1f); // Apenas detrás: ¿qué volumen es?
                float abajo = PRIMER_PISO_Y - tipo.altoVentana / 2;
                float arriba = PRIMER_PISO_Y + tipo.altoVentana / 2;
                for (int iv = 0; iv < volumenes.size(); iv++) { // El volumen que tiene esa pared a esa altura.
                    Edificio.Volumen vol = volumenes.get(iv);
                    boolean entra = abajo >= vol.yBase + MARGEN_VERTICAL && arriba <= vol.yTope - MARGEN_VERTICAL;
                    if (vol.cubre(adentro[0], adentro[1]) && entra) {
                        if (!tapada(volumenes, iv, p, abajo, arriba)) {
                            lista.add(new Ventana(iv, cara, 0, col, p[0], p[1], PRIMER_PISO_Y, ANCHO_VENTANA_PLANTA_BAJA, tipo.altoVentana));
                        }
                        break; // Un solo volumen por ventana.
                    }
                }
            }
        }
    }

    /**
     * Portón de garaje de una CASA: {cara, lado} (lado -1 = izquierda de la puerta, +1 = derecha) o null si la casa no
     * tiene. Tienen garaje las casas con Variacion < PROBABILIDAD_GARAJE; la cara es la primera que da a la calle a
     * partir de una sorteada, así siempre mira a una calle.
     */
    public static int[] garaje(int fila, int columna) {
        if (UsoPlantaBaja.de(fila, columna) != UsoPlantaBaja.CASA
            || Variacion.valor(fila, columna, 0, SEMILLA_GARAJE) >= PROBABILIDAD_GARAJE) {
            return null; // Sin garaje.
        }
        int inicio = (int) (Variacion.valor(fila, columna, 1, SEMILLA_GARAJE) * Mapa.VECINOS.length); // Cara sorteada.
        int lado = Variacion.valor(fila, columna, 2, SEMILLA_GARAJE) < 0.5f ? -1 : 1; // Izquierda o derecha.
        for (int k = 0; k < Mapa.VECINOS.length; k++) { // A partir de la sorteada, la primera con calle.
            int cara = (inicio + k) % Mapa.VECINOS.length;
            int[] dir = Mapa.VECINOS[cara];
            if (Mapa.esCalleSegura(fila + dir[0], columna + dir[1])) {
                return new int[] {cara, lado};
            }
        }
        return null; // Una casa sin calles alrededor (no pasa en este mapa).
    }

    /** Indica si otro volumen (con su losa) tapa la ventana en el punto p, entre las alturas abajo y arriba. */
    private static boolean tapada(List<Edificio.Volumen> volumenes, int propio, float[] p, float abajo, float arriba) {
        for (int i = 0; i < volumenes.size(); i++) { // Los demás volúmenes.
            Edificio.Volumen otro = volumenes.get(i);
            boolean cruzaEnAltura = abajo < otro.yTope + Edificio.GROSOR_LOSA && arriba > otro.yBase; // Comparten alturas.
            if (i != propio && otro.cubre(p[0], p[1]) && cruzaEnAltura) { // La ventana quedaría adentro del otro.
                return true;
            }
        }
        return false; // Se ve desde afuera.
    }

    /**
     * Tono de una ventana de noche: -1 si está apagada, o el índice en TONOS_VENTANA si está encendida.
     * Usa dos valores de Variacion: uno decide encendida/apagada y el otro el tono, repartido según PESOS_TONOS.
     */
    public static int tonoVentana(int fila, int columna, Ventana ventana) {
        int indice = ventana.indice(); // Identidad de la ventana en su edificio.
        if (Variacion.valor(fila, columna, indice, 21) >= PORCENTAJE_VENTANAS_ENCENDIDAS) { // Por encima del umbral...
            return -1; // ...la ventana está apagada.
        }
        float sorteo = Variacion.valor(fila, columna, indice, 22); // Segundo número: elige el tono.
        float acumulado = 0; // Suma de pesos recorridos.
        for (int tono = 0; tono < TONOS_VENTANA.length; tono++) { // Recorre los tonos en orden.
            acumulado += PESOS_TONOS[tono]; // Agrega el peso de este tono.
            if (sorteo < acumulado) { // El número cayó en la franja de este tono.
                return tono; // Tono elegido.
            }
        }
        return TONOS_VENTANA.length - 1; // Por redondeo, el último tono.
    }

    /** Color {r, g, b, emisiva} de una ventana: vidrio de día; encendida (emisiva) o apagada de noche. */
    public static float[] colorVentana(int fila, int columna, Ventana ventana, boolean noche) {
        if (!noche) { // De día todas las ventanas son vidrio.
            return new float[] {COLOR_VIDRIO_DIA[0], COLOR_VIDRIO_DIA[1], COLOR_VIDRIO_DIA[2], 0}; // Sin emisión: las ilumina el sol.
        }
        int tono = tonoVentana(fila, columna, ventana); // Estado de esta ventana.
        if (tono < 0) { // Apagada.
            return new float[] {COLOR_VENTANA_APAGADA[0], COLOR_VENTANA_APAGADA[1], COLOR_VENTANA_APAGADA[2], 0}; // Oscura, sin emisión.
        }
        float[] c = TONOS_VENTANA[tono]; // Color de la luz interior.
        return new float[] {c[0], c[1], c[2], 1}; // Emisiva: se ve encendida aunque no le llegue luz.
    }

    /** Índice del color del toldo del edificio (en COLORES_TOLDO). */
    public static int colorToldo(int fila, int columna) {
        return (int) (Variacion.valor(fila, columna, 0, 23) * COLORES_TOLDO.length); // Reparte los colores en partes iguales.
    }

    /** Indica si el toldo del edificio es a rayas. */
    public static boolean toldoARayas(int fila, int columna) {
        return Variacion.valor(fila, columna, 0, 24) < PROBABILIDAD_RAYAS; // Algunos edificios, siempre los mismos.
    }

    /**
     * Color de la franja "franja" (0 = la de abajo) de una vidriera de noche: interpolación lineal entre
     * COLOR_VIDRIERA_NOCHE_ABAJO y COLOR_VIDRIERA_NOCHE_ARRIBA, t = franja / (FRANJAS_VIDRIERA - 1).
     */
    public static float[] colorVidrieraNoche(int franja) {
        float t = FRANJAS_VIDRIERA > 1 ? (float) franja / (FRANJAS_VIDRIERA - 1) : 0; // 0 abajo, 1 arriba.
        float[] c = new float[3]; // Resultado.
        for (int i = 0; i < 3; i++) { // Rojo, verde y azul.
            c[i] = COLOR_VIDRIERA_NOCHE_ABAJO[i] + (COLOR_VIDRIERA_NOCHE_ARRIBA[i] - COLOR_VIDRIERA_NOCHE_ABAJO[i]) * t; // Mezcla.
        }
        return c; // Color de la franja.
    }

    /** Tramo {uMin, uMax} que ocupa la puerta sobre la cara: siempre centrada. */
    public static float[] tramoPuerta() {
        return new float[] {-ANCHO_PUERTA / 2, ANCHO_PUERTA / 2}; // Del -0.5 al 0.5: lejos de ambas esquinas.
    }

    /**
     * EJES DE ACCESO de una cara: las posiciones u (a lo largo de la cara, mismo eje que puntoEnCara) por donde se entra
     * al edificio. Toda cara a la calle tiene su puerta CENTRADA (u = 0), sea cual sea el uso de planta baja: la de
     * vidrio del negocio, la doble del lobby, la del escalón de departamentos o la de madera de la casa. Si la casa
     * tiene el portón de GARAJE en esta cara, su eje también cuenta (por ahí sale un auto). Una cara sin calle no tiene
     * accesos. Lo usa Iluminacion para no plantar una farola frente a una entrada.
     */
    public static float[] ejesDeAcceso(int fila, int columna, int cara) {
        int[] v = Mapa.VECINOS[cara];
        if (Mapa.tipo(fila, columna) != Mapa.EDIFICIO || !Mapa.esCalleSegura(fila + v[0], columna + v[1])) {
            return new float[0]; // Sin edificio o sin calle: no hay puerta.
        }
        int[] garaje = garaje(fila, columna); // {cara, lado} o null.
        if (garaje != null && garaje[0] == cara) {
            return new float[] {0, garaje[1] * CENTRO_VENTANA_PLANTA_BAJA}; // Puerta y portón.
        }
        return new float[] {0}; // Solo la puerta centrada.
    }

    /** Tramos {uMin, uMax} de las dos vidrieras, una a cada lado de la puerta. */
    public static float[][] tramosVidrieras() {
        return new float[][] { // Izquierda y derecha, simétricas.
            {-CENTRO_VIDRIERA - ANCHO_VIDRIERA / 2, -CENTRO_VIDRIERA + ANCHO_VIDRIERA / 2}, // Vidriera izquierda: de -3.1 a -0.7.
            {CENTRO_VIDRIERA - ANCHO_VIDRIERA / 2, CENTRO_VIDRIERA + ANCHO_VIDRIERA / 2} // Vidriera derecha: de 0.7 a 3.1.
        };
    }

    /** Tramos {uMin, uMax} de los dos toldos: cada uno cubre su vidriera y EXCESO_TOLDO de más a cada lado. */
    public static float[][] tramosToldos() {
        float[][] vidrieras = tramosVidrieras(); // Parte de las vidrieras.
        return new float[][] { // Un toldo por vidriera: la puerta queda al descubierto entre ambos.
            {vidrieras[0][0] - EXCESO_TOLDO, vidrieras[0][1] + EXCESO_TOLDO}, // Toldo izquierdo.
            {vidrieras[1][0] - EXCESO_TOLDO, vidrieras[1][1] + EXCESO_TOLDO} // Toldo derecho.
        };
    }

    /** Punto {x, z} del mundo sobre la cara "cara" de un edificio con centro (x, z): u a lo largo, afuera hacia la calle. */
    public static float[] puntoEnCara(float x, float z, int cara, float u, float afuera) {
        int[] v = Mapa.VECINOS[cara]; // {dFila, dColumna}: la columna es X y la fila es Z.
        float nx = v[1]; // Normal de la cara en X.
        float nz = v[0]; // Normal de la cara en Z.
        float tx = -nz; // Eje "u" a lo largo de la pared, en X (perpendicular a la normal).
        float tz = nx; // Eje "u" en Z.
        return new float[] {x + nx * afuera + tx * u, z + nz * afuera + tz * u}; // Centro + normal · afuera + eje u · u.
    }

    // ==================== 4. DIBUJO ====================

    private final Shader shader; // Programa que recibe el interruptor de emisión.
    private final Cubo cubo; // Geometría compartida.
    private final List<List<Ventana>> ventanasPorCelda = new ArrayList<>(); // Ventanas calculadas una sola vez (fila · ancho + columna).

    /** Recibe el shader y el cubo compartidos y ubica de una vez las ventanas de todos los edificios. */
    public Fachada(Shader shader, Cubo cubo) {
        this.shader = shader; // Guarda el programa para cambiar uEmision.
        this.cubo = cubo; // Guarda la geometría compartida.
        for (int fila = 0; fila < Mapa.MAPA.length; fila++) { // Recorre el mapa.
            for (int columna = 0; columna < Mapa.MAPA[fila].length; columna++) {
                boolean hayEdificio = Mapa.tipo(fila, columna) == Mapa.EDIFICIO; // Solo las manzanas con edificio.
                ventanasPorCelda.add(hayEdificio ? Collections.unmodifiableList(ventanas(fila, columna)) : Collections.emptyList());
            }
        }
    }

    /** Dibuja la planta baja de las caras a la calle y las ventanas del edificio de la celda (fila, columna), con centro (x, z). */
    public void dibujar(int fila, int columna, float x, float z, boolean noche) {
        Estadisticas.fijarCategoria(Estadisticas.EDIFICIOS); // Puertas, vidrieras, toldos...
        UsoPlantaBaja uso = UsoPlantaBaja.de(fila, columna); // Negocio, hall, departamentos o casa.
        int[] garaje = garaje(fila, columna); // {cara, lado} o null.
        for (int cara = 0; cara < Mapa.VECINOS.length; cara++) { // Norte, sur, oeste y este (mismo orden que Mapa.VECINOS).
            int[] vecino = Mapa.VECINOS[cara]; // {dFila, dColumna} hacia afuera de esta cara.
            if (!Mapa.esCalleSegura(fila + vecino[0], columna + vecino[1])) { // Solo las caras a la calle tienen planta baja.
                continue;
            }
            switch (uso) {
                case COMERCIAL:
                    dibujarPlantaBaja(fila, columna, x, z, cara, noche); // Puerta, vidriera (iluminada de noche) y toldo.
                    break;
                case LOBBY_OFICINAS:
                    dibujarLobby(x, z, cara, noche); // Vidrio de piso a techo, puerta doble y marquesina.
                    break;
                case RESIDENCIAL:
                    dibujarEntrada(x, z, cara, COLOR_PUERTA, true); // Puerta con escalón y alero.
                    break;
                default: // CASA.
                    dibujarEntrada(x, z, cara, COLOR_PUERTA_CASA, false); // Puerta de madera con escalón.
                    if (garaje != null && garaje[0] == cara) {
                        dibujarGaraje(x, z, cara, garaje[1] * CENTRO_VENTANA_PLANTA_BAJA); // Portón en lugar de una ventana.
                    }
                    break;
            }
        }
        Estadisticas.fijarCategoria(Estadisticas.VENTANAS); // Las cajas de las ventanas.
        for (Ventana v : ventanasPorCelda.get(fila * Mapa.MAPA[0].length + columna)) { // Ventanas ya ubicadas.
            float[] c = colorVentana(fila, columna, v, noche); // Vidrio, encendida o apagada.
            shader.entero("uEmision", (int) c[3]); // Las ventanas encendidas simulan habitaciones con luz en el ambiente nocturno.
            cajaEnPunto(v.x, v.z, v.cara, v.y, v.ancho, v.alto, GROSOR_PEGADO, c[0], c[1], c[2]); // Ventana sobre su pared.
        }
        shader.entero("uEmision", 0); // Restablece la iluminación normal de los demás elementos.
    }

    /**
     * Dibuja una caja pegada a una cara: u = posición a lo largo de la pared, afuera = distancia desde el centro del
     * edificio hacia la calle, y = altura del centro; anchoU, alto y grosor son sus medidas.
     */
    private void cajaEnCara(float x, float z, int cara, float u, float afuera, float y, float anchoU, float alto, float grosor, float r, float g, float b) {
        float[] centro = puntoEnCara(x, z, cara, u, afuera); // Centro de la caja sobre la cara.
        cajaEnPunto(centro[0], centro[1], cara, y, anchoU, alto, grosor, r, g, b); // Caja alineada a esa cara.
    }

    /** Dibuja una caja centrada en (cx, y, cz), con anchoU a lo largo de la cara y grosor en la dirección de su normal. */
    private void cajaEnPunto(float cx, float cz, int cara, float y, float anchoU, float alto, float grosor, float r, float g, float b) {
        float nx = Mapa.VECINOS[cara][1]; // Normal de la cara en X (la columna es X).
        float sx = nx != 0 ? grosor : anchoU; // Si la cara mira al este u oeste, el grosor va en X y el ancho en Z.
        float sz = nx != 0 ? anchoU : grosor; // Y al revés si mira al norte o al sur.
        cubo.caja(cx, y, cz, sx, alto, sz, r, g, b); // Caja alineada a los ejes, pegada a la cara.
    }

    /** Planta baja comercial "vidriera | puerta | vidriera": puerta centrada y un toldo inclinado sobre cada vidriera. */
    private void dibujarPlantaBaja(int fila, int columna, float x, float z, int cara, boolean noche) {
        float pared = Mapa.ANCHO_EDIFICIO / 2 + SEPARACION_PARED + GROSOR_PEGADO / 2; // Plano apenas delante de la pared.
        cajaEnCara(x, z, cara, 0, pared, ALTURA_ACERA + ALTO_PUERTA / 2, ANCHO_PUERTA, ALTO_PUERTA, GROSOR_PEGADO,
            COLOR_PUERTA[0], COLOR_PUERTA[1], COLOR_PUERTA[2]); // Puerta oscura CENTRADA, apoyada en la acera: lejos de las esquinas.
        shader.entero("uEmision", noche ? 1 : 0); // De noche las vidrieras brillan con luz cálida propia.
        for (float[] tramo : tramosVidrieras()) { // Vidriera izquierda y derecha.
            float uVidriera = (tramo[0] + tramo[1]) / 2; // Centro de la vidriera sobre la cara.
            if (!noche) { // De día: un solo vidrio, iluminado por el sol.
                cajaEnCara(x, z, cara, uVidriera, pared, BASE_VIDRIERA + ALTO_VIDRIERA / 2, ANCHO_VIDRIERA, ALTO_VIDRIERA, GROSOR_PEGADO,
                    COLOR_VIDRIERA_DIA[0], COLOR_VIDRIERA_DIA[1], COLOR_VIDRIERA_DIA[2]); // Vidriera del negocio.
                continue;
            }
            float altoFranja = ALTO_VIDRIERA / FRANJAS_VIDRIERA; // Las franjas cubren justo el alto del vidrio.
            for (int franja = 0; franja < FRANJAS_VIDRIERA; franja++) { // De abajo hacia arriba.
                float[] c = colorVidrieraNoche(franja); // Más clara abajo, más tenue arriba.
                float y = BASE_VIDRIERA + (franja + 0.5f) * altoFranja; // Centro de la franja.
                cajaEnCara(x, z, cara, uVidriera, pared, y, ANCHO_VIDRIERA, altoFranja, GROSOR_PEGADO, c[0], c[1], c[2]); // Franja del vidrio.
            }
        }
        shader.entero("uEmision", 0); // Los toldos son tela: reciben luz normal.
        for (float[] tramo : tramosToldos()) { // Un toldo sobre cada vidriera; la puerta queda descubierta en el medio.
            dibujarToldo(fila, columna, x, z, cara, (tramo[0] + tramo[1]) / 2, tramo[1] - tramo[0]); // Toldo sobre la vidriera.
        }
    }

    /**
     * Hall de oficinas: dos paños de vidrio de piso a techo (de MARGEN_ESQUINA hasta el marco de la puerta), con parantes
     * cada SEPARACION_PARANTES como máximo, un paño más sobre la puerta, la puerta doble de vidrio oscuro al centro y una
     * marquesina plana que sale VUELO_MARQUESINA hacia la vereda. De noche el vidrio es emisivo suave; las hojas de la
     * puerta, los parantes y la marquesina no brillan.
     */
    private void dibujarLobby(float x, float z, int cara, boolean noche) {
        float pared = Mapa.ANCHO_EDIFICIO / 2 + SEPARACION_PARED + GROSOR_PEGADO / 2; // Plano apenas delante de la pared.
        float[] vidrio = noche ? COLOR_VIDRIO_LOBBY_NOCHE : COLOR_VIDRIO_LOBBY_DIA; // El hall iluminado, o vidrio al sol.
        float mediaPuerta = ANCHO_PUERTA_DOBLE / 2;
        float inicioPano = mediaPuerta + SEPARACION_PUERTA_LOBBY; // Borde del paño junto a la puerta.
        float finPano = Mapa.ANCHO_EDIFICIO / 2 - MARGEN_ESQUINA; // Borde del paño junto a la esquina.
        float anchoPano = finPano - inicioPano;
        float yVidrio = ALTURA_ACERA + ALTO_VIDRIO_LOBBY / 2; // Centro del vidrio: de la acera hacia arriba.
        float altoSobrePuerta = ALTO_VIDRIO_LOBBY - ALTO_PUERTA_LOBBY; // Paño sobre la puerta.
        shader.entero("uEmision", noche ? 1 : 0);
        for (int lado = -1; lado <= 1; lado += 2) { // Paño izquierdo y derecho.
            cajaEnCara(x, z, cara, lado * (inicioPano + anchoPano / 2), pared, yVidrio, anchoPano, ALTO_VIDRIO_LOBBY, GROSOR_PEGADO,
                vidrio[0], vidrio[1], vidrio[2]);
        }
        cajaEnCara(x, z, cara, 0, pared, ALTURA_ACERA + ALTO_PUERTA_LOBBY + altoSobrePuerta / 2, ANCHO_PUERTA_DOBLE + 2 * SEPARACION_PUERTA_LOBBY,
            altoSobrePuerta, GROSOR_PEGADO, vidrio[0], vidrio[1], vidrio[2]); // Vidrio sobre la puerta.
        shader.entero("uEmision", 0); // El resto recibe luz normal.
        float frente = pared + GROSOR_PEGADO; // Parantes y hojas, apenas delante del vidrio.
        int tramos = (int) Math.ceil(anchoPano / SEPARACION_PARANTES); // Paños entre parantes.
        for (int lado = -1; lado <= 1; lado += 2) {
            for (int k = 0; k <= tramos; k++) { // Un parante en cada borde y entre tramos.
                float u = lado * (inicioPano + k * anchoPano / tramos);
                cajaEnCara(x, z, cara, u, frente, yVidrio, ANCHO_PARANTE, ALTO_VIDRIO_LOBBY, GROSOR_PEGADO,
                    COLOR_PARANTE[0], COLOR_PARANTE[1], COLOR_PARANTE[2]);
            }
        }
        float anchoHoja = mediaPuerta - ANCHO_PARANTE / 2; // Dos hojas separadas por un parante central.
        for (int lado = -1; lado <= 1; lado += 2) {
            cajaEnCara(x, z, cara, lado * (ANCHO_PARANTE / 2 + anchoHoja / 2), frente, ALTURA_ACERA + ALTO_PUERTA_LOBBY / 2, anchoHoja,
                ALTO_PUERTA_LOBBY, GROSOR_PEGADO, COLOR_PUERTA_LOBBY[0], COLOR_PUERTA_LOBBY[1], COLOR_PUERTA_LOBBY[2]); // Hoja.
        }
        cajaEnCara(x, z, cara, 0, frente, ALTURA_ACERA + ALTO_PUERTA_LOBBY / 2, ANCHO_PARANTE, ALTO_PUERTA_LOBBY, GROSOR_PEGADO,
            COLOR_PARANTE[0], COLOR_PARANTE[1], COLOR_PARANTE[2]); // Parante entre las hojas.
        float yMarquesina = ALTURA_ACERA + ALTO_PUERTA_LOBBY + GROSOR_MARQUESINA; // Justo encima de la puerta.
        cajaEnCara(x, z, cara, 0, Mapa.ANCHO_EDIFICIO / 2 + VUELO_MARQUESINA / 2, yMarquesina, ANCHO_MARQUESINA, GROSOR_MARQUESINA,
            VUELO_MARQUESINA, COLOR_MARQUESINA[0], COLOR_MARQUESINA[1], COLOR_MARQUESINA[2]); // Marquesina plana.
    }

    /**
     * Entrada de departamentos o de casa: escalón sobre la acera, puerta centrada que arranca sobre el escalón y, si
     * "alero", un alero chico encima. Las ventanas de esta planta baja se dibujan con las demás (son Ventana del piso 0).
     */
    private void dibujarEntrada(float x, float z, int cara, float[] colorPuerta, boolean alero) {
        float pared = Mapa.ANCHO_EDIFICIO / 2 + SEPARACION_PARED + GROSOR_PEGADO / 2; // Plano apenas delante de la pared.
        float baseEscalon = ALTURA_ACERA; // El escalón se apoya en la acera...
        float basePuerta = ALTURA_ACERA + ALTO_ESCALON; // ...y la puerta, sobre el escalón.
        cajaEnCara(x, z, cara, 0, Mapa.ANCHO_EDIFICIO / 2 + FONDO_ESCALON / 2, baseEscalon + ALTO_ESCALON / 2,
            ANCHO_PUERTA + 2 * EXCESO_ESCALON, ALTO_ESCALON, FONDO_ESCALON, COLOR_ESCALON[0], COLOR_ESCALON[1], COLOR_ESCALON[2]); // Escalón.
        cajaEnCara(x, z, cara, 0, pared, basePuerta + ALTO_PUERTA / 2, ANCHO_PUERTA, ALTO_PUERTA, GROSOR_PEGADO,
            colorPuerta[0], colorPuerta[1], colorPuerta[2]); // Puerta centrada.
        if (alero) {
            float yAlero = basePuerta + ALTO_PUERTA + GROSOR_ALERO; // Apenas encima del marco.
            cajaEnCara(x, z, cara, 0, Mapa.ANCHO_EDIFICIO / 2 + VUELO_ALERO / 2, yAlero, ANCHO_ALERO, GROSOR_ALERO, VUELO_ALERO,
                COLOR_ALERO[0], COLOR_ALERO[1], COLOR_ALERO[2]); // Alero chico.
        }
    }

    /** Portón de garaje de chapa centrado en u, desde la acera, con LISTONES_GARAJE juntas horizontales. */
    private void dibujarGaraje(float x, float z, int cara, float u) {
        float pared = Mapa.ANCHO_EDIFICIO / 2 + SEPARACION_PARED + GROSOR_PEGADO / 2; // Plano apenas delante de la pared.
        cajaEnCara(x, z, cara, u, pared, ALTURA_ACERA + ALTO_GARAJE / 2, ANCHO_GARAJE, ALTO_GARAJE, GROSOR_PEGADO,
            COLOR_GARAJE[0], COLOR_GARAJE[1], COLOR_GARAJE[2]); // Portón.
        float seccion = ALTO_GARAJE / (LISTONES_GARAJE + 1); // Alto de cada sección.
        for (int k = 1; k <= LISTONES_GARAJE; k++) { // Juntas entre secciones.
            cajaEnCara(x, z, cara, u, pared + GROSOR_PEGADO / 2, ALTURA_ACERA + k * seccion, ANCHO_GARAJE, 0.04f, GROSOR_PEGADO / 2,
                COLOR_LISTON[0], COLOR_LISTON[1], COLOR_LISTON[2]);
        }
    }

    /**
     * Toldo inclinado: ESCALONES_TOLDO tiras que se alejan de la pared y bajan un poco cada una, más un faldón vertical
     * en el borde. Sobresale VUELO_TOLDO = 0.7: su borde queda a 4.2 del centro, sobre la vereda (que llega a 5) y antes
     * de los postes de semáforo (4.4) y farola (4.6). Si el edificio tiene toldo a rayas, cada tira alterna color y blanco.
     */
    private void dibujarToldo(int fila, int columna, float x, float z, int cara, float uCentro, float anchoToldo) {
        float[] color = COLORES_TOLDO[colorToldo(fila, columna)]; // Color del toldo de este edificio.
        boolean rayas = toldoARayas(fila, columna); // ¿Alterna con blanco?
        float fondoEscalon = VUELO_TOLDO / ESCALONES_TOLDO; // Profundidad de cada tira.
        float pared = Mapa.ANCHO_EDIFICIO / 2; // Plano de la pared.
        int rayasPorEscalon = rayas ? Math.max(1, Math.round(anchoToldo / ANCHO_RAYA)) : 1; // Una sola pieza si es liso.
        float anchoRaya = anchoToldo / rayasPorEscalon; // Ancho real de cada raya (reparte el toldo en partes iguales).
        for (int e = 0; e < ESCALONES_TOLDO; e++) { // De la pared hacia afuera.
            float afuera = pared + (e + 0.5f) * fondoEscalon; // Centro de la tira, cada vez más lejos de la pared.
            float y = ALTURA_TOLDO - (e + 0.5f) * CAIDA_TOLDO / ESCALONES_TOLDO; // Y cada vez más baja: forma la pendiente.
            for (int k = 0; k < rayasPorEscalon; k++) { // Rayas a lo ancho del toldo.
                float u = uCentro - anchoToldo / 2 + (k + 0.5f) * anchoRaya; // Centro de la raya.
                boolean blanca = rayas && k % 2 == 1; // Una de cada dos rayas es blanca.
                float r = blanca ? 0.95f : color[0]; // Color de la raya.
                float g = blanca ? 0.95f : color[1]; // Color de la raya.
                float b = blanca ? 0.92f : color[2]; // Color de la raya.
                cajaEnCara(x, z, cara, u, afuera, y, anchoRaya, 0.06f, fondoEscalon + 0.02f, r, g, b); // Tira del toldo (se superponen apenas).
            }
        }
        float bordeExterior = pared + VUELO_TOLDO - 0.02f; // Faldón en el extremo del toldo.
        float yFaldon = ALTURA_TOLDO - CAIDA_TOLDO - 0.1f; // Cuelga debajo de la última tira.
        cajaEnCara(x, z, cara, uCentro, bordeExterior, yFaldon, anchoToldo, 0.2f, 0.04f, color[0], color[1], color[2]); // Faldón del toldo.
    }
}
