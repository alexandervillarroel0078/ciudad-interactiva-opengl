package com.graphics.ciudad.motor; // Agrupa las piezas técnicas: ventana, shaders, geometría y cámara.

import com.graphics.ciudad.mundo.Edificio; // Altura de los edificios: la cámara aérea no entra en ellos.
import com.graphics.ciudad.mundo.Mapa; // Celdas de calle: la cámara de seguimiento no se pone sobre una manzana.
import static org.lwjgl.glfw.GLFW.*; // Permite consultar las flechas que orbitan la cámara.

/**
 * CAMARA: decide desde dónde se observa la ciudad.
 * Responsable de tres modos, que la tecla C recorre en orden: SEGUIMIENTO (detrás del auto) → ORBITAL (alrededor del
 * auto, manejada con el mouse) → AEREA (vista general de la ciudad, también manejada con el mouse).
 * Las dos cámaras que se manejan con el mouse comparten la misma matemática, la clase Orbita (coordenadas esféricas
 * θ, φ y D alrededor de un centro); solo cambian el centro y los límites:
 *  - ORBITAL DEL AUTO: el centro es el auto y θ se suma a su ángulo, así la cámara lo acompaña cuando dobla.
 *    φ entre ELEVACION_MIN y ELEVACION_MAX, D entre DISTANCIA_MIN y DISTANCIA_MAX. Se puede seguir manejando.
 *  - AÉREA: el centro es un punto de la ciudad (al entrar, el origen). φ entre ELEVACION_AEREA_MIN y
 *    ELEVACION_AEREA_MAX, D entre DISTANCIA_AEREA_MIN y DISTANCIA_AEREA_MAX (con la máxima se ve toda la ciudad).
 *    Mantiene además un centro (centroX, centroZ) que nunca sale de ±límite de la ciudad; Shift lo desplaza en vez
 *    de orbitar (ver Mouse, abajo). Al entrar al modo la cámara vuelve a la vista general de siempre (las capturas).
 * Mouse (ORBITAL y AEREA, usaMouseLook() da true en ambas): click derecho activa/desactiva el MOUSE-LOOK (Ventana
 * oculta y captura el cursor, con motion cruda si el sistema la soporta); el botón izquierdo no hace nada. Con el
 * modo activo, mover el mouse sin presionar nada gira y eleva (arrastrar()); en AEREA, sosteniendo Shift (izquierdo
 * o derecho) el mismo movimiento desplaza el centro (desplazar()) en vez de orbitar — en ORBITAL, Shift no hace
 * nada porque desplazar() solo actúa en AEREA. La ruedita siempre acerca y aleja (zoom()), con o sin mouse-look. En
 * la cámara de seguimiento el mouse no hace nada (usaMouseLook() da false). Esc con el mouse-look activo lo apaga
 * en vez de cerrar la ventana; Ventana también lo apaga si la ventana pierde el foco o si Juego cambia de modo con
 * C (incluido ORBITAL↔AEREA, para no arrastrar el estado de un centro al otro).
 * SEGUIMIENTO: detrás del auto, con su ángulo suavizado, mirando a un punto por delante de él con una inclinación fija.
 * Se acerca al auto cuando el punto de detrás cae sobre una manzana o fuera de la ciudad (ver distanciaLibre()), y
 * vuelve a su distancia de a poco.
 * Se comunica con: Shader, al que envía uOjo, uObjetivo, uAspecto y uPlanoLejano; Juego, que le pasa la posición y
 * el ángulo del Auto y el tamaño de la Ventana en cada cuadro; Ventana, que le entrega los movimientos del mouse;
 * Mapa, para saber qué celdas son calle.
 */
public class Camara {

    /** Modos de cámara, en el orden en que los recorre la tecla C. */
    public enum Modo {
        SEGUIMIENTO, // Detrás del auto, con el ángulo suavizado y mirando por delante de él.
        ORBITAL, // Alrededor del auto, controlada con el mouse.
        AEREA // Vista general de toda la ciudad, controlada con el mouse.
    }

    // ==================== MOUSE (valores ajustables, compartidos por la orbital y la aérea) ====================
    public static final float SENSIBILIDAD_GIRO = 0.008f; // Radianes de θ por píxel arrastrado en horizontal.
    public static final float SENSIBILIDAD_ELEVACION = 0.006f; // Radianes de φ por píxel arrastrado en vertical.

    // ==================== CÁMARA ORBITAL DEL AUTO (valores ajustables) ====================
    public static final float ANGULO_INICIAL = 0; // θ al entrar al modo: 0 = justo detrás del auto.
    public static final float ELEVACION_INICIAL = (float) Math.toRadians(25); // φ al entrar al modo.
    public static final float DISTANCIA_INICIAL = 9; // D al entrar al modo.
    public static final float ELEVACION_MIN = (float) Math.toRadians(5); // Casi a ras del suelo, sin atravesarlo.
    public static final float ELEVACION_MAX = (float) Math.toRadians(85); // Casi cenital, sin llegar a 90°.
    public static final float DISTANCIA_MIN = 4; // Lo más cerca del auto (su largo es 2.6).
    public static final float DISTANCIA_MAX = 30; // Lo más lejos.
    public static final float PASO_ZOOM = 1; // Unidades de distancia por cada paso de la ruedita.

    // ==================== CÁMARA AÉREA (valores ajustables) ====================
    // Vista inicial: la de siempre (la de las capturas), con la cámara a FACTOR_RADIO límites del centro sobre el suelo
    // y FACTOR_ALTURA límites de altura, en el ángulo ANGULO_AEREO_INICIAL. En esféricas: φ = atan(altura / radio) ≈ 40°
    // y D = √(radio² + altura²) ≈ 2.43 límites (≈ 134 con límite 55).
    public static final float ANGULO_AEREO_INICIAL = 0.6f; // θ inicial alrededor de la ciudad, en radianes.
    private static final float FACTOR_RADIO = 1.86f; // Radio de la vista inicial en "límites": con la ciudad original (límite 35) daba 65.
    private static final float FACTOR_ALTURA = 1.57f; // Altura de la vista inicial en "límites": con la ciudad original daba 55.
    public static final float ELEVACION_AEREA_MIN = (float) Math.toRadians(20); // Vista oblicua: se ven las fachadas.
    public static final float ELEVACION_AEREA_MAX = (float) Math.toRadians(85); // Casi un plano visto desde arriba.
    public static final float DISTANCIA_AEREA_MIN = 20; // Lo más cerca: una o dos manzanas.
    public static final float FACTOR_DISTANCIA_AEREA_MAX = 2.6f; // DISTANCIA_AEREA_MAX = 2.6 límites (143 con límite 55): toda la ciudad, un poco más lejos que la vista inicial.
    public static final float MARGEN_TECHO = 2; // La aérea queda al menos esto por encima del techo que tiene debajo.
    public static final float PASO_ZOOM_AEREO = 6; // Unidades de distancia por paso de ruedita: la ciudad es grande.
    public static final float SENSIBILIDAD_DESPLAZAMIENTO = 0.0015f; // Desplazamiento por píxel, por unidad de distancia (lejos = más rápido).

    // ==================== CÁMARA DE SEGUIMIENTO (valores ajustables) ====================
    // ENCUADRE: la cámara va DISTANCIA_SEGUIMIENTO detrás del auto y mira a un punto ADELANTE unidades por delante de
    // él, a ALTURA_OBJETIVO_MIRA. Su altura sale de la inclinación: altura = ALTURA_OBJETIVO_MIRA + (d + ADELANTE) ·
    // tan(INCLINACION), así la cámara mira siempre INCLINACION hacia abajo aunque el recorte cambie d (se acerca y baja
    // a la vez, sin cabecear). Con 11° y el campo visual de 55° el horizonte queda en el tercio superior de la pantalla.
    // POR QUÉ MIRAR POR DELANTE DEL AUTO: mirando al auto, este queda clavado en el centro y la mitad de la pantalla es
    // calle que ya se recorrió. Mirando adelante, el auto baja al tercio inferior y se ve hacia dónde va; además, el
    // punto de mira usa el ángulo REAL del auto, así que al doblar la vista se adelanta a la curva, como en los juegos.
    // SUAVIZADO DEL ÁNGULO: la cámara no copia el ángulo del auto; lo persigue. En cada cuadro el atraso (ángulo del
    // auto − anguloCamara) se multiplica por exp(−K_GIRO · dt). POR QUÉ NO DEPENDE DE LOS FPS: dos cuadros de dt dejan
    // exp(−k·dt) · exp(−k·dt) = exp(−k · 2dt), lo mismo que un cuadro de 2dt; a 30, 60 o 144 FPS, en medio segundo el
    // atraso queda multiplicado por exp(−k · 0.5). Un "atraso · 0.9 por cuadro" fijo, en cambio, se achicaría el doble
    // de rápido a 120 FPS que a 60. Con K_GIRO = 5 el atraso se reduce a la mitad en ln 2 / 5 ≈ 0.14 s, y al doblar a
    // fondo (≈ 101°/s) la cámara queda unos 20° atrás: se ve el costado del auto y cada toque de A/D ya no sacude la imagen.
    // RECORTE: la cámara va DISTANCIA_SEGUIMIENTO detrás del auto, pero si ese punto cae sobre una manzana (al doblar,
    // el "detrás" apunta en diagonal hacia un edificio) o fuera de ±(límite − MARGEN_BORDE_CAMARA), se acerca al auto.
    // distanciaLibre() camina desde el auto hacia atrás en pasos de PASO_RECORTE y se queda con el último punto que
    // sigue sobre calle: así el ojo y toda la línea que lo une con el auto quedan sobre la calle, sin atravesar edificios.
    // El rayo se traza hacia atrás desde anguloCamara (el ángulo suavizado), que es donde está la cámara de verdad.
    // BÚSQUEDA BINARIA: si la distancia solo pudiera valer múltiplos de PASO_RECORTE, al retroceder hacia un obstáculo
    // quedaría fija mientras el auto avanza 0.25 hacia él y después saltaría 0.25 de golpe. El ojo iría para atrás con
    // el auto y volvería para adelante unas 24 veces por segundo (6 u/s ÷ 0.25): el temblor en reversa. Por eso, al
    // encontrar el primer punto tapado, se parte ITERACIONES_RECORTE veces al medio el tramo entre el último libre y ese:
    // 10 mitades dejan un error de 0.25 / 2¹⁰ ≈ 0.0002. Así la distancia es continua y, retrocediendo, el ojo queda
    // quieto frente al obstáculo mientras el auto se le acerca, bajando de a poco.
    // SUAVIZADO: acercarse es inmediato (la cámara nunca entra a un edificio); alejarse, en cambio, se hace a
    // VELOCIDAD_ALEJAMIENTO para que no salte al salir de la curva. En una calle recta no hay recorte y la distancia es
    // siempre DISTANCIA_SEGUIMIENTO: se ve igual que sin recorte, sin quedar atrasada.
    public static final float DISTANCIA_SEGUIMIENTO = 8; // Distancia horizontal de la cámara detrás del auto ("acercá la cámara").
    public static final float ADELANTE = 4; // Cuánto por delante del auto está el punto al que mira.
    public static final float ALTURA_OBJETIVO_MIRA = 1.2f; // Altura de ese punto: un poco sobre el techo del auto.
    public static final float INCLINACION = (float) Math.toRadians(11); // Cuánto mira hacia abajo ("que mire más arriba": bajarla).
    public static final float ALTURA_MINIMA_SEGUIMIENTO = 2; // Nunca más baja que esto, aunque se achique la inclinación.
    public static final float K_GIRO = 5; // Rapidez (1/s) con que la cámara alcanza el ángulo del auto: más = más rígida.
    private static final float ALTURA_OBJETIVO = 0.8f; // Altura del punto del auto al que mira la cámara orbital (la carrocería).
    public static final float DISTANCIA_SEGUIMIENTO_MIN = 1.5f; // Nunca más cerca: con 0 la cámara miraría justo hacia abajo.
    public static final float MARGEN_BORDE_CAMARA = 1; // La cámara queda al menos esto adentro del borde de la ciudad.
    public static final float PASO_RECORTE = 0.25f; // Paso grueso de la búsqueda del punto libre, en unidades.
    public static final int ITERACIONES_RECORTE = 10; // Mitades de la búsqueda binaria dentro del último paso.
    public static final float VELOCIDAD_ALEJAMIENTO = 12; // Unidades por segundo con que la cámara recupera su distancia.

    // ==================== CAMPO VISUAL (valores ajustables) ====================
    // ciudad.vert usa uCampoVisual para la perspectiva. SEGUIMIENTO y ORBITAL comparten el mismo valor de siempre;
    // en AEREA, visto desde arriba, un campo visual grande exagera la perspectiva y las torres parecen inclinarse
    // hacia los bordes de la pantalla (mientras más gran angular, más notorio). Con un campo visual más chico la
    // vista aérea se acerca a una proyección casi ortográfica y los edificios se ven más rectos.
    public static final float CAMPO_VISUAL = 55; // Grados verticales en SEGUIMIENTO y ORBITAL.
    public static final float CAMPO_VISUAL_AEREO = 45; // Grados verticales en AEREA: menos gran angular, torres más rectas.

    // ==================== PLANO LEJANO (valores ajustables) ====================
    // ciudad.vert recorta lo que está más lejos que uPlanoLejano. Debe alcanzar la esquina opuesta de la ciudad con la
    // aérea alejada al máximo y el centro en una esquina: DISTANCIA_AEREA_MAX + diagonal, más un margen.
    public static final float MARGEN_PLANO_LEJANO = 20; // Con límite 55: 143 + 155.6 + 20 ≈ 319.

    // ==================== ESTADO ====================
    private Modo modo = Modo.SEGUIMIENTO; // El juego arranca con la cámara de seguimiento.
    private final Orbita orbitaAuto = new Orbita(ANGULO_INICIAL, ELEVACION_INICIAL, DISTANCIA_INICIAL,
        ELEVACION_MIN, ELEVACION_MAX, DISTANCIA_MIN, DISTANCIA_MAX); // θ, φ y D alrededor del auto.
    private final Orbita orbitaAerea; // θ, φ y D alrededor del centro aéreo.
    private final float limite; // Distancia del origen a cada borde de la ciudad (Mapa.LIMITE).
    private final float elevacionAereaInicial; // φ de la vista inicial.
    private final float distanciaAereaInicial; // D de la vista inicial.
    private float centroX = 0; // Punto de la ciudad que mira la cámara aérea, en X.
    private float centroZ = 0; // Y en Z.
    private float distanciaSeguimiento = DISTANCIA_SEGUIMIENTO; // Distancia actual detrás del auto (suavizada).
    private float anguloCamara = 0; // Ángulo desde el que mira la cámara de seguimiento: persigue al del auto.
    private boolean reiniciarSeguimiento = true; // El próximo actualizarSeguimiento() se coloca detrás del auto sin barrido.
    private final float[] ojo = new float[3]; // Última posición de la cámara enviada al shader: Cielo centra su cúpula ahí.

    /** Recibe la distancia del centro a cada borde (Mapa.LIMITE) y ajusta la vista aérea a ese tamaño. */
    public Camara(float limiteCiudad) {
        limite = limiteCiudad; // El centro aéreo nunca sale de ±limite.
        elevacionAereaInicial = (float) Math.atan2(FACTOR_ALTURA, FACTOR_RADIO); // ≈ 40°.
        distanciaAereaInicial = limiteCiudad * (float) Math.hypot(FACTOR_RADIO, FACTOR_ALTURA); // ≈ 134 con límite 55.
        orbitaAerea = new Orbita(ANGULO_AEREO_INICIAL, elevacionAereaInicial, distanciaAereaInicial,
            ELEVACION_AEREA_MIN, ELEVACION_AEREA_MAX, DISTANCIA_AEREA_MIN, getDistanciaAereaMax()); // Vista general.
    }

    /** Indica si está activa la vista aérea de la ciudad; Juego dibuja entonces la flecha sobre el auto. */
    public boolean esAerea() {
        return modo == Modo.AEREA; // La flecha no hace falta en seguimiento ni en la orbital del auto.
    }

    /** Indica si el modo actual se maneja con mouse-look (click derecho activa/desactiva): ORBITAL y AEREA. */
    public boolean usaMouseLook() {
        return modo == Modo.ORBITAL || modo == Modo.AEREA; // Ventana lo consulta para decidir qué hace el botón derecho.
    }

    /** Pasa al siguiente modo: seguimiento → orbital del auto → aérea → seguimiento; Juego lo llama al presionar C. */
    public void alternar() {
        modo = Modo.values()[(modo.ordinal() + 1) % Modo.values().length]; // Avanza en el orden del enum y vuelve al inicio.
        if (modo == Modo.AEREA) { // Al entrar a la aérea...
            reiniciarAerea(); // ...arranca con la vista general de siempre.
        }
        if (modo == Modo.SEGUIMIENTO) { // Al volver al seguimiento...
            reiniciarSeguimiento = true; // ...arranca justo detrás del auto (acá no se conoce su posición).
        }
    }

    /** Vuelve la cámara aérea a la vista inicial: centro en el origen, ángulo, elevación y distancia de las capturas. */
    private void reiniciarAerea() {
        centroX = 0; // Centro de la ciudad.
        centroZ = 0;
        orbitaAerea.colocar(ANGULO_AEREO_INICIAL, elevacionAereaInicial, distanciaAereaInicial); // Vista general.
    }

    /** Modo actual. */
    public Modo getModo() {
        return modo; // SEGUIMIENTO, ORBITAL o AEREA.
    }

    /** Nombre del modo para el HUD. */
    public String nombreModo() {
        switch (modo) { // Un texto por modo.
            case ORBITAL: return "Orbital (mouse)"; // Arrastrar y ruedita.
            case AEREA: return "Aérea"; // Vista general (también con mouse).
            default: return "Seguimiento"; // Detrás del auto.
        }
    }

    /** Órbita que maneja el mouse en el modo actual, o null en seguimiento (donde el mouse no hace nada). */
    private Orbita orbitaActiva() {
        switch (modo) {
            case ORBITAL: return orbitaAuto; // Alrededor del auto.
            case AEREA: return orbitaAerea; // Alrededor del centro aéreo.
            default: return null; // Seguimiento.
        }
    }

    /**
     * Movimiento del mouse con mouse-look activo, en píxeles (dx a la derecha, dy hacia abajo). En la orbital y en la
     * aérea, mover a la derecha gira la cámara alrededor de su centro y mover hacia arriba la eleva. En la aérea,
     * Ventana lo redirige a desplazar() en vez de a este método mientras se sostiene Shift.
     */
    public void arrastrar(double dx, double dy) {
        Orbita orbita = orbitaActiva(); // La cámara que corresponde.
        if (orbita == null) { // En seguimiento el mouse no mueve la cámara.
            return; // Ignora el arrastre.
        }
        // dy es negativo hacia arriba: subir el mouse eleva la cámara. Los límites de φ los aplica la Orbita.
        orbita.girar(-(float) dx * SENSIBILIDAD_GIRO, -(float) dy * SENSIBILIDAD_ELEVACION);
    }

    /** Ruedita del mouse: pasos positivos (hacia adelante) acercan la cámara; en la orbital y en la aérea. */
    public void zoom(double pasos) {
        if (modo == Modo.ORBITAL) { // Alrededor del auto: pasos cortos.
            orbitaAuto.acercar((float) pasos * PASO_ZOOM);
        } else if (modo == Modo.AEREA) { // Sobre la ciudad: pasos largos.
            orbitaAerea.acercar((float) pasos * PASO_ZOOM_AEREO);
        }
    }

    /**
     * Movimiento del mouse con mouse-look activo y Shift sostenido, en píxeles: solo en la aérea, desplaza el punto
     * que mira la cámara como si se arrastrara el suelo con la mano (el suelo sigue al cursor). Se mueve en los ejes
     * de la pantalla proyectados al suelo: la derecha de la cámara es (cos θ, -sen θ) y su frente, (-sen θ, -cos θ).
     * Cuanto más lejos está la cámara, más avanza por píxel. El centro nunca sale de ±límite: la vista no se va al
     * vacío. En ORBITAL, Ventana igual puede llamarlo con Shift sostenido; se ignora porque el modo no es AEREA.
     */
    public void desplazar(double dx, double dy) {
        if (modo != Modo.AEREA) { // En los otros modos el botón derecho no hace nada.
            return;
        }
        float theta = orbitaAerea.getAngulo(); // Hacia dónde mira la cámara.
        float paso = SENSIBILIDAD_DESPLAZAMIENTO * orbitaAerea.getDistancia(); // Unidades del mundo por píxel.
        float derecha = -(float) dx * paso; // Arrastrar a la derecha lleva el centro a la izquierda.
        float adelante = (float) dy * paso; // Arrastrar hacia abajo acerca lo que estaba más lejos.
        centroX += (float) Math.cos(theta) * derecha - (float) Math.sin(theta) * adelante; // Derecha y frente, en X.
        centroZ += -(float) Math.sin(theta) * derecha - (float) Math.cos(theta) * adelante; // Derecha y frente, en Z.
        centroX = Math.max(-limite, Math.min(limite, centroX)); // Dentro de la ciudad.
        centroZ = Math.max(-limite, Math.min(limite, centroZ));
    }

    /** Elevación actual de la cámara orbital del auto, en radianes. */
    public float getElevacion() {
        return orbitaAuto.getElevacion(); // Entre ELEVACION_MIN y ELEVACION_MAX.
    }

    /** Distancia actual de la cámara orbital al auto. */
    public float getDistancia() {
        return orbitaAuto.getDistancia(); // Entre DISTANCIA_MIN y DISTANCIA_MAX.
    }

    /** Elevación actual de la cámara aérea, en radianes. */
    public float getElevacionAerea() {
        return orbitaAerea.getElevacion(); // Entre ELEVACION_AEREA_MIN y ELEVACION_AEREA_MAX.
    }

    /** Distancia actual de la cámara aérea a su centro. */
    public float getDistanciaAerea() {
        return orbitaAerea.getDistancia(); // Entre DISTANCIA_AEREA_MIN y getDistanciaAereaMax().
    }

    /** Distancia inicial de la cámara aérea (la vista de las capturas, que muestra toda la ciudad). */
    public float getDistanciaAereaInicial() {
        return distanciaAereaInicial;
    }

    /** DISTANCIA_AEREA_MAX: depende del tamaño de la ciudad (FACTOR_DISTANCIA_AEREA_MAX · límite). */
    public float getDistanciaAereaMax() {
        return FACTOR_DISTANCIA_AEREA_MAX * limite; // 143 con límite 55.
    }

    /** Plano lejano que se envía a ciudad.vert: la aérea más lejana ve la esquina opuesta de la ciudad. */
    public float getPlanoLejano() {
        float diagonal = (float) Math.hypot(2 * limite, 2 * limite); // De una esquina de la ciudad a la opuesta.
        return getDistanciaAereaMax() + diagonal + MARGEN_PLANO_LEJANO; // ≈ 319 con límite 55.
    }

    // ==================== CÁMARA DE SEGUIMIENTO: RECORTE Y SUAVIZADO ====================

    /**
     * Mayor distancia (hasta DISTANCIA_SEGUIMIENTO) a la que se puede poner la cámara detrás del auto sin salir de
     * ±(límite − MARGEN_BORDE_CAMARA) ni pasar sobre una manzana. Detrás del auto es (sen ángulo, cos ángulo).
     */
    float distanciaLibre(float autoX, float autoZ, float angulo) {
        float atrasX = (float) Math.sin(angulo); // Dirección hacia atrás, en X.
        float atrasZ = (float) Math.cos(angulo); // Y en Z.
        float libre = 0; // Último punto libre encontrado.
        for (float d = PASO_RECORTE; d <= DISTANCIA_SEGUIMIENTO + 1e-4f; d += PASO_RECORTE) { // Camina desde el auto hacia atrás.
            if (!puntoLibre(autoX + atrasX * d, autoZ + atrasZ * d)) { // Fuera del mapa o sobre una manzana.
                // El obstáculo empieza en algún lugar entre libre y d: se lo busca partiendo el tramo al medio.
                float tapado = d; // Primer punto tapado.
                for (int i = 0; i < ITERACIONES_RECORTE; i++) {
                    float medio = (libre + tapado) / 2; // Mitad del tramo que falta decidir.
                    if (puntoLibre(autoX + atrasX * medio, autoZ + atrasZ * medio)) {
                        libre = medio; // El obstáculo está más atrás.
                    } else {
                        tapado = medio; // El obstáculo está más cerca.
                    }
                }
                break; // Lo que sigue queda tapado: la cámara se detiene en el último punto libre.
            }
            libre = d; // Este punto sirve.
        }
        return Math.max(DISTANCIA_SEGUIMIENTO_MIN, Math.min(libre, DISTANCIA_SEGUIMIENTO)); // Nunca pegada al auto ni más lejos que la normal.
    }

    /** Indica si la cámara puede estar sobre (x, z): dentro de ±(límite − MARGEN_BORDE_CAMARA) y sobre la calle. */
    private boolean puntoLibre(float x, float z) {
        float borde = limite - MARGEN_BORDE_CAMARA; // La cámara no pasa de acá.
        return Math.abs(x) <= borde && Math.abs(z) <= borde && Mapa.esCalleEn(x, z);
    }

    /**
     * Cada cuadro: el ángulo de la cámara persigue al del auto (atraso · exp(−K_GIRO · dt)) y la distancia se recorta
     * trazando el rayo desde ese ángulo suavizado, que es donde está la cámara de verdad. Acercarse es inmediato;
     * alejarse, a VELOCIDAD_ALEJAMIENTO. Después de reiniciarSeguimiento() o de volver con C, se coloca sin barrido.
     */
    public void actualizarSeguimiento(float deltaTime, float autoX, float autoZ, float angulo) {
        if (reiniciarSeguimiento) { // Pedido pendiente (C, o el primer cuadro): justo detrás del auto.
            reiniciarSeguimiento(autoX, autoZ, angulo);
            return;
        }
        float atraso = diferenciaAngular(angulo, anguloCamara); // Cuánto le falta girar a la cámara, en (−π, π].
        anguloCamara = angulo - atraso * (float) Math.exp(-K_GIRO * deltaTime); // El atraso se achica igual a cualquier FPS.
        float libre = distanciaLibre(autoX, autoZ, anguloCamara); // Hasta dónde se puede alejar ahora.
        if (libre <= distanciaSeguimiento) { // Hay algo más cerca que la cámara actual.
            distanciaSeguimiento = libre; // Se acerca de inmediato: nunca queda dentro de un edificio.
        } else { // Hay más espacio: vuelve de a poco.
            distanciaSeguimiento = Math.min(libre, distanciaSeguimiento + VELOCIDAD_ALEJAMIENTO * deltaTime);
        }
    }

    /** Coloca la cámara de seguimiento justo detrás del auto, sin atraso ni barrido; Juego lo llama al presionar R. */
    public void reiniciarSeguimiento(float autoX, float autoZ, float angulo) {
        anguloCamara = angulo; // Mira desde atrás del auto, ya alineada.
        distanciaSeguimiento = distanciaLibre(autoX, autoZ, angulo); // Toda la distancia libre, sin volver de a poco.
        reiniciarSeguimiento = false; // Pedido atendido.
    }

    /** Diferencia a − b llevada a (−π, π]: el camino corto para girar de b hasta a. */
    static float diferenciaAngular(float a, float b) {
        double diferencia = (a - b) % (2 * Math.PI); // Entre −2π y 2π (el resto conserva el signo de a − b).
        if (diferencia > Math.PI) { // Más de media vuelta a la izquierda: es más corto por la derecha.
            diferencia -= 2 * Math.PI;
        } else if (diferencia <= -Math.PI) { // Y al revés.
            diferencia += 2 * Math.PI;
        }
        return (float) diferencia;
    }

    /** Distancia actual de la cámara de seguimiento detrás del auto. */
    public float getDistanciaSeguimiento() {
        return distanciaSeguimiento;
    }

    /** Ángulo suavizado de la cámara de seguimiento, en radianes (el del auto, con atraso). */
    public float getAnguloCamara() {
        return anguloCamara;
    }

    /** Altura de la cámara para mirar INCLINACION hacia abajo al punto que está ADELANTE del auto, a d detrás de él. */
    static float alturaSeguimiento(float d) {
        float alcance = d + ADELANTE; // Distancia horizontal de la cámara al punto de mira.
        return Math.max(ALTURA_MINIMA_SEGUIMIENTO, ALTURA_OBJETIVO_MIRA + alcance * (float) Math.tan(INCLINACION));
    }

    /** Posición {x, y, z} de la cámara de seguimiento: la misma que configurar() envía al shader. */
    float[] ojoSeguimiento(float autoX, float autoZ) {
        float d = Math.min(distanciaSeguimiento, distanciaLibre(autoX, autoZ, anguloCamara)); // Por si el auto se movió sin actualizar.
        float x = autoX + (float) Math.sin(anguloCamara) * d; // Detrás del auto (según la cámara) en X.
        float z = autoZ + (float) Math.cos(anguloCamara) * d; // Y en Z.
        return new float[] {x, alturaSeguimiento(d), z}; // Más cerca = más baja: la inclinación no cambia.
    }

    /** Punto {x, y, z} que mira la cámara de seguimiento: ADELANTE del auto según su ángulo real, a ALTURA_OBJETIVO_MIRA. */
    static float[] objetivoSeguimiento(float autoX, float autoZ, float angulo) {
        float x = autoX - (float) Math.sin(angulo) * ADELANTE; // El frente del auto es (−sen, −cos).
        float z = autoZ - (float) Math.cos(angulo) * ADELANTE;
        return new float[] {x, ALTURA_OBJETIVO_MIRA, z};
    }

    /** Centro de la cámara aérea en X. */
    public float getCentroX() {
        return centroX; // Entre -límite y límite.
    }

    /** Centro de la cámara aérea en Z. */
    public float getCentroZ() {
        return centroZ; // Entre -límite y límite.
    }

    /**
     * Posición {x, y, z} de la cámara aérea (la misma que configurar() envía al shader).
     * Con la distancia mínima (20) y la elevación mínima (20°) el ojo baja hasta ≈ 6.8 de altura; si justo cae sobre
     * una manzana con edificio (las torres llegan a ≈ 37 con la antena), quedaría adentro y se verían sus paredes
     * desde dentro. Por eso, solo sobre una celda con edificio, el ojo sube hasta MARGEN_TECHO por encima de su techo.
     * Sobre la calle o fuera de la ciudad la órbita no cambia.
     */
    float[] ojoAereo() {
        float[] ojo = orbitaAerea.ojo(centroX, 0, centroZ, 0); // Órbita alrededor del centro, sobre el suelo.
        int fila = Mapa.indiceCelda(ojo[2]); // Celda que queda debajo del ojo (las filas son Z).
        int columna = Mapa.indiceCelda(ojo[0]); // Y las columnas, X.
        boolean enCiudad = fila >= 0 && fila < Mapa.MAPA.length && columna >= 0 && columna < Mapa.MAPA[fila].length;
        if (enCiudad && Mapa.tipo(fila, columna) == Mapa.EDIFICIO) { // Encima de un edificio.
            ojo[1] = Math.max(ojo[1], Edificio.alturaTotal(fila, columna) + MARGEN_TECHO); // Nunca dentro de él.
        }
        return ojo;
    }

    /**
     * Mueve la cámara alrededor de la ciudad con las flechas horizontales.
     * Era el control de la primera lección, cuando solo existía la ciudad. En el juego completo las flechas
     * conducen el auto, así que Juego no lo llama: la vista aérea se maneja con el mouse.
     */
    public void orbitar(Ventana ventana, float deltaTime) {
        if (ventana.pulsada(GLFW_KEY_LEFT)) { // Comprueba la flecha izquierda.
            orbitaAerea.girar(-deltaTime, 0); // Reduce el ángulo a razón de un radián por segundo.
        }
        if (ventana.pulsada(GLFW_KEY_RIGHT)) { // Comprueba la flecha derecha.
            orbitaAerea.girar(deltaTime, 0); // Aumenta el ángulo a la misma velocidad.
        }
    }

    /** Elige entre la vista aérea, la orbital alrededor del auto y la cámara situada detrás del auto. */
    public void configurar(Shader shader, float autoX, float autoZ, float angulo, int ancho, int alto) {
        if (modo == Modo.AEREA) { // Vista general: órbita alrededor del centro aéreo, sobre el suelo.
            enviar(shader, ojoAereo(), centroX, 0, centroZ, ancho, alto, CAMPO_VISUAL_AEREO); // Órbita, sin entrar en los edificios.
            return; // Evita reemplazarla con la cámara de seguimiento.
        }
        if (modo == Modo.ORBITAL) { // Órbita alrededor de la carrocería; θ relativo al ángulo del auto.
            enviar(shader, orbitaAuto.ojo(autoX, ALTURA_OBJETIVO, autoZ, angulo), autoX, ALTURA_OBJETIVO, autoZ, ancho, alto, CAMPO_VISUAL);
            return; // No sigue con la cámara de seguimiento.
        }
        float[] objetivo = objetivoSeguimiento(autoX, autoZ, angulo); // Por delante del auto.
        enviar(shader, ojoSeguimiento(autoX, autoZ), objetivo[0], objetivo[1], objetivo[2], ancho, alto, CAMPO_VISUAL); // Hasta 8 detrás, mirando 11° hacia abajo.
    }

    /** Posición de la cámara calculada en el último configurar() (copia); Cielo centra la cúpula en ella. */
    public float[] getOjo() {
        return ojo.clone(); // Copia: nadie puede mover la cámara desde afuera.
    }

    /** Envía al shader la posición de la cámara (ojo), el punto que mira, la proporción de la ventana y el campo visual. */
    private void enviar(Shader shader, float[] ojoNuevo, float objetivoX, float objetivoY, float objetivoZ, int ancho, int alto, float campoVisual) {
        System.arraycopy(ojoNuevo, 0, ojo, 0, 3); // Recuerda el ojo para getOjo().
        shader.vector("uOjo", ojo[0], ojo[1], ojo[2]); // Posición de la cámara.
        shader.vector("uObjetivo", objetivoX, objetivoY, objetivoZ); // Punto al que mira.
        shader.decimal("uAspecto", (float) ancho / alto); // Mantiene las proporciones al redimensionar la ventana.
        shader.decimal("uPlanoLejano", getPlanoLejano()); // Distancia máxima visible, según el tamaño de la ciudad.
        shader.decimal("uCampoVisual", campoVisual); // Grados verticales de la perspectiva: menor en AEREA.
    }
}
