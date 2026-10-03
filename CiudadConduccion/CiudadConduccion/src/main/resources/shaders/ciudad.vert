#version 330 core // Selecciona GLSL 3.30 para el contexto OpenGL 3.3.
// SHADER DE VÉRTICES: transforma vértices del cubo al mundo y después a la pantalla.
// ==================== SHADERS: CÓDIGO QUE EJECUTA LA GPU ====================
// Lo carga Shader desde el classpath (/shaders/ciudad.vert) y lo usan todas las cajas que dibuja Cubo.
// Coordenadas: X = izquierda/derecha; Y = altura; Z = profundidad.
layout (location = 0) in vec3 aPos; // Lee la posición local del vértice desde el VBO.
layout (location = 1) in vec3 aNormal; // Lee la normal de la cara desde el mismo VBO.
uniform vec3 uPos; // Recibe el centro de la caja en la ciudad.
uniform vec3 uEscala; // Recibe el tamaño de la caja en cada eje.
uniform vec3 uOjo; // Recibe la posición de la cámara.
uniform vec3 uObjetivo; // Recibe el punto que observa la cámara.
uniform float uGiro; // Recibe el giro del objeto alrededor de Y.
uniform mat3 uRotacion; // Rotación extra, antes del giro en Y (identidad para casi todo; las ruedas giran sobre su eje con ella).
uniform float uAspecto; // Recibe la relación ancho/alto de la imagen.
uniform int uMapa; // Selecciona perspectiva (0) o vista superior ortográfica (1).
uniform float uPlanoLejano; // Distancia máxima visible: la calcula Camara.getPlanoLejano() a partir de Mapa.LIMITE.
uniform float uMitadMapa; // Media anchura visible del minimapa: Mapa.LIMITE más un margen (antes fijo en 37).
uniform float uCampoVisual; // Campo visual vertical de la cámara, en grados: lo elige Camara según el modo (menor en AEREA).
uniform int uSombraPlanar; // 1 mientras Sombras dibuja la sombra planar del sol (Sombras.dibujarPlanares, solo de día).
uniform vec3 uDireccionSol; // Hacia dónde está el sol de día (Sombras.DIRECCION_SOL_DIA, igual que iluminacion.frag).
uniform float uAlturaPlanoSombra; // Altura del piso al que se proyecta: calle (0), acera o césped del parque (Sombras.dibujarPlanares).
out vec3 vMundo; // Envía la posición mundial al shader de fragmentos.
out vec3 vNormal; // Envía la normal transformada para la iluminación de iluminacion.frag.
out vec3 vLocal; // Posición del vértice dentro de la figura unitaria (-0.5 a 0.5): la usa la sombra falsa para su degradado.

// ==================== CONSTANTES DE PROYECCIÓN (valores ajustables) ====================
const float PLANO_CERCANO = 0.1; // Distancia mínima visible; lo que esté más cerca de la cámara se recorta.
const float ESCALA_ALTURA_MAPA = 100.0; // En el minimapa, divide la altura para ordenar la profundidad (lo alto tapa lo bajo).
const float ELEVACION_SOMBRA_PLANAR = 0.01; // La sombra planar queda apenas sobre el suelo: evita z-fighting con él.

void main() { // OpenGL ejecuta este bloque una vez por vértice.
    float coseno = cos(uGiro); // Calcula el coseno del giro del objeto.
    float seno = sin(uGiro); // Calcula el seno del mismo giro.
    mat3 giro = mat3( // Construye la matriz de rotación; GLSL recibe sus columnas.
        coseno, 0.0, -seno, // Primera columna: dirección del eje X rotado.
        0.0, 1.0, 0.0, // Segunda columna: Y permanece vertical.
        seno, 0.0, coseno // Tercera columna: dirección del eje Z rotado.
    ); // Completa la matriz de tres filas y tres columnas.
    // Matriz de modelo completa: primero escala, después uRotacion (cualquier eje), luego el giro en Y y al final la traslación.
    vMundo = giro * (uRotacion * (aPos * uEscala)) + uPos; // Escala, gira y traslada el vértice al mundo.
    // Las rotaciones son ortonormales (su inversa transpuesta es ella misma): solo la escala necesita invertirse.
    vNormal = normalize(giro * (uRotacion * (aNormal / uEscala))); // Corrige la normal con la inversa transpuesta de escala y giro.
    vLocal = aPos; // Sin escala ni giro: el centro de la figura es (0, 0, 0) y sus bordes están a ±0.5.

    if (uMapa == 1) { // Esta rama se usa al dibujar el minimapa en Minimapa.
        float pantallaX = vMundo.x / uMitadMapa; // Ajusta el ancho del mundo al intervalo visible -1 a 1.
        float pantallaY = -vMundo.z / uMitadMapa; // Coloca el norte (-Z) en la parte superior del mapa.
        float profundidad = -vMundo.y / ESCALA_ALTURA_MAPA; // Hace que los objetos más altos se vean por encima.
        gl_Position = vec4(pantallaX, pantallaY, profundidad, 1.0); // Proyecta sin reducir objetos lejanos.
    } else { // La escena principal utiliza una cámara con perspectiva.
        if (uSombraPlanar == 1) { // Sombra planar: aplasta el vértice hacia el piso que le toque (calle, acera o parque) siguiendo al sol.
            float t = (vMundo.y - uAlturaPlanoSombra) / uDireccionSol.y; // Cuánto hay que avanzar hacia el sol para tocar ESE piso (uDireccionSol.y > 0: nunca bajo el horizonte).
            vMundo = vec3(vMundo.x - uDireccionSol.x * t, uAlturaPlanoSombra + ELEVACION_SOMBRA_PLANAR, vMundo.z - uDireccionSol.z * t); // Mismo punto, proyectado sobre ese piso.
        }
        vec3 frente = normalize(uObjetivo - uOjo); // Calcula la dirección hacia la que mira la cámara.
        vec3 derecha = normalize(cross(frente, vec3(0.0, 1.0, 0.0))); // Obtiene el eje horizontal de la cámara.
        vec3 arriba = cross(derecha, frente); // Obtiene el eje vertical perpendicular a los otros dos.
        vec3 diferencia = vMundo - uOjo; // Traslada el origen del mundo hasta la cámara.
        float vistaX = dot(diferencia, derecha); // Mide cuánto está el vértice a la derecha de la cámara.
        float vistaY = dot(diferencia, arriba); // Mide cuánto está el vértice encima de la cámara.
        float vistaZ = -dot(diferencia, frente); // Usa Z negativa delante de la cámara, como espera OpenGL.
        float factor = 1.0 / tan(radians(uCampoVisual) * 0.5); // Convierte el campo visual (Camara.CAMPO_VISUAL/_AEREO) en escala de perspectiva.
        float cerca = PLANO_CERCANO; // Define la distancia mínima visible.
        float lejos = uPlanoLejano; // Define la distancia máxima visible (≈ 319 con la ciudad 11 × 11).
        float clipX = vistaX * factor / uAspecto; // Corrige la coordenada horizontal según el ancho de la pantalla.
        float clipY = vistaY * factor; // Aplica el campo visual a la coordenada vertical.
        float clipZ = (lejos + cerca) / (cerca - lejos) * vistaZ; // Calcula el término de profundidad dependiente de Z.
        clipZ += 2.0 * lejos * cerca / (cerca - lejos); // Añade el término constante de profundidad.
        gl_Position = vec4(clipX, clipY, clipZ, -vistaZ); // OpenGL dividirá XYZ por W para producir perspectiva.
    }
}
