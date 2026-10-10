package com.graphics.ciudad.vehiculo; // Agrupa el vehículo del jugador y sus colisiones.

import com.graphics.ciudad.motor.Camara; // Envía al shader de las marcas la misma cámara que la ciudad.
import com.graphics.ciudad.motor.Malla; // Rectángulo plano que se reutiliza para cada tramo.
import com.graphics.ciudad.motor.Shader; // Programa marcas.vert / marcas.frag.
import com.graphics.ciudad.mundo.Mapa; // Dice si un punto está sobre una calle.
import static org.lwjgl.opengl.GL33.*; // Importa las funciones OpenGL hasta la versión 3.3.

/**
 * MARCAS DE FRENADO: las huellas de goma que deja el auto del jugador al frenar fuerte o derrapar.
 *
 * BUFFER CIRCULAR: MAX_SEGMENTOS segmentos en arreglos reservados una sola vez (no se crea ningún objeto por cuadro).
 * Cada segmento es una muestra de ambas ruedas traseras: punto izquierdo (xI, zI), punto derecho (xD, zD), ancho y vida.
 * Un segmento con "continua" activo se une al anterior: el tramo dibujado es el rectángulo que va del punto anterior al
 * actual en cada rueda. El primer segmento de una tira no se une a nada, así al cortarse la tira (la condición termina)
 * queda un extremo limpio. Cuando el buffer se llena, el segmento nuevo pisa al más viejo.
 *
 * CUÁNDO SE AGREGA UN SEGMENTO (actualizar): solo si el auto va rápido Y (frena con el freno pulsado O gira cerrado, derrape),
 * si las dos ruedas están sobre una calle (no sobre pasto, aceras ni parques) y si avanzó al menos DISTANCIA_MINIMA desde
 * el último segmento. Si la condición termina, el auto sale de la calle o salta (reinicio), la tira se corta.
 * Todo avanza con dt: con dt = 0 (pausa) no cambia nada. La vida baja con dt y la opacidad sigue a la vida.
 *
 * DIBUJO: rectángulos planos (Malla) a ALTURA sobre el asfalto (por encima de la pintura vial, que llega a 0.04, para
 * evitar z-fighting), con mezcla alfa y glDepthMask(false); el estado de OpenGL se restaura al terminar. El color es
 * oscuro y sin iluminación: no brilla de noche. La cámara y la relación ancho/alto se envían en cada cuadro, así que
 * funciona igual al redimensionar. Solo se dibuja en la vista principal.
 * Se comunica con: Auto (lee su estado), Juego (actualiza, vacía, dibuja), Mapa, Camara, Shader y Malla.
 * La lógica no usa OpenGL, así que se prueba sin ventana; solo crear(), eliminar() y dibujar() lo necesitan.
 */
public class MarcasFrenado {

    // ==================== 1. CONSTANTES (valores ajustables) ====================
    public static final int MAX_SEGMENTOS = 600; // Tamaño fijo del buffer circular.
    public static final float VELOCIDAD_FRENADO = 8; // Por debajo de esto, frenar no deja marca (unidades/s; el máximo es 16).
    public static final float VELOCIDAD_DERRAPE = 12; // Giro cerrado a esta velocidad o más = derrape.
    public static final float DIRECCION_DERRAPE = 0.8f * Rueda.ANGULO_MAX_DIRECCION; // Ruedas casi a fondo (80 % del giro máximo).
    public static final float DISTANCIA_MINIMA = 0.4f; // Lo que debe avanzar el auto para agregar otro segmento.
    public static final float DISTANCIA_MAXIMA = 3f; // Si avanzó más (salto, reinicio), empieza una tira nueva.
    public static final float ANCHO_FRENADO = 0.20f; // Ancho de la huella al frenar (el neumático mide 0.24).
    public static final float ANCHO_DERRAPE = 0.26f; // Un poco más ancha al derrapar.
    public static final float VIDA = 8; // Segundos que dura un segmento.
    public static final float ALFA_MAXIMA = 0.55f; // Opacidad de un segmento recién hecho.
    public static final float ALTURA = 0.05f; // Sobre el asfalto (Y = 0) y la pintura vial (hasta 0.04); bajo las sombras (0.06).
    private static final float SUPERPOSICION = 0.03f; // Cada tramo se alarga un poco: no quedan rendijas en las curvas.
    private static final float[] COLOR = {0.02f, 0.02f, 0.025f}; // Goma casi negra.
    private static final String SHADER_VERTICES = "/shaders/marcas.vert"; // Rectángulo plano en el mundo.
    private static final String SHADER_FRAGMENTOS = "/shaders/marcas.frag"; // Color oscuro con alfa.

    // ==================== 2. ESTADO DEL BUFFER ====================
    private final float[] xIzq = new float[MAX_SEGMENTOS]; // Rueda trasera izquierda.
    private final float[] zIzq = new float[MAX_SEGMENTOS];
    private final float[] xDer = new float[MAX_SEGMENTOS]; // Rueda trasera derecha.
    private final float[] zDer = new float[MAX_SEGMENTOS];
    private final float[] ancho = new float[MAX_SEGMENTOS];
    private final float[] vida = new float[MAX_SEGMENTOS];
    private final boolean[] continua = new boolean[MAX_SEGMENTOS]; // true: se une al segmento anterior del buffer.
    private int siguiente = 0; // Posición donde se escribirá el próximo segmento.
    private int cantidad = 0; // Segmentos vivos: ocupan las posiciones anteriores a "siguiente" (con vuelta).
    private boolean enTira = false; // true mientras el último segmento puede continuarse.

    // ==================== 3. RECURSOS DE GPU ====================
    private final Shader shader = new Shader(); // Programa propio.
    private final Malla rectangulo = new Malla(shader, rectanguloUnitario()); // Rectángulo plano unitario.

    /** Rectángulo en el plano XZ, de -0.5 a 0.5, con el formato de Malla (posición y normal hacia arriba). */
    private static float[] rectanguloUnitario() {
        float[][] esquinas = {{-0.5f, -0.5f}, {0.5f, -0.5f}, {0.5f, 0.5f}, {-0.5f, -0.5f}, {0.5f, 0.5f}, {-0.5f, 0.5f}};
        float[] v = new float[esquinas.length * Malla.FLOATS_POR_VERTICE];
        for (int i = 0; i < esquinas.length; i++) {
            int k = i * Malla.FLOATS_POR_VERTICE;
            v[k] = esquinas[i][0]; // X.
            v[k + 2] = esquinas[i][1]; // Z (Y queda en 0).
            v[k + 4] = 1; // Normal +Y.
        }
        return v;
    }

    /** Compila el shader y sube el rectángulo a la GPU (necesita el contexto OpenGL). */
    public void crear() {
        shader.crear(SHADER_VERTICES, SHADER_FRAGMENTOS);
        rectangulo.crear();
    }

    /** Libera el programa y el rectángulo si llegaron a crearse. */
    public void eliminar() {
        rectangulo.eliminar();
        shader.eliminar();
    }

    // ==================== 4. ACTUALIZACIÓN ====================

    /** Atajo: toma el estado del auto del jugador. */
    public void actualizar(float dt, Auto auto) {
        actualizar(dt, auto.getX(), auto.getZ(), auto.getAngulo(), auto.getVelocidad(), auto.frenando(), auto.getAnguloDireccion());
    }

    /**
     * Envejece las marcas y, si el auto frena fuerte o derrapa, agrega un segmento.
     * @param frenando true con el freno pulsado.
     * @param direccion giro de las ruedas delanteras en radianes (positivo = izquierda).
     */
    public void actualizar(float dt, float x, float z, float angulo, float velocidad, boolean frenando, float direccion) {
        if (dt <= 0) { // Pausa o menú: nada cambia.
            return;
        }
        envejecer(dt);
        float rapidez = Math.abs(velocidad);
        boolean frenaFuerte = frenando && rapidez >= VELOCIDAD_FRENADO;
        boolean derrapa = rapidez >= VELOCIDAD_DERRAPE && Math.abs(direccion) >= DIRECCION_DERRAPE;
        if (!frenaFuerte && !derrapa) { // La condición terminó: se corta la tira.
            enTira = false;
            return;
        }
        float seno = (float) Math.sin(angulo);
        float coseno = (float) Math.cos(angulo);
        float xi = x + coseno * -Rueda.LADO_RUEDA + seno * Rueda.EJE_RUEDA; // Ruedas traseras: misma transformación que Rueda.dibujar().
        float zi = z - seno * -Rueda.LADO_RUEDA + coseno * Rueda.EJE_RUEDA;
        float xd = x + coseno * Rueda.LADO_RUEDA + seno * Rueda.EJE_RUEDA;
        float zd = z - seno * Rueda.LADO_RUEDA + coseno * Rueda.EJE_RUEDA;
        if (!Mapa.esCalleEn(xi, zi) || !Mapa.esCalleEn(xd, zd)) { // Solo sobre calles: no pasto, aceras ni parques.
            enTira = false;
            return;
        }
        boolean une = enTira; // ¿Hay un segmento anterior con el que unirse?
        if (enTira) {
            int ultimo = (siguiente - 1 + MAX_SEGMENTOS) % MAX_SEGMENTOS;
            float dx = xi - xIzq[ultimo];
            float dz = zi - zIzq[ultimo];
            float distancia = (float) Math.sqrt(dx * dx + dz * dz);
            if (distancia < DISTANCIA_MINIMA) { // Todavía no avanzó lo suficiente.
                return;
            }
            if (distancia > DISTANCIA_MAXIMA) { // Saltó (reinicio): empieza una tira nueva.
                une = false;
            }
        }
        agregar(xi, zi, xd, zd, derrapa && !frenaFuerte ? ANCHO_DERRAPE : ANCHO_FRENADO, une);
        enTira = true;
    }

    /** Escribe un segmento en "siguiente"; si el buffer está lleno, pisa al más viejo (queda reciclado). */
    private void agregar(float xi, float zi, float xd, float zd, float anchoMarca, boolean une) {
        if (cantidad == MAX_SEGMENTOS) { // Se pisa el más viejo: el que sigue pierde a su anterior.
            continua[(siguiente + 1) % MAX_SEGMENTOS] = false;
        } else {
            cantidad++;
        }
        xIzq[siguiente] = xi;
        zIzq[siguiente] = zi;
        xDer[siguiente] = xd;
        zDer[siguiente] = zd;
        ancho[siguiente] = anchoMarca;
        vida[siguiente] = VIDA;
        continua[siguiente] = une;
        siguiente = (siguiente + 1) % MAX_SEGMENTOS;
    }

    /** Baja la vida de todos los segmentos; los viejos (los primeros) que llegan a cero se liberan. */
    private void envejecer(float dt) {
        int primero = (siguiente - cantidad + MAX_SEGMENTOS) % MAX_SEGMENTOS;
        for (int k = 0; k < cantidad; k++) {
            vida[(primero + k) % MAX_SEGMENTOS] -= dt;
        }
        while (cantidad > 0 && vida[(siguiente - cantidad + MAX_SEGMENTOS) % MAX_SEGMENTOS] <= 0) { // Nacen en orden: mueren en orden.
            cantidad--;
            if (cantidad > 0) { // El nuevo más viejo ya no tiene anterior.
                continua[(siguiente - cantidad + MAX_SEGMENTOS) % MAX_SEGMENTOS] = false;
            }
        }
        if (cantidad == 0) {
            enTira = false;
        }
    }

    /** Deja el buffer vacío y corta la tira (reinicio con R). */
    public void vaciar() {
        cantidad = 0;
        siguiente = 0;
        enTira = false;
    }

    // ==================== 5. DIBUJO ====================

    /** Opacidad de un segmento con la vida que le queda: máxima al nacer, se desvanece en la segunda mitad de su vida. */
    public static float alfaDeVida(float vidaRestante) {
        return ALFA_MAXIMA * Math.max(0, Math.min(1, 2 * vidaRestante / VIDA));
    }

    /**
     * Dibuja todas las marcas; debe llamarse después de la escena y antes del HUD. Deja activo el programa de las marcas:
     * el que llama debe volver a activar su shader y su cubo (Shader.usar, Cubo.enlazar).
     */
    public void dibujar(Camara camara, float autoX, float autoZ, float angulo, int ancho, int alto) {
        if (cantidad < 2) { // Nada que unir: no toca el estado de OpenGL.
            return;
        }
        boolean habiaMezcla = glIsEnabled(GL_BLEND); // Estado previo, para restaurarlo igual.
        boolean habiaCaras = glIsEnabled(GL_CULL_FACE);
        boolean escribiaProfundidad = glGetBoolean(GL_DEPTH_WRITEMASK);
        int origen = glGetInteger(GL_BLEND_SRC_ALPHA);
        int destino = glGetInteger(GL_BLEND_DST_ALPHA);
        glDepthMask(false); // No escriben profundidad: las marcas no tapan nada ni se tapan entre sí.
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_CULL_FACE);
        try { // Asegura que los estados vuelvan aunque algo falle.
            shader.usar();
            camara.configurar(shader, autoX, autoZ, angulo, ancho, alto); // Cámara y relación ancho/alto actuales.
            int primero = (siguiente - cantidad + MAX_SEGMENTOS) % MAX_SEGMENTOS;
            for (int k = 1; k < cantidad; k++) { // El primero no tiene anterior.
                int i = (primero + k) % MAX_SEGMENTOS;
                if (!continua[i]) {
                    continue;
                }
                int anterior = (i - 1 + MAX_SEGMENTOS) % MAX_SEGMENTOS;
                shader.decimal("uAlfaMarca", alfaDeVida(vida[i]));
                tramo(xIzq[anterior], zIzq[anterior], xIzq[i], zIzq[i], this.ancho[i]);
                tramo(xDer[anterior], zDer[anterior], xDer[i], zDer[i], this.ancho[i]);
            }
        } finally {
            glBlendFunc(origen, destino);
            if (!habiaMezcla) {
                glDisable(GL_BLEND);
            }
            if (habiaCaras) {
                glEnable(GL_CULL_FACE);
            }
            glDepthMask(escribiaProfundidad);
            glBindVertexArray(0);
        }
    }

    /** Dibuja el rectángulo que va del punto (x0, z0) al (x1, z1): centrado entre ambos, girado hacia el segundo. */
    private void tramo(float x0, float z0, float x1, float z1, float anchoMarca) {
        float dx = x1 - x0;
        float dz = z1 - z0;
        float largo = (float) Math.sqrt(dx * dx + dz * dz);
        if (largo < 1e-4f) {
            return;
        }
        float giro = (float) Math.atan2(dx, dz); // El eje Z local del rectángulo queda a lo largo de (dx, dz).
        rectangulo.dibujarGirada((x0 + x1) / 2, ALTURA, (z0 + z1) / 2, anchoMarca, 1, largo + SUPERPOSICION,
            COLOR[0], COLOR[1], COLOR[2], giro);
    }

    // ==================== 6. CONSULTAS (también para las pruebas) ====================

    /** Segmentos vivos. */
    public int cantidad() {
        return cantidad;
    }

    /** Posición en el buffer del segmento k-ésimo, contando desde el más viejo (0) hasta el más nuevo (cantidad - 1). */
    private int indice(int k) {
        return (siguiente - cantidad + k + MAX_SEGMENTOS) % MAX_SEGMENTOS;
    }

    /** Vida restante del segmento k (0 = el más viejo). */
    public float vida(int k) {
        return vida[indice(k)];
    }

    /** Opacidad actual del segmento k. */
    public float alfa(int k) {
        return alfaDeVida(vida[indice(k)]);
    }

    /** X de la rueda izquierda del segmento k. */
    public float xIzquierda(int k) {
        return xIzq[indice(k)];
    }

    /** Z de la rueda izquierda del segmento k. */
    public float zIzquierda(int k) {
        return zIzq[indice(k)];
    }

    /** true si el segmento k se une al anterior. */
    public boolean continua(int k) {
        return continua[indice(k)];
    }
}
