package com.graphics.ciudad.motor; // Agrupa las piezas técnicas: ventana, shaders, geometría, cámara y texto.

import static org.lwjgl.opengl.GL33.*; // Importa las funciones OpenGL hasta la versión 3.3.

/**
 * MALLA: figura genérica guardada en la GPU con un VAO y un VBO, con el mismo formato que Cubo:
 * cada vértice tiene posición XYZ y normal XYZ (seis float), y se dibuja como lista de triángulos.
 * Responsable de: subir los vértices a la GPU, dibujar la figura posicionada, escalada, girada y coloreada
 * (como Cubo.caja), liberar la memoria, y GENERAR por fórmulas las figuras base: esfera, cilindro y cono, además de
 * figuras extruidas desde un perfil 2D (extruir(), con medidas reales), como la cabina de los autos.
 * Todas las figuras son UNITARIAS y centradas en el origen, igual que el cubo: miden 1 de ancho y 1 de alto
 * (radio 0.5, Y entre -0.5 y 0.5). Así la escala que se pasa al dibujar es directamente el tamaño en el mundo.
 * Se comunica con: Shader (uPos, uEscala, uColor, uGiro) y Figuras, que crea una malla de cada tipo.
 *
 * NORMALES: la normal de un vértice es el vector unitario perpendicular a la superficie en ese punto. El shader la
 * usa para la luz difusa (Lambert), las farolas y los faros. Si se usa la normal de la SUPERFICIE CURVA en cada
 * vértice (y no la de cada triángulo plano), OpenGL la interpola entre vértices y la figura se ve redondeada.
 * ciudad.vert ya corrige las normales con la inversa de la escala, así que siguen siendo correctas al estirar la figura.
 */
public class Malla {

    public static final int FLOATS_POR_VERTICE = 6; // X, Y, Z, normalX, normalY, normalZ.
    private static final float RADIO = 0.5f; // Radio de las figuras unitarias (diámetro 1, como el cubo).
    private static final float MITAD_ALTO = 0.5f; // Las figuras van de Y = -0.5 a Y = 0.5 (alto 1).

    private final Shader shader; // Programa que recibe la transformación y el color.
    private final float[] vertices; // Datos de la figura: seis float por vértice.
    private int vao; // Identificador del VAO: describe cómo leer los vértices.
    private int vbo; // Identificador del VBO: contiene los vértices en la GPU.

    /** Guarda los datos de la figura; la GPU todavía no se usa (eso ocurre en crear()). */
    public Malla(Shader shader, float[] vertices) {
        this.shader = shader; // Programa compartido con el resto de la escena.
        this.vertices = vertices; // Vértices generados por esfera(), cilindro() o cono().
    }

    /** Cantidad de vértices de la figura (tres por triángulo). */
    public int cantidadVertices() {
        return vertices.length / FLOATS_POR_VERTICE; // Cada vértice ocupa seis float.
    }

    // ==================== 1. GPU: CREAR, DIBUJAR Y LIBERAR ====================

    /** Sube los vértices a la GPU con el mismo formato de atributos que Cubo (posición en 0, normal en 1). */
    public void crear() {
        vao = glGenVertexArrays(); // Reserva el objeto que recordará la disposición de los atributos.
        glBindVertexArray(vao); // Activa ese VAO para configurarlo.
        vbo = glGenBuffers(); // Reserva el buffer que almacenará los vértices.
        glBindBuffer(GL_ARRAY_BUFFER, vbo); // Selecciona el VBO como destino de datos de vértices.
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW); // Copia los datos a la GPU; la figura no cambiará.
        int bytesPorVertice = FLOATS_POR_VERTICE * Float.BYTES; // Cada vértice contiene seis float de cuatro bytes.
        glVertexAttribPointer(0, 3, GL_FLOAT, false, bytesPorVertice, 0L); // Posición XYZ desde el primer byte (aPos).
        glEnableVertexAttribArray(0); // Habilita la posición.
        glVertexAttribPointer(1, 3, GL_FLOAT, false, bytesPorVertice, 3L * Float.BYTES); // Normal después de XYZ (aNormal).
        glEnableVertexAttribArray(1); // Habilita la normal.
        glBindBuffer(GL_ARRAY_BUFFER, 0); // Deja de seleccionar el VBO una vez configurado.
        glBindVertexArray(0); // Finaliza la configuración del VAO.
    }

    /** Dibuja la figura sin giro: posición XYZ del centro, tamaño XYZ y color RGB entre 0 y 1 (igual que Cubo.caja). */
    public void dibujar(float x, float y, float z, float sx, float sy, float sz, float r, float g, float b) {
        dibujarGirada(x, y, z, sx, sy, sz, r, g, b, 0); // Reutiliza el dibujo general con un ángulo de cero.
    }

    /** Dibuja la figura escalada, girada alrededor de Y, trasladada y coloreada (igual que Cubo.cajaGirada). */
    public void dibujarGirada(float x, float y, float z, float sx, float sy, float sz, float r, float g, float b, float angulo) {
        glBindVertexArray(vao); // Activa los vértices de ESTA figura (el cubo u otra malla pudo estar activo).
        shader.vector("uPos", x, y, z); // Envía la posición del centro en el mundo.
        shader.vector("uEscala", sx, sy, sz); // Envía el tamaño en cada eje.
        shader.vector("uColor", r, g, b); // Envía el color del material.
        shader.decimal("uGiro", angulo); // Envía la orientación en radianes.
        Estadisticas.contarLlamada(); // Un draw call más para el HUD.
        glDrawArrays(GL_TRIANGLES, 0, cantidadVertices()); // Dibuja todos los triángulos de la figura.
    }

    /** Libera los recursos de la GPU si llegaron a crearse. */
    public void eliminar() {
        if (vbo != 0) { // Comprueba si se reservó el buffer de vértices.
            glDeleteBuffers(vbo); // Libera los datos de geometría de la GPU.
        }
        if (vao != 0) { // Comprueba si se creó la descripción de atributos.
            glDeleteVertexArrays(vao); // Libera la configuración de la figura.
        }
    }

    // ==================== 2. GENERADORES DE FIGURAS (solo fórmulas: seno y coseno) ====================

    /**
     * ESFERA "UV" de radio 0.5: se divide como un globo terráqueo en "anillos" (paralelos, de polo a polo) y "sectores"
     * (meridianos). Un punto de la esfera unitaria con latitud θ (0 en el polo norte, π en el sur) y longitud φ es
     * (sen θ · cos φ, cos θ, sen θ · sen φ). NORMAL: en una esfera centrada en el origen, la perpendicular a la
     * superficie apunta exactamente desde el centro hacia el punto, así que la normal es ese mismo vector unitario.
     * Cantidad de vértices: los anillos de los polos aportan un triángulo por sector y los demás dos triángulos por
     * sector, en total 6 · sectores · (anillos - 1). Con 12 × 8 son 504 vértices.
     */
    public static float[] esfera(int sectores, int anillos) {
        float[] datos = new float[6 * sectores * (anillos - 1) * FLOATS_POR_VERTICE]; // Tamaño exacto del resultado.
        int escrito = 0; // Posición de escritura.
        for (int i = 0; i < anillos; i++) { // Recorre las franjas de latitud.
            double theta1 = Math.PI * i / anillos; // Latitud del borde superior de la franja.
            double theta2 = Math.PI * (i + 1) / anillos; // Latitud del borde inferior.
            for (int j = 0; j < sectores; j++) { // Recorre los meridianos.
                double phi1 = 2 * Math.PI * j / sectores; // Longitud del borde izquierdo.
                double phi2 = 2 * Math.PI * (j + 1) / sectores; // Longitud del borde derecho.
                float[] a = puntoEsfera(theta1, phi1); // Esquina superior izquierda (dirección unitaria).
                float[] b = puntoEsfera(theta2, phi1); // Esquina inferior izquierda.
                float[] c = puntoEsfera(theta2, phi2); // Esquina inferior derecha.
                float[] d = puntoEsfera(theta1, phi2); // Esquina superior derecha.
                if (i != anillos - 1) { // Salvo en el polo sur, el triángulo a-b-c tiene tres puntos distintos.
                    escrito = verticeEsfera(datos, escrito, a); // Primer vértice.
                    escrito = verticeEsfera(datos, escrito, b); // Segundo vértice.
                    escrito = verticeEsfera(datos, escrito, c); // Tercer vértice.
                }
                if (i != 0) { // Salvo en el polo norte (donde a y d coinciden), se agrega el triángulo a-c-d.
                    escrito = verticeEsfera(datos, escrito, a); // Primer vértice.
                    escrito = verticeEsfera(datos, escrito, i == anillos - 1 ? b : c); // En el polo sur b = c: usa b.
                    escrito = verticeEsfera(datos, escrito, d); // Tercer vértice.
                }
            }
        }
        return datos; // Vértices de la esfera.
    }

    /** Dirección unitaria de la esfera para latitud theta y longitud phi. */
    private static float[] puntoEsfera(double theta, double phi) {
        return new float[] { // (sen θ cos φ, cos θ, sen θ sen φ).
            (float) (Math.sin(theta) * Math.cos(phi)), // X.
            (float) Math.cos(theta), // Y: 1 en el polo norte, -1 en el sur.
            (float) (Math.sin(theta) * Math.sin(phi)) // Z.
        };
    }

    /** Escribe un vértice de la esfera: posición = dirección · radio; normal = la misma dirección unitaria. */
    private static int verticeEsfera(float[] datos, int i, float[] direccion) {
        return vertice(datos, i, direccion[0] * RADIO, direccion[1] * RADIO, direccion[2] * RADIO, direccion[0], direccion[1], direccion[2]); // Normal radial.
    }

    /**
     * CILINDRO de diámetro 1 y alto 1 con tapas: el borde de la base es un polígono de "lados" lados (cos φ, sen φ).
     * NORMALES: en el costado, la perpendicular es horizontal y apunta hacia afuera desde el eje: (cos φ, 0, sen φ);
     * se usa la del ángulo de cada vértice (no la del lado plano), así el costado se ve redondo. En las tapas la
     * superficie es plana: normal (0, 1, 0) arriba y (0, -1, 0) abajo; por eso los vértices del borde se repiten con
     * otra normal (misma posición, distinta perpendicular: ahí hay una arista viva).
     * Cantidad de vértices: costado 6 · lados (dos triángulos por lado) + tapas 3 · lados cada una = 12 · lados.
     */
    public static float[] cilindro(int lados) {
        float[] datos = new float[12 * lados * FLOATS_POR_VERTICE]; // Tamaño exacto del resultado.
        int i = 0; // Posición de escritura.
        for (int k = 0; k < lados; k++) { // Recorre los lados del polígono.
            float c1 = (float) Math.cos(2 * Math.PI * k / lados); // Coseno del ángulo inicial del lado.
            float s1 = (float) Math.sin(2 * Math.PI * k / lados); // Seno del ángulo inicial.
            float c2 = (float) Math.cos(2 * Math.PI * (k + 1) / lados); // Coseno del ángulo final.
            float s2 = (float) Math.sin(2 * Math.PI * (k + 1) / lados); // Seno del ángulo final.
            // Costado: dos triángulos entre la base y la tapa; normales radiales horizontales.
            i = vertice(datos, i, c1 * RADIO, -MITAD_ALTO, s1 * RADIO, c1, 0, s1); // Abajo, ángulo inicial.
            i = vertice(datos, i, c2 * RADIO, -MITAD_ALTO, s2 * RADIO, c2, 0, s2); // Abajo, ángulo final.
            i = vertice(datos, i, c2 * RADIO, MITAD_ALTO, s2 * RADIO, c2, 0, s2); // Arriba, ángulo final.
            i = vertice(datos, i, c1 * RADIO, -MITAD_ALTO, s1 * RADIO, c1, 0, s1); // Abajo, ángulo inicial.
            i = vertice(datos, i, c2 * RADIO, MITAD_ALTO, s2 * RADIO, c2, 0, s2); // Arriba, ángulo final.
            i = vertice(datos, i, c1 * RADIO, MITAD_ALTO, s1 * RADIO, c1, 0, s1); // Arriba, ángulo inicial.
            // Tapa superior: abanico desde el centro, normal hacia arriba.
            i = vertice(datos, i, 0, MITAD_ALTO, 0, 0, 1, 0); // Centro de la tapa.
            i = vertice(datos, i, c2 * RADIO, MITAD_ALTO, s2 * RADIO, 0, 1, 0); // Borde, ángulo final.
            i = vertice(datos, i, c1 * RADIO, MITAD_ALTO, s1 * RADIO, 0, 1, 0); // Borde, ángulo inicial.
            // Tapa inferior: abanico desde el centro, normal hacia abajo.
            i = vertice(datos, i, 0, -MITAD_ALTO, 0, 0, -1, 0); // Centro de la base.
            i = vertice(datos, i, c1 * RADIO, -MITAD_ALTO, s1 * RADIO, 0, -1, 0); // Borde, ángulo inicial.
            i = vertice(datos, i, c2 * RADIO, -MITAD_ALTO, s2 * RADIO, 0, -1, 0); // Borde, ángulo final.
        }
        return datos; // Vértices del cilindro.
    }

    /**
     * CONO de base con radio 0.5 en Y = -0.5 y punta en Y = 0.5, con base cerrada.
     * NORMAL DEL COSTADO: el costado está inclinado. Con radio r y alto h, la recta que sube de la base a la punta tiene
     * dirección t = (-r cos φ, h, -r sen φ) y la tangente horizontal al borde es u = (-sen φ, 0, cos φ). La normal es
     * perpendicular a ambas (producto vectorial) y, orientada hacia afuera, vale (h cos φ, r, h sen φ); se normaliza.
     * Tiene componente Y positiva: el costado "mira" un poco hacia arriba. En la punta todas las direcciones se juntan,
     * así que ese vértice usa la normal del ángulo medio de su triángulo. La base es plana: normal (0, -1, 0).
     * Cantidad de vértices: costado 3 · lados + base 3 · lados = 6 · lados.
     */
    public static float[] cono(int lados) {
        float[] datos = new float[6 * lados * FLOATS_POR_VERTICE]; // Tamaño exacto del resultado.
        float alto = 2 * MITAD_ALTO; // h = 1.
        int i = 0; // Posición de escritura.
        for (int k = 0; k < lados; k++) { // Recorre los lados.
            double phi1 = 2 * Math.PI * k / lados; // Ángulo inicial del lado.
            double phi2 = 2 * Math.PI * (k + 1) / lados; // Ángulo final.
            double phiMedio = (phi1 + phi2) / 2; // Ángulo de la normal de la punta.
            float[] n1 = normalCono(phi1, alto); // Normal del borde en el ángulo inicial.
            float[] n2 = normalCono(phi2, alto); // Normal del borde en el ángulo final.
            float[] nPunta = normalCono(phiMedio, alto); // Normal usada en la punta de este triángulo.
            float x1 = (float) Math.cos(phi1) * RADIO; // Borde de la base, ángulo inicial, X.
            float z1 = (float) Math.sin(phi1) * RADIO; // Borde de la base, ángulo inicial, Z.
            float x2 = (float) Math.cos(phi2) * RADIO; // Borde de la base, ángulo final, X.
            float z2 = (float) Math.sin(phi2) * RADIO; // Borde de la base, ángulo final, Z.
            // Costado: un triángulo de la base a la punta.
            i = vertice(datos, i, x1, -MITAD_ALTO, z1, n1[0], n1[1], n1[2]); // Base, ángulo inicial.
            i = vertice(datos, i, x2, -MITAD_ALTO, z2, n2[0], n2[1], n2[2]); // Base, ángulo final.
            i = vertice(datos, i, 0, MITAD_ALTO, 0, nPunta[0], nPunta[1], nPunta[2]); // Punta.
            // Base cerrada: abanico desde el centro, normal hacia abajo.
            i = vertice(datos, i, 0, -MITAD_ALTO, 0, 0, -1, 0); // Centro de la base.
            i = vertice(datos, i, x2, -MITAD_ALTO, z2, 0, -1, 0); // Borde, ángulo final.
            i = vertice(datos, i, x1, -MITAD_ALTO, z1, 0, -1, 0); // Borde, ángulo inicial.
        }
        return datos; // Vértices del cono.
    }

    /** Normal unitaria del costado del cono en el ángulo phi: (h cos φ, r, h sen φ) normalizada. */
    private static float[] normalCono(double phi, float alto) {
        float nx = (float) (alto * Math.cos(phi)); // Componente horizontal X.
        float ny = RADIO; // Componente vertical: cuanto más ancho el cono, más mira hacia arriba.
        float nz = (float) (alto * Math.sin(phi)); // Componente horizontal Z.
        float largo = (float) Math.sqrt(nx * nx + ny * ny + nz * nz); // Longitud para normalizar.
        return new float[] {nx / largo, ny / largo, nz / largo}; // Vector de largo 1.
    }

    /**
     * EXTRUSIÓN DE PERFIL: toma un contorno 2D visto de costado y lo "estira" a lo ancho, como una masa que sale por un
     * molde. perfil es una lista ordenada de puntos {z, y} (el contorno lateral, CONVEXO, en cualquier sentido de giro);
     * la figura ocupa de X = -ancho/2 a X = +ancho/2 y usa medidas reales (no es unitaria: se dibuja con escala 1).
     * Caras: las dos tapas laterales (el perfil en X = ±ancho/2, en abanico: n - 2 triángulos cada una) y un
     * rectángulo (dos triángulos) por cada lado del contorno, que une ambas tapas.
     * NORMALES POR CARA (flat shading): cada triángulo a-b-c usa la normal (b - a) × (c - a) normalizada, el producto
     * cruz de dos de sus lados, que es perpendicular al plano del triángulo. Los tres vértices comparten esa normal, así
     * que cada cara se ve plana y las aristas quedan marcadas (como en una carrocería). El producto cruz puede apuntar
     * hacia adentro según el orden de los vértices: si apunta hacia el centro de la figura, se invierte. Con un
     * contorno convexo eso deja todas las normales hacia afuera.
     * Cantidad de vértices: tapas 2 · 3 · (n - 2) + costados 6 · n = 12 · n - 12 (36 para un perfil de 4 puntos).
     */
    public static float[] extruir(float[][] perfil, float ancho) {
        int n = perfil.length; // Cantidad de puntos del contorno.
        float mitad = ancho / 2; // Las tapas quedan en X = ±mitad.
        float centroZ = 0; // Centro del contorno en Z (promedio de los puntos).
        float centroY = 0; // Centro del contorno en Y.
        for (float[] p : perfil) { // Suma los puntos.
            centroZ += p[0] / n; // Promedio en Z.
            centroY += p[1] / n; // Promedio en Y.
        }
        float[] centro = {0, centroY, centroZ}; // Centro de la figura (X = 0, a mitad del ancho).
        float[] datos = new float[(12 * n - 12) * FLOATS_POR_VERTICE]; // Tamaño exacto del resultado.
        int i = 0; // Posición de escritura.
        for (int lado = -1; lado <= 1; lado += 2) { // Tapa izquierda (X = -mitad) y derecha (X = +mitad).
            float x = lado * mitad; // Plano de la tapa.
            for (int k = 1; k < n - 1; k++) { // Abanico desde el primer punto del contorno.
                i = trianguloPlano(datos, i, centro,
                    new float[] {x, perfil[0][1], perfil[0][0]}, // Primer punto del abanico: {x, y, z}.
                    new float[] {x, perfil[k][1], perfil[k][0]}, // Punto k.
                    new float[] {x, perfil[k + 1][1], perfil[k + 1][0]}); // Punto k + 1.
            }
        }
        for (int k = 0; k < n; k++) { // Un rectángulo por cada lado del contorno.
            float[] p = perfil[k]; // Punto inicial del lado.
            float[] q = perfil[(k + 1) % n]; // Punto final (el último se une con el primero).
            float[] a = {-mitad, p[1], p[0]}; // Esquina en la tapa izquierda, punto p.
            float[] b = {-mitad, q[1], q[0]}; // Tapa izquierda, punto q.
            float[] c = {mitad, q[1], q[0]}; // Tapa derecha, punto q.
            float[] d = {mitad, p[1], p[0]}; // Tapa derecha, punto p.
            i = trianguloPlano(datos, i, centro, a, b, c); // Primer triángulo del rectángulo.
            i = trianguloPlano(datos, i, centro, a, c, d); // Segundo triángulo: misma normal, mismo plano.
        }
        return datos; // Vértices de la figura extruida.
    }

    /** Escribe un triángulo con normal plana (b - a) × (c - a), orientada hacia afuera del centro de la figura. */
    private static int trianguloPlano(float[] datos, int i, float[] centro, float[] a, float[] b, float[] c) {
        float ux = b[0] - a[0]; // Lado a → b, en X.
        float uy = b[1] - a[1]; // Lado a → b, en Y.
        float uz = b[2] - a[2]; // Lado a → b, en Z.
        float vx = c[0] - a[0]; // Lado a → c, en X.
        float vy = c[1] - a[1]; // Lado a → c, en Y.
        float vz = c[2] - a[2]; // Lado a → c, en Z.
        float nx = uy * vz - uz * vy; // Producto cruz u × v, componente X.
        float ny = uz * vx - ux * vz; // Componente Y.
        float nz = ux * vy - uy * vx; // Componente Z.
        float largo = (float) Math.sqrt(nx * nx + ny * ny + nz * nz); // Largo del producto cruz (el doble del área).
        nx /= largo; // Normaliza: largo 1.
        ny /= largo; // Normaliza.
        nz /= largo; // Normaliza.
        float mx = (a[0] + b[0] + c[0]) / 3 - centro[0]; // Del centro de la figura al centro del triángulo, en X.
        float my = (a[1] + b[1] + c[1]) / 3 - centro[1]; // En Y.
        float mz = (a[2] + b[2] + c[2]) / 3 - centro[2]; // En Z.
        if (nx * mx + ny * my + nz * mz < 0) { // La normal apunta hacia adentro...
            nx = -nx; // ...se invierte.
            ny = -ny; // ...se invierte.
            nz = -nz; // ...se invierte.
        }
        i = vertice(datos, i, a[0], a[1], a[2], nx, ny, nz); // Vértice a con la normal de la cara.
        i = vertice(datos, i, b[0], b[1], b[2], nx, ny, nz); // Vértice b.
        return vertice(datos, i, c[0], c[1], c[2], nx, ny, nz); // Vértice c.
    }

    /** Escribe un vértice (posición y normal) en el arreglo y devuelve la próxima posición libre. */
    private static int vertice(float[] datos, int i, float x, float y, float z, float nx, float ny, float nz) {
        datos[i] = x; // Posición X.
        datos[i + 1] = y; // Posición Y.
        datos[i + 2] = z; // Posición Z.
        datos[i + 3] = nx; // Normal X.
        datos[i + 4] = ny; // Normal Y.
        datos[i + 5] = nz; // Normal Z.
        return i + FLOATS_POR_VERTICE; // Avanza al siguiente vértice.
    }

    /** Devuelve los datos crudos (para las pruebas). */
    public float[] datos() {
        return vertices.clone(); // Copia: nadie puede modificar la figura desde afuera.
    }
}
