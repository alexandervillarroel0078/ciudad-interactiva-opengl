package com.graphics.ciudad.motor; // Agrupa las piezas técnicas: ventana, shaders, geometría y cámara.

import static org.lwjgl.opengl.GL33.*; // Importa las funciones OpenGL hasta la versión 3.3.

/**
 * CUBO: la única geometría del proyecto, guardada en la GPU con un VAO y un VBO.
 * Responsable de: crear el cubo unitario de 36 vértices (posición y normal), activarlo, dibujarlo
 * transformado como una "caja" (posición, escala, giro y color) y liberar la memoria de la GPU.
 * Se comunica con: Shader, al que envía uPos, uEscala, uColor y uGiro antes de cada dibujo; y con
 * Ciudad, Decoracion, Semaforo, Auto, Iluminacion, Entregas y Minimapa, que construyen todo con cajas.
 * En caja() y cajaGirada() los argumentos son: posición X/Y/Z, tamaño X/Y/Z y color rojo/verde/azul.
 */
public class Cubo {

    private final Shader shader; // Programa que recibe la transformación y el color de cada caja.
    private int vao; // Identificador del VAO: describe cómo leer los vértices.
    private int vbo; // Identificador del VBO: contiene los vértices en la GPU.

    /** Recuerda el shader que recibirá los uniforms de cada caja. */
    public Cubo(Shader shader) {
        this.shader = shader; // Guarda la referencia; la GPU todavía no se utiliza aquí.
    }

    // ==================== 1. GEOMETRÍA DEL CUBO: VAO Y VBO ====================

    /** Crea un cubo explícito, con posición XYZ y normal XYZ por vértice, como en los ejemplos. */
    public void crear() {
        float[] vertices = { // Cada línea describe un vértice: X, Y, Z, normalX, normalY, normalZ.
            -0.5f, -0.5f, 0.5f, 0, 0, 1, // Cara frontal: esquina inferior izquierda, normal hacia +Z.
            0.5f, -0.5f, 0.5f, 0, 0, 1, // Cara frontal: esquina inferior derecha.
            0.5f, 0.5f, 0.5f, 0, 0, 1, // Cara frontal: esquina superior derecha; termina el primer triángulo.
            0.5f, 0.5f, 0.5f, 0, 0, 1, // Cara frontal: repite la esquina para el segundo triángulo.
            -0.5f, 0.5f, 0.5f, 0, 0, 1, // Cara frontal: esquina superior izquierda.
            -0.5f, -0.5f, 0.5f, 0, 0, 1, // Cara frontal: cierra el segundo triángulo.

            -0.5f, -0.5f, -0.5f, 0, 0, -1, // Cara trasera: primer vértice, normal hacia -Z.
            -0.5f, 0.5f, -0.5f, 0, 0, -1, // Cara trasera: segundo vértice.
            0.5f, 0.5f, -0.5f, 0, 0, -1, // Cara trasera: termina el primer triángulo.
            0.5f, 0.5f, -0.5f, 0, 0, -1, // Cara trasera: inicia el segundo triángulo.
            0.5f, -0.5f, -0.5f, 0, 0, -1, // Cara trasera: esquina inferior derecha.
            -0.5f, -0.5f, -0.5f, 0, 0, -1, // Cara trasera: cierra la cara.

            -0.5f, 0.5f, 0.5f, -1, 0, 0, // Cara izquierda: primer vértice, normal hacia -X.
            -0.5f, 0.5f, -0.5f, -1, 0, 0, // Cara izquierda: segundo vértice.
            -0.5f, -0.5f, -0.5f, -1, 0, 0, // Cara izquierda: termina el primer triángulo.
            -0.5f, -0.5f, -0.5f, -1, 0, 0, // Cara izquierda: inicia el segundo triángulo.
            -0.5f, -0.5f, 0.5f, -1, 0, 0, // Cara izquierda: esquina inferior frontal.
            -0.5f, 0.5f, 0.5f, -1, 0, 0, // Cara izquierda: cierra la cara.

            0.5f, 0.5f, 0.5f, 1, 0, 0, // Cara derecha: primer vértice, normal hacia +X.
            0.5f, -0.5f, 0.5f, 1, 0, 0, // Cara derecha: segundo vértice.
            0.5f, -0.5f, -0.5f, 1, 0, 0, // Cara derecha: termina el primer triángulo.
            0.5f, -0.5f, -0.5f, 1, 0, 0, // Cara derecha: inicia el segundo triángulo.
            0.5f, 0.5f, -0.5f, 1, 0, 0, // Cara derecha: esquina superior trasera.
            0.5f, 0.5f, 0.5f, 1, 0, 0, // Cara derecha: cierra la cara.

            -0.5f, 0.5f, -0.5f, 0, 1, 0, // Cara superior: primer vértice, normal hacia +Y.
            -0.5f, 0.5f, 0.5f, 0, 1, 0, // Cara superior: segundo vértice.
            0.5f, 0.5f, 0.5f, 0, 1, 0, // Cara superior: termina el primer triángulo.
            0.5f, 0.5f, 0.5f, 0, 1, 0, // Cara superior: inicia el segundo triángulo.
            0.5f, 0.5f, -0.5f, 0, 1, 0, // Cara superior: esquina trasera derecha.
            -0.5f, 0.5f, -0.5f, 0, 1, 0, // Cara superior: cierra la cara.

            -0.5f, -0.5f, -0.5f, 0, -1, 0, // Cara inferior: primer vértice, normal hacia -Y.
            0.5f, -0.5f, -0.5f, 0, -1, 0, // Cara inferior: segundo vértice.
            0.5f, -0.5f, 0.5f, 0, -1, 0, // Cara inferior: termina el primer triángulo.
            0.5f, -0.5f, 0.5f, 0, -1, 0, // Cara inferior: inicia el segundo triángulo.
            -0.5f, -0.5f, 0.5f, 0, -1, 0, // Cara inferior: esquina frontal izquierda.
            -0.5f, -0.5f, -0.5f, 0, -1, 0 // Cara inferior: cierra los 36 vértices del cubo.
        };
        vao = glGenVertexArrays(); // Reserva el objeto que recordará la disposición de los atributos.
        glBindVertexArray(vao); // Activa ese VAO para configurarlo.
        vbo = glGenBuffers(); // Reserva el buffer que almacenará los vértices.
        glBindBuffer(GL_ARRAY_BUFFER, vbo); // Selecciona el VBO como destino de datos de vértices.
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW); // Copia los datos a la GPU; el cubo no cambiará.
        int bytesPorVertice = 6 * Float.BYTES; // Cada vértice contiene seis float de cuatro bytes.
        glVertexAttribPointer(0, 3, GL_FLOAT, false, bytesPorVertice, 0L); // Describe XYZ desde el primer byte del vértice.
        glEnableVertexAttribArray(0); // Habilita la posición que recibe aPos.
        glVertexAttribPointer(1, 3, GL_FLOAT, false, bytesPorVertice, 3L * Float.BYTES); // Describe la normal después de XYZ.
        glEnableVertexAttribArray(1); // Habilita la normal que recibe aNormal.
        glBindBuffer(GL_ARRAY_BUFFER, 0); // Deja de seleccionar el VBO una vez configurado.
        glBindVertexArray(0); // Finaliza la configuración del VAO.
    }

    /** Selecciona el cubo compartido antes de dibujar la escena. */
    public void enlazar() {
        glBindVertexArray(vao); // Selecciona los atributos del cubo compartido.
    }

    /** Libera únicamente los recursos que llegaron a crearse. */
    public void eliminar() {
        if (vbo != 0) { // Comprueba si se reservó el buffer de vértices.
            glDeleteBuffers(vbo); // Libera los datos de geometría de la GPU.
        }
        if (vao != 0) { // Comprueba si se creó la descripción de atributos.
            glDeleteVertexArrays(vao); // Libera la configuración del cubo.
        }
    }

    // ==================== 2. DIBUJAR CAJAS Y ENVIAR UNIFORMS ====================

    /** Dibuja una caja sin giro: posición XYZ, tamaño XYZ y color RGB entre 0 y 1. */
    public void caja(float x, float y, float z, float sx, float sy, float sz, float r, float g, float b) {
        cajaGirada(x, y, z, sx, sy, sz, r, g, b, 0); // Reutiliza el dibujo general con un ángulo de cero.
    }

    /**
     * Caja translúcida y emisiva: se mezcla con lo que hay detrás.
     * HOY NO LA USA NADIE: los halos de las luces del auto se quitaron. Se conserva como ejemplo mínimo de mezcla
     * con alfa parejo en toda la caja. Las sombras falsas (iluminacion/Sombras) usan la misma idea, pero activan la
     * mezcla una sola vez para todas las manchas y el shader les da un degradado circular (bordes difusos).
     * Activa la mezcla alfa (color · alfa + fondo · (1 - alfa)) y NO escribe profundidad (glDepthMask(false)): así la
     * caja no tapa lo que se dibuje después detrás de ella; conviene dibujarla al final de la escena, después de todo lo
     * opaco. Al terminar restaura el estado opaco normal de la escena.
     */
    public void cajaTranslucida(float x, float y, float z, float sx, float sy, float sz, float r, float g, float b, float alfa, float angulo) {
        glEnable(GL_BLEND); // Activa la mezcla con el fondo.
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA); // Mezcla clásica por transparencia.
        glDepthMask(false); // Se prueba contra la profundidad, pero no la escribe.
        shader.entero("uEmision", 1); // Usa el color tal cual, sin iluminación (el shader solo aplica uAlfa en esa rama).
        shader.decimal("uAlfa", alfa); // Opacidad de la caja.
        cajaGirada(x, y, z, sx, sy, sz, r, g, b, angulo); // Dibuja la caja con la orientación indicada.
        shader.decimal("uAlfa", 1); // Vuelve a opacidad completa.
        shader.entero("uEmision", 0); // Vuelve al material normal.
        glDepthMask(true); // Vuelve a escribir profundidad.
        glDisable(GL_BLEND); // La escena 3D es opaca: sin mezcla.
    }

    /** Dibuja el cubo unitario con escala, giro alrededor de Y, posición y color. */
    public void cajaGirada(float x, float y, float z, float sx, float sy, float sz, float r, float g, float b, float angulo) {
        glBindVertexArray(vao); // Activa el cubo: una Malla (esfera, cilindro, cono) pudo dejar activo su propio VAO.
        shader.vector("uPos", x, y, z); // Envía la posición del centro de la caja en el mundo.
        shader.vector("uEscala", sx, sy, sz); // Envía el ancho, alto y profundidad de la caja.
        shader.vector("uColor", r, g, b); // Envía las intensidades roja, verde y azul del material.
        shader.decimal("uGiro", angulo); // Envía la orientación en radianes.
        Estadisticas.contarLlamada(); // Un draw call más para el HUD.
        glDrawArrays(GL_TRIANGLES, 0, 36); // Dibuja 12 triángulos: dos por cada una de las seis caras.
    }
}
