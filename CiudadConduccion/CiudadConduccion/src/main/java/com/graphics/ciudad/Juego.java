package com.graphics.ciudad; // Paquete raíz de la versión organizada por composición.

import com.graphics.ciudad.iluminacion.Cielo; // Fondo con degradado, estrellas y luna.
import com.graphics.ciudad.iluminacion.Iluminacion; // Sol, farolas y faros.
import com.graphics.ciudad.iluminacion.Sombras; // Manchas oscuras (sombras falsas) bajo los objetos.
import com.graphics.ciudad.interfaz.Dibujo2D; // Pase 2D con paneles y texto (STBEasyFont).
import com.graphics.ciudad.interfaz.Hud; // Panel de estado, ayuda, pausa y menú dentro de la ventana.
import com.graphics.ciudad.juego.EstadoPartida; // Menú de inicio, partida en curso o pausa.
import com.graphics.ciudad.juego.Entregas; // Reglas y progreso de la partida.
import com.graphics.ciudad.juego.Minimapa; // Segundo pase de dibujo desde arriba.
import com.graphics.ciudad.motor.Camara; // Cámara de seguimiento y aérea.
import com.graphics.ciudad.motor.Cubo; // Geometría compartida (VAO/VBO).
import com.graphics.ciudad.motor.Figuras; // Esfera, cilindro y cono (mallas redondeadas).
import com.graphics.ciudad.motor.Shader; // Programa GLSL y envío de uniforms.
import com.graphics.ciudad.motor.Ventana; // Ventana GLFW, teclado y presentación.
import com.graphics.ciudad.mundo.Ciudad; // Asfalto, calles, edificios y parques.
import com.graphics.ciudad.mundo.Decoracion; // Árboles, bancos, ventanas, pasos peatonales y semáforos.
import com.graphics.ciudad.mundo.Entorno; // Campo verde, cordón y árboles alrededor de la ciudad.
import com.graphics.ciudad.mundo.Mapa; // Aporta el límite de la ciudad a la cámara aérea.
import com.graphics.ciudad.trafico.Trafico; // Vehículos autónomos que recorren la ciudad.
import com.graphics.ciudad.vehiculo.Auto; // Vehículo del jugador.
import com.graphics.ciudad.vehiculo.Cabina; // Cabina trapezoidal extruida, compartida por el jugador y el tráfico.
import com.graphics.ciudad.vehiculo.Colisiones; // Manzanas y bordes; Juego le suma el tráfico para el deslizamiento.
import com.graphics.ciudad.vehiculo.IndicadorJugador; // Flecha flotante sobre el auto en la vista aérea.
import java.util.function.IntPredicate; // Pregunta si una tecla está presionada; las pruebas pueden simularlo.
import org.lwjgl.glfw.GLFWErrorCallback; // Muestra los errores de GLFW en la consola.
import static org.lwjgl.glfw.GLFW.*; // Importa las funciones y constantes de ventanas y teclado.
import static org.lwjgl.opengl.GL33.*; // Importa las funciones OpenGL hasta la versión 3.3.

/**
 * JUEGO: coordina todos los módulos; reemplaza la antigua cadena de herencia por etapas (ciudad → auto → iluminación → juego final).
 * Sigue el flujo de los ejemplos: iniciar, loop, dibujar y limpiar. En cada cuadro: entrada → actualizar(dt) → dibujar.
 * Coordenadas: X = izquierda/derecha; Y = altura; Z = profundidad.
 * Responsable de: crear los módulos, repartir las teclas, actualizar Auto y Entregas en orden, componer el título,
 * decidir el orden de dibujo de la escena y liberar los recursos al salir.
 * Se comunica con: Ventana, Shader, Cubo y Camara (motor); Ciudad, Decoracion y Entorno (mundo); Auto (vehiculo);
 * Trafico; Iluminacion, Cielo y Sombras; Entregas, Minimapa y EstadoPartida (juego); Hud y Dibujo2D (interfaz). Main lo crea y llama a ejecutar().
 * Antes cada etapa ampliaba a la anterior con extends y super; ahora Juego llama a cada módulo en el mismo orden.
 * Los comentarios explican las instrucciones; las llaves solo delimitan bloques.
 */
public class Juego {

    // ==================== 1. VARIABLES DE LA APLICACIÓN: MÓDULOS Y CONSTANTES ====================
    private static final String SHADER_VERTICES = "/shaders/ciudad.vert"; // Transformaciones de vértices, en src/main/resources.
    private static final String SHADER_FRAGMENTOS = "/shaders/iluminacion.frag"; // Iluminación; "/shaders/plano.frag" da colores planos.
    private static final double DT_MAXIMO = 0.05; // Paso de tiempo máximo por cuadro (50 ms): evita que el auto atraviese manzanas tras una pausa.
    private static final String NOMBRE_JUEGO = "Ciudad Interactiva"; // Nombre que encabeza el título de la ventana.

    private final Ventana ventana = new Ventana(); // Ventana GLFW con su contexto OpenGL.
    private final Shader shader = new Shader(); // Programa GLSL compartido por todos los dibujos.
    private final Cubo cubo = new Cubo(shader); // Geometría única con la que se construye todo.
    private final Camara camara = new Camara(Mapa.LIMITE); // Cámara de seguimiento o vista aérea ajustada al tamaño del mapa.
    private final Figuras figuras = new Figuras(shader); // Mallas redondeadas y prisma, generadas por fórmulas.
    private final Ciudad ciudad = new Ciudad(cubo, figuras); // Ciudad generada a partir del Mapa.
    private final Cabina cabina = new Cabina(shader, cubo); // Cabina de los autos: perfil extruido con vidrios.
    private final Decoracion decoracion = new Decoracion(shader, cubo, figuras); // Detalles urbanos de la ciudad terminada.
    private final Entorno entorno = new Entorno(cubo, figuras); // Campo que rodea la ciudad; no cambia límites ni colisiones.
    private final Cielo cielo = new Cielo(shader, cubo, figuras); // Fondo de la vista principal.
    private final Sombras sombras = new Sombras(shader, cubo, figuras, cabina, ciudad.getEdificio()); // Manchas o sombra planar.
    private final Auto auto = new Auto(); // Vehículo del jugador; no necesita OpenGL para existir.
    private final Iluminacion iluminacion = new Iluminacion(shader, figuras); // Día/noche, farolas y faros.
    private final Entregas entregas = new Entregas(shader, cubo); // Destinos, progreso y cronómetro.
    private final Minimapa minimapa = new Minimapa(shader, cubo); // Vista superior en un recuadro.
    private final IndicadorJugador indicador = new IndicadorJugador(shader, cubo); // Señala el auto desde la vista aérea.
    private final Trafico trafico = new Trafico(shader, cubo, iluminacion::esNoche); // Vehículos autónomos con rutas cíclicas por las calles.
    private final Dibujo2D dibujo2D = new Dibujo2D(); // Pase 2D final: paneles y texto sobre la escena.
    private final Hud hud = new Hud(); // Panel de estado, ayuda (H), cartel de pausa y menú de inicio.
    private final EstadoPartida estado = new EstadoPartida(); // Arranca en el menú; ENTER empieza y P pausa.
    private IntPredicate teclado = ventana::pulsada; // Teclas mantenidas; por defecto las lee de la ventana GLFW.
    private float relojGlobal = 0; // Segundos desde el arranque; no se detiene al ganar ni se reinicia con R. Anima los semáforos.

    // ==================== 2. INICIO, CICLO Y LIMPIEZA ====================

    /** Organiza las tres fases de la aplicación y garantiza la limpieza si ocurre un error. */
    public void ejecutar() {
        GLFWErrorCallback errores = GLFWErrorCallback.createPrint(System.err); // Prepara mensajes de error en consola.
        errores.set(); // Instala el manejador de errores de GLFW.
        try { // Protege el arranque y el ciclo para poder liberar recursos si fallan.
            iniciar(); // Crea la ventana, los shaders y la geometría.
            loop(); // Actualiza y dibuja hasta que se cierre la ventana.
        } finally { // Este bloque se ejecuta tanto al salir normalmente como al fallar.
            limpiar(); // Libera la GPU, los callbacks y la ventana.
            glfwSetErrorCallback(null); // Desconecta el manejador antes de liberar su memoria.
            errores.free(); // Libera el callback de errores.
        }
    }

    /** Crea la ventana, compila los shaders y sube el cubo a la GPU. */
    private void iniciar() {
        ventana.crear(NOMBRE_JUEGO, this::tecla); // Crea la ventana y entrega cada tecla presionada a tecla().
        ventana.configurarMouse(camara::arrastrar, camara::desplazar, camara::zoom, camara::usaMouseLook); // Orbital y aérea: mouse-look (click derecho) + Shift para desplazar en aérea.
        shader.crear(SHADER_VERTICES, SHADER_FRAGMENTOS); // Compila y enlaza los shaders que transforman y colorean los vértices.
        cubo.crear(); // Guarda en la GPU el cubo que servirá para todos los objetos.
        figuras.crear(); // Sube a la GPU la esfera, el cilindro y el cono.
        cabina.crear(); // Sube a la GPU la cabina y las ventanillas.
        dibujo2D.crear(); // Compila el shader del HUD y reserva su buffer de vértices.
    }

    /** Mantiene el ciclo continuo de eventos, actualización, dibujo y presentación. */
    private void loop() {
        double tiempoAnterior = glfwGetTime(); // Guarda el instante inicial para calcular el tiempo entre cuadros.
        int cuadros = 0; // Cuenta los cuadros dibujados para la comprobación automática opcional.
        int maxCuadros = Integer.getInteger("demo.frames", 0); // Cero permite jugar; otro valor limita el arranque de prueba.

        while (!ventana.debeCerrarse()) { // Repite mientras no se haya solicitado cerrar.
            ventana.procesarEventos(); // Entrada: procesa teclado, redimensionamiento y botón de cierre.
            double tiempoActual = glfwGetTime(); // Consulta el tiempo actual en segundos.
            float deltaTime = (float) Math.min(tiempoActual - tiempoAnterior, DT_MAXIMO); // Evita saltos de más de 50 ms.
            tiempoAnterior = tiempoActual; // Conserva el instante de este cuadro para la próxima vuelta.
            actualizar(deltaTime); // Actualiza conducción y entregas.
            ventana.actualizarTamano(); // Obtiene los píxeles reales del framebuffer tras un posible redimensionamiento.
            if (ventana.ancho() > 0 && ventana.alto() > 0) { // Evita dibujar y dividir por cero cuando la ventana está minimizada.
                dibujarFrame(); // Genera la imagen de la ciudad para este cuadro.
            }
            ventana.presentar(); // Muestra la imagen terminada intercambiando los buffers.
            cuadros++; // Registra que se completó una vuelta del ciclo.
            if (maxCuadros > 0 && cuadros >= maxCuadros) { // Detecta el final de un arranque automático limitado.
                ventana.cerrar(); // Solicita salir por el mismo camino que un cierre normal.
            }
        }
    }

    /** Libera únicamente los recursos que llegaron a crearse. */
    private void limpiar() {
        dibujo2D.eliminar(); // Libera el shader y el buffer del HUD si existen.
        shader.eliminar(); // Libera el programa de shaders si existe.
        cubo.eliminar(); // Libera el VBO y el VAO del cubo si existen.
        figuras.eliminar(); // Libera las mallas redondeadas si existen.
        cabina.eliminar(); // Libera las mallas de la cabina si existen.
        ventana.destruir(); // Libera callbacks, ventana y GLFW.
    }

    // ==================== 3. TECLADO Y REINICIO (CONTROLES) ====================

    /** Atiende acciones que deben ocurrir una sola vez por pulsación y las reparte entre los módulos. */
    private void tecla(int key) {
        // Antes cada clase sustituía tecla() y conservaba las acciones anteriores mediante super; aquí están todas juntas.
        if (key == GLFW_KEY_ESCAPE) { // Comprueba si se presionó ESC.
            if (ventana.mouseLookActivo()) { // Con el mouse-look activo (ORBITAL o AEREA), Esc lo apaga en vez de cerrar.
                ventana.desactivarMouseLook();
            } else {
                ventana.cerrar(); // Solicita terminar el ciclo principal.
            }
        }

        if (key == GLFW_KEY_C) { // Comprueba si se presionó la tecla de cámara.
            camara.alternar(); // Invierte el modo de cámara actual.
            ventana.desactivarMouseLook(); // Se apaga siempre: evita saltos al pasar ORBITAL↔AEREA con el cursor capturado.
        }

        if (key == GLFW_KEY_R) { // Comprueba si el usuario quiere comenzar de nuevo.
            reiniciar(); // Auto, entregas, tráfico y cámara vuelven al inicio.
        }

        if (key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER) { // ENTER del teclado principal o del numérico.
            estado.empezar(); // Sale del menú de inicio y comienza la partida.
        }

        if (key == GLFW_KEY_P) { // Comprueba la tecla de pausa.
            estado.alternarPausa(); // Congela o reanuda auto, tráfico, entregas y semáforos.
        }

        if (key == GLFW_KEY_H) { // Comprueba la tecla de ayuda.
            hud.alternarAyuda(); // Muestra u oculta la lista de controles.
        }

        iluminacion.tecla(key); // Amplía los controles con N y F; conserva ESC, C y R.
        minimapa.tecla(key); // Amplía las teclas con M; conserva salida, cámara, reinicio y luces.
    }

    /** Reinicio de la partida (tecla R): auto en la salida, entregas y tráfico al inicio, cámara detrás del auto. */
    void reiniciar() {
        auto.reset(); // Restaura posición, velocidad y orientación del vehículo.
        entregas.reset(); // Amplía el reinicio del auto: vuelve a la primera parada y al cronómetro en cero.
        trafico.reset(); // Devuelve cada vehículo autónomo al inicio de su ruta.
        camara.reiniciarSeguimiento(auto.getX(), auto.getZ(), auto.getAngulo()); // Sin barrido desde donde estaba.
    }

    // ==================== 4. ACTUALIZACIÓN POR CUADRO ====================

    /** Actualiza el vehículo y comprueba si llegó y frenó en el destino activo; deltaTime son segundos entre cuadros. */
    void actualizar(float deltaTime) {
        // En el menú o en pausa el paso de tiempo es 0: el mismo código se ejecuta, pero nada se mueve ni avanza.
        deltaTime = estado.dtEfectivo(deltaTime); // Tiempo real al jugar, 0 en menú o pausa.
        relojGlobal += deltaTime; // Avanza el reloj de la animación urbana en todos los cuadros.
        // Una posición es libre si no toca manzanas ni bordes (círculo contra rectángulo) ni al tráfico (círculo contra
        // círculo). Mientras Auto prueba, getX()/getZ() siguen siendo la posición de partida del cuadro.
        Colisiones.PosicionLibre libre = (nx, nz) -> Colisiones.puedeCircular(nx, nz)
            && !trafico.bloquea(auto.getX(), auto.getZ(), nx, nz, Auto.RADIO_AUTO);
        auto.actualizar(deltaTime, teclado, libre, !iluminacion.esNoche()); // Aceleración, giro, colisiones y sombra solar, de día.
        trafico.actualizar(deltaTime, auto.getX(), auto.getZ()); // Mueve los vehículos; frenan si el jugador les cierra el carril.
        actualizarTitulo(); // Muestra los controles y la velocidad actual.
        entregas.actualizar(deltaTime, auto); // Añade el objetivo del juego al movimiento del auto.
        camara.actualizarSeguimiento(deltaTime, auto.getX(), auto.getZ(), auto.getAngulo()); // Recorta o recupera la distancia detrás del auto.
    }

    /** Compone el texto de la ventana sin mezclarlo con las fórmulas de conducción. */
    private void actualizarTitulo() {
        int kilometrosPorHora = Math.round(Math.abs(auto.getVelocidad()) * 3.6f); // Convierte m/s a km/h y redondea.
        // Formato: "Ciudad Interactiva | Vel: X km/h | Luces: ON/OFF | Día/Noche | Entregas: X/3 | Destino: ...".
        // Los controles (WASD/flechas, Espacio, C, R, N, F, M, ESC) ya no ocupan el título: están en ENTREGA.md.
        String titulo = NOMBRE_JUEGO; // Obtiene el nombre del juego que encabeza el título.
        titulo += " | Vel: " + kilometrosPorHora + " km/h"; // Añade la magnitud de la velocidad.
        titulo += iluminacion.estado(); // Añade los indicadores de faros y día/noche de Iluminacion.
        titulo += " | " + entregas.estado(); // Añade el progreso y el destino activo de Entregas.
        ventana.titulo(titulo); // Publica el texto en la barra superior de la ventana.
    }

    // ==================== 5. DIBUJO: ESCENA FINAL Y DESTINO ====================

    /** Prepara OpenGL, dibuja la vista principal y después el minimapa. */
    private void dibujarFrame() {
        int ancho = ventana.ancho(); // Ancho actual del framebuffer en píxeles.
        int alto = ventana.alto(); // Alto actual del framebuffer en píxeles.
        glViewport(0, 0, ancho, alto); // Utiliza toda el área de la ventana para la escena principal.
        glClearColor(0.12f, 0.20f, 0.30f, 1); // Fondo de respaldo, azul oscuro opaco: en la vista principal lo cubre el cielo (Cielo).
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT); // Borra imagen, profundidad y el stencil de Sombras.
        shader.usar(); // Activa los shaders de esta etapa.
        shader.matriz3("uRotacion", Shader.IDENTIDAD_3X3); // Sin rotación extra: solo las ruedas la cambian (y la restauran).
        shader.decimal("uAlfa", 1); // Opacidad completa; solo la bajan Sombras y Cubo.cajaTranslucida() (y la restauran).
        shader.decimal("uFactorSombraSol", 1); // Sol pleno; solo lo bajan Auto y Vehiculo bajo la sombra de un edificio (y lo restauran).
        shader.entero("uCielo", 0); // Material normal: solo Cielo activa el degradado (y lo apaga al terminar).
        shader.entero("uSombra", 0); // Material normal: solo Sombras activa las manchas (y las apaga al terminar).
        cubo.enlazar(); // Selecciona los atributos del cubo compartido.
        shader.entero("uMapa", 0); // Selecciona perspectiva normal, no la proyección del minimapa.
        camara.configurar(shader, auto.getX(), auto.getZ(), auto.getAngulo(), ancho, alto); // Actualiza la posición y el objetivo de la cámara.
        iluminacion.preparar(auto); // Envía la iluminación: día/noche, faros y posiciones de las farolas.
        trafico.prepararFaros(); // Envía los focos de los faros del tráfico (ninguno de día).
        cielo.dibujar(camara.getOjo(), iluminacion.esNoche()); // PRIMERO el fondo, sin escribir profundidad: todo lo demás queda delante.
        escena(); // Dibuja la ciudad y todos los módulos sobre ella.
        if (glGetError() != GL_NO_ERROR) { // Comprueba si OpenGL reportó una operación inválida.
            throw new IllegalStateException("Error OpenGL al dibujar"); // Hace visible el problema en consola.
        }
        minimapa.dibujar(ancho, alto, auto, this::escena); // Amplía el cuadro: dibuja otra vez la escena en el minimapa.
        dibujarHud(ancho, alto); // Último pase: interfaz 2D encima de todo.
        if (glGetError() != GL_NO_ERROR) { // Comprueba que el HUD no haya generado errores OpenGL.
            throw new IllegalStateException("Error OpenGL en el HUD"); // Expone el error en la consola.
        }
    }

    /** Dibuja el HUD en un pase ortográfico 2D y restaura el estado de OpenGL para el próximo cuadro. */
    private void dibujarHud(int ancho, int alto) {
        int kilometrosPorHora = Math.round(Math.abs(auto.getVelocidad()) * 3.6f); // Convierte m/s a km/h y redondea.
        String[] lineas = { // Líneas del panel de estado, de arriba hacia abajo.
            "Velocidad: " + kilometrosPorHora + " km/h", // Velocidad actual.
            "Faros: " + (iluminacion.farosEncendidos() ? "ON" : "OFF"), // Estado de los faros (tecla F).
            "Ambiente: " + (iluminacion.esNoche() ? "Noche" : "Día"), // Día o noche (tecla N).
            "Entregas: " + entregas.getEntregas() + "/" + Entregas.DESTINOS.length, // Progreso de la partida.
            "Destino: " + entregas.nombreDestino(), // Parada activa (marca dorada).
            "Sector: " + Mapa.nombreSector(auto.getX(), auto.getZ()), // Zona de la ciudad donde está el auto.
            "Cámara: " + camara.nombreModo(), // Modo de cámara actual (tecla C).
            "H: ayuda   P: pausa" // Recordatorio de las teclas de la interfaz.
        };
        dibujo2D.comenzar(ancho, alto); // Desactiva la profundidad, activa la mezcla y usa el shader del HUD.
        try { // Asegura que el estado 3D se restaure aunque falle el dibujo.
            dibujarNombresSectores(ancho, alto); // Nombres de los sectores sobre el minimapa (si está visible).
            hud.dibujar(dibujo2D, ancho, alto, lineas, estado.getEstado()); // Panel, ayuda, pausa o menú.
        } finally { // El próximo cuadro espera profundidad activa y sin mezcla.
            dibujo2D.terminar(); // Restaura el estado de OpenGL.
        }
    }

    /** Ubica el nombre de cada sector en el centro de su rectángulo, convertido a píxeles del minimapa. */
    private void dibujarNombresSectores(int ancho, int alto) {
        float[][] posiciones = new float[Mapa.SECTORES.length][]; // Un punto de pantalla por sector.
        for (int i = 0; i < Mapa.SECTORES.length; i++) { // Recorre los sectores de Mapa.
            float[] r = Mapa.SECTORES[i]; // Rectángulo {xMin, xMax, zMin, zMax}.
            posiciones[i] = minimapa.aPantalla((r[0] + r[1]) / 2, (r[2] + r[3]) / 2, alto); // Centro del sector en píxeles.
            if (posiciones[i] == null) { // El minimapa está oculto (tecla M).
                return; // No hay dónde escribir los nombres.
            }
        }
        hud.dibujarEtiquetasMapa(dibujo2D, ancho, alto, posiciones, Mapa.NOMBRES_SECTORES); // Escribe los nombres.
    }

    // ==================== 6. ACCESO PARA PRUEBAS ====================

    /** Reemplaza la lectura del teclado; las pruebas simulan teclas presionadas sin abrir una ventana. */
    void usarTeclado(IntPredicate teclasSimuladas) {
        teclado = teclasSimuladas; // Auto.actualizar() consultará esta función en lugar de GLFW.
    }

    /** Devuelve el estado de la partida (menú, jugando o pausa). */
    EstadoPartida getEstado() {
        return estado; // Estado actual.
    }

    /** Devuelve el auto del jugador. */
    Auto getAuto() {
        return auto; // Vehículo controlado por el usuario.
    }

    /** Devuelve la cámara. */
    Camara getCamara() {
        return camara; // Seguimiento, orbital o aérea.
    }

    /** Devuelve el tráfico autónomo. */
    Trafico getTrafico() {
        return trafico; // Vehículos de la ciudad.
    }

    /** Dibuja la escena completa; Minimapa la reutiliza para su segundo pase. */
    private void escena() {
        ciudad.dibujar(); // Dibuja asfalto, calles, edificios y parques.
        if (!minimapa.enVistaMapa()) { // El minimapa muestra solo la ciudad: su escala sigue siendo Mapa.LIMITE.
            entorno.dibujar(); // Campo verde, cordón y árboles de afuera.
        }
        auto.dibujar(cubo, figuras, shader, cabina, iluminacion.farosEncendidos()); // Añade el vehículo con ruedas que giran y sus luces; los faros siguen a la tecla F (en todas las cámaras y en el minimapa).
        if (camara.esAerea() && !minimapa.enVistaMapa()) { // Desde arriba el auto se ve chico: se marca con una flecha.
            indicador.dibujar(auto.getX(), auto.getZ(), relojGlobal); // En la cámara de seguimiento no hace falta.
        }
        trafico.dibujar(figuras, cabina); // Añade los vehículos autónomos (con sus luces según día/noche); también aparecen en el minimapa.
        iluminacion.dibujarFarolas(); // Añade las farolas: base, poste, brazo curvo, pantalla y bombilla.
        if (!minimapa.enVistaMapa()) { // Los detalles pequeños solo son necesarios en la vista principal.
            decoracion.dibujar(iluminacion.esNoche(), relojGlobal); // Añade árboles, bancos, ventanas y señalización urbana.
        }
        if (entregas.quedanEntregas()) { // Dibuja un objetivo únicamente mientras queden entregas.
            entregas.dibujarDestino(minimapa.enVistaMapa()); // Coloca la marca dorada en la parada activa.
        }
        if (!minimapa.enVistaMapa()) { // Desde arriba, en el minimapa, las manchas (o la sombra planar) solo ensuciarían el plano.
            sombras.dibujar(auto, trafico.getVehiculos(), iluminacion.esNoche(), iluminacion::dibujarFarolasConHuella); // AL FINAL: la mezcla necesita el suelo ya pintado.
        }
    }
}
