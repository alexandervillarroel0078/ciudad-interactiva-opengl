# Ciudad Interactiva con OpenGL

Ciudad 3D de 11 × 11 celdas hecha en Java con LWJGL y OpenGL 3.3: edificios, parques, farolas, semáforos, tráfico autónomo y ciclo día/noche.
Conduce un auto para completar 3 entregas contra el reloj, con minimapa y faros dinámicos.

## Requisitos

- JDK 17
- Maven

Ver también la documentación de diseño en [ENTREGA.md](CiudadConduccion/CiudadConduccion/ENTREGA.md).

## Ejecutar

```
cd CiudadConduccion/CiudadConduccion
mvn compile exec:exec
```

## Controles

| Tecla | Acción |
|---|---|
| W / S o ↑ / ↓ | Acelerar / frenar y retroceder |
| A / D o ← / → | Girar |
| Espacio | Freno fuerte |
| C | Cambiar de cámara |
| N | Día / noche |
| F | Faros del auto |
| M | Mostrar / ocultar minimapa |
| H | Ayuda en pantalla |
| P | Pausa |
| R | Reiniciar partida |
| ESC | Salir (o apagar el mouse-look si está activo) |
| Botón derecho del mouse | Mover la cámara (orbital y aérea) |

ENTER empieza la partida desde el menú de inicio.

## Notas

- La primera ejecución descarga las dependencias. Se necesita una sesión gráfica y un controlador con OpenGL 3.3; incluye nativos de Windows, Linux (x64/ARM64) y macOS (Intel/Apple Silicon).
- En macOS el perfil Maven agrega `-XstartOnFirstThread`; desde un IDE hay que añadirlo a la JVM. Usa `exec:exec`, no `exec:java`.
- Pruebas: `mvn test` (sin abrir ventana). Arranque gráfico breve: `-Ddemo.frames=6` en la JVM del juego.

## Organización

```text
com.graphics.ciudad
├── Main, Juego   ← entrada y ciclo entrada → actualizar(dt) → dibujar
├── motor/        ← Ventana, Shader, Cubo, Camara
├── mundo/        ← Mapa, Ciudad, Decoracion, Semaforo, Senalizacion
├── vehiculo/     ← Auto, Colisiones
├── iluminacion/  ← Iluminacion
├── trafico/      ← Vehiculo, Trafico
├── interfaz/     ← Dibujo2D, Hud
└── juego/        ← Entregas, Minimapa, EstadoPartida
src/main/resources/shaders ← ciudad.vert, iluminacion.frag, plano.frag, hud.vert, hud.frag
```

## Capturas de la entrega

Guardar en `CiudadConduccion/CiudadConduccion/docs/capturas/`: `dia.png` (cámara de seguimiento de día), `noche.png` (misma zona con N), `minimapa.png` (sectores, auto y destino) y `parque.png` (fuente, senderos, árboles y bancos).

Más detalles en [DOCUMENTO.docx](CiudadConduccion/CiudadConduccion/DOCUMENTO.docx).

## Después de cambiar el código

Desde `CiudadConduccion/CiudadConduccion`:

```
mvn compile
mvn test
```

## Generar el ZIP de entrega

Desde `CiudadConduccion/CiudadConduccion`:

```
powershell -ExecutionPolicy Bypass -File .\entregar.ps1
```

## Actualizar el repositorio (Git)

```
git add .
git commit -m "actualizacion"
git push origin main
```
