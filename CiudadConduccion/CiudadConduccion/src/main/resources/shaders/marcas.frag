#version 330 core // Selecciona GLSL 3.30 para el contexto OpenGL 3.3.
// SHADER DE FRAGMENTOS DE LAS MARCAS DE FRENADO: color oscuro SIN iluminación (no brilla ni de día ni de noche) y
// opacidad según la vida de la marca, con los bordes laterales suavizados. Lo carga MarcasFrenado.
in vec2 vLocal; // Posición dentro del rectángulo, de -0.5 a 0.5.
uniform vec3 uColor; // Color de la goma (Malla lo envía).
uniform float uAlfaMarca; // Opacidad de este tramo: baja con su vida.
out vec4 FragColor; // Color final con transparencia.

void main() {
    float borde = 1.0 - smoothstep(0.3, 0.5, abs(vLocal.x)); // Bordes laterales difusos.
    FragColor = vec4(uColor, uAlfaMarca * borde);
}
