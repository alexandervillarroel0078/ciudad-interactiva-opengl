package com.graphics.ciudad.motor; // Agrupa las piezas técnicas: ventana, shaders, geometría y cámara.

import java.util.function.BiConsumer; // Recibe el método que atenderá cada movimiento de mouse-look (dx, dy).
import java.util.function.BooleanSupplier; // Recibe el método que indica si el modo actual usa mouse-look (ORBITAL/AEREA).
import java.util.function.DoubleConsumer; // Recibe el método que atenderá cada giro de la ruedita.
import java.util.function.IntConsumer; // Recibe el método que atenderá cada tecla presionada.
import org.lwjgl.glfw.Callbacks; // Permite liberar los callbacks de la ventana al cerrar.
import org.lwjgl.opengl.GL; // Carga las funciones OpenGL disponibles en el equipo.
import static org.lwjgl.glfw.GLFW.*; // Importa las funciones y constantes de ventanas y teclado.
import static org.lwjgl.opengl.GL33.*; // Importa las funciones OpenGL hasta la versión 3.3.

/**
 * VENTANA: envoltorio de GLFW para la ventana, el contexto OpenGL, el teclado y el mouse.
 * Responsable de: crear la ventana y el contexto OpenGL 3.3 Core, registrar los callbacks de teclado y mouse
 * (incluido el mouse-look de las cámaras ORBITAL y AEREA: cursor oculto y capturado, con motion cruda si el sistema
 * la soporta), informar el tamaño real del framebuffer (resize), cambiar el título, presentar cada imagen y
 * destruirse.
 * Se comunica con: Juego, que la crea, le entrega el método tecla() y la consulta en cada vuelta del ciclo (y que
 * apaga el mouse-look con Esc o al cambiar de modo); Auto, que lee las teclas mantenidas mediante pulsada(); Camara,
 * que recibe los movimientos de mouse-look (como arrastre o como desplazamiento, según Shift) y la ruedita, y que
 * le dice (usaMouseLook()) si el modo actual es ORBITAL o AEREA.
 */
public class Ventana {

    public static final int ANCHO_MINIMO = 640; // Ancho mínimo de la ventana, en píxeles.
    public static final int ALTO_MINIMO = 480; // Alto mínimo de la ventana: con menos, el panel de estado y el de ayuda se pisan.

    private long ventana; // Identificador que GLFW asigna a la ventana.
    private int ancho = 1100; // Ancho inicial de la ventana; después contiene píxeles del framebuffer.
    private int alto = 760; // Alto inicial de la ventana; después contiene píxeles del framebuffer.
    private final int[] anchoReal = new int[1]; // Reserva espacio para que GLFW escriba el ancho en píxeles.
    private final int[] altoReal = new int[1]; // Reserva espacio para que GLFW escriba el alto en píxeles.
    private double ultimoX; // Última posición X conocida del cursor, en píxeles de la ventana (referencia del mouse-look).
    private double ultimoY; // Última posición Y conocida del cursor (crece hacia abajo).
    private boolean mouseLookActivo = false; // true mientras ORBITAL o AEREA tienen el cursor oculto y capturado.
    private boolean saltoPendienteMouseLook = false; // true en el primer evento de cursor tras activar: solo fija la referencia.

    /** Configura GLFW y OpenGL, igual que las clases de cámara del proyecto original. */
    public void crear(String titulo, IntConsumer alPresionar) {
        if (!glfwInit()) { // Intenta iniciar la biblioteca que administra ventanas y entrada.
            throw new IllegalStateException("No se pudo iniciar GLFW"); // Detiene el arranque si GLFW falla.
        }
        glfwDefaultWindowHints(); // Restablece las opciones de ventana antes de personalizarlas.
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3); // Solicita la versión mayor de OpenGL.
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3); // Solicita OpenGL 3.3.
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE); // Usa el perfil moderno basado en shaders.
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE); // Solicita compatibilidad con contextos modernos de macOS.
        glfwWindowHint(GLFW_STENCIL_BITS, 8); // Framebuffer con stencil: Sombras.dibujarPlanares lo usa para no oscurecer dos veces donde se solapan.
        ventana = glfwCreateWindow(ancho, alto, titulo, 0, 0); // Crea una ventana normal.
        if (ventana == 0) { // GLFW devuelve cero cuando no consigue crear la ventana.
            throw new IllegalStateException("No se pudo crear la ventana OpenGL"); // Informa el fallo de creación.
        }
        glfwSetWindowSizeLimits(ventana, ANCHO_MINIMO, ALTO_MINIMO, GLFW_DONT_CARE, GLFW_DONT_CARE); // Evita que el HUD y la ayuda se pisen.
        glfwMakeContextCurrent(ventana); // Selecciona la ventana que recibirá las llamadas OpenGL.
        glfwSwapInterval(1); // Sincroniza la presentación con la pantalla para reducir cortes de imagen.
        GL.createCapabilities(); // Carga las funciones OpenGL del contexto actual.
        glEnable(GL_DEPTH_TEST); // Hace que las superficies cercanas oculten las lejanas.
        glfwSetKeyCallback(ventana, (ventanaEvento, key, scancode, action, mods) -> { // Registra la función que recibe eventos de teclado.
            if (action == GLFW_PRESS) { // Responde una sola vez al presionar, no a las repeticiones de la tecla.
                alPresionar.accept(key); // Entrega la tecla al método tecla() de Juego, que la reparte entre los módulos.
            }
        }); // Termina el registro del callback de teclado.
    }

    /**
     * Registra los callbacks del mouse, igual que el de teclado: GLFW los llama durante procesarEventos().
     * - Botón: solo importa el derecho, y solo si usaMouseLook da true (ORBITAL o AEREA). Al presionarse (no al
     *   soltarse) alterna el mouse-look: activarMouseLook() u desactivarMouseLookInterno(). El izquierdo no hace
     *   nada en ningún modo; en SEGUIMIENTO (usaMouseLook da false) ningún botón hace nada.
     * - Cursor: sin mouse-look activo no hace nada (el mouse no mueve la cámara si no está encendido, y en
     *   SEGUIMIENTO nunca se enciende). Con mouse-look activo, cada movimiento consulta si Shift (izquierdo o
     *   derecho) está sostenido y entrega el delta a alDesplazar si lo está, o a alArrastrar si no; Camara decide
     *   qué hace cada uno según el modo (desplazar() solo actúa en AEREA). El primer evento tras activar solo fija
     *   la referencia (saltoPendienteMouseLook), para no generar un salto con la posición virtual que GLFW usa en
     *   modo GLFW_CURSOR_DISABLED.
     * - Ruedita: entrega a alRodar los pasos verticales (positivo = hacia adelante), en cualquier modo.
     * - Foco: si la ventana lo pierde, se apaga el mouse-look para no quedar con el cursor capturado en segundo plano.
     * Ventana no decide qué modo usa cada gesto ni qué efecto tiene: usaMouseLook (Camara::usaMouseLook) solo le
     * dice si el modo actual es ORBITAL o AEREA, y Shift solo decide a cuál de los dos métodos despachar; el resto
     * de la semántica sigue en Camara, que ignora lo que el modo actual no usa.
     * Callbacks.glfwFreeCallbacks() libera todos estos callbacks (y el de teclado) al destruir la ventana.
     */
    public void configurarMouse(BiConsumer<Double, Double> alArrastrar, BiConsumer<Double, Double> alDesplazar,
                                 DoubleConsumer alRodar, BooleanSupplier usaMouseLook) {
        glfwSetMouseButtonCallback(ventana, (ventanaEvento, boton, accion, mods) -> { // Botones del mouse.
            if (boton == GLFW_MOUSE_BUTTON_RIGHT && accion == GLFW_PRESS && usaMouseLook.getAsBoolean()) { // Solo al presionar.
                if (mouseLookActivo) {
                    desactivarMouseLookInterno(); // Vuelve a mostrar el cursor normal.
                } else {
                    activarMouseLook(); // Oculta y captura el cursor (y motion cruda, si el sistema la soporta).
                }
            }
        }); // Termina el registro del callback de botones.
        glfwSetCursorPosCallback(ventana, (ventanaEvento, px, py) -> { // Movimiento del cursor.
            if (!mouseLookActivo) { // Sin mouse-look (incluida siempre SEGUIMIENTO) el mouse no mueve la cámara.
                return;
            }
            if (saltoPendienteMouseLook) { // Primer evento tras activar: fija la referencia, sin generar un salto.
                saltoPendienteMouseLook = false;
            } else {
                boolean shift = glfwGetKey(ventana, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS // Cualquiera de los dos Shift.
                    || glfwGetKey(ventana, GLFW_KEY_RIGHT_SHIFT) == GLFW_PRESS;
                if (shift) { // Con Shift, Camara interpreta el movimiento como desplazamiento (solo hace algo en AEREA).
                    alDesplazar.accept(px - ultimoX, py - ultimoY);
                } else { // Sin Shift, como arrastre: gira y eleva.
                    alArrastrar.accept(px - ultimoX, py - ultimoY);
                }
            }
            ultimoX = px; // Actualiza la referencia para el próximo evento.
            ultimoY = py;
        }); // Termina el registro del callback de cursor.
        glfwSetScrollCallback(ventana, (ventanaEvento, dx, dy) -> alRodar.accept(dy)); // Ruedita: pasos verticales.
        glfwSetWindowFocusCallback(ventana, (ventanaEvento, enfocada) -> { // Pierde el foco (Alt-Tab, otra ventana).
            if (!enfocada) { // Evita quedar con el cursor oculto y capturado en segundo plano.
                desactivarMouseLook();
            }
        }); // Termina el registro del callback de foco.
    }

    /** Activa el mouse-look: oculta y captura el cursor, con motion cruda si el sistema la soporta. */
    private void activarMouseLook() {
        mouseLookActivo = true; // El cursorPosCallback pasa a entregar cada movimiento a alArrastrar.
        saltoPendienteMouseLook = true; // El próximo evento de cursor solo fija la referencia (ver cursorPosCallback).
        glfwSetInputMode(ventana, GLFW_CURSOR, GLFW_CURSOR_DISABLED); // Oculta el cursor y lo deja virtual/ilimitado.
        if (glfwRawMouseMotionSupported()) { // No todos los sistemas la soportan.
            glfwSetInputMode(ventana, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE); // Movimiento sin aceleración ni escalado del SO.
        }
    }

    /** Apaga el mouse-look sin comprobar si estaba activo; la llaman el toggle y desactivarMouseLook(), que ya lo saben. */
    private void desactivarMouseLookInterno() {
        mouseLookActivo = false; // El cursorPosCallback deja de entregar el movimiento a alArrastrar.
        glfwSetInputMode(ventana, GLFW_CURSOR, GLFW_CURSOR_NORMAL); // Vuelve a mostrar el cursor normal.
    }

    /** Apaga el mouse-look si estaba activo; lo llaman Juego (Esc, o al cambiar de modo con C) y el callback de foco. */
    public void desactivarMouseLook() {
        if (mouseLookActivo) { // Evita tocar GLFW si ya estaba apagado.
            desactivarMouseLookInterno();
        }
    }

    /** Indica si el mouse-look (ORBITAL o AEREA) está activo; Juego lo consulta para que Esc lo apague en vez de cerrar. */
    public boolean mouseLookActivo() {
        return mouseLookActivo;
    }

    /** Indica si el usuario o el programa solicitaron cerrar la ventana. */
    public boolean debeCerrarse() {
        return glfwWindowShouldClose(ventana); // Devuelve true cuando se pidió terminar el ciclo principal.
    }

    /** Solicita terminar el ciclo principal por el mismo camino que un cierre normal. */
    public void cerrar() {
        glfwSetWindowShouldClose(ventana, true); // Solicita terminar el ciclo principal.
    }

    /** Procesa la entrada pendiente: aquí GLFW llama al callback de teclado. */
    public void procesarEventos() {
        glfwPollEvents(); // Procesa teclado, redimensionamiento y botón de cierre.
    }

    /** Consulta el tamaño real en píxeles; responde al redimensionamiento de la ventana. */
    public void actualizarTamano() {
        glfwGetFramebufferSize(ventana, anchoReal, altoReal); // Obtiene los píxeles reales, también en pantallas Retina.
        ancho = anchoReal[0]; // Copia el ancho actual al estado de la aplicación.
        alto = altoReal[0]; // Copia el alto actual al estado de la aplicación.
    }

    /** Muestra la imagen terminada. */
    public void presentar() {
        glfwSwapBuffers(ventana); // Muestra la imagen terminada intercambiando los buffers.
    }

    /** Consulta si una tecla sigue presionada durante el cuadro actual. */
    public boolean pulsada(int key) {
        return glfwGetKey(ventana, key) == GLFW_PRESS; // Devuelve true mientras el usuario mantiene la tecla.
    }

    /** Publica un texto en la barra superior de la ventana. */
    public void titulo(String titulo) {
        if (ventana == 0) { // Sin ventana creada (por ejemplo, en las pruebas) no hay título que cambiar.
            return; // Evita llamar a GLFW sin inicializar.
        }
        glfwSetWindowTitle(ventana, titulo); // Publica el texto en la barra superior de la ventana.
    }

    /** Devuelve el ancho actual del framebuffer en píxeles. */
    public int ancho() {
        return ancho; // Valor copiado en actualizarTamano().
    }

    /** Devuelve el alto actual del framebuffer en píxeles. */
    public int alto() {
        return alto; // Valor copiado en actualizarTamano().
    }

    /** Libera la ventana si llegó a crearse y finaliza GLFW. */
    public void destruir() {
        if (ventana != 0) { // Evita destruir una ventana que no pudo crearse.
            Callbacks.glfwFreeCallbacks(ventana); // Libera el callback de teclado registrado.
            glfwDestroyWindow(ventana); // Destruye la ventana y su contexto OpenGL.
        }
        glfwTerminate(); // Finaliza GLFW y sus recursos globales.
    }
}
