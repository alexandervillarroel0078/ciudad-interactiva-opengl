#version 330 core // Selecciona la versión GLSL correspondiente a OpenGL 3.3.
// SHADER DE FRAGMENTOS CON ILUMINACIÓN: calcula el color iluminado de cada fragmento.
// Reemplaza al shader de color plano (plano.frag). Los uniforms de luz los envía Iluminacion.preparar().
// El shader calcula iluminación local, sin sombras reales: las "sombras falsas" de Sombras son manchas oscuras con mezcla (rama uSombra).
in vec3 vMundo; // Recibe la posición del fragmento en la ciudad.
in vec3 vNormal; // Recibe la dirección perpendicular a la superficie.
in vec3 vLocal; // Posición dentro de la figura unitaria (-0.5 a 0.5), para el degradado de las sombras falsas.
uniform vec3 uColor; // Recibe el color base de la caja.
uniform vec3 uLuces[16]; // Recibe las posiciones de las farolas; 16 es el máximo (Iluminacion.MAX_LUCES).
uniform int uNumLuces; // Recibe cuántas posiciones de uLuces están en uso (Iluminacion.LUCES.length).
uniform vec3 uAuto; // Recibe la posición del auto a la altura de los faros.
uniform vec3 uFrente; // Recibe la dirección hacia la que apunta el vehículo.
uniform float uFactorNoche; // 0 = día, 1 = noche, con valores intermedios durante la transición (Iluminacion.factorNoche, el mismo del cielo).
uniform int uFaros; // Vale 1 cuando los focos están encendidos.
uniform int uEmision; // Vale 1 si el objeto debe conservar su color sin oscurecerse.
uniform int uMapa; // Vale 1 durante el dibujo del minimapa de Minimapa.
uniform vec3 uOjo; // Posición de la cámara (la misma que recibe ciudad.vert): origen de la distancia para la niebla.
uniform float uDensidadNiebla; // Niebla exponencial: 0 = sin niebla; Iluminacion la cambia con las teclas + y -.
uniform vec3 uColorNiebla; // Color de la niebla: el del horizonte del cielo (Iluminacion.colorNiebla).
uniform float uAlfa; // Opacidad: 1 normalmente. La bajan Sombras (centro de cada mancha) y Cubo.cajaTranslucida().
uniform float uFactorSombraSol; // 1 = sol pleno; más bajo mientras el auto está bajo la sombra de un edificio (Sombras, solo de día).
uniform int uSombra; // Vale 1 mientras Sombras dibuja las manchas oscuras bajo los objetos.
uniform float uFormaSombra; // Curva de la mancha: 2 = elipse, 4 = rectángulo de esquinas redondeadas.
uniform float uNucleoSombra; // Hasta dónde la mancha es pareja (0 = centro, 1 = borde); de ahí al borde se difumina.
uniform int uSombraPlanar; // Vale 1 mientras Sombras dibuja la sombra planar del sol (Sombras.dibujarPlanares).
uniform vec3 uColorSombraPlanar; // Color fijo de esa sombra: ignora uColor, porque reutiliza objetos con su color real.
// HUELLA: restringe en qué (x, z) puede caer la copia planar que se está dibujando, para que cada pase (calle, acera
// o césped) solo pinte donde el piso real está a esa altura; sin esto, una copia más alta "flota" sobre un piso más
// bajo que queda más cerca de la cámara (gana la prueba de profundidad aunque no sea su piso). Ver Sombras.aplicarHuella.
uniform int uHuellaModo; // 0 = sin filtro; 1 = DENTRO del rectángulo exterior (y fuera del interior, si hay); 2 = FUERA del exterior.
uniform vec2 uHuellaMin; // Esquina (x, z) mínima del rectángulo exterior.
uniform vec2 uHuellaMax; // Esquina (x, z) máxima del rectángulo exterior.
uniform vec2 uHuellaExcluirMin; // Esquina mínima de un "agujero" interior (modo 1): p. ej. el césped dentro de una manzana de parque.
uniform vec2 uHuellaExcluirMax; // Esquina máxima de ese agujero interior; con min > max no excluye nada.
out vec4 color; // Entrega el color RGBA final al framebuffer.

// ==================== CONSTANTES DE ILUMINACIÓN (valores ajustables) ====================
// Ambiente y sol.
const vec3 AMBIENTE_DIA = vec3(0.48); // Luz ambiental diurna que llega a todas las caras (gris neutro).
const vec3 AMBIENTE_NOCHE = vec3(0.12, 0.16, 0.24); // Luz ambiental nocturna: tenue y azulada.
const float INTENSIDAD_SOL_DIA = 0.65; // Fuerza de la luz direccional del sol durante el día.
const float INTENSIDAD_SOL_NOCHE = 0.10; // Luz direccional residual de noche (luna).
// DIRECCION_SOL_DIA: elevación ≈ 64° sobre el horizonte (y=1.0, horizontal=0.5 → atan(1/0.5)≈63.7°), con un
// ángulo lateral leve (atan2(0.12,0.48)≈14°, algo al este y al sur) para que las paredes no queden planas.
const vec3 DIRECCION_SOL_DIA = vec3(0.12, 1.0, 0.48); // Hacia dónde está el sol de día (se normaliza al usarla).
const vec3 DIRECCION_SOL_NOCHE = vec3(0.4, 1.0, 0.3); // Luna: sin cambios respecto del valor original.
// Farolas: atenuación = 1 + LINEAL · d + CUADRATICA · d². Valores más chicos = mayor alcance.
const float FAROLA_ATENUACION_LINEAL = 0.12; // Término lineal de la atenuación de cada farola.
const float FAROLA_ATENUACION_CUADRATICA = 0.045; // Término cuadrático: domina lejos y define el radio útil de la luz.
const vec3 COLOR_FAROLA = vec3(1.0, 0.73, 0.34); // Tono cálido (sodio) de las farolas.
const float INTENSIDAD_FAROLA = 3.0; // Multiplicador del aporte de cada farola.
// Farolas como FOCO hacia abajo. Ángulos medidos desde la vertical que baja de la bombilla, en grados.
// Con la bombilla a 4.5 de altura, 35° dan un círculo pleno de ≈ 3.2 de radio en el suelo (toda la vereda)
// y 60° un borde de ≈ 7.8 (la luz se desvanece pasando el centro de la calzada).
const vec3 DIRECCION_FAROLA = vec3(0.0, -1.0, 0.0); // Eje del foco: recto hacia el suelo, donde la pantalla deja salir la luz.
const float ANGULO_FAROLA_INTERIOR = 35.0; // Dentro de este ángulo la luz es plena.
const float ANGULO_FAROLA_EXTERIOR = 60.0; // Fuera de este ángulo no hay luz; entre ambos, borde suave (smoothstep).
// Faros del auto: posición, cono y alcance.
const float FAROS_SEPARACION = 0.55; // Distancia lateral de cada faro al centro del auto.
const float FAROS_AVANCE = 1.36; // Distancia del centro del auto al frente, donde nacen los haces.
const float FAROS_INCLINACION = 0.10; // Cuánto se inclinan los haces hacia el suelo.
const float CONO_BORDE = 0.85; // Coseno del borde exterior del haz (≈ 32° desde el eje): fuera de aquí no hay luz.
const float CONO_CENTRO = 0.97; // Coseno del núcleo del haz (≈ 14°): dentro de aquí la luz es plena.
const float FAROS_ATENUACION_CUADRATICA = 0.04; // Alcance de los faros: atenuación = 1 + 0.04 · d².
const vec3 COLOR_FARO = vec3(1.0, 0.94, 0.72); // Luz frontal blanca y cálida.
const float INTENSIDAD_FARO = 8.0; // Multiplicador del aporte de cada faro.
// Con atenuación = 1 cerca del origen del foco (d≈0), el aporte tiende a la intensidad completa y "quema" las
// superficies muy próximas (p. ej. el auto pegado a una pared). FRACCION_TOPE_FOCO limita cada foco a la mitad de
// su intensidad: ese es justo el valor que ya se alcanza a la distancia de referencia (atenuación = 2, ≈5 unidades
// para el faro del auto), así que a media y larga distancia el resultado no cambia; solo se achata el pico cercano.
const float FRACCION_TOPE_FOCO = 0.5; // Tope de cada foco = intensidad · esta fracción.
// Faros del tráfico: mismo cono que los del jugador, pero más débiles y de menor alcance.
const int MAX_FAROS_TRAFICO = 16; // Tamaño de los arreglos de focos de tráfico; debe coincidir con Trafico.MAX_FAROS_TRAFICO.
const float INTENSIDAD_FARO_TRAFICO = 4.0; // Multiplicador de cada foco de tráfico: la mitad que el faro del jugador.
const float ALCANCE_FARO_TRAFICO = 4.0; // Distancia a la que un foco de tráfico rinde la mitad (el del jugador ≈ 5).

uniform vec3 uFarosTrafico[MAX_FAROS_TRAFICO]; // Posición de cada foco de tráfico (dos por vehículo), enviada por Trafico.
uniform vec3 uDireccionFarosTrafico[MAX_FAROS_TRAFICO]; // Dirección de avance del vehículo dueño de cada foco.
uniform int uNumFarosTrafico; // Cuántos focos están en uso; de día vale 0 y el bucle no calcula nada.

// Luminarias GLOBO de los parques: LUZ PUNTUAL. Arreglo propio (no comparten uLuces con las farolas de calle): cada
// tipo de luz tiene su fórmula y el bucle no necesita preguntar "¿esta es foco o puntual?" en cada iteración.
const int MAX_GLOBOS = 16; // Tamaño de uGlobos; debe coincidir con Iluminacion.MAX_GLOBOS.
const vec3 COLOR_GLOBO = vec3(1.0, 0.86, 0.62); // Blanco cálido, más claro que el sodio de las farolas de calle.
const float INTENSIDAD_GLOBO = 2.2; // Menor que la farola: es una luz peatonal, baja y cercana.
const float GLOBO_ATENUACION_LINEAL = 0.25; // Atenuación = 1 + LINEAL · d + CUADRATICA · d²: más fuerte que la farola,
const float GLOBO_ATENUACION_CUADRATICA = 0.10; // así la luz queda en el parque (radio útil ≈ 5) y no invade la calle.
uniform vec3 uGlobos[MAX_GLOBOS]; // Centro de cada globo (Parque.LUMINARIAS), enviado por Iluminacion.preparar().
uniform int uNumGlobos; // Cuántos globos están en uso.

// ==================== NIEBLA ====================
// Visibilidad = exp(-distancia · densidad): 1 pegado a la cámara y cada vez menos lejos. El color final es
// mix(uColorNiebla, color, visibilidad): con visibilidad 1 es el color real y con 0 es solo niebla.
// No hay niebla en el minimapa (vista ortográfica: la distancia no significa nada). El cielo y el HUD tienen shader propio.
float visibilidadNiebla() {
    if (uMapa == 1) {
        return 1.0;
    }
    return exp(-length(vMundo - uOjo) * uDensidadNiebla);
}

vec4 conNiebla(vec3 colorFinal, float alfa) {
    return vec4(mix(uColorNiebla, colorFinal, visibilidadNiebla()), alfa);
}

// ==================== LUZ PUNTUAL vs. FOCO ====================
// Luz PUNTUAL (omnidireccional): emite igual hacia todos lados; solo importan la distancia (atenuación) y
// cuánto mira la cara hacia la luz (difusa). Ilumina también lo que está por encima o al costado de la fuente.
// FOCO (cono): además tiene un EJE. Se mide el ángulo entre el eje y la dirección luz → fragmento; con el producto
// punto de dos vectores unitarios se obtiene el coseno de ese ángulo. Si el ángulo supera el borde exterior, no llega luz.
// La farola tiene una pantalla que la tapa por arriba: una luz puntual iluminaría las paredes por encima de la pantalla,
// algo imposible en la realidad. Por eso la farola es un foco apuntando hacia abajo, igual que los faros del auto.
// La luminaria GLOBO de los parques, en cambio, es una esfera de vidrio sin pantalla: la luz sale hacia todos lados
// (también hacia arriba, e ilumina las copas de los árboles desde abajo). Por eso es una luz PUNTUAL: sin cono.

// ==================== FUNCIÓN DE CONO (factor 0..1 con borde suave) ====================
// Devuelve 1 dentro del cono interior, 0 fuera del exterior y una transición suave entre ambos.
// La usan los faros (vía aporteFoco) y las farolas, así el cálculo del cono existe una sola vez.
float factorCono(vec3 haciaSuperficie, vec3 eje, float cosenoExterior, float cosenoInterior) {
    float alineacion = dot(normalize(haciaSuperficie), eje); // Coseno del ángulo al eje: 1 en el centro del haz, negativo detrás del foco.
    return smoothstep(cosenoExterior, cosenoInterior, alineacion); // Ángulo mayor = coseno menor: el exterior es el límite inferior.
}

// ==================== FOCO DE LOS FAROS (cono con smoothstep y atenuación) ====================
// La usan los faros del jugador y los del tráfico.
vec3 aporteFoco(vec3 origen, vec3 frente, vec3 normal, float atenuacionCuadratica, float intensidad) {
    vec3 haciaSuperficie = vMundo - origen; // Forma el vector del faro al fragmento.
    float distancia = length(haciaSuperficie); // Mide la distancia recorrida por la luz.
    vec3 eje = normalize(frente + vec3(0.0, -FAROS_INCLINACION, 0.0)); // Inclina el foco ligeramente hacia el suelo.
    float cono = factorCono(haciaSuperficie, eje, CONO_BORDE, CONO_CENTRO); // Suaviza el borde entre el exterior y el interior del foco.
    float difusa = max(dot(normal, -normalize(haciaSuperficie)), 0.0); // Mide cuánto mira la cara hacia el faro.
    float atenuacion = 1.0 + atenuacionCuadratica * distancia * distancia; // Disminuye la intensidad al alejarse.
    vec3 colorFaro = COLOR_FARO; // Define una luz frontal blanca y cálida.
    float factor = cono * difusa * intensidad / atenuacion; // Aporte sin limitar: cerca del origen tiende a "intensidad".
    float tope = intensidad * FRACCION_TOPE_FOCO; // Nunca más brillante que a la distancia de referencia: evita quemar superficies muy cercanas.
    return colorFaro * min(factor, tope); // Devuelve el aporte del foco a la iluminación total, ya acotado.
}

// ==================== CÁLCULO DE LUZ EN LA GPU ====================
void main() { // Se ejecuta para cada fragmento visible de una caja.
    if (uSombra == 1) { // Sombra falsa (Sombras.dibujar): mancha con bordes difusos.
        vec2 p = abs(vLocal.xz) * 2.0; // Posición en la base de la caja: 0 en el centro, 1 en cada borde.
        // "Distancia" generalizada: con exponente 2 es la del círculo (Pitágoras) y la mancha es una elipse;
        // con 4 las curvas de nivel se parecen a un cuadrado de esquinas redondeadas.
        float d = pow(pow(p.x, uFormaSombra) + pow(p.y, uFormaSombra), 1.0 / uFormaSombra);
        float mancha = 1.0 - smoothstep(uNucleoSombra, 1.0, d); // 1 en el núcleo, baja suave y vale 0 en el borde.
        color = vec4(uColor, uAlfa * mancha * visibilidadNiebla()); // Alfa variable: el blending oscurece más el centro que el borde.
        return; // La sombra no se ilumina.
    }

    if (uSombraPlanar == 1) { // Sombra planar (Sombras.dibujarPlanares): reutiliza edificios, árboles, farolas y autos
        if (uHuellaModo != 0) { // Restringe esta copia a la porción de piso que le corresponde (ver uHuellaModo).
            bool dentroExterior = vMundo.x >= uHuellaMin.x && vMundo.x <= uHuellaMax.x
                && vMundo.z >= uHuellaMin.y && vMundo.z <= uHuellaMax.y;
            bool dentroAgujero = vMundo.x >= uHuellaExcluirMin.x && vMundo.x <= uHuellaExcluirMax.x
                && vMundo.z >= uHuellaExcluirMin.y && vMundo.z <= uHuellaExcluirMax.y;
            if (uHuellaModo == 1 && (!dentroExterior || dentroAgujero)) { // DENTRO del exterior y fuera del agujero.
                discard; // Esta porción cae fuera de la huella: la pinta otro pase, o ninguno (es calle).
            }
            if (uHuellaModo == 2 && dentroExterior) { // FUERA del exterior (pase de calle).
                discard; // Esta porción cae sobre una acera o un parque: la pinta su propio pase.
            }
        }
        color = vec4(uColorSombraPlanar, uAlfa * visibilidadNiebla()); // ya aplastados por ciudad.vert; ignora uColor, el color real de cada uno.
        return; // Sin iluminación ni degradado: un color plano, parejo en toda la silueta proyectada.
    }

    if (uEmision == 1 || uMapa == 1) { // Bombillas y minimapa usan colores directos.
        color = conNiebla(uColor, uAlfa); // Conserva el color base (con niebla); uAlfa < 1 solo en cajas translúcidas (con mezcla activada).
        return; // Termina el shader sin calcular iluminación.
    }

    vec3 normal = normalize(vNormal); // Convierte la normal interpolada en un vector unitario.
    // Día y noche se calculan completos y se mezclan con uFactorNoche: mix(a, b, t) = a · (1 - t) + b · t.
    vec3 luz = mix(AMBIENTE_DIA, AMBIENTE_NOCHE, uFactorNoche); // Ambiente: gris neutro de día, azul tenue de noche.
    float incidenciaDia = max(dot(normal, normalize(DIRECCION_SOL_DIA)), 0.0); // Lambert con el sol.
    float incidenciaNoche = max(dot(normal, normalize(DIRECCION_SOL_NOCHE)), 0.0); // Lambert con la luna.
    // uFactorSombraSol solo baja la directiva del sol (no el ambiente) y no afecta a la luna.
    float solDia = INTENSIDAD_SOL_DIA * incidenciaDia * uFactorSombraSol;
    float solNoche = INTENSIDAD_SOL_NOCHE * incidenciaNoche;
    luz += vec3(mix(solDia, solNoche, uFactorNoche)); // Contribución direccional.

    if (uFactorNoche > 0.0) { // Farolas y globos: se encienden con la noche (de día el bucle no corre).
        float cosenoFarolaExterior = cos(radians(ANGULO_FAROLA_EXTERIOR)); // El cono se compara con cosenos, no con grados.
        float cosenoFarolaInterior = cos(radians(ANGULO_FAROLA_INTERIOR));
        for (int indice = 0; indice < uNumLuces; indice++) { // Acumula el aporte de cada bombilla.
            vec3 haciaLuz = uLuces[indice] - vMundo; // Forma el vector desde la superficie hacia la farola.
            float distancia = length(haciaLuz); // Mide cuántas unidades separan superficie y bombilla.
            // Foco hacia abajo: lo que está por encima de la bombilla queda detrás del eje (coseno negativo) y recibe 0.
            float cono = factorCono(-haciaLuz, DIRECCION_FAROLA, cosenoFarolaExterior, cosenoFarolaInterior);
            float difusa = max(dot(normal, normalize(haciaLuz)), 0.0); // Calcula la incidencia de la luz sobre la cara.
            float atenuacion = 1.0 + FAROLA_ATENUACION_LINEAL * distancia + FAROLA_ATENUACION_CUADRATICA * distancia * distancia; // Reduce el alcance con la distancia.
            vec3 colorFarola = COLOR_FAROLA; // Define el tono cálido de la farola.
            float factorFarola = cono * difusa * INTENSIDAD_FAROLA / atenuacion; // Aporte sin limitar.
            float topeFarola = INTENSIDAD_FAROLA * FRACCION_TOPE_FOCO; // Mismo criterio que los faros: evita quemar una fachada muy pegada al poste.
            luz += colorFarola * min(factorFarola, topeFarola) * uFactorNoche; // Suma el aporte atenuado de esta bombilla, solo dentro del cono, a medida que anochece.
        }
        for (int indice = 0; indice < uNumGlobos; indice++) { // Luminarias globo: luz PUNTUAL, sin factor de cono.
            vec3 haciaLuz = uGlobos[indice] - vMundo; // De la superficie al globo.
            float distancia = length(haciaLuz); // Distancia recorrida por la luz.
            float difusa = max(dot(normal, normalize(haciaLuz)), 0.0); // Lambert: solo importa hacia dónde mira la cara.
            float atenuacion = 1.0 + GLOBO_ATENUACION_LINEAL * distancia + GLOBO_ATENUACION_CUADRATICA * distancia * distancia;
            luz += COLOR_GLOBO * difusa * INTENSIDAD_GLOBO / atenuacion * uFactorNoche; // Igual hacia todos lados: no hay pantalla.
        }
    }

    if (uFaros == 1) { // Calcula los conos únicamente si están encendidos.
        for (int indice = 0; indice < 2; indice++) { // Repite el cálculo para los dos faros.
            vec3 lateral = vec3(-uFrente.z, 0.0, uFrente.x); // Obtiene la dirección hacia el lado derecho del auto.
            float separacion = -FAROS_SEPARACION; // Selecciona inicialmente el faro izquierdo.
            if (indice == 1) { // Comprueba si corresponde calcular el segundo faro.
                separacion = FAROS_SEPARACION; // Desplaza el segundo faro al lado derecho.
            }
            vec3 origen = uAuto + uFrente * FAROS_AVANCE + lateral * separacion; // Ubica el faro delante de la carrocería.
            luz += aporteFoco(origen, uFrente, normal, FAROS_ATENUACION_CUADRATICA, INTENSIDAD_FARO); // Añade el aporte del foco a la iluminación total.
        }
    }

    // Faros del tráfico: independientes de la tecla F. Trafico envía uNumFarosTrafico = 0 de día.
    float atenuacionTrafico = 1.0 / (ALCANCE_FARO_TRAFICO * ALCANCE_FARO_TRAFICO); // Con d = alcance, 1 + d²/alcance² = 2: rinde la mitad.
    for (int indice = 0; indice < uNumFarosTrafico; indice++) { // Recorre solo los focos en uso.
        luz += aporteFoco(uFarosTrafico[indice], uDireccionFarosTrafico[indice], normal, atenuacionTrafico, INTENSIDAD_FARO_TRAFICO); // Mismo cono, más débil y corto.
    }

    // Con el sol más alto, el ambiente (0.48) más la directiva (0.65) pueden superar 1.0 sobre paredes claras y
    // "quemarlas" (blanco plano, sin detalle): se recorta el resultado final a 0..1 antes de mostrarlo.
    color = conNiebla(clamp(uColor * luz, 0.0, 1.0), 1.0); // Multiplica el material por toda la luz acumulada y lo mezcla con la niebla.
}
