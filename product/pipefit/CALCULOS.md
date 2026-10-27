# Pipe Fitter Calc — hoja de cálculos

Verificada en `calc_check.py` (Kotlin portará estas mismas fórmulas). Todas las
comprobaciones pasan, incluida una cruzada contra tabla comercial: tubo de acero 2" SCH40
(60,3 × 3,9 mm) da **5,42 kg/m** frente a los 5,44 kg/m de catálogo.

## Identidad del producto

- **Qué es:** calculadora de montaje de tubo para el oficial, en el móvil, sin cuenta.
- **Qué la hace distinta:** **la fórmula a la vista**. El oficial puede comprobar el número
  antes de cortar. La queja número uno del incumbente es "the multipliers are wrong"; aquí
  no hay multiplicadores mágicos, hay `1/sin(θ)` escrito en pantalla.
- **Modelo:** gratis con desbloqueo único. No hay suscripción, ni anuncios, ni cuenta.

## Fórmulas (todas verificadas)

| Cálculo | Fórmula | Comprobación |
|---|---|---|
| Multiplicador de recorrido | `csc θ = 1/sin θ` | 45° → 1,4142 (√2); 22,5° → 2,6131 |
| Avance | `cot θ = 1/tan θ` | 22,5° → 2,4142; 60° → 0,5774; 45° → 1,0000 |
| Recorrido | `desvío × csc θ` | 100 mm a 45° → 141,4 mm |
| Ángulo de corte | `θ / 2` | 45° → 22,5° por extremo |
| Offset con giro | `desvío real = √(v² + h²)`; `giro = atan2(h, v)` | 100/60 → 116,6 mm y 30,96° |
| Takeout de codo | `factor × NPS × 25,4 mm` | 90° LR = 1,5; 90° SR = 1,0; 45° LR = 0,625 |
| Codo a inglete | `corte = ángulo total / (2 × segmentos)` | 90° en 3 segmentos → 15° |
| Peso de tubo | `π (R² − r²) × L × densidad` | 2" SCH40 → 5,42 kg/m (catálogo 5,44) |
| Volumen interior | `π r² × L` | 6 m de 2" → 13,0 l |

Densidades usadas (kg/m³): acero 7850, inox 8000, cobre 8960, PVC 1400.

Ángulos en v1: 11,25° · 22,5° · 30° · 45° · 60°.

## Qué es gratis y qué se desbloquea

La línea no está en "medio cálculo": está en **el trabajo más común gratis y el resto
pagado**. Así el que sólo hace offsets de 45° no paga nunca, y el que vive de esto paga
una vez.

**Gratis, para siempre**
- Desvío de 45° completo: recorrido, avance, ángulo de corte y takeout.
- La fórmula a la vista, con los números sustituidos.
- Imperial y métrico.
- Sin anuncios, sin cuenta, sin conexión.

**Desbloqueo único, 6,99 €**
- Todos los ángulos (11,25° · 22,5° · 30° · 60°).
- Offset con giro (rolling offset).
- Cortes a inglete y segmentos.
- Peso y volumen de tubo por material y diámetro.
- Guardar trabajos y exportar a PDF para el cliente.
- Historial de los últimos cálculos.

## Por qué este modelo y no pago por delante

1. **La barrera es la desconfianza**, no el precio: el usuario tiene que poder comprobar el
   cálculo antes de pagar. Un muro por delante reproduce el problema que vamos a resolver.
2. **Instalaciones → ranking.** Play ordena por instalaciones y reseñas. Gratis consigue
   5-10× instalaciones, y con una conversión del 1-3% el ingreso es igual o mejor que cobrar
   6,99 $ a la entrada, con más gente usando la app.
3. **Cero quejas de cobro.** El incumbente recibe reseñas de 1★ por "recurring fee". Aquí no
   hay suscripción, y eso se dice en la ficha.

## Aritmética del objetivo

| Objetivo neto | Bruto (Play 15%) | Ventas/mes a 6,99 € |
|---|---|---|
| 50 € | 59 € | 8 |
| 100 € | 118 € | 17 |
| 150 € | 176 € | 25 |

Con modelo gratis + desbloqueo, una conversión realista del 1-3% sobre 300-1.000
instalaciones/mes da **4-100 €/mes**. Objetivo escrito: **50 €/mes en 90 días**, y criterio
de parada: menos de 3 desbloqueos en 60 días tras publicar.

## Lo que NO entra en v1

- **Separación de soportes:** depende de norma (ASME B31.1 y equivalentes) y de material,
  temperatura y servicio. Sin una tabla citada, no se implementa: mejor no tenerlo que
  tenerlo mal.
- **Dilatación térmica:** útil, pero es otra tabla y otro público. v2.
- **Todo lo que requiera cuenta o nube.** No hay servidor, no hay login, no hay datos que
  salgan del móvil.
