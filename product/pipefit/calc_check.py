"""Verificación numérica de la hoja de cálculos de Pipe Fitter Calc.

Todo lo que la app va a calcular sale de aquí, con la fórmula a la vista. Si un número
no cuadra con la trigonometría, no entra en la app.
"""

from __future__ import annotations

import math

# Ángulos de trabajo en fontanería/montaje de tubo (grados).
ANGLES = (11.25, 22.5, 30.0, 45.0, 60.0)


def offset_multiplier(angle_deg: float) -> float:
    """Recorrido (travel) por unidad de desvío: csc(θ) = 1/sin(θ)."""
    return 1.0 / math.sin(math.radians(angle_deg))


def offset_run(angle_deg: float) -> float:
    """Avance (run) por unidad de desvío: cot(θ) = 1/tan(θ)."""
    return 1.0 / math.tan(math.radians(angle_deg))


def offset_solution(offset_mm: float, angle_deg: float) -> dict:
    """Desvío conocido -> recorrido, avance y ángulo de corte del tubo."""
    if offset_mm <= 0:
        raise ValueError("el desvío debe ser positivo")
    travel = offset_mm * offset_multiplier(angle_deg)
    run = offset_mm * offset_run(angle_deg)
    return {
        "offset": offset_mm,
        "angle": angle_deg,
        "multiplier": offset_multiplier(angle_deg),
        "travel": travel,
        "run": run,
        # Corte de cada extremo: la mitad del ángulo, medido desde la perpendicular.
        "cut_angle": angle_deg / 2.0,
    }


def rolling_offset(vertical_offset: float, horizontal_offset: float, angle_deg: float) -> dict:
    """Offset con giro: el desvío real es la diagonal de los dos desvíos."""
    true_offset = math.hypot(vertical_offset, horizontal_offset)
    solution = offset_solution(true_offset, angle_deg)
    solution["true_offset"] = true_offset
    solution["roll_angle"] = math.degrees(
        math.atan2(horizontal_offset, vertical_offset)
    )
    return solution


# Takeout (descuento de codo) por tamaño nominal, en milímetros por pulgada de diámetro.
# 90° radio largo: 1,5 × NPS. 90° radio corto: 1,0 × NPS. 45° radio largo: 0,625 × NPS.
TAKEOUT_FACTORS = {
    ("90", "LR"): 1.5,
    ("90", "SR"): 1.0,
    ("45", "LR"): 0.625,
}


def takeout(angle: str, radius: str, nominal_inches: float) -> float:
    factor = TAKEOUT_FACTORS[(angle, radius)]
    return factor * nominal_inches * 25.4  # a milímetros


def miter_cuts(total_angle: float, segments: int) -> dict:
    """Codo fabricado a inglete: ángulo de cada corte y longitud del segmento."""
    if segments < 2:
        raise ValueError("un codo a inglete necesita al menos 2 segmentos")
    per_cut = total_angle / (2.0 * segments)
    return {"segments": segments, "total_angle": total_angle, "cut_angle": per_cut}


DENSITY_KG_M3 = {"acero": 7850.0, "cobre": 8960.0, "pvc": 1400.0, "inox": 8000.0}


def pipe_weight(outer_mm: float, wall_mm: float, length_m: float, material: str) -> dict:
    """Peso de tubo por volumen de pared, no por diámetro exterior."""
    if wall_mm * 2 >= outer_mm:
        raise ValueError("pared incompatible con el diámetro")
    outer_r = outer_mm / 2000.0
    inner_r = (outer_mm - 2 * wall_mm) / 2000.0
    area = math.pi * (outer_r**2 - inner_r**2)
    volume = area * length_m
    density = DENSITY_KG_M3[material]
    return {
        "volume_m3": volume,
        "litres_inner": math.pi * inner_r**2 * length_m * 1000.0,
        "weight_kg": volume * density,
    }


def check(label: str, actual: float, expected: float, tolerance: float = 0.01) -> None:
    ok = abs(actual - expected) <= tolerance
    print(f"{'OK ' if ok else 'FALLA'} {label}: {actual:.4f} (esperado {expected:.4f})")
    if not ok:
        raise SystemExit(f"verificación fallida: {label}")


if __name__ == "__main__":
    print("== Multiplicadores por ángulo (1/sin θ) ==")
    for a in ANGLES:
        print(f"  {a:>6}°  recorrido × {offset_multiplier(a):.4f}   avance × {offset_run(a):.4f}")

    print("\n== Casos de referencia (de oficio) ==")
    # Los tres valores que cualquiera del oficio reconoce de memoria.
    check("45° recorrido (√2)", offset_multiplier(45), 1.4142)
    check("22,5° recorrido (csc)", offset_multiplier(22.5), 2.6131)
    check("22,5° avance (cot)", offset_run(22.5), 2.4142)
    check("60° avance (cot)", offset_run(60), 0.5774)

    print("\n== Ejemplo: desvío de 100 mm ==")
    for a in (22.5, 45.0, 60.0):
        s = offset_solution(100.0, a)
        print(f"  {a:>5}° -> recorrido {s['travel']:.1f} mm, avance {s['run']:.1f} mm, corte {s['cut_angle']:.2f}°")

    print("\n== Offset con giro (100 mm vertical, 60 mm horizontal, 45°) ==")
    r = rolling_offset(100.0, 60.0, 45.0)
    print(f"  desvío real {r['true_offset']:.1f} mm, recorrido {r['travel']:.1f} mm, giro {r['roll_angle']:.2f}°")
    check("diagonal 100/60", r["true_offset"], math.hypot(100, 60))

    print("\n== Takeout (descuento de codo), 2\" ==")
    for (angle, radius), factor in TAKEOUT_FACTORS.items():
        print(f"  {angle}° {radius}: {takeout(angle, radius, 2.0):.1f} mm  (factor {factor} × NPS)")

    print("\n== Codo a inglete de 90° ==")
    for segments in (3, 4):
        m = miter_cuts(90.0, segments)
        print(f"  {segments} segmentos -> corte de {m['cut_angle']:.2f}°")

    print("\n== Peso y volumen: tubo de acero 2\" (60,3 × 3,9 mm), 6 m ==")
    w = pipe_weight(60.3, 3.9, 6.0, "acero")
    print(f"  peso {w['weight_kg']:.2f} kg, volumen interior {w['litres_inner']:.2f} l")
    # Comprobación de orden de magnitud contra tabla comercial: ~5,4 kg/m para 2" SCH40.
    check("peso por metro 2\" SCH40 (kg/m)", w["weight_kg"] / 6.0, 5.44, tolerance=0.15)

    print("\nTodas las comprobaciones han pasado.")
