#version 330 core // Selecciona GLSL 3.30 para el contexto OpenGL 3.3.
// SHADER DE VÉRTICES DEL CIELO: dibuja el cubo unitario (Cubo) agrandado y centrado en la cámara.
// Lo carga Cielo. La proyección es la misma que la de ciudad.vert (rama de perspectiva), sin giro ni sombras.
layout (location = 0) in vec3 aPos; // Posición local del vértice, de -0.5 a 0.5 (Cubo.crear).
layout (location = 1) in vec3 aNormal; // Normal del cubo: este shader no la necesita, pero el VAO la entrega.
uniform vec3 uPos; // Centro del cubo: la posición de la cámara.
uniform vec3 uEscala; // Lado del cubo (menor que el plano lejano, ver Cielo.LADO_CUBO).
uniform float uGiro; // Giro alrededor de Y (Cubo.caja lo envía siempre; vale 0).
uniform vec3 uOjo; // Posición de la cámara (la envía Camara.configurar).
uniform vec3 uObjetivo; // Punto que observa la cámara.
uniform float uAspecto; // Relación ancho/alto de la imagen.
uniform float uPlanoLejano; // Distancia máxima visible.
uniform float uCampoVisual; // Campo visual vertical, en grados.
out vec3 vDir; // Posición local del vértice: como el cubo no gira, es la dirección de la mirada en el mundo.

const float PLANO_CERCANO = 0.1; // Igual que ciudad.vert.

void main() {
    vDir = aPos; // El fragmento recibe la dirección interpolada; cielo.frag la normaliza.
    vec3 mundo = aPos * uEscala + uPos; // Escala y traslada el cubo al ojo.
    vec3 frente = normalize(uObjetivo - uOjo); // Hacia dónde mira la cámara.
    vec3 derecha = normalize(cross(frente, vec3(0.0, 1.0, 0.0))); // Eje horizontal de la cámara.
    vec3 arriba = cross(derecha, frente); // Eje vertical de la cámara.
    vec3 diferencia = mundo - uOjo; // Vértice relativo a la cámara.
    float vistaX = dot(diferencia, derecha);
    float vistaY = dot(diferencia, arriba);
    float vistaZ = -dot(diferencia, frente); // Negativa delante de la cámara.
    float factor = 1.0 / tan(radians(uCampoVisual) * 0.5);
    float cerca = PLANO_CERCANO;
    float lejos = uPlanoLejano;
    float clipZ = (lejos + cerca) / (cerca - lejos) * vistaZ + 2.0 * lejos * cerca / (cerca - lejos);
    gl_Position = vec4(vistaX * factor / uAspecto, vistaY * factor, clipZ, -vistaZ);
}
