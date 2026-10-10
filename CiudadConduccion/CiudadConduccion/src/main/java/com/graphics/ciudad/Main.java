package com.graphics.ciudad; // Paquete raíz de la versión organizada por composición.

/**
 * MAIN: punto de entrada del proyecto final (mainClass del pom.xml).
 * Responsable de: crear el Juego y arrancarlo; no contiene lógica propia.
 * Se comunica con: Juego.
 */
public class Main {

    /** Punto de entrada del proyecto final. */
    public static void main(String[] args) {
        Juego juego = new Juego(); // Crea la versión con todas las etapas acumuladas.
        juego.ejecutar(); // Inicia el ciclo de vida completo de la aplicación.
    }
}
//hola
