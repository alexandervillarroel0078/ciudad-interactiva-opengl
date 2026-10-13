package com.graphics.ciudad.trafico; // Prueba el tráfico desde su mismo paquete.

import com.graphics.ciudad.motor.Cubo; // Se crea sin tocar la GPU: solo guarda referencias.
import com.graphics.ciudad.motor.Shader; // Se crea sin tocar la GPU: OpenGL se usa recién en crear().
import com.graphics.ciudad.mundo.Mapa; // Comprueba celdas de calle y límites.
import com.graphics.ciudad.mundo.Semaforo; // Ciclo de las luces que respetan los vehículos.
import com.graphics.ciudad.vehiculo.Colisiones; // Comprueba que ningún vehículo toque una manzana.
import java.util.List; // Tipo de la lista de vehículos.
import junit.framework.TestCase; // Proporciona las comprobaciones de JUnit usadas por Maven.

/** Simula el tráfico sin abrir una ventana OpenGL. */
public class TraficoTest extends TestCase {

    private static final float DT = 1f / 60; // Paso fijo: 60 cuadros por segundo.
    private static final float SEGUNDOS = 120; // Duración de la simulación.
    private static final float VENTANA_MOVIMIENTO = Semaforo.CICLO; // Cada ciclo de semáforo (14 s) se exige que cada vehículo haya avanzado: un rojo dura 7 s.
    private static final float AVANCE_MINIMO = 5; // Unidades mínimas recorridas en cada ventana de 5 s.
    private static final float LEJOS = 1000; // Posición del jugador fuera del mapa: no frena a nadie.
    private static final float MARGEN_GIRO = 8; // En los 8 primeros y últimos metros de cada tramo el vehículo está girando.
    private static final float TOLERANCIA_CARRIL = 0.35f; // Error admitido respecto a DESPLAZAMIENTO_CARRIL en los tramos rectos.
    private static final float FRACCION_RECORRIDO_MINIMO = 0.4f; // En 120 s cada vehículo recorre al menos el 40 % de crucero × tiempo.
    private static final float TOLERANCIA = 1e-4f; // Error de redondeo admitido en las velocidades.

    /** Crea el tráfico sin OpenGL. */
    private Trafico nuevoTrafico() {
        Shader shader = new Shader(); // Programa sin compilar: la prueba no dibuja.
        return new Trafico(shader, new Cubo(shader), () -> true); // Las rutas y el movimiento no necesitan la GPU; noche fija.
    }

    /**
     * Durante 120 s los vehículos siempre están en calles, dentro del mapa, sin tocar manzanas, y siguen moviéndose.
     * Como ahora frenan también unos ante otros, además se comprueba que NUNCA se solapan entre sí y que TODOS recorren
     * una distancia mínima total: si dos se esperaran mutuamente en un cruce, alguno quedaría trabado y fallaría.
     */
    public void testSimulacionDeDosMinutos() {
        Trafico trafico = nuevoTrafico(); // Tráfico recién creado.
        List<Vehiculo> vehiculos = trafico.getVehiculos(); // Vehículos a observar.
        assertTrue(vehiculos.size() >= 3); // El enunciado pide al menos tres vehículos.
        float limite = Mapa.LIMITE - Vehiculo.RADIO_VEHICULO; // El círculo completo debe quedar dentro del mapa.
        int pasos = Math.round(SEGUNDOS / DT); // Cantidad de cuadros simulados.
        int pasosPorVentana = Math.round(VENTANA_MOVIMIENTO / DT); // Cuadros entre controles de movimiento.
        float[] recorrido = new float[vehiculos.size()]; // Distancia acumulada en la ventana actual.
        float[] recorridoTotal = new float[vehiculos.size()]; // Distancia acumulada en los 120 s.
        float minimaEntreCentros = 2 * Vehiculo.RADIO_VEHICULO; // Dos círculos se solapan si sus centros están más cerca.
        for (int paso = 0; paso < pasos; paso++) { // Avanza la simulación cuadro a cuadro.
            float[][] antes = new float[vehiculos.size()][2]; // Posiciones antes del cuadro.
            for (int i = 0; i < vehiculos.size(); i++) { // Guarda la posición de cada vehículo.
                antes[i][0] = vehiculos.get(i).getX(); // X previo.
                antes[i][1] = vehiculos.get(i).getZ(); // Z previo.
            }
            trafico.actualizar(DT, LEJOS, LEJOS); // El jugador está lejos: ningún vehículo tiene que frenar por él.
            for (int i = 0; i < vehiculos.size(); i++) { // Revisa cada vehículo después del cuadro.
                Vehiculo v = vehiculos.get(i); // Vehículo revisado.
                String donde = "vehiculo " + i + " en t=" + (paso * DT) + " (" + v.getX() + ", " + v.getZ() + ")"; // Mensaje de error útil.
                assertTrue(donde, Mapa.esCalleEn(v.getX(), v.getZ())); // Siempre sobre una celda de calle.
                assertTrue(donde, Math.abs(v.getX()) <= limite && Math.abs(v.getZ()) <= limite); // Nunca fuera del mapa.
                assertTrue(donde, Colisiones.puedeCircular(v.getX(), v.getZ())); // Su círculo nunca invade una manzana.
                float avance = (float) Math.hypot(v.getX() - antes[i][0], v.getZ() - antes[i][1]); // Lo avanzado en el cuadro.
                recorrido[i] += avance; // Suma a la ventana.
                recorridoTotal[i] += avance; // Suma al total.
                for (int j = i + 1; j < vehiculos.size(); j++) { // Cada par de vehículos una sola vez.
                    Vehiculo otro = vehiculos.get(j);
                    float separacion = (float) Math.hypot(v.getX() - otro.getX(), v.getZ() - otro.getZ()); // Entre centros.
                    assertTrue("solapados " + i + " y " + j + ": " + donde, separacion >= minimaEntreCentros); // Nunca se tocan.
                }
                comprobarCarril(v, donde); // En los tramos rectos debe ir por su carril derecho.
            }
            if ((paso + 1) % pasosPorVentana == 0) { // Terminó una ventana de 5 segundos.
                for (int i = 0; i < vehiculos.size(); i++) { // Revisa cada vehículo.
                    assertTrue("vehiculo " + i + " detenido", recorrido[i] > AVANCE_MINIMO); // Debe seguir moviéndose.
                    recorrido[i] = 0; // Empieza a medir la siguiente ventana.
                }
            }
        }
        for (int i = 0; i < vehiculos.size(); i++) { // Ninguno quedó trabado: todos recorrieron lo mínimo.
            float minimo = FRACCION_RECORRIDO_MINIMO * Trafico.VELOCIDADES[i % Trafico.VELOCIDADES.length] * SEGUNDOS;
            assertTrue("vehiculo " + i + " recorrió " + recorridoTotal[i] + " < " + minimo, recorridoTotal[i] >= minimo);
        }
    }

    /**
     * 5 minutos con dt fijo y varios reinicios (R) en momentos arbitrarios, uno de ellos en plena curva: tras cada
     * cuadro, incluidos los posteriores al reinicio, ningún vehículo está en un edificio/parque ni fuera del mapa.
     * Además cada vehículo tiene estado propio (posición, ángulo y velocidad distintos) y completa su ruta: pasa por
     * todos sus waypoints, o sea que hace sus giros y continúa.
     */
    public void testCincoMinutosConReinicios() {
        Trafico trafico = nuevoTrafico();
        List<Vehiculo> vehiculos = trafico.getVehiculos();
        assertTrue(vehiculos.size() >= 3); // Al menos tres vehículos autónomos.
        float limite = Mapa.LIMITE - Vehiculo.RADIO_VEHICULO;
        int pasos = Math.round(300 / DT); // Cinco minutos.
        int[] reinicios = {Math.round(47.3f / DT), Math.round(101.9f / DT), Math.round(102.0f / DT), Math.round(233.4f / DT)}; // Incluye dos seguidos.
        boolean[][] visitado = new boolean[vehiculos.size()][]; // Waypoints visitados entre reinicios.
        int[] vueltas = new int[vehiculos.size()]; // Veces que cada uno volvió a su primer waypoint.
        for (int i = 0; i < vehiculos.size(); i++) {
            visitado[i] = new boolean[Trafico.RUTAS_CELDAS[i % Trafico.RUTAS_CELDAS.length].length];
        }
        comprobarEstadosPropios(vehiculos);
        for (int paso = 0; paso < pasos; paso++) {
            for (int reinicio : reinicios) {
                if (paso == reinicio) {
                    trafico.reset(); // Igual que la tecla R.
                    for (int i = 0; i < vehiculos.size(); i++) {
                        assertEquals(1, vehiculos.get(i).siguiente); // Vuelve a apuntar al segundo waypoint.
                        java.util.Arrays.fill(visitado[i], false);
                    }
                    comprobarEstadosPropios(vehiculos);
                }
            }
            trafico.actualizar(DT, LEJOS, LEJOS);
            for (int i = 0; i < vehiculos.size(); i++) {
                Vehiculo v = vehiculos.get(i);
                String donde = "vehiculo " + i + " paso " + paso + " (" + v.getX() + ", " + v.getZ() + ")";
                assertTrue(donde, Mapa.esCalleEn(v.getX(), v.getZ())); // Sobre calle.
                assertTrue(donde, Math.abs(v.getX()) <= limite && Math.abs(v.getZ()) <= limite); // Dentro del mapa.
                assertTrue(donde, Colisiones.puedeCircular(v.getX(), v.getZ())); // Sin tocar manzanas.
                assertFalse(donde, Float.isNaN(v.getX()) || Float.isNaN(v.getZ()) || Float.isNaN(v.getAngulo()));
                visitado[i][v.siguiente] = true;
                if (v.siguiente == 1 && visitado[i][0] && allTrue(visitado[i])) { // Completó una vuelta.
                    vueltas[i]++;
                    java.util.Arrays.fill(visitado[i], false);
                }
            }
        }
        for (int i = 0; i < vehiculos.size(); i++) {
            assertTrue("vehiculo " + i + " no completó ninguna vuelta", vueltas[i] >= 1);
        }
    }

    private static boolean allTrue(boolean[] valores) {
        for (boolean valor : valores) {
            if (!valor) {
                return false;
            }
        }
        return true;
    }

    /** Cada vehículo guarda su propia posición, orientación y velocidad: no hay dos iguales. */
    private static void comprobarEstadosPropios(List<Vehiculo> vehiculos) {
        for (int i = 0; i < vehiculos.size(); i++) {
            for (int j = i + 1; j < vehiculos.size(); j++) {
                Vehiculo a = vehiculos.get(i);
                Vehiculo b = vehiculos.get(j);
                assertTrue("misma posición " + i + "/" + j, a.getX() != b.getX() || a.getZ() != b.getZ());
                assertTrue("misma velocidad " + i + "/" + j, a.getVelocidad() != b.getVelocidad());
            }
        }
    }

    /**
     * En la parte recta de un tramo (lejos de ambas esquinas), la distancia a la línea central de la calle debe ser
     * DESPLAZAMIENTO_CARRIL, y del lado DERECHO de la dirección de avance.
     */
    private static void comprobarCarril(Vehiculo v, String donde) {
        float[] tramo = v.tramoCentral(); // {desdeX, desdeZ, hastaX, hastaZ} de la línea central.
        float dx = tramo[2] - tramo[0]; // Avance del tramo en X.
        float dz = tramo[3] - tramo[1]; // Avance del tramo en Z.
        float largo = (float) Math.hypot(dx, dz); // Longitud del tramo.
        float dirX = dx / largo; // Dirección unitaria en X.
        float dirZ = dz / largo; // Dirección unitaria en Z.
        float relX = v.getX() - tramo[0]; // Vector del inicio del tramo al vehículo, en X.
        float relZ = v.getZ() - tramo[1]; // Vector del inicio del tramo al vehículo, en Z.
        float avance = relX * dirX + relZ * dirZ; // Proyección sobre la línea central: cuánto avanzó.
        if (avance < MARGEN_GIRO || avance > largo - MARGEN_GIRO) { // Cerca de una esquina está girando.
            return; // Allí no se exige el carril exacto.
        }
        float derechaX = -dirZ; // Derecha de (dx, dz) = (-dz, dx), la convención de ejes del proyecto.
        float derechaZ = dirX; // Componente Z de la derecha.
        float lateral = relX * derechaX + relZ * derechaZ; // Distancia con signo: positiva = a la derecha de la línea.
        assertEquals("carril de " + donde, Vehiculo.DESPLAZAMIENTO_CARRIL, lateral, TOLERANCIA_CARRIL); // Carril derecho.
    }

    /** Al menos dos rutas recorren un mismo tramo de calle en sentidos opuestos (carriles distintos). */
    public void testHaySentidosOpuestosEnUnaMismaCalle() {
        boolean encontrado = false; // Se vuelve true al encontrar un par de tramos opuestos.
        int[][][] rutas = Trafico.RUTAS_CELDAS; // Rutas en celdas {fila, columna}.
        for (int a = 0; a < rutas.length; a++) { // Primera ruta del par.
            for (int b = a + 1; b < rutas.length; b++) { // Segunda ruta del par.
                for (int i = 0; i < rutas[a].length; i++) { // Tramos de la primera ruta.
                    for (int j = 0; j < rutas[b].length; j++) { // Tramos de la segunda ruta.
                        int[] a1 = rutas[a][i]; // Inicio del tramo de la ruta a.
                        int[] a2 = rutas[a][(i + 1) % rutas[a].length]; // Fin del tramo de la ruta a.
                        int[] b1 = rutas[b][j]; // Inicio del tramo de la ruta b.
                        int[] b2 = rutas[b][(j + 1) % rutas[b].length]; // Fin del tramo de la ruta b.
                        boolean mismoTramo = a1[0] == b2[0] && a1[1] == b2[1] && a2[0] == b1[0] && a2[1] == b1[1]; // Mismos extremos, invertidos.
                        encontrado |= mismoTramo; // Basta con un par.
                    }
                }
            }
        }
        assertTrue(encontrado); // Debe haber una calle con vehículos en ambos sentidos.
    }

    /** Las esquinas de carril de un giro de 90° quedan a DESPLAZAMIENTO_CARRIL de ambas líneas centrales. */
    public void testEsquinasDeCarril() {
        float d = Vehiculo.DESPLAZAMIENTO_CARRIL; // Desplazamiento lateral del carril.
        float[][] cuadrado = {{0, 0}, {0, -20}, {20, -20}, {20, 0}}; // Recorrido: norte, este, sur y oeste (giros a la derecha).
        float[][] esquinas = Vehiculo.calcularCarriles(cuadrado, d); // Esquinas del carril derecho.
        assertEquals(d, esquinas[1][0], 1e-5f); // Yendo al norte, la derecha es +X: la esquina está en X = d.
        assertEquals(-20 + d, esquinas[1][1], 1e-5f); // Saliendo al este, la derecha es +Z: la esquina está en Z = -20 + d.
        float[][] recto = {{0, 0}, {0, -20}, {0, -40}}; // Un waypoint en medio de una recta.
        float[][] enRecta = Vehiculo.calcularCarriles(recto, d); // El carril no debe torcerse en una recta.
        assertEquals(d, enRecta[1][0], 1e-5f); // Mismo desplazamiento d, no 2·d.
    }

    // ==================== FRENADO ANTE EL JUGADOR (regla del corredor) ====================

    /**
     * Posición {x, z} a "adelante" unidades por delante del vehículo y "derecha" unidades a su derecha
     * (negativo = izquierda). Frente = (-sen a, -cos a) y derecha = (cos a, -sen a), como en Vehiculo.
     */
    private static float[] relativo(Vehiculo v, float adelante, float derecha) {
        float a = v.getAngulo(); // Orientación del vehículo.
        float x = v.getX() - (float) Math.sin(a) * adelante + (float) Math.cos(a) * derecha; // Frente + derecha en X.
        float z = v.getZ() - (float) Math.cos(a) * adelante - (float) Math.sin(a) * derecha; // Frente + derecha en Z.
        return new float[] {x, z};
    }

    /** Distancia entre el centro del vehículo y un punto. */
    private static float distancia(Vehiculo v, float[] punto) {
        return (float) Math.hypot(v.getX() - punto[0], v.getZ() - punto[1]);
    }

    /**
     * El jugador en el carril CONTRARIO (a 2 · DESPLAZAMIENTO_CARRIL a la izquierda) y apenas adelante: con la regla
     * vieja (semiplano + 6 de radio) el vehículo se detenía; ahora sigue a velocidad de crucero y sin luces de freno.
     */
    public void testCarrilContrarioNoFrena() {
        Trafico trafico = nuevoTrafico(); // Tráfico recién creado.
        Vehiculo v = trafico.getVehiculos().get(0); // Primer vehículo, en una recta.
        float crucero = v.getVelocidad(); // Arranca a velocidad de crucero.
        float[] jugador = relativo(v, 3, -2 * Vehiculo.DESPLAZAMIENTO_CARRIL); // Carril contrario, 3 adelante.
        assertTrue(distancia(v, jugador) < 6); // La regla vieja lo habría detenido.
        assertEquals(Vehiculo.SIN_OBSTACULO, v.huecoHasta(jugador[0], jugador[1], Trafico.RADIO_JUGADOR)); // Fuera del corredor.
        for (int i = 0; i < 30; i++) { // Medio segundo: lo pasa de largo.
            trafico.actualizar(DT, jugador[0], jugador[1]);
            assertEquals(crucero, v.getVelocidad(), TOLERANCIA); // No perdió velocidad.
            assertFalse(v.frenando()); // Ni encendió el freno.
        }
    }

    /**
     * El jugador en el MISMO carril, 10 adelante (hueco 10 − 3.3 = 6.7, dentro de DISTANCIA_FRENADO): el vehículo no
     * frena en seco. En cada cuadro pierde como mucho DESACELERACION · dt, enciende el freno y nunca lo toca.
     * (A 7 u/s y con DESACELERACION = 8 necesita 7² / (2 · 8) ≈ 3 unidades para detenerse: el hueco alcanza.)
     */
    public void testMismoCarrilFrenaGradualmente() {
        Trafico trafico = nuevoTrafico();
        Vehiculo v = trafico.getVehiculos().get(0);
        float crucero = v.getVelocidad();
        float[] jugador = relativo(v, 10, 0); // Mismo carril, 10 adelante.
        trafico.actualizar(DT, jugador[0], jugador[1]); // Un cuadro.
        assertTrue(v.getVelocidad() < crucero); // Empezó a frenar...
        assertTrue(v.getVelocidad() >= crucero - Vehiculo.DESACELERACION * DT - TOLERANCIA); // ...sin bajar más de lo permitido.
        assertTrue(v.getVelocidad() > 0); // No se detuvo de golpe.
        assertTrue(v.frenando()); // Luces de freno encendidas.
        float anterior = v.getVelocidad();
        for (int i = 0; i < 180; i++) { // Tres segundos más.
            trafico.actualizar(DT, jugador[0], jugador[1]);
            assertTrue(v.getVelocidad() <= anterior + TOLERANCIA); // La velocidad nunca sube: sigue frenando.
            assertTrue(anterior - v.getVelocidad() <= Vehiculo.DESACELERACION * DT + TOLERANCIA); // Ni cae de golpe.
            assertTrue(distancia(v, jugador) >= Vehiculo.RADIO_VEHICULO + Trafico.RADIO_JUGADOR); // Nunca lo toca.
            anterior = v.getVelocidad();
        }
        assertTrue(v.getVelocidad() < 1); // Terminó casi detenido detrás del jugador...
        float hueco = distancia(v, jugador) - Vehiculo.RADIO_VEHICULO - Trafico.RADIO_JUGADOR; // Espacio que dejó.
        assertTrue("hueco " + hueco, hueco > 0.5f * Vehiculo.DISTANCIA_DETENCION); // ...sin llegar a tocarlo.
    }

    /**
     * El jugador PEGADO adelante (4 entre centros: hueco 0.7, menor que DISTANCIA_DETENCION): el vehículo termina
     * detenido, sin atravesarlo, con el freno encendido mientras espera. Al irse el jugador vuelve a andar, y reset()
     * lo devuelve al inicio de la ruta.
     */
    public void testPegadoAdelanteSeDetieneYReinicia() {
        Trafico trafico = nuevoTrafico();
        Vehiculo v = trafico.getVehiculos().get(0);
        float xInicial = v.getX(); // Posición antes de actualizar.
        float zInicial = v.getZ();
        float[] jugador = relativo(v, 4, 0); // Mismo carril, pegado.
        for (int i = 0; i < 120; i++) { // Dos segundos.
            trafico.actualizar(DT, jugador[0], jugador[1]);
            assertTrue(distancia(v, jugador) >= Vehiculo.RADIO_VEHICULO + Trafico.RADIO_JUGADOR - TOLERANCIA); // No lo atraviesa.
        }
        assertEquals(0f, v.getVelocidad(), TOLERANCIA); // Detenido.
        assertTrue(v.frenando()); // Esperando con el freno.
        for (int i = 0; i < 600; i++) { // Diez segundos sin jugador cerca.
            trafico.actualizar(DT, LEJOS, LEJOS); // Arranca de a poco y se mueve libremente.
        }
        assertTrue(Math.hypot(v.getX() - xInicial, v.getZ() - zInicial) > 1); // Se alejó del inicio.
        trafico.reset(); // Mismo reinicio que la tecla R.
        assertEquals(xInicial, v.getX(), 1e-6f); // Vuelve al primer waypoint en X.
        assertEquals(zInicial, v.getZ(), 1e-6f); // Vuelve al primer waypoint en Z.
        assertFalse(v.frenando()); // Y sin luces de freno.
    }

    /** El jugador DETRÁS, en el mismo carril: el vehículo no frena. */
    public void testDetrasNoFrena() {
        Trafico trafico = nuevoTrafico();
        Vehiculo v = trafico.getVehiculos().get(0);
        float crucero = v.getVelocidad();
        float[] jugador = relativo(v, -4, 0); // Cuatro detrás.
        assertEquals(Vehiculo.SIN_OBSTACULO, v.huecoHasta(jugador[0], jugador[1], Trafico.RADIO_JUGADOR));
        for (int i = 0; i < 30; i++) { // Medio segundo.
            trafico.actualizar(DT, jugador[0], jugador[1]);
            assertEquals(crucero, v.getVelocidad(), TOLERANCIA); // Sigue a velocidad de crucero.
        }
    }

    /** factorPorHueco: 1 libre, 0 a DISTANCIA_DETENCION o menos, lineal en el medio. */
    public void testFactorPorHueco() {
        assertEquals(1f, Vehiculo.factorPorHueco(Vehiculo.SIN_OBSTACULO), 0f); // Camino libre.
        assertEquals(1f, Vehiculo.factorPorHueco(Vehiculo.DISTANCIA_FRENADO), 1e-6f); // Borde de la zona de frenado.
        assertEquals(0f, Vehiculo.factorPorHueco(Vehiculo.DISTANCIA_DETENCION), 1e-6f); // Quiere estar detenido.
        assertEquals(0f, Vehiculo.factorPorHueco(-1), 0f); // Ya lo toca.
        float medio = (Vehiculo.DISTANCIA_FRENADO + Vehiculo.DISTANCIA_DETENCION) / 2; // Mitad de la zona.
        assertEquals(0.5f, Vehiculo.factorPorHueco(medio), 1e-6f);
    }

    // ==================== SEMÁFOROS ====================

    /** X de la línea de detención del cruce (4,4) para quien viaja al este por la fila 4. */
    private static float lineaEste() {
        return Mapa.centro(4) - Vehiculo.LINEA_DETENCION;
    }

    /** Pone al vehículo 0 en su carril, yendo al este por la fila 4 hacia el cruce (4,4): tramo de (4,0) a (4,4) de su ruta. */
    private static Vehiculo vehiculoAlEste(Trafico trafico) {
        Vehiculo v = trafico.getVehiculos().get(0);
        v.siguiente = 2; // Hacia el waypoint (4,4).
        v.x = Mapa.centro(0) + 5; // Poco después del cruce (4,0).
        v.z = Mapa.centro(4) + Vehiculo.DESPLAZAMIENTO_CARRIL; // Carril derecho de quien va al este.
        v.angulo = -(float) Math.PI / 2; // Frente (1, 0): al este.
        return v;
    }

    /**
     * El vehículo 0 va al este por la fila 4 hacia el cruce (4,4): grupo este-oeste, rojo cuando tiempo ∈ [7, 14) del ciclo.
     * Con rojo se detiene ANTES de la línea (sin cruzarla y sin frenar más fuerte que DESACELERACION); al pasar a verde
     * (tiempo = 14) vuelve a arrancar y cruza la línea.
     */
    public void testSeDetieneEnRojoYReanudaEnVerde() {
        Trafico trafico = nuevoTrafico();
        Vehiculo v = vehiculoAlEste(trafico);
        float tiempo = Semaforo.DESFASE_ESTE_OESTE; // 7: justo empieza el rojo del grupo este-oeste.
        assertEquals(Semaforo.ROJA, Semaforo.luzParaAcceso(tiempo, false));
        float anterior = v.getVelocidad();
        while (tiempo < Semaforo.CICLO - DT) { // Todo el rojo.
            trafico.actualizar(DT, LEJOS, LEJOS, tiempo);
            tiempo += DT;
            assertTrue("cruzó en rojo, x=" + v.getX(), v.getX() <= lineaEste() - Vehiculo.RADIO_VEHICULO + TOLERANCIA);
            assertTrue(anterior - v.getVelocidad() <= Vehiculo.DESACELERACION * DT + TOLERANCIA); // Frenada suave.
            anterior = v.getVelocidad();
        }
        assertEquals(0f, v.getVelocidad(), 0.2f); // Esperando (casi) detenido: se frena de a poco hasta la línea...
        assertTrue(v.getX() > lineaEste() - 5 * Vehiculo.RADIO_VEHICULO); // ...cerca de la línea, no a mitad de cuadra.
        float parado = v.getX();
        for (int i = 0; i < 4 * 60; i++) { // 4 s de verde (el verde dura 5).
            trafico.actualizar(DT, LEJOS, LEJOS, tiempo);
            tiempo += DT;
            if (i < 90) { // Primer segundo y medio: recta, antes de la curva del cruce (que limita la velocidad por su cuenta).
                assertTrue(v.getVelocidad() - anterior <= Vehiculo.ACELERACION_TRAFICO * DT + TOLERANCIA); // Arranque suave.
            }
            anterior = v.getVelocidad();
        }
        assertTrue(v.getX() > lineaEste() + Vehiculo.RADIO_VEHICULO); // Reanudó y cruzó la línea.
        assertTrue(v.getX() > parado);
    }

    /**
     * Con rojo fijo el vehículo espera detenido ante la línea, pero no para siempre: pasado ESPERA_MAXIMA_SEMAFORO
     * (contada desde que se detiene, unos segundos después de llegar) se salta la luz y sigue su camino.
     */
    public void testEsperaMaximaAnteRojoPermanente() {
        Trafico trafico = nuevoTrafico();
        Vehiculo v = vehiculoAlEste(trafico);
        float rojoFijo = Semaforo.DESFASE_ESTE_OESTE; // Instante de rojo para el grupo este-oeste; no avanza.
        float tCruce = -1; // Segundo en que cruza la línea.
        for (int i = 0; i < Math.round(80 / DT); i++) {
            trafico.actualizar(DT, LEJOS, LEJOS, rojoFijo);
            if (tCruce < 0 && v.getX() > lineaEste() - Vehiculo.RADIO_VEHICULO + TOLERANCIA) { // Su frente pasó la línea.
                tCruce = i * DT;
            }
            if (tCruce < 0) { // Mientras espera, no pasa la línea ni invade el cruce.
                assertTrue(v.getX() <= lineaEste() - Vehiculo.RADIO_VEHICULO + TOLERANCIA);
            }
        }
        assertTrue("nunca cruzó la línea", tCruce > 0);
        assertTrue("cruzó antes de agotar la espera: t=" + tCruce, tCruce >= Vehiculo.ESPERA_MAXIMA_SEMAFORO);
        assertTrue("esperó de más: t=" + tCruce, tCruce <= Vehiculo.ESPERA_MAXIMA_SEMAFORO + 15);
    }

    /** Con verde no hay obstáculo; con rojo muy cerca de la línea (no alcanza a frenar) tampoco: pasa. */
    public void testHuecoSemaforo() {
        Trafico trafico = nuevoTrafico();
        Vehiculo v = vehiculoAlEste(trafico); // Está lejos de la línea del cruce (4,4).
        assertEquals(Vehiculo.SIN_OBSTACULO, v.huecoSemaforo(7f)); // Rojo, pero aún fuera de la zona de frenado.
        v.x = lineaEste() - Vehiculo.RADIO_VEHICULO - 5; // A 5 de la línea, a velocidad de crucero.
        assertEquals(Vehiculo.SIN_OBSTACULO, v.huecoSemaforo(0f)); // Verde: sigue.
        assertEquals(5f, v.huecoSemaforo(7f), 1e-3f); // Rojo: debe parar.
        v.x = lineaEste() - Vehiculo.RADIO_VEHICULO - 1; // A 1: 7² / 16 = 3 > 1, no alcanza a frenar.
        assertEquals(Vehiculo.SIN_OBSTACULO, v.huecoSemaforo(7f)); // Pasa.
    }

    /** El jugador no puede meterse dentro de un vehículo, pero sí alejarse de él. */
    public void testJugadorNoAtraviesaVehiculos() {
        Trafico trafico = nuevoTrafico(); // Tráfico recién creado.
        Vehiculo v = trafico.getVehiculos().get(0); // Primer vehículo, en su posición inicial.
        float vx = v.getX(); // Centro del vehículo en X.
        float vz = v.getZ(); // Centro del vehículo en Z.
        assertTrue(trafico.bloquea(vx + 5, vz, vx + 2, vz, 1.65f)); // Acercarse hasta solaparse está prohibido.
        assertFalse(trafico.bloquea(vx + 2, vz, vx + 3, vz, 1.65f)); // Alejarse está permitido aunque siga solapado.
        assertFalse(trafico.bloquea(vx + 9, vz, vx + 8, vz, 1.65f)); // Acercarse sin tocarlo está permitido.
    }

    /** Una ruta diagonal o que pase por una manzana se rechaza al construirla. */
    public void testValidacionDeRutas() {
        try { // Ruta con un tramo diagonal.
            Trafico.validarRuta(new int[][] {{0, 0}, {2, 2}}); // Debe fallar.
            fail("Se aceptó un tramo diagonal"); // No debería llegar aquí.
        } catch (IllegalStateException esperado) { // El error es el comportamiento correcto.
            assertTrue(esperado.getMessage().contains("diagonal")); // Explica la causa.
        }
        try { // Ruta que baja por la columna 1, que tiene manzanas.
            Trafico.validarRuta(new int[][] {{0, 1}, {2, 1}}); // Debe fallar.
            fail("Se aceptó una ruta a través de una manzana"); // No debería llegar aquí.
        } catch (IllegalStateException esperado) { // El error es el comportamiento correcto.
            assertTrue(esperado.getMessage().contains("manzana")); // Explica la causa.
        }
    }
}
