package com.graphics.ciudad.mundo; // Agrupa lo que forma la ciudad: mapa, edificios, decoración y semáforos.

import com.graphics.ciudad.motor.Cubo; // Dibuja la caja y las viseras (piezas planas).
import com.graphics.ciudad.motor.Figuras; // Cilindros de la base, el poste y las lentes.
import com.graphics.ciudad.motor.Shader; // Activa la emisión de la lente encendida y la rotación de cada pieza.
import java.util.ArrayList; // Lista de piezas del modelo.
import java.util.List; // Tipo de esa lista.

/**
 * SEMAFORO: cabezal de semáforo animado (base, poste, caja, tres lentes redondas y sus viseras).
 * Responsable de: calcular qué lente está encendida según el reloj global del juego (ciclo de CICLO = 14 segundos,
 * con duraciones en constantes), coordinar los dos grupos de accesos de una intersección y dibujar un cabezal
 * orientado hacia los autos que se acercan. Para el JUGADOR los semáforos son solo visuales: no se detiene en rojo. El
 * tráfico NPC sí los respeta: Vehiculo.huecoSemaforo() consulta luzParaAcceso() con el reloj global y frena ante la línea
 * de detención con rojo (o amarillo, si aún alcanza a frenar con comodidad). Esta clase no cambia por eso.
 * Se comunica con: Senalizacion (ubica un cabezal por acceso en las intersecciones del Centro y le pasa el reloj
 * global de Juego), Cubo y Figuras (dibujan) y Shader (uEmision hace que la lente activa se vea encendida y
 * uRotacion pone de frente las lentes e inclina las viseras).
 *
 * COORDINACIÓN: los accesos opuestos (norte y sur, o este y oeste) muestran siempre el mismo color, y los dos grupos
 * perpendiculares se alternan. Para eso el rojo dura exactamente lo mismo que verde + amarillo, y el grupo este-oeste
 * usa el mismo ciclo desfasado DURACION_ROJO segundos: mientras norte-sur está en rojo, este-oeste recorre verde y
 * amarillo, y al revés. Nunca hay verde o amarillo en los dos grupos a la vez.
 *
 * MODELO (mismo estilo que las farolas de Iluminacion): modelo(activa) arma la lista de piezas sin OpenGL, así
 * SemaforoTest puede revisarla, y dibujar() solo la recorre. Las piezas están en coordenadas LOCALES del cabezal:
 * X a lo ancho, Y altura sobre el suelo y Z hacia atrás; el frente (las lentes) está en Z negativa y mira al tráfico.
 * La parte de atrás de la caja queda lisa, como en un semáforo real: nada sobresale por Z positiva.
 */
public class Semaforo {

    private final Shader shader; // Programa que recibe el interruptor de emisión y la rotación.
    private final Cubo cubo; // Caja y viseras.
    private final Figuras figuras; // Cilindros de base, poste y lentes.

    // Duraciones del ciclo rojo → verde → amarillo, en segundos. Cambiarlas aquí ajusta todos los semáforos.
    public static final float DURACION_VERDE = 5; // Segundos con la luz verde encendida.
    public static final float DURACION_AMARILLO = 2; // Segundos con la luz amarilla encendida.
    public static final float DURACION_ROJO = DURACION_VERDE + DURACION_AMARILLO; // Segundos con la luz roja: 7, lo que dura el paso del otro grupo.
    public static final float CICLO = DURACION_ROJO + DURACION_VERDE + DURACION_AMARILLO; // Duración de una vuelta completa: 14 s.
    public static final float DESFASE_ESTE_OESTE = DURACION_ROJO; // El grupo este-oeste va adelantado un rojo completo.
    public static final float BRILLO_APAGADA = 0.15f; // Las lentes apagadas usan su color multiplicado por esto: muy oscuras, pero se distingue cuál es cuál.
    public static final int ROJA = 0; // Índice de la lente roja (arriba).
    public static final int AMARILLA = 1; // Índice de la lente amarilla (centro).
    public static final int VERDE = 2; // Índice de la lente verde (abajo).

    // ==================== FORMA DEL CABEZAL (valores ajustables) ====================
    private static final float ALTURA_ACERA = 0.3f; // El semáforo se apoya sobre la acera, que Ciudad dibuja con 0.3 de alto.
    // Base y poste: cilindros, como las farolas (base más ancha, a modo de zócalo).
    public static final float ANCHO_BASE = 0.34f; // Diámetro de la base.
    public static final float ALTO_BASE = 0.35f; // Alto de la base sobre la acera.
    public static final float ANCHO_POSTE = 0.14f; // Diámetro del poste.
    // Caja: angosta y alta, apoyada sobre el poste.
    public static final float ANCHO_CAJA = 0.42f; // Ancho de la caja (X local).
    public static final float ALTO_CAJA = 1.3f; // Alto de la caja: entran las tres lentes una sobre otra.
    public static final float PROFUNDIDAD_CAJA = 0.34f; // Profundidad (Z local): del frente con lentes a la espalda lisa.
    public static final float BASE_CAJA = 2.5f; // Altura del borde inferior de la caja: por encima del techo de los autos.
    // Lentes: cilindros cortos acostados, con la cara redonda hacia el tráfico.
    public static final float DIAMETRO_LENTE = 0.3f; // Diámetro de cada lente.
    public static final float GROSOR_LENTE = 0.05f; // Cuánto sobresale cada lente del frente de la caja.
    public static final float SEPARACION_LENTES = 0.38f; // Distancia vertical entre los centros de dos lentes vecinas.
    // Viseras: placa fina sobre cada lente, inclinada hacia abajo, que le da sombra (en la realidad evita que el sol
    // la haga parecer encendida).
    public static final float LARGO_VISERA = 0.22f; // Cuánto sale la visera hacia adelante.
    public static final float GROSOR_VISERA = 0.025f; // Espesor de la placa.
    public static final float EXCESO_VISERA = 0.06f; // La visera es esto más ancha que la lente (repartido a los dos lados).
    public static final float HOLGURA_VISERA = 0.03f; // Espacio entre el borde superior de la lente y la visera.
    public static final float INCLINACION_VISERA = 20; // Grados que baja la punta de la visera respecto de la horizontal.
    // Colores.
    public static final float[] COLOR_BASE = {0.14f, 0.15f, 0.17f}; // Gris oscuro, como la base de las farolas.
    public static final float[] COLOR_POSTE = {0.20f, 0.22f, 0.24f}; // Gris oscuro.
    public static final float[] COLOR_CAJA = {0.05f, 0.06f, 0.07f}; // Casi negro: hace resaltar las lentes.
    public static final float[][] COLORES_LENTE = { // Color intenso de cada lente encendida, en el orden ROJA, AMARILLA, VERDE.
        {1.0f, 0.10f, 0.06f}, // Rojo.
        {1.0f, 0.72f, 0.05f}, // Ámbar.
        {0.10f, 1.0f, 0.40f} // Verde azulado, como los LED reales.
    };

    // Rotaciones para uRotacion (matriz 3 × 3 columna por columna: adónde va cada eje local X, Y, Z).
    // Lente: el eje del cilindro (Y) pasa a apuntar a -Z, hacia el tráfico; Z pasa a Y. Es un giro de 90° sobre X.
    static final float[] GIRO_LENTE = {1, 0, 0, 0, 0, -1, 0, 1, 0};
    // Visera: giro de INCLINACION_VISERA sobre X, con la punta delantera (Z negativa) hacia abajo.
    static final float[] GIRO_VISERA = giroSobreX((float) Math.toRadians(INCLINACION_VISERA));

    /** Recibe el shader, el cubo y las figuras compartidas. */
    public Semaforo(Shader shader, Cubo cubo, Figuras figuras) {
        this.shader = shader; // Guarda el programa para cambiar uEmision y uRotacion.
        this.cubo = cubo; // Guarda la geometría de cajas.
        this.figuras = figuras; // Guarda las mallas redondeadas.
    }

    /** Devuelve qué bombilla está encendida en un instante: ROJA, luego VERDE y por último AMARILLA, en bucle. */
    public static int luzActiva(float tiempo) {
        float fase = tiempo % CICLO; // Repite un ciclo de segundos comprendidos entre 0 y CICLO.
        if (fase < DURACION_ROJO) { // Primer tramo del ciclo: selecciona la bombilla roja.
            return ROJA; // Mantiene rojo durante los primeros DURACION_ROJO segundos.
        }
        if (fase < DURACION_ROJO + DURACION_VERDE) { // Segundo tramo del ciclo: selecciona la bombilla verde.
            return VERDE; // Mantiene verde durante los DURACION_VERDE segundos intermedios.
        }
        return AMARILLA; // Último tramo: selecciona la bombilla amarilla. Mantiene amarillo en los últimos DURACION_AMARILLO segundos del ciclo.
    }

    /** Luz de un acceso: el grupo norte-sur usa el ciclo tal cual; el grupo este-oeste, desfasado DESFASE_ESTE_OESTE. */
    public static int luzParaAcceso(float tiempo, boolean grupoNorteSur) {
        if (grupoNorteSur) { // Accesos que llegan desde el norte o desde el sur.
            return luzActiva(tiempo); // Ciclo base.
        }
        return luzActiva(tiempo + DESFASE_ESTE_OESTE); // Accesos este y oeste: complementarios del grupo norte-sur.
    }

    // ==================== MODELO DEL CABEZAL ====================

    /** Qué parte del semáforo es una pieza; SemaforoTest busca las LENTE. */
    public enum Parte { BASE, POSTE, CAJA, LENTE, VISERA }

    /** Figura con la que se dibuja una pieza. */
    public enum Forma { CAJA, CILINDRO }

    /**
     * PIEZA: una figura en coordenadas locales del cabezal. (x, y, z) es su centro, (sx, sy, sz) su tamaño antes de
     * rotar, rotacion la matriz uRotacion y luz el índice de color (ROJA, AMARILLA o VERDE) si es una lente, o -1.
     */
    public static final class Pieza {
        public final Parte parte; // Base, poste, caja, lente o visera.
        public final Forma forma; // Caja o cilindro.
        public final float x, y, z; // Centro en coordenadas locales.
        public final float sx, sy, sz; // Tamaño en cada eje, antes de rotar.
        public final float[] color; // RGB entre 0 y 1.
        public final float[] rotacion; // Matriz 3 × 3 por columnas para uRotacion.
        public final boolean emisiva; // true = conserva su color sin iluminación (la lente encendida).
        public final int luz; // Índice de color de la lente, o -1 si la pieza no es una lente.

        Pieza(Parte parte, Forma forma, float x, float y, float z, float sx, float sy, float sz,
              float[] color, float[] rotacion, boolean emisiva, int luz) {
            this.parte = parte; // Parte del semáforo.
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
            this.luz = luz; // Color de lente.
        }
    }

    /** Altura del centro de la lente indicada: roja arriba, amarilla al medio y verde abajo. */
    public static float alturaLente(int indice) {
        float centroCaja = BASE_CAJA + ALTO_CAJA / 2; // La amarilla va en el centro de la caja.
        return centroCaja + (AMARILLA - indice) * SEPARACION_LENTES; // ROJA (0) sube, VERDE (2) baja.
    }

    /** Arma las piezas de un cabezal con la lente "activa" encendida, de abajo hacia arriba y de atrás hacia adelante. */
    public static List<Pieza> modelo(int activa) {
        List<Pieza> piezas = new ArrayList<>(); // Resultado.
        float[] recto = Shader.IDENTIDAD_3X3; // Piezas sin rotación extra.

        // Base: cilindro corto y ancho sobre la acera.
        piezas.add(new Pieza(Parte.BASE, Forma.CILINDRO, 0, ALTURA_ACERA + ALTO_BASE / 2, 0,
            ANCHO_BASE, ALTO_BASE, ANCHO_BASE, COLOR_BASE, recto, false, -1));
        // Poste: de la base hasta el fondo de la caja.
        float pie = ALTURA_ACERA + ALTO_BASE; // Donde termina la base.
        piezas.add(new Pieza(Parte.POSTE, Forma.CILINDRO, 0, (pie + BASE_CAJA) / 2, 0,
            ANCHO_POSTE, BASE_CAJA - pie, ANCHO_POSTE, COLOR_POSTE, recto, false, -1));
        // Caja: centrada sobre el poste; su espalda (Z positiva) queda lisa.
        piezas.add(new Pieza(Parte.CAJA, Forma.CAJA, 0, BASE_CAJA + ALTO_CAJA / 2, 0,
            ANCHO_CAJA, ALTO_CAJA, PROFUNDIDAD_CAJA, COLOR_CAJA, recto, false, -1));

        float frente = -PROFUNDIDAD_CAJA / 2; // Plano del frente de la caja (Z local negativa = hacia el tráfico).
        float seno = (float) Math.sin(Math.toRadians(INCLINACION_VISERA)); // Cuánto baja la visera por unidad de largo.
        float coseno = (float) Math.cos(Math.toRadians(INCLINACION_VISERA)); // Cuánto avanza por unidad de largo.
        for (int indice = ROJA; indice <= VERDE; indice++) { // Rojo arriba, amarillo al centro y verde abajo.
            float altura = alturaLente(indice); // Centro de esta lente.
            // Lente: la encendida usa su color intenso y es emisiva; las apagadas, el mismo color muy oscuro y reciben
            // la iluminación normal. Así siempre se ve que hay tres lentes y cuál corresponde a cada color.
            boolean encendida = indice == activa;
            float[] base = COLORES_LENTE[indice];
            float[] color = encendida ? base
                : new float[] {base[0] * BRILLO_APAGADA, base[1] * BRILLO_APAGADA, base[2] * BRILLO_APAGADA};
            piezas.add(new Pieza(Parte.LENTE, Forma.CILINDRO, 0, altura, frente - GROSOR_LENTE / 2,
                DIAMETRO_LENTE, GROSOR_LENTE, DIAMETRO_LENTE, color, GIRO_LENTE, encendida, indice));
            // Visera: su borde trasero toca el frente de la caja, justo encima de la lente, y la punta baja hacia adelante.
            float arriba = altura + DIAMETRO_LENTE / 2 + HOLGURA_VISERA; // Borde trasero de la visera.
            piezas.add(new Pieza(Parte.VISERA, Forma.CAJA, 0, arriba - LARGO_VISERA / 2 * seno, frente - LARGO_VISERA / 2 * coseno,
                DIAMETRO_LENTE + EXCESO_VISERA, GROSOR_VISERA, LARGO_VISERA, COLOR_CAJA, GIRO_VISERA, false, -1));
        }
        return piezas; // Cabezal completo.
    }

    /**
     * Giro de "angulo" radianes sobre el eje X que baja la punta delantera (Z negativa). Columnas: X queda igual,
     * Y = (0, cos, -sen) y Z = (0, sen, cos); un punto en Z = -L queda a altura -L · sen, más abajo que la espalda.
     */
    static float[] giroSobreX(float angulo) {
        float c = (float) Math.cos(angulo); // Coseno del giro.
        float s = (float) Math.sin(angulo); // Seno del giro.
        return new float[] {1, 0, 0, 0, c, -s, 0, s, c}; // Tres columnas.
    }

    /**
     * Dibuja un cabezal en (x, z) con la lente "activa" encendida.
     * angulo orienta el frente del cabezal (el lado de las lentes, -Z local) hacia los autos del acceso, con la
     * misma convención que Auto: el frente mira hacia (-sen(angulo), -cos(angulo)).
     */
    public void dibujar(float x, float z, float angulo, int activa) {
        float coseno = (float) Math.cos(angulo); // Coseno de la orientación.
        float seno = (float) Math.sin(angulo); // Seno de la orientación.
        for (Pieza p : modelo(activa)) { // Recorre las piezas.
            float mundoX = x + coseno * p.x + seno * p.z; // Misma transformación que Senalizacion.pieza(): gira y traslada.
            float mundoZ = z - seno * p.x + coseno * p.z; // Posición Z en la ciudad.
            shader.matriz3("uRotacion", p.rotacion); // Pone de frente las lentes e inclina las viseras; identidad para lo demás.
            shader.entero("uEmision", p.emisiva ? 1 : 0); // Solo la lente encendida ignora la iluminación.
            if (p.forma == Forma.CILINDRO) { // Base, poste y lentes.
                figuras.cilindro.dibujarGirada(mundoX, p.y, mundoZ, p.sx, p.sy, p.sz, p.color[0], p.color[1], p.color[2], angulo);
            } else { // Caja y viseras.
                cubo.cajaGirada(mundoX, p.y, mundoZ, p.sx, p.sy, p.sz, p.color[0], p.color[1], p.color[2], angulo);
            }
        }
        shader.matriz3("uRotacion", Shader.IDENTIDAD_3X3); // Restablece la rotación para el siguiente objeto.
        shader.entero("uEmision", 0); // Evita que el siguiente objeto herede la emisión del semáforo.
    }
}
