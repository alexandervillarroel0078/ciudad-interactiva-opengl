package com.graphics.ciudad.mundo; // Agrupa lo que forma la ciudad: mapa, edificios, decoración y señales.

import com.graphics.ciudad.motor.Cubo; // Campo y cordón como cajas.
import com.graphics.ciudad.motor.Estadisticas; // Categoría de cada draw call (desglose de F5).
import com.graphics.ciudad.motor.Figuras; // Esfera, cilindro y cono de los árboles.
import java.util.ArrayList; // Lista de árboles del campo.
import java.util.Collections; // Publica la lista sin permitir modificarla.
import java.util.List; // Tipo de esa lista.

/**
 * ENTORNO: el campo que rodea la ciudad.
 * Responsable de: dibujar un pasto verde que se extiende ENTORNO_EXTRA unidades más allá de cada borde del mapa, un
 * cordón de vereda entre el asfalto y el campo, y algunos árboles dispersos afuera (los mismos pinos y frondosos de
 * los parques, dibujados con Parque.dibujarArbol).
 * POR QUÉ: sin entorno, desde la cámara orbital o la aérea se ve la ciudad "flotando" sobre el color de fondo. Con el
 * campo, el suelo sigue hasta el horizonte y el cielo (Cielo) aparece recién detrás.
 * SOLO DECORACIÓN: no cambia Mapa.LIMITE, ni las colisiones (Colisiones sigue deteniendo al auto en el borde), ni la
 * escala del minimapa. Juego no lo dibuja en el minimapa.
 * GEOMETRÍA SIN SUPERPOSICIONES: el pasto son 4 franjas (norte, sur, oeste y este) que rodean la base de asfalto sin
 * pisarla. Si hubiera una sola caja grande debajo de la ciudad, su cara superior quedaría casi en el mismo plano que el
 * asfalto y, de lejos, la profundidad no alcanzaría para separarlas: las dos superficies parpadearían (z-fighting).
 * ÁRBOLES DETERMINÍSTICOS: sus posiciones y medidas salen de Variacion.valor con una semilla fija (sin Random), así el
 * campo es siempre igual.
 * Se comunica con: Mapa (LIMITE), Parque (dibujo de los árboles), Variacion (hash), Cubo y Figuras (dibujo), Juego
 * (lo dibuja en la vista principal) y Sombras (proyecta la sombra de sus árboles).
 */
public class Entorno {

    // ==================== 1. CONSTANTES (valores ajustables) ====================
    public static final float ENTORNO_EXTRA = 100; // Cuánto se extiende el campo más allá de cada borde del mapa.
    public static final float BORDE_CAMPO = Mapa.LIMITE + ENTORNO_EXTRA; // 155: del origen al final del campo.
    public static final float ALTURA_CAMPO = 0; // Cara superior del pasto: al ras del asfalto (Y = 0).
    public static final float GROSOR_CAMPO = 0.5f; // Espesor de las franjas de pasto, igual que la base de asfalto.
    public static final float[] COLOR_CAMPO = {0.30f, 0.52f, 0.24f}; // Verde pasto, algo más claro que el césped de los parques.
    public static final float ANCHO_CORDON = 1.2f; // Ancho del cordón de vereda que separa la ciudad del campo.
    public static final float ALTO_CORDON = 0.3f; // Mismo alto que las aceras de las manzanas.
    public static final float[] COLOR_CORDON = {0.60f, 0.64f, 0.66f}; // Mismo gris que las aceras.
    public static final int CANTIDAD_ARBOLES = 36; // Árboles dispersos en el campo.
    public static final float DISTANCIA_MIN_ARBOL = 6; // Ningún árbol a menos de esto del borde del mapa (queda lugar para el cordón).
    public static final float ANCHO_FRANJA_ARBOLES = 50; // Los árboles se reparten entre DISTANCIA_MIN_ARBOL y esto más allá del borde.
    public static final int SEMILLA_ARBOLES = 211; // Cambiarla sortea otra disposición (siempre la misma para cada semilla).
    private static final int INTENTOS_POR_ARBOL = 50; // Sorteos máximos para ubicar un árbol en la franja (casi siempre alcanza con 1 o 2).

    // Árboles del campo, con el mismo formato y la misma escala que Parque.arboles() (Parque.arbol()).
    public static final List<float[]> ARBOLES = Collections.unmodifiableList(calcularArboles());

    // ==================== 2. ÁRBOLES: POSICIONES DETERMINÍSTICAS ====================

    /**
     * Sortea CANTIDAD_ARBOLES posiciones en el anillo que rodea la ciudad: un punto al azar (determinístico) dentro del
     * cuadrado exterior se acepta solo si queda afuera del cuadrado interior (el mapa más DISTANCIA_MIN_ARBOL). Es el
     * "método de rechazo": más simple que repartir por lados y reparte los árboles de forma pareja.
     */
    static List<float[]> calcularArboles() {
        List<float[]> lista = new ArrayList<>(); // Resultado.
        float interior = Mapa.LIMITE + DISTANCIA_MIN_ARBOL; // Cuadrado que queda libre: ciudad, cordón y un margen.
        float exterior = Mapa.LIMITE + ANCHO_FRANJA_ARBOLES; // Cuadrado donde terminan los árboles.
        for (int i = 0; i < CANTIDAD_ARBOLES; i++) { // Un árbol por vuelta.
            for (int intento = 0; intento < INTENTOS_POR_ARBOL; intento++) { // Sorteos hasta caer en la franja.
                float x = -exterior + 2 * exterior * Variacion.valor(i, intento, 0, SEMILLA_ARBOLES); // X entre -exterior y exterior.
                float z = -exterior + 2 * exterior * Variacion.valor(i, intento, 1, SEMILLA_ARBOLES); // Z en el mismo rango.
                if (Math.abs(x) < interior && Math.abs(z) < interior) { // Cayó dentro de la ciudad o pegado a ella.
                    continue; // Se rechaza y se sortea otro punto.
                }
                lista.add(arbol(i, x, z)); // Punto aceptado: se completan tipo y medidas.
                break; // Pasa al árbol siguiente.
            }
        }
        return lista; // Árboles del campo.
    }

    /** Tipo y medidas de un árbol del campo, con los mismos rangos que los de los parques. */
    private static float[] arbol(int i, float x, float z) {
        int tipo = Variacion.valor(i, 0, 2, SEMILLA_ARBOLES) < 0.5f ? Parque.PINO : Parque.FRONDOSO; // Mitad y mitad.
        float v = Variacion.valor(i, 0, 3, SEMILLA_ARBOLES); // Tamaño relativo del árbol, entre 0 y 1.
        float verde = Variacion.valor(i, 0, 4, SEMILLA_ARBOLES); // Tono de verde.
        float giro = (float) (2 * Math.PI * Variacion.valor(i, 0, 5, SEMILLA_ARBOLES)); // Orientación de la copa.
        return Parque.arbol(x, z, tipo, v, v, verde, giro); // Mismas alturas (5-7 y 5-6) que en los parques.
    }

    // ==================== 3. DIBUJO ====================

    private final Cubo cubo; // Pasto y cordón.
    private final Figuras figuras; // Árboles.

    /** Recibe la geometría compartida; los árboles ya están calculados en ARBOLES. */
    public Entorno(Cubo cubo, Figuras figuras) {
        this.cubo = cubo; // Guarda el cubo.
        this.figuras = figuras; // Guarda las figuras.
    }

    /** Dibuja el pasto, el cordón y los árboles del campo. */
    public void dibujar() {
        Estadisticas.fijarCategoria(Estadisticas.ARBOLES_PARQUES); // Campo, cordón y árboles de afuera.
        dibujarCampo(); // Cuatro franjas verdes.
        dibujarCordon(); // Borde gris alrededor del asfalto.
        for (float[] arbol : ARBOLES) { // Árboles dispersos.
            Parque.dibujarArbol(figuras, arbol, ALTURA_CAMPO); // Los mismos pinos y frondosos de los parques.
        }
    }

    /** Cuatro franjas de pasto que rodean el cuadrado de la ciudad sin superponerse con él ni entre sí. */
    private void dibujarCampo() {
        float l = Mapa.LIMITE; // Borde de la ciudad.
        float b = BORDE_CAMPO; // Borde del campo.
        float y = ALTURA_CAMPO - GROSOR_CAMPO / 2; // Centro de la caja: su cara superior queda en ALTURA_CAMPO.
        float[] c = COLOR_CAMPO; // Verde pasto.
        float largo = 2 * b; // Las franjas norte y sur cubren todo el ancho, esquinas incluidas.
        float ancho = b - l; // Ancho de cada franja: ENTORNO_EXTRA.
        float centro = (b + l) / 2; // Distancia del origen al centro de cada franja.
        cubo.caja(0, y, -centro, largo, GROSOR_CAMPO, ancho, c[0], c[1], c[2]); // Franja norte (Z negativa).
        cubo.caja(0, y, centro, largo, GROSOR_CAMPO, ancho, c[0], c[1], c[2]); // Franja sur.
        cubo.caja(-centro, y, 0, ancho, GROSOR_CAMPO, 2 * l, c[0], c[1], c[2]); // Franja oeste: solo el alto de la ciudad.
        cubo.caja(centro, y, 0, ancho, GROSOR_CAMPO, 2 * l, c[0], c[1], c[2]); // Franja este.
    }

    /** Cordón de vereda justo afuera del borde: el asfalto de la calle perimetral termina contra él, como en una calle real. */
    private void dibujarCordon() {
        float l = Mapa.LIMITE; // Borde de la ciudad.
        float centro = l + ANCHO_CORDON / 2; // El cordón empieza exactamente en el borde, hacia afuera.
        float largo = 2 * (l + ANCHO_CORDON); // Cubre también las esquinas.
        float y = ALTURA_CAMPO + ALTO_CORDON / 2; // Apoyado sobre el pasto.
        float[] c = COLOR_CORDON; // Gris de acera.
        cubo.caja(0, y, -centro, largo, ALTO_CORDON, ANCHO_CORDON, c[0], c[1], c[2]); // Cordón norte.
        cubo.caja(0, y, centro, largo, ALTO_CORDON, ANCHO_CORDON, c[0], c[1], c[2]); // Cordón sur.
        cubo.caja(-centro, y, 0, ANCHO_CORDON, ALTO_CORDON, 2 * l, c[0], c[1], c[2]); // Cordón oeste.
        cubo.caja(centro, y, 0, ANCHO_CORDON, ALTO_CORDON, 2 * l, c[0], c[1], c[2]); // Cordón este.
    }
}
