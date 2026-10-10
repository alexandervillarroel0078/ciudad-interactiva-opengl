#version 330 core // Selecciona GLSL 3.30 para el contexto OpenGL 3.3.
// SHADER DE VÉRTICES DE LAS PARTÍCULAS: convierte un cuadrado unitario (Malla) en un BILLBOARD, un cuadrado siempre
// orientado a la cámara. Lo carga SistemaParticulas. La proyección es la misma que la de ciudad.vert (perspectiva).
layout (location = 0) in vec3 aPos; // Esquina del cuadrado: X e Y entre -0.5 y 0.5, Z = 0.
layout (location = 1) in vec3 aNormal; // Malla la entrega; este shader no la usa.
uniform vec3 uPos; // Centro de la partícula en el mundo.
uniform vec3 uEscala; // Ancho (x) y alto (y) del cuadrado en unidades del mundo.
uniform vec3 uOjo; // Posición de la cámara (la envía Camara.configurar).
uniform vec3 uObjetivo; // Punto que observa la cámara.
uniform float uAspecto; // Relación ancho/alto de la ventana: se actualiza en cada cuadro, así sigue el redimensionado.
uniform float uPlanoLejano; // Distancia máxima visible.
uniform float uCampoVisual; // Campo visual vertical, en grados.
out vec2 vLocal; // Esquina del cuadrado, para dibujar un disco suave en el fragment shader.

const float PLANO_CERCANO = 0.1; // Igual que ciudad.vert.

void main() {
    vLocal = aPos.xy; // Posición dentro del cuadrado.
    vec3 frente = normalize(uObjetivo - uOjo); // Hacia dónde mira la cámara.
    vec3 derecha = normalize(cross(frente, vec3(0.0, 1.0, 0.0))); // Eje horizontal de la cámara.
    vec3 arriba = cross(derecha, frente); // Eje vertical de la cámara.
    // Billboard: el cuadrado se extiende a lo largo de los ejes de la cámara, no de los del mundo.
    vec3 mundo = uPos + derecha * (aPos.x * uEscala.x) + arriba * (aPos.y * uEscala.y);
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
