#version 330 core // Selecciona la versión GLSL correspondiente a OpenGL 3.3.
// SHADER DE FRAGMENTOS DEL CIELO PROCEDURAL: todo el cielo se calcula por píxel a partir de la dirección de la mirada.
//  1. Degradado horizonte-cénit que mezcla día, atardecer y noche.
//  2. Disco del sol y de la luna, con halo.
//  3. Estrellas por hash 3D, solo de noche, con parpadeo leve.
//  4. Nubes: ruido fbm de 4 octavas proyectado sobre un plano, que se desplaza con el tiempo y se ilumina según el sol.
// Los uniforms los envía Cielo.dibujar() con datos de Iluminacion (dirección del sol y factor de noche).
in vec3 vDir; // Dirección (sin normalizar) desde la cámara hacia este punto del cubo.
uniform vec3 uDireccionSol; // Dirección unitaria hacia el sol (Iluminacion.direccionSol); baja al horizonte al anochecer.
uniform vec3 uDireccionLuna; // Dirección unitaria hacia la luna (Cielo.direccionLuna).
uniform float uFactorNoche; // 0 = día pleno, 1 = noche plena; cambia suavemente al pulsar N (Iluminacion.factorNoche).
uniform float uTiempo; // Segundos de juego: parpadeo de estrellas y movimiento de nubes.
uniform vec3 uHorizonteDia; // Colores del horizonte, enviados por Cielo (Cielo.HORIZONTE_*): son también el color de la niebla.
uniform vec3 uHorizonteAtardecer;
uniform vec3 uHorizonteNoche;
out vec4 color; // Color RGBA final.

// ==================== CONSTANTES (valores ajustables) ====================
const vec3 CENIT_DIA = vec3(0.16, 0.38, 0.82); // Día, arriba: azul intenso.
const vec3 CENIT_ATARDECER = vec3(0.22, 0.27, 0.55); // Atardecer, arriba: azul violáceo.
const vec3 CENIT_NOCHE = vec3(0.01, 0.015, 0.05); // Noche, arriba: azul casi negro.
const float CURVA_CIELO = 0.6; // < 1: el azul intenso llega rápido y el celeste queda cerca del horizonte.
const vec3 COLOR_SOL = vec3(1.0, 0.93, 0.75); // Disco solar de día.
const vec3 COLOR_SOL_ATARDECER = vec3(1.0, 0.55, 0.25); // Disco solar cerca del horizonte.
const vec3 COLOR_LUNA = vec3(0.95, 0.93, 0.82); // Blanco amarillento (igual que la luna anterior).
const float COS_RADIO_SOL = 0.9990; // cos del radio angular del disco (≈ 2.6°, más grande que el real para que se note).
const float COS_RADIO_LUNA = 0.99955; // ≈ 1.7°.
const float ESCALA_ESTRELLAS = 70.0; // Celdas por unidad de dirección: más = más estrellas y más chicas.
const float DENSIDAD_ESTRELLAS = 0.985; // Una celda tiene estrella si su hash supera este valor.
const float ESCALA_NUBES = 0.55; // Tamaño de las nubes sobre el plano: menor = más grandes.
const float VELOCIDAD_NUBES = 0.012; // Desplazamiento del plano por segundo.
const float COBERTURA_NUBES = 0.48; // Valor de fbm desde el que empieza a haber nube (menor = más cielo cubierto).

// ==================== RUIDO ====================
// Hash 3D → [0, 1) (Inigo Quilez): mismo punto, mismo valor; sin texturas ni Random.
float hash3(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.1));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

// Ruido de valor 2D: interpola con suavidad el hash de las cuatro esquinas de la celda.
float ruido(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f); // Curva suave (la misma de smoothstep).
    float a = hash2(i);
    float b = hash2(i + vec2(1.0, 0.0));
    float c = hash2(i + vec2(0.0, 1.0));
    float d = hash2(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

// fbm de 4 octavas: suma ruidos con el doble de frecuencia y la mitad de amplitud cada vez.
float fbm(vec2 p) {
    float suma = 0.0;
    float amplitud = 0.5;
    for (int i = 0; i < 4; i++) {
        suma += amplitud * ruido(p);
        p = p * 2.03 + vec2(17.3, 9.1); // Más frecuencia y un corrimiento para que las octavas no se alineen.
        amplitud *= 0.5;
    }
    return suma; // 0 a ~0.94
}

// ==================== ESTRELLAS ====================
// La dirección se multiplica por ESCALA_ESTRELLAS y se parte en celdas de un cubo; cada celda decide con su hash si
// tiene una estrella, dónde cae dentro de ella, qué tan grande y brillante es y con qué fase parpadea.
float estrellas(vec3 dir) {
    vec3 q = dir * ESCALA_ESTRELLAS;
    vec3 celda = floor(q);
    float h = hash3(celda);
    if (h < DENSIDAD_ESTRELLAS) {
        return 0.0; // Celda sin estrella.
    }
    vec3 centro = vec3(hash3(celda + 1.7), hash3(celda + 5.3), hash3(celda + 9.1)) * 0.6 + 0.2; // Posición dentro de la celda.
    float radio = mix(0.05, 0.13, hash3(celda + 3.9)); // Tamaño variable.
    float d = length(fract(q) - centro);
    float punto = 1.0 - smoothstep(0.0, radio, d); // Punto con borde suave.
    float brillo = mix(0.55, 1.0, hash3(celda + 7.7));
    float parpadeo = 0.875 + 0.125 * sin(uTiempo * 2.5 + h * 200.0); // Entre 0.75 y 1: parpadeo leve.
    return punto * brillo * parpadeo;
}

void main() {
    vec3 dir = normalize(vDir); // Dirección de la mirada.
    vec3 sol = normalize(uDireccionSol);
    vec3 luna = normalize(uDireccionLuna);

    // ---- Pesos de día / atardecer / noche: dependen de la altura del sol y del factor de noche ----
    // (Misma fórmula que Cielo.pesos en Java, que Iluminacion usa para el color de la niebla.)
    float dia = smoothstep(0.35, 0.7, sol.y); // Sol alto.
    float noche = max(1.0 - smoothstep(-0.2, 0.1, sol.y), smoothstep(0.85, 1.0, uFactorNoche)); // Sol bajo el horizonte.
    float atardecer = max(1.0 - dia - noche, 0.0); // Lo que queda entre ambos.
    float total = dia + atardecer + noche;
    dia /= total;
    atardecer /= total;
    noche /= total;

    // ---- 1. Degradado horizonte-cénit ----
    float altura = clamp(dir.y, 0.0, 1.0);
    float mezcla = pow(altura, CURVA_CIELO);
    vec3 horizonte = dia * uHorizonteDia + atardecer * uHorizonteAtardecer + noche * uHorizonteNoche;
    vec3 cenit = dia * CENIT_DIA + atardecer * CENIT_ATARDECER + noche * CENIT_NOCHE;
    vec3 cielo = mix(horizonte, cenit, mezcla);
    // Resplandor del atardecer: franja cálida concentrada hacia el lado del sol.
    vec2 horizSol = normalize(sol.xz + vec2(1e-4));
    float haciaSol = max(dot(normalize(dir.xz + vec2(1e-4)), horizSol), 0.0);
    cielo += uHorizonteAtardecer * atardecer * pow(haciaSol, 3.0) * (1.0 - smoothstep(0.0, 0.5, dir.y)) * 0.5;

    // ---- 4. Nubes: fbm sobre un plano (uv = dir.xz / altura), desplazado por el viento ----
    float nube = 0.0;
    float luzNube = 1.0;
    if (dir.y > 0.0) {
        vec2 uv = dir.xz / (dir.y + 0.12) * ESCALA_NUBES + vec2(uTiempo * VELOCIDAD_NUBES, uTiempo * VELOCIDAD_NUBES * 0.4);
        float f = fbm(uv);
        nube = smoothstep(COBERTURA_NUBES, COBERTURA_NUBES + 0.22, f) * smoothstep(0.0, 0.2, dir.y); // Se funden con el horizonte.
        // Iluminación: se vuelve a muestrear un poco hacia el sol; si ahí hay menos nube, este borde mira al sol y brilla.
        float f2 = fbm(uv + horizSol * 0.12);
        luzNube = clamp(0.65 + (f - f2) * 3.0, 0.0, 1.0);
    }

    // ---- 3. Estrellas (solo de noche, tapadas por las nubes) ----
    float visibleNoche = smoothstep(0.5, 1.0, uFactorNoche) * smoothstep(0.0, 0.12, dir.y);
    cielo += vec3(0.92, 0.94, 1.0) * estrellas(dir) * visibleNoche * (1.0 - nube);

    // ---- 2. Sol y luna con halo ----
    float cosSol = dot(dir, sol);
    float discoSol = smoothstep(COS_RADIO_SOL, COS_RADIO_SOL + 0.0004, cosSol);
    float halo = pow(max(cosSol, 0.0), 80.0) * 0.55 + pow(max(cosSol, 0.0), 8.0) * 0.18;
    vec3 colorSol = mix(COLOR_SOL, COLOR_SOL_ATARDECER, atardecer);
    float verSol = 1.0 - noche; // De noche el sol no se dibuja.
    cielo += colorSol * (discoSol * 1.2 + halo) * verSol * (1.0 - 0.85 * nube);

    float cosLuna = dot(dir, luna);
    float discoLuna = smoothstep(COS_RADIO_LUNA, COS_RADIO_LUNA + 0.0002, cosLuna);
    float haloLuna = pow(max(cosLuna, 0.0), 220.0) * 0.40 + pow(max(cosLuna, 0.0), 24.0) * 0.07;
    float verLuna = smoothstep(0.4, 1.0, uFactorNoche) * smoothstep(-0.02, 0.05, dir.y);
    cielo += (COLOR_LUNA * discoLuna + vec3(0.6, 0.7, 1.0) * haloLuna) * verLuna * (1.0 - 0.85 * nube);

    // ---- Color de las nubes y mezcla final ----
    vec3 nubeDia = mix(vec3(0.60, 0.65, 0.74), vec3(1.0), luzNube);
    vec3 nubeAtardecer = mix(vec3(0.45, 0.35, 0.50), vec3(1.0, 0.62, 0.42), luzNube);
    vec3 nubeNoche = mix(vec3(0.03, 0.04, 0.08), vec3(0.16, 0.18, 0.27), luzNube * verLuna);
    vec3 colorNube = dia * nubeDia + atardecer * nubeAtardecer + noche * nubeNoche;
    cielo = mix(cielo, colorNube, nube * 0.92);

    color = vec4(clamp(cielo, 0.0, 1.0), 1.0);
}
