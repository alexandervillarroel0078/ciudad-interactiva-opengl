#version 330 core // Selecciona GLSL 3.30 para el contexto OpenGL 3.3.
// SHADER DE FRAGMENTOS DE LAS PARTÍCULAS: un disco de bordes suaves con el color de la partícula y su opacidad.
// Lo carga SistemaParticulas; la mezcla por alfa la activa el código Java.
in vec2 vLocal; // Posición dentro del cuadrado, de -0.5 a 0.5.
uniform vec3 uColor; // Color de la partícula (Malla lo envía).
uniform float uAlfaParticula; // Opacidad de esta partícula: baja al final de su vida.
uniform float uBrillo; // 1 de día; menor de noche, para que el humo gris no brille en la oscuridad.
out vec4 FragColor; // Color final con transparencia.

void main() {
    float distancia = length(vLocal) * 2.0; // 0 en el centro, 1 en el borde del disco.
    float forma = 1.0 - smoothstep(0.5, 1.0, distancia); // Borde difuso.
    if (forma <= 0.0) { // Fuera del disco: no se dibuja.
        discard;
    }
    FragColor = vec4(uColor * uBrillo, uAlfaParticula * forma);
}
