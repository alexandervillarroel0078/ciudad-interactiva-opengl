package com.graphics.ciudad.iluminacion; // Agrupa el estado de las luces de la escena.

import com.graphics.ciudad.motor.Camara; // Envía al shader del cielo la misma cámara que la ciudad.
import com.graphics.ciudad.motor.Cubo; // Geometría del cielo: el mismo cubo unitario de todo el proyecto.
import com.graphics.ciudad.motor.Shader; // Programa cielo.vert / cielo.frag.
import static org.lwjgl.opengl.GL33.*; // glDepthMask, glEnable, glDisable.

/**
 * CIELO: fondo procedural calculado por completo en un shader.
 * Responsable de: dibujar un CUBO grande centrado en la cámara con su propio programa (cielo.vert + cielo.frag). El
 * fragment shader calcula por píxel, a partir de la dirección de la mirada, el degradado horizonte-cénit (día,
 * atardecer y noche), el sol y la luna con halo, las estrellas (hash 3D, solo de noche) y las nubes (fbm).
 *
 * POR QUÉ UN CUBO CENTRADO EN LA CÁMARA: el cielo está "infinitamente lejos": al mover la cámara no debe acercarse ni
 * alejarse. Si el cubo se mueve con el ojo, cada dirección de la vista siempre ve el mismo color. El shader no usa la
 * posición del cubo sino su dirección, así que da igual que sea un cubo y no una esfera.
 *
 * POR QUÉ SE DIBUJA PRIMERO, SIN PROFUNDIDAD NI CARAS OCULTAS:
 *  - glDepthMask(false) y glDisable(GL_DEPTH_TEST): el cielo es el fondo y no debe escribir ni probar profundidad; el
 *    depth buffer queda "vacío" y la ciudad que se dibuja después pasa siempre la prueba y lo pinta encima.
 *  - glDisable(GL_CULL_FACE): la cámara está DENTRO del cubo, así que lo que se ve son sus caras interiores.
 *  - Los tres estados se restauran a como estaban al terminar (aunque algo falle).
 * El cubo debe quedar dentro del plano lejano (Camara.getPlanoLejano(), ≈ 319): su esquina está a LADO_CUBO · √3 / 2.
 * No se dibuja en el minimapa (vista desde arriba: no hay cielo).
 * Se comunica con: Juego (lo crea, lo dibuja al empezar cada cuadro y lo libera), Iluminacion (dirección del sol y
 * factor de noche), Camara, Shader, Cubo y el shader cielo.frag.
 */
public class Cielo {

    // ==================== 1. CONSTANTES (valores ajustables) ====================
    private static final String SHADER_VERTICES = "/shaders/cielo.vert"; // Proyección del cubo del cielo.
    private static final String SHADER_FRAGMENTOS = "/shaders/cielo.frag"; // Todo el cielo procedural.
    // Radio de referencia del cielo: debe quedar dentro del plano lejano (Camara.getPlanoLejano() ≈ 319) o el recorte lejano lo cortaría.
    public static final float RADIO_CIELO = 250;
    // Lado del cubo: su esquina queda a LADO_CUBO · √3 / 2 ≈ 260, por debajo del plano lejano (≈ 319).
    public static final float LADO_CUBO = 1.2f * RADIO_CIELO;
    public static final float[] DIRECCION_LUNA = {0.45f, 0.32f, -0.83f}; // Hacia el norte-noreste, ≈ 19° sobre el horizonte (se normaliza).

    /** Dirección unitaria de la luna. */
    public static float[] direccionLuna() {
        float[] d = DIRECCION_LUNA; // Nombre corto.
        float largo = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]); // Largo para normalizar.
        return new float[] {d[0] / largo, d[1] / largo, d[2] / largo}; // Vector de largo 1.
    }

    // ==================== 2. RECURSOS DE GPU ====================

    private final Shader shader = new Shader(); // Programa propio del cielo: distinto del de la ciudad.
    private final Cubo cubo = new Cubo(shader); // Cubo unitario que recibe uPos/uEscala de este programa.

    /** Compila el shader del cielo y sube su cubo a la GPU (necesita el contexto OpenGL de la ventana). */
    public void crear() {
        shader.crear(SHADER_VERTICES, SHADER_FRAGMENTOS); // Compila y enlaza cielo.vert + cielo.frag.
        cubo.crear(); // VAO/VBO propios del cielo.
    }

    /** Libera el programa y el cubo si llegaron a crearse. */
    public void eliminar() {
        cubo.eliminar(); // Libera el cubo.
        shader.eliminar(); // Libera el programa.
    }

    // ==================== 3. DIBUJO ====================

    /**
     * Dibuja el cielo alrededor del ojo de la cámara; debe ser lo primero de la vista principal. Deja activo el
     * programa del cielo: el que llama debe volver a activar su shader y su cubo (Shader.usar, Cubo.enlazar).
     * @param tiempo segundos de juego, para el parpadeo de las estrellas y el viento de las nubes.
     */
    public void dibujar(Camara camara, float autoX, float autoZ, float angulo, int ancho, int alto, Iluminacion iluminacion, float tiempo) {
        boolean habiaProfundidad = glIsEnabled(GL_DEPTH_TEST); // Estado previo, para restaurarlo igual.
        boolean habiaCaras = glIsEnabled(GL_CULL_FACE);
        glDepthMask(false); // No escribe profundidad.
        glDisable(GL_DEPTH_TEST); // No la prueba.
        glDisable(GL_CULL_FACE); // La cámara está dentro del cubo: se ven sus caras interiores.
        try { // Asegura que los estados vuelvan aunque algo falle.
            shader.usar(); // Programa del cielo.
            camara.configurar(shader, autoX, autoZ, angulo, ancho, alto); // Misma cámara que la ciudad (uOjo, uObjetivo, uAspecto...).
            float[] sol = iluminacion.direccionSol(); // Baja al horizonte al anochecer.
            float[] luna = direccionLuna();
            shader.vector("uDireccionSol", sol[0], sol[1], sol[2]);
            shader.vector("uDireccionLuna", luna[0], luna[1], luna[2]);
            shader.decimal("uFactorNoche", iluminacion.factorNoche()); // 0 día, 1 noche, con transición.
            shader.decimal("uTiempo", tiempo);
            float[] ojo = camara.getOjo(); // Centro del cubo.
            cubo.caja(ojo[0], ojo[1], ojo[2], LADO_CUBO, LADO_CUBO, LADO_CUBO, 0, 0, 0); // Color sin uso: el shader lo calcula.
        } finally {
            glDepthMask(true); // El resto de la escena sí escribe profundidad.
            if (habiaProfundidad) { // Restaura la prueba de profundidad.
                glEnable(GL_DEPTH_TEST);
            }
            if (habiaCaras) { // Restaura el descarte de caras traseras (hoy desactivado en todo el proyecto).
                glEnable(GL_CULL_FACE);
            }
        }
    }
}
