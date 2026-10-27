# Investigación: utilidad de importación y gráficas de salud en Android

**Fecha de la foto:** 27 de octubre de 2026. **Fuente de los datos de apps:** Google Play
(US/en) vía `google-play-scraper`. Las cifras de instalaciones son los rangos que publica
Play (cubos), no números exactos.

---

## 1. Veredicto corto

**El nicho de "exportar datos de salud" es demasiado pequeño, y esto lo digo corrigiendo mi
primera impresión.** En la primera pasada vi apps de pago y un líder con 3,58★ y me pareció
un hueco. Al medirlo en serio: las cuatro apps específicas de exportación suman **34
reseñas en total** y **8 reseñas negativas**. Eso no es un mercado, es un grupo de amigos.

Consecuencia aritmética: 34 reseñas en una categoría equivalen a unos cientos de usuarios
activos, no a miles. A 9,99 € eso son **unos pocos cientos de euros una vez**, no 150 €
cada mes. La categoría no sostiene el objetivo.

Lo que sí aprendimos, y sirve para cualquier otra idea:

- El "pliego" de las reseñas es real pero son 8 datos: sirve para diseñar la función, no
  para justificar el negocio.
- El precedente de pago (14,99 $, 11,99 $) demuestra que **la gente paga por utilidades de
  una sola función**, aunque sea poca gente.
- Un mercado de verdad se reconoce por **cientos de reseñas y varias apps de pago
  sobreviviendo con buena nota**, no por una app pagada con 500 instalaciones.

Y la puerta que decide más que la idea, en cualquier caso: **12 testers durante 14 días**
antes de publicar, si la cuenta de Play es personal y nueva.

---

## 2. Quién está ahí (snapshot)

| App | Instalaciones | Reseñas | Nota | Precio | IAP | id |
|---|---|---|---|---|---|---|
| Health Data Export | 5.000–7.842 | 63 | 3,58 | Gratis | sí | `com.teqxnology.healthdataexport` |
| Health.md – Health Data Export | 500–969 | — | — | Gratis | sí | `com.healthmd.android` |
| Health Connect Webhook | 500–761 | — | — | **14,99 $** | no | `com.hcwebhook.app` |
| Health Analytics: Data Export | 10–21 | — | — | Gratis | sí | `com.arnohealth.healthanalytics` |
| CSV to Health Connect | 100–333 | — | — | Gratis | no | `de.jelestudios.csv_to_healthconnect` |
| Health Connect View | 100–108 | — | — | Gratis | sí | `de.steppicrew.healthconnectview` |
| Health Sync | 1.000.000–4.067.624 | 41.865 | 4,33 | Gratis | sí | `nl.appyhapps.healthsync` |
| Heart Rate Variability Logger | 1.000–2.695 | 54 | 3,11 | **11,99 $** | no | `com.asma.hrvlogger` |
| Guava: Health Tracker | 100.000–148.756 | 1.872 | 4,67 | Gratis | no | `com.guavahealth.app` |

Lectura:

- **Health Sync** (1 M+) es el incumbente y no hace export: hace *sync* entre plataformas.
  Ese es el techo real de la categoría, y su volumen viene de un problema distinto.
- Las apps de export específicas son **micro**: 500–8.000 instalaciones. Están repartidas,
  gratis, con notas mediocres (3,11–3,58).
- **Sí hay precedente de pago**: 14,99 $ y 11,99 $ por utilidades de una sola función, sin
  suscripción, con 500–2.700 instalaciones. Poca gente, pero paga.

Lo que esto significa para 150 €/mes: a 9,99 € hacen falta **~15 ventas al mes**. El líder
mueve ~900 instalaciones/mes y es **gratis**; una app nueva de pago no llega ahí. Y con 34
reseñas totales en la categoría, ni el líder tiene ese tráfico por esta vía.

**Revisión tras medir:** esta categoría no llega a 150 €/mes de forma recurrente. Como
mucho, un ingreso puntual de unos cientos de euros. Si el objetivo es recurrente, hay que
elegir una categoría con cientos de reseñas y competidores de pago vivos.

---

## 3. El pliego: lo que dicen las reseñas de 1–3★

*(Base: 34 reseñas leídas en las cuatro apps de exportación; 8 negativas. Muestra pequeña:
léase como diseño de producto, no como tamaño de mercado.)*

De `com.teqxnology.healthdataexport` (3,58★), citas literales:

- "neither manual or auto export work"
- "does not do anything"
- "the export does not give you any progress nor logs, so you have no idea if it's stuck or
  just taking a long time"
- "unable to export beyond the past four months"
- "does not export heart rate measurements. only min max and average. kinda useless"

Requisitos que salen de ahí, sin interpretación:

1. Que funcione, y que se pueda reintentar.
2. **Progreso y log visibles** durante una importación que puede tardar minutos.
3. **Historial completo**, sin recortes arbitrarios.
4. **Muestras crudas**, no solo mín/máx/media.

---

## 4. De dónde salen los datos: el archivo de Google

Ruta del archivo (Google Takeout): `Takeout/Fitbit/Global Export Data/` con **un JSON por
día y por métrica**:

- `steps-YYYY-MM-DD.json`
- `heart_rate-YYYY-MM-DD.json` (archivos antiguos: `heartrate-YYYY-MM-DD.json`)
- `weight-YYYY-MM-DD.json`
- y el resto de métricas con el mismo patrón

Consecuencias de diseño:

- El parser es **por fichero y por fecha**, no un JSON único: hay que recorrer miles de
  ficheros pequeños. De ahí el requisito de progreso y log.
- **No hace falta Health Connect**: se lee un ZIP que el usuario ya tiene. Cero permisos
  sensibles, cero declaración de Health Connect, y encaja con el discurso local-first.
- **Falta fijar el esquema exacto de campos** (nombres de claves dentro de cada JSON).
  Eso no se resuelve leyendo guías: hay que descargar un export real y mirarlo. Es el
  primer paso del proyecto y lo puede hacer el propio interesado desde su cuenta.

---

## 5. Requisitos y costes de Google Play (2026)

- **Cuenta de desarrollador:** 25 $ una vez. Verificación de identidad obligatoria.
- **Cuentas personales creadas después del 13/11/2023:** test cerrado con **12 testers
  durante 14 días consecutivos** antes de poder pedir acceso a producción. Es la única
  traba que obliga a "pedirle a gente", y no se puede automatizar.
  Alternativa: cuenta de organización (requiere datos de empresa/D-U-N-S).
- **Data Safety form:** obligatorio siempre (declarar qué se recoge; aquí: nada, todo local).
- **Health Apps declaration:** si se usa Health Connect, sí o sí, y con revisión. **Si solo
  se lee un archivo local, se evita.**
- **Comisión:** 15% del primer millón de dólares anual (automático). Suscripciones, con
  tipo reducido en 2026. IVA y facturación al usuario los gestiona Google: no hay que
  facturar a cada cliente.
- **Modelo de precio:** app de pago (sin versión gratis, menos instalaciones) vs gratis con
  compra única dentro de la app. Para una utilidad, la segunda capta más volumen.

---

## 6. Legal y datos

- El procesamiento es **local**: sin servidor, sin cuenta. Eso simplifica enormemente el
  RGPD (no hay encargado de tratamiento, no hay brecha posible en un backend).
- Aun así hacen falta: **política de privacidad publicada** y **términos**. Play las exige.
- Si en algún momento se añade sincronización o nube, cambia el régimen entero: categoría
  especial de datos (salud) y todas las obligaciones. **Mantenerlo local no es solo
  discurso: es la decisión que evita el papeleo.**

---

## 7. Puertas y riesgos

| Puerta | Dureza | Se puede automatizar |
|---|---|---|
| 12 testers × 14 días | alta (rompe la condición de "no pedir a nadie") | no |
| Cuenta de organización (alternativa) | media (datos de empresa) | no |
| Ranking en búsqueda de Play | alta | parcialmente (listado y reseñas) |
| Esquema del archivo de Takeout | baja | sí, con un export real |
| Soporte por email a usuarios que pagan | media (constante, acotado) | no |

Riesgo principal: **construir 3 semanas y descubrir en el mes 3 que la categoría no da
para 150 €/mes.** Mitigación: medir antes de construir (sección 8).

---

## 8. Validación barata, antes de escribir la app

1. **Descargar el export real** de la propia cuenta y documentar el esquema de cada JSON.
   Coste 0, un día.
2. **Contar la demanda** con datos ya accesibles: reseñas de los cinco competidores
   (cuántas quejas por "no funciona", "sin progreso", "historial cortado") y apariciones en
   foros pidiendo esto. Coste 0, una tarde.
3. **Construir el parser + CSV limpio primero** (sin UI), y probarlo con el export real.
   Es la parte que se puede romper y la que todos los competidores rompen.
4. Solo entonces: gráficas, listado de Play, y la decisión de precio.

Si el paso 3 funciona con un historial de años sin cortarse, hay producto. Si el archivo de
Google no trae lo que hace falta, es mejor saberlo antes que después.

---

## 9. Preguntas abiertas

1. ¿Acepta el peaje de los 12 testers, o prefiere cuenta de organización?
2. ¿Un pago único (9,99–14,99 €) o gratis con desbloqueo?
3. ¿El producto se queda en "importar y ver", o incluye comparación con la nube de Google
   (que exigiría OAuth y servidor)?
4. ¿Castellano e inglés desde el día uno, o solo inglés?
