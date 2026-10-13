package com.graphics.ciudad.interfaz; // Agrupa la interfaz dentro de la ventana: dibujo 2D y HUD.

import com.graphics.ciudad.motor.Estadisticas; // Cuenta su draw call para el HUD.
import com.graphics.ciudad.motor.Shader; // Programa GLSL propio del HUD (hud.vert / hud.frag).
import com.graphics.ciudad.motor.Texto; // Conversión a ASCII compartida con los carteles 3D.
import java.nio.ByteBuffer; // Memoria nativa donde STBEasyFont escribe los vértices del texto.
import org.lwjgl.BufferUtils; // Reserva memoria nativa en el orden de bytes de la plataforma.
import static org.lwjgl.opengl.GL33.*; // Importa las funciones OpenGL hasta la versión 3.3.
import static org.lwjgl.stb.STBEasyFont.*; // stb_easy_font_print y stb_easy_font_width.

/**
 * DIBUJO2D: pase ortográfico 2D, en píxeles, que se dibuja encima de la escena y del minimapa.
 * Responsable de: su propio shader (hud.vert / hud.frag) y su VAO/VBO dinámico; dibujar rectángulos
 * semitransparentes y texto; preparar el estado de OpenGL para 2D (sin prueba de profundidad, con mezcla alfa)
 * y restaurarlo al terminar para no alterar el cuadro siguiente.
 * El texto lo genera STBEasyFont (biblioteca lwjgl-stb): convierte cada letra en pequeños cuadriláteros de color
 * sólido, así que no hacen falta texturas ni archivos de fuentes. Solo admite ASCII: las tildes se quitan (á → a).
 * Se comunica con: Shader (programa propio), Hud (le pide paneles y textos) y Juego (lo crea, lo libera y abre y cierra
 * el pase en cada cuadro).
 */
public class Dibujo2D {

    // ==================== 1. CONSTANTES ====================
    private static final String SHADER_VERTICES = "/shaders/hud.vert"; // Convierte píxeles en coordenadas de pantalla.
    private static final String SHADER_FRAGMENTOS = "/shaders/hud.frag"; // Pinta con un color RGBA uniforme.
    private static final int BYTES_POR_CARACTER = 270; // Espacio que recomienda stb_easy_font por carácter en el peor caso.
    private static final int BYTES_POR_VERTICE = 16; // Formato de STBEasyFont: x, y, z (float) + color (4 bytes).
    private static final int VERTICES_POR_QUAD = 4; // STBEasyFont genera cuadriláteros de cuatro vértices.

    // ==================== 2. ESTADO ====================
    private final Shader shader = new Shader(); // Programa del HUD; se compila en crear().
    private int vao; // Describe cómo leer los vértices 2D.
    private int vbo; // Buffer que se reescribe en cada dibujo (GL_STREAM_DRAW).
    private ByteBuffer bufferTexto = BufferUtils.createByteBuffer(BYTES_POR_CARACTER * 64); // Crece si el texto es más largo.

    /** Compila el shader del HUD y crea el VAO/VBO; requiere el contexto OpenGL ya creado. */
    public void crear() {
        shader.crear(SHADER_VERTICES, SHADER_FRAGMENTOS); // Compila y enlaza hud.vert y hud.frag.
        vao = glGenVertexArrays(); // Reserva la descripción de atributos.
        glBindVertexArray(vao); // La activa para configurarla.
        vbo = glGenBuffers(); // Reserva el buffer de vértices.
        glBindBuffer(GL_ARRAY_BUFFER, vbo); // Lo selecciona como destino de datos.
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0L); // Cada vértice son dos float: X e Y en píxeles.
        glEnableVertexAttribArray(0); // Habilita aPos del shader.
        glBindBuffer(GL_ARRAY_BUFFER, 0); // Termina de configurar el VBO.
        glBindVertexArray(0); // Termina de configurar el VAO.
    }

    /** Libera los recursos de la GPU si llegaron a crearse. */
    public void eliminar() {
        shader.eliminar(); // Libera el programa del HUD.
        if (vbo != 0) { // Comprueba si se reservó el buffer.
            glDeleteBuffers(vbo); // Libera el buffer de vértices.
        }
        if (vao != 0) { // Comprueba si se creó el VAO.
            glDeleteVertexArrays(vao); // Libera la descripción de atributos.
        }
    }

    // ==================== 3. ABRIR Y CERRAR EL PASE 2D ====================

    /** Prepara OpenGL para dibujar en 2D sobre toda la ventana. */
    public void comenzar(int ancho, int alto) {
        glViewport(0, 0, ancho, alto); // El HUD ocupa toda la ventana (el minimapa pudo dejar otro viewport).
        glDisable(GL_CULL_FACE); // Los rectángulos del HUD (y las letras de STBEasyFont) no tienen un sentido de giro garantizado.
        glDisable(GL_DEPTH_TEST); // El HUD siempre queda encima: no compite en profundidad con la escena.
        glEnable(GL_BLEND); // Activa la mezcla para que los paneles sean semitransparentes.
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA); // Mezcla clásica: color · alfa + fondo · (1 - alfa).
        shader.usar(); // Activa el programa del HUD.
        shader.vector2("uPantalla", ancho, alto); // Tamaño actual: el HUD se adapta al redimensionar.
        glBindVertexArray(vao); // Selecciona los atributos 2D.
        glBindBuffer(GL_ARRAY_BUFFER, vbo); // Selecciona el buffer que se reescribe en cada dibujo.
    }

    /** Restaura el estado que espera la escena 3D del próximo cuadro. */
    public void terminar() {
        glBindBuffer(GL_ARRAY_BUFFER, 0); // Deja de seleccionar el VBO del HUD.
        glBindVertexArray(0); // Deja de seleccionar el VAO del HUD; Juego vuelve a enlazar el del cubo.
        glDisable(GL_BLEND); // La escena 3D es opaca: sin mezcla.
        glEnable(GL_DEPTH_TEST); // Vuelve a activar la prueba de profundidad de la escena.
        glEnable(GL_CULL_FACE); // Y el descarte de caras traseras.
    }

    // ==================== 4. PRIMITIVAS: RECTÁNGULOS Y TEXTO ====================

    /** Dibuja un rectángulo de ancho × alto píxeles con su esquina superior izquierda en (x, y). */
    public void rectangulo(float x, float y, float ancho, float alto, float r, float g, float b, float a) {
        float[] vertices = { // Dos triángulos que cubren el rectángulo.
            x, y, x + ancho, y, x + ancho, y + alto, // Primer triángulo: arriba-izquierda, arriba-derecha, abajo-derecha.
            x, y, x + ancho, y + alto, x, y + alto // Segundo triángulo: arriba-izquierda, abajo-derecha, abajo-izquierda.
        };
        dibujarTriangulos(vertices, 0, 0, 1, r, g, b, a); // Las coordenadas ya están en píxeles: origen 0 y escala 1.
    }

    /** Dibuja texto con su esquina superior izquierda en (x, y); escala 1 ≈ 7 píxeles de alto por letra. */
    public void texto(float x, float y, float escala, String texto, float r, float g, float b, float a) {
        String ascii = aAscii(texto); // STBEasyFont solo conoce los caracteres ASCII 32 a 126.
        int necesario = Math.max(1, ascii.length()) * BYTES_POR_CARACTER; // Tamaño seguro para este texto.
        if (bufferTexto.capacity() < necesario) { // Si el texto es más largo que el buffer actual...
            bufferTexto = BufferUtils.createByteBuffer(necesario); // ...reserva uno más grande.
        }
        bufferTexto.clear(); // Escribe desde el principio del buffer.
        int quads = stb_easy_font_print(0, 0, ascii, null, bufferTexto); // Genera los cuadriláteros de las letras.
        float[] vertices = new float[quads * 6 * 2]; // Cada quad se convierte en 2 triángulos = 6 vértices de 2 float.
        int escrito = 0; // Posición de escritura en el arreglo de triángulos.
        for (int quad = 0; quad < quads; quad++) { // Recorre los cuadriláteros generados.
            int base = quad * VERTICES_POR_QUAD * BYTES_POR_VERTICE; // Primer byte de este quad.
            float[] esquinas = new float[8]; // X e Y de las cuatro esquinas del quad.
            for (int v = 0; v < VERTICES_POR_QUAD; v++) { // Lee cada esquina.
                esquinas[v * 2] = bufferTexto.getFloat(base + v * BYTES_POR_VERTICE); // X de la esquina.
                esquinas[v * 2 + 1] = bufferTexto.getFloat(base + v * BYTES_POR_VERTICE + 4); // Y de la esquina.
            }
            int[] orden = {0, 1, 2, 0, 2, 3}; // El quad 0-1-2-3 se parte en los triángulos 0-1-2 y 0-2-3.
            for (int indice : orden) { // Copia las esquinas en el orden de los triángulos.
                vertices[escrito++] = esquinas[indice * 2]; // X.
                vertices[escrito++] = esquinas[indice * 2 + 1]; // Y.
            }
        }
        dibujarTriangulos(vertices, x, y, escala, r, g, b, a); // El shader escala y traslada las letras.
    }

    /** Devuelve el ancho en píxeles que ocupará un texto con la escala indicada. */
    public float anchoTexto(String texto, float escala) {
        return stb_easy_font_width(aAscii(texto)) * escala; // Ancho de STBEasyFont (escala 1) multiplicado por la escala.
    }

    /** Sube los triángulos al VBO y los dibuja con el origen, la escala y el color indicados. */
    private void dibujarTriangulos(float[] vertices, float origenX, float origenY, float escala, float r, float g, float b, float a) {
        if (vertices.length == 0) { // Un texto vacío no genera triángulos.
            return; // No hay nada que dibujar.
        }
        shader.vector2("uOrigen", origenX, origenY); // Posición del dibujo en píxeles.
        shader.decimal("uEscala", escala); // Tamaño del dibujo.
        shader.vector4("uColor", r, g, b, a); // Color con transparencia.
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STREAM_DRAW); // Reemplaza el contenido: se usa una sola vez.
        Estadisticas.contarLlamada(); // Un draw call más (el HUD se dibuja después de leer el total).
        glDrawArrays(GL_TRIANGLES, 0, vertices.length / 2); // Cada vértice ocupa dos float.
    }

    /** Convierte el texto a ASCII: quita tildes (á → a, ñ → n) y reemplaza otros símbolos por '?'. */
    public static String aAscii(String texto) {
        return Texto.aAscii(texto); // La conversión vive en motor/Texto, compartida con los carteles 3D.
    }
}
