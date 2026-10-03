# Ciudad Interactiva — documento de entrega

Simulador de conducción en una ciudad 3D generada por código. Está escrito en Java 17 con LWJGL 3.3.3 (GLFW + OpenGL 3.3 Core) y shaders GLSL. La ciudad y el auto se arman con un cubo, tres mallas redondeadas (esfera, cilindro y cono) y figuras extruidas desde un perfil (la cabina de los autos), cada una en su VAO/VBO, que se escalan, rotan y colorean en cada dibujo. El proyecto no usa imágenes, texturas ni modelos 3D externos.

## 1. Cómo ejecutar

Hace falta JDK 17 o superior, Maven y una placa de video compatible con OpenGL 3.3. Los comandos se ejecutan en la carpeta que contiene `pom.xml`:

```sh
mvn compile exec:exec
```

La primera ejecución descarga las dependencias. La clase principal es `com.graphics.ciudad.Main`, definida en `pom.xml`. El juego abre en un menú de inicio: **ENTER** empieza la partida y **ESC** cierra la ventana. Arranca en un estado pensado para la demostración: **de día** (`Iluminacion.NOCHE_AL_INICIAR`), con la **cámara de seguimiento**, el **minimapa visible** y el auto en la salida (esquina suroeste, mirando al norte).

Pruebas automáticas (no abren ventana):

```sh
mvn test
```

`mvn test` corre todas las pruebas **dos veces**: con la ciudad 11 × 11 de `Mapa.MAPA` y con el mapa 13 × 13 de referencia (`MapasDePrueba.MAPA_13`, ver sección 8). Informes en `target/surefire-reports` y `target/surefire-reports-mapa-13`.

## 2. Diseño de la ciudad

La ciudad está definida por la matriz `MAPA` de [Mapa.java](src/main/java/com/graphics/ciudad/mundo/Mapa.java). Tiene 11 × 11 celdas de 10 unidades (`TAM_CELDA`), así que mide 110 × 110 unidades (`TAMANO`). Cada borde está a 55 unidades del origen (`LIMITE`). Las filas avanzan en Z (de norte a sur) y las columnas en X (de oeste a este). Y es la altura.

- **Valores de `MAPA`:** `0` = calle, `1` = edificio, `2` = parque.
- **Calles:** las filas y columnas pares son calles continuas (6 avenidas horizontales y 6 verticales), por eso toda la red está conectada. `MapaTest` lo comprueba con un recorrido BFS.
- **Manzanas:** las celdas con fila y columna impares son 25 manzanas: **19 edificios y 6 parques**.
- **Edificios:** hay cinco tipos (torre, bloque, escalonado, casa baja y doble), con alturas de 3.2 a 22 y una paleta urbana de paredes y techos. Cada celda sortea su tipo y sus colores con un hash, así que la ciudad sale siempre igual (ver "Edificios: cinco tipos").

Plano con leyenda (norte arriba; la primera fila es Z = -50 y la última Z = +50):

```text
            X:  -50 -40 -30 -20 -10   0  10  20  30  40  50
 fila 0  Z=-50   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   1
 fila 1  Z=-40   ·  E<   ·   E   ·  P>   ·   E   ·  E^   ·
 fila 2  Z=-30   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·
 fila 3  Z=-20   ·  Pv   ·  E^   ·   E   ·  E>   ·  P>   ·
 fila 4  Z=-10   2   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·
 fila 5  Z=  0   ·  Ev   ·   E   ·   P   ·   E   ·  Ev   ·
 fila 6  Z= 10   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·
 fila 7  Z= 20   ·   E   ·   P   ·   E   ·  Ev   ·   E   ·
 fila 8  Z= 30   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·
 fila 9  Z= 40   A  E<   ·  E>   ·   E   ·   P   ·  E>   ·
 fila 10 Z= 50   ·   ·   ·   ·   ·   ·   ·   ·   3   ·   ·
```

| Símbolo | Significado |
|---|---|
| `·` | Calle (0) |
| `E` | Manzana con edificio (1): acera y edificio de uno de los cinco tipos; en cada cara a la calle, una planta baja según su uso (negocio con vidrieras y toldos, hall de oficinas, departamentos o casa) y ventanas en los pisos superiores |
| `P` | Parque (2): acera y césped con senderos en cruz, fuente central, 4 a 6 árboles (pinos y frondosos) y 2 a 4 bancos; pasos peatonales en sus calles vecinas |
| `^` `v` `<` `>` | Farola en la vereda de esa manzana, del lado norte, sur, oeste o este; su brazo lleva la bombilla sobre la calle de ese lado. Son 13 en total (`Iluminacion.LUCES`), ubicadas por reglas: ver "Farolas" más abajo |
| `A` | Salida del auto (-47.5, 40), mirando al norte (`Auto.X_INICIAL`, `Auto.Z_INICIAL`): calle del borde oeste, penúltima fila (`FILA_SALIDA`), a mitad de la última cuadra (ni en el cruce ni sobre un paso peatonal), en el carril derecho a `CARRIL_SALIDA` = 2.5 de la línea amarilla (X = -50), igual que el tráfico. Se calcula con `Mapa.centro()`. R lo devuelve ahí |
| `1` `2` `3` | Entregas en orden (`Entregas.CELDAS_DESTINOS`, convertidas a `DESTINOS` con `Mapa.centro()`): esquina noreste (celda (0, 10) = (50, -50)), borde oeste ((4, 0) = (-50, -10)) y borde sur-este ((10, 8) = (30, 50)) |

`MapaTest` verifica que todos los destinos y la salida caen sobre celdas de calle.

**Farolas: en la vereda de los edificios, no en el asfalto ni en los parques.** Cada farola se planta sobre la acera de una manzana **con edificio**, en la franja de mobiliario: a 0.4 del cordón del lado que da a la calle (la base ocupa de 0.19 a 0.61 del cordón, antes del toldo, que empieza a 0.8), cerca de mitad de cuadra. **Nunca va del lado de un parque:** la acera del parque mide solo 0.5 (el poste la ocuparía entera) y el parque ya se ilumina con sus luminarias globo (`uGlobos`). Una farola en la vereda de enfrente de un parque sí vale, porque está sobre la acera de un edificio. Todos los sectores tienen veredas de edificio de sobra para su cuota (el más justo, Zona Oeste en 11 × 11: 7 candidatas para 2). Al aplicar la regla se movieron solo las farolas que estaban en parques (3 con 11 × 11 y 2 con 13 × 13); las demás no cambiaron.

**Farolas: nunca frente a una puerta.** Todo edificio tiene la puerta en el centro de cada fachada a la calle (sea negocio, lobby, departamentos o casa), y la cuadra mide lo mismo que la manzana: una farola justo a mitad de cuadra quedaba delante de la puerta, y quien salía se llevaba el poste por delante. Regla:
- El poste sigue en la franja de mobiliario, pero corrido `DESPLAZAMIENTO_FAROLA` = 2.0 del centro del borde, a lo largo de la cuadra, hacia un lado o el otro (`sentido`, cuarto número de cada `LUCES`; se prueban los dos).
- Debe quedar a `DISTANCIA_MIN_PUERTA` = 1.5 o más del **eje de cada acceso** de esa cara (`Fachada.ejesDeAcceso()`): la puerta centrada de cualquier uso y, en las casas, el portón de garaje (en ±1.9, del lado de su cara). Con 2.0, la base deja casi 1 libre hasta el marco de la puerta más ancha (la doble del lobby, 1.6) y el poste queda a 3 de la esquina. Si el portón está del mismo lado, vale solo el otro sentido.
- Siguen las demás condiciones: fuera de los pasos (poste y bombilla), a 3 de semáforos, PARE y carteles, y los basureros esquivan los postes. Los pasos ocupan de 1.5 a 4.5 del centro de la cuadra, del lado de su cruce: con el poste corrido, la bombilla colgaría sobre el paso si lo hay de ese lado, así que en esas cuadras solo sirve el sentido contrario. Aun así, todos los sectores tienen candidatas de sobra: el más justo es el Centro (14 con 11 × 11 y 9 con 13 × 13, para 3).
- La distancia a la puerta se mide sobre el mismo eje u que usa `Fachada.puntoEnCara()` para puertas y portones: `posicion()` ubica el poste con esa misma función.
- Como ya ninguna farola puede ir en el centro del borde, al aplicar la regla se movieron **las 13** (con 11 × 11 y con 13 × 13); el reparto por sector ("punto más lejano") eligió de nuevo entre las candidatas corridas. Siguen siendo 13, con la misma cuota por sector.

Un brazo curvo lleva la bombilla `BRAZO_FAROLA` = 1.5 hacia la calle, así que queda 1.1 por encima de la calzada, a 4.5 de altura. La luz que recibe el shader sale de la bombilla.

**Luz de la farola: foco hacia abajo, no luz puntual.** Una luz puntual es omnidireccional: ilumina igual hacia todos lados, así que también encendía las paredes por encima de la pantalla, que en la realidad la tapa. Por eso `iluminacion.frag` trata cada farola como un foco (cono) con eje (0, −1, 0), con la misma lógica que los faros del auto (`factorCono()`, compartida): el ángulo entre el eje y la dirección bombilla → fragmento decide si llega luz. Dentro de `ANGULO_FAROLA_INTERIOR` (35°) es plena, fuera de `ANGULO_FAROLA_EXTERIOR` (60°) es nula y entre ambos baja con `smoothstep`; además conserva la atenuación por distancia (lineal + cuadrática). Resultado: un círculo de luz en el suelo, pleno en toda la vereda y que se desvanece pasando el centro de la calzada, y las paredes cercanas solo se iluminan por debajo de la bombilla (a unos 3 de altura como máximo), nunca por encima de la pantalla. La bombilla emisiva no cambia. Nada de la farola se apoya en la calzada ni participa en colisiones.

**Modelo de la farola.** Solo usa las mallas de los árboles (cilindro, esfera y cono de `Figuras`), sin figuras nuevas. De abajo hacia arriba:
- **Base:** cilindro corto y ancho, gris oscuro (0.42 de diámetro y 0.45 de alto).
- **Poste:** dos tramos de cilindro gris metálico. El de arriba es más delgado (0.2 → 0.14), así el poste se afina hacia la punta, a 4.15 de altura.
- **Brazo curvo:** tres tramos de cilindro que siguen `PERFIL_BRAZO`: uno empinado, uno suave y uno horizontal a 4.85. Cada tramo se inclina con `uRotacion`, cuyas columnas son el eje lateral, la dirección del tramo y su producto vectorial. Esferas chicas en los codos tapan las uniones.
- **Luminaria:** una pantalla cónica casi negra cuelga de la punta del brazo. Debajo asoma la bombilla, una esfera de 0.32 gris claro de día; de noche es emisiva y de color blanco cálido.

La bombilla se dibuja en `BOMBILLAS[i]`, el mismo punto que `preparar()` envía como `uLuces[i]`: el dibujo y la luz comparten la constante, sin coordenadas duplicadas. El modelo se arma sin OpenGL (`modeloFarola(i, noche)` devuelve la lista de piezas) y `dibujarFarolas()` solo lo recorre.

Las 13 están cerca de mitad de cuadra (corridas 2.0 para no tapar la puerta), lejos de las esquinas donde están los semáforos, los PARE y los pasos peatonales (al menos 3 unidades de cualquier señal), y repartidas por los cinco sectores: Centro 3, Barrio Norte 3, Parque Sur 3, Zona Oeste 2 y Zona Este 2. `FarolasTest` comprueba:
- **Ubicación del poste:** cada farola está en una manzana con edificio y calle por el lado indicado, y el poste queda sobre la acera.
- **Ninguna en un parque** (`testNingunaFarolaEnUnParque`, con 11 × 11 y 13 × 13): la regla descarta todos los bordes de parque, en todos los sectores; ningún poste queda en una vereda de parque ni sobre senderos, plaza o franjas.
- **Ninguna frente a una puerta** (`testNingunaFarolaFrenteAUnaPuerta`, con 11 × 11 y 13 × 13): revisando todos los edificios, cada poste está en la vereda de una sola cara y queda a `DISTANCIA_MIN_PUERTA` o más del eje de cada puerta y portón de esa cara. `testReglasDeUbicacion` comprueba además que cada poste está corrido exactamente `DESPLAZAMIENTO_FAROLA` del centro del borde.
- **Ubicación de la bombilla:** queda sobre el borde de la calle, en la punta del brazo.
- **Bombilla dibujada = luz:** hay al menos 9 farolas y, de día y de noche, la bombilla del modelo está exactamente en la posición de la luz de su farola. Solo es emisiva de noche.
- **Modelo conectado:** la base apoya en la acera, el poste llega al primer punto del brazo, el último tramo termina sobre la bombilla y cada rotación es una matriz ortonormal.
- **Sin conflictos:** respeta la separación con semáforos, PARE y carteles, y no hay farolas sobre pasos peatonales.
- **Reparto y colisiones:** hay farolas en todos los sectores y las calles siguen transitables.
- **Foco hacia abajo:** con los valores leídos de `iluminacion.frag`, un punto por encima de la bombilla recibe 0, el suelo justo debajo recibe el máximo (cono pleno) y un punto fuera del cono exterior recibe 0.
- **Atenuación:** a lo largo del eje, la luz disminuye con la distancia.
- **Círculo de luz:** en cada farola llega luz a la vereda hasta la pared del edificio y al centro de la calzada, y la pared a la altura de la pantalla queda a oscuras.

### Edificios: cinco tipos

Antes todos los edificios eran la misma caja con techo plano gris: solo cambiaban la altura y el color, y se veían clonados. Ahora `mundo/Edificio` elige para cada manzana un tipo (`TipoEdificio`) y lo arma con varios **volúmenes** (cajas con paredes) y **piezas de techo**:

Las alturas se cuentan en **pisos**: la planta baja mide `ALTO_MINIMO_BASE` = 3.2 y cada piso de arriba `ALTO_PISO` = 3.0, como uno real (`Edificio.alturaPared(pisos)` = 3.2 + (pisos − 1) · 3). Así cada fila de ventanas coincide con un piso.

| Tipo | Forma | Pisos (pared) | Azotea o techo |
|---|---|---|---|
| `TORRE` | Podio de 7 × 7 con el hall de oficinas y, encima, una torre angosta de 4.4 × 4.4 | 8 a 11 (24.2 a 33.2) | Antena (mástil de 3.5) o tanque de agua sobre cuatro patas, mitad y mitad |
| `BLOQUE` | Una caja de 7 × 7, como el edificio de antes | 4 a 6 (12.2 a 18.2) | Losa, baranda metálica en todo el borde y caja de ascensor corrida hacia una esquina |
| `ESCALONADO` | 2 o 3 niveles apilados de 7, 5.2 y 3.6 de lado, cada uno apoyado en el de abajo | Primer nivel 2 o 3, los demás 2 (4 a 7 en total) | Una losa en cada nivel |
| `CASA_BAJA` | Casa de 2 pisos (6.2 de pared) | Techo de 2 más | Techo a dos aguas: un prisma triangular color teja, con la cumbrera en X o en Z, y una chimenea |
| `DOBLE` | La manzana se parte en dos volúmenes pegados: uno alto de 4 de ancho y uno bajo de 3, de otro color | 5 a 7 y 2 a 3 | Una losa en cada volumen |

- **La torre se sigue leyendo como torre:** la más baja (8 pisos, 24.2) supera a la parte más alta de cualquier otro tipo (7 pisos, 21.2). Con este mapa las torres llegan a 28 a 36 contando la antena o el tanque (`Edificio.ALTURA_MAXIMA` = 35.9).
- **Cámaras y minimapa con torres altas:** el minimapa dibuja sus marcas (divisiones de sectores, destino e indicador del auto) por encima de `Edificio.ALTURA_MAXIMA` (antes estaban fijas en 22 a 26 y una torre las habría tapado). La cámara aérea, que con la distancia y la elevación mínimas baja a ≈ 6.8, sube hasta `MARGEN_TECHO` = 2 sobre el techo si queda encima de un edificio. El plano lejano (≈ 319) alcanza también a la punta de la torre más alta, y la cámara de seguimiento no cambia: va a 9 de altura sobre la calle, nunca sobre una manzana.

- **Siempre la misma ciudad:** el tipo, los colores, las alturas y la orientación de cada pieza salen de `Variacion.valor` con la fila, la columna y una semilla fija por decisión, sin `Random`. La misma celda da siempre el mismo edificio. `SEMILLA_TIPO` está elegida para que en este mapa los tipos salgan parejos: 4 torres, 4 bloques, 4 escalonados, 4 casas bajas y 3 dobles.
- **Paleta urbana:** las paredes son ladrillo, crema, blanco hueso, gris cemento, terracota, verde agua o amarillo pálido (`PALETA_FACHADAS`). Los techos son gris oscuro, gris claro, teja o verde de terraza (`PALETA_TECHOS`); las casas bajas son siempre de teja.
- **Misma huella:** el volumen de abajo ocupa siempre los 7 × 7 de la manzana y tiene al menos `ALTO_MINIMO_BASE` = 3.2, así que la planta baja (negocio, hall, departamentos o casa) entra igual en todos los tipos. Nada pasa de `MEDIA_HUELLA` = 3.65 desde el centro (la losa sobresale `VUELO_CORNISA` = 0.15, como la cubierta de antes). Las colisiones, `Mapa.LIMITE`, el minimapa y los destinos de entrega no cambian.
- **Dibujo:** `Ciudad` le pide a `Edificio` cada edificio. Las piezas se calculan una sola vez al crear la ciudad, y se dibujan con `Cubo` (paredes, losas, baranda, ascensor, patas, chimenea), el cilindro de `Figuras` (antena y tanque) y el prisma de `Figuras` (techo a dos aguas).

`EdificioTest` comprueba:
- **Determinismo:** la misma celda da siempre el mismo tipo, los mismos colores y las mismas piezas.
- **Variedad:** hay al menos 4 tipos y 4 colores de pared, y no todos los techos son gris oscuro.
- **Huella:** ninguna pieza sale de la huella de su manzana ni se hunde en la acera, y el volumen de abajo tiene altura para la planta baja.
- **Forma:** la torre es angosta y alta, el escalonado se achica y cada nivel se apoya en el de abajo, la casa baja tiene techo de teja a dos aguas y el doble tiene dos alturas distintas.

### Edificios: planta baja y ventanas

Las fachadas las dibuja `mundo/Fachada`.

**Usos de la planta baja.** Cada edificio tiene un uso (`mundo/UsoPlantaBaja`), que decide qué se dibuja a nivel de la vereda en las caras que dan a una calle. La regla sale del tipo y de la zona, sin azar: la misma celda da siempre el mismo uso.

| Tipo de edificio | Zona | Uso | Planta baja |
|---|---|---|---|
| `TORRE` | cualquiera | `LOBBY_OFICINAS` | Vidrio de piso a techo con parantes, puerta doble al centro y marquesina plana |
| `CASA_BAJA` | cualquiera | `CASA` | Puerta de madera con escalón y dos ventanas; en algunas, un portón de garaje en lugar de una ventana |
| `BLOQUE`, `ESCALONADO`, `DOBLE` | Centro o sobre una avenida principal | `COMERCIAL` | Vidriera, puerta y vidriera, con toldos (abajo) |
| `BLOQUE`, `ESCALONADO`, `DOBLE` | Barrios | `RESIDENCIAL` | Pared con dos ventanas comunes, puerta con escalón y alero chico |

- **Avenidas principales:** las calles (filas o columnas pares) a `DISTANCIA_AVENIDA` = 2 celdas o menos del eje central de la ciudad. En el mapa 11 × 11 el eje es la fila/columna 5, de manzanas, así que son las calles 4 y 6; en el 13 × 13 el eje es la calle 6, y con ella las 4 y 8. Un edificio da a una avenida si alguna de sus caras mira a una celda de esa calle. El Centro es `SECTOR_COMERCIAL` (índice 0 de `Mapa.SECTORES`).
- **Reparto actual:** 8 comerciales de 19 edificios (42 %), 3 residenciales (1,1; 1,9 y 9,1), 4 casas y 4 lobbies. Con el 13 × 13 de referencia (26 edificios): 13 comerciales (50 %), 3 residenciales (1,1; 11,1 y 11,11), 5 casas (garaje en 5,1 y 7,1) y 5 lobbies. Casas con garaje: 5,1 y 7,1 (`PROBABILIDAD_GARAJE` = 0.5, `SEMILLA_GARAJE` = 29).
- **Puerta siempre centrada:** en los cuatro usos, así ninguna puerta queda junto a una esquina.
- **Lobby:** los paños de vidrio van de `MARGEN_ESQUINA` hasta el marco de la puerta doble (`ANCHO_PUERTA_DOBLE` = 1.6, alto 2.3), con un parante cada `SEPARACION_PARANTES` = 1 como máximo, y llegan a 3.2 (`ALTO_VIDRIO_LOBBY`), bajo la losa del podio. De noche el vidrio es emisivo **suave** (`COLOR_VIDRIO_LOBBY_NOCHE`, más tenue que la vidriera); la puerta, los parantes y la marquesina no brillan. La marquesina sale `VUELO_MARQUESINA` = 0.7, igual que el toldo.
- **Departamentos y casas:** las ventanas de planta baja (`ANCHO_VENTANA_PLANTA_BAJA` = 1.4, en ±`CENTRO_VENTANA_PLANTA_BAJA` = ±1.9, con el alto de ventana del tipo) son ventanas comunes del piso 0: **de noche se encienden o no como todas las demás**. La puerta arranca sobre un escalón (`ALTO_ESCALON` = 0.15, `FONDO_ESCALON` = 0.35). Los departamentos llevan además un alero (`ANCHO_ALERO` × `VUELO_ALERO` = 1.6 × 0.5). El portón de garaje (`ANCHO_GARAJE` = 2.4 × `ALTO_GARAJE` = 2.2, con `LISTONES_GARAJE` = 5 juntas) ocupa el lugar de una ventana, en una sola cara a la calle.
- **Nada sale del toldo:** escalón, alero y marquesina no sobresalen más que `VUELO_TOLDO`; no llegan a la calzada ni a los postes, ni cambian colisiones.

**Planta baja comercial: vidriera | puerta | vidriera.** En cada cara de un edificio `COMERCIAL` que da a una calle, la fila de ventanas del primer piso se reemplaza por un negocio. Sobre los 7 de ancho de la cara, desde el centro hacia cada esquina, hay:

| Tramo | Ancho |
|---|---|
| Media puerta | 0.5 |
| Separación (`SEPARACION_PUERTA`) | 0.2 |
| Vidriera | 2.4 |
| Margen de esquina (`MARGEN_ESQUINA`) | 0.4 |

- **Puerta:** oscura, de 1 × 2 y **centrada**. Así ninguna puerta queda junto a una esquina y dos caras vecinas nunca tienen puertas pegadas.
- **Vidrieras:** una a cada lado de la puerta, de 2.4 × 1.4. De día son vidrio celeste. De noche se ven iluminadas desde adentro: son emisivas, cálidas y menos intensas que una placa blanca, con un degradado vertical, más claro abajo y más tenue arriba. Como `Cubo` pinta cada caja de un solo color, el degradado se arma con `FRANJAS_VIDRIERA` = 6 franjas horizontales cuyo color se interpola entre `COLOR_VIDRIERA_NOCHE_ABAJO` y `COLOR_VIDRIERA_NOCHE_ARRIBA` (`colorVidrieraNoche()`).
- **Toldos:** uno sobre cada vidriera, 0.05 más ancho a cada lado, sin tapar la puerta ni pasar el margen de la esquina. Como `Cubo` solo gira alrededor del eje Y, la inclinación se arma con 3 tiras escalonadas y un faldón.
  - **Vuelo:** sobresalen 0.7, así que su borde queda a 4.2 del centro del edificio: sobre la vereda y antes de los postes de semáforo (4.4) y de farola (4.6). Nunca llegan a la calzada ni cambian colisiones.
  - **Color:** rojo, verde, azul o naranja, y en algunos edificios a rayas con blanco. Se elige por celda y siempre es el mismo.

**Ventanas según el tipo.** `Fachada.ventanas()` recorre cada cara de cada volumen y reparte las ventanas con el patrón de su `TipoEdificio`:

| Tipo | Columnas por cara | Ancho × alto | Altura de piso |
|---|---|---|---|
| `TORRE` | 3, juntas | 0.9 × 1.4 | 3 |
| `BLOQUE` | 2, anchas | 2.0 × 1.4 | 3 |
| `ESCALONADO` | 3 | 1.1 × 1.4 | 3 |
| `CASA_BAJA` | 2, pocas | 1.1 × 1.2 | 3 |
| `DOBLE` | 3 | 0.9 × 1.4 | 3 |

- **Tamaño real:** el piso mide lo mismo en todos los tipos (`Edificio.ALTO_PISO` = 3) y las ventanas de 1.2 a 1.4 de alto, como las reales. El centro de cada fila está 1.4 por encima de su piso (`PRIMER_PISO_Y` = 1.9, luego 4.9, 7.9...): deja un antepecho de ≈ 0.7.

- **Caras angostas:** si las columnas no entran en una cara (por ejemplo, la parte baja de un doble), se usan menos, dejando `MARGEN_LATERAL` a cada lado.
- **Pisos alineados:** los pisos se cuentan desde la acera para todo el edificio, así las filas de ventanas de volúmenes vecinos coinciden.
- **Ventanas que no se ponen:** las que no entran entre la base y el tope de su volumen (`MARGEN_VERTICAL`), las del patrón que caerían en la planta baja de una cara a la calle (por debajo de `TOPE_PLANTA_BAJA`: ahí manda el uso, con su vidriera, su hall o sus propias ventanas que esquivan la puerta) y las que tapa otro volumen del mismo edificio, como la parte baja de un doble o el nivel de abajo de un escalonado. Por eso las casas bajas son siempre de 2 pisos (`PISOS_CASA_MIN` = 2): con uno solo, cuando toda planta baja era negocio, no quedaba lugar para ventanas (la casa de la celda 5,1 no tenía ninguna). `FachadaTest` verifica que todos los edificios tengan ventanas.

**Ventanas según día y noche.**
- **De día:** todas son vidrio claro, blanco-celeste grisáceo, sin emisión. El sol las ilumina como a cualquier superficie.
- **De noche:** alrededor del 65 % está encendida (`PORCENTAJE_VENTANAS_ENCENDIDAS`); hoy son 613 de 962 (64 %).
  - **Encendidas:** emisivas, con tonos de `TONOS_VENTANA`: amarillo cálido 40 %, blanco cálido 30 %, anaranjado suave 20 % y blanco frío 10 %.
  - **Apagadas:** azul-gris muy oscuro.
- **Sin parpadeo:** cada ventana decide con un hash de edificio, volumen, cara, piso y columna (`Variacion.valor`, sin `Random`), así que la misma ventana siempre está igual.
- **Estado día/noche:** se lee de `Iluminacion.esNoche()` y no se duplica.

`FachadaTest` comprueba:
- **Ventanas:** cada una tiene siempre el mismo estado; de noche la fracción encendida queda dentro de ±10 % y aparecen todos los tonos; de día ninguna es emisiva. Además, cada ventana queda dentro de su volumen, nunca sobre el negocio ni tapada por otro volumen, y el patrón cambia con el tipo.
- **Usos:** en departamentos y casas cada cara a la calle tiene dos ventanas de planta baja (una si ahí está el portón), sin tapar la puerta, y de noche unas se encienden y otras no; el garaje solo aparece en casas (en algunas sí y en otras no), mirando a la calle y sin tocar la puerta ni la esquina; marquesina, alero y escalón no salen más que el toldo. `UsoPlantaBajaTest` comprueba que cada torre sea lobby y cada casa baja sea casa, que los demás sigan la regla de zona, que haya al menos 3 usos distintos y al menos 40 % de comercio, y que la misma celda dé siempre el mismo uso.
- **Planta baja:** ninguna puerta está a menos de `MARGEN_ESQUINA` de una esquina y las puertas de caras vecinas nunca comparten esquina. Además, las vidrieras y los toldos entran en la cara sin tapar la puerta, y de noche cada franja de la vidriera es igual o más tenue que la de abajo, todas menos intensas que la placa de antes.
- **Toldos:** su vuelo no llega a la calzada y se usan todos los colores, lisos y a rayas.

### Parques

Cada parque (clase `mundo/Parque`) tiene:
- **Senderos:** dos caminos beige en cruz, apenas elevados sobre el césped, que unen los cuatro lados con el centro.
- **Plaza y franjas de acceso:** una plaza octogonal pavimentada rodea la fuente, y una franja pavimentada recibe cada paso peatonal que llega al parque (ver "Reglas peatonales del parque").
- **Fuente central:** base cilíndrica gris, espejo de agua azul (levemente emisivo de noche), pilar con plato y un chorro.
- **Árboles:** entre 4 y 6, en las esquinas y los bordes; el centro queda libre para la fuente. Hay dos tipos:
  - **Frondoso:** tronco cilíndrico fino y una copa de 3 esferas de distinto tamaño, desplazadas entre sí.
  - **Pino:** tronco y 3 conos apilados que se achican hacia arriba.

  **Escala real:** los frondosos miden de 5 a 7 y los pinos de 5 a 6 (`ALTURA_FRONDOSO_*`, `ALTURA_PINO_*`), como árboles de vereda. Crecen hacia arriba: el tronco es el 40 % del frondoso (la copa empieza por encima de una persona) y el 20 % del pino, y la copa se estira en vertical. El ancho de la copa no cambia (1.8 a 2.4, nunca más de un cuarto del parque), así que no sale del césped. Los mismos árboles, con la misma escala, están en el campo que rodea la ciudad (`Parque.arbol()`, compartido con `Entorno`).
- **Bancos:** entre 2 y 4 de madera con patas, en las diagonales entre los senderos, en el borde de la plaza y mirando hacia la fuente.
- **Luminarias globo y basureros:** 1 o 2 luminarias peatonales en el borde de los senderos y un basurero junto a cada banco (ver "Luminarias de parque y basureros").

Los parques no son copias. Cada uno usa su fila y su columna para "sortear" cuántos árboles tiene, dónde van, la mezcla de pinos y frondosos, las alturas, el tamaño de las copas, los tonos de verde y la cantidad de bancos. Para eso usa `Parque.variacion()`, una función de hash sin azar: da siempre el mismo resultado, así que el parque se ve igual en todos los cuadros y en cada ejecución.

Todo queda dentro de la celda del parque. Como ahora la copa está a la altura de las señales (de 2 a 7), un árbol solo se planta si su copa más ancha queda a `MARGEN_POSTES` = 0.2 de cada semáforo, PARE, cartel de sector y farola de su acera. Las colisiones no cambian: la manzana entera ya es un obstáculo. `ParqueTest` verifica cantidades, distancias, altura según el tipo, que la copa no salga del césped ni toque farolas, pantallas, semáforos o PARE, orientación de los bancos, que cada parque sea distinto, que cada parque tenga al menos 1 árbol y 1 banco (también probado con 13 × 13) y que las calles sigan transitables.

### Reglas peatonales del parque

El pavimento del parque (senderos, plaza y franjas) está todo a la misma altura, la del sendero (`TOPE_CESPED` + `GROSOR_SENDERO` = 0.40), y sus piezas se tocan: forman **un solo camino**. Un peatón que cruza la calle por un paso pisa una franja, sigue por ella hasta un brazo de la cruz, llega a la plaza y la rodea hasta cualquier otro brazo sin pisar césped. Todo sale de `Mapa` y de `Decoracion.UBICACIONES_PASOS`, así que funciona igual con 13 × 13.

1. **Senderos de borde a borde.** Los dos brazos de la cruz miden todo el césped (9) y se cruzan bajo la fuente.
2. **Plaza alrededor de la fuente.** Es un **octógono** regular: un cuadrado recto más el mismo cuadrado girado 45° (`cubo.caja` + `cubo.cajaGirada`). Su apotema (del centro a cada lado) es `MITAD_PLAZA` = `RADIO_FUENTE` + `PASO_FUENTE` = 1.2 + 1.0 = 2.2, así que entre la fuente y el césped queda siempre al menos 1 para caminar. Se eligió octógono y no círculo porque el cilindro de `Figuras` tiene solo 10 lados; el octógono sale exacto con dos cajas y sus lados diagonales quedan paralelos al frente de los bancos. Para saber si un punto está en la plaza se usa el "radio octogonal" `max(|x|, |z|, (|x| + |z|) / √2)`: vale 2.2 en todo el borde (`Parque.radioOctogonal()`).
3. **Cada paso desemboca en pavimento.** El paso está pegado a su cruce y el brazo está en el medio de la cuadra, así que el paso llega al borde del parque 3 al costado del brazo. Por cada paso que llega, `Parque.franjas()` pavimenta el pie de ese borde desde el eje del brazo hasta el extremo del paso, con `PROFUNDIDAD_FRANJA` = 0.9 hacia adentro. Con 11 × 11 son 30 franjas y con 13 × 13, 55.
4. **Bancos en el borde de la plaza, mirando a la fuente.** `DISTANCIA_BANCO` ya no es un número fijo: se deduce de la plaza para que las esquinas delanteras del banco queden `MARGEN_BANCO_PLAZA` = 0.05 afuera del octógono (≈ 2.63 del centro). El banco queda sobre el césped y el frente, a 0.18 del lado diagonal de la plaza.
5. **Nada pisa el pavimento.**
   - **Árboles:** los troncos quedan antes de la franja (3.47 < 3.6). Un **pino** tiene ramas casi hasta el suelo: si su copa tocaría un sendero, la plaza, una franja o el lugar de un basurero, se planta un **frondoso** en su lugar (copa por encima de 2, una persona pasa por debajo). La cantidad de árboles no cambia; hay menos pinos (6 de 29 con 11 × 11).
   - **Luminarias:** siguen en el borde de un brazo, pero no en la plaza ni en las franjas, y dejan libre el lugar de cada basurero.
   - **Basureros:** sobre el césped, fuera de todo el pavimento (`Parque.tocaPavimento()`).
6. **Un basurero por banco.** El costado del cesto se sortea **una vez por parque** (`Parque.ladoBasureros()`), no una vez por banco. Como el costado es local al banco, todos los cestos quedan "girados" en el mismo sentido y cada uno junto a un brazo distinto (a más de 4 entre sí). Antes, dos bancos vecinos podían elegir el mismo brazo, sus cestos quedaban a 1.98 (menos que `DISTANCIA_MIN_ENTRE_BASUREROS` = 2) y un banco se quedaba sin cesto. Árboles y luminarias le dejan libre ese lugar (`Parque.lugarBasurero()`). `RETIRO_BANCO` bajó a 0.15 para que el cesto quede a 0.92 del tronco de un árbol de borde (el mínimo es 0.8).

**Pendiente:** no hay rebaje de cordón. Toda la manzana es una acera de 0.3 de alto (`Ciudad`), así que el peatón sube ese escalón al llegar del paso.

`ParquePeatonalTest` lo prueba con 11 × 11 y 13 × 13 (ver sección 4).

### Luminarias de parque y basureros

**Dos tipos de luz.** La farola de calle tiene una **pantalla** que la tapa por arriba: una luz puntual iluminaría las paredes por encima de ella, algo imposible. Por eso es un **foco** hacia abajo (cono, `uLuces`). La luminaria de parque es un **globo** de vidrio sin pantalla: la luz sale hacia todos lados (también ilumina las copas de los árboles desde abajo). Por eso es una **luz puntual** (`uGlobos`): solo cuenta la distancia (atenuación) y hacia dónde mira la cara (difusa), sin factor de cono. Las dos se calculan solo de noche. De día el globo es gris lechoso y de noche es emisivo.

**Límite de luces del shader.** `uLuces[16]` (`Iluminacion.MAX_LUCES`) ya tenía 13 farolas ocupadas, así que quedaban 3 lugares para 6 parques. Se eligió un **arreglo propio** `uGlobos[16]` (`Iluminacion.MAX_GLOBOS`, igual a `MAX_GLOBOS` en `iluminacion.frag`):
- Cada tipo de luz tiene su fórmula y el bucle no pregunta "¿foco o puntual?" en cada iteración.
- **Costo:** 16 vec4 más de uniforms (en total unos 84 de los 256 que garantiza OpenGL 3.3) y, de noche, de 6 a 12 iteraciones más por fragmento, baratas porque no calculan cono.
- "Luces solo de noche" no hubiera liberado lugares: el tamaño del arreglo se fija al compilar el shader.
- **Cantidad por parque** (`Parque.luminariasPorParque()`): `LUMINARIAS_MAX_POR_PARQUE` = 2 si entran todas en `uGlobos`; si no, 1 (nunca menos).
  - 11 × 11: 6 parques × 2 = 12.
  - 13 × 13 de referencia (10 parques): 10 × 1 = 10.
  - Con más de 16 parques, `LuminariasParqueTest` lo avisa.

**Luminaria globo (`Parque`).**
- **Modelo:** base, poste fino verde oscuro, portaglobo y una esfera de 0.45. El centro del globo está a `ALTURA_GLOBO` = 3.0 sobre el césped y la punta a 3.225.
- **Mismo punto para dibujo y luz:** `Parque.LUMINARIAS` guarda el centro del globo, que es el que se dibuja y el que `Iluminacion.preparar()` envía como `uGlobos[i]`.
- **Reglas de ubicación:**
  - **Sobre un sendero:** en el borde de un brazo de la cruz, a `DESVIO_LUMINARIA` = 0.5 del eje (la base de 0.24 no sale del sendero de 1.4), a 2.4, 3.0 o 3.6 del centro (`DISTANCIAS_LUMINARIA`). Son 24 candidatas por parque.
  - **Sin chocar:** con la copa real de cada árbol (el globo está a la altura de las copas), con el círculo de cada banco (`MARGEN_LUMINARIA` = 0.15) y con semáforos, PARE, carteles y farolas de la acera (`MARGEN_LUMINARIA_POSTES` = 0.5). La fuente queda lejos por construcción.
  - **Sin estorbar al peatón:** la base no toca la plaza ni una franja de acceso, y deja libre el lugar del basurero de cada banco (con la misma distancia que exige `Basureros`).
  - **Repartidas:** la primera es la primera candidata válida desde un punto de partida propio del parque (`variacion()`); la segunda, la válida más lejana a la primera. Sin `Random`.

**Basureros (`mundo/Basureros`).** Un cilindro verde de 0.5 de diámetro con un aro y una tapa gris un poco más ancha, de 0.9 de alto en total. No participan en colisiones (están dentro de las manzanas). Hay tres reglas de ubicación, que se aplican en este orden:
1. **Parques:** al costado de cada banco (`BANCOS_POR_BASURERO` = 1; con 2 va uno cada dos bancos) y `RETIRO_BANCO` = 0.15 más atrás, lejos de la fuente. El costado se sortea una vez por parque (`Parque.ladoBasureros()`); si ese no sirve, se prueba el otro. Queda sobre el césped, fuera de los senderos, la plaza y las franjas (ver "Reglas peatonales del parque").
2. **Esquinas de cruces con paso peatonal:** entre el cruce y el paso quedan solo 0.5 de vereda (`SEPARACION_CRUCE`), así que en la esquina misma un basurero taparía el paso. Va **a la salida del paso**, del lado de la cuadra, a `HOLGURA_SALIDA_PASO` = 0.4 del paso y sobre la **franja de mobiliario**: `MARGEN_CORDON` = 0.45 del cordón, entre el borde del toldo y la calzada, donde en una ciudad real van farolas, señales y cestos. Ahí no tapa puertas ni vidrieras. En las esquinas con semáforo o PARE la señal está justo en la salida del paso, así que se descartan por distancia.
3. **Junto a algunos negocios**, solo en edificios de planta baja `COMERCIAL` (`FRACCION_NEGOCIOS` = 0.35 de sus caras, elegidas con `Variacion` y `SEMILLA_NEGOCIOS`; hoy 6): pegado a la pared, en el tramo ciego entre la vidriera y la esquina del edificio.

**Sin basureros en las veredas de los parques:** miden 0.5, lo mismo que el basurero, y el parque ya tiene los suyos.

**Distancias mínimas de los basureros (con nombre):**
- **Postes:** semáforos, PARE, carteles y farolas, con `MARGEN_POSTES` = 0.35 hasta la mitad de la señal (se reutiliza `Parque.cercaDeUnPoste()`).
- **Pasos:** el paso y la franja de vereda donde desemboca (el rectángulo del paso estirado de pared a pared), con `HOLGURA_PASO` = 0.3.
- **Negocios:** "tapar" es ocupar el frente de la puerta o de una vidriera a menos de `ZONA_FRENTE_NEGOCIO` (el toldo + 0.05) de la pared.
- **Árboles:** del pino, su copa (tiene ramas casi hasta el suelo); del frondoso, `DISTANCIA_MIN_TRONCO` = 0.8 (su copa empieza a 2 de alto).
- **Luminarias:** `MARGEN_LUMINARIA` = 0.4.
- **Entre basureros:** `DISTANCIA_MIN_ENTRE_BASUREROS` = 2.

Con 11 × 11 hay 19 basureros en parques (uno por banco), 44 en esquinas y 6 junto a negocios; con el 13 × 13 de referencia (10 parques), 29, 63 y 6.

**Sombras:** cada basurero y cada luminaria tiene una sombra falsa chica (`ESCALA_SOMBRA_BASURERO`, `SOMBRA_LUMINARIA`).

**Tests:**
- `LuminariasParqueTest` comprueba:
  - al menos 1 luminaria por parque y que todas entren en `uGlobos`, con el tamaño leído de `iluminacion.frag`;
  - la altura del globo;
  - que la base quede sobre el sendero, fuera de la fuente, del césped hacia adentro, nunca en la calzada ni sobre un paso;
  - que no choque con árboles, bancos, farolas, semáforos, PARE, carteles ni basureros;
  - que sea determinística.
- `BasurerosTest` comprueba:
  - la escala y que haya de los tres tipos;
  - que ninguno toque la calzada ni un paso o su salida;
  - que solo haya en veredas de edificio, sin tapar puertas ni vidrieras, y que los de esquina estén en la franja de mobiliario;
  - que los de "junto a negocios" estén solo en edificios `COMERCIAL`;
  - que los de parque estén junto a un banco, sin tocar senderos ni fuente (que cada banco tenga el suyo lo prueba `ParquePeatonalTest`);
  - todas las distancias mínimas y que sean determinísticos;
  - que las calles sigan transitables.
- Todo se prueba también con el 13 × 13 de referencia (segunda pasada de `mvn test`).

### Señalización vial

La señalización sigue criterios viales reales, con circulación por la derecha. Todo va sobre la vereda, dentro de la celda de una manzana: nada ocupa la calle ni cambia las colisiones del auto o del tráfico (`SenalizacionTest` lo comprueba).

**Definiciones (en `Mapa`).** Una **intersección** es una celda de calle con calle hacia el norte o el sur **y** hacia el este o el oeste: con este mapa son las 36 celdas de fila y columna pares (`Mapa.intersecciones()`). Un **acceso** es cada calle que llega a una intersección (`Mapa.accesos()`). `Mapa.sectorDeCelda()` indica a qué sector pertenece cada una.

**Semáforos: solo en el Centro.** Las 4 intersecciones del sector Centro, (4,4), (4,6), (6,4) y (6,6) (`INTERSECCIONES_SEMAFORO`), son las únicas del sector y es donde se cruzan las avenidas con más tránsito. En los barrios alcanza con prioridad de paso (PARE). Hay **un cabezal por acceso**: 4 por intersección, **16** en total.
- **A la derecha y mirando al auto:** cada cabezal está en la esquina de vereda a la **derecha** de la calle que llega, porque el conductor mira hacia adelante y a su derecha, y así no lo tapa el tránsito contrario. Está detrás del paso peatonal (`RETROCESO_SEMAFORO` = 0.5 + 3 = 3.5 desde el borde del cruce), donde el auto se detiene antes de pisar las franjas, y sus luces miran hacia los autos que se acercan por ese acceso.
- **Cómo se coordinan:** la secuencia es rojo → verde → amarillo con el reloj global del juego. El rojo dura lo mismo que verde + amarillo (7 = 5 + 2), y el grupo este-oeste usa el mismo ciclo desfasado 7 s. Por eso:
  - los accesos opuestos (norte y sur, o este y oeste) muestran siempre el mismo color;
  - mientras norte-sur está en rojo, este-oeste pasa por verde y amarillo, y al revés;
  - nunca hay verde o amarillo en los dos grupos a la vez.

  `SemaforoTest` recorre dos ciclos completos y lo verifica.
- **Modelo del cabezal (coherente con las farolas):** base cilíndrica ancha (`ANCHO_BASE` 0.34, `ALTO_BASE` 0.35) y poste cilíndrico gris oscuro (`ANCHO_POSTE` 0.14) hasta una caja angosta y alta casi negra (`ANCHO_CAJA` 0.42 × `ALTO_CAJA` 1.3 × `PROFUNDIDAD_CAJA` 0.34, desde `BASE_CAJA` = 2.5 de altura, por encima del techo de los autos). En la cara que mira al tráfico hay tres **lentes redondas** (cilindros cortos acostados con `uRotacion`): roja arriba, amarilla al medio y verde abajo. La lente de la fase es **emisiva** y tiene su color intenso (`COLORES_LENTE`); las otras dos usan el mismo color multiplicado por `BRILLO_APAGADA` (0.15), sin emisión, así se ve que hay tres y cuál es cuál. Sobre cada lente hay una **visera**: una placa fina inclinada `INCLINACION_VISERA` = 20° hacia abajo. La espalda de la caja queda lisa, como en un semáforo real.
- **Modelo sin OpenGL:** igual que `modeloFarola()`, `Semaforo.modelo(activa)` devuelve la lista de piezas en coordenadas locales del cabezal y `dibujar()` solo la recorre, girándola hacia el acceso. `SemaforoTest` comprueba que en cada fase hay exactamente una lente emisiva y es la del color de la fase, que las lentes están en orden (rojo arriba, verde abajo) dentro de la caja y en su frente, y que nada sobresale por detrás.

**Pasos peatonales: donde el peatón cruza.** Hay **52** en total:
- **En cada acceso de los cruces controlados (48):** los 4 con semáforo y los 8 con PARE (`Decoracion.crucesControlados()`), 4 accesos cada uno. Ahí el auto se detiene, así que el peatón cruza seguro.
- **Junto a cada parque (4 más):** en la calle vecina al norte y en la vecina al oeste, junto al cruce siguiente, porque los parques atraen peatones. Donde coincide con un paso de un cruce controlado no se repite.

Cada paso:
- **Posición:** está en la celda de calle vecina a la intersección, a `SEPARACION_CRUCE` = 0.5 del borde del cruce: la esquina queda libre para doblar, como en la vida real.
- **Tamaño:** mide `LARGO_PASO` = 3 en el sentido de circulación y cubre la calle de vereda a vereda: 6 franjas separadas 1.65 cubren 9.15 de los 10 de ancho.
- **Franjas:** son alargadas en el sentido de circulación, a 0.02–0.04 sobre el asfalto para evitar el z-fighting.
- **Orientación:** los hay en calles norte-sur y este-oeste.
- **Línea amarilla:** `Ciudad` no pinta las marcas que quedarían debajo del paso.

`UBICACIONES_PASOS` se genera desde los cruces controlados y la lista de parques, así que con 13 × 13 se recalcula solo. Las farolas no pisan los pasos: están cerca de mitad de cuadra y los pasos, junto a los cruces; donde la bombilla corrida colgaría sobre un paso, se usa el otro sentido (ver "Farolas: nunca frente a una puerta").

**PARE: en cruces sin semáforo, nunca junto a uno.** No se escriben a mano: `calcularUbicacionesPare()` elige **los cruces sin semáforo más cercanos al Centro**, hasta `MAX_PARE` = 10, con estos criterios:
- **Fuera del Centro:** allí hay semáforos.
- **Cruce interior:** los del borde son esquinas y "T" de la calle perimetral, que rodea la ciudad y conserva la prioridad.
- **No en el cruce de entrada del sector:** la entrada principal (la del cartel) es la avenida con prioridad.
- **Cercanía al Centro:** se ordenan por distancia al origen; es donde hay más tránsito cruzado.

Con el mapa 11 × 11 quedan **8** (`UBICACIONES_PARE`):

| Sector | Intersecciones | Autos que detiene |
|---|---|---|
| Barrio Norte | (2,2), (2,4), (2,8) | los que vienen del norte |
| Parque Sur | (8,2), (8,6), (8,8) | los que vienen del sur |
| Zona Oeste | (6,2) | los que vienen del oeste |
| Zona Este | (4,8) | los que vienen del este |

(2,6), (8,4), (4,2) y (6,8) no llevan PARE porque son los cruces de entrada del Barrio Norte, el Parque Sur, la Zona Oeste y la Zona Este.
- **Por qué esos accesos:** frenan a quien llega desde el borde de la ciudad por la calle secundaria (`direccionHaciaAfuera()` del sector).
- **Posición:** también en la vereda a la derecha del acceso, mirando al auto que llega, y detrás del paso peatonal (`RETROCESO_PARE` = 3.5, igual que el semáforo): el auto se detiene antes de las franjas.
- **Forma:** octógono rojo aproximado con cubos, borde blanco y franja blanca, sobre un poste.
- **Nunca junto a un semáforo:** dos señales que ordenan cosas distintas en el mismo cruce confunden al conductor. `SenalizacionTest` lo verifica.
- **Contramano:** se eliminaron las señales de contramano/dirección.

**Carteles de sector: uno por sector (5).** Están en la **entrada principal** de cada sector (`entradaDeSector()`), calculados con `cartelDeSector()`:
- **Sentido de la entrada:** hacia afuera (al Barrio Norte se entra yendo al norte, a la Zona Oeste yendo al oeste); al Centro se entra desde el sur.
- **Calle de la entrada:** el eje central si es una calle; si no, la avenida contigua al eje del lado derecho del conductor. Con 11 × 11: X = 10 hacia el norte (Centro y Barrio Norte), X = -10 hacia el sur (Parque Sur), Z = -10 hacia el oeste (Zona Oeste) y Z = 10 hacia el este (Zona Este).
- **Posición:** en la vereda derecha, sobre la **primera manzana del sector** que encuentra el conductor, `RETROCESO_CARTEL` = 0.75 después de la esquina y `SEPARACION_CARTEL` = 1.5 adentro del cordón, mirando a los autos que entran.

 Son verdes con borde blanco, estilo vial, y llevan el mismo nombre que el HUD y el minimapa. El texto se genera con STBEasyFont y cada trazo se dibuja como una caja fina sobre la placa.

### Escala: 1 unidad ≈ 1 metro

El auto mide 1.4 de alto, como uno real, así que todo se mide en metros. Como referencia, una persona mide 1.7 (no está modelada).

| Elemento | En el juego | Real | Constantes |
|---|---|---|---|
| Auto | 1.4 alto × 1.65 ancho × 2.6 largo | 1.45 × 1.8 × 4.3 (compacto: más corto) | `Cabina.PERFIL_CABINA`, `Auto` |
| Puerta | 2.0 × 1.0 | 2.0–2.1 × 0.9 | `Fachada.ALTO_PUERTA`, `ANCHO_PUERTA` |
| Planta baja | 3.2 | 3–4 | `Edificio.ALTO_MINIMO_BASE` |
| Piso | 3.0 | 2.8–3.0 | `Edificio.ALTO_PISO` |
| Ventana | 1.2–1.4 alto × 0.9–2.0 ancho | 1.2–1.5 alto | `TipoEdificio` |
| Vidriera | 1.4 alto (de 0.6 a 2.0) × 2.4 | 1.5–2 | `Fachada.ALTO_VIDRIERA` |
| Edificios | Casa 8.6 con techo; bloque 12–21; torre 28–36 | — | `Edificio.PISOS_*` |
| Banco | Asiento a 0.5, 1.6 de largo, respaldo hasta ≈ 1.0 | 0.45 / 1.5–1.8 | `Parque.dibujarBanco` |
| Árbol | Frondoso 5–7, pino 5–6; copa de 1.8–2.4 de ancho | 5–10 | `Parque.ALTURA_FRONDOSO_*`, `ALTURA_PINO_*` |
| Farola | Bombilla a 4.5, brazo de 1.5 | 4–5 (peatonal) | `Iluminacion.ALTURA_BOMBILLA` |
| Semáforo | Caja de 2.5 a 3.8, lentes de 0.3 | 2.4–3.5, lentes de 0.2–0.3 | `Semaforo.BASE_CAJA`, `ALTO_CAJA` |
| PARE | Octógono de 0.9 sobre poste de 2.6 | 0.75 a 2.1–2.5 | `Senalizacion.LADO_PARE`, `ALTURA_POSTE` |
| Cartel de sector | Placa de 2.6 × 0.8 con centro a 2.4 | Similar | `Senalizacion.ANCHO_CARTEL` |
| Calle | 10 de ancho, carriles de 5 | Carriles de 3–3.5 | `Mapa.TAM_CELDA` (ancha a propósito: colisiones y tráfico dependen de ella) |

`EdificioTest` verifica que el piso esté entre 2.8 y 3.2, que la puerta entre en la planta baja y que la torre más baja supere a cualquier otro tipo. `FachadaTest` verifica que las ventanas midan 1.2 a 1.5 y entren en su piso.

### Figuras generadas por código

Además del cubo, la ciudad usa tres mallas creadas con seno y coseno, sin imágenes ni modelos externos (`motor/Malla`). Todas son unitarias, como el cubo: 1 de ancho y 1 de alto, centradas en el origen, así que la escala que se pasa al dibujar es su tamaño real.

| Figura | Cómo se arma | Normal en cada vértice | Vértices |
|---|---|---|---|
| Esfera (12 × 8) | Como un globo terráqueo: 8 franjas de latitud × 12 meridianos, y cada punto es (sen θ cos φ, cos θ, sen θ sen φ) · 0.5 | La dirección del centro al punto: en una esfera es perpendicular a la superficie | 6 · 12 · 7 = 504 |
| Cilindro (10 lados) | Un polígono de 10 lados arriba y otro abajo, unidos por rectángulos, más dos tapas en abanico | En el costado, horizontal hacia afuera (cos φ, 0, sen φ); en las tapas, (0, ±1, 0). Los bordes se repiten con otra normal porque ahí hay una arista | 12 · 10 = 120 |
| Cono (10 lados) | Triángulos de la base a la punta, más la base en abanico | En el costado, (h cos φ, r, h sen φ) normalizada: el producto vectorial entre la recta que sube a la punta y el borde. En la punta se usa el ángulo medio de cada triángulo. En la base, (0, -1, 0) | 6 · 10 = 60 |
| Prisma triangular (`Figuras.prisma`) | El perfil `PERFIL_PRISMA` (un triángulo de base 1 y alto 1) extruido con `Malla.extruir` a 1 de ancho. Escalado, es el techo a dos aguas de las casas bajas | Una por cara, como la extrusión: dos faldones inclinados, la base y las dos tapas triangulares | 12 · 3 − 12 = 24 |
| Extrusión de perfil (`Malla.extruir(perfil, ancho)`) | Un contorno 2D visto de costado (puntos {z, y} en orden, convexo) se "estira" a lo ancho en X: dos tapas con la forma del perfil (en abanico) y un rectángulo por cada lado del contorno que las une | Una por cara (flat shading): el producto cruz de dos lados del triángulo, normalizado; si apunta hacia el centro de la figura se invierte, así todas miran hacia afuera. Las aristas quedan marcadas, como en una carrocería | 12 · n − 12 (36 para 4 puntos) |

Como cada vértice lleva la normal de la superficie curva y no la de su triángulo plano, OpenGL la interpola y la iluminación las muestra redondeadas. `ciudad.vert` corrige las normales con la inversa de la escala, así que siguen siendo correctas al estirar una figura, por ejemplo una copa achatada. `Juego` crea y libera las figuras igual que el cubo. Cada malla enlaza su propio VAO al dibujar, y `Cubo` también, para poder mezclarlas en la misma escena.

**Cabina de los autos (`vehiculo/Cabina`).** Reemplaza el bloque celeste. Es un trapecio visto de costado (`PERFIL_CABINA`) extruido a `ANCHO_CABINA` = 1.40, un poco menos que la carrocería (1.65):
- **Forma:** la base mide 1.40 y el techo 0.67. El parabrisas está a 45° y la luneta a unos 65°, más vertical. El techo queda en Y = 1.40, casi a la misma altura que el bloque anterior (1.395).
- **Color:** la cabina se pinta del color de la carrocería, así se ven el techo y los parantes.
- **Parabrisas y luneta:** cajas finas azul-gris oscuro, sin emisión, separadas 0.012 de la cara para evitar el z-fighting. Siguen la inclinación de su cara con `uRotacion`, cuyas columnas son el eje lateral, la dirección de la cara y su normal.
- **Ventanillas:** dos por lado. Cada una es un trapecio fino extruido que sigue la inclinación de los parantes, y un parante central del color del auto las separa.
- **Tráfico:** usa la misma cabina con su propio color.

`CabinaTest` verifica la forma, que nada sobresalga del ancho del cuerpo y que las ventanillas queden dentro de la cabina y a cada lado del parante.

### Ambiente: campo, cielo y sombras

Antes la ciudad flotaba sobre un fondo azul liso y los objetos parecían despegados del suelo. Ahora hay tres agregados, todos visuales: no cambian `Mapa.LIMITE`, las colisiones ni la escala del minimapa.

**Campo alrededor (`mundo/Entorno`).**
- **Pasto:** se extiende `ENTORNO_EXTRA` = 100 más allá de cada borde (hasta ±155, `BORDE_CAMPO`). Son 4 franjas (norte, sur, oeste y este) que rodean la base de asfalto sin superponerse con ella. Una sola caja grande debajo de la ciudad quedaría casi en el mismo plano que el asfalto y, de lejos, parpadearía (z-fighting).
- **Cordón:** una vereda gris de `ANCHO_CORDON` = 1.2, del alto de las aceras, justo afuera del borde. La calle perimetral termina contra él.
- **Árboles:** 36 (`CANTIDAD_ARBOLES`), los mismos pinos y frondosos de los parques (`Parque.dibujarArbol`). Van entre 6 y 50 unidades afuera del borde. Las posiciones se sortean con `Variacion` y una semilla fija (`SEMILLA_ARBOLES`) por el método de rechazo: se elige un punto del cuadrado exterior y se descarta si cae dentro de la ciudad. Siempre salen iguales.
- **Qué no cambia:** `Colisiones` sigue deteniendo al auto en el borde (`LIMITE - RADIO_AUTO`), así que nunca llega al cordón ni al pasto. En el minimapa no se dibuja.

**Cielo con degradado (`iluminacion/Cielo`).**
- **Cúpula:** es la esfera de `Figuras`, de radio `RADIO_CIELO` = 250 (dentro del plano lejano, `Camara.getPlanoLejano()` ≈ 319) y centrada en la cámara. Así el cielo nunca se acerca ni se aleja, como si estuviera infinitamente lejos. `iluminacion.frag` (rama `uCielo`) calcula el color con la dirección de la mirada: `mix(horizonte, cénit, altura^0.6)`.
  - **De día:** azul intenso arriba y celeste claro en el horizonte.
  - **De noche:** azul casi negro arriba y azul noche en el horizonte, con 160 estrellas (cajas chicas emisivas, en posiciones fijas sobre el horizonte) y una luna (esfera emisiva) hacia el norte-noreste.
- **Se dibuja primero y sin escribir profundidad** (`glDepthMask(false)`). El depth buffer guarda la distancia de lo más cercano en cada píxel. Si la cúpula la escribiera, lo que está más lejos que ella (el final del campo desde la cámara aérea) quedaría tapado por el cielo. Sin escribirla, el buffer sigue vacío y todo lo que se dibuja después queda delante, sea cual sea el radio.
- **Dónde se ve:** desde la cámara de seguimiento, en el tercio superior de la pantalla (mira solo 11° hacia abajo). También en la orbital (C) bajando la cámara con el mouse, y arriba de la aérea.

**Sombras falsas (`iluminacion/Sombras`).**
- **Qué son:** una mancha oscura semitransparente debajo de cada edificio, auto (jugador y tráfico), árbol (de los parques y del campo), banco, basurero y luminaria de parque. No calculan de dónde viene la luz: solo oscurecen el suelo bajo el objeto para que parezca apoyado.
- **Forma:** cada mancha es el cubo aplastado, del tamaño y con el giro de su objeto. El shader (rama `uSombra`) recibe la posición local del fragmento (`vLocal`, de -0.5 a 0.5) y calcula una distancia al centro d = (|x|ⁿ + |z|ⁿ)^(1/n), con n = 2 (elipse) o n = 4 (rectángulo de esquinas redondeadas, para los edificios). La opacidad baja con `smoothstep` desde el núcleo hasta el borde, así los bordes son difusos y nunca rectangulares. En los edificios el degradado empieza en la pared, que es lo que se ve sobre la acera.
- **Movimiento:** las de autos y tráfico siguen su posición y orientación en cada cuadro. Las demás se calculan una sola vez (`Sombras.FIJAS`).
- **Blending:** con `GL_BLEND` y `glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)` el color final es sombra · α + suelo · (1 − α): el suelo se oscurece sin taparse. α vale `ALFA_SOMBRA_DIA` = 0.45 en el centro, y de noche `ALFA_SOMBRA_NOCHE` = 0.22, más tenue.
- **Sin escribir profundidad:** la prueba de profundidad sigue activa, así que la pared o la rueda que está delante tapa la mancha. Pero la mancha no escribe su distancia: así dos sombras superpuestas no se cortan con un borde duro. Por eso se dibujan al final de la escena opaca, cuando el suelo ya está pintado.
- **Altura:** `ELEVACION_SOMBRA` = 0.06 sobre su superficie (asfalto, acera, césped o pasto), para evitar el z-fighting y quedar por encima de la pintura vial (0.04). Ninguna mancha fija sale de su acera o su césped. En el minimapa no se dibujan.

`EntornoTest` comprueba que `Mapa.LIMITE` sigue siendo medio lado de la matriz (55 con 11 × 11) y las colisiones no cambian: las calles perimetrales siguen transitables, el borde permitido es el de siempre y ni el cordón ni los árboles son alcanzables. También que los árboles están afuera, dentro del campo y son siempre los mismos. `AmbienteTest` comprueba que la cúpula, las estrellas y la luna entran en el plano lejano, que las estrellas están sobre el horizonte y que hay una sombra fija por edificio, árbol, banco, luminaria y basurero, sin salir de su superficie, más tenue de noche.

### Colisiones y frenado del tráfico

**Colisiones del jugador.** El auto es un círculo de radio `RADIO_AUTO` = 1.65 que contiene todas sus piezas. Se prueba contra cada manzana con **círculo contra rectángulo**: se busca el punto del rectángulo más cercano al centro y se compara la distancia al cuadrado con el radio al cuadrado. Contra los bordes, el centro no puede pasar de `LIMITE - RADIO_AUTO`. Contra el tráfico, **círculo contra círculo**: dos círculos se tocan si la distancia entre centros es menor que la suma de los radios; alejarse de un vehículo siempre está permitido, así el jugador nunca queda atrapado. `Juego` junta las tres pruebas en una sola pregunta, "¿el auto cabe en (x, z)?" (`Colisiones.PosicionLibre`), y se la pasa a `Auto.actualizar`.

**Deslizamiento por ejes.** Antes, si el paso del cuadro chocaba, se descartaba entero y la velocidad quedaba en 0. Rozando una pared en diagonal, el auto quedaba pegado y, como el giro es proporcional a la velocidad, casi no podía doblar para salir. Ahora, si el paso completo (dx, dz) choca, se prueba cada eje por separado, primero el que más se mueve. Como las paredes de la ciudad son paralelas a X o a Z, en un roce solo uno de los dos componentes atraviesa la pared: se anula ese y se conserva el otro. Solo si ningún eje queda libre (pared de frente o esquina) el auto se detiene. **Reparto de la velocidad:** la velocidad es un número a lo largo del frente (−sen a, −cos a). Al deslizar, el desplazamiento del cuadro usa solo la componente del eje libre (`|frenteX|` o `|frenteZ|` de la velocidad). Además, la velocidad pierde por **roce** una desaceleración `FRICCION_ROCE` = 30 u/s² escalada por la fracción bloqueada (1 − |frente del eje libre|). Rozando casi en paralelo casi no frena; cuanto más de frente, más frena. Con la pared de frente (fracción libre menor que `FRACCION_MINIMA_DESLIZAMIENTO` = 0.1, unos 84°) el auto se detiene. Las ruedas giran lo que el auto avanzó de verdad.

**Independiente de los FPS.** El roce resta desaceleración · dt en vez de multiplicar la velocidad por un factor en cada cuadro. Un factor por cuadro depende de la frecuencia de dibujo: 0.9 aplicado 30 veces en un segundo deja 0.04, y aplicado 144 veces deja 0.0000003. En cambio, en un segundo la suma de los dt es 1 con cualquier FPS, así que siempre se pierde lo mismo. La resistencia normal (e^(−k·dt)) también es independiente, porque repetida durante un segundo da e^(−k). `AutoTest` lo comprueba: el mismo segundo rozando el borde oeste a 30, 60 y 144 FPS termina con la misma velocidad y posición (con el factor por cuadro, a 30 FPS quedaba en 5.9 u/s y a 60 FPS en 3.6). También comprueba que, pegado a una manzana y yendo en diagonal contra ella, el auto sigue hacia el norte sin invadirla, y que de frente se detiene.

**Regla del corredor del tráfico.** Antes un vehículo se detenía en seco si el jugador estaba en el semiplano de adelante y a menos de 6 unidades, sin mirar el carril. Por eso frenaba aunque el jugador estuviera al costado o en el carril contrario (a 5 unidades de lado). Ahora, en `Vehiculo.huecoHasta`, el vector hacia el obstáculo se descompone en coordenadas del vehículo: **longitudinal** = hacia · frente y **lateral** = hacia · derecha, con derecha = (cos a, −sen a). El obstáculo cuenta solo si cumple las tres condiciones:

- está **adelante** (longitudinal > 0);
- está en el **mismo carril**, `|lateral| < MEDIO_CORREDOR` = 2.2 (medio auto + medio obstáculo + margen);
- el **hueco** (longitudinal menos los dos radios) es menor que `DISTANCIA_FRENADO` = 8.

**Frenado gradual.** La velocidad que deja el obstáculo es crucero × `factorPorHueco(hueco)`. Ese factor vale 1 con el camino libre, 0 con el hueco en `DISTANCIA_DETENCION` = 1 o menos, y cambia en línea recta entre esos dos valores. La velocidad se acerca a ese valor sin bajar más de `DESACELERACION` = 8 u/s² ni subir más de `ACELERACION_TRAFICO` = 4 u/s². La reducción por curva sigue siendo inmediata: si el vehículo entrara rápido a la curva, abriría el giro. Si alguien se cruza demasiado cerca para frenar a tiempo, el vehículo avanza solo hasta tocarlo y se detiene: nunca lo atraviesa.

**Tráfico entre sí.** Los vehículos aplican la misma regla entre ellos, y además miran la **trayectoria** del otro: dónde estará en los próximos `PREVISION` = 1.5 s si sigue derecho (`huecoHastaTrayectoria`, 7 puntos). Sin esto, en un cruce dos vehículos que llegan en perpendicular recién entran uno en el corredor del otro cuando ya es tarde para frenar. Para que dos vehículos no se esperen mutuamente para siempre, en cada par cede uno solo (`Trafico.cede`): si solo uno ve al otro, cede ese; si uno tiene al otro físicamente adelante, cede el de atrás; en un cruce cede el de índice mayor, una prioridad fija que no cambia de un cuadro a otro.

**Luces de freno del tráfico.** Se encienden mientras la velocidad baja (más de `UMBRAL_LUZ_FRENO` = 0.5 u/s²) y mientras el vehículo espera detenido ante un obstáculo. Usan `LucesVehiculo.colorTrasera(luces, frenando)`, igual que el jugador: el freno (rojo intenso, emisivo) domina sobre la luz de posición, de día y de noche.

**Ruedas del tráfico.** Antes eran cubos negros; ahora son las mismas ruedas redondas del jugador (`vehiculo/Rueda`): neumático, llanta, dos rayos y la marca roja que da vueltas, en la misma posición de la carrocería (±`LADO_RUEDA` = 0.88 de lado a lado, ±`EJE_RUEDA` = 0.82 a lo largo) y con el centro a `RADIO_RUEDA` = 0.32 del suelo, así que se apoyan en el asfalto.
- **Rotación:** `anguloRueda += paso / RADIO_RUEDA`, donde `paso` es lo que el vehículo avanzó de verdad en el cuadro (ya recortado si un obstáculo lo frenó). Al frenar giran más lento; detenido, no giran; en pausa, tampoco.
- **Dirección (modelo de bicicleta):** un auto con distancia entre ejes L (`DISTANCIA_EJES` = 1.64) y las delanteras dobladas δ recorre un círculo de radio R = L / tan δ. Como velocidad = velocidad angular · R, las delanteras deben doblar δ = atan(L · ω / v), con ω el giro que el vehículo hizo en ese cuadro (`Vehiculo.direccionPorGiro`). El resultado se limita a `ANGULO_MAX_DIRECCION` = 30° y las ruedas se acercan a él a `VELOCIDAD_DIRECCION` = 3 rad/s, igual que las del jugador (`Rueda.acercarDireccion`). En recta ω = 0, así que vuelven a 0.
- **Sombras:** no cambian: la mancha de cada vehículo sigue su posición y orientación como antes.

## 3. Controles

| Tecla | Acción |
|---|---|
| W / ↑ | Acelerar |
| S / ↓ | Frenar y, detenido, retroceder |
| A / ← y D / → | Girar (solo con el auto en movimiento; en reversa el giro se invierte) |
| Espacio | Freno fuerte |
| C | Cambiar de cámara: seguimiento → orbital del auto → aérea de toda la ciudad → seguimiento |
| Mouse, botón izquierdo | En la cámara orbital: arrastrar para girar alrededor del auto (horizontal) y subir o bajar la cámara (vertical). En la aérea: lo mismo, pero alrededor del punto de la ciudad que se está mirando |
| Ruedita del mouse | En la orbital y en la aérea: acercar y alejar (en la aérea, alejada al máximo se ve toda la ciudad) |
| Mouse, botón derecho | En la aérea: arrastrar para desplazar el punto que se mira, como si se arrastrara el suelo con la mano; nunca sale de la ciudad |

En la cámara de seguimiento el mouse no hace nada. Cada vez que se entra a la aérea, arranca con la vista general de siempre (la de las capturas).
| N | Alternar día / noche |
| F | Encender / apagar los faros del auto: los conos de luz sobre la calle, las bombillas delanteras (blancas y emisivas) y las luces de posición traseras (rojo tenue) |
| M | Mostrar / ocultar el minimapa |
| R | Reiniciar: auto en la salida, entregas en 0/3, cronómetro en 0, tráfico al inicio de sus rutas y cámara de seguimiento detrás del auto |
| ENTER | Empezar la partida desde el menú de inicio |
| P | Pausa / continuar: congela auto, tráfico, entregas y semáforos |
| H | Mostrar / ocultar la ayuda de controles dentro de la ventana |
| ESC | Salir |

**Objetivo:** llegar a la marca dorada del destino activo y frenar sobre ella, a menos de 3 unidades y con velocidad menor que 1 unidad/s. Al completar las 3 entregas, el título muestra el tiempo total.

**Título de la ventana:** `Ciudad Interactiva | Vel: X km/h | Luces: ON/OFF | Día/Noche | Entregas: X/3 | Destino: …`. Además, el HUD dentro de la ventana muestra velocidad, faros, día/noche, entregas, destino y sector.

## 4. Funciones implementadas por clase

Paquete `com.graphics.ciudad`:

| Clase | Funciones |
|---|---|
| `Main` | Punto de entrada: crea `Juego` y llama a `ejecutar()`. |
| `Juego` | Ciclo principal entrada → `actualizar(dt)` → dibujar, con dt limitado a 50 ms y llevado a 0 en menú o pausa. Reparte las teclas, compone el título, lleva el reloj global de la animación, arma la prueba de colisión del jugador (manzanas, bordes y tráfico) para el deslizamiento por ejes, fija el orden de dibujo (cielo, ciudad, campo, auto, tráfico, farolas, decoración, destino, sombras, minimapa, HUD) y libera los recursos. |
| `motor/Ventana` | Ventana GLFW y contexto OpenGL 3.3 Core, callback de teclado, `pulsada()`, tamaño real del framebuffer, título, presentación y cierre, y callbacks de mouse (botones izquierdo y derecho, cursor y ruedita) que entregan a la cámara los arrastres de cada botón y el zoom. |
| `motor/Shader` | Carga GLSL desde `resources/shaders`, normaliza `#version` para Windows, compila, enlaza y envía uniforms (`vector2`, `vector`, `vector4`, `decimal`, `entero`, `matriz3`) con caché de ubicaciones. |
| `motor/Cubo` | Cubo de 36 vértices con normales en un VAO/VBO; `caja()` y `cajaGirada()` lo dibujan con posición, escala, giro y color. |
| `motor/Camara` | Tres modos con C: seguimiento (8 unidades detrás del auto, con su ángulo suavizado por exp(−K_GIRO · dt), mirando 11° hacia abajo a un punto 4 unidades por delante de él; se acerca si ese punto cae sobre una manzana o fuera de la ciudad, trazando el rayo desde el ángulo suavizado en pasos de 0.25 y afinando el primer punto tapado con una búsqueda binaria (distancia continua: sin temblor al retroceder hacia un obstáculo), y vuelve de a poco: `distanciaLibre()`, `actualizarSeguimiento()`; con R y al volver con C se coloca detrás del auto sin barrido: `reiniciarSeguimiento()`), orbital del auto y vista aérea de la ciudad. Las dos últimas se manejan con el mouse y usan cada una su `Orbita`: la del auto, centrada en el auto y relativa a su ángulo; la aérea, centrada en un punto de la ciudad que el botón derecho desplaza (`desplazar()`, limitado a ±`Mapa.LIMITE`). Al entrar a la aérea vuelve a la vista inicial, proporcional a `Mapa.LIMITE`. `getOjo()` devuelve la última posición de la cámara, donde `Cielo` centra su cúpula. `getPlanoLejano()` calcula la distancia máxima visible desde `Mapa.LIMITE` y la envía a `ciudad.vert` (`uPlanoLejano`). |
| `motor/Orbita` | Coordenadas esféricas compartidas por las dos cámaras con mouse: ángulo θ, elevación φ y distancia D alrededor de un centro, con sus límites. `girar()`, `acercar()`, `colocar()` y `ojo()`, que convierte (θ, φ, D) en la posición (D · cos φ · sen θ, D · sen φ, D · cos φ · cos θ) + centro. |
| `motor/Texto` | Convierte texto en rectángulos con STBEasyFont; lo usan el HUD (2D) y los carteles de sector (3D). |
| `motor/Malla` | Figura genérica en un VAO/VBO con el mismo formato que `Cubo` (posición + normal). Generadores por fórmulas: `esfera(sectores, anillos)` (esfera UV), `cilindro(lados)` con tapas y `cono(lados)` con base, todos unitarios. `dibujar()` / `dibujarGirada()` funcionan como `caja()` / `cajaGirada()`.. `extruir(perfil, ancho)` genera una figura extruida desde un perfil 2D con normales por cara (producto cruz). |
| `motor/Figuras` | Crea la esfera (12 × 8), el cilindro (10 lados), el cono (10 lados) y el prisma triangular del techo a dos aguas, los sube a la GPU y los libera, igual que `Cubo`. |
| `mundo/Mapa` | `MAPA` 11 × 11 (o el que indique `-Dciudad.mapa`, `PROPIEDAD_MAPA` / `elegirMapa()`: lo usan las pruebas con el 13 × 13 de referencia), intersecciones (`esInterseccion`, `intersecciones`), accesos (`accesos`), parques (`parques`), `sectorDeCelda`, `TAM_CELDA`, `TAMANO`, `LIMITE`, `centro()`, `indiceCelda()`, `esCalle()`, `esCalleEn()`, y los cinco sectores con nombre (`SECTORES`, `NOMBRES_SECTORES`, `sector()`, `nombreSector()`). |
| `mundo/Ciudad` | Base de asfalto, líneas amarillas entre cruces, aceras, edificios (delegados en `Edificio`) y césped de los parques. |
| `mundo/Edificio` | Elige el tipo y los colores de cada edificio con `Variacion` (`tipo()`, `colorPared()`, `colorTecho()`), arma sus volúmenes (`volumenes()`) y las piezas del techo (`piezas()`: losas, baranda, ascensor, antena, tanque, techo a dos aguas y chimenea), y las dibuja. Tiene la paleta urbana y las alturas por tipo. |
| `mundo/TipoEdificio` | Enum con los cinco tipos (`TORRE`, `BLOQUE`, `ESCALONADO`, `CASA_BAJA`, `DOBLE`) y el patrón de ventanas de cada uno. |
| `mundo/Decoracion` | Fachadas de los edificios (delegadas en `Fachada`) y los pasos peatonales de `UBICACIONES_PASOS` (un paso por acceso de cada cruce con semáforo o PARE, a `SEPARACION_CRUCE` del cruce, y hasta dos junto a cada parque, en ambas orientaciones); `hayPasoSobre()` permite que `Ciudad` corte la línea amarilla. Delega los parques en `Parque`, la señalización en `Senalizacion` y los cestos de basura en `Basureros`. |
| `mundo/UsoPlantaBaja` | Enum con los usos de planta baja (`COMERCIAL`, `LOBBY_OFICINAS`, `RESIDENCIAL`, `CASA`) y la regla `de(fila, columna)`: torre → lobby, casa baja → casa, los demás comercio en el Centro y sobre las avenidas principales (`esAvenidaPrincipal`, `daAAvenidaPrincipal`) y departamentos en los barrios. |
| `mundo/Fachada` | Planta baja según el uso en las caras a la calle. Comercial: vidriera, puerta centrada y vidriera, con un toldo escalonado de color (a veces a rayas) sobre cada vidriera; las vidrieras se iluminan de noche. Lobby: vidrio de piso a techo (emisivo suave de noche), puerta doble y marquesina. Departamentos y casas: puerta con escalón, ventanas de planta baja (piso 0, se encienden de noche), alero en los departamentos y portón de garaje en algunas casas (`garaje()`). Ventanas en cada volumen según el patrón del tipo (`ventanas()`), sin tapar el negocio ni quedar dentro de otro volumen: vidrio claro de día; de noche, ≈65 % encendidas con tonos variados, decidido por ventana con un hash. |
| `mundo/Variacion` | Hash determinístico `valor(fila, columna, índice, semilla)` que usan `Parque`, `Fachada` y `Edificio` para variar sin azar por cuadro. |
| `mundo/Parque` | Senderos en cruz, plaza octogonal alrededor de la fuente central, franjas de acceso donde llegan los pasos (`franjas()`), 4 a 6 árboles (frondosos de esferas y pinos de conos), 2 a 4 bancos mirando a la fuente desde el borde de la plaza y 1 o 2 luminarias globo en el borde de los senderos. `tocaPavimento()`, `tocaPlaza()`, `tocaFranja()` y `enPavimento()` dicen qué está sobre el camino; `ladoBasureros()` y `lugarBasurero()` ubican el cesto de cada banco. `arboles()`, `bancos()` y `luminarias()` calculan la disposición de cada parque con `variacion()` (determinística); `LUMINARIAS` guarda el centro de cada globo, que `Iluminacion` envía como luz puntual. `cercaDeUnPoste()` lo comparten árboles, luminarias y basureros. `dibujarArbol()` es estático: `Entorno` lo reutiliza para los árboles del campo. |
| `mundo/Basureros` | Cestos cilíndricos con tapa. `UBICACIONES` se calcula una vez con tres reglas (junto a los bancos, a la salida de los pasos sobre la franja de mobiliario y junto a algunos negocios) y distancias mínimas a señales, farolas, pasos, negocios, árboles, luminarias y otros basureros. Lo dibuja `Decoracion`. |
| `mundo/Entorno` | Campo que rodea la ciudad: 4 franjas de pasto hasta `ENTORNO_EXTRA` más allá del borde, cordón de vereda y `ARBOLES` dispersos (posiciones determinísticas con `Variacion`). Solo decoración: no cambia `Mapa.LIMITE`, colisiones ni minimapa. |
| `mundo/Senalizacion` | Ubica y dibuja la señalización vial: `INTERSECCIONES_SEMAFORO` (las del Centro), un cabezal por acceso (`SEMAFOROS`), `UBICACIONES_PARE` y `CARTELES_SECTOR`, calculados con reglas (`direccionHaciaAfuera()`, `entradaDeSector()`, `cruceDeEntrada()`, `cartelDeSector()`, `calcularUbicacionesPare()`). `esquinaDerecha()` calcula la esquina de vereda a la derecha de un acceso y la orientación hacia el auto. |
| `mundo/Semaforo` | Ciclo rojo (7 s) → verde (5 s) → amarillo (2 s) en bucle; `luzParaAcceso()` desfasa el grupo este-oeste 7 s para que sea complementario del norte-sur. Arma el cabezal como lista de piezas (`modelo()`: base y poste cilíndricos, caja angosta, tres lentes redondas con visera) y lo dibuja orientado hacia su acceso; solo la lente activa es emisiva. |
| `vehiculo/Auto` | Estado (x, z, ángulo, velocidad), física por dt (aceleración 9, resistencia exponencial, freno, límites -6..16), giro proporcional a la velocidad y `reset()`. Ruedas redondas (`vehiculo/Rueda`) que giran según la distancia recorrida (`anguloRueda += avance / RADIO_RUEDA`), delanteras que doblan hasta 30° con A/D y vuelven solas al centro, luces de freno y de reversa (`frenando()`, `enReversa()`: S frena si el auto iba hacia adelante y, si no, enciende la reversa, también detenido contra una pared). Al chocar desliza por el eje libre (prueba X y Z por separado) y pierde velocidad por roce proporcional a dt. Las bombillas siguen a la tecla F, leída de `Iluminacion` al dibujar. |
| `vehiculo/Rueda` | Ruedas compartidas por el jugador y el tráfico, sin estado: medidas, colores y ubicación en la carrocería (`LADO_RUEDA`, `EJE_RUEDA`), `giroPorDistancia(d)` = d / `RADIO_RUEDA`, `acercarDireccion()` (paso limitado a `VELOCIDAD_DIRECCION` y tope `ANGULO_MAX_DIRECCION`), `rotacion()` (matriz de `uRotacion`) y el dibujo: `dibujarCuatro()` y `dibujar()` (neumático, llanta, rayos y marca roja). Cada vehículo guarda su `anguloRueda` y su `anguloDireccion`. |
| `vehiculo/LucesVehiculo` | Ubicación, tamaño y colores de faros y luces traseras, compartidos por el jugador y el tráfico. `colorFaro(encendido)` y `colorTrasera(lucesEncendidas, frenando)` devuelven el color y si es emisivo; el freno tiene prioridad sobre la luz de posición. |
| `vehiculo/Cabina` | Cabina de los autos: trapecio extruido (`PERFIL_CABINA`, `ANCHO_CABINA`) del color de la carrocería, con parabrisas y luneta inclinados, y dos ventanillas por lado separadas por un parante central. La comparten el jugador y el tráfico. |
| `vehiculo/Colisiones` | Círculo del auto (radio 1.65) contra el rectángulo de cada manzana y contra los bordes del mapa; círculo contra círculo para el tráfico. `PosicionLibre`: la pregunta "¿el auto cabe en (x, z)?" que `Auto` usa para deslizar. |
| `vehiculo/IndicadorJugador` | Flecha cian emisiva que apunta al auto desde la vista aérea; sube y baja y gira con el tiempo. |
| `trafico/Vehiculo` | Auto autónomo: posición, ángulo, velocidad y color; calcula el carril derecho de su ruta (`calcularCarriles`) y lo sigue girando suavemente hacia el próximo waypoint, frena en las curvas y ante un obstáculo adelante en su mismo carril (regla del corredor, `huecoHasta`), de forma gradual; mira la trayectoria de los otros vehículos (`huecoHastaTrayectoria`); dibujo por piezas con luces traseras que brillan de noche y luces de freno al desacelerar; ruedas redondas (`Rueda`) que giran lo que avanzó de verdad y delanteras que doblan según el giro actual (`direccionPorGiro`, modelo de bicicleta). |
| `trafico/Trafico` | Cuatro rutas en celdas de calle (`RUTAS_CELDAS`), validadas contra el Mapa al arrancar; crea, actualiza, reinicia y dibuja los vehículos; calcula el hueco de cada uno ante el jugador y los demás, y decide quién cede en un cruce (`cede`); indica a `Juego` si un movimiento del jugador lo haría atravesar uno. De noche envía al shader los focos de sus faros (`prepararFaros`). |
| `iluminacion/Cielo` | Cúpula centrada en la cámara con degradado cénit → horizonte (día y noche), `ESTRELLAS` determinísticas y luna emisiva. Se dibuja primero y sin escribir profundidad. |
| `iluminacion/Sombras` | Sombras falsas: manchas con degradado circular bajo edificios, autos, árboles, bancos, basureros y luminarias de parque (`FIJAS` y las de los autos, que siguen posición y giro). Activa la mezcla, no escribe profundidad y restaura el estado al terminar. |
| `iluminacion/Iluminacion` | Día/noche, faros, 13 farolas en la vereda de los edificios, nunca en parques (`LUCES` = manzana + lado, elegidas por `calcularLuces()` con `farolaValida()`; `POSTES` y `BOMBILLAS` se calculan desde ahí), envío de uniforms de luz y modelo de cada farola (`modeloFarola`: base, poste, brazo curvo, pantalla y bombilla con cilindros, esferas y un cono), dibujado por `dibujarFarolas()`. Envía además las luminarias globo de los parques (`Parque.LUMINARIAS`) como luces puntuales en `uGlobos` (`MAX_GLOBOS` = 16). |
| `juego/Entregas` | `DESTINOS` con nombres, regla de llegada, progreso, cronómetro, `reset()`, texto del título, marca dorada con baliza giratoria y cuadrado dorado en el minimapa. |
| `juego/Minimapa` | Segundo pase de dibujo ortográfico con el norte arriba y la ciudad completa (escala `Mapa.LIMITE`). Usa scissor y viewport y restaura el estado al terminar. Muestra un indicador cian con punta blanca para la posición y la orientación del auto, el tráfico y las divisiones de los sectores; `aPantalla()` convierte coordenadas del mundo a píxeles para escribir sus nombres. |
| `juego/EstadoPartida` | Estados MENU, JUGANDO y PAUSA; `dtEfectivo()` devuelve 0 fuera de JUGANDO, así todo queda congelado. |
| `interfaz/Dibujo2D` | Pase ortográfico 2D en píxeles con shader y VAO/VBO propios: rectángulos semitransparentes y texto de STBEasyFont. Desactiva la profundidad y activa la mezcla, y lo restaura al terminar. |
| `interfaz/Hud` | Panel de estado (velocidad, faros, día/noche, entregas, destino, sector), ayuda de controles (H), cartel de PAUSA, menú de inicio y nombres de sectores sobre el minimapa. Escala el texto según el tamaño de la ventana. |

Shaders en `src/main/resources/shaders`:

- `ciudad.vert`: escala, rotación y traslación del cubo; proyección en perspectiva o vista superior para el minimapa. La matriz `uRotacion` (identidad para todo, salvo las ruedas) agrega una rotación en cualquier eje antes del giro en Y. `vLocal` pasa la posición dentro de la figura unitaria, para el degradado de las sombras.
- `iluminacion.frag`: luz ambiente, sol (Lambert), farolas como focos hacia abajo con atenuación y conos de los faros (`smoothstep`). La función `factorCono()` calcula el borde suave de un cono y la usan las farolas y `aporteFoco()`, que a su vez usan tanto los faros del jugador como los focos del tráfico (`uFarosTrafico`, `uDireccionFarosTrafico`, `uNumFarosTrafico`). Dos ramas especiales: `uCielo` (degradado del cielo según la dirección de la mirada) y `uSombra` (mancha con alfa que baja con `smoothstep` hacia el borde).
- `plano.frag`: shader de color plano de la primera lección, conservado como referencia.
- `hud.vert` y `hud.frag`: dibujo 2D en píxeles con color RGBA, para el HUD.

Pruebas en `src/test/java/com/graphics/ciudad`:

- `MapaTest`: límites 110/55, al menos 12 edificios y 4 parques, calles conectadas (BFS) y posiciones sobre calles. Además: regla de la salida (sin parque en {penúltima fila, 1}, con el cálculo del paso que la taparía); `MAPA_13` es válido (13 × 13, calles en pares, ≥ 12 edificios y ≥ 4 parques); el bloque de ENTREGA.md coincide con `MAPA_13`; y `-Dciudad.mapa` elige ese mapa.
- `ColisionesTest`: calles transitables y obstáculos (círculo contra rectángulo y bordes).
- `AutoTest`: `reset()` del auto, que lo deja en el carril derecho (a la derecha del centro de su calle, sin tocar la línea amarilla ni la vereda); recorrer 2π · `RADIO_RUEDA` gira la rueda 2π (y negativo en reversa); las ruedas delanteras nunca pasan `ANGULO_MAX_DIRECCION` y vuelven a 0 al soltar; luces de freno (S hacia adelante o Espacio) y de reversa según velocidad y teclas, sin parpadear al empujar marcha atrás contra el borde; deslizamiento en diagonal contra una manzana (avanza por el eje libre sin invadirla), detención de frente y roce independiente de los FPS (1 s a 30, 60 y 144 FPS da la misma velocidad y posición).
- `CamaraTest`: C recorre los tres modos; la elevación y la distancia de la cámara orbital nunca salen de sus límites; en la aérea, la elevación, la distancia y el centro nunca salen de los suyos (y llegan justo al tope); la aérea arranca con la vista de siempre cada vez que se entra; en seguimiento el mouse no hace nada y en la orbital el botón derecho tampoco, ni se toca la aérea; el plano lejano (`getPlanoLejano()`, enviado como `uPlanoLejano`) alcanza para la aérea alejada al máximo con el centro en una esquina; la cámara de seguimiento queda dentro del límite en la salida, a 45° en un cruce el ojo y la línea hasta el auto no pasan sobre la manzana, en una calle recta la distancia es la normal (8) sin atraso, y después de un recorte vuelve a 8 sin saltos; el suavizado del ángulo da el mismo resultado a 30, 60 y 144 FPS y gira por el camino corto; la inclinación se mantiene en `INCLINACION` aunque el recorte cambie la distancia; tras R y al volver con C la cámara queda detrás del auto sin barrido; retrocediendo hacia el borde sur, mientras está recortada el ojo queda quieto (|ΔojoZ| < 0.01) y la distancia nunca sube; doblando a la izquierda en un cruce la distancia sigue la exacta hasta la manzana, sin escalones de 0.25.
- `OrbitaTest`: la posición sale de las coordenadas esféricas (distancia al centro = D, altura = D · sen φ, ángulo horizontal = base + θ) y φ y D nunca salen de sus límites.
- `EntregasTest`: 3 entregas, `reset()`, progreso en 0 y primer destino otra vez activo.
- `MallaTest`: esfera, cilindro y cono tienen la cantidad de vértices esperada (504, 120 y 60), normales de largo 1 y dentro del cubo unitario; la esfera tiene normales radiales, el cilindro horizontales en el costado y verticales en las tapas, y el cono tiene la inclinación correcta; la extrusión de un perfil de 4 puntos tiene 36 vértices, normales unitarias hacia afuera (con el contorno en cualquier sentido de giro), iguales en los tres vértices de cada triángulo, y tapas mirando a ±X.
- `CabinaTest`: base más larga que el techo, parabrisas más inclinado que la luneta, misma altura aproximada que el bloque anterior, nada fuera del ancho del cuerpo, y ventanillas dentro de la cabina y a cada lado del parante central.
- `ParqueTest`: 4 a 6 árboles por parque, con el centro libre, copa ≤ 1/4 del parque y dentro de la celda; 2 a 4 bancos mirando a la fuente; parques distintos entre sí, con pinos y frondosos; las calles siguen transitables.
- `ParquePeatonalTest` (11 × 11 y 13 × 13):
  - cada paso que llega a un parque desemboca en pavimento: toda su boca está pavimentada y hay pavimento continuo hasta el brazo;
  - la plaza conecta los 4 brazos: el anillo entre la fuente y la plaza está pavimentado en todos los ángulos, mide al menos `PASO_FUENTE` y cada brazo sigue hasta el borde;
  - cada banco tiene su basurero;
  - los bancos miran la fuente desde el borde de la plaza;
  - nada pisa el pavimento: troncos, copas de pino, bancos y basureros fuera de senderos, plaza y franjas; luminarias fuera de la plaza y las franjas.
- `EdificioTest`: la misma celda da siempre el mismo tipo, colores y piezas; hay al menos 4 tipos y 4 colores de pared, y no todos los techos son gris oscuro; ninguna pieza sale de la huella de su manzana (`MEDIA_HUELLA`) ni se hunde en la acera; cada tipo respeta su forma (torre angosta y alta, escalonado que se achica, casa con techo de teja a dos aguas, doble con dos alturas).
- `UsoPlantaBajaTest`: cada torre es lobby y cada casa baja es casa; los demás, comercio en el Centro y sobre las avenidas principales y departamentos en el resto; al menos 3 usos distintos y 40 % de comercio; la misma celda da siempre el mismo uso; las avenidas principales son las calles junto al eje.
- `FachadaTest`: cada ventana tiene siempre el mismo estado; cada ventana queda dentro de su volumen, nunca sobre la vidriera o el hall ni tapada por otro volumen, y las de planta baja de departamentos y casas no tapan la puerta, se encienden de noche como las demás y faltan solo donde está el garaje (que solo tienen algunas casas); el patrón de ventanas cambia con el tipo; de noche, la fracción encendida está dentro de ±10 % de `PORCENTAJE_VENTANAS_ENCENDIDAS` y aparecen todos los tonos; de día ninguna ventana es emisiva; vidrieras y toldos entran en la cara con `MARGEN_ESQUINA` y no tapan la puerta; ninguna puerta está a menos de `MARGEN_ESQUINA` de una esquina y las puertas de caras vecinas nunca comparten esquina; el toldo no llega a la calzada y se usan todos los colores, lisos y a rayas.
- `SemaforoTest`: orden y duraciones del ciclo; en dos ciclos completos, los accesos opuestos siempre muestran el mismo color y norte-sur y este-oeste nunca están a la vez en verde o amarillo; en cada fase, exactamente una lente emisiva y del color de la fase; lentes ordenadas en el frente de la caja y espalda lisa.
- `SenalizacionTest`: semáforos en todas las intersecciones del Centro y solo ahí, uno por acceso, a la derecha y mirando al auto; 6 a 10 PARE en cruces sin semáforo, a la derecha y mirando al auto; un cartel por sector dentro de su sector; ningún semáforo, PARE ni cartel sobre la calle, y las calles siguen transitables. Reglas: cada PARE está en un cruce interior, fuera del Centro, que no es de entrada, detiene a quien viene del borde y ningún cruce descartado está más cerca del Centro; cada cartel está a la derecha de la entrada de su sector, sobre la primera manzana del sector y mirando al auto.
- `PasosPeatonalesTest`: cada paso está sobre calle, en la celda vecina a una intersección y a `SEPARACION_CRUCE` de su borde; todos los accesos de los cruces con semáforo o PARE tienen paso, y el semáforo y el PARE quedan detrás de él; con `LARGO_PASO` en el sentido de circulación y de vereda a vereda; hay pasos en ambas orientaciones; cada paso está junto a un cruce con semáforo o a un parque; cada acceso con semáforo y cada parque tienen su paso; ninguna marca amarilla queda debajo.
- `MapaTest.testSectores`: entre 4 y 5 sectores con nombre y ningún punto de la ciudad sin sector.
- `TraficoTest`: 120 s simulados con dt fijo; todos los vehículos siempre en calles, dentro del mapa, sin tocar manzanas, en movimiento y, en los tramos rectos, a `DESPLAZAMIENTO_CARRIL` ± 0.35 a la derecha de la línea central. En esa misma simulación, los vehículos nunca se solapan entre sí y todos recorren al menos el 40 % de crucero × 120 s (ninguno queda trabado en una espera mutua). Además comprueba que dos rutas comparten una calle en sentidos opuestos y la geometría de las esquinas de carril. Regla del corredor: con el jugador en el carril contrario no frena; en el mismo carril a 10 reduce de a poco (nunca más de `DESACELERACION` · dt por cuadro), con el freno encendido y sin tocarlo; pegado adelante se detiene sin atravesarlo; detrás no frena; y `factorPorHueco`. También `reset()`, choque con el jugador y rutas inválidas rechazadas.
- `RuedasTraficoTest`: en 60 s simulados, en cada cuadro el ángulo de rueda de cada vehículo avanza exactamente distancia / `RADIO_RUEDA` (también mientras frena en los cruces); detenido ante un obstáculo (hueco 0) o en pausa no cambia; las delanteras nunca superan `ANGULO_MAX_DIRECCION`, doblan más de 10° en las curvas y vuelven a 0 en recta; `direccionPorGiro` da 0 sin giro, el signo del giro y el tope.
- `RuedaTest`: las ruedas se apoyan en el asfalto y quedan dentro del largo de la carrocería, con la cara interna bajo ella; `acercarDireccion` respeta el paso por cuadro y el tope.
- `LucesTraficoTest`: con noche activa todos los vehículos tienen las luces encendidas y con día apagadas; la tecla F no las cambia; los faros acompañan posición y orientación durante los giros.
- `LucesVehiculoTest`: con F encendido los faros del jugador son emisivos (blanco cálido) y con F apagado no (gris oscuro); las traseras son luz de posición emisiva con F y rojo oscuro sin F; el freno domina en ambos casos, también con Espacio y F apagado.
- `EntornoTest`: el campo no cambia `Mapa.LIMITE` (55) ni las colisiones (calles perimetrales transitables, mismo borde permitido, cordón y árboles inalcanzables); los árboles están todos afuera de la ciudad y del cordón, dentro del campo, con tipos y copas de parque, y son siempre los mismos.
- `AmbienteTest`: la cúpula, las estrellas y la luna entran en el plano lejano; las estrellas tienen dirección unitaria y están sobre el horizonte; hay una sombra fija por edificio, árbol y banco, ninguna sale de su acera o su césped ni cae en la calle; las sombras quedan apenas sobre el suelo y son más tenues de noche.
- `JuegoTest`: en menú y en pausa, `actualizar()` no mueve el auto ni el tráfico aunque se mantenga W; jugando, sí; tras R (`reiniciar()`) la cámara de seguimiento queda alineada con el auto y no barre en el cuadro siguiente.

## 5. Mejoras opcionales implementadas

| Mejora | Qué hace | Dónde |
|---|---|---|
| Ambiente | Campo verde con cordón y árboles alrededor de la ciudad, cielo con degradado (de noche con estrellas y luna) y sombras falsas con bordes difusos bajo edificios, autos, árboles y bancos. Ver "Ambiente" en la sección 2. | `mundo/Entorno`, `iluminacion/Cielo`, `iluminacion/Sombras`, `iluminacion.frag` (`uCielo`, `uSombra`) |
| Edificios variados | Cinco tipos de edificio (torre con antena o tanque, bloque con baranda y ascensor, escalonado, casa baja con techo de teja a dos aguas y doble), elegidos por celda con un hash, con una paleta urbana de paredes y techos y ventanas según el tipo. Respetan la huella de la manzana: las colisiones no cambian. | `mundo/Edificio`, `mundo/TipoEdificio`, `mundo/Fachada`, `motor/Figuras` |
| Figuras redondeadas y parques | Mallas nuevas generadas por fórmulas (esfera, cilindro y cono) con normales suaves, para que la luz del sol, las farolas y los faros las muestren redondeadas. Los parques tienen senderos, fuente, árboles frondosos y pinos, y bancos, con variación determinística entre parques. | `motor/Malla`, `motor/Figuras`, `mundo/Parque` |
| Farolas redondeadas | Las farolas dejan de ser cubos: base ancha, poste que se afina, brazo curvo de tres tramos inclinados con `uRotacion`, pantalla cónica oscura y bombilla esférica, emisiva solo de noche, en la misma posición que la luz. | `iluminacion/Iluminacion` |
| Semáforos redondeados | Con el mismo estilo que las farolas: base y poste cilíndricos, caja angosta y alta, tres lentes redondas (la activa emisiva y las apagadas del mismo color muy oscuro) con visera inclinada, y espalda lisa. | `mundo/Semaforo` |
| Auto mejorado | Ruedas cilíndricas con llanta, rayos y una marca roja que gira según la distancia recorrida; delanteras que doblan con A/D; luces de freno (rojo intenso) y de reversa (blanca), emisivas, de día y de noche. Las bombillas siguen a la tecla F: con F, faros blancos emisivos y luces de posición traseras en rojo tenue; sin F, apagadas (el freno sigue funcionando). Resuelve la limitación original "no hay ruedas animadas". El tráfico usa las mismas ruedas (`vehiculo/Rueda`): giran con lo que avanza cada vehículo y las delanteras doblan en las curvas. | `vehiculo/Auto`, `vehiculo/Rueda`, `trafico/Vehiculo`, `vehiculo/LucesVehiculo`, `ciudad.vert` (`uRotacion`) |
| Cabina con forma | La cabina deja de ser un bloque celeste: es un trapecio extruido desde un perfil lateral (`Malla.extruir`), con parabrisas y luneta inclinados, dos ventanillas por lado y un parante central, del color de cada auto. | `motor/Malla`, `vehiculo/Cabina`, `vehiculo/Auto`, `trafico/Vehiculo` |
| Cámara orbital del auto | Nuevo modo de C: la cámara gira alrededor del auto con el mouse (arrastrar = girar y elevar, ruedita = zoom), con límites de elevación (5° a 85°) y de distancia (4 a 30); acompaña al auto cuando dobla y se puede seguir manejando. No afecta al minimapa ni al HUD. | `motor/Camara`, `motor/Ventana` |
| Cámara aérea con mouse | La vista aérea deja de ser fija: con el botón izquierdo se gira alrededor de la ciudad y se cambia la elevación (20° a 85°), con la ruedita se acerca y aleja (20 a 2.6 límites, donde se ve toda la ciudad) y con el botón derecho se desplaza el punto que se mira, sin salir de la ciudad. Reutiliza la matemática de la orbital del auto (clase `Orbita`) y al entrar arranca con la vista de siempre. No cambia el minimapa ni el HUD (solo la ayuda H). | `motor/Orbita`, `motor/Camara`, `motor/Ventana`, `interfaz/Hud`, `ciudad.vert` (`uPlanoLejano`) |
| Flecha sobre el auto | En la vista aérea (tecla C), una flecha cian emisiva a 6 unidades de altura apunta al auto, sube y baja y gira despacio; se ve también de noche. No aparece en la cámara de seguimiento ni en el minimapa. | `vehiculo/IndicadorJugador`, `motor/Camara.esAerea`, `Juego` |
| Luces del tráfico | Los vehículos encienden sus luces solos de noche (tecla N) y las apagan de día; la tecla F solo afecta los faros del jugador. Encendidas, los faros son blancos y las traseras rojas, ambas emisivas; apagadas se ven gris oscuro y rojo oscuro. De noche cada vehículo proyecta dos focos reales sobre la calle, con el mismo cono (`smoothstep`) y atenuación que los del jugador, pero más débiles y cortos. El estado día/noche se lee de `Iluminacion` (no está duplicado). | `trafico/Vehiculo`, `trafico/Trafico.prepararFaros`, `iluminacion.frag` (`aporteFoco`) |
| Tráfico autónomo | Cuatro vehículos (amarillo, azul, verde y blanco) recorren rutas cíclicas con giros, en los sectores noroeste, noreste, sureste (con forma de L) y suroeste. Circulan por el carril derecho de su sentido, a `DESPLAZAMIENTO_CARRIL` (2.5) de la línea amarilla. Las rutas 1 y 4 comparten la calle Z = -10 en sentidos opuestos. En cada esquina pasan al carril del tramo siguiente girando suavemente, sin cruzar la línea central de su calle, y frenan en las curvas. Las rutas se validan contra el Mapa, así que nunca atraviesan edificios ni salen de la ciudad. De noche se ven sus luces traseras y, al frenar, las de freno. Frenan de forma gradual ante el jugador u otro vehículo que tengan adelante en su mismo carril (regla del corredor), y el jugador no puede atravesarlos (círculo contra círculo). R los reinicia. | `trafico/Vehiculo`, `trafico/Trafico`, `vehiculo/Colisiones`, `Juego` |
| HUD dentro de la ventana | Panel semitransparente arriba a la izquierda con velocidad, faros, día/noche, entregas, destino y sector. Se adapta al tamaño de la ventana. El texto lo genera STBEasyFont (sin texturas) en un pase 2D final que restaura profundidad, mezcla y viewport. | `interfaz/Dibujo2D`, `interfaz/Hud`, `hud.vert`, `hud.frag` |
| Ayuda de controles | H muestra u oculta la lista de teclas, abajo a la izquierda. | `interfaz/Hud` |
| Pausa | P congela auto, tráfico, entregas y semáforos (dt = 0) y muestra "PAUSA". | `juego/EstadoPartida`, `Juego`, `interfaz/Hud` |
| Menú de inicio | Al abrir, la escena queda de fondo oscurecida con el título y "Presiona ENTER para empezar". | `juego/EstadoPartida`, `interfaz/Hud` |
| Sectores con nombre | Centro, Barrio Norte, Parque Sur, Zona Oeste y Zona Este. El HUD muestra el sector actual y el minimapa dibuja sus divisiones y sus nombres. | `mundo/Mapa`, `juego/Minimapa`, `interfaz/Hud` |
| Señalización vial | Semáforos coordinados solo en el Centro (un cabezal por acceso, a la derecha y mirando al auto), pasos peatonales en todos los accesos de los cruces con semáforo o PARE y junto a los parques, PARE octogonales en los cruces sin semáforo más cercanos al Centro (8 con 11 × 11) y un cartel verde por sector en su entrada. Ver "Señalización vial" en la sección 2. | `mundo/Senalizacion`, `mundo/Semaforo`, `mundo/Decoracion`, `mundo/Mapa` |

## 6. Limitaciones conocidas

- **Iluminación:** es local y sin sombras reales. La luz de una farola puede atravesar un edificio (solo por debajo de la bombilla, dentro de su cono). Las sombras falsas son manchas debajo de cada objeto: no dependen de la dirección del sol ni de las farolas, y se oscurecen dos veces donde dos manchas se superponen.
- **Cielo y campo:** desde la aérea alejada al máximo, el borde lejano del campo puede quedar más allá del plano lejano y recortarse; detrás se ve el cielo.
- **Colisiones:** el auto choca solo con manzanas y bordes. Farolas, semáforos y árboles no tienen colisión, pero todos están sobre la vereda (el brazo de la farola cruza el cordón a 4.7 de altura y lo más bajo sobre la calzada es la bombilla, a 4.34), dentro de la celda de una manzana: el auto nunca llega hasta ellos, porque la colisión con la manzana lo detiene antes.
- **Choques:** el auto desliza por el eje libre o, de frente, se detiene sin rebote. El círculo de colisión es conservador (de costado sobran ≈ 0.65), por eso el auto choca un poco antes de tocar la acera con el lateral.
- **Semáforos:** son visuales: ni el tráfico ni el jugador se detienen en rojo (el alcance del enunciado es visual). No hay peatones ni audio.
- **Luces del tráfico:** sus focos iluminan la calle y los edificios, pero no proyectan sombras (como todas las luces del juego). `MAX_FAROS_TRAFICO` limita el tráfico con luces a 8 vehículos; si hubiera más, los restantes circularían con las bombillas encendidas pero sin foco.
- **Tráfico:** las rutas son fijas. Los vehículos no se superponen entre sí (lo comprueba `TraficoTest` con las rutas actuales). La previsión de la trayectoria supone que el otro sigue derecho, así que con rutas nuevas muy enredadas podría quedarse corta. Frenan ante el jugador o un vehículo en su carril, pero no esquivan ni respetan semáforos. El corredor usa la orientación del vehículo, así que en plena curva es aproximado.
- **Texto del HUD:** STBEasyFont solo tiene caracteres ASCII, así que las tildes se quitan ("Día" se ve como "Dia", "Presioná" como "Presiona"). La letra es de trazo fino y pixelado.
- **Señales:** `Cubo` solo gira alrededor del eje Y, así que el octógono del PARE se aproxima con tres rectángulos superpuestos y la palabra PARE se representa con una franja blanca. Los carteles de sector sí muestran su nombre (STBEasyFont, sin tildes).
- **Ruedas:** ya no es una limitación: giran según la distancia recorrida y las delanteras doblan, en el jugador y en el tráfico. Es solo visual; la física del giro no cambió (en el tráfico, la dirección se deduce del giro que ya hacía).
- **Minimapa:** se reduce en ventanas pequeñas (como máximo 260 px, un tercio del lado menor), siempre en la esquina superior derecha.
- **Título:** puede verse recortado si la ventana es angosta. La velocidad en km/h supone 1 unidad = 1 metro.
- **Entorno:** necesita una sesión gráfica; no funciona en un servidor sin pantalla.

## 7. Recursos externos usados

| Recurso | Uso |
|---|---|
| Proyecto base del curso: las etapas `clase1` a `clase4` del docente (ciudad, auto, iluminación y juego final, encadenadas por herencia), basadas en los ejemplos `AppCamara.java` y `AppLaberinto.java` | Punto de partida. Este proyecto las reorganizó por composición (`com.graphics.ciudad`) y las amplió; las etapas originales ya no se incluyen |
| [LWJGL 3.3.3](https://www.lwjgl.org/) — `lwjgl-glfw` (GLFW) y sus bibliotecas nativas (Windows, Linux, macOS) | Ventana, contexto OpenGL y teclado |
| LWJGL 3.3.3 — `lwjgl-opengl` (OpenGL 3.3 Core) y sus nativas | Todas las llamadas de dibujo, shaders, VAO/VBO |
| LWJGL 3.3.3 — `lwjgl-stb` (STB / STBEasyFont) y sus nativas | Texto del HUD y de los carteles de sector: convierte cada letra en cuadriláteros, sin texturas ni archivos de fuentes |
| JUnit 3.8.1 | Pruebas automáticas (`mvn test`) |
| Maven, con `maven-compiler-plugin` 3.13.0 y `exec-maven-plugin` 3.1.0 | Compilación, dependencias y ejecución |
| JDK 17 | Lenguaje y máquina virtual |
| Claude (chat) y Claude Code | Asistentes de IA usados para generar y revisar código, pruebas JUnit, el control de cámara con mouse y una auditoría contra el enunciado (ver "Herramientas de asistencia" más abajo) |

No se usan texturas, imágenes, modelos 3D, archivos de fuentes ni sonidos externos. Toda la geometría y los colores se generan en el código.

### Herramientas de asistencia

Se usó Claude (chat) y Claude Code como asistentes para generar y revisar código, escribir pruebas JUnit, implementar el control de cámara con mouse (orbital y aérea) y realizar una auditoría del proyecto contra el enunciado de la actividad. El autor revisó, entendió y adaptó cada cambio propuesto, y es capaz de explicar el funcionamiento del código entregado.

## 8. Cambios en vivo: qué tocar

Todos los valores ajustables son constantes con nombre al inicio de su archivo. Después de editar, volvé a ejecutar `mvn compile exec:exec`: los shaders se copian al classpath en `compile` y se cargan al arrancar.

### Ciudad y juego (Java)

| Cambio | Archivo | Constante o dato |
|---|---|---|
| Cantidad y reparto de farolas | `iluminacion/Iluminacion.java` | `FAROLAS_POR_SECTOR` ({3, 3, 3, 2, 2}: Centro, Barrio Norte, Parque Sur, Zona Oeste, Zona Este; la suma no puede pasar de `MAX_LUCES` = 16), `SEPARACION_SENALES` (3: del poste a un semáforo, PARE o cartel), `HOLGURA_POSTE_PASO` (0.3) y `HOLGURA_BOMBILLA_PASO` (0.35). `DESPLAZAMIENTO_FAROLA` (2.0: del centro del borde al poste, a lo largo de la cuadra), `DISTANCIA_MIN_PUERTA` (1.5: del poste al eje de una puerta o portón; debe ser ≤ `DESPLAZAMIENTO_FAROLA` o ninguna farola sería válida), `SENTIDOS` ({-1, 1}). `LUCES` (`{fila, columna, lado, sentido}`) no se escribe a mano: `farolaValida()` acepta un borde de manzana **con edificio** que da a la calle (en la franja de mobiliario de su vereda, corrido `DESPLAZAMIENTO_FAROLA` hacia `sentido` y lejos de puertas y portones; nunca un parque), dentro del sector, lejos de señales y fuera de los pasos; `calcularLuces()` elige, sector por sector, la candidata más alejada de las farolas ya ubicadas (`FarolasTest` lo verifica) |
| Ubicación de la bombilla (y de la luz) | `iluminacion/Iluminacion.java` | `BRAZO_FAROLA` (1.5), `MARGEN_POSTE` (0.4, del cordón al poste), `ALTURA_BOMBILLA` (4.5). Mueven la bombilla dibujada y la luz juntas |
| Forma de las farolas | `iluminacion/Iluminacion.java` | `ANCHO_BASE` (0.42), `ALTO_BASE` (0.45), `ANCHO_POSTE_ABAJO` (0.2), `ANCHO_POSTE_ARRIBA` (0.14), `GROSOR_BRAZO` (0.09), `PERFIL_BRAZO` (puntos {avance, altura} de los 3 tramos del brazo), `ANCHO_PANTALLA` (0.8), `ALTO_PANTALLA` (0.3), `SEPARACION_PANTALLA` (0.05), `DIAMETRO_BOMBILLA` (0.32) |
| Colores de las farolas | `iluminacion/Iluminacion.java` | `COLOR_BASE` (gris oscuro), `COLOR_POSTE` (gris metálico), `COLOR_PANTALLA` (casi negro), `COLOR_BOMBILLA_DIA` (gris claro), `COLOR_BOMBILLA_NOCHE` (blanco cálido, emisivo) |
| Agregar un parque o un edificio | `mundo/Mapa.java` | `MAPA`: una celda con fila y columna impares a `2` (parque) o `1` (edificio) |
| Ampliar el mapa: solo `Mapa.MAPA` | `mundo/Mapa.java` | Agregar filas y columnas manteniendo la matriz cuadrada, impar, con calles en filas y columnas pares (por ejemplo 13 × 13). Se adapta solo: **límites** (`TAMANO` y `LIMITE` salen de `MAPA.length × TAM_CELDA`); **colisiones** (`Colisiones` recorre la matriz y usa `LIMITE`); **cámaras** (la aérea escala su vista, su distancia máxima y el desplazamiento con `LIMITE`; la de seguimiento se recorta contra las celdas y el borde); **minimapa** (`uMitadMapa` = `LIMITE` + margen); **plano lejano** (`Camara.getPlanoLejano()` = distancia aérea máxima + diagonal + margen, enviado como `uPlanoLejano`); **inicio** (`Auto` usa `Mapa.centro()` sobre la penúltima fila); **destinos** (`CELDAS_DESTINOS` usa la última fila y columna); **sectores** (`RADIO_CENTRO` es proporcional a `LIMITE`: 25 con 11 × 11, 35 con 13 × 13); **semáforos** (todos los cruces del Centro); **usos de planta baja** (las avenidas principales salen del eje central de `MAPA`); **PARE** (los cruces sin semáforo más cercanos al Centro); **carteles** (en la entrada principal de cada sector); **farolas** (reglas de `farolaValida()` y reparto de `calcularLuces()`); también el suelo, el campo y los pasos peatonales. Los tests derivan sus valores de `Mapa`. Hay un 13 × 13 de referencia listo para pegar (sección "Mapa 13 × 13 de referencia"), que `mvn test` prueba siempre y que corre con `-Ddemo.frames=6` sin fallos. **No poner parque en la celda {penúltima fila, 1}**: taparía la salida del auto |
| Velocidad y manejo del auto | `vehiculo/Auto.java` | `VELOCIDAD_MAX` (16), `VELOCIDAD_REVERSA` (6), `ACELERACION` (9), `RESISTENCIA` (0.7), `FRENO` (7), `VELOCIDAD_GIRO` (0.11) |
| Ruedas y dirección (jugador y tráfico) | `vehiculo/Rueda.java` | `RADIO_RUEDA` (0.32; el centro queda a esa altura, así toca el asfalto), `ANCHO_RUEDA` (0.24), `FRACCION_LLANTA` (0.62), `LADO_RUEDA` (0.88) y `EJE_RUEDA` (0.82: ubicación en la carrocería; `RuedaTest` avisa si salen de ella), `ANGULO_MAX_DIRECCION` (30°), `VELOCIDAD_DIRECCION` (3 rad/s), `COLOR_NEUMATICO`, `COLOR_LLANTA`, `COLOR_RAYO`, `COLOR_MARCA_LLANTA` |
| Luces del auto: faros, posición y freno | `vehiculo/LucesVehiculo.java` (compartidas con el tráfico) | `COLOR_FARO_ENCENDIDO` (blanco cálido, emisivo), `COLOR_FARO_APAGADO` (gris-beige oscuro), `COLOR_POSICION` (rojo tenue, emisivo), `COLOR_FRENO` (rojo intenso, emisivo), `COLOR_TRASERA_APAGADA` (rojo oscuro), `LADO_LUZ` (0.55), `ALTURA_LUZ` (0.68), `FRENTE_LUZ` (-1.32), `TRASERA_LUZ` (1.32), `TAMANO_FARO`, `TAMANO_TRASERA` |
| Luz de reversa | `vehiculo/Auto.java` | `COLOR_REVERSA_APAGADA`, `COLOR_REVERSA` (blanca, emisiva), `UMBRAL_MOVIMIENTO` (0.1). Se enciende mientras el auto va hacia atrás o mientras S pide reversa con el auto detenido (velocidad previa ≤ `UMBRAL_MOVIMIENTO`), así no parpadea al empujar marcha atrás contra una pared |
| Cámara orbital del auto | `motor/Camara.java` | `ANGULO_INICIAL` (0 = detrás), `ELEVACION_INICIAL` (25°), `DISTANCIA_INICIAL` (9), `ELEVACION_MIN` / `ELEVACION_MAX` (5° / 85°), `DISTANCIA_MIN` / `DISTANCIA_MAX` (4 / 30), `PASO_ZOOM` (1) |
| Sensibilidad del mouse (orbital y aérea) | `motor/Camara.java` | `SENSIBILIDAD_GIRO` (0.008 rad/px), `SENSIBILIDAD_ELEVACION` (0.006 rad/px) |
| Punto de partida | `vehiculo/Auto.java` | `COLUMNA_SALIDA` (0) y `FILA_SALIDA` (penúltima fila, impar: mitad de cuadra); `X_INICIAL` y `Z_INICIAL` se calculan con `Mapa.centro()`; `CARRIL_SALIDA` (2.5, como el carril del tráfico) |
| Tiempos del semáforo | `mundo/Semaforo.java` | `DURACION_VERDE` (5), `DURACION_AMARILLO` (2); `DURACION_ROJO` se calcula como verde + amarillo (7) y `DESFASE_ESTE_OESTE` = rojo, para mantener la coordinación; `BRILLO_APAGADA` (0.15: brillo de las lentes apagadas) |
| Forma del semáforo | `mundo/Semaforo.java` | `ANCHO_BASE` (0.34), `ALTO_BASE` (0.35), `ANCHO_POSTE` (0.14), `ANCHO_CAJA` (0.42), `ALTO_CAJA` (1.3), `PROFUNDIDAD_CAJA` (0.34), `BASE_CAJA` (2.5), `DIAMETRO_LENTE` (0.3), `GROSOR_LENTE` (0.05), `SEPARACION_LENTES` (0.38; con la caja de 1.3 y lentes de 0.3, hasta 0.5), `LARGO_VISERA` (0.22), `GROSOR_VISERA` (0.025), `EXCESO_VISERA` (0.06), `HOLGURA_VISERA` (0.03), `INCLINACION_VISERA` (20°). `SemaforoTest` avisa si las lentes se salen de la caja |
| Colores del semáforo | `mundo/Semaforo.java` | `COLOR_BASE`, `COLOR_POSTE` (grises oscuros), `COLOR_CAJA` (casi negro, también las viseras), `COLORES_LENTE` (rojo, ámbar y verde encendidos; apagados = × `BRILLO_APAGADA`) |
| Dónde hay semáforos | `mundo/Senalizacion.java` | `SECTOR_SEMAFOROS` (0 = Centro): todas las intersecciones de ese sector forman `INTERSECCIONES_SEMAFORO`; `RETROCESO_SEMAFORO` y `RETROCESO_PARE` (= `SEPARACION_CRUCE` + `LARGO_PASO` = 3.5: detrás del paso), `MARGEN_VEREDA` (0.6) |
| Agregar o mover una entrega | `juego/Entregas.java` | `CELDAS_DESTINOS` `{fila, columna}` de calle (se convierten a `DESTINOS` con `Mapa.centro()`, como las rutas de `Trafico`) y su nombre en `NOMBRES_DESTINOS` |
| Qué tan cerca y lento hay que llegar | `juego/Entregas.java` | `RADIO_LLEGADA` (3), `VELOCIDAD_LLEGADA` (1) |
| Tamaño del minimapa | `juego/Minimapa.java` | `TAMANO_MAX_MINIMAPA` (260 px), `MARGEN_MINIMAPA` (18 px), `BORDE_MINIMAPA` (3 px), `MARGEN_MAPA` (zoom), `ESCALA_INDICADOR` (1.5) |
| Cámara de seguimiento ("acercá la cámara", "que mire más arriba") | `motor/Camara.java` | Encuadre: `DISTANCIA_SEGUIMIENTO` (8: acercar = bajarla), `INCLINACION` (11°: que mire más arriba = bajarla, más desde arriba = subirla), `ADELANTE` (4: cuánto por delante del auto está el punto que mira), `ALTURA_OBJETIVO_MIRA` (1.2: altura de ese punto), `ALTURA_MINIMA_SEGUIMIENTO` (2). La altura no se escribe: sale de `ALTURA_OBJETIVO_MIRA + (d + ADELANTE) · tan(INCLINACION)` (≈ 3.5 con d = 8), así la inclinación no cambia cuando el recorte acerca la cámara. Suavizado: `K_GIRO` (5 por segundo: más = sigue el giro del auto más rígida, menos = más atrasada; el atraso se multiplica por exp(−K_GIRO · dt) y da lo mismo a cualquier FPS). Recorte: `DISTANCIA_SEGUIMIENTO_MIN` (1.5), `MARGEN_BORDE_CAMARA` (1: cuánto adentro del borde queda la cámara), `PASO_RECORTE` (0.25: paso grueso de la búsqueda), `ITERACIONES_RECORTE` (10: búsqueda binaria dentro del último paso, deja la distancia continua con un error de ≈ 0.0002; con 0 vuelven los escalones de 0.25 y el temblor en reversa), `VELOCIDAD_ALEJAMIENTO` (12 unidades/s para volver a la distancia normal; acercarse es inmediato). `ALTURA_OBJETIVO` (0.8) queda solo para la orbital |
| Vista inicial de la cámara aérea | `motor/Camara.java` | `ANGULO_AEREO_INICIAL` (0.6 rad), `FACTOR_RADIO` (1.86), `FACTOR_ALTURA` (1.57), proporcionales a `Mapa.LIMITE`: la cámara arranca a 1.86 límites del centro sobre el suelo y 1.57 de altura (≈ 40° de elevación y 134 de distancia) |
| Mouse en la cámara aérea | `motor/Camara.java` | `ELEVACION_AEREA_MIN` / `ELEVACION_AEREA_MAX` (20° / 85°), `DISTANCIA_AEREA_MIN` (20), `FACTOR_DISTANCIA_AEREA_MAX` (2.6: la distancia máxima es 2.6 · `Mapa.LIMITE` = 143, algo más que la vista inicial; el plano lejano se ajusta solo), `PASO_ZOOM_AEREO` (6 por paso de ruedita), `SENSIBILIDAD_DESPLAZAMIENTO` (0.0015 por píxel y por unidad de distancia: lejos, el botón derecho mueve más rápido); `MARGEN_TECHO` (2: sobre un edificio, la aérea queda al menos esto por encima de su techo) |
| Rutas del tráfico | `trafico/Trafico.java` | `RUTAS_CELDAS`: listas cíclicas de cruces `{fila, columna}` (pares); cada tramo debe ir en línea recta por calle, o el juego se detiene al arrancar con un mensaje |
| Velocidad y color de los vehículos | `trafico/Trafico.java` | `VELOCIDADES` (7, 6, 8, 6.5), `COLORES` |
| Manejo del tráfico | `trafico/Vehiculo.java` | `VELOCIDAD_GIRO` (2.2 rad/s), `RADIO_WAYPOINT` (2), `FRACCION_MINIMA_CURVA` (0.3), `DISTANCIA_ANTICIPACION` (4) |
| Frenado del tráfico (regla del corredor) | `trafico/Vehiculo.java` | `MEDIO_CORREDOR` (2.2: con más de 5 volvería a frenar por el carril contrario), `DISTANCIA_FRENADO` (8), `DISTANCIA_DETENCION` (1), `DESACELERACION` (8 u/s²), `ACELERACION_TRAFICO` (4 u/s²), `PREVISION` (1.5 s) y `MUESTRAS_PREVISION` (6), `UMBRAL_LUZ_FRENO` (0.5 u/s²), `UMBRAL_DETENIDO` (0.05) |
| Tamaño del jugador para el tráfico | `trafico/Trafico.java` | `RADIO_JUGADOR` (= `Auto.RADIO_AUTO`, 1.65) |
| Colisión y deslizamiento del jugador | `vehiculo/Auto.java`, `vehiculo/Colisiones.java` | `RADIO_AUTO` (1.65), `FRICCION_ROCE` (30 u/s², escalada por la fracción bloqueada), `FRACCION_MINIMA_DESLIZAMIENTO` (0.1: con menos, de frente, se detiene) |
| Luces del tráfico (shader) | `iluminacion.frag` | `INTENSIDAD_FARO_TRAFICO` (4.0, la mitad que el jugador), `ALCANCE_FARO_TRAFICO` (4.0: a esa distancia el foco rinde la mitad), `MAX_FAROS_TRAFICO` (16) |
| Máximo de focos (Java) | `trafico/Trafico.java` | `MAX_FAROS_TRAFICO` (16): debe ser igual al del shader; `FAROS_POR_VEHICULO` (2) |
| Luces del tráfico | `vehiculo/LucesVehiculo.java` | Las mismas constantes que el auto del jugador; de noche el tráfico muestra faros encendidos y luces de posición |
| Carril del tráfico | `trafico/Vehiculo.java` | `DESPLAZAMIENTO_CARRIL` (`TAM_CELDA / 4` = 2.5): distancia del centro del carril derecho a la línea amarilla. Con más de ≈ 3.3 el vehículo tocaría la vereda (`TraficoTest` lo detectaría) |
| Flecha sobre el auto (vista aérea) | `vehiculo/IndicadorJugador.java` | `ALTURA_INDICADOR` (6), `TAMANO_INDICADOR` (2.5), `COLOR_INDICADOR` (cian), `AMPLITUD_OSCILACION` (0.6), `FRECUENCIA_OSCILACION` (3), `VELOCIDAD_ROTACION` (1.2) |
| Sectores | `mundo/Mapa.java` | `NOMBRES_SECTORES`, `SECTORES` (rectángulos `{xMin, xMax, zMin, zMax}`), `FRACCION_CENTRO` (5/11): `RADIO_CENTRO` = `radioDelCentro(FRACCION_CENTRO · LIMITE)`, el borde válido más cercano, con una manzana adentro y una calle afuera (25 con 11 × 11) |
| Tamaño y estilo del HUD | `interfaz/Hud.java` | `ALTO_REFERENCIA` (380: escala 2 con 760 px de alto), `ESCALA_MINIMA` (1), `ESCALA_MAXIMA` (3), `MARGEN` (12), `RELLENO` (8), `ALTO_LINEA` (11), `ALFA_PANEL` (0.55), `ALFA_MENU` (0.7), `AYUDA` |
| PARE | `mundo/Senalizacion.java` | `MAX_PARE` (10); `UBICACIONES_PARE` (`{fila, columna, dFila, dColumna}`) lo calcula `calcularUbicacionesPare()`; `LADO_PARE` (0.9), `BORDE_PARE` (0.07), `ALTURA_POSTE` (2.6) |
| Carteles de sector | `mundo/Senalizacion.java` | `RETROCESO_CARTEL` (0.75, de la esquina al cartel), `SEPARACION_CARTEL` (1.5, del cordón al cartel); `CARTELES_SECTOR` (`{sector, x, z, ángulo}`) lo calcula `cartelDeSector()`; `ANCHO_CARTEL` (2.6), `ALTO_CARTEL` (0.8), `BORDE_CARTEL` (0.08), `ALTURA_CARTEL` (2.4), `COLOR_CARTEL` |
| Pasos peatonales | `mundo/Decoracion.java` | `LARGO_PASO` (3, en el sentido de circulación), `SEPARACION_CRUCE` (0.5, asfalto libre entre el cruce y el paso), `FRANJAS_PASO` (6), `SEPARACION_FRANJAS` (1.65), `ANCHO_FRANJA` (0.9), `ALTURA_FRANJA` (0.03), `GROSOR_FRANJA` (0.02). `UBICACIONES_PASOS` se genera en `calcularUbicaciones()`: todos los accesos de `crucesControlados()` (semáforo o PARE) y los parques |
| Colores de paredes y techos | `mundo/Edificio.java` | `PALETA_FACHADAS` (ladrillo, crema, blanco hueso, gris cemento, terracota, verde agua, amarillo pálido), `PALETA_TECHOS` (gris oscuro, gris claro, teja, verde), `TECHO_TEJA` (índice del techo de las casas), `COLOR_METAL`, `COLOR_TANQUE` |
| Qué tipo sale en cada manzana | `mundo/Edificio.java` | `SEMILLA_TIPO` (167): cambiarla sortea otra ciudad (siempre la misma para cada semilla). `EdificioTest` avisa si quedan menos de 4 tipos |
| Torre | `mundo/Edificio.java` | `ALTO_PISO` (3, todos los tipos), `PISOS_TORRE_MIN` / `PISOS_TORRE_MAX` (8 / 11 = 24.2 a 33.2), `ANCHO_TORRE` (4.4), `ALTURA_PODIO` (3.2), `PROBABILIDAD_ANTENA` (0.5), `ALTO_ANTENA` (3.5), `GROSOR_ANTENA` (0.12), `DIAMETRO_TANQUE` (1.6), `ALTO_TANQUE` (1.3), `ALTO_PATAS` (0.8). Mantener `PISOS_TORRE_MIN` mayor que el máximo de los otros tipos (`EdificioTest`) |
| Bloque | `mundo/Edificio.java` | `PISOS_BLOQUE_MIN` / `PISOS_BLOQUE_MAX` (4 / 6), `ALTO_BARANDA` (0.5), `GROSOR_BARANDA` (0.1), `ANCHO_ASCENSOR` (2), `ALTO_ASCENSOR` (1.8), `CORRIMIENTO_ASCENSOR` (1.3) |
| Escalonado | `mundo/Edificio.java` | `ANCHOS_NIVELES` (7, 5.2, 3.6), `NIVELES_MIN` / `NIVELES_MAX` (2 / 3), `PISOS_PRIMER_NIVEL_MIN` / `_MAX` (2 / 3), `PISOS_POR_NIVEL` (2, los de arriba) |
| Casa baja | `mundo/Edificio.java` | `PISOS_CASA_MIN` / `PISOS_CASA_MAX` (2 / 2: con 1 piso no hay lugar para ventanas), `ALTO_PLANTA_CASA` (3.2), `ALTO_PISO_CASA` (= `ALTO_PISO`), `ALTO_TECHO_CASA` (2), `LADO_CHIMENEA` (0.5), `ALTO_CHIMENEA` (1.8) |
| Doble | `mundo/Edificio.java` | `ANCHO_PARTE_ALTA` (4 de los 7), `PISOS_DOBLE_ALTA_MIN` / `_MAX` (5 / 7), `PISOS_DOBLE_BAJA_MIN` / `_MAX` (2 / 3) |
| Huella y losas | `mundo/Edificio.java` | `VUELO_CORNISA` (0.15; `MEDIA_HUELLA` = 3.5 + vuelo debe quedar < 5 o `EdificioTest` avisa), `GROSOR_LOSA` (0.3), `ALTO_MINIMO_BASE` (3.2: la planta baja con su toldo) |
| Ventanas según el tipo | `mundo/TipoEdificio.java` | Los cinco números de cada tipo: columnas por cara, separación, ancho, alto y altura de piso (por ejemplo `TORRE(4, 1.0f, 0.6f, 0.8f, 1.6f)`) |
| Dónde van las ventanas | `mundo/Fachada.java` | `PRIMER_PISO_Y` (1.9), `MARGEN_VERTICAL` (0.35), `MARGEN_LATERAL` (0.3), `TOPE_PLANTA_BAJA` (2.8) |
| Ventanas de noche | `mundo/Fachada.java` | `PORCENTAJE_VENTANAS_ENCENDIDAS` (0.65), `TONOS_VENTANA` (amarillo cálido, blanco cálido, anaranjado, blanco frío), `PESOS_TONOS` (0.4, 0.3, 0.2, 0.1), `COLOR_VENTANA_APAGADA` |
| Ventanas de día | `mundo/Fachada.java` | `COLOR_VIDRIO_DIA` (blanco-celeste grisáceo, sin emisión) |
| Toldos | `mundo/Fachada.java` | `COLORES_TOLDO` (rojo, verde, azul, naranja), `PROBABILIDAD_RAYAS` (0.35), `ANCHO_RAYA` (0.6), `EXCESO_TOLDO` (0.05 a cada lado de la vidriera), `VUELO_TOLDO` (0.7; mantener ≤ 0.8 para no tocar los postes de semáforo), `ALTURA_TOLDO` (2.45), `CAIDA_TOLDO` (0.3), `ESCALONES_TOLDO` (3) |
| Puerta y vidrieras | `mundo/Fachada.java` | `ANCHO_PUERTA` (1, siempre centrada), `ALTO_PUERTA` (2), `SEPARACION_PUERTA` (0.2), `MARGEN_ESQUINA` (0.4); `ANCHO_VIDRIERA` se calcula como 3.5 − margen − media puerta − separación (2.4) y `CENTRO_VIDRIERA` (1.9); `ALTO_VIDRIERA` (1.4), `COLOR_VIDRIERA_DIA`, `COLOR_VIDRIERA_NOCHE_ABAJO` / `COLOR_VIDRIERA_NOCHE_ARRIBA` (degradado de noche), `FRANJAS_VIDRIERA` (6) |
| Usos de planta baja por zona | `mundo/UsoPlantaBaja.java` | `SECTOR_COMERCIAL` (0 = Centro: ahí todo lo que no es torre ni casa es negocio), `DISTANCIA_AVENIDA` (2: calles a esa distancia del eje o menos son avenidas principales; con 1, en el 13 × 13 solo cuenta la calle 6 y el comercio baja a ≈ 35 %; con 4 casi todo lo que no es torre ni casa es comercial). La regla por tipo está en `de()`. `UsoPlantaBajaTest` avisa si el comercio baja del 40 % |
| Lobby de oficinas (torres) | `mundo/Fachada.java` | `ANCHO_PUERTA_DOBLE` (1.6), `ALTO_PUERTA_LOBBY` (2.3), `SEPARACION_PUERTA_LOBBY` (0.1), `ALTO_VIDRIO_LOBBY` (2.9; menor que `ALTURA_PODIO`), `SEPARACION_PARANTES` (1), `ANCHO_PARANTE` (0.08), `COLOR_VIDRIO_LOBBY_DIA` / `COLOR_VIDRIO_LOBBY_NOCHE` (emisivo suave), `ANCHO_MARQUESINA` (2.4), `VUELO_MARQUESINA` (0.7; mantener ≤ `VUELO_TOLDO`), `GROSOR_MARQUESINA` (0.12) |
| Departamentos y casas | `mundo/Fachada.java` | `ANCHO_VENTANA_PLANTA_BAJA` (1.4), `CENTRO_VENTANA_PLANTA_BAJA` (1.9), `ALTO_ESCALON` (0.15), `FONDO_ESCALON` (0.35), `EXCESO_ESCALON` (0.15), `ANCHO_ALERO` (1.6), `VUELO_ALERO` (0.5), `GROSOR_ALERO` (0.1), `COLOR_PUERTA_CASA` (madera) |
| Garaje de las casas | `mundo/Fachada.java` | `PROBABILIDAD_GARAJE` (0.5), `SEMILLA_GARAJE` (29: 2 de 4 casas; con 25 ninguna, y `FachadaTest` avisa), `ANCHO_GARAJE` (= `ANCHO_VIDRIERA`, 2.4), `ALTO_GARAJE` (2.2), `LISTONES_GARAJE` (5), `COLOR_GARAJE`, `COLOR_LISTON` |
| Resolución de las figuras | `motor/Figuras.java` | `SECTORES_ESFERA` (12), `ANILLOS_ESFERA` (8), `LADOS_CILINDRO` (10), `LADOS_CONO` (10), `PERFIL_PRISMA` (triángulo del techo a dos aguas): más lados = más redondo y más triángulos |
| Forma de la cabina | `vehiculo/Cabina.java` | `PERFIL_CABINA` (puntos {z, y} del contorno lateral; debe ser convexo), `ANCHO_CABINA` (1.40) |
| Vidrios de la cabina | `vehiculo/Cabina.java` | `COLOR_VIDRIO` (azul-gris oscuro), `SEPARACION_VIDRIO` (0.012), `GROSOR_VIDRIO` (0.02), `MARGEN_VIDRIO` (0.07), `BASE_VENTANILLA` (0.98), `TECHO_VENTANILLA` (1.33), `CENTRO_PARANTE` (0.20), `ANCHO_PARANTE` (0.12) |
| Color del auto del jugador | `vehiculo/Auto.java` | `COLOR_CARROCERIA` (rojo: carrocería y cabina) |
| Árboles de los parques y del campo | `mundo/Parque.java` | `ARBOLES_MIN` / `ARBOLES_MAX` (4 / 6), `ALTURA_FRONDOSO_MIN` / `_MAX` (5 / 7), `ALTURA_PINO_MIN` / `_MAX` (5 / 6), `FRACCION_TRONCO_FRONDOSO` (0.4), `FRACCION_TRONCO_PINO` (0.2), `COPA_MAXIMA` (1/4 de la celda = 2.5, ancho), `RADIO_CENTRO_LIBRE` (2.2), `LUGARES_ARBOL` (esquinas y bordes posibles), `MARGEN_POSTES` (0.2 entre la copa y cada señal; `MITAD_SEMAFORO` 0.5, `MITAD_PARE`, `MITAD_CARTEL`, `MITAD_FAROLA`) |
| Senderos, fuente y bancos | `mundo/Parque.java` | `ANCHO_SENDERO` (1.4), `GROSOR_SENDERO` (0.03), `COLOR_SENDERO`, `RADIO_FUENTE` (1.2), `COLOR_AGUA`, `COLOR_AGUA_NOCHE`, `BANCOS_MIN` / `BANCOS_MAX` (2 / 4), `MITAD_FONDO_BANCO` (0.25), `FONDO_RESPALDO` (0.28). `DISTANCIA_BANCO` (≈ 2.63) ya no se escribe: sale de la plaza y `MARGEN_BANCO_PLAZA` |
| Plaza y franjas de acceso (reglas peatonales) | `mundo/Parque.java` | `PASO_FUENTE` (1.0, anillo libre alrededor de la fuente), `MITAD_PLAZA` (= `RADIO_FUENTE` + `PASO_FUENTE` = 2.2, apotema del octógono), `PROFUNDIDAD_FRANJA` (0.9; más de 1.03 haría que la franja toque los troncos de borde), `MARGEN_BANCO_PLAZA` (0.05, aire entre el banco y la plaza). Las franjas salen de `Decoracion.UBICACIONES_PASOS`: si se mueven los pasos, las franjas los siguen |
| Luminarias globo: modelo | `mundo/Parque.java` | `ALTURA_GLOBO` (3.0, centro sobre el césped), `DIAMETRO_GLOBO` (0.45), `ANCHO_BASE_GLOBO` (0.24), `ALTO_BASE_GLOBO` (0.3), `ANCHO_POSTE_GLOBO` (0.1), `ANCHO_ANILLO_GLOBO` / `ALTO_ANILLO_GLOBO` (0.2 / 0.1), `COLOR_POSTE_GLOBO`, `COLOR_GLOBO_DIA`, `COLOR_GLOBO_NOCHE` |
| Luminarias globo: ubicación y cantidad | `mundo/Parque.java` | `LUMINARIAS_MAX_POR_PARQUE` (2; baja a 1 si no entran en `uGlobos`), `DESVIO_LUMINARIA` (0.5 del eje del sendero; la base no debe pasar el borde, a 0.7), `DISTANCIAS_LUMINARIA` (2.4, 3.0, 3.6 del centro), `MARGEN_LUMINARIA` (0.15 a copas y bancos), `MARGEN_LUMINARIA_POSTES` (0.5 a señales y farolas) |
| Cuántas luces puntuales admite el shader | `iluminacion/Iluminacion.java` y `iluminacion.frag` | `MAX_GLOBOS` (16) en los dos archivos: `LuminariasParqueTest` avisa si no coinciden |
| Basurero: modelo | `mundo/Basureros.java` | `DIAMETRO` (0.5), `ALTO` (0.9 con tapa), `ALTO_TAPA` (0.08), `EXCESO_TAPA` (0.04), `ALTO_ARO` (0.06), `COLOR_CUERPO` (verde), `COLOR_TAPA` (gris oscuro) |
| Basureros en los parques | `mundo/Basureros.java` | `BANCOS_POR_BASURERO` (1 = uno por banco, 2 = uno cada dos), `SEPARACION_BANCO` (0.15), `RETIRO_BANCO` (0.15; con 0.3 el cesto queda a menos de 0.8 de un tronco de borde), `SEMILLA_LADO_BANCO` (31, sortea el costado de todos los bancos de un parque) |
| Basureros en esquinas y negocios | `mundo/Basureros.java` | `MARGEN_CORDON` (0.45: sobre la franja de mobiliario), `HOLGURA_SALIDA_PASO` (0.4), `FRACCION_NEGOCIOS` (0.35), `SEMILLA_NEGOCIOS` (97), `HOLGURA_VIDRIERA` (0.1), `SEPARACION_PARED` (0.05) |
| Distancias mínimas de los basureros | `mundo/Basureros.java` | `MARGEN_POSTES` (0.35 a señales y farolas), `HOLGURA_PASO` (0.3 alrededor del paso y su salida), `ZONA_FRENTE_NEGOCIO` (toldo + 0.05), `DISTANCIA_MIN_TRONCO` (0.8, frondosos; pinos: su copa), `MARGEN_LUMINARIA` (0.4), `MARGEN_BANCO` (0.1), `DISTANCIA_MIN_ENTRE_BASUREROS` (2) |
| Marcas del minimapa | `juego/Minimapa.java` | `GROSOR_DIVISION` (0.7); `ALTURA_DIVISION`, `ALTURA_DESTINO`, `ALTURA_INDICADOR`, `ALTURA_PUNTA` = `Edificio.ALTURA_MAXIMA` + 1 a 4 (por encima de la torre más alta y por debajo de `ESCALA_ALTURA_MAPA` = 100) |
| Campo alrededor de la ciudad | `mundo/Entorno.java` | `ENTORNO_EXTRA` (100: cuánto sigue el pasto más allá del borde; no cambia `Mapa.LIMITE`), `ALTURA_CAMPO` (0), `COLOR_CAMPO`, `ANCHO_CORDON` (1.2), `ALTO_CORDON` (0.3), `COLOR_CORDON` |
| Árboles del campo | `mundo/Entorno.java` | `CANTIDAD_ARBOLES` (36), `DISTANCIA_MIN_ARBOL` (6 desde el borde; debe superar cordón + media copa o `EntornoTest` avisa), `ANCHO_FRANJA_ARBOLES` (50), `SEMILLA_ARBOLES` (211: otra semilla, otra disposición, siempre la misma) |
| Colores del cielo | `iluminacion/Cielo.java` | `COLOR_CENIT_DIA` (azul intenso), `COLOR_HORIZONTE_DIA` (celeste claro), `COLOR_CENIT_NOCHE` (azul casi negro), `COLOR_HORIZONTE_NOCHE` (azul noche); `RADIO_CIELO` (250, menor que el plano lejano ≈ 319) |
| Estrellas y luna | `iluminacion/Cielo.java` | `CANTIDAD_ESTRELLAS` (160), `SEMILLA_ESTRELLAS` (97), `ALTURA_MIN_ESTRELLA` (0.08 ≈ 5° sobre el horizonte), `TAMANO_MIN_ESTRELLA` / `TAMANO_MAX_ESTRELLA` (0.5 / 1.3), `BRILLO_MIN_ESTRELLA` (0.55); `DIRECCION_LUNA` (norte-noreste, ≈ 19° de altura), `DIAMETRO_LUNA` (11), `COLOR_LUNA` |
| Sombras falsas | `iluminacion/Sombras.java` | `ALFA_SOMBRA_DIA` (0.45) / `ALFA_SOMBRA_NOCHE` (0.22, más tenue), `COLOR_SOMBRA`, `ELEVACION_SOMBRA` (0.06 sobre la superficie: más chico puede parpadear o quedar bajo la pintura vial), `NUCLEO_SOMBRA` (0.35: dónde empieza el difuminado), `FORMA_REDONDA` (2) / `FORMA_CUADRADA` (4) |
| Tamaño de cada sombra | `iluminacion/Sombras.java` | `ESCALA_SOMBRA_EDIFICIO` (1.4 · base de 7 = 9.8; mantener < 10/7 para no salir de la acera), `SOMBRA_AUTO` (2.3 × 3.5), `ESCALA_SOMBRA_ARBOL` (1.25 · copa), `SOMBRA_BANCO` (2.1 × 1.0), `ESCALA_SOMBRA_BASURERO` (1.6 · 0.5 = 0.8), `SOMBRA_LUMINARIA` (0.7) |

### Mapa 13 × 13 de referencia (para la defensa)

Es el mismo mapa que usan las pruebas (`MapasDePrueba.MAPA_13`, en `src/test`); `MapaTest` comprueba que este bloque y la constante coincidan. Tiene 36 manzanas: 26 edificios y 10 parques.

**Cómo usarlo en vivo:** en `src/main/java/com/graphics/ciudad/mundo/Mapa.java`, dentro de `MAPA = elegirMapa(new int[][] { … });`, borrá las **11 filas** `{0, …}` (desde `{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, // Fila norte` hasta `{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0} // Calle del borde sur`) y pegá en su lugar estas 13. No toques la primera línea (`public static final int[][] MAPA = elegirMapa(new int[][] {`) ni la última (`});`). Después: `mvn compile exec:exec`.

```java
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, // Fila 0: calle del borde norte.
        {0, 1, 0, 1, 0, 2, 0, 1, 0, 1, 0, 1, 0}, // Fila 1: parque en la columna 5.
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, // Fila 2.
        {0, 2, 0, 1, 0, 1, 0, 1, 0, 1, 0, 2, 0}, // Fila 3: parques en los extremos oeste y este.
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, // Fila 4.
        {0, 1, 0, 1, 0, 2, 0, 1, 0, 2, 0, 1, 0}, // Fila 5: parques en las columnas 5 y 9.
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, // Fila 6: calle central (eje de la ciudad).
        {0, 1, 0, 2, 0, 1, 0, 1, 0, 2, 0, 1, 0}, // Fila 7: parques en las columnas 3 y 9.
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, // Fila 8.
        {0, 1, 0, 1, 0, 2, 0, 1, 0, 1, 0, 1, 0}, // Fila 9: parque en la columna 5.
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, // Fila 10.
        {0, 1, 0, 1, 0, 1, 0, 2, 0, 2, 0, 1, 0}, // Fila 11: la salida está a su izquierda (columna 0): {11, 1} es edificio.
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0} // Fila 12: calle del borde sur.
```

Para volver a la ciudad normal, deshacé el cambio (por ejemplo `git checkout -- src/main/java/com/graphics/ciudad/mundo/Mapa.java`).

**Sin editar nada:** `mvn test` ya prueba este mapa en una segunda pasada (`pom.xml`, ejecución `pruebas-mapa-13`, con `-Dciudad.mapa=com.graphics.ciudad.mundo.MapasDePrueba#MAPA_13`; informes en `target/surefire-reports-mapa-13`). Para abrir el juego con este mapa sin tocar `Mapa.java`, compilá las pruebas (`mvn test-compile`) y ejecutá `java` con `target/test-classes` en el classpath y `-Dciudad.mapa=com.graphics.ciudad.mundo.MapasDePrueba#MAPA_13` (ver el comando abajo).

```powershell
mvn test-compile exec:exec "-Dexec.classpathScope=test" "-Dciudad.mapa=com.graphics.ciudad.mundo.MapasDePrueba#MAPA_13"
```

(Las comillas hacen falta en PowerShell, que corta los argumentos `-D` en el punto. `exec.classpathScope=test` suma `target/test-classes`, donde está `MapasDePrueba`; `pom.xml` pasa `ciudad.mapa` al juego y, vacía, deja la ciudad 11 × 11.)

**Números del 13 × 13 de referencia:** `RADIO_CENTRO` = 35; 9 cruces con semáforo y 10 PARE; 83 pasos peatonales; 10 luminarias de parque (una por parque); 55 franjas de acceso en los parques; 29 basureros en parques (uno por banco), 63 en esquinas y 6 junto a negocios; usos de planta baja: 13 comerciales (50 %), 5 lobbies, 3 residenciales y 5 casas; de noche, 812 de 1250 ventanas encendidas (65 %).

**Regla de la salida: no poner parque en la celda {penúltima fila, 1}** (`{11, 1}` en 13 × 13; `{9, 1}` en 11 × 11). Es la manzana a la derecha del auto al salir. Cada parque lleva un paso peatonal en la calle de su oeste, pegado al cruce del sur; para esa manzana, el paso caería en la calle de la salida a 3 del auto y lo taparía. `MapaTest.testSinParqueJuntoALaSalida` calcula ese paso, comprueba que taparía la salida y verifica que ni el mapa activo ni `MAPA_13` tengan parque ahí.

### Iluminación y proyección (shaders GLSL en `src/main/resources/shaders`)

| Cambio | Archivo | Constante (valor) |
|---|---|---|
| Luz ambiente de día y de noche | `iluminacion.frag` | `AMBIENTE_DIA` (0.48), `AMBIENTE_NOCHE` (0.12, 0.16, 0.24) |
| Intensidad y dirección del sol | `iluminacion.frag` | `INTENSIDAD_SOL_DIA` (0.65), `INTENSIDAD_SOL_NOCHE` (0.10), `DIRECCION_SOL` (0.4, 1.0, 0.3) |
| Alcance de las farolas | `iluminacion.frag` | `FAROLA_ATENUACION_LINEAL` (0.12), `FAROLA_ATENUACION_CUADRATICA` (0.045); más chico = más alcance |
| Color y fuerza de las farolas | `iluminacion.frag` | `COLOR_FAROLA` (1.0, 0.73, 0.34), `INTENSIDAD_FAROLA` (3.0) |
| Tamaño del círculo de luz de las farolas | `iluminacion.frag` | `ANGULO_FAROLA_INTERIOR` (35°, luz plena), `ANGULO_FAROLA_EXTERIOR` (60°, fin del borde suave); en grados desde la vertical. Más grande = círculo más ancho; el exterior debe quedar por debajo de 90° o la luz volvería a subir por las paredes. `FarolasTest` verifica que siga cubriendo vereda y calzada |
| Dirección del foco de las farolas | `iluminacion.frag` | `DIRECCION_FAROLA` (0, −1, 0): recto hacia abajo |
| Luz de las luminarias globo (puntual) | `iluminacion.frag` | `COLOR_GLOBO` (1.0, 0.86, 0.62), `INTENSIDAD_GLOBO` (2.2), `GLOBO_ATENUACION_LINEAL` (0.25), `GLOBO_ATENUACION_CUADRATICA` (0.10): más chico = más alcance. `MAX_GLOBOS` (16) debe coincidir con `Iluminacion.MAX_GLOBOS` |
| Ángulo del cono de los faros | `iluminacion.frag` | `CONO_BORDE` (0.85 ≈ 32°), `CONO_CENTRO` (0.97 ≈ 14°); son cosenos, así que más cerca de 1 = cono más angosto |
| Alcance y fuerza de los faros | `iluminacion.frag` | `FAROS_ATENUACION_CUADRATICA` (0.04), `INTENSIDAD_FARO` (8.0), `COLOR_FARO` (1.0, 0.94, 0.72) |
| Posición e inclinación de los faros | `iluminacion.frag` | `FAROS_SEPARACION` (0.55), `FAROS_AVANCE` (1.36), `FAROS_INCLINACION` (0.10) |
| Campo visual de la cámara | `ciudad.vert` | `CAMPO_VISUAL` (55°) |
| Distancia de dibujo | `ciudad.vert` y `motor/Camara.java` | `PLANO_CERCANO` (0.1, en el shader). El plano lejano ya no es un número fijo: `Camara.getPlanoLejano()` = distancia aérea máxima + diagonal de la ciudad + `MARGEN_PLANO_LEJANO` (20) ≈ 319 con límite 55, y llega al shader como `uPlanoLejano`. `CamaraTest` lo verifica |
| Orden de alturas en el minimapa | `ciudad.vert` | `ESCALA_ALTURA_MAPA` (100) |
| Reparto del degradado del cielo | `iluminacion.frag` | `CURVA_CIELO` (0.6): exponente de la altura; más chico = el azul intenso baja más cerca del horizonte |

## 9. Guion de demostración

Sigue el orden del enunciado. Antes de empezar, conviene tener la ventana en tamaño normal (1100 × 760).

| Paso | Acción | Qué se ve |
|---|---|---|
| 0 | `mvn compile exec:exec` y **ENTER** | Menú de inicio con el título; al presionar ENTER, la partida arranca de día, con la cámara de seguimiento, el minimapa arriba a la derecha y el HUD arriba a la izquierda |
| 1 | **W** para acelerar, **A / D** para girar, **S** para frenar y retroceder, **Espacio** para el freno fuerte | El auto acelera y dobla. Velocidad en el título y en el HUD. El tráfico circula por su carril |
| 2 | Rozar una manzana en diagonal, chocarla de frente, y ponerse adelante de un vehículo del tráfico (en su carril y después en el contrario) | En diagonal el auto desliza a lo largo de la pared; de frente se detiene. El vehículo frena de a poco, con luces de freno, solo si el auto está en su mismo carril; por el carril contrario pasa de largo |
| 3 | **C** (tres veces) | Cámara orbital del auto (arrastrar con el mouse para girar y elevar, ruedita para acercar); después la vista aérea de toda la ciudad, rodeada de campo con árboles y con sombras suaves bajo edificios, árboles y autos, con la flecha cian sobre el auto: arrastrar con el botón izquierdo para girar alrededor de la ciudad, ruedita para acercar y botón derecho para recorrerla; al final vuelve la cámara de seguimiento |
| 3b | En la cámara orbital (**C** una vez), girar hasta ver el auto de costado y manejar: **W**, **A/D**, **S** y **Espacio** | Las ruedas giran (la marca roja da vueltas) y las delanteras doblan con A/D. Mirando la cola del auto: al frenar se encienden las luces de freno y al ir marcha atrás, la luz blanca de reversa. Funciona de día y de noche. Los autos del tráfico tienen las mismas ruedas: giran mientras avanzan, se quedan quietas cuando frenan hasta detenerse y las delanteras doblan en las esquinas |
| 4 | **N** | Noche: cielo azul oscuro con estrellas y luna (se ven con la orbital baja), farolas, ventanas encendidas (≈65 %, tonos variados), vidrieras iluminadas, luces del tráfico con sus focos sobre la calle. El HUD y el título muestran "Noche" |
| 5 | **F** (y otra vez **F**) | Se apagan y encienden los faros del jugador: los conos sobre la calle, las bombillas delanteras (blancas y emisivas) y las luces de posición traseras. Frenando con S o Espacio, las traseras pasan a rojo intenso aunque F esté apagado. Las luces del tráfico no cambian |
| 6 | **M** (y otra vez **M**) | Se oculta y vuelve el minimapa, con la ciudad completa, el norte arriba, los sectores con nombre, el auto (cian con punta blanca) y el destino dorado |
| 7 | Redimensionar la ventana (achicar y agrandar) | La escena mantiene sus proporciones, el minimapa sigue en la esquina superior derecha y el HUD se adapta |
| 8 | Completar las 3 entregas: frenar sobre cada marca dorada | Recorrido sugerido. **1:** ir al norte por el borde oeste, y al este por el borde norte hasta la esquina noreste. **2:** volver al oeste por el borde norte, y al sur por el borde oeste hasta Z = -10. **3:** seguir al sur hasta el borde sur, y al este hasta X = 30. El título y el HUD muestran "Entregas: X/3" y el destino; al final aparece GANASTE con el tiempo |
| 9 | **R** y nuevo recorrido | Auto en la salida, entregas en 0/3, cronómetro en 0 y tráfico al inicio de sus rutas; se puede volver a jugar |
| Extra | **H** (ayuda) y **P** (pausa) | Lista de controles en pantalla; en pausa todo se congela y aparece "PAUSA" |

## 10. Capturas

Las capturas van en `docs/capturas/`. Si todavía no se tomaron, las imágenes de abajo aparecen como enlaces rotos.

| Captura | Qué mostrar |
|---|---|
| ![De día](docs/capturas/dia.png) | `dia.png`: cámara de seguimiento de día, con edificios de distintos tipos (una torre, una casa baja), toldos, tráfico y HUD |
| ![De noche](docs/capturas/noche.png) | `noche.png`: la misma zona de noche, con farolas, ventanas encendidas y luces del tráfico |
| ![Minimapa](docs/capturas/minimapa.png) | `minimapa.png`: minimapa con sectores, auto y destino (puede ser un recorte de la esquina superior derecha) |
| ![Parque](docs/capturas/parque.png) | `parque.png`: un parque con fuente, senderos, árboles y bancos |
