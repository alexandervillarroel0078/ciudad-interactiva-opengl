#version 330 core // Selecciona GLSL 3.30 para el contexto OpenGL 3.3.
// SHADER DE VÉRTICES DE LAS MARCAS DE FRENADO: coloca un rectángulo plano (Malla) sobre el asfalto, escalado, girado en Y
// y trasladado como las demás figuras, y lo proyecta con la misma cámara que ciudad.vert. Lo carga MarcasFrenado.
layout (location = 0) in vec3 aPos; // Esquina del rectángulo: X y Z entre -0.5 y 0.5, Y = 0.
layout (location = 1) in vec3 aNormal; // Malla la entrega; este shader no la usa.
uniform vec3 uPos; // Centro del rectángulo en el mundo (Y = un poco sobre el asfalto).
uniform vec3 uEscala; // Ancho (x) y largo (z) de la marca.
uniform float uGiro; // Giro alrededor de Y: alinea el largo con la dirección del auto.
uniform vec3 uOjo; // Posición de la cámara (la envía Camara.configurar).
uniform vec3 uObjetivo; // Punto que observa la cámara.
uniform float uAspecto; // Relación ancho/alto de la ventana: se envía en cada cuadro, así sigue el redimensionado.
uniform float uPlanoLejano; // Distancia máxima visible.
uniform float uCampoVisual; // Campo visual vertical, en grados.
out vec2 vLocal; // Posición dentro del rectángulo, para suavizar los bordes.

const float PLANO_CERCANO = 0.1; // Igual que ciudad.vert.

void main() {
    vLocal = aPos.xz; // Ancho en x, largo en y.
    float coseno = cos(uGiro);
    float seno = sin(uGiro);
    mat3 giro = mat3(coseno, 0.0, -seno, 0.0, 1.0, 0.0, seno, 0.0, coseno); // Igual que ciudad.vert.
    vec3 mundo = giro * (aPos * uEscala) + uPos;
    vec3 frente = normalize(uObjetivo - uOjo);
    vec3 derecha = normalize(cross(frente, vec3(0.0, 1.0, 0.0)));
    vec3 arriba = cross(derecha, frente);
    vec3 diferencia = mundo - uOjo;
    float vistaX = dot(diferencia, derecha);
    float vistaY = dot(diferencia, arriba);
    float vistaZ = -dot(diferencia, frente);
    float factor = 1.0 / tan(radians(uCampoVisual) * 0.5);
    float cerca = PLANO_CERCANO;
    float lejos = uPlanoLejano;
    float clipZ = (lejos + cerca) / (cerca - lejos) * vistaZ + 2.0 * lejos * cerca / (cerca - lejos);
    gl_Position = vec4(vistaX * factor / uAspecto, vistaY * factor, clipZ, -vistaZ);
}
