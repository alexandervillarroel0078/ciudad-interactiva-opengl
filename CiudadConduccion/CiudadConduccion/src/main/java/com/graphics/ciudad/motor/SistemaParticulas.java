package com.graphics.ciudad.motor; // Agrupa las piezas técnicas: ventana, shaders, geometría, cámara y partículas.

import com.graphics.ciudad.mundo.Mapa; // Centros de los parques, donde están las fuentes.
import com.graphics.ciudad.mundo.Parque; // Altura del césped, para ubicar el agua y el chorro de la fuente.
import java.util.List; // Lista de parques del mapa.
import java.util.Random; // Azar de las partículas; con semilla fija las pruebas son repetibles.
import static org.lwjgl.opengl.GL33.*; // Importa las funciones OpenGL hasta la versión 3.3.

/**
 * SISTEMA DE PARTÍCULAS: humo del escape, salpicaduras de las fuentes, todo en un único POOL fijo.
 *
 * POOL: CAPACIDAD partículas guardadas en un solo arreglo de float reservado al construir (CAMPOS float por partícula) y
 * un arreglo de tipos. Las vivas ocupan siempre las posiciones 0 .. activas-1. Al nacer una partícula se escribe en la
 * primera posición libre; al morir, la última viva se copia sobre ella (la posición queda reutilizada). Nunca se crea un
 * objeto por partícula ni por cuadro: ni al emitir, ni al actualizar, ni al dibujar. Si el pool está lleno, la emisión se
 * descarta.
 * Cada partícula tiene posición, velocidad, vida, color y tamaño; todo avanza con dt (con dt = 0, en pausa, no cambia
 * nada y tampoco se emite).
 *
 * DIBUJO: cada partícula es un cuadrado (Malla) orientado a la cámara: particulas.vert lo arma con los ejes derecha y
 * arriba de la cámara, así siempre mira al ojo. Se mezcla con alfa y glDepthMask(false): las partículas no escriben
 * profundidad (no se tapan entre sí ni tapan al resto) pero sí la prueban, así los edificios las ocultan. Al terminar se
 * restaura el estado de OpenGL. La cámara y la relación ancho/alto se envían en cada cuadro, así que el dibujo sigue
 * bien al redimensionar la ventana. Solo se dibuja en la vista principal, no en el minimapa.
 *
 * Los emisores y el pool no usan OpenGL: se prueban sin ventana. Solo crear(), eliminar() y dibujar() lo necesitan.
 * Se comunica con: Juego (actualiza, emite y dibuja), Camara, Shader, Malla, Mapa y Parque.
 */
public class SistemaParticulas {

    // ==================== 1. CONSTANTES (valores ajustables) ====================
    public static final int CAPACIDAD = 3000; // Partículas del pool.
    public static final int TIPO_HUMO = 0; // Humo del escape del auto.
    public static final int TIPO_FUENTE = 1; // Gotas de las fuentes de los parques.
    private static final int TIPOS = 2; // Cantidad de tipos.

    private static final String SHADER_VERTICES = "/shaders/particulas.vert"; // Billboard hacia la cámara.
    private static final String SHADER_FRAGMENTOS = "/shaders/particulas.frag"; // Disco suave con transparencia.

    // Campos de cada partícula dentro del arreglo de datos.
    private static final int X = 0, Y = 1, Z = 2; // Posición.
    private static final int VX = 3, VY = 4, VZ = 5; // Velocidad (unidades por segundo).
    private static final int AY = 6; // Aceleración vertical: negativa = cae, positiva = sube (el humo flota).
    private static final int VIDA = 7, VIDA_TOTAL = 8; // Segundos que le quedan y con los que nació.
    private static final int R = 9, G = 10, B = 11; // Color.
    private static final int TAM = 12, CREC = 13; // Tamaño al nacer y cuánto crece por segundo.
    private static final int ALARGO = 14, ALFA = 15; // Estiramiento vertical y opacidad máxima.
    private static final int CAMPOS = 16; // Float por partícula.

    // Humo: sale del escape (esquina trasera izquierda) y la tasa crece con la velocidad.
    private static final float ESCAPE_LOCAL_X = -0.5f; // Del eje del auto, hacia su izquierda.
    private static final float ESCAPE_LOCAL_Z = 1.4f; // Detrás del paragolpes trasero (el frente es -Z local).
    private static final float ESCAPE_ALTURA = 0.3f; // Casi a ras del suelo.
    private static final float HUMO_POR_VELOCIDAD = 2.5f; // Partículas por segundo por cada unidad/s de velocidad (a 16: 40).
    private static final float HUMO_VIDA_MIN = 1.0f, HUMO_VIDA_MAX = 1.6f; // Duración.
    private static final float HUMO_TAMANO = 0.22f; // Tamaño al nacer.
    private static final float HUMO_CRECIMIENTO = 0.8f; // El humo se ensancha mientras sube.
    private static final float HUMO_GRIS = 0.55f; // Color gris.
    private static final float HUMO_ALFA = 0.5f; // Opacidad máxima.
    private static final float UMBRAL_MOVIMIENTO = 0.1f; // Igual que Auto.UMBRAL_MOVIMIENTO: detenido no echa humo.

    // Fuentes: gotas que salen de la punta del chorro y caen al agua.
    private static final float Y_AGUA = Parque.TOPE_CESPED + 0.46f; // Espejo de agua (Parque.dibujarFuente).
    private static final float Y_CHORRO = Y_AGUA + 1.6f; // Punta del chorro.
    private static final float FUENTE_POR_SEGUNDO = 35; // Gotas por segundo y por fuente.
    private static final float GRAVEDAD = -9.8f; // Aceleración de las gotas.
    private static final int MAX_EMISIONES_POR_CUADRO = 16; // Evita ráfagas si un cuadro dura mucho.

    // ==================== 2. ESTADO DEL POOL ====================
    private final float[] datos = new float[CAPACIDAD * CAMPOS]; // Todas las partículas, reservado una sola vez.
    private final int[] tipos = new int[CAPACIDAD]; // Tipo de cada partícula.
    private final int[] porTipo = new int[TIPOS]; // Cuántas partículas vivas hay de cada tipo.
    private final float[][] fuentes; // {x, z} del centro de cada parque.
    private final Random azar = new Random(7); // Semilla fija: comportamiento repetible.
    private int activas = 0; // Partículas vivas: ocupan las posiciones 0 .. activas-1.
    private float acumuladoHumo = 0; // Parte fraccionaria de partículas pendientes de emitir, por emisor.
    private float acumuladoFuente = 0;

    // ==================== 3. RECURSOS DE GPU ====================
    private final Shader shader = new Shader(); // Programa propio: particulas.vert + particulas.frag.
    private final Malla cuadro = new Malla(shader, cuadroUnitario()); // Cuadrado unitario que se reutiliza para cada partícula.

    /** Calcula dónde están las fuentes; no usa OpenGL. */
    public SistemaParticulas() {
        List<int[]> parques = Mapa.parques(); // Celdas {fila, columna} de los parques.
        fuentes = new float[parques.size()][2]; // Una fuente en el centro de cada parque.
        for (int i = 0; i < fuentes.length; i++) {
            fuentes[i][0] = Mapa.centro(parques.get(i)[1]); // X: la columna.
            fuentes[i][1] = Mapa.centro(parques.get(i)[0]); // Z: la fila.
        }
    }

    /** Dos triángulos en el plano XY, de -0.5 a 0.5, con el formato de Malla (posición y normal). */
    private static float[] cuadroUnitario() {
        float[][] esquinas = {{-0.5f, -0.5f}, {0.5f, -0.5f}, {0.5f, 0.5f}, {-0.5f, -0.5f}, {0.5f, 0.5f}, {-0.5f, 0.5f}};
        float[] v = new float[esquinas.length * Malla.FLOATS_POR_VERTICE];
        for (int i = 0; i < esquinas.length; i++) {
            int k = i * Malla.FLOATS_POR_VERTICE;
            v[k] = esquinas[i][0]; // X.
            v[k + 1] = esquinas[i][1]; // Y.
            v[k + 5] = 1; // Normal +Z (el shader no la usa, pero el VAO de Malla la espera).
        }
        return v;
    }

    /** Compila el shader y sube el cuadrado a la GPU (necesita el contexto OpenGL). */
    public void crear() {
        shader.crear(SHADER_VERTICES, SHADER_FRAGMENTOS);
        cuadro.crear();
    }

    /** Libera el programa y el cuadrado si llegaron a crearse. */
    public void eliminar() {
        cuadro.eliminar();
        shader.eliminar();
    }

    // ==================== 4. POOL: NACER, AVANZAR, MORIR, VACIAR ====================

    /** Nace una partícula simple (sin aceleración, estiramiento ni crecimiento); devuelve false si el pool está lleno. */
    public boolean emitir(int tipo, float x, float y, float z, float vx, float vy, float vz,
                          float vida, float r, float g, float b, float tamano) {
        return emitir(tipo, x, y, z, vx, vy, vz, 0, vida, r, g, b, tamano, 0, 1, 1);
    }

    /** Nace una partícula completa en la primera posición libre; devuelve false (y no hace nada) si el pool está lleno. */
    private boolean emitir(int tipo, float x, float y, float z, float vx, float vy, float vz, float ay,
                           float vida, float r, float g, float b, float tamano, float crecimiento, float alargo, float alfa) {
        if (activas >= CAPACIDAD || vida <= 0) { // Sin lugar (o ya muerta): se descarta.
            return false;
        }
        int base = activas * CAMPOS; // Primera posición libre.
        datos[base + X] = x;
        datos[base + Y] = y;
        datos[base + Z] = z;
        datos[base + VX] = vx;
        datos[base + VY] = vy;
        datos[base + VZ] = vz;
        datos[base + AY] = ay;
        datos[base + VIDA] = vida;
        datos[base + VIDA_TOTAL] = vida;
        datos[base + R] = r;
        datos[base + G] = g;
        datos[base + B] = b;
        datos[base + TAM] = tamano;
        datos[base + CREC] = crecimiento;
        datos[base + ALARGO] = alargo;
        datos[base + ALFA] = alfa;
        tipos[activas] = tipo;
        porTipo[tipo]++;
        activas++;
        return true;
    }

    /** Avanza todas las partículas dt segundos; las que se quedan sin vida mueren. */
    public void actualizar(float dt) {
        if (dt <= 0) { // Pausa o menú: nada cambia.
            return;
        }
        for (int i = activas - 1; i >= 0; i--) { // De atrás hacia adelante: matar copia la última viva, ya revisada.
            int base = i * CAMPOS;
            datos[base + VIDA] -= dt;
            datos[base + VY] += datos[base + AY] * dt; // Gravedad o flotación.
            datos[base + X] += datos[base + VX] * dt;
            datos[base + Y] += datos[base + VY] * dt;
            datos[base + Z] += datos[base + VZ] * dt;
            if (datos[base + VIDA] <= 0) {
                matar(i);
            }
        }
    }

    /** Libera la posición i copiando sobre ella la última partícula viva (la posición queda lista para reutilizarse). */
    private void matar(int i) {
        porTipo[tipos[i]]--;
        int ultima = activas - 1;
        if (i != ultima) { // Si no es la última, la última ocupa su lugar.
            System.arraycopy(datos, ultima * CAMPOS, datos, i * CAMPOS, CAMPOS);
            tipos[i] = tipos[ultima];
        }
        activas--;
    }

    /** Deja el pool vacío y los emisores sin pendientes (reinicio con R). */
    public void vaciar() {
        activas = 0;
        for (int t = 0; t < TIPOS; t++) {
            porTipo[t] = 0;
        }
        acumuladoHumo = 0;
        acumuladoFuente = 0;
    }

    // ==================== 5. EMISORES ====================

    /** Humo del escape: la tasa es proporcional a la velocidad (en cualquier sentido); detenido no sale nada. */
    public void emitirHumo(float dt, float autoX, float autoZ, float angulo, float velocidad) {
        float rapidez = Math.abs(velocidad);
        if (dt <= 0 || rapidez < UMBRAL_MOVIMIENTO) {
            return;
        }
        acumuladoHumo += HUMO_POR_VELOCIDAD * rapidez * dt;
        int n = (int) acumuladoHumo;
        acumuladoHumo -= n;
        float seno = (float) Math.sin(angulo);
        float coseno = (float) Math.cos(angulo);
        float escapeX = autoX + coseno * ESCAPE_LOCAL_X + seno * ESCAPE_LOCAL_Z; // Mismo cambio de ejes que Auto.pieza().
        float escapeZ = autoZ - seno * ESCAPE_LOCAL_X + coseno * ESCAPE_LOCAL_Z;
        for (int k = 0; k < n; k++) {
            float tamano = HUMO_TAMANO * (0.8f + 0.4f * azar.nextFloat());
            float vida = HUMO_VIDA_MIN + (HUMO_VIDA_MAX - HUMO_VIDA_MIN) * azar.nextFloat();
            float vx = seno * 0.5f + (azar.nextFloat() - 0.5f) * 0.4f; // Deriva hacia atrás del auto.
            float vz = coseno * 0.5f + (azar.nextFloat() - 0.5f) * 0.4f;
            float gris = HUMO_GRIS * (0.85f + 0.3f * azar.nextFloat());
            emitir(TIPO_HUMO, escapeX, ESCAPE_ALTURA, escapeZ, vx, 0.5f + 0.4f * azar.nextFloat(), vz, 0.2f,
                vida, gris, gris, gris, tamano, HUMO_CRECIMIENTO, 1, HUMO_ALFA);
        }
    }

    /** Salpicaduras: gotas que salen de la punta del chorro de cada fuente y caen al agua. */
    public void emitirFuentes(float dt) {
        if (dt <= 0) {
            return;
        }
        acumuladoFuente += FUENTE_POR_SEGUNDO * dt;
        int n = Math.min((int) acumuladoFuente, MAX_EMISIONES_POR_CUADRO);
        acumuladoFuente -= (int) acumuladoFuente;
        for (float[] fuente : fuentes) {
            for (int k = 0; k < n; k++) {
                double rumbo = azar.nextDouble() * 2 * Math.PI; // Dirección horizontal al azar.
                float alcance = 0.5f + 0.8f * azar.nextFloat(); // Velocidad horizontal: cae dentro del espejo de agua.
                float vy = 1.5f + 1.5f * azar.nextFloat(); // Sube un poco antes de caer.
                float altura = Y_CHORRO - Y_AGUA;
                float vida = (vy + (float) Math.sqrt(vy * vy - 2 * GRAVEDAD * altura)) / -GRAVEDAD; // Tarda esto en llegar al agua.
                emitir(TIPO_FUENTE, fuente[0], Y_CHORRO, fuente[1], (float) Math.cos(rumbo) * alcance, vy,
                    (float) Math.sin(rumbo) * alcance, GRAVEDAD, vida, 0.72f, 0.88f, 1.0f, 0.09f, 0, 1, 0.85f);
            }
        }
    }

    // ==================== 6. DIBUJO ====================

    /**
     * Dibuja las partículas vivas; debe llamarse después de la escena opaca y antes del HUD. Deja activo el programa y el
     * VAO de las partículas: el que llama debe volver a activar su shader y su cubo (Shader.usar, Cubo.enlazar).
     * @param brillo 1 de día; menor de noche, para que el humo gris no brille en la oscuridad.
     */
    public void dibujar(Camara camara, float autoX, float autoZ, float angulo, int ancho, int alto, float brillo) {
        if (activas == 0) { // Nada que dibujar: no toca el estado de OpenGL.
            return;
        }
        boolean habiaMezcla = glIsEnabled(GL_BLEND); // Estado previo, para restaurarlo igual.
        boolean habiaCaras = glIsEnabled(GL_CULL_FACE);
        boolean escribiaProfundidad = glGetBoolean(GL_DEPTH_WRITEMASK);
        int origen = glGetInteger(GL_BLEND_SRC_ALPHA);
        int destino = glGetInteger(GL_BLEND_DST_ALPHA);
        glDepthMask(false); // No escriben profundidad: se superponen sin taparse entre sí.
        glEnable(GL_BLEND); // Transparencia.
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA); // Mezcla por alfa.
        glDisable(GL_CULL_FACE); // El cuadrado mira a la cámara, pero así no depende del sentido de los triángulos.
        try { // Asegura que los estados vuelvan aunque algo falle.
            shader.usar();
            camara.configurar(shader, autoX, autoZ, angulo, ancho, alto); // Cámara y relación ancho/alto actuales.
            shader.decimal("uBrillo", brillo);
            for (int i = 0; i < activas; i++) {
                int base = i * CAMPOS;
                float restante = datos[base + VIDA] / datos[base + VIDA_TOTAL]; // 1 al nacer, 0 al morir.
                float edad = datos[base + VIDA_TOTAL] - datos[base + VIDA];
                float tamano = datos[base + TAM] + datos[base + CREC] * edad;
                shader.decimal("uAlfaParticula", datos[base + ALFA] * Math.min(1, 2 * restante)); // Se desvanece en la 2.ª mitad.
                cuadro.dibujar(datos[base + X], datos[base + Y], datos[base + Z], tamano, tamano * datos[base + ALARGO], 1,
                    datos[base + R], datos[base + G], datos[base + B]);
            }
        } finally {
            glBlendFunc(origen, destino);
            if (!habiaMezcla) {
                glDisable(GL_BLEND);
            }
            if (habiaCaras) {
                glEnable(GL_CULL_FACE);
            }
            glDepthMask(escribiaProfundidad); // Restaura la escritura de profundidad.
            glBindVertexArray(0);
        }
    }

    // ==================== 7. CONSULTAS (también para las pruebas) ====================

    /** Partículas vivas. */
    public int activas() {
        return activas;
    }

    /** Partículas vivas de un tipo. */
    public int activas(int tipo) {
        return porTipo[tipo];
    }

    /** Posición X de la partícula viva i. */
    public float x(int i) {
        return datos[i * CAMPOS + X];
    }

    /** Altura de la partícula viva i. */
    public float y(int i) {
        return datos[i * CAMPOS + Y];
    }

    /** Posición Z de la partícula viva i. */
    public float z(int i) {
        return datos[i * CAMPOS + Z];
    }

    /** Segundos de vida que le quedan a la partícula viva i. */
    public float vida(int i) {
        return datos[i * CAMPOS + VIDA];
    }

    /** Cantidad de fuentes (una por parque). */
    public int cantidadFuentes() {
        return fuentes.length;
    }
}
