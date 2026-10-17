package com.graphics.ciudad.iluminacion; // Agrupa el estado de las luces de la escena.

import com.graphics.ciudad.motor.Cubo; // Cada sombra es una caja muy fina.
import com.graphics.ciudad.motor.Estadisticas; // Cuenta sus draw calls como SOMBRAS.
import com.graphics.ciudad.motor.Figuras; // Mallas redondeadas: las reutilizan los árboles, las farolas y los edificios.
import com.graphics.ciudad.motor.Shader; // Modo sombra (uSombra), forma y opacidad.
import com.graphics.ciudad.mundo.Basureros; // Cestos de basura.
import com.graphics.ciudad.mundo.Edificio; // Huella de los edificios; su sombra planar reutiliza Edificio.dibujar().
import com.graphics.ciudad.mundo.Entorno; // Árboles del campo.
import com.graphics.ciudad.mundo.Mapa; // Celdas de edificio y de parque.
import com.graphics.ciudad.mundo.Parque; // Árboles y bancos de los parques.
import com.graphics.ciudad.trafico.Vehiculo; // Vehículos del tráfico.
import com.graphics.ciudad.vehiculo.Auto; // Auto del jugador.
import com.graphics.ciudad.vehiculo.Chata; // Remolque del jugador.
import com.graphics.ciudad.vehiculo.Cabina; // Forma de la cabina: la sombra planar del auto reutiliza su dibujo real.
import java.util.ArrayList; // Lista de sombras fijas.
import java.util.Collections; // Publica la lista sin permitir modificarla.
import java.util.List; // Tipo de esa lista.
import java.util.function.IntConsumer; // dibujarFarolas recibe el modo de huella (dentro/fuera) de cada pase.
import static org.lwjgl.opengl.GL33.*; // Mezcla (blending), máscara de profundidad y stencil.

/**
 * SOMBRAS FALSAS ("blob shadows"): una mancha oscura y semitransparente en el suelo, debajo de cada edificio, auto,
 * árbol, banco, basurero y luminaria de parque. No se calcula desde dónde viene la luz (eso serían sombras reales, mucho más caras): solo se oscurece
 * el suelo bajo el objeto, y eso alcanza para que parezca "apoyado" y no flotando.
 *
 * FORMA CON BORDES DIFUSOS: cada sombra se dibuja con el cubo aplastado (GROSOR_SOMBRA de alto), del tamaño y con el
 * giro del objeto. iluminacion.frag (rama uSombra) recibe la posición LOCAL del fragmento dentro del cubo (-0.5 a 0.5)
 * y calcula una "distancia al centro" d: 0 en el medio y 1 en el borde. La opacidad baja suavemente con smoothstep
 * entre el núcleo y el borde, así la mancha es un degradado circular y nunca un rectángulo con bordes duros.
 * El núcleo (uNucleoSombra) es la parte pareja del centro: en autos, árboles y bancos es NUCLEO_SOMBRA; en los
 * edificios coincide con las paredes, porque lo de adentro queda tapado y solo se ve el degradado sobre la acera.
 * uFormaSombra elige la curva: 2 = elipse (autos, árboles, bancos); 4 = "superelipse", un rectángulo de esquinas
 * redondeadas (edificios, que son cuadrados).
 *
 * BLENDING (MEZCLA): normalmente cada fragmento REEMPLAZA el color del píxel. Con glEnable(GL_BLEND) y
 * glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA), OpenGL COMBINA los dos colores:
 *     final = colorSombra · alfa + colorQueYaEstaba · (1 - alfa)
 * Con un color casi negro y alfa 0.45 el asfalto queda al 55 % de su brillo: se oscurece sin taparlo. Donde el
 * degradado lleva alfa a 0 (el borde), el suelo queda intacto.
 *
 * POR QUÉ SE DESACTIVA LA ESCRITURA DE PROFUNDIDAD (glDepthMask(false)):
 *  - La prueba de profundidad SIGUE activa: la sombra no se pinta encima de las ruedas o las paredes que están
 *    delante de ella (el edificio tapa la parte de la mancha que queda debajo suyo).
 *  - Pero no ESCRIBE su distancia: si lo hiciera, dos sombras superpuestas (dos autos juntos) se cortarían entre sí
 *    con un borde duro, y la parte casi transparente del degradado "taparía" lo que se dibuje después debajo de ella.
 *  - Por eso las sombras van AL FINAL de la escena opaca: la mezcla necesita que el suelo ya esté pintado.
 * ELEVACION_SOMBRA la separa apenas del suelo: si estuviera en el mismo plano, las dos superficies tendrían la misma
 * profundidad y parpadearían (z-fighting). De noche la sombra es más tenue (ALFA_SOMBRA_NOCHE): hay menos luz.
 * Solo se dibujan en la vista principal, no en el minimapa.
 *
 * SOMBRA PLANAR (usarSombrasProyectadas, tecla F1, de día): para edificios, árboles de los parques, farolas y autos
 * (jugador y tráfico), dibujarPlanares() reutiliza la MISMA geometría que la escena ya dibuja (Edificio.dibujar,
 * Parque.dibujarArbol, Iluminacion.dibujarFarolasConHuella, Auto.dibujar y Vehiculo.dibujar), pero con ciudad.vert en
 * modo uSombraPlanar: cada vértice se aplasta hacia uAlturaPlanoSombra siguiendo DIRECCION_SOL_DIA, la misma
 * dirección que usa iluminacion.frag para la luz del sol (ver el comentario de esa constante). El fragmento sale con
 * un color plano fijo (uColorSombraPlanar), sin iluminación: así no importa el color real de cada objeto reutilizado.
 * CUATRO PISOS: la calle, la acera (de un edificio o el aro de 0.5 alrededor del césped de un parque) y el césped
 * del parque NO están a la misma altura (0 y Edificio.ALTURA_ACERA, los dos; Parque.TOPE_CESPED). Por eso cada
 * objeto se dibuja una vez por cada piso donde su sombra puede caer: autos solo en la calle; edificios y farolas en
 * la calle y en su propia acera; árboles de parque en la calle, en el aro de acera y en el césped (ver
 * dibujarPlanares). HUELLA (uHuellaModo en iluminacion.frag): la prueba de profundidad SOLA no alcanza para elegir
 * la copia correcta, porque cada piso es un plano horizontal fijo sin importar qué haya realmente en ese (x, z); una
 * copia a nivel acera, más cerca de una cámara elevada que la calle de abajo, puede ganarle la prueba de profundidad
 * a la calle aunque ese punto sea calle de verdad, y quedar flotando. Por eso, además, cada copia se descarta por
 * fragmento (discard) si su (x, z) cae fuera de la porción de piso que le toca: manzana() y cesped() dan el
 * rectángulo de cada celda, y aplicarHuella() lo manda al shader antes de dibujar esa copia (dentro, fuera, o dentro
 * de un rectángulo y fuera de otro, para el aro). esHuellaAcera()/esHuellaParque() documentan la misma regla, para
 * cualquier punto, sin depender de qué celda se esté dibujando.
 * SOLAPES SIN OSCURECER EL DOBLE: a diferencia de las manchas (que ya podían superponerse), la sombra planar usa el
 * STENCIL: antes de cada fragmento exige stencil == 0 (GL_EQUAL) y, si lo dibuja, lo marca en 1 (GL_INCR); la
 * siguiente sombra (de otro objeto, o la segunda copia del mismo) que caiga en el mismo píxel ya no pasa la prueba.
 * Con la huella, dos pisos distintos ya no compiten por el mismo (x, z); el stencil sigue haciendo falta para que,
 * DENTRO de un mismo piso, dos sombras que se superponen (un edificio y la farola de su propia vereda) no oscurezcan
 * el doble. Por eso Ventana pide un framebuffer con stencil (GLFW_STENCIL_BITS) y Juego lo limpia cada cuadro junto
 * con color y profundidad.
 * Z-FIGHTING: en vez de glPolygonOffset, ciudad.vert deja el vértice proyectado en uAlturaPlanoSombra +
 * ELEVACION_SOMBRA_PLANAR, una fracción mínima sobre cada piso (mismo truco que ELEVACION_SOMBRA, pero más chico
 * porque no hay mancha debajo).
 * SOLO DE DÍA: de noche (o con usarSombrasProyectadas en false) todo vuelve a las manchas de siempre, sin cambios;
 * así se puede desactivar el cambio en vivo. Los árboles del campo (Entorno), los bancos, las luminarias de parque y
 * los basureros NO tienen sombra planar (quedan fuera de la prioridad pedida: calles y aceras dentro de la ciudad) y
 * conservan su mancha también de día.
 * COSTO: dibuja cada edificio, árbol de parque, farola y auto una vez más (aplastados). Si el FPS bajara demasiado,
 * la alternativa más liviana es desplazar y alargar las manchas actuales según DIRECCION_SOL_DIA en vez de proyectar
 * la geometría real.
 * SOMBRA SOBRE LOS AUTOS (OSCURECER_AUTOS_EN_SOMBRA, de día): además de dibujarse en el suelo, la sombra de cada
 * edificio puede caer sobre un auto. objetivoFactorSombraSol(x, z, esDeDia) prueba si el centro del auto está dentro
 * de esa sombra (la huella del edificio barrida hacia -DIRECCION_SOL_DIA.xz, ver su comentario) y devuelve a qué
 * factor debería ir uFactorSombraSol: FACTOR_SOMBRA_SOL ahí, 1 (sol pleno) en cualquier otro caso, incluida la noche.
 * Auto y Vehiculo llaman a esto en su propio actualizar() (ahí ya tienen dt), lo suavizan con
 * suavizarFactorSombraSol() para no saltar al entrar o salir de la sombra, y en su dibujar() mandan el resultado a
 * iluminacion.frag, que lo usa para bajar solo la luz direccional del sol (no el ambiente ni las demás luces).
 * Se comunica con: Juego (la dibuja al final de la escena y limpia el stencil), Shader e iluminacion.frag, Cubo,
 * Figuras, Mapa, Edificio, Parque y Entorno (sombras fijas y planares), Auto, Vehiculo y Cabina (sombras que siguen
 * posición y orientación, planas o en mancha, y la sombra de edificios sobre los autos).
 */
public class Sombras {

    // ==================== 1. CONSTANTES (valores ajustables) ====================
    public static final float[] COLOR_SOMBRA = {0.02f, 0.03f, 0.05f}; // Casi negro, apenas azulado.
    public static final float ALFA_SOMBRA_DIA = 0.45f; // Opacidad en el centro de la mancha, de día.
    public static final float ALFA_SOMBRA_NOCHE = 0.22f; // De noche, más tenue.
    public static final float ELEVACION_SOMBRA = 0.06f; // Altura sobre la superficie: evita el z-fighting (y queda sobre la pintura vial, a 0.04).
    public static final float GROSOR_SOMBRA = 0.01f; // Alto de la caja aplastada.
    public static final float FORMA_REDONDA = 2; // Exponente de la curva: 2 = elipse.
    public static final float FORMA_CUADRADA = 4; // 4 = rectángulo de esquinas redondeadas.
    public static final float NUCLEO_SOMBRA = 0.35f; // Fracción del centro con sombra pareja; de ahí al borde se difumina.
    public static final float ESCALA_SOMBRA_EDIFICIO = 1.4f; // La mancha mide 1.4 veces la base del edificio: 9.8, apenas menos que la acera (10).
    public static final float[] SOMBRA_AUTO = {2.3f, 3.5f}; // Ancho y largo de la mancha de un auto (el auto mide 1.65 × 2.6).
    public static final float ESCALA_SOMBRA_ARBOL = 1.25f; // Diámetro de la mancha respecto de la copa.
    public static final float[] SOMBRA_BANCO = {2.1f, 1.0f}; // Ancho y fondo de la mancha de un banco (mide 1.6 × 0.5).
    public static final float ESCALA_SOMBRA_BASURERO = 1.6f; // Diámetro de la mancha respecto del basurero (0.5 → 0.8).
    public static final float SOMBRA_LUMINARIA = 0.7f; // Diámetro de la mancha al pie de una luminaria globo (base de 0.24).

    // ---- Sombra planar (valores ajustables) ----
    private static boolean usarSombrasProyectadas = true; // false vuelve a las manchas de siempre también de día (tecla F1 en Juego).

    /** Indica si de día se usa la sombra planar proyectada (true) o solo las manchas (false). */
    public static boolean getUsarSombrasProyectadas() {
        return usarSombrasProyectadas;
    }

    /** Elige entre sombra planar proyectada y manchas; Juego lo alterna con F1. */
    public static void setUsarSombrasProyectadas(boolean usar) {
        usarSombrasProyectadas = usar;
    }
    public static final float ALFA_SOMBRA_PLANAR = 0.35f; // Opacidad pareja de la sombra planar (sin degradado).
    // Misma dirección que iluminacion.frag (DIRECCION_SOL_DIA, junto a INTENSIDAD_SOL_DIA): si se cambia el ángulo
    // del sol ahí, hay que repetirlo acá para que la sombra planar siga apuntando al mismo lado. No se normaliza:
    // la proyección (ciudad.vert) solo usa la razón entre sus componentes, así que la escala no importa.
    public static final float[] DIRECCION_SOL_DIA = {0.12f, 1.0f, 0.48f};
    private static final int CATEGORIA_SIEMPRE = 0; // Bancos, luminarias, basureros y árboles del campo: mancha siempre.
    private static final int CATEGORIA_EDIFICIO = 1; // De día con usarSombrasProyectadas, pasa a sombra planar.
    private static final int CATEGORIA_ARBOL_PARQUE = 2; // Ídem.
    private static final float ALTURA_CALLE = 0f; // Piso del asfalto: autos y la parte de cada sombra que cae en la calle.
    private static final boolean SOMBRAS_EN_ACERA = true; // false vuelve a proyectar edificios y farolas solo a nivel calle.

    // ---- Huella de cada copia (qué parte del piso le toca; ver aplicarHuella y el comentario de uHuellaModo) ----
    static final int HUELLA_SIN_FILTRO = 0; // Sin restricción: autos, y cualquier copia fuera de la sombra planar.
    static final int HUELLA_DENTRO = 1; // Solo dentro del rectángulo exterior (y fuera del interior, si se da uno).
    static final int HUELLA_FUERA = 2; // Solo fuera del rectángulo exterior.

    // ---- Sombra de edificios sobre los autos (valores ajustables) ----
    public static final boolean OSCURECER_AUTOS_EN_SOMBRA = true; // false desactiva el oscurecimiento (sin tocar las sombras falsas ni la planar).
    public static final float FACTOR_SOMBRA_SOL = 0.4f; // Luz solar directa que le queda al auto bajo la sombra de un edificio (1 = sol pleno).
    private static final float VELOCIDAD_SUAVIZADO_SOMBRA_SOL = 6f; // Qué tan rápido se acerca el factor al objetivo (más alto = más brusco).

    // Sombras de lo que no se mueve, calculadas una vez: cada una es
    // {x, y, z, anchoX, anchoZ, angulo, forma, nucleo, categoria}.
    public static final List<float[]> FIJAS = Collections.unmodifiableList(calcularFijas());

    // ==================== 2. SOMBRAS FIJAS ====================

    /** Edificios, árboles y bancos de los parques, y árboles del campo. */
    private static List<float[]> calcularFijas() {
        List<float[]> lista = new ArrayList<>(); // Resultado.
        float ladoEdificio = Mapa.ANCHO_EDIFICIO * ESCALA_SOMBRA_EDIFICIO; // Todos los edificios tienen base de 7 × 7.
        float nucleoEdificio = 1 / ESCALA_SOMBRA_EDIFICIO; // La pared está a 1/1.4 ≈ 0.71 del borde de la mancha: ahí empieza el degradado.
        for (int fila = 0; fila < Mapa.MAPA.length; fila++) { // Recorre el mapa.
            for (int columna = 0; columna < Mapa.MAPA[fila].length; columna++) {
                if (Mapa.tipo(fila, columna) == Mapa.EDIFICIO) { // Manzana con edificio.
                    float y = Edificio.ALTURA_ACERA + ELEVACION_SOMBRA; // Sobre la acera, donde se apoya el edificio.
                    lista.add(new float[] {Mapa.centro(columna), y, Mapa.centro(fila), ladoEdificio, ladoEdificio, 0,
                        FORMA_CUADRADA, nucleoEdificio, CATEGORIA_EDIFICIO});
                }
            }
        }
        for (int[] celda : Mapa.parques()) { // Árboles y bancos de cada parque, apoyados en el césped.
            float y = Parque.TOPE_CESPED + ELEVACION_SOMBRA; // Sobre el césped.
            float cx = Mapa.centro(celda[1]); // Centro del parque en X.
            float cz = Mapa.centro(celda[0]); // Centro del parque en Z.
            for (float[] a : Parque.arboles(celda[0], celda[1])) { // Mismos árboles que dibuja Parque.
                // Una mancha que pasara el borde del césped quedaría flotando sobre la acera, que está más baja:
                // se achica para que no salga del césped.
                float lejania = Math.max(Math.abs(a[0] - cx), Math.abs(a[1] - cz)); // Distancia al centro, en el eje más largo.
                float diametro = Math.min(a[4] * ESCALA_SOMBRA_ARBOL, 2 * (Parque.MITAD_CESPED - lejania)); // No sale del césped.
                lista.add(new float[] {a[0], y, a[1], diametro, diametro, 0, FORMA_REDONDA, NUCLEO_SOMBRA, CATEGORIA_ARBOL_PARQUE});
            }
            for (float[] b : Parque.bancos(celda[0], celda[1])) { // Bancos: siguen su orientación.
                lista.add(new float[] {b[0], y, b[1], SOMBRA_BANCO[0], SOMBRA_BANCO[1], b[2], FORMA_REDONDA, NUCLEO_SOMBRA, CATEGORIA_SIEMPRE});
            }
            for (float[] l : Parque.luminarias(celda[0], celda[1])) { // Luminarias globo: mancha chica al pie del poste.
                lista.add(new float[] {l[0], y, l[1], SOMBRA_LUMINARIA, SOMBRA_LUMINARIA, 0, FORMA_REDONDA, NUCLEO_SOMBRA, CATEGORIA_SIEMPRE});
            }
        }
        for (float[] b : Basureros.UBICACIONES) { // Basureros {x, yBase, z, tipo}: sobre el césped o la acera.
            float diametro = Basureros.DIAMETRO * ESCALA_SOMBRA_BASURERO;
            lista.add(new float[] {b[0], b[1] + ELEVACION_SOMBRA, b[2], diametro, diametro, 0, FORMA_REDONDA, NUCLEO_SOMBRA, CATEGORIA_SIEMPRE});
        }
        for (float[] a : Entorno.ARBOLES) { // Árboles del campo, sobre el pasto: sin sombra planar (fuera de la ciudad).
            float diametro = a[4] * ESCALA_SOMBRA_ARBOL; // Proporcional a la copa.
            lista.add(new float[] {a[0], Entorno.ALTURA_CAMPO + ELEVACION_SOMBRA, a[1], diametro, diametro, 0, FORMA_REDONDA, NUCLEO_SOMBRA, CATEGORIA_SIEMPRE});
        }
        return lista; // Sombras fijas.
    }

    // ==================== 3. DIBUJO ====================

    private final Shader shader; // Recibe el modo sombra.
    private final Cubo cubo; // Geometría de cada mancha.
    private final Figuras figuras; // Mallas redondeadas: árboles, farolas y piezas de los edificios.
    private final Cabina cabina; // Forma de la cabina: la reutiliza la sombra planar del auto y del tráfico.
    private final Edificio edificio; // El mismo Edificio de Ciudad: su dibujar() ya arma la forma real de cada manzana.

    /** Recibe el shader y la geometría compartidos; figuras, cabina y edificio alimentan la sombra planar. */
    public Sombras(Shader shader, Cubo cubo, Figuras figuras, Cabina cabina, Edificio edificio) {
        this.shader = shader; // Guarda el programa.
        this.cubo = cubo; // Guarda el cubo.
        this.figuras = figuras; // Guarda las mallas redondeadas.
        this.cabina = cabina; // Guarda la forma de la cabina.
        this.edificio = edificio; // Guarda el armado de los edificios (el mismo que dibuja Ciudad).
    }

    /**
     * Dibuja todas las sombras; va después de todo lo opaco de la vista principal. dibujarFarolas es
     * Iluminacion.dibujarFarolasConHuella: la sombra planar la llama de nuevo para proyectar la misma geometría,
     * pasándole HUELLA_DENTRO o HUELLA_FUERA según el pase (ver dibujarPlanares).
     */
    public void dibujar(Auto auto, Chata chata, List<Vehiculo> vehiculos, float factorNoche, IntConsumer dibujarFarolas) {
        // factorNoche (Iluminacion.factorNoche) es 0 de día y 1 de noche. En los extremos todo es igual que antes; en la
        // transición las sombras se desvanecen con el mismo factor que la luz: la planar baja a 0 y las manchas de lo que
        // la planar reemplaza (edificios, árboles de parque, autos) suben de 0 al valor de noche; las manchas fijas
        // (bancos, luminarias, basureros, árboles del campo) pasan del alfa de día al de noche.
        boolean planarDeDia = usarSombrasProyectadas && factorNoche < 1; // Hay sombra planar mientras no sea noche plena.
        boolean manchasDeNoche = !planarDeDia || factorNoche > 0; // Manchas de edificios, árboles de parque y autos.
        float alfaFija = ALFA_SOMBRA_DIA + (ALFA_SOMBRA_NOCHE - ALFA_SOMBRA_DIA) * factorNoche; // Manchas que siempre existen.
        float alfaNoche = planarDeDia ? ALFA_SOMBRA_NOCHE * factorNoche : ALFA_SOMBRA_NOCHE; // Manchas que reemplaza la planar.
        Estadisticas.forzarCategoria(Estadisticas.SOMBRAS); // Manchas y sombra planar (que reutiliza edificios, árboles, farolas y autos) cuentan juntas.
        glEnable(GL_BLEND); // Activa la mezcla: la sombra se combina con el suelo.
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA); // final = sombra · alfa + suelo · (1 - alfa).
        glDepthMask(false); // Prueba la profundidad, pero no la escribe (ver comentario de la clase).
        try { // Asegura que el estado opaco vuelva aunque algo falle.
            // Las manchas son cajas de 0.01 de alto: sin culling se dibujan la cara de arriba Y la de abajo, y la mezcla
            // se aplica dos veces (ese es el oscuro que siempre tuvieron). Con culling quedaría una sola y todas las
            // manchas se verían más claras; se desactiva solo aquí para conservar su aspecto.
            glDisable(GL_CULL_FACE);
            shader.entero("uSombra", 1); // iluminacion.frag calcula el degradado circular.
            for (float[] s : FIJAS) { // Edificios, árboles, bancos, luminarias y basureros.
                boolean reemplazable = s[8] == CATEGORIA_EDIFICIO || s[8] == CATEGORIA_ARBOL_PARQUE; // Los que la planar proyecta de día.
                if (!reemplazable || manchasDeNoche) { // Si no, dibujarPlanares() es lo único que los dibuja: evita oscurecer dos veces.
                    shader.decimal("uAlfa", reemplazable ? alfaNoche : alfaFija); // Opacidad en el centro.
                    mancha(s[0], s[1], s[2], s[3], s[4], s[5], s[6], s[7]); // Una mancha por objeto.
                }
            }
            if (manchasDeNoche) { // Autos: de noche, o mientras la planar se desvanece.
                shader.decimal("uAlfa", alfaNoche);
                float y = ELEVACION_SOMBRA; // Los autos están sobre el asfalto (Y = 0).
                mancha(auto.getX(), y, auto.getZ(), SOMBRA_AUTO[0], SOMBRA_AUTO[1], auto.getAngulo(), FORMA_REDONDA, NUCLEO_SOMBRA); // Jugador.
                mancha(chata.getX(), y, chata.getZ(), Chata.ANCHO + 0.8f, Chata.LARGO_CAJA + 0.9f, chata.getAngulo(), FORMA_REDONDA, NUCLEO_SOMBRA); // Chata.
                for (Vehiculo v : vehiculos) { // Tráfico.
                    mancha(v.getX(), y, v.getZ(), SOMBRA_AUTO[0], SOMBRA_AUTO[1], v.getAngulo(), FORMA_REDONDA, NUCLEO_SOMBRA); // Sigue posición y giro.
                }
            }
            shader.entero("uSombra", 0); // Vuelve al material normal antes de la sombra planar (otra rama del shader).
            glEnable(GL_CULL_FACE); // La sombra planar reutiliza geometría real y sí usa el culling.
            if (planarDeDia) { // De día (y en la transición), si no se desactivó con usarSombrasProyectadas (F1).
                dibujarPlanares(auto, chata, vehiculos, dibujarFarolas, ALFA_SOMBRA_PLANAR * (1 - factorNoche));
            }
        } finally {
            shader.entero("uSombra", 0); // Vuelve al material normal.
            shader.decimal("uAlfa", 1); // Opacidad completa.
            glDepthMask(true); // Vuelve a escribir profundidad.
            glDisable(GL_BLEND); // La escena 3D es opaca: sin mezcla.
            glEnable(GL_CULL_FACE); // Deja el culling activo como lo espera el resto del cuadro.
            Estadisticas.liberarCategoria();
        }
    }

    /** Una mancha: caja aplastada de anchoX × anchoZ, girada como el objeto; el shader le da el degradado. */
    private void mancha(float x, float y, float z, float anchoX, float anchoZ, float angulo, float forma, float nucleo) {
        shader.decimal("uFormaSombra", forma); // Elipse o rectángulo redondeado.
        shader.decimal("uNucleoSombra", nucleo); // Dónde empieza a difuminarse.
        cubo.cajaGirada(x, y, z, anchoX, GROSOR_SOMBRA, anchoZ, COLOR_SOMBRA[0], COLOR_SOMBRA[1], COLOR_SOMBRA[2], angulo);
    }

    // ==================== 4. SOMBRA PLANAR (día) ====================

    /**
     * Proyecta sobre el piso que corresponda la misma geometría de los edificios, los árboles de los parques, las
     * farolas y los autos (ver el comentario de la clase): activa uSombraPlanar (ciudad.vert aplasta cada vértice
     * hacia uAlturaPlanoSombra siguiendo DIRECCION_SOL_DIA) y uColorSombraPlanar (iluminacion.frag ignora el color
     * real de cada objeto), y vuelve a llamar a sus dibujar() de siempre.
     * Como la calle, la acera y el césped están a alturas distintas, cada objeto se dibuja una vez por cada piso
     * donde puede caer su sombra, de más bajo a más alto, con el mismo stencil activo durante los pasos:
     *   a) calle (ALTURA_CALLE): autos siempre; edificios, árboles de parque y farolas también, pero solo la parte
     *      de su sombra que cae FUERA de la manzana de origen (HUELLA_FUERA con la propia manzana, ver manzana()).
     *   b) acera (Edificio.ALTURA_ACERA): edificios y farolas, solo DENTRO de su propia manzana (HUELLA_DENTRO).
     *      Desactivado con SOMBRAS_EN_ACERA (en false): esos edificios y farolas solo proyectan a nivel calle.
     *   d) aro de acera del parque, misma altura que b): el borde de 0.5 entre el césped y el final de la manzana
     *      (también acera, ver Ciudad.dibujar) es DENTRO de la manzana pero FUERA del césped.
     *   c) césped del parque (Parque.TOPE_CESPED), el piso más alto: árboles de parque, solo DENTRO del césped.
     * SIN LA HUELLA, la copia de un piso más alto (acera o césped) "flotaría" sobre un piso más bajo que queda más
     * cerca de la cámara (gana la prueba de profundidad aunque ese no sea su piso): por eso cada copia se restringe
     * a la porción exacta de (x, z) que le corresponde, además del propio stencil, que sigue evitando que dos copias
     * del MISMO piso se dibujen dos veces sobre el mismo píxel.
     */
    private void dibujarPlanares(Auto auto, Chata chata, List<Vehiculo> vehiculos, IntConsumer dibujarFarolas, float alfa) {
        glEnable(GL_STENCIL_TEST); // Activa la prueba de stencil solo para esta sección.
        glStencilFunc(GL_EQUAL, 0, 0xFF); // Solo dibuja donde ninguna sombra planar pasó todavía por este píxel.
        glStencilOp(GL_KEEP, GL_KEEP, GL_INCR); // Si lo dibuja (y pasa la prueba de profundidad), lo marca para la próxima.
        shader.entero("uSombraPlanar", 1); // ciudad.vert aplasta los vértices; iluminacion.frag usa el color plano.
        shader.vector("uColorSombraPlanar", COLOR_SOMBRA[0], COLOR_SOMBRA[1], COLOR_SOMBRA[2]); // Mismo color que la mancha.
        shader.decimal("uAlfa", alfa); // Opacidad pareja, sin degradado (ALFA_SOMBRA_PLANAR de día; se desvanece con la noche).
        shader.vector("uDireccionSol", DIRECCION_SOL_DIA[0], DIRECCION_SOL_DIA[1], DIRECCION_SOL_DIA[2]); // Hacia dónde proyectar.
        try {
            // a) Piso de calle: lo que cae en el asfalto (autos siempre están acá; edificios, árboles y farolas
            // también, para la parte de su sombra que se extiende más allá de la acera o del césped).
            shader.decimal("uAlturaPlanoSombra", ALTURA_CALLE);
            dibujarEdificios(HUELLA_FUERA);
            dibujarArbolesParqueFuera();
            dibujarFarolas.accept(HUELLA_FUERA); // Iluminacion.dibujarFarolasConHuella(): base, poste, brazo, pantalla y bombilla.
            sinHuella(); // Los autos no tienen copia de acera: se dibujan sin restricción, como siempre.
            auto.dibujar(cubo, figuras, shader, cabina, false); // Carrocería, cabina y ruedas del jugador.
            chata.dibujar(cubo, figuras, shader, auto); // Su chata, con el mismo dibujo real.
            for (Vehiculo v : vehiculos) { // Tráfico: misma forma, sigue su posición y ángulo de este cuadro.
                v.dibujar(cubo, figuras, shader, cabina);
            }

            // b) Piso de acera: lo que cae en la vereda, donde se apoyan los edificios y las farolas (desactivado:
            // SOMBRAS_EN_ACERA en false).
            if (SOMBRAS_EN_ACERA) {
                shader.decimal("uAlturaPlanoSombra", Edificio.ALTURA_ACERA);
                dibujarEdificios(HUELLA_DENTRO);
                dibujarFarolas.accept(HUELLA_DENTRO);
            }

            // d) Aro de acera del parque, misma altura que b): incluso sin SOMBRAS_EN_ACERA, el borde de 0.5 fuera
            // del césped (también acera, no calle) necesita su propia copia a nivel acera; si no, la sombra de un
            // árbol que lo alcance quedaría sin dibujar (ni calle ni césped son su piso ahí).
            shader.decimal("uAlturaPlanoSombra", Edificio.ALTURA_ACERA);
            dibujarArbolesParqueAro();

            // c) Piso de césped, el más alto: lo que cae en el pasto, donde se apoyan los árboles de cada parque.
            shader.decimal("uAlturaPlanoSombra", Parque.TOPE_CESPED);
            dibujarArbolesParqueCesped();
        } finally {
            sinHuella(); // No deja un filtro puesto para el resto de la escena.
            shader.entero("uSombraPlanar", 0); // Vuelve a la proyección normal de la cámara.
            glDisable(GL_STENCIL_TEST); // El resto de la escena no usa stencil.
        }
    }

    // ==================== HUELLA: qué parte del piso le toca a cada copia (ver uHuellaModo en iluminacion.frag) ====

    /** Sin restricción: la copia se dibuja en cualquier (x, z) de la caja (autos, o al salir de la sombra planar). */
    private void sinHuella() {
        shader.entero("uHuellaModo", HUELLA_SIN_FILTRO);
    }

    /** DENTRO o FUERA de un rectángulo {minX, minZ, maxX, maxZ}, sin agujero interior (edificios, farolas, césped). */
    private void aplicarHuella(int modo, float[] rectangulo) {
        aplicarHuella(modo, rectangulo, VACIO);
    }

    /** Con un agujero interior (el aro de un parque: dentro de su manzana, pero fuera de su césped). */
    private void aplicarHuella(int modo, float[] rectangulo, float[] agujero) {
        shader.entero("uHuellaModo", modo);
        shader.vector2("uHuellaMin", rectangulo[0], rectangulo[1]);
        shader.vector2("uHuellaMax", rectangulo[2], rectangulo[3]);
        shader.vector2("uHuellaExcluirMin", agujero[0], agujero[1]);
        shader.vector2("uHuellaExcluirMax", agujero[2], agujero[3]);
    }

    private static final float[] VACIO = {1, 0, 0, 0}; // minX (1) > maxX (0): "dentro de este agujero" nunca es cierto.

    /**
     * Rectángulo {minX, minZ, maxX, maxZ} de la manzana (fila, columna): toda su acera, de 10 × 10 (Mapa.TAM_CELDA),
     * la tenga o no un edificio arriba (Ciudad.dibujar dibuja esa losa para cualquier manzana que no sea calle).
     */
    static float[] manzana(int fila, int columna) {
        float cx = Mapa.centro(columna), cz = Mapa.centro(fila), mitad = Mapa.TAM_CELDA / 2;
        return new float[] {cx - mitad, cz - mitad, cx + mitad, cz + mitad};
    }

    /** Rectángulo del césped (9 × 9, Parque.MITAD_CESPED) de la manzana de un parque. */
    private static float[] cesped(int fila, int columna) {
        float cx = Mapa.centro(columna), cz = Mapa.centro(fila), mitad = Parque.MITAD_CESPED;
        return new float[] {cx - mitad, cz - mitad, cx + mitad, cz + mitad};
    }

    /** Envía uHuellaModo y el rectángulo al shader activo: lo reutiliza Iluminacion.dibujarFarolasConHuella. */
    static void aplicarHuella(Shader shader, int modo, float minX, float minZ, float maxX, float maxZ) {
        shader.entero("uHuellaModo", modo);
        shader.vector2("uHuellaMin", minX, minZ);
        shader.vector2("uHuellaMax", maxX, maxZ);
        shader.vector2("uHuellaExcluirMin", VACIO[0], VACIO[1]);
        shader.vector2("uHuellaExcluirMax", VACIO[2], VACIO[3]);
    }

    /** Cada manzana con edificio: misma forma que Ciudad dibuja, ahora aplastada hacia uAlturaPlanoSombra, restringida
     * a HUELLA_DENTRO o HUELLA_FUERA de su propia manzana (manzana()) según el pase que esté dibujando. */
    private void dibujarEdificios(int modo) {
        for (int fila = 0; fila < Mapa.MAPA.length; fila++) {
            for (int columna = 0; columna < Mapa.MAPA[fila].length; columna++) {
                if (Mapa.tipo(fila, columna) == Mapa.EDIFICIO) {
                    aplicarHuella(modo, manzana(fila, columna));
                    edificio.dibujar(fila, columna);
                }
            }
        }
    }

    /** Pase de calle: los árboles de cada parque, excluyendo TODA su manzana (césped y aro de acera). */
    private void dibujarArbolesParqueFuera() {
        for (int[] celda : Mapa.parques()) {
            aplicarHuella(HUELLA_FUERA, manzana(celda[0], celda[1]));
            dibujarArbolesDe(celda);
        }
    }

    /** Pase de césped: los árboles de cada parque, solo dentro de su césped (9 × 9). */
    private void dibujarArbolesParqueCesped() {
        for (int[] celda : Mapa.parques()) {
            aplicarHuella(HUELLA_DENTRO, cesped(celda[0], celda[1]));
            dibujarArbolesDe(celda);
        }
    }

    /** Pase del aro de acera: los árboles de cada parque, dentro de su manzana pero fuera de su césped. */
    private void dibujarArbolesParqueAro() {
        for (int[] celda : Mapa.parques()) {
            aplicarHuella(HUELLA_DENTRO, manzana(celda[0], celda[1]), cesped(celda[0], celda[1]));
            dibujarArbolesDe(celda);
        }
    }

    /** Árboles de la celda de parque dada: mismo tronco y copa que dibuja Parque, aplastados hacia uAlturaPlanoSombra. */
    private void dibujarArbolesDe(int[] celda) {
        for (float[] arbol : Parque.arboles(celda[0], celda[1])) {
            Parque.dibujarArbol(figuras, arbol, Parque.TOPE_CESPED);
        }
    }

    // ==================== FUNCIÓN PURA: a qué piso pertenece un punto (sin OpenGL; la prueba la usa directo) ====
    // Documenta, para cualquier (x, z), la misma regla que ya aplican manzana()/cesped() con el rectángulo de una
    // celda conocida: existe para que SombrasHuellaTest pueda verificarla sin ventana ni shaders.

    /**
     * true si (x, z) cae sobre una acera: toda la manzana de un edificio, o el aro de 0.5 alrededor del césped de
     * un parque (Ciudad.dibujar cubre cualquier manzana que no sea calle con la misma losa de acera, tenga o no
     * edificio encima). Sobre una calle, o fuera del mapa, da false.
     */
    public static boolean esHuellaAcera(float x, float z) {
        int fila = Mapa.indiceCelda(z);
        int columna = Mapa.indiceCelda(x);
        if (!dentroDelMapa(fila, columna)) {
            return false; // Fuera de la matriz: ni acera ni parque.
        }
        int tipo = Mapa.tipo(fila, columna);
        if (tipo == Mapa.EDIFICIO) {
            return true; // Toda la manzana, con o sin edificio encima en ese punto exacto, es acera.
        }
        return tipo == Mapa.PARQUE && !dentroDelCesped(x, z, fila, columna); // Del parque, todo menos su césped.
    }

    /** true si (x, z) cae sobre el césped de un parque (9 × 9, Parque.MITAD_CESPED). Fuera de ahí da false. */
    public static boolean esHuellaParque(float x, float z) {
        int fila = Mapa.indiceCelda(z);
        int columna = Mapa.indiceCelda(x);
        return dentroDelMapa(fila, columna) && Mapa.tipo(fila, columna) == Mapa.PARQUE && dentroDelCesped(x, z, fila, columna);
    }

    /** true si (fila, columna) cae dentro de la matriz de Mapa.MAPA. */
    private static boolean dentroDelMapa(int fila, int columna) {
        return fila >= 0 && fila < Mapa.MAPA.length && columna >= 0 && columna < Mapa.MAPA[fila].length;
    }

    /** true si (x, z) cae dentro del césped (9 × 9) de la manzana de parque (fila, columna). */
    private static boolean dentroDelCesped(float x, float z, int fila, int columna) {
        float cx = Mapa.centro(columna), cz = Mapa.centro(fila);
        return Math.abs(x - cx) <= Parque.MITAD_CESPED && Math.abs(z - cz) <= Parque.MITAD_CESPED;
    }

    // ==================== 5. SOMBRA DE EDIFICIOS SOBRE LOS AUTOS ====================
    // Oscurece solo la luz solar directa (uFactorSombraSol en iluminacion.frag) del auto que esté bajo la sombra
    // de un edificio, de día. No dibuja nada: Auto y Vehiculo llaman a objetivoFactorSombraSol() en su actualizar()
    // (ahí ya tienen dt) y guardan el resultado suavizado para usarlo en su propio dibujar().

    /** Hacia dónde debería ir factorSombraSol: FACTOR_SOMBRA_SOL bajo la sombra de un edificio, 1 en cualquier otro caso. */
    public static float objetivoFactorSombraSol(float x, float z, boolean esDeDia) {
        if (!OSCURECER_AUTOS_EN_SOMBRA || !esDeDia) { // De noche, o con el efecto desactivado, siempre sol pleno.
            return 1f;
        }
        return enSombraDeEdificio(x, z) ? FACTOR_SOMBRA_SOL : 1f;
    }

    /** Acerca factorActual a objetivo con un decaimiento exponencial (independiente de FPS, como Auto.factorFrenado). */
    public static float suavizarFactorSombraSol(float factorActual, float objetivo, float deltaTime) {
        float avance = 1f - (float) Math.exp(-VELOCIDAD_SUAVIZADO_SOMBRA_SOL * deltaTime); // Fracción recorrida en este cuadro.
        return factorActual + (objetivo - factorActual) * avance;
    }

    /** true si (x, z) cae dentro de la sombra que algún edificio proyecta sobre el suelo, según DIRECCION_SOL_DIA. */
    private static boolean enSombraDeEdificio(float x, float z) {
        float mitadBase = Mapa.ANCHO_EDIFICIO / 2; // Medio lado de la huella real del edificio (7 / 2, sin la escala de la mancha).
        for (int fila = 0; fila < Mapa.MAPA.length; fila++) {
            for (int columna = 0; columna < Mapa.MAPA[fila].length; columna++) {
                if (Mapa.tipo(fila, columna) == Mapa.EDIFICIO
                    && puntoEnSombraDe(x, z, Mapa.centro(columna), Mapa.centro(fila), mitadBase, Edificio.alturaTotal(fila, columna))) {
                    return true; // Alcanza con un edificio: no hace falta seguir recorriendo el mapa.
                }
            }
        }
        return false;
    }

    /**
     * La sombra de un edificio es su huella (un cuadrado) barrida hacia -DIRECCION_SOL_DIA.xz una distancia
     * altura / DIRECCION_SOL_DIA.y (mismo t que usa ciudad.vert para aplastar un vértice de esa altura al suelo):
     * es la unión de Minkowski del cuadrado con ese segmento. (x, z) cae ahí si, y solo si, el segmento que va de
     * (x, z) a (x, z) - desplazamiento (recorrerlo "deshace" el barrido) toca el cuadrado para algún punto suyo.
     */
    private static boolean puntoEnSombraDe(float x, float z, float cx, float cz, float mitadBase, float altura) {
        if (altura <= 0) { // Edificio sin volumen (no debería pasar, pero evita dividir por 0 de forma rara).
            return false;
        }
        float t = altura / DIRECCION_SOL_DIA[1]; // Mismo cociente que (vMundo.y - uAlturaPlanoSombra) / uDireccionSol.y en ciudad.vert.
        float desplazamientoX = -DIRECCION_SOL_DIA[0] * t;
        float desplazamientoZ = -DIRECCION_SOL_DIA[2] * t;
        return segmentoTocaCuadrado(x, z, x - desplazamientoX, z - desplazamientoZ, cx, cz, mitadBase);
    }

    /** true si el segmento (ax,az)-(bx,bz) toca el cuadrado centrado en (cx,cz) de media extensión mitad (método de las franjas). */
    private static boolean segmentoTocaCuadrado(float ax, float az, float bx, float bz, float cx, float cz, float mitad) {
        float tDesde = 0f, tHasta = 1f; // Tramo del segmento (en parámetro t) todavía dentro de las dos franjas.
        float dx = bx - ax;
        if (dx == 0f) { // Segmento vertical en X: solo importa si ax ya cae en la franja.
            if (ax < cx - mitad || ax > cx + mitad) {
                return false;
            }
        } else {
            float t1 = (cx - mitad - ax) / dx;
            float t2 = (cx + mitad - ax) / dx;
            tDesde = Math.max(tDesde, Math.min(t1, t2));
            tHasta = Math.min(tHasta, Math.max(t1, t2));
            if (tDesde > tHasta) {
                return false;
            }
        }
        float dz = bz - az;
        if (dz == 0f) { // Segmento horizontal en Z: solo importa si az ya cae en la franja.
            if (az < cz - mitad || az > cz + mitad) {
                return false;
            }
        } else {
            float t1 = (cz - mitad - az) / dz;
            float t2 = (cz + mitad - az) / dz;
            tDesde = Math.max(tDesde, Math.min(t1, t2));
            tHasta = Math.min(tHasta, Math.max(t1, t2));
        }
        return tDesde <= tHasta; // Queda un tramo común a ambas franjas: el segmento atraviesa el cuadrado.
    }
}
