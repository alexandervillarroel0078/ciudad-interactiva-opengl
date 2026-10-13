# Guía de cambios en vivo

Rutas relativas a `src/main/java/com/graphics/ciudad/` (los shaders están en `src/main/resources/shaders/`).
**Después de editar: `mvn compile exec:exec`** (los shaders también se recargan al arrancar). Las líneas son aproximadas: busca el nombre de la constante con Ctrl+F.
Para volver atrás: `git checkout -- <archivo>`.

## 1. Velocidad máxima, aceleración y freno del auto
- `vehiculo/Auto.java`: `VELOCIDAD_MAX` (:27) = 16, `ACELERACION` (:29) = 9, `FRENO` (:31) = 7 (`RESISTENCIA` :30 = 0.7 es el roce al soltar).
- Prueba `VELOCIDAD_MAX = 30`, `ACELERACION = 20`, `FRENO = 15`.
- Se ve: el auto llega más rápido a tope y se detiene en menos metros con Espacio. Con 30 los giros son más cerrados (el giro crece con la velocidad, `VELOCIDAD_GIRO` :34).

## 2. Duración del ciclo del semáforo
- `mundo/Semaforo.java`: `DURACION_VERDE` (:37) = 5 y `DURACION_AMARILLO` (:38) = 2. El rojo (:39) = verde + amarillo y el ciclo (:40) = 14 s se recalculan solos.
- Prueba `DURACION_VERDE = 10` y `DURACION_AMARILLO = 3` (ciclo de 26 s).
- Se ve: las lentes cambian más lento y los autos NPC esperan más ante el rojo. No toques las demás fórmulas: la coordinación de accesos opuestos depende de ellas.

## 3. Cantidad, posición y alcance de las farolas
- Cantidad: `iluminacion/Iluminacion.java` `FAROLAS_POR_SECTOR` (:97) = `{3,3,3,2,2}` (13 en total; máximo 16 por `MAX_LUCES` :54). Posición: `DESPLAZAMIENTO_FAROLA` (:107) = 2.0, a lo largo de la cuadra. Prueba `{4,4,4,2,2}` y `DESPLAZAMIENTO_FAROLA = 0`.
- Alcance: `resources/shaders/iluminacion.frag` `FAROLA_ATENUACION_CUADRATICA` (:49) = 0.045 e `INTENSIDAD_FAROLA` (:51) = 3.0. Prueba `0.015` y `5.0`.
- Se ve (pulsa N para la noche): más pozas de luz naranja en las calles; con menor atenuación cada poza es mucho más grande. El cono se abre con `ANGULO_FAROLA_EXTERIOR` (:57) = 60.

## 4. Alcance, ángulo e intensidad de los faros del auto
- `resources/shaders/iluminacion.frag`: alcance `FAROS_ATENUACION_CUADRATICA` (:64) = 0.04, ángulo `CONO_BORDE` (:62) = 0.85 (≈32°) y `CONO_CENTRO` (:63) = 0.97, intensidad `INTENSIDAD_FARO` (:66) = 8.0.
- Prueba `0.01`, `CONO_BORDE = 0.6` (≈53°) y `INTENSIDAD_FARO = 12.0`. Cuanto menor el coseno, más ancho el haz.
- Se ve (N y luego F): el haz llega mucho más lejos y se abre como un abanico. Los faros del tráfico se ajustan con `INTENSIDAD_FARO_TRAFICO` (:74) y `ALCANCE_FARO_TRAFICO` (:75).

## 5. Colores y alturas de los edificios
- Colores: `mundo/Edificio.java` `PALETA_FACHADAS` (:26) y `PALETA_TECHOS` (:35); cada fila es RGB 0–1. Prueba cambiar todas las filas de fachadas a `{0.2f, 0.4f, 0.9f}` (azul).
- Alturas: `ALTO_PISO` (:58) = 3.0 y `PISOS_TORRE_MIN/MAX` (:59-60) = 8–11 (bloques: :70-71). Prueba `ALTO_PISO = 5` o `PISOS_TORRE_MAX = 20`.
- Se ve: edificios azules y/o rascacielos (la cámara aérea, el minimapa y las sombras se ajustan solos a `ALTURA_MAXIMA` :185). `SEMILLA_TIPO` (:102) reparte los tipos distinto.

## 6. Agregar o quitar un vehículo del tráfico y cambiar su ruta
- `trafico/Trafico.java`: `RUTAS_CELDAS` (:34), una fila por vehículo con celdas `{fila, columna}` pares (cruces) y tramos rectos. `VELOCIDADES` (:40) y `COLORES` (:41) se asignan por índice (si faltan, se repiten).
- Agregar: añade `{{2, 0}, {2, 10}, {8, 10}, {8, 0}}` como 5.ª ruta (más una velocidad y un color si quieres). Quitar: borra una fila (los tests suponen ≥ 3 vehículos). Máximo 8: `MAX_FAROS_TRAFICO` (:48).
- Se ve: aparece otro auto recorriendo ese rectángulo, parando en los semáforos del Centro. Si una ruta cruza una manzana o es diagonal, la app falla al arrancar con un mensaje claro (`validarRuta`).

## 7. Velocidad del tráfico NPC y distancia de frenada ante semáforos
- Velocidad: `trafico/Trafico.java` `VELOCIDADES` (:40) = `{7, 6, 8, 6.5f}`; prueba `{12, 10, 14, 11}`.
- Frenada: `trafico/Vehiculo.java` `DISTANCIA_FRENADO` (:59) = 8 (empieza a frenar), `DISTANCIA_DETENCION` (:60) = 1, `DESACELERACION` (:62) = 8, `DESACELERACION_AMARILLO` (:73) = 4, `ESPERA_MAXIMA_SEMAFORO` (:74) = 20. Prueba `DISTANCIA_FRENADO = 16` y `DESACELERACION_AMARILLO = 8`.
- Se ve: los autos van más rápido y frenan mucho antes del cruce, con más suavidad. Con amarillo más exigente casi todos frenan; con `ESPERA_MAXIMA_SEMAFORO = 3` y rojo largo se ve cómo se saltan la luz tras esperar.

## 8. Tamaño y posición del minimapa
- `juego/Minimapa.java`: `TAMANO_MAX_MINIMAPA` (:29) = 260 px, `FRACCION_MINIMAPA` (:30) = 3 (en ventanas chicas el lado es 1/3 de la dimensión menor), `MARGEN_MINIMAPA` (:31) = 18. La posición está en `calcularRecuadro` (:74-76).
- Tamaño: prueba `TAMANO_MAX_MINIMAPA = 400` y `FRACCION_MINIMAPA = 2`. Posición abajo a la izquierda: `x = margen;` y `y = margen;` en las líneas 75-76.
- Se ve: un recuadro más grande o en otra esquina (M lo oculta). Ojo: Hud dibuja los nombres de sector sobre el minimapa y usa el mismo cálculo, así que siguen alineados.

## 9. Niebla, color del cielo y transición día/noche
- Niebla: `iluminacion/Iluminacion.java` `DENSIDAD_NIEBLA_INICIAL` (:48) = 0.0005 (máx. :49 = 0.03). Prueba `0.01`. En vivo: teclas + y −, y F2 la apaga.
- Cielo: `iluminacion/Cielo.java` `HORIZONTE_DIA` (:41), `HORIZONTE_ATARDECER` (:42), `HORIZONTE_NOCHE` (:43), y el azul de arriba en `resources/shaders/cielo.frag` `CENIT_DIA` (:19). La niebla toma el color del horizonte.
- Hora: **no hay reloj de hora del día**: N alterna día/noche con una transición. `DURACION_TRANSICION` (:45) = 2 s (prueba 8); `NOCHE_AL_INICIAR` (:42) = false (prueba true para arrancar de noche).
- Se ve: ciudad borrosa y de color celeste/azul (prueba `HORIZONTE_DIA = {0.9f, 0.6f, 0.4f}` para un cielo anaranjado); con 8 s el atardecer se aprecia claramente.

## 10. Cantidad de árboles y bancos por parque
- `mundo/Parque.java`: `ARBOLES_MIN/MAX` (:44-45) = 4–6 y `BANCOS_MIN/MAX` (:46-47) = 2–4.
- Prueba `ARBOLES_MIN = 8`, `ARBOLES_MAX = 12` (el tope real es 12: son los lugares de `LUGARES_ARBOL` :67) y `BANCOS_MAX = 4` (sube más y se ocupará el espacio de la plaza).
- Se ve: parques mucho más arbolados. Con 0 y 0 quedan vacíos (solo fuente y senderos).

## 11. Agregar un destino de entrega
- `juego/Entregas.java`: `CELDAS_DESTINOS` (:33-37) y `NOMBRES_DESTINOS` (:39-43), con el mismo número de elementos.
- Agrega `{ULTIMA, 4}` en la primera y `"borde sur (centro)"` en la segunda. La celda debe ser calle (fila o columna par).
- Se ve: la 4.ª parada aparece con su baliza dorada y el marcador del HUD pasa a `0/4`. Hay que llegar a menos de `RADIO_LLEGADA` (:27) = 3 y a menos de `VELOCIDAD_LLEGADA` (:28) = 1.

## 12. Cambiar una tecla (N, F, M…)
- N (día/noche): `iluminacion/Iluminacion.java` :263 `GLFW_KEY_N`. F (faros): :272 `GLFW_KEY_F`. M (minimapa): `juego/Minimapa.java` :57 `GLFW_KEY_M`. L (lluvia): `motor/SistemaParticulas.java` :275 y el `import` de :7.
- Prueba `GLFW_KEY_N` → `GLFW_KEY_B` (en Iluminacion basta con `import static GLFW.*`, ya está).
- Se ve: B alterna día/noche y N ya no. Actualiza también el texto de `interfaz/Hud.java` `AYUDA` (:35-38) para que H muestre la tecla nueva.

## 13. Cantidad de lluvia y color de las partículas
- `motor/SistemaParticulas.java`: `LLUVIA_POR_SEGUNDO` (:73) = 900, `MAX_LLUVIA` (:72) = 1500 (el pool total `CAPACIDAD` :33 = 3000). Color: el `0.70f, 0.80f, 1.0f` de la llamada `emitir(TIPO_LLUVIA…)` (:268-269).
- Prueba `LLUVIA_POR_SEGUNDO = 2500`, `MAX_LLUVIA = 2500` y color `1.0f, 0.2f, 0.2f` (lluvia roja). Humo: `HUMO_GRIS` (:60) = 0.55.
- Se ve (tecla L): un aguacero más denso, con gotas del color elegido. Lluvia: `LLUVIA_VELOCIDAD` (:76) = 18 y `LLUVIA_VIENTO` (:77) = 1.2 inclinan o aceleran la caída.

## 14. Color del auto del jugador
- `vehiculo/Auto.java` `COLOR_CARROCERIA` (:40) = `{0.95f, 0.24f, 0.12f}` (rojo): carrocería y cabina.
- Prueba `{0.10f, 0.80f, 0.20f}` (verde) o `{0.1f, 0.1f, 0.1f}` (negro).
- Se ve: el auto cambia de color al reiniciar. El indicador/halo y los NPC no se tocan; los NPC tienen su color en `Trafico.COLORES` (:41).

## 15. Cambiar el tamaño del mapa y qué se rompe
- `mundo/Mapa.java` `MAPA` (:29): matriz cuadrada, de lado impar, con calles en las filas y columnas pares. En `ENTREGA.md` (sección "Mapa 13 × 13 de referencia") hay un 13 × 13 listo para pegar. Cambia las 11 filas por las 13 y arranca.
- Se adaptan solos: límites, colisiones, cámaras, minimapa, plano lejano, salida del auto, destinos, sectores, semáforos, PARE, carteles, farolas y pasos peatonales.
- Se rompe o hay que revisar: **`Trafico.RUTAS_CELDAS`** (si una ruta pisa una manzana, la app lanza error al arrancar), el máximo de 16 farolas/luminarias en el shader, y no poner un parque en la celda {penúltima fila, 1} (tapa la salida). Con más cruces del Centro hay más semáforos.

---

# Cómo explicar el proyecto en 2 minutos

**Qué es.** Una ciudad 3D en OpenGL 3.3 (Java + LWJGL), sin motores ni librerías de matemática 3D. Se maneja un auto con WASD, hay tráfico autónomo, semáforos, día/noche, lluvia, minimapa y un juego de entregas.

**Arquitectura (un paquete por responsabilidad):**
- `motor/`: ventana GLFW, shader, cubo y mallas (esfera, cilindro, cono), cámara, partículas y estadísticas. No conoce la ciudad.
- `mundo/`: la ciudad a partir de `Mapa` (matriz de 0 calle, 1 edificio, 2 parque): edificios, fachadas, parques, semáforos, señalización.
- `vehiculo/`: el auto del jugador (física simple, ruedas, luces, colisiones, marcas de frenado).
- `trafico/`: los autos NPC. Siguen rutas, frenan ante obstáculos y respetan semáforos.
- `iluminacion/`: día/noche, farolas, faros, cielo procedural y sombras.
- `juego/` e `interfaz/`: entregas, minimapa, HUD. `Juego` orquesta todo.

**Bucle principal con dt** (`Juego.loop`, :111): en cada vuelta se leen eventos, se calcula `dt = min(tiempoActual − tiempoAnterior, 0.05)` y se llama a `actualizar(dt)`. Después se dibuja (`dibujarFrame`) y se intercambian buffers. Toda la física usa `dt` (por eso va igual a 30 o a 144 FPS). El tope de 50 ms evita que el auto atraviese edificios tras una pausa; en pausa `dt = 0`, así que todo se congela con el mismo código.

**Iluminación** (`iluminacion.frag`, por fragmento):
- *Difusa (Lambert):* `max(dot(normal, direccionLuz), 0)`. Hay un ambiente (gris de día, azul de noche) más el sol o la luna como luz direccional.
- *Farolas:* de noche, cada una es un foco apuntando hacia abajo. El cono usa `smoothstep` sobre el coseno del ángulo; la atenuación es `1 + a·d + b·d²`. Hay un tope para no quemar paredes pegadas.
- *Faros:* dos conos delante del auto, inclinados hacia el suelo. Es la misma función `aporteFoco`, también para el tráfico con menor intensidad.
- Todo se mezcla con `uFactorNoche` (0 día, 1 noche) y al final se aplica la niebla exponencial `exp(-distancia·densidad)`.

# Preguntas probables del profesor

**1. ¿Cómo haces la proyección sin glm ni matrices de la librería?**
En `ciudad.vert` (:55-69) construyo a mano la base de la cámara (`frente`, `derecha = cross(frente, up)`, `arriba`), paso el punto a coordenadas de vista con productos punto y aplico la perspectiva con `1/tan(fov/2)`. `gl_Position = (clipX, clipY, clipZ, −vistaZ)`; la división por `w` la hace la GPU.

**2. ¿Qué significan `clipZ` y `w`, y por qué la profundidad no es lineal?**
`w = −z de vista`, y tras dividir queda `z/w`, una función racional de la distancia, con mucha precisión cerca y poca lejos. Por eso `PLANO_CERCANO = 0.1` y `uPlanoLejano` ≈ 319 se eligen con cuidado, y las sombras planas se elevan 0.01 para evitar z-fighting.

**3. ¿Cómo transformas las normales cuando escalas un objeto?**
Con la inversa transpuesta: en `ciudad.vert` (:42) divido la normal por la escala antes de girarla. Si la multiplicara por la escala, un cubo estirado tendría normales mal inclinadas. Las rotaciones son ortonormales y no hace falta invertirlas.

**4. ¿De dónde salen las normales de cada figura?**
El cubo trae las suyas por cara (normal plana, aristas vivas). En `Malla`, la esfera usa el vector del centro al vértice (suave); el cilindro usa normales radiales en el costado y verticales en las tapas, repitiendo vértices; el cono usa el producto vectorial de la generatriz y la tangente.

**5. ¿Qué es el culling y cómo lo configuras?**
Descarta las caras traseras antes de rasterizar: `glEnable(GL_CULL_FACE)`, `glCullFace(GL_BACK)`, `glFrontFace(GL_CCW)` en `Ventana` (:60-62). Hay que respetar el orden antihorario en todas las mallas. Se desactiva para el cielo (la cámara está dentro del cubo), el HUD y las partículas, y el minimapa invierte a `GL_CW` porque su proyección espeja el eje Y.

**6. ¿Para qué usas el stencil?**
En `Sombras` (:297), la sombra planar proyecta la geometría sobre el suelo. Dos objetos superpuestos oscurecerían dos veces el mismo píxel. Con `stencil == 0` (GL_EQUAL) y `GL_INCR`, cada píxel se oscurece una sola vez. Por eso `Ventana` pide 8 bits de stencil y `Juego` lo limpia cada cuadro.

**7. ¿Por qué usas `dt` y qué pasa si lo quitas?**
Sin `dt` la velocidad dependería de los FPS. Integro `velocidad += a·dt` y aplico la resistencia como `velocidad *= exp(−k·dt)`, que da el mismo decaimiento a cualquier tasa de cuadros. El `dt` está limitado a 50 ms y vale 0 en pausa.

**8. ¿Cómo se calcula la luz difusa y por qué `max(…, 0)`?**
Es la ley de Lambert: la intensidad es el coseno entre la normal y la dirección hacia la luz (producto punto de vectores unitarios). Si es negativo, la cara mira hacia el lado contrario a la luz y no recibe nada, por eso se recorta a 0.

**9. ¿Cómo funciona el cono de un faro o una farola?**
Se calcula el coseno del ángulo entre el eje del foco y el vector hacia el fragmento. `smoothstep(cosExterior, cosInterior, coseno)` da 1 dentro del núcleo, 0 fuera del borde y una transición suave. Luego se multiplica por la difusa y se divide por la atenuación.

**10. ¿Qué es la niebla y qué hace el z-buffer en tu escena?**
La niebla exponencial mezcla el color del objeto con el del horizonte: `mix(colorNiebla, color, exp(−d·densidad))`, con el mismo color que el cielo para que no haya costura. El z-buffer (`GL_DEPTH_TEST`) deja pasar solo el fragmento más cercano. El cielo se dibuja primero y sin profundidad, y el HUD al final y sin profundidad.
