#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Generador de V20260929_2_Seed_MCAS_Reglas_Hoja2.sql (precarga de reglas y
retrocesiones MCAS a partir de lo que escribió el cliente) y de
V20260930_2_Seed_MCAS_Nombres.sql (nombre y descripción legibles de cada tabla
de comisiones y de cada regla, para el administrador no técnico).

Cada conjunto y cada regla exigen `nombre` y `descripcion` (en usted): el
generador se detiene si falta alguno. El script de nombres solo rellena los que
están vacíos (nombre IS NULL), así que no pisa lo que cambie el administrador.

Fuentes:
  * Hoja2 del «LISTADO DATOS AUTOMATIZACION SERVICIOS 1.xlsx»: reglas escritas por
    el cliente. Si hay conflicto con el Excel de comisiones, MANDA LA HOJA2 y el
    valor del Excel queda anotado en `localizador_origen`.
  * «COMISIONES NEXO A 16032026 1.xlsx»: tablas numéricas (matrices de Endesa e
    Iberdrola, fechas de vigencia, políticas de retrocesión).

Ningún importe se inventa: los de la Hoja2 están escritos abajo junto al texto
literal de la celda que los justifica (con --listado se comprueba que ese texto
sigue estando en la celda) y los del Excel se leen de sus celdas y se redondean
a 2 decimales (el Excel trae 46.800000000000004).

Uso (desde la raíz del repositorio backend):
    python tools/mcas/generar_seed_hoja2.py "C:/Users/Ivan/Downloads/COMISIONES NEXO A 16032026 1.xlsx" \
        --listado "C:/Users/Ivan/Downloads/LISTADO DATOS AUTOMATIZACION SERVICIOS 1.xlsx"

Opciones:
    --listado RUTA   Excel del cliente (Hoja1/Hoja2). Verifica los textos de la
                     Hoja2 y calcula su SHA-256. Sin él se usa el SHA-256 ya
                     registrado en V20260913_2 y se avisa de que no se verificó.
    --salida RUTA    Fichero SQL de salida (por defecto
                     src/main/resources/BBDD/V20260929_2_Seed_MCAS_Reglas_Hoja2.sql).
    --salida-nombres RUTA
                     Fichero SQL de nombres (por defecto
                     src/main/resources/BBDD/V20260930_2_Seed_MCAS_Nombres.sql).

Requisitos: Python 3.9+ y openpyxl. La salida es determinista (sin fechas de
generación), así que regenerar con los mismos Excel no produce diferencias.
Si algo no cuadra (cabeceras movidas, rangos con huecos, reglas que se solapan,
textos demasiado largos) el generador se detiene con un mensaje y no escribe nada.
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import re
import sys
from dataclasses import dataclass, field
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path
from typing import Optional

try:
    import openpyxl
except ImportError:  # pragma: no cover
    sys.exit("Falta openpyxl: pip install openpyxl")

# ---------------------------------------------------------------------------
# Constantes
# ---------------------------------------------------------------------------
ARCHIVO_COMISIONES = "COMISIONES NEXO A 16032026 1.xlsx"
ARCHIVO_LISTADO = "LISTADO DATOS AUTOMATIZACION SERVICIOS 1.xlsx"
# SHA-256 del LISTADO tal como quedó registrado en V20260913_2 (solo se usa sin --listado).
HASH_LISTADO_REGISTRADO = "4D5D5174AED1BDDF1CC167AA701159E6163E2F5D2798202AAE11B4057893CAC8"
# SHA-256 del Excel de comisiones con el que se revisó este generador (solo informativo).
HASH_COMISIONES_REVISADO = "654D4AF590BC084500EDD3F594862121601B00E9A84847E1FB78DEB695C5197D"

CREADO_POR = "seed-hoja2"
CREADO_POR_SEED_ANTERIOR = "seed-controlado"   # V20260913_2 (conjuntos IRIS y VISALIA)
CONJUNTOS_V20260913_2 = {"IRIS-FIJO-20260304", "VISALIA-FIJO-20250612"}
POLITICA_PAGO = '{"numero_pagos_sugerido":1}'  # el número de pagos lo pone el usuario en el contrato
NOMBRE_SQL = "V20260929_2_Seed_MCAS_Reglas_Hoja2.sql"
NOMBRE_SQL_NOMBRES = "V20260930_2_Seed_MCAS_Nombres.sql"
CARPETA_BBDD = Path(__file__).resolve().parents[2] / "src" / "main" / "resources" / "BBDD"
SALIDA_POR_DEFECTO = CARPETA_BBDD / NOMBRE_SQL
SALIDA_NOMBRES_POR_DEFECTO = CARPETA_BBDD / NOMBRE_SQL_NOMBRES

# Longitudes de columna (V20260913_1, V20260930_1 / entidades JPA): se comprueban antes de escribir.
MAX_CODIGO = 100
MAX_NOMBRE = 150
MAX_DESCRIPCION = 1000
MAX_NOMBRE_PRODUCTO = 250
MAX_LOCALIZADOR = 250
MAX_MOTIVO = 500
MAX_DESCRIPCION_POLITICA = 500
MAX_CATEGORIA = 100

# Comercializadoras: patrón de búsqueda por nombre. {a} es el alias de tbcomercializadoras.
# Solo se siembra si el patrón encuentra EXACTAMENTE una fila.
PATRONES = {
    "ENDESA": "UPPER(TRIM({a}.nombre)) LIKE 'ENDESA%' AND UPPER({a}.nombre) NOT LIKE '%XXI%'",
    "IBERDROLA": "UPPER(TRIM({a}.nombre)) LIKE 'IBERDROLA%'",
    "IRIS": "UPPER(TRIM({a}.nombre)) LIKE 'IRIS%'",
    "UNIELECTRICA": "UPPER(TRIM({a}.nombre)) LIKE 'UNIEL%'",
    "NATURGY": "UPPER(TRIM({a}.nombre)) LIKE 'NATURGY%'",
    "VISALIA": "UPPER(TRIM({a}.nombre)) LIKE 'VISALIA%'",
    "TOTAL": "UPPER(TRIM({a}.nombre)) LIKE 'TOTAL%'",
}
ETIQUETA_PATRON = {
    "ENDESA": "ENDESA% (excepto ENDESA XXI)",
    "IBERDROLA": "IBERDROLA%",
    "IRIS": "IRIS%",
    "UNIELECTRICA": "UNIEL%",
    "NATURGY": "NATURGY%",
    "VISALIA": "VISALIA%",
    "TOTAL": "TOTAL%",
}

CENT = Decimal("0.01")


class ErrorGenerador(Exception):
    """Algo del Excel o de la configuración no cuadra: no se escribe el SQL."""


def exigir(condicion: bool, mensaje: str) -> None:
    if not condicion:
        raise ErrorGenerador(mensaje)


# ---------------------------------------------------------------------------
# Modelo
# ---------------------------------------------------------------------------
@dataclass(frozen=True)
class Rango:
    minimo: Optional[Decimal]
    min_inc: bool
    maximo: Optional[Decimal]      # None = sin límite superior
    max_inc: bool

    def texto(self) -> str:
        izq = "[" if self.min_inc else "("
        der = "]" if self.max_inc else ")"
        mx = "∞" if self.maximo is None else miles(self.maximo)
        mn = "-∞" if self.minimo is None else miles(self.minimo)
        return f"{izq}{mn}, {mx}{der}"


@dataclass
class Dim:
    """Una fila de mcas_valores_rango (una dimensión de una celda)."""
    dimension: str
    rango: Optional[Rango] = None
    categoria: Optional[str] = None


@dataclass
class Celda:
    ref: str
    dims: list
    importe: Decimal


@dataclass
class Regla:
    codigo: str
    prioridad: int
    metodo: str
    condiciones: dict
    localizador: str
    calculo: Optional[dict] = None
    motivo: Optional[str] = None
    celdas: list = field(default_factory=list)
    omitidas: list = field(default_factory=list)   # textos de celdas sin importe (N. Aplica, Consultar)
    comentario: str = ""
    nombre: str = ""                   # legible: «Iris · pyme luz · 44 % del FEE» (obligatorio)
    descripcion: str = ""              # breve, en usted (obligatoria)


@dataclass(frozen=True)
class ReglaAnterior:
    """Regla que creó V20260913_2 (el generador no la inserta): solo se le pone nombre."""
    codigo: str
    nombre: str
    descripcion: str


@dataclass
class Conjunto:
    codigo: str
    version: str
    fase: str
    vigencia: dt.date
    archivo: str
    hoja: str
    fecha_interna: Optional[dt.date]
    hash_origen: str
    reglas: list
    existente: bool = False            # True: lo creó V20260913_2 (solo se amplía si sigue en BORRADOR)
    desactivar: list = field(default_factory=list)   # [(codigo_regla, localizador)]
    comentario: str = ""
    nombre: str = ""                   # legible: «Endesa 2026-03» (obligatorio)
    descripcion: str = ""              # breve, en usted (obligatoria)
    anteriores: list = field(default_factory=list)   # [ReglaAnterior] de V20260913_2 (solo si existente)

    @property
    def creado_por(self) -> str:
        return CREADO_POR_SEED_ANTERIOR if self.existente else CREADO_POR


@dataclass
class Producto:
    codigo: str
    nombre: str
    tipo_precio: str
    tipo_suministro: str
    activo_desde: dt.date
    ref: str


@dataclass
class Tramo:
    unidad: str
    desde: int
    hasta: Optional[int]
    desde_inc: bool
    hasta_inc: bool
    valor: Decimal
    tipo_valor: str
    ref: str
    etiqueta: str


@dataclass
class Politica:
    codigo: str
    version: str
    vigencia: dt.date
    tipo_suministro: Optional[str]
    segmento: Optional[str]
    tarifa: Optional[str]
    metodo: str
    descripcion: str
    tramos: list
    limite_importe_pagado: bool = True


@dataclass
class Comercializadora:
    clave: str
    titulo: str
    resumen: list                       # líneas de comentario de cabecera
    productos: list = field(default_factory=list)
    conjuntos: list = field(default_factory=list)
    politicas: list = field(default_factory=list)


# ---------------------------------------------------------------------------
# Utilidades de formato
# ---------------------------------------------------------------------------
def miles(d: Decimal) -> str:
    """5000 -> 5.000 ; 2.5 -> 2,5 (formato español para los comentarios)."""
    d = Decimal(d)
    entero = int(d)
    if d == entero:
        return f"{entero:,}".replace(",", ".")
    return f"{d.normalize()}".replace(".", ",")


def euros(d: Decimal) -> str:
    """110 -> «110 €» ; 50.63 -> «50,63 €» ; 40.5 -> «40,50 €»."""
    d = Decimal(d).quantize(CENT)
    return (f"{int(d)}" if d == d.to_integral() else f"{d}".replace(".", ",")) + " €"


def fecha_es(f: dt.date) -> str:
    """2026-03-09 -> «09/03/2026» (descripciones para el administrador)."""
    return f"{f:%d/%m/%Y}"


def importe(valor) -> Decimal:
    """Importe del Excel redondeado a 2 decimales (HALF_UP sobre su representación decimal)."""
    exigir(es_numero(valor), f"se esperaba un número y hay {valor!r}")
    return Decimal(repr(valor) if isinstance(valor, float) else str(valor)).quantize(CENT, rounding=ROUND_HALF_UP)


def factor(valor) -> Decimal:
    """Factor (0,44) o valor de retrocesión: se redondea a 2 decimales (0.44999999999999996 -> 0.45)."""
    if isinstance(valor, str) and valor.strip().endswith("%"):
        return (Decimal(valor.strip()[:-1].replace(",", ".")) / 100).quantize(CENT, rounding=ROUND_HALF_UP)
    return importe(valor)


def excel_txt(valor) -> str:
    """Valor del Excel tal cual para anotarlo (sin el ruido binario): 16.875 -> «16,875», 4.0500…01 -> «4,05»."""
    exigir(es_numero(valor), f"se esperaba un número y hay {valor!r}")
    return miles(Decimal(repr(valor)).quantize(Decimal("0.001"), rounding=ROUND_HALF_UP))


def es_numero(valor) -> bool:
    return isinstance(valor, (int, float)) and not isinstance(valor, bool)


def sql_texto(s: Optional[str]) -> str:
    if s is None:
        return "NULL"
    exigir("\\" not in s, f"texto con barra invertida (MariaDB la interpreta): {s!r}")
    exigir(";" not in s, f"texto con punto y coma (rompe los divisores de script): {s!r}")
    exigir("--" not in s, f"texto con doble guion (parece un comentario): {s!r}")
    return "'" + s.replace("'", "''") + "'"


def sql_num(d: Optional[Decimal], decimales: Optional[int] = None) -> str:
    if d is None:
        return "NULL"
    d = Decimal(d)
    if decimales is not None:
        return f"{d.quantize(Decimal(1).scaleb(-decimales))}"
    return f"{d.normalize():f}" if d != d.to_integral() else f"{int(d)}"


def sql_bool(b: bool) -> str:
    return "TRUE" if b else "FALSE"


def sql_fecha(f: Optional[dt.date]) -> str:
    return "NULL" if f is None else f"'{f.isoformat()}'"


def json_compacto(obj: Optional[dict]) -> Optional[str]:
    if obj is None:
        return None

    def conv(v):
        if isinstance(v, Decimal):
            return int(v) if v == v.to_integral() else float(v)
        return v

    return json.dumps({k: conv(v) for k, v in obj.items()}, ensure_ascii=False, separators=(",", ":"))


def fecha_celda(ws, ref: str) -> dt.date:
    v = ws[ref].value
    exigir(isinstance(v, (dt.datetime, dt.date)), f"{ws.title}!{ref}: se esperaba una fecha y hay {v!r}")
    return v.date() if isinstance(v, dt.datetime) else v


def texto_celda(ws, ref: str) -> str:
    v = ws[ref].value
    return "" if v is None else " ".join(str(v).split())


def columnas(desde: str, hasta: str) -> list:
    a = openpyxl.utils.column_index_from_string(desde)
    b = openpyxl.utils.column_index_from_string(hasta)
    return [openpyxl.utils.get_column_letter(i) for i in range(a, b + 1)]


# ---------------------------------------------------------------------------
# Hoja2: textos del cliente que justifican cada importe o regla manual
# ---------------------------------------------------------------------------
class Hoja2:
    """Comprueba (si se pasó --listado) que el texto citado sigue en la celda."""

    def __init__(self, ruta: Optional[Path]):
        self.hojas = None
        self.comprobaciones = 0
        if ruta is not None:
            wb = openpyxl.load_workbook(ruta, data_only=True)
            self.hojas = {ws.title: ws for ws in wb.worksheets}

    @property
    def verificada(self) -> bool:
        return self.hojas is not None

    def exige(self, hoja: str, ref: str, *fragmentos: str) -> str:
        """Devuelve «Hoja2 K3» y, si hay LISTADO, exige que la celda contenga los fragmentos."""
        if self.hojas is not None:
            exigir(hoja in self.hojas, f"el LISTADO no tiene la hoja {hoja}")
            texto = texto_celda(self.hojas[hoja], ref)
            for f in fragmentos:
                exigir(" ".join(f.split()) in texto,
                       f"{hoja}!{ref} ya no contiene «{f}». Revise la regla antes de regenerar.\nTexto actual: {texto}")
                self.comprobaciones += 1
        return f"{hoja} {ref}"


# ---------------------------------------------------------------------------
# Lectura de rangos del Excel
# ---------------------------------------------------------------------------
def numero_es(t: str) -> Decimal:
    """«5.000» -> 5000 ; «10» -> 10 (el punto es separador de miles en el Excel)."""
    t = t.strip()
    exigir(re.fullmatch(r"\d{1,3}(\.\d{3})*|\d+", t) is not None, f"número no reconocido: {t!r}")
    return Decimal(t.replace(".", ""))


def rango_etiqueta(etiqueta: str) -> Rango:
    """
    Rangos de las tablas del Excel (plan T2):
      «0-5.000» -> [0, 5000]            «5.001-10.000» -> (5000, 10000]
      «>800.001» -> (800000, ∞)         «0-10» -> [0, 10]      «>10-15» -> (10, 15]
      «>200» -> (200, ∞)                «>0-50» -> (0, 50]     «10.000-20.000» -> (10000, 20000]
    El mínimo solo es inclusivo cuando es 0 sin «>»; el máximo siempre es inclusivo.
    """
    t = etiqueta.strip().replace(" ", "")
    mayor = t.startswith(">")
    if mayor:
        t = t[1:]
    partes = t.split("-")
    exigir(len(partes) in (1, 2) and partes[0] != "", f"rango no reconocido: {etiqueta!r}")
    exigir(len(partes) == 2 or mayor, f"rango sin máximo y sin «>»: {etiqueta!r}")
    minimo = numero_es(partes[0])
    if re.fullmatch(r"\d{1,3}(\.\d{3})*\.001", partes[0]):
        minimo -= 1                                 # «5.001» = «más de 5.000»
    maximo = numero_es(partes[1]) if len(partes) == 2 else None
    return Rango(minimo, minimo == 0 and not mayor, maximo, maximo is not None)


def rango_potencia_iberdrola(etiqueta: str) -> Rango:
    """«Pc ≤ 5 kW» -> [0, 5] ; «5 kW < Pc ≤ 10 kW» -> (5, 10] ; «10kw < Pc ≤ 15 (…)» -> (10, 15]."""
    m = re.search(r"(?:(\d+)\s*kw\s*<\s*)?pc\s*≤\s*(\d+)", etiqueta, re.IGNORECASE)
    exigir(m is not None, f"potencia de Iberdrola no reconocida: {etiqueta!r}")
    if m.group(1) is None:
        return Rango(Decimal(0), True, Decimal(m.group(2)), True)
    return Rango(Decimal(m.group(1)), False, Decimal(m.group(2)), True)


def rango_consumo_iberdrola(etiqueta: str) -> Rango:
    """«20KW> Pc Consumo 10.000kwh-20.000kwh» -> (10000, 20000]."""
    m = re.search(r"consumo\s*(.+)$", etiqueta, re.IGNORECASE)
    exigir(m is not None, f"consumo de Iberdrola no reconocido: {etiqueta!r}")
    return rango_etiqueta(re.sub(r"kwh", "", m.group(1), flags=re.IGNORECASE))


def exigir_contiguos(rangos: list, que: str) -> None:
    """Cada rango empieza donde acaba el anterior, sin huecos ni solapes."""
    for a, b in zip(rangos, rangos[1:]):
        exigir(a.maximo is not None and a.maximo == b.minimo and a.max_inc and not b.min_inc,
               f"{que}: los rangos {a.texto()} y {b.texto()} no son contiguos")


# ---------------------------------------------------------------------------
# Matrices del Excel
# ---------------------------------------------------------------------------
def matriz_consumo_potencia(ws, fila_cab: int, filas: range, col_ini: str, col_fin: str,
                            tarifa: str) -> tuple:
    """Tablas pymes luz de ENDESA: consumo (columna B) × potencia (fila de cabecera)."""
    cabecera = texto_celda(ws, f"B{fila_cab}")
    exigir(tarifa.replace(" ", "") in cabecera.replace(" ", ""),
           f"ENDESA!B{fila_cab} debería ser la tabla {tarifa} y es «{cabecera}»")
    cols = columnas(col_ini, col_fin)
    potencias = [rango_etiqueta(texto_celda(ws, f"{c}{fila_cab}")) for c in cols]
    consumos = [rango_etiqueta(texto_celda(ws, f"B{f}")) for f in filas]
    exigir_contiguos(potencias, f"ENDESA {tarifa} potencias")
    exigir_contiguos(consumos, f"ENDESA {tarifa} consumos")
    celdas, omitidas = [], []
    for f, consumo in zip(filas, consumos):
        for c, potencia in zip(cols, potencias):
            v = ws[f"{c}{f}"].value
            if not es_numero(v):
                omitidas.append(f"{c}{f} = {v!r}")
                continue
            celdas.append(Celda(f"{c}{f}", [Dim("consumo_kwh", consumo), Dim("potencia_kw", potencia)], importe(v)))
    return celdas, omitidas


def matriz_iberdrola(ws, fila_zona: int, fila_oferta: int, filas: range, dimension: str, lector) -> tuple:
    """Tablas «fuera de ámbito» de IBERDROLA: fila × zona (ZBR C:F, ZAR G:J) × oferta."""
    exigir(texto_celda(ws, f"C{fila_zona}") == "ZBR" and texto_celda(ws, f"G{fila_zona}") == "ZAR",
           f"IBERDROLA!C{fila_zona}/G{fila_zona} deberían ser ZBR/ZAR")
    cols = columnas("C", "J")
    ofertas = {}
    for c in cols:
        t = texto_celda(ws, f"{c}{fila_oferta}")
        m = re.fullmatch(r"Oferta (\d)", t)
        exigir(m is not None, f"IBERDROLA!{c}{fila_oferta} debería ser «Oferta N» y es «{t}»")
        ofertas[c] = f"OFERTA_{m.group(1)}"
    rangos = [lector(texto_celda(ws, f"A{f}")) for f in filas]
    exigir_contiguos(rangos, f"IBERDROLA {dimension}")
    celdas, omitidas = [], []
    for f, rango in zip(filas, rangos):
        for c in cols:
            v = ws[f"{c}{f}"].value
            if not es_numero(v):
                omitidas.append(f"{c}{f} = {v!r}")
                continue
            zona = "ZBR" if c <= "F" else "ZAR"
            celdas.append(Celda(f"{c}{f}", [Dim(dimension, rango), Dim("zona_comision", categoria=zona),
                                             Dim("codigo_producto", categoria=ofertas[c])], importe(v)))
    return celdas, omitidas


def tramo(ws, ref_etiqueta: str, etiqueta_esperada: str, ref_valor: str, unidad: str,
          desde: int, hasta: Optional[int], desde_inc: bool, hasta_inc: bool,
          tipo_valor: str = "PORCENTAJE") -> Tramo:
    """Tramo de retrocesión: los límites se escriben aquí (legibles) y se comprueba la etiqueta del Excel."""
    etiqueta = texto_celda(ws, ref_etiqueta)
    exigir(etiqueta == etiqueta_esperada,
           f"{ws.title}!{ref_etiqueta} debería ser «{etiqueta_esperada}» y es «{etiqueta}»")
    return Tramo(unidad, desde, hasta, desde_inc, hasta_inc, factor(ws[ref_valor].value), tipo_valor,
                 ref_valor, etiqueta)


def tabla_endesa(tarifa: str) -> str:
    return (f"Tabla por consumo anual y potencia para la tarifa {tarifa}. Si el contrato no encaja en ninguna "
            "casilla, la comisión se revisa a mano.")


def cond(**kw) -> dict:
    """Condiciones en el orden en que las evalúa el motor (legible en el JSON)."""
    orden = ["tipo_suministro", "segmento", "ambito", "tarifa_acceso", "codigo_producto", "es_renovacion",
             "consumo_min_kwh", "consumo_min_inclusivo", "consumo_max_kwh", "consumo_max_inclusivo",
             "potencia_min_kw", "potencia_min_inc", "potencia_max_kw", "potencia_max_inc"]
    exigir(set(kw) <= set(orden), f"condición no soportada: {set(kw) - set(orden)}")
    return {k: kw[k] for k in orden if k in kw}


# ---------------------------------------------------------------------------
# Contenido (plan T2). Hoja2 prevalece; el Excel queda anotado.
# ---------------------------------------------------------------------------
def construir(wb, h2: Hoja2, hash_comisiones: str, hash_listado: str) -> list:
    ws_endesa, ws_iber, ws_iris = wb["ENDESA"], wb["IBERDROLA"], wb["IRIS"]
    ws_nat, ws_vis, ws_tot = wb["NATURGY"], wb["VISALIA"], wb["TOTAL"]
    resultado = []

    # ======================================================================= ENDESA
    vig = fecha_celda(ws_endesa, "F1")
    k3 = h2.exige("Hoja2", "K3")
    exigir(importe(ws_endesa["C7"].value) == importe(ws_endesa["C8"].value)
           and importe(ws_endesa["C9"].value) == importe(ws_endesa["C10"].value) == importe(ws_endesa["C11"].value),
           "ENDESA!C7:C11 ya no son dos importes (≤10 kW / >10 kW): revise la anotación")
    excel_residencial = (f"Excel ENDESA C7:C11 indica {euros(importe(ws_endesa['C7'].value))} ≤10 kW "
                         f"y {euros(importe(ws_endesa['C9'].value))} >10 kW")
    c20, o20 = matriz_consumo_potencia(ws_endesa, 17, range(18, 29), "C", "D", "2.0TD")
    c30, o30 = matriz_consumo_potencia(ws_endesa, 30, range(31, 46), "C", "I", "3.0TD")
    c61, o61 = matriz_consumo_potencia(ws_endesa, 47, range(48, 63), "C", "H", "6.1TD")
    pyme_luz = dict(tipo_suministro="LUZ", segmento="PYME")
    endesa = Comercializadora("ENDESA", "ENDESA", [
        f"Hoja2 K3 (cliente): pyme = matriz de la tarifa de acceso (potencia × consumo), residencial =",
        f"110 € con menos de 10 kW y 120 € con 10 kW o más, renovación y gas = manual.",
        f"Excel hoja ENDESA, actualización F1 = {vig.isoformat()}.",
    ])
    endesa.conjuntos.append(Conjunto(
        f"ENDESA-FIJO-{vig:%Y%m%d}", f"{vig:%Y.%m.%d}", "FIJO", vig, ARCHIVO_COMISIONES, "ENDESA", vig,
        hash_comisiones, [
            Regla("ENDESA_RENOVACION_MANUAL", 10, "MANUAL", cond(es_renovacion=True),
                  f"{h2.exige('Hoja2', 'K3', 'Si está marcado SI en renovación se introduce manual')} (cliente): "
                  "renovación = manual. Excel ENDESA B83:I130 (renovación pymes) y F11 (B2C no comisiona) no se usan",
                  motivo="Endesa: las renovaciones se introducen manualmente (Hoja2)",
                  nombre="Endesa · renovaciones (a mano)",
                  descripcion="Las renovaciones de Endesa no se calculan: complete usted la comisión a mano."),
            Regla("ENDESA_GAS_MANUAL", 20, "MANUAL", cond(tipo_suministro="GAS", es_renovacion=False),
                  f"{h2.exige('Hoja2', 'K3', 'si es gas se introduce manual')} (cliente): gas = manual. "
                  "Excel ENDESA C12:C14 (gas B2C) y B64:E74 (gas pymes) no se usan",
                  motivo="Endesa: el gas se introduce manualmente (Hoja2)",
                  nombre="Endesa · gas (a mano)",
                  descripcion="El gas de Endesa no se calcula: complete usted la comisión a mano."),
            Regla("ENDESA_RESIDENCIAL_MENOS_10KW", 30, "FIXED_AMOUNT",
                  cond(tipo_suministro="LUZ", segmento="RESIDENCIAL", es_renovacion=False,
                       potencia_max_kw=10, potencia_max_inc=False),
                  f"{h2.exige('Hoja2', 'K3', 'si es menos de 10 kW son 110€')} (cliente): menos de 10 kW = 110 €. "
                  f"{excel_residencial}",
                  calculo={"importe": Decimal("110")},
                  nombre="Endesa · residencial menos de 10 kW",
                  descripcion="Importe fijo de 110 € (Hoja2) para luz residencial con menos de 10 kW de potencia."),
            Regla("ENDESA_RESIDENCIAL_10KW_O_MAS", 40, "FIXED_AMOUNT",
                  cond(tipo_suministro="LUZ", segmento="RESIDENCIAL", es_renovacion=False,
                       potencia_min_kw=10, potencia_min_inc=True),
                  f"{h2.exige('Hoja2', 'K3', 'si es igual o más de 10 kW son 120€')} (cliente): 10 kW o más = 120 €. "
                  f"{excel_residencial}",
                  calculo={"importe": Decimal("120")},
                  nombre="Endesa · residencial 10 kW o más",
                  descripcion="Importe fijo de 120 € (Hoja2) para luz residencial con 10 kW de potencia o más."),
            Regla("ENDESA_PYME_LUZ_2_0TD", 50, "MATRIX_LOOKUP",
                  cond(**pyme_luz, tarifa_acceso="2.0TD", es_renovacion=False),
                  f"Excel ENDESA B18:D28 (potencias C17:D17), consumo kWh × potencia kW redondeado a 2 decimales. "
                  f"{h2.exige('Hoja2', 'K3', 'se busca la potencia contratada y el consumo anual')}: pyme = tabla "
                  "de la tarifa. Más de 15 kW: sin celda (revisión manual)",
                  celdas=c20, omitidas=o20, comentario="Pymes luz 2.0TD: ENDESA B17:D28",
                  nombre="Endesa · pyme luz 2.0TD", descripcion=tabla_endesa("2.0TD")),
            Regla("ENDESA_PYME_LUZ_3_0TD", 60, "MATRIX_LOOKUP",
                  cond(**pyme_luz, tarifa_acceso="3.0TD", es_renovacion=False),
                  f"Excel ENDESA B31:I45 (potencias C30:I30), consumo kWh × potencia kW redondeado a 2 decimales. "
                  f"{k3}: pyme = tabla de la tarifa. Menos de 15 kW: sin celda (revisión manual)",
                  celdas=c30, omitidas=o30, comentario="Pymes luz 3.0TD: ENDESA B30:I45",
                  nombre="Endesa · pyme luz 3.0TD", descripcion=tabla_endesa("3.0TD")),
            Regla("ENDESA_PYME_LUZ_6_1TD", 70, "MATRIX_LOOKUP",
                  cond(**pyme_luz, tarifa_acceso="6.1TD", es_renovacion=False),
                  f"Excel ENDESA B48:H62 (potencias C47:H47), consumo kWh × potencia kW redondeado a 2 decimales. "
                  f"{k3}: pyme = tabla de la tarifa",
                  celdas=c61, omitidas=o61, comentario="Pymes luz 6.1TD: ENDESA B47:H62",
                  nombre="Endesa · pyme luz 6.1TD", descripcion=tabla_endesa("6.1TD")),
        ],
        nombre=f"Endesa {vig:%Y-%m}",
        descripcion=f"Endesa a precio fijo desde el {fecha_es(vig)}. Pymes de luz por tabla de consumo y potencia, "
                    "residencial con importe fijo. Las renovaciones y el gas los completa usted a mano."))
    endesa.politicas += [
        Politica(f"RET-ENDESA-RESIDENCIAL-{vig:%Y%m%d}", f"{vig:%Y.%m.%d}", vig, None, "RESIDENCIAL", None,
                 "PORCENTAJE",
                 "Retrocomisión Endesa doméstico por meses desde la activación (Excel ENDESA B76:E78).",
                 [tramo(ws_endesa, "D77", "0 a 3", "D78", "MESES", 0, 3, True, False),
                  tramo(ws_endesa, "E77", "3 a 12", "E78", "MESES", 3, 12, True, False)]),
        Politica(f"RET-ENDESA-PYME-{vig:%Y%m%d}", f"{vig:%Y.%m.%d}", vig, None, "PYME", None, "PORCENTAJE",
                 "Retrocomisión Endesa pymes y AFF por meses desde la activación (Excel ENDESA B80:G81).",
                 [tramo(ws_endesa, "D80", "0 a 3", "D81", "MESES", 0, 3, True, False),
                  tramo(ws_endesa, "E80", "3 a 6", "E81", "MESES", 3, 6, True, False),
                  tramo(ws_endesa, "F80", "6 a 9", "F81", "MESES", 6, 9, True, False),
                  tramo(ws_endesa, "G80", "9 a 12", "G81", "MESES", 9, 12, True, False)]),
    ]
    resultado.append(endesa)

    # ==================================================================== IBERDROLA
    vig = fecha_celda(ws_iber, "F2")
    k9 = h2.exige("Hoja2", "K9")
    c_pot, o_pot = matriz_iberdrola(ws_iber, 8, 9, range(10, 14), "potencia_kw", rango_potencia_iberdrola)
    c_con, o_con = matriz_iberdrola(ws_iber, 15, 16, range(17, 25), "consumo_kwh", rango_consumo_iberdrola)
    exigir(texto_celda(ws_iber, "C25") == "Consultar", "IBERDROLA!C25 debería decir «Consultar» (>200.000 kWh)")
    o_con.append("A25:C25 >200.000 kWh = 'Consultar'")
    luz_fuera = dict(tipo_suministro="LUZ", ambito="FUERA", es_renovacion=False)
    iber = Comercializadora("IBERDROLA", "IBERDROLA", [
        "Hoja2 K9 (cliente): dentro de ámbito, indexado y gas = manual. Fuera de ámbito = tabla por potencia",
        "(y por consumo si Pc > 20 kW), zona ZBR/ZAR y oferta (tarifa de precios del contrato).",
        f"Excel hoja IBERDROLA, actualización F2 = {vig.isoformat()}. Indexado: sin conjunto INDEXADO (queda manual).",
    ])
    planes = {}
    for f in range(86, 93):
        oferta = texto_celda(ws_iber, f"B{f}")
        m = re.fullmatch(r"OFERTA (\d)", oferta)
        exigir(m is not None, f"IBERDROLA!B{f} debería ser «OFERTA N» y es «{oferta}»")
        tarifa = "2.0TD" if f <= 88 else "3.0TD/6.1TD"
        exigir(("2.0" in texto_celda(ws_iber, "A86")) and ("3.0TD" in texto_celda(ws_iber, "A89")),
               "IBERDROLA!A86/A89 deberían ser 2.0 TD / 3.0TD")
        planes.setdefault(m.group(1), []).append((f, texto_celda(ws_iber, f"C{f}"), tarifa))
    for n in sorted(planes):
        partes = [f"{texto} ({tarifa})" for _, texto, tarifa in planes[n]]
        refs = ",".join(f"C{f}" for f, _, _ in planes[n])
        iber.productos.append(Producto(f"OFERTA_{n}", f"Oferta {n} — " + " · ".join(partes), "FIJO", "LUZ", vig,
                                       f"IBERDROLA {refs}"))
    exigir(sorted(planes) == ["0", "1", "2", "3"], "IBERDROLA A86:C92 debería listar las ofertas 0 a 3")
    iber.conjuntos.append(Conjunto(
        f"IBERDROLA-FIJO-{vig:%Y%m%d}", f"{vig:%Y.%m.%d}", "FIJO", vig, ARCHIVO_COMISIONES, "IBERDROLA", vig,
        hash_comisiones, [
            Regla("IBERDROLA_RENOVACION_MANUAL", 10, "MANUAL", cond(es_renovacion=True),
                  "Sin tabla de renovaciones en el Excel IBERDROLA ni regla en la Hoja2 K9",
                  motivo="Iberdrola: las renovaciones se introducen manualmente (el Excel no tiene tabla de renovaciones)",
                  nombre="Iberdrola · renovaciones (a mano)",
                  descripcion="El Excel de Iberdrola no trae tabla de renovaciones: complete usted la comisión a mano."),
            Regla("IBERDROLA_DENTRO_AMBITO_MANUAL", 20, "MANUAL",
                  cond(tipo_suministro="LUZ", ambito="DENTRO", es_renovacion=False),
                  f"{h2.exige('Hoja2', 'K9', 'si está dentro se hace manual')}: dentro de ámbito manual. "
                  "Excel IBERDROLA A42:J77 (dentro de ámbito) no se usa",
                  motivo="Iberdrola: dentro de ámbito se introduce manualmente (Hoja2)",
                  nombre="Iberdrola · dentro de ámbito (a mano)",
                  descripcion="Los contratos de luz dentro de ámbito no se calculan: complete usted la comisión a mano."),
            Regla("IBERDROLA_GAS_MANUAL", 30, "MANUAL", cond(tipo_suministro="GAS", es_renovacion=False),
                  f"{h2.exige('Hoja2', 'K9', 'si es gas se introduce manual')}: gas manual, dentro o fuera de ámbito. "
                  "Excel IBERDROLA A28:F34 y A65:F71 (gas) no se usan",
                  motivo="Iberdrola: el gas se introduce manualmente (Hoja2)",
                  nombre="Iberdrola · gas (a mano)",
                  descripcion="El gas de Iberdrola, dentro o fuera de ámbito, no se calcula: complete usted la "
                              "comisión a mano."),
            Regla("IBERDROLA_FUERA_HASTA_20KW", 40, "MULTI_DIMENSION_LOOKUP",
                  cond(**luz_fuera, potencia_max_kw=20, potencia_max_inc=True),
                  f"Excel IBERDROLA A8:J13: potencia × zona (ZBR C:F, ZAR G:J) × oferta. «N. Aplica» "
                  f"(F10:F12, J10:J12) sin celda = revisión manual. {k9}: fuera de ámbito por tabla",
                  celdas=c_pot, omitidas=o_pot, comentario="Fuera de ámbito, Pc ≤ 20 kW: IBERDROLA A8:J13",
                  nombre="Iberdrola · fuera de ámbito hasta 20 kW",
                  descripcion="Tabla por potencia, zona (ZBR o ZAR) y oferta. Las casillas «N. Aplica» quedan "
                              "vacías: esos contratos se revisan a mano."),
            Regla("IBERDROLA_FUERA_MAS_20KW", 50, "MULTI_DIMENSION_LOOKUP",
                  cond(**luz_fuera, potencia_min_kw=20, potencia_min_inc=False),
                  f"Excel IBERDROLA A15:J24: consumo × zona × oferta. A25 «>200.000 kWh Consultar» sin celda = "
                  f"revisión manual. A26 (1 MWh por kW sin consumo) fuera de alcance. {k9}",
                  celdas=c_con, omitidas=o_con, comentario="Fuera de ámbito, Pc > 20 kW: IBERDROLA A15:J25",
                  nombre="Iberdrola · fuera de ámbito más de 20 kW",
                  descripcion="Tabla por consumo anual, zona (ZBR o ZAR) y oferta. Con más de 200.000 kWh "
                              "(«Consultar») la comisión se revisa a mano."),
        ],
        nombre=f"Iberdrola {vig:%Y-%m}",
        descripcion=f"Iberdrola a precio fijo desde el {fecha_es(vig)}. Luz fuera de ámbito por tabla de potencia "
                    "o consumo, zona y oferta. Dentro de ámbito, renovaciones y gas los completa usted a mano."))
    exigir(texto_celda(ws_iber, "A80") == "DECOMISIÓN POR BAJA ANTICIPADA" and texto_celda(ws_iber, "A81") == "DÍAS →",
           "IBERDROLA!A80:A81 debería ser la tabla de decomisión por días")
    exigir(texto_celda(ws_iber, "A82") == "2.0TD" and texto_celda(ws_iber, "A83") == "3.0TD y 6.1TD",
           "IBERDROLA!A82:A83 deberían ser 2.0TD / 3.0TD y 6.1TD")
    tope = texto_celda(ws_iber, "A84")
    exigir("nunca será superior a la comisión liquidada" in tope, "IBERDROLA!A84 ya no indica el tope")
    aviso = ("Unidades del Excel sin confirmar (v2 §11.2): confirmar con el cliente antes de publicar. "
             "Excel IBERDROLA A80:F84 (días desde la activación). El Excel no cubre el día 90 («< 90» y «91-180»). "
             "A84: la decomisión nunca supera la comisión liquidada.")

    def tramos_iber(fila: int) -> list:
        sin_confirmar = "SIN_CONFIRMAR"
        return [tramo(ws_iber, "B81", "< 90", f"B{fila}", "DIAS", 0, 90, True, False),
                tramo(ws_iber, "C81", "91-180", f"C{fila}", "DIAS", 90, 180, False, True, sin_confirmar),
                tramo(ws_iber, "D81", "181-270", f"D{fila}", "DIAS", 180, 270, False, True, sin_confirmar),
                tramo(ws_iber, "E81", "271-330", f"E{fila}", "DIAS", 270, 330, False, True, sin_confirmar),
                tramo(ws_iber, "F81", "331-365", f"F{fila}", "DIAS", 330, 365, False, True, sin_confirmar)]

    # La fila A83 («3.0TD y 6.1TD») se siembra como dos políticas, una por tarifa: una
    # sola política sin tarifa cubriría también la 2.0TD y cualquier otra tarifa.
    iber.politicas += [
        Politica(f"RET-IBERDROLA-2_0TD-{vig:%Y%m%d}", f"{vig:%Y.%m.%d}", vig, "LUZ", None, "2.0TD", "PORCENTAJE",
                 aviso + " Fila A82 (2.0TD): 20 / 12 / 8 / 4 sin unidad.", tramos_iber(82)),
    ] + [
        Politica(f"RET-IBERDROLA-{tarifa.replace('.', '_')}-{vig:%Y%m%d}", f"{vig:%Y.%m.%d}", vig, "LUZ", None, tarifa,
                 "PORCENTAJE", aviso + f" Fila A83 (3.0TD y 6.1TD), política para {tarifa}: C83:F83 sin unidad.",
                 tramos_iber(83))
        for tarifa in ("3.0TD", "6.1TD")
    ]
    resultado.append(iber)

    # ========================================================================= IRIS
    vig = fecha_celda(ws_iris, "K2")
    exigir(factor(ws_iris["Z27"].value) == Decimal("0.44") and "fee ≥5" in texto_celda(ws_iris, "AA27"),
           "IRIS!Z27:AA27 ya no es 44 % con FEE ≥ 5")
    exigir(importe(ws_iris["F8"].value) == importe(ws_iris["F9"].value) == Decimal("40")
           and importe(ws_iris["G10"].value) == importe(ws_iris["G12"].value) == Decimal("64"),
           "IRIS!F8:G12 ya no son 40 € / 64 €")
    exigir("365 días con prorrateo" in texto_celda(ws_iris, "W58"), "IRIS!W58 ya no indica el prorrateo a 365 días")
    k15 = h2.exige("Hoja2", "K15")
    iris = Comercializadora("IRIS", "IRIS", [
        "Hoja2 K15 (cliente): pyme = consumo (MWh) × FEE × 44 %, residencial = 40 € (< 5.000 kWh) o 64 €",
        "(5.000 kWh o más), gas = manual. Hoja1 E42: indexado manual (sin conjunto INDEXADO).",
        "Se amplía el conjunto IRIS-FIJO-20260304 de V20260913_2 solo si sigue en BORRADOR y lo creó seed-controlado.",
    ])
    iris.conjuntos.append(Conjunto(
        f"IRIS-FIJO-{vig:%Y%m%d}", f"{vig:%Y.%m.%d}", "FIJO", vig, ARCHIVO_COMISIONES, "IRIS", vig, hash_comisiones, [
            Regla("IRIS_PYME_LUZ", 20, "FEE_PERCENTAGE",
                  cond(tipo_suministro="LUZ", segmento="PYME", es_renovacion=False),
                  f"{h2.exige('Hoja2', 'K15', 'el fee y el 44%')}: pyme = consumo (MWh) × FEE × 44 %. "
                  "Excel IRIS Z27:AA27: 44 % siempre que FEE ≥ 5",
                  calculo={"porcentaje_aplicacion_fee": Decimal("0.44"), "fee_minimo_eur_mwh": Decimal("5")},
                  nombre="Iris · pyme luz",
                  descripcion="Consumo anual (MWh) × FEE del contrato × 44 % (Hoja2). Con un FEE inferior a "
                              "5 €/MWh la "
                              "comisión se revisa a mano."),
            Regla("IRIS_RESIDENCIAL_MENOS_5000", 30, "FIXED_AMOUNT",
                  cond(tipo_suministro="LUZ", segmento="RESIDENCIAL", es_renovacion=False,
                       consumo_max_kwh=5000, consumo_max_inclusivo=False),
                  f"{h2.exige('Hoja2', 'K15', 'si es menor es 40€')}: residencial < 5.000 kWh = 40 €. "
                  "Excel IRIS F8:F9 (NEW RESIDENC PROMO) = 40 € hasta 4.999 kWh",
                  calculo={"importe": Decimal("40")},
                  nombre="Iris · residencial menos de 5.000 kWh",
                  descripcion="Importe fijo de 40 € (Hoja2) para luz residencial con menos de 5.000 kWh al año."),
            Regla("IRIS_RESIDENCIAL_5000_O_MAS", 40, "FIXED_AMOUNT",
                  cond(tipo_suministro="LUZ", segmento="RESIDENCIAL", es_renovacion=False,
                       consumo_min_kwh=5000, consumo_min_inclusivo=True),
                  f"{h2.exige('Hoja2', 'K15', 'si es mayor de 5000 kWh el importe es 64€')}: residencial 5.000 kWh "
                  "o más = 64 €. Excel IRIS G10:G12 (NEW PROMOCIONAL) = 64 € solo de 5.000 a 14.999 kWh",
                  calculo={"importe": Decimal("64")},
                  nombre="Iris · residencial 5.000 kWh o más",
                  descripcion="Importe fijo de 64 € (Hoja2) para luz residencial con 5.000 kWh al año o más."),
            Regla("IRIS_GAS_MANUAL", 50, "MANUAL", cond(tipo_suministro="GAS", es_renovacion=False),
                  f"{h2.exige('Hoja2', 'K15', 'Si es gas se introduce manual')} (cliente): gas = manual. "
                  "Excel IRIS X28:AA28 indica gas "
                  "44 % con FEE ≥ 5 y B71:R115 tablas de gas: no se usan",
                  motivo="Iris: el gas se introduce manualmente (Hoja2)",
                  nombre="Iris · gas (a mano)",
                  descripcion="El gas de Iris no se calcula: complete usted la comisión a mano."),
            Regla("IRIS_RENOVACION_MANUAL", 60, "MANUAL", cond(es_renovacion=True),
                  "Excel IRIS W45:W53: las renovaciones dependen del mes y de si se firma contrato nuevo. "
                  f"{k15} no las calcula: se completan a mano",
                  motivo="Iris: las renovaciones se introducen manualmente",
                  nombre="Iris · renovaciones (a mano)",
                  descripcion="Las renovaciones de Iris dependen del mes y de si se firma contrato nuevo: complete "
                              "usted la comisión a mano."),
        ],
        existente=True,
        desactivar=[("IRIS_PLAN_PERSONALIZADO_PYME",
                     "Sustituida por IRIS_PYME_LUZ (Hoja2 K15: pyme = consumo × FEE × 44 % sin depender del producto)")],
        nombre=f"Iris {vig:%Y-%m}",
        descripcion=f"Iris a precio fijo desde el {fecha_es(vig)}. Pymes de luz: 44 % del FEE. Residencial: 40 € o "
                    "64 € según el consumo. Las renovaciones y el gas los completa usted a mano.",
        anteriores=[ReglaAnterior(
            "IRIS_PLAN_PERSONALIZADO_PYME", "Iris · Plan Personalizado",
            "Regla anterior, desactivada: 44 % del FEE con un FEE mínimo de 5 €/MWh, solo para la tarifa Plan "
            "Personalizado. La sustituye «Iris · pyme luz», que no depende de la tarifa de precios.")]))
    iris.politicas.append(Politica(
        f"RET-IRIS-{vig:%Y%m%d}", f"{vig:%Y.%m.%d}", vig, None, None, None, "PRORRATEO",
        "Retrocomisión prorrateada a 365 días (Excel IRIS W58).", []))
    resultado.append(iris)

    # ================================================================= UNIELÉCTRICA
    k20 = h2.exige("Hoja2", "K20")
    vig = dt.date(2026, 1, 1)   # sin hoja en el Excel de comisiones: fecha fijada en el plan (T2)
    residencial_luz = dict(tipo_suministro="LUZ", segmento="RESIDENCIAL", es_renovacion=False)
    uniel = Comercializadora("UNIELECTRICA", "UNIELÉCTRICA", [
        "Hoja2 K20 (cliente): pyme = consumo (MWh) × FEE × 55 %, residencial = 50 € (< 5.000 kWh) u 80 €",
        "(5.000 kWh o más), gas = manual. Sin hoja en el Excel de comisiones: origen LISTADO / Hoja2.",
        "Hoja1 E53: indexado manual (sin conjunto INDEXADO).",
    ])
    uniel.conjuntos.append(Conjunto(
        f"UNIELECTRICA-FIJO-{vig:%Y%m%d}", f"{vig:%Y.%m.%d}", "FIJO", vig, ARCHIVO_LISTADO, "Hoja2", None,
        hash_listado, [
            Regla("UNIELECTRICA_RENOVACION_MANUAL", 10, "MANUAL", cond(es_renovacion=True),
                  f"{k20} no define comisión de renovación: se completa a mano",
                  motivo="Unieléctrica: las renovaciones se introducen manualmente (sin regla en la Hoja2)",
                  nombre="Unieléctrica · renovaciones (a mano)",
                  descripcion="La Hoja2 no define comisión de renovación para Unieléctrica: complete usted la "
                              "comisión a mano."),
            Regla("UNIELECTRICA_GAS_MANUAL", 20, "MANUAL", cond(tipo_suministro="GAS", es_renovacion=False),
                  f"{h2.exige('Hoja2', 'K20', 'Si es gas se introduce manual')} (cliente): gas = manual. "
                  "Sin hoja en el Excel de comisiones",
                  motivo="Unieléctrica: el gas se introduce manualmente (Hoja2)",
                  nombre="Unieléctrica · gas (a mano)",
                  descripcion="El gas de Unieléctrica no se calcula: complete usted la comisión a mano."),
            Regla("UNIELECTRICA_PYME_LUZ", 30, "FEE_PERCENTAGE",
                  cond(tipo_suministro="LUZ", segmento="PYME", es_renovacion=False),
                  f"{h2.exige('Hoja2', 'K20', 'el fee y el 55%')}: pyme = consumo (MWh) × FEE × 55 %. "
                  "Sin FEE mínimo en la Hoja2",
                  calculo={"porcentaje_aplicacion_fee": Decimal("0.55")},
                  nombre="Unieléctrica · pyme luz",
                  descripcion="Consumo anual (MWh) × FEE del contrato × 55 % (Hoja2)."),
            Regla("UNIELECTRICA_RESIDENCIAL_MENOS_5000", 40, "FIXED_AMOUNT",
                  cond(**residencial_luz, consumo_max_kwh=5000, consumo_max_inclusivo=False),
                  f"{h2.exige('Hoja2', 'K20', 'si es menor es 50€')}: residencial < 5.000 kWh = 50 €",
                  calculo={"importe": Decimal("50")},
                  nombre="Unieléctrica · residencial menos de 5.000 kWh",
                  descripcion="Importe fijo de 50 € (Hoja2) para luz residencial con menos de 5.000 kWh al año."),
            Regla("UNIELECTRICA_RESIDENCIAL_5000_O_MAS", 50, "FIXED_AMOUNT",
                  cond(**residencial_luz, consumo_min_kwh=5000, consumo_min_inclusivo=True),
                  f"{h2.exige('Hoja2', 'K20', 'si es mayor de 5000 kWh el importe es 80€')}: residencial "
                  "5.000 kWh o más = 80 €",
                  calculo={"importe": Decimal("80")},
                  nombre="Unieléctrica · residencial 5.000 kWh o más",
                  descripcion="Importe fijo de 80 € (Hoja2) para luz residencial con 5.000 kWh al año o más."),
        ],
        nombre=f"Unieléctrica {vig:%Y-%m}",
        descripcion=f"Unieléctrica a precio fijo desde el {fecha_es(vig)}. Pymes de luz: 55 % del FEE. Residencial: "
                    "50 € u 80 € según el consumo. Las renovaciones y el gas los completa usted a mano."))
    resultado.append(uniel)

    # ====================================================================== NATURGY
    vig = fecha_celda(ws_nat, "J2")
    k26 = h2.exige("Hoja2", "K26")
    h2.exige("Hoja1", "C66", "fee gas")
    h2.exige("Hoja1", "E66", "manual")
    h2.exige("Hoja1", "D60", "luz y gas")
    exigir(texto_celda(ws_nat, "E7") == "FIJO" and texto_celda(ws_nat, "H7") == "VARIABLE",
           "NATURGY!E7/H7 deberían ser FIJO / VARIABLE")
    nat = Comercializadora("NATURGY", "NATURGY", [
        "Hoja2 K26 (cliente): residencial = 50,63 € (luz y gas), pyme luz < 10.000 kWh = 40,50 €,",
        "pyme luz 10.000 kWh o más = consumo (MWh) × 4,05 €. Hoja1 E66: FEE de gas pyme manual.",
        f"Excel hoja NATURGY, vigencia J2 = {vig.isoformat()}. Productos E8:J8 para el desplegable del contrato.",
    ])
    for c in columnas("E", "J"):
        nombre = texto_celda(ws_nat, f"{c}8")
        exigir(re.fullmatch(r"P[FV]( [A-Z]+)+", nombre) is not None, f"NATURGY!{c}8 no parece un producto: «{nombre}»")
        nat.productos.append(Producto(nombre.replace(" ", "_"), nombre, "FIJO" if c <= "G" else "INDEXADO", "LUZ",
                                      vig, f"NATURGY {c}8"))
    nat.conjuntos.append(Conjunto(
        f"NATURGY-FIJO-{vig:%Y%m%d}", f"{vig:%Y.%m.%d}", "FIJO", vig, ARCHIVO_COMISIONES, "NATURGY", vig,
        hash_comisiones, [
            Regla("NATURGY_RENOVACION_MANUAL", 10, "MANUAL", cond(es_renovacion=True),
                  f"{k26} y el Excel NATURGY no definen comisión de renovación: se completa a mano",
                  motivo="Naturgy: las renovaciones se introducen manualmente (sin regla en la Hoja2)",
                  nombre="Naturgy · renovaciones (a mano)",
                  descripcion="Ni la Hoja2 ni el Excel de Naturgy definen comisión de renovación: complete usted la "
                              "comisión a mano."),
            Regla("NATURGY_RESIDENCIAL", 20, "FIXED_AMOUNT", cond(segmento="RESIDENCIAL", es_renovacion=False),
                  f"{h2.exige('Hoja2', 'K26', 'precio fijo de 50,63€')} (cliente): residencial = 50,63 € luz y gas "
                  f"(Hoja1 D60). Excel NATURGY C30 (luz) y C32 (gas) = "
                  f"{excel_txt(ws_nat['C30'].value)} € y {excel_txt(ws_nat['C32'].value)} €",
                  calculo={"importe": Decimal("50.63")},
                  nombre="Naturgy · residencial",
                  descripcion="Importe fijo de 50,63 € (Hoja2) para residencial, de luz o de gas."),
            Regla("NATURGY_PYME_LUZ_MENOS_10MWH", 30, "FIXED_AMOUNT",
                  cond(tipo_suministro="LUZ", segmento="PYME", es_renovacion=False,
                       consumo_max_kwh=10000, consumo_max_inclusivo=False),
                  f"{h2.exige('Hoja2', 'K26', 'menor de 10.000 kWh también es precio fijo de 40,50€')} (cliente): "
                  f"pyme < 10.000 kWh = 40,50 €. Excel NATURGY E9:J9 varía por producto "
                  f"({' / '.join(excel_txt(ws_nat[f'{c}9'].value) for c in 'EFG')} €)",
                  calculo={"importe": Decimal("40.50")},
                  nombre="Naturgy · pyme luz menos de 10.000 kWh",
                  descripcion="Importe fijo de 40,50 € (Hoja2) para pymes de luz con menos de 10.000 kWh al año."),
            Regla("NATURGY_PYME_LUZ_10MWH_O_MAS", 40, "RATE_PER_MWH",
                  cond(tipo_suministro="LUZ", segmento="PYME", es_renovacion=False,
                       consumo_min_kwh=10000, consumo_min_inclusivo=True),
                  f"{h2.exige('Hoja2', 'K26', 'el fee que es fijo de 4,05€')} (cliente): pyme 10.000 kWh o más = "
                  f"consumo (MWh) × 4,05 €. Excel NATURGY E10:J10 varía por producto "
                  f"({' / '.join(excel_txt(ws_nat[f'{c}10'].value) for c in 'EFG')} €/MWh)",
                  calculo={"tasa_eur_mwh": Decimal("4.05")},
                  nombre="Naturgy · pyme luz 10.000 kWh o más",
                  descripcion="Consumo anual (MWh) × 4,05 € (Hoja2) para pymes de luz con 10.000 kWh al año "
                              "o más."),
            Regla("NATURGY_PYME_GAS_MANUAL", 50, "MANUAL",
                  cond(tipo_suministro="GAS", segmento="PYME", es_renovacion=False),
                  "Hoja1 C66:E66 «fee gas … manual». Excel NATURGY B12:J16 (gas pymes) no se usa",
                  motivo="Naturgy: la comisión de gas pyme se introduce manualmente (Hoja1)",
                  nombre="Naturgy · pyme gas (a mano)",
                  descripcion="El gas de pymes de Naturgy depende del FEE de gas: complete usted la comisión a mano."),
        ],
        nombre=f"Naturgy {vig:%Y-%m}",
        descripcion=f"Naturgy a precio fijo desde el {fecha_es(vig)}. Residencial: 50,63 €. Pymes de luz: 40,50 € "
                    "con menos de 10.000 kWh y 4,05 € por MWh a partir de ahí. Las renovaciones y el gas de pymes "
                    "los completa usted a mano."))
    nat.politicas += [
        Politica(f"RET-NATURGY-PYME-{vig:%Y%m%d}", f"{vig:%Y.%m.%d}", vig, None, "PYME", None, "PORCENTAJE",
                 "Cancelación anticipada Naturgy pymes por meses desde la activación (Excel NATURGY B21:D24).",
                 [tramo(ws_nat, "C22", "<6 meses", "D22", "MESES", 0, 6, True, False),
                  tramo(ws_nat, "C23", "6-9 meses", "D23", "MESES", 6, 9, True, False),
                  tramo(ws_nat, "C24", "9-12 meses", "D24", "MESES", 9, 12, True, False)]),
        Politica(f"RET-NATURGY-RESIDENCIAL-{vig:%Y%m%d}", f"{vig:%Y.%m.%d}", vig, None, "RESIDENCIAL", None,
                 "PORCENTAJE",
                 "Cancelación anticipada Naturgy residencial (Excel NATURGY E29:G32). "
                 "El Excel no define el tramo de 2 a 3 meses: confirmar con el cliente antes de publicar.",
                 [tramo(ws_nat, "F30", "<2 meses", "G30", "MESES", 0, 2, True, False),
                  tramo(ws_nat, "F31", "3-4 meses", "G31", "MESES", 3, 4, True, True),
                  tramo(ws_nat, "F32", ">4 meses", "G32", "MESES", 4, None, False, False)]),
    ]
    resultado.append(nat)

    # ====================================================================== VISALIA
    vig = fecha_celda(ws_vis, "E2")
    k32 = h2.exige("Hoja2", "K32", "Si se elige Pyme tarifa base e indexado",
                   "Si se elige cualquier otra cosa se introduce manual")
    exigir(all(factor(ws_vis[f"C{f}"].value) == Decimal("0.52") for f in (19, 20, 22, 23)),
           "VISALIA!C19:C23 ya no es 52 % en los indexados")
    exigir("fee mínimo 1,5€/MWh" in texto_celda(ws_vis, "A29"), "VISALIA!A29 ya no indica el FEE mínimo 1,5 €/MWh")
    vis = Comercializadora("VISALIA", "VISALIA", [
        "Hoja2 K32 (cliente): «Pyme tarifa base e indexado» = consumo (MWh) × FEE × 52 %, cualquier otra cosa manual.",
        "Se mantiene VISALIA_BASE_PYME (V20260913_2) y se desactivan Fijo Comercio, Esmeralda, Jade y Cuarzo",
        "(solo si VISALIA-FIJO-20250612 sigue en BORRADOR y lo creó seed-controlado). Nuevo conjunto INDEXADO",
        "(no calcula hasta activar MCAS_INDEXED_ENABLED).",
    ])

    def loc_visalia(ref_nombre: str, ref_valor: str, unidad: str) -> str:
        return (f"Desactivada: {k32} indica manual (solo Base pyme es automática). Excel VISALIA {ref_nombre}:"
                f"{ref_valor} {texto_celda(ws_vis, ref_nombre)} = {unidad}. Activar si el cliente lo confirma")

    def desactivada_visalia(importe_excel: str) -> str:
        return (f"Desactivada: la Hoja2 indica que esta tarifa se completa a mano. Importe del Excel de "
                f"comisiones: {importe_excel}. Actívela solo si el cliente lo confirma.")

    vis.conjuntos.append(Conjunto(
        f"VISALIA-FIJO-{vig:%Y%m%d}", f"{vig:%Y.%m.%d}", "FIJO", vig, ARCHIVO_COMISIONES, "VISALIA", vig,
        hash_comisiones, [], existente=True,
        desactivar=[
            ("VISALIA_FIJO_COMERCIO_PYME", loc_visalia("A12", "B12", f"{miles(factor(ws_vis['B12'].value) * 100)} % del FEE")),
            ("VISALIA_ESMERALDA_PYME", loc_visalia("A9", "B9", f"{miles(importe(ws_vis['B9'].value))} €/MWh")),
            ("VISALIA_JADE_PYME", loc_visalia("A10", "B10", f"{miles(importe(ws_vis['B10'].value))} €/MWh")),
            ("VISALIA_CUARZO_PYME", loc_visalia("A11", "B11", f"{miles(importe(ws_vis['B11'].value))} €/MWh")),
        ],
        nombre=f"Visalia {vig:%Y-%m}",
        descripcion=f"Visalia a precio fijo desde el {fecha_es(vig)}. Solo la tarifa Base de pymes se calcula sola "
                    "(52 % del FEE). El resto de tarifas están desactivadas y las completa usted a mano.",
        anteriores=[
            ReglaAnterior("VISALIA_BASE_PYME", "Visalia · pyme Base",
                          "Consumo anual (MWh) × FEE del contrato × 52 % para la tarifa Base (Excel de "
                          "comisiones). Con un FEE inferior a "
                          "5 €/MWh la comisión se revisa a mano."),
            ReglaAnterior("VISALIA_FIJO_COMERCIO_PYME", "Visalia · pyme Fijo Comercio",
                          desactivada_visalia(f"{miles(factor(ws_vis['B12'].value) * 100)} % del FEE")),
            ReglaAnterior("VISALIA_ESMERALDA_PYME", "Visalia · pyme Esmeralda",
                          desactivada_visalia(f"{miles(importe(ws_vis['B9'].value))} €/MWh")),
            ReglaAnterior("VISALIA_JADE_PYME", "Visalia · pyme Jade",
                          desactivada_visalia(f"{miles(importe(ws_vis['B10'].value))} €/MWh")),
            ReglaAnterior("VISALIA_CUARZO_PYME", "Visalia · pyme Cuarzo",
                          desactivada_visalia(f"{miles(importe(ws_vis['B11'].value))} €/MWh")),
        ]))
    vis.conjuntos.append(Conjunto(
        f"VISALIA-INDEX-{vig:%Y%m%d}", f"{vig:%Y.%m.%d}", "INDEXADO", vig, ARCHIVO_COMISIONES, "VISALIA", vig,
        hash_comisiones, [
            Regla("VISALIA_INDEX_PYME", 10, "FEE_PERCENTAGE",
                  cond(tipo_suministro="LUZ", segmento="PYME", es_renovacion=False),
                  f"{h2.exige('Hoja2', 'K32', 'el fee y el 52%')}: pyme indexado = consumo (MWh) × FEE × 52 %. "
                  "Excel VISALIA C19:C23 = 52 %, A29: FEE mínimo 1,5 €/MWh (indexado base)",
                  calculo={"porcentaje_aplicacion_fee": Decimal("0.52"), "fee_minimo_eur_mwh": Decimal("1.5")},
                  nombre="Visalia · pyme indexado",
                  descripcion="Consumo anual (MWh) × FEE del contrato × 52 % (Hoja2). Con un FEE inferior a "
                              "1,5 €/MWh la "
                              "comisión se revisa a mano."),
        ],
        nombre=f"Visalia indexado {vig:%Y-%m}",
        descripcion=f"Visalia indexado desde el {fecha_es(vig)}. Pymes de luz: 52 % del FEE. Solo se puede activar "
                    "con el interruptor Indexado encendido."))
    resultado.append(vis)

    # =============================================================== TOTALENERGIES
    vig = fecha_celda(ws_tot, "F1")
    exigir(texto_celda(ws_tot, "O6") == "CO Referencia" and factor(ws_tot["Q6"].value) == Decimal("0.45"),
           "TOTAL!O6:Q6 ya no es CO Referencia 0,45")
    tot = Comercializadora("TOTAL", "TOTALENERGIES", [
        "Hoja2 K44 (cliente): consumo (MWh) × FEE × 45 %, renovación = manual, cualquier otra cosa manual.",
        f"Excel hoja TOTAL, versión F1 = {vig.isoformat()}.",
    ])
    tot.conjuntos.append(Conjunto(
        f"TOTAL-FIJO-{vig:%Y%m%d}", f"{vig:%Y.%m.%d}", "FIJO", vig, ARCHIVO_COMISIONES, "TOTAL", vig,
        hash_comisiones, [
            Regla("TOTAL_RENOVACION_MANUAL", 10, "MANUAL", cond(es_renovacion=True),
                  f"{h2.exige('Hoja2', 'K44', 'Si en la renovación pone que SI se introduce de manera manual')} "
                  "(cliente): renovación = manual. Excel TOTAL O20:O21 (renovaciones tácitas a ROCK TOTAL) no se usa",
                  motivo="TotalEnergies: las renovaciones se introducen manualmente (Hoja2)",
                  nombre="TotalEnergies · renovaciones (a mano)",
                  descripcion="Las renovaciones de TotalEnergies no se calculan: complete usted la comisión a mano."),
            Regla("TOTAL_LUZ", 20, "FEE_PERCENTAGE", cond(tipo_suministro="LUZ", es_renovacion=False),
                  f"{h2.exige('Hoja2', 'K44', 'el fee y el 45%')}: consumo (MWh) × FEE × 45 %. "
                  "Excel TOTAL O6:Q6 (CO Referencia) = 0,45",
                  calculo={"porcentaje_aplicacion_fee": Decimal("0.45")},
                  nombre="TotalEnergies · luz",
                  descripcion="Consumo anual (MWh) × FEE del contrato × 45 % (Hoja2)."),
        ],
        nombre=f"TotalEnergies {vig:%Y-%m}",
        descripcion=f"TotalEnergies a precio fijo desde el {fecha_es(vig)}. Luz: 45 % del FEE. Las renovaciones "
                    "las completa usted a mano."))
    resultado.append(tot)
    return resultado


# ---------------------------------------------------------------------------
# Comprobaciones previas a la escritura
# ---------------------------------------------------------------------------
TEXTO_CONDICION = ["tipo_suministro", "segmento", "ambito", "tarifa_acceso", "codigo_producto"]
RANGOS_CONDICION = [("consumo_min_kwh", "consumo_max_kwh", "consumo_min_inclusivo", "consumo_max_inclusivo"),
                    ("potencia_min_kw", "potencia_max_kw", "potencia_min_inc", "potencia_max_inc")]


def condiciones_solapan(a: dict, b: dict) -> bool:
    """Mismo criterio que McasRuleSetValidator.condicionesSolapan."""
    for k in TEXTO_CONDICION:
        if k in a and k in b and str(a[k]).upper() != str(b[k]).upper():
            return False
    if "es_renovacion" in a and "es_renovacion" in b and a["es_renovacion"] != b["es_renovacion"]:
        return False
    for mn, mx, mni, mxi in RANGOS_CONDICION:
        if mx in a and mn in b:
            if a[mx] < b[mn] or (a[mx] == b[mn] and not (a.get(mxi, False) and b.get(mni, True))):
                return False
        if mx in b and mn in a:
            if b[mx] < a[mn] or (b[mx] == a[mn] and not (b.get(mxi, False) and a.get(mni, True))):
                return False
    return True


def rangos_solapan(r1: Rango, r2: Rango) -> bool:
    if r1.maximo is not None and r2.minimo is not None:
        if r1.maximo < r2.minimo or (r1.maximo == r2.minimo and not (r1.max_inc and r2.min_inc)):
            return False
    if r2.maximo is not None and r1.minimo is not None:
        if r2.maximo < r1.minimo or (r2.maximo == r1.minimo and not (r2.max_inc and r1.min_inc)):
            return False
    return True


def celdas_solapan(a: Celda, b: Celda) -> bool:
    da = {d.dimension: d for d in a.dims}
    db = {d.dimension: d for d in b.dims}
    for dim in set(da) & set(db):
        x, y = da[dim], db[dim]
        if x.categoria is not None or y.categoria is not None:
            if (x.categoria or "").upper() != (y.categoria or "").upper():
                return False
        elif not rangos_solapan(x.rango, y.rango):
            return False
    return True


def exigir_nombre(que: str, nombre: str, descripcion: str) -> None:
    """Nombre y descripción obligatorios, dentro de su columna y aptos para el SQL."""
    exigir(bool(nombre and nombre.strip()), f"{que}: falta el nombre legible")
    exigir(bool(descripcion and descripcion.strip()), f"{que}: falta la descripción")
    exigir(nombre == nombre.strip() and len(nombre) <= MAX_NOMBRE, f"{que}: nombre de {len(nombre)} caracteres")
    # «Nueva versión» copia el nombre y el administrador puede cambiar el importe: un nombre
    # con «110 €» o «44 %» quedaría falso. Los importes van en la descripción.
    exigir("€" not in nombre and "%" not in nombre, f"{que}: el nombre «{nombre}» lleva un importe o un porcentaje")
    exigir(len(descripcion) <= MAX_DESCRIPCION, f"{que}: descripción de {len(descripcion)} caracteres")
    sql_texto(nombre)
    sql_texto(descripcion)


def comprobar_nombres(comercializadoras: list) -> None:
    nombres_conjunto = set()
    for com in comercializadoras:
        for cj in com.conjuntos:
            exigir_nombre(cj.codigo, cj.nombre, cj.descripcion)
            exigir(cj.nombre not in nombres_conjunto, f"{cj.codigo}: nombre repetido «{cj.nombre}»")
            nombres_conjunto.add(cj.nombre)
            exigir(cj.existente == bool(cj.anteriores),
                   f"{cj.codigo}: solo los conjuntos de V20260913_2 llevan reglas anteriores (y todos las llevan)")
            anteriores = [a.codigo for a in cj.anteriores]
            exigir({c for c, _ in cj.desactivar} <= set(anteriores),
                   f"{cj.codigo}: las reglas desactivadas deben estar entre las anteriores con nombre")
            todas = [(r.codigo, r.nombre, r.descripcion) for r in cj.reglas] + \
                    [(a.codigo, a.nombre, a.descripcion) for a in cj.anteriores]
            exigir(len({c for c, _, _ in todas}) == len(todas), f"{cj.codigo}: reglas repetidas")
            exigir(len({n for _, n, _ in todas}) == len(todas), f"{cj.codigo}: dos reglas con el mismo nombre")
            for codigo, nombre, descripcion in todas:
                exigir_nombre(codigo, nombre, descripcion)


def comprobar(comercializadoras: list) -> None:
    comprobar_nombres(comercializadoras)
    codigos_conjunto, codigos_politica = set(), set()
    for com in comercializadoras:
        productos = [p.codigo for p in com.productos]
        exigir(len(productos) == len(set(productos)), f"{com.titulo}: productos repetidos")
        for p in com.productos:
            exigir(len(p.codigo) <= MAX_CODIGO and len(p.nombre) <= MAX_NOMBRE_PRODUCTO,
                   f"producto {p.codigo}: código o nombre demasiado largo ({len(p.nombre)})")
        for cj in com.conjuntos:
            exigir(cj.codigo not in codigos_conjunto, f"conjunto repetido {cj.codigo}")
            exigir(not cj.existente or cj.codigo in CONJUNTOS_V20260913_2,
                   f"{cj.codigo} no es un conjunto de V20260913_2 ({', '.join(sorted(CONJUNTOS_V20260913_2))})")
            codigos_conjunto.add(cj.codigo)
            reglas = [r.codigo for r in cj.reglas]
            exigir(len(reglas) == len(set(reglas)), f"{cj.codigo}: reglas repetidas")
            for r in cj.reglas:
                exigir(len(r.codigo) <= MAX_CODIGO, f"{r.codigo}: código demasiado largo")
                exigir(len(r.localizador) <= MAX_LOCALIZADOR,
                       f"{r.codigo}: localizador de {len(r.localizador)} caracteres (máx. {MAX_LOCALIZADOR})")
                exigir(r.motivo is None or len(r.motivo) <= MAX_MOTIVO, f"{r.codigo}: motivo demasiado largo")
                exigir((r.metodo == "MANUAL") == (r.motivo is not None), f"{r.codigo}: MANUAL exige motivo y solo MANUAL")
                es_tabla = r.metodo in ("MATRIX_LOOKUP", "MULTI_DIMENSION_LOOKUP")
                exigir(es_tabla == bool(r.celdas), f"{r.codigo}: solo las matrices llevan celdas")
                for i, a in enumerate(r.celdas):
                    exigir(len({d.dimension for d in a.dims}) == len(a.dims) >= 2, f"{r.codigo} {a.ref}: dimensiones")
                    for d in a.dims:
                        exigir(d.categoria is None or len(d.categoria) <= MAX_CATEGORIA, f"{r.codigo}: categoría larga")
                    for b in r.celdas[i + 1:]:
                        exigir(not celdas_solapan(a, b), f"{r.codigo}: las celdas {a.ref} y {b.ref} se solapan")
            for _, loc in cj.desactivar:
                exigir(len(loc) <= MAX_LOCALIZADOR, f"{cj.codigo}: localizador de desactivación demasiado largo")
            for i, a in enumerate(cj.reglas):
                for b in cj.reglas[i + 1:]:
                    exigir(not condiciones_solapan(a.condiciones, b.condiciones),
                           f"{cj.codigo}: las reglas {a.codigo} y {b.codigo} se solapan")
        for pol in com.politicas:
            exigir(pol.codigo not in codigos_politica and len(pol.codigo) <= MAX_CODIGO, f"política {pol.codigo}")
            codigos_politica.add(pol.codigo)
            exigir(len(pol.descripcion) <= MAX_DESCRIPCION_POLITICA,
                   f"{pol.codigo}: descripción de {len(pol.descripcion)} caracteres")
            exigir((pol.metodo in ("PRORRATEO", "NO_APLICA")) == (not pol.tramos), f"{pol.codigo}: tramos")


# ---------------------------------------------------------------------------
# Escritura del SQL
# ---------------------------------------------------------------------------
def guarda_comercializadora(clave: str, alias: str = "c") -> str:
    patron = PATRONES[clave]
    return (f"{patron.format(a=alias)}\n"
            f"  AND (SELECT COUNT(*) FROM tbcomercializadoras x WHERE {patron.format(a='x')}) = 1")


def guarda_conjunto(cj: Conjunto, alias: str = "cr") -> str:
    return (f"{alias}.codigo = {sql_texto(cj.codigo)} AND {alias}.estado = 'BORRADOR' "
            f"AND {alias}.creado_por = {sql_texto(cj.creado_por)}")


def sql_productos(com: Comercializadora) -> list:
    if not com.productos:
        return []
    filas = []
    for i, p in enumerate(com.productos):
        if i == 0:
            filas.append(f"    SELECT {sql_texto(p.codigo)} AS codigo, {sql_texto(p.nombre)} AS nombre,\n"
                         f"           {sql_texto(p.tipo_precio)} AS tipo_precio, {sql_texto(p.tipo_suministro)} AS tipo_suministro,\n"
                         f"           {sql_fecha(p.activo_desde)} AS activo_desde   -- {p.ref}")
        else:
            filas.append(f"    UNION ALL SELECT {sql_texto(p.codigo)}, {sql_texto(p.nombre)},\n"
                         f"           {sql_texto(p.tipo_precio)}, {sql_texto(p.tipo_suministro)}, "
                         f"{sql_fecha(p.activo_desde)}   -- {p.ref}")
    return [
        f"-- Productos (tarifa de precios del contrato): {', '.join(p.codigo for p in com.productos)}\n"
        "INSERT INTO mcas_productos_comerciales\n"
        "    (id_comercializadora, codigo, nombre, tipo_precio, tipo_suministro, activo_desde, activo)\n"
        "SELECT c.id, s.codigo, s.nombre, s.tipo_precio, s.tipo_suministro, s.activo_desde, TRUE\n"
        "FROM tbcomercializadoras c\n"
        "CROSS JOIN (\n" + "\n".join(filas) + "\n) s\n"
        f"WHERE {guarda_comercializadora(com.clave)}\n"
        "  AND NOT EXISTS (SELECT 1 FROM mcas_productos_comerciales p\n"
        "                  WHERE p.id_comercializadora = c.id AND p.codigo = s.codigo);",
    ]


def sql_conjunto(com: Comercializadora, cj: Conjunto) -> list:
    if cj.existente:
        return [f"-- Conjunto existente {cj.codigo} (V20260913_2): se amplía solo si sigue en BORRADOR "
                f"y creado_por = '{cj.creado_por}'."]
    return [
        f"-- Conjunto {cj.codigo} (BORRADOR, fase {cj.fase}, vigente desde {cj.vigencia.isoformat()})\n"
        "INSERT INTO mcas_conjuntos_reglas\n"
        "    (codigo, version, id_comercializadora, id_gestora, fase_precio, vigencia_desde, vigencia_hasta,\n"
        "     estado, archivo_origen, hoja_origen, fecha_interna_origen, hash_origen,\n"
        "     creado_por, aprobado_por, fecha_creacion, fecha_aprobacion)\n"
        f"SELECT {sql_texto(cj.codigo)}, {sql_texto(cj.version)}, c.id, NULL, {sql_texto(cj.fase)}, "
        f"{sql_fecha(cj.vigencia)}, NULL,\n"
        f"       'BORRADOR', {sql_texto(cj.archivo)}, {sql_texto(cj.hoja)}, {sql_fecha(cj.fecha_interna)},\n"
        f"       {sql_texto(cj.hash_origen)},\n"
        f"       {sql_texto(CREADO_POR)}, NULL, CURRENT_TIMESTAMP, NULL\n"
        "FROM tbcomercializadoras c\n"
        f"WHERE {guarda_comercializadora(com.clave)}\n"
        f"  AND NOT EXISTS (SELECT 1 FROM mcas_conjuntos_reglas x WHERE x.codigo = {sql_texto(cj.codigo)});",
    ]


def sql_desactivar(cj: Conjunto) -> list:
    salida = []
    for codigo, loc in cj.desactivar:
        salida += [
            f"-- Desactivar {codigo} (la Hoja2 no la contempla)\n"
            "UPDATE mcas_reglas_comision\n"
            f"SET activo = FALSE,\n"
            f"    localizador_origen = {sql_texto(loc)}\n"
            f"WHERE codigo = {sql_texto(codigo)}\n"
            "  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr\n"
            f"                             WHERE {guarda_conjunto(cj)});",
        ]
    return salida


def describir_regla(r: Regla) -> str:
    if r.metodo == "MANUAL":
        return "MANUAL (revisión manual)"
    if r.metodo == "FIXED_AMOUNT":
        return f"FIXED_AMOUNT {euros(r.calculo['importe'])}"
    if r.metodo == "FEE_PERCENTAGE":
        extra = f", FEE mínimo {miles(r.calculo['fee_minimo_eur_mwh'])} €/MWh" if "fee_minimo_eur_mwh" in r.calculo else ""
        return f"FEE_PERCENTAGE {miles(r.calculo['porcentaje_aplicacion_fee'] * 100)} %{extra}"
    if r.metodo == "RATE_PER_MWH":
        return f"RATE_PER_MWH {miles(r.calculo['tasa_eur_mwh'])} €/MWh"
    return f"{r.metodo} ({len(r.celdas)} celdas)"


def sql_regla(cj: Conjunto, r: Regla) -> list:
    return [
        f"-- {r.codigo} · {describir_regla(r)}\n"
        "INSERT INTO mcas_reglas_comision\n"
        "    (id_conjunto_reglas, codigo, prioridad, metodo_calculo, condiciones_json, calculo_json,\n"
        "     politica_pago_json, motivo_manual, localizador_origen, activo)\n"
        f"SELECT cr.id, {sql_texto(r.codigo)}, {r.prioridad}, {sql_texto(r.metodo)},\n"
        f"       {sql_texto(json_compacto(r.condiciones))},\n"
        f"       {sql_texto(json_compacto(r.calculo))},\n"
        f"       {sql_texto(POLITICA_PAGO)},\n"
        f"       {sql_texto(r.motivo)},\n"
        f"       {sql_texto(r.localizador)},\n"
        "       TRUE\n"
        "FROM mcas_conjuntos_reglas cr\n"
        f"WHERE {guarda_conjunto(cj)}\n"
        "  AND NOT EXISTS (SELECT 1 FROM mcas_reglas_comision x\n"
        f"                  WHERE x.id_conjunto_reglas = cr.id AND x.codigo = {sql_texto(r.codigo)});",
    ]


def union_alineada(alias: list, filas: list, notas: list) -> str:
    """
    Tabla derivada «SELECT … UNION ALL SELECT …» legible: una cabecera comentada con el
    nombre de cada columna, la primera fila con los alias y el resto alineadas debajo.
    Cada fila termina con su comentario (celda del Excel y significado).
    """
    anchos = [max(len(a), *(len(f[i]) for f in filas)) for i, a in enumerate(alias)]
    salida = ["    --" + " " * 15 + ", ".join(a.rjust(w) for a, w in zip(alias, anchos))]
    for n, (fila, nota) in enumerate(zip(filas, notas)):
        valores = [v.rjust(w) for v, w in zip(fila, anchos)]
        if n == 0:
            cuerpo = ", ".join(f"{v.strip()} AS {a}" for v, a in zip(valores, alias))
            salida.append(f"              SELECT {cuerpo}  -- {nota}")
        else:
            salida.append(f"    UNION ALL SELECT {', '.join(valores)}  -- {nota}")
    return "\n".join(salida)


def sql_matriz(cj: Conjunto, r: Regla) -> list:
    unidad = {"consumo_kwh": "kWh", "potencia_kw": "kW"}
    filas, notas = [], []
    for orden, celda in enumerate(r.celdas, start=1):
        for j, d in enumerate(celda.dims):
            if d.rango is not None:
                mn, mx = sql_num(d.rango.minimo), sql_num(d.rango.maximo)
                mni, mxi = sql_bool(d.rango.min_inc), sql_bool(d.rango.max_inc)
                cat, que = "NULL", f"{d.dimension} {d.rango.texto()} {unidad.get(d.dimension, '')}".rstrip()
            else:
                mn = mx = "NULL"
                mni, mxi = "TRUE", "FALSE"
                cat, que = sql_texto(d.categoria), f"{d.dimension} = {d.categoria}"
            filas.append([str(orden), sql_texto(d.dimension), mn, mx, mni, mxi, cat, sql_num(celda.importe, 2)])
            notas.append(f"{celda.ref if j == 0 else '':<4} {que}")
    tabla = union_alineada(["orden", "dimension", "valor_min", "valor_max", "min_inc", "max_inc",
                            "categoria", "importe"], filas, notas)
    comentario = [f"-- Valores de {r.codigo}: {r.comentario}. {len(r.celdas)} celdas",
                  f"-- (cada celda = un «orden» con {len(r.celdas[0].dims)} filas, una por dimensión, "
                  "con el mismo importe). Se insertan solo si la regla aún no tiene valores."]
    comentario += [f"--   Sin celda (revisión manual): {o}" for o in r.omitidas]
    return [
        "\n".join(comentario) + "\n"
        "INSERT INTO mcas_valores_rango\n"
        "    (id_regla, dimension, valor_min, valor_max, min_inclusivo, max_inclusivo,\n"
        "     valor_categoria, valor_resultado, unidad_resultado, orden)\n"
        "SELECT r.id, v.dimension, v.valor_min, v.valor_max, v.min_inc, v.max_inc,\n"
        "       v.categoria, v.importe, 'EUR', v.orden\n"
        "FROM mcas_reglas_comision r\n"
        "JOIN mcas_conjuntos_reglas cr ON cr.id = r.id_conjunto_reglas\n"
        "CROSS JOIN (\n" + tabla + "\n) v\n"
        f"WHERE {guarda_conjunto(cj)}\n"
        f"  AND r.codigo = {sql_texto(r.codigo)}\n"
        "  AND NOT EXISTS (SELECT 1 FROM mcas_valores_rango x WHERE x.id_regla = r.id);"]


def texto_tramo(t: Tramo) -> str:
    izq = "[" if t.desde_inc else "("
    der = "]" if t.hasta_inc else ")"
    hasta = "∞" if t.hasta is None else str(t.hasta)
    valor = (f"{miles(t.valor * 100)} %" if t.tipo_valor == "PORCENTAJE"
             else f"{miles(t.valor)} (sin unidad confirmada)")
    unidad = {"DIAS": "días", "MESES": "meses"}[t.unidad]
    return f"{t.ref} «{t.etiqueta}»: {izq}{t.desde}, {hasta}{der} {unidad} = {valor}"


def sql_politica(com: Comercializadora, pol: Politica) -> list:
    salida = [
        f"-- Política {pol.codigo} ({pol.metodo}"
        + (f", segmento {pol.segmento}" if pol.segmento else "")
        + (f", suministro {pol.tipo_suministro}" if pol.tipo_suministro else "")
        + (f", tarifa {pol.tarifa}" if pol.tarifa else "") + ")\n"
        "INSERT INTO mcas_politicas_retrocesion\n"
        "    (codigo, descripcion, id_comercializadora, id_gestora, version, vigencia_desde, vigencia_hasta,\n"
        "     tipo_suministro, segmento, tarifa_acceso, metodo, limite_importe_pagado, archivo_origen, estado)\n"
        f"SELECT {sql_texto(pol.codigo)},\n"
        f"       {sql_texto(pol.descripcion)},\n"
        f"       c.id, NULL, {sql_texto(pol.version)}, {sql_fecha(pol.vigencia)}, NULL,\n"
        f"       {sql_texto(pol.tipo_suministro)}, {sql_texto(pol.segmento)}, {sql_texto(pol.tarifa)}, "
        f"{sql_texto(pol.metodo)}, {sql_bool(pol.limite_importe_pagado)},\n"
        f"       {sql_texto(ARCHIVO_COMISIONES)}, 'BORRADOR'\n"
        "FROM tbcomercializadoras c\n"
        f"WHERE {guarda_comercializadora(com.clave)}\n"
        f"  AND NOT EXISTS (SELECT 1 FROM mcas_politicas_retrocesion x WHERE x.codigo = {sql_texto(pol.codigo)});",
    ]
    if not pol.tramos:
        salida.append(f"-- {pol.codigo}: sin tramos ({pol.metodo}).")
        return salida
    filas = [[sql_texto(t.unidad), str(t.desde), "NULL" if t.hasta is None else str(t.hasta),
              sql_bool(t.desde_inc), sql_bool(t.hasta_inc), sql_num(t.valor, 2), sql_texto(t.tipo_valor),
              str((i + 1) * 10)] for i, t in enumerate(pol.tramos)]
    tabla = union_alineada(["unidad", "desde", "hasta", "desde_inc", "hasta_inc", "valor", "tipo_valor", "prioridad"],
                           filas, [texto_tramo(t) for t in pol.tramos])
    salida.append(
        f"-- Tramos de {pol.codigo} (solo si la política sigue en BORRADOR y aún no tiene tramos)\n"
        "INSERT INTO mcas_tramos_retrocesion\n"
        "    (id_politica, unidad, desde_valor, hasta_valor, desde_inclusivo, hasta_inclusivo,\n"
        "     valor, tipo_valor, prioridad)\n"
        "SELECT p.id, t.unidad, t.desde, t.hasta, t.desde_inc, t.hasta_inc, t.valor, t.tipo_valor, t.prioridad\n"
        "FROM mcas_politicas_retrocesion p\n"
        "CROSS JOIN (\n" + tabla + "\n) t\n"
        f"WHERE p.codigo = {sql_texto(pol.codigo)} AND p.estado = 'BORRADOR'\n"
        "  AND NOT EXISTS (SELECT 1 FROM mcas_tramos_retrocesion x WHERE x.id_politica = p.id);")
    return salida


def sql_comprobacion(comercializadoras: list) -> list:
    patrones = [(c.clave, ETIQUETA_PATRON[c.clave]) for c in comercializadoras]
    union = "\nUNION ALL\n".join(
        f"SELECT {sql_texto(etq)} AS patron, COUNT(*) AS encontradas,\n"
        "       CASE WHEN COUNT(*) = 1 THEN 'SE SIEMBRA' ELSE 'NO SE SIEMBRA (debe haber exactamente una)' END AS resultado\n"
        f"FROM tbcomercializadoras x WHERE {PATRONES[clave].format(a='x')}"
        for clave, etq in patrones)
    alguna = "\n   OR ".join(f"({PATRONES[clave].format(a='c')})" for clave, _ in patrones)
    conjuntos = ", ".join(sql_texto(cj.codigo) for com in comercializadoras for cj in com.conjuntos)
    politicas = ", ".join(sql_texto(p.codigo) for com in comercializadoras for p in com.politicas)
    return [
        "-- 1. Qué comercializadoras ha encontrado cada patrón (solo se siembra si hay exactamente una).\n"
        + union + ";",
        "-- 2. Recuento por comercializadora (incluye lo sembrado antes por V20260913_2).\n"
        "SELECT c.nombre AS comercializadora,\n"
        "       (SELECT COUNT(*) FROM mcas_productos_comerciales p WHERE p.id_comercializadora = c.id) AS productos,\n"
        "       (SELECT COUNT(*) FROM mcas_conjuntos_reglas cr WHERE cr.id_comercializadora = c.id) AS conjuntos,\n"
        "       (SELECT COUNT(*) FROM mcas_reglas_comision r JOIN mcas_conjuntos_reglas cr ON cr.id = r.id_conjunto_reglas\n"
        "         WHERE cr.id_comercializadora = c.id AND r.activo = TRUE) AS reglas_activas,\n"
        "       (SELECT COUNT(*) FROM mcas_reglas_comision r JOIN mcas_conjuntos_reglas cr ON cr.id = r.id_conjunto_reglas\n"
        "         WHERE cr.id_comercializadora = c.id AND r.activo = FALSE) AS reglas_inactivas,\n"
        "       (SELECT COUNT(*) FROM mcas_valores_rango v JOIN mcas_reglas_comision r ON r.id = v.id_regla\n"
        "          JOIN mcas_conjuntos_reglas cr ON cr.id = r.id_conjunto_reglas\n"
        "         WHERE cr.id_comercializadora = c.id) AS valores_rango,\n"
        "       (SELECT COUNT(*) FROM mcas_politicas_retrocesion pr WHERE pr.id_comercializadora = c.id) AS politicas,\n"
        "       (SELECT COUNT(*) FROM mcas_tramos_retrocesion t JOIN mcas_politicas_retrocesion pr ON pr.id = t.id_politica\n"
        "         WHERE pr.id_comercializadora = c.id) AS tramos\n"
        "FROM tbcomercializadoras c\n"
        f"WHERE {alguna}\n"
        "ORDER BY c.nombre;",
        "-- 3. Conjuntos sembrados o ampliados: estado (debe ser BORRADOR) y reglas.\n"
        "SELECT cr.codigo, cr.estado, cr.fase_precio, cr.creado_por, c.nombre AS comercializadora,\n"
        "       (SELECT COUNT(*) FROM mcas_reglas_comision r WHERE r.id_conjunto_reglas = cr.id AND r.activo = TRUE) AS reglas_activas,\n"
        "       (SELECT COUNT(*) FROM mcas_reglas_comision r WHERE r.id_conjunto_reglas = cr.id AND r.activo = FALSE) AS reglas_inactivas\n"
        "FROM mcas_conjuntos_reglas cr\n"
        "JOIN tbcomercializadoras c ON c.id = cr.id_comercializadora\n"
        f"WHERE cr.codigo IN ({conjuntos})\n"
        "ORDER BY cr.codigo;",
        "-- 4. Políticas de retrocesión sembradas (todas en BORRADOR, las de Iberdrola no se pueden publicar\n"
        "--    hasta confirmar la unidad de los tramos SIN_CONFIRMAR).\n"
        "SELECT p.codigo, p.estado, p.metodo, p.segmento, p.tipo_suministro, p.tarifa_acceso, c.nombre AS comercializadora,\n"
        "       (SELECT COUNT(*) FROM mcas_tramos_retrocesion t WHERE t.id_politica = p.id) AS tramos,\n"
        "       (SELECT COUNT(*) FROM mcas_tramos_retrocesion t WHERE t.id_politica = p.id AND t.tipo_valor = 'SIN_CONFIRMAR') AS tramos_sin_confirmar\n"
        "FROM mcas_politicas_retrocesion p\n"
        "JOIN tbcomercializadoras c ON c.id = p.id_comercializadora\n"
        f"WHERE p.codigo IN ({politicas})\n"
        "ORDER BY p.codigo;",
    ]


def cabecera(sha_comisiones: str, sha_listado: str, hoja2_verificada: bool, comprobaciones: int) -> list:
    return [
        "-- ============================================================",
        "-- MCAS - Precarga de reglas y retrocesiones (Hoja2 del cliente + Excel de comisiones)",
        f"-- {NOMBRE_SQL}",
        "--",
        "-- CÓMO EJECUTARLO",
        "--   Después de V20260913_1, V20260913_2 y V20260929_1_MCAS_Conformidad.sql, contra la BBDD de",
        "--   Aura (MariaDB). Solo DML: no crea ni altera tablas. Revise al final las consultas de",
        "--   comprobación (qué comercializadoras se han encontrado y qué se ha sembrado).",
        "--",
        "-- PRINCIPIOS",
        "--   * Idempotente: se puede relanzar. Todo se inserta con NOT EXISTS por código y nunca duplica.",
        "--   * Todo queda en BORRADOR (creado_por = 'seed-hoja2'): nada se publica solo. Un administrador",
        "--     prueba, valida y publica cada conjunto y cada política en Configuración MCAS.",
        "--   * La comercializadora se busca por nombre y solo se siembra si el patrón encuentra EXACTAMENTE",
        "--     una (ENDESA excluye ENDESA XXI). Si no, no inserta nada y la consulta final lo muestra.",
        "--   * Nunca toca conjuntos PUBLICADO/VALIDADO/ARCHIVADO ni los creados por personas. Los conjuntos",
        "--     IRIS-FIJO-20260304 y VISALIA-FIJO-20250612 de V20260913_2 solo se modifican si siguen en",
        "--     BORRADOR y con creado_por = 'seed-controlado'.",
        "--   * Si la Hoja2 (lo que escribió el cliente) contradice al Excel, manda la Hoja2 y el valor del",
        "--     Excel queda anotado en localizador_origen. Importes del Excel redondeados a 2 decimales.",
        "--   * Rangos del Excel: «5.001-10.000» = (5000, 10000], «0-5.000» = [0, 5000], «>800.001» =",
        "--     (800000, ∞). Dentro de un conjunto, dos reglas activas nunca coinciden para el mismo contrato.",
        "--   * politica_pago_json = 1 pago sugerido en todas (el número de pagos lo pone el usuario).",
        "--   * GANA, REPSOL y HELIOELEC no tienen tablas: siguen en revisión manual («Sin conjunto publicado»).",
        "--",
        "-- NO EDITAR A MANO: se genera con backend/tools/mcas/generar_seed_hoja2.py",
        "--   python tools/mcas/generar_seed_hoja2.py \"<ruta>/COMISIONES NEXO A 16032026 1.xlsx\" \\",
        "--       --listado \"<ruta>/LISTADO DATOS AUTOMATIZACION SERVICIOS 1.xlsx\"",
        "--",
        "-- Origen (SHA-256):",
        f"--   {ARCHIVO_COMISIONES}",
        f"--   {sha_comisiones}",
        f"--   {ARCHIVO_LISTADO}",
        f"--   {sha_listado}",
        ("--   Textos de la Hoja2 verificados contra el LISTADO: "
         f"{comprobaciones} comprobaciones." if hoja2_verificada
         else "--   AVISO: textos de la Hoja2 NO verificados (el generador se ejecutó sin --listado)."),
        "-- ============================================================",
    ]


def generar_sql(comercializadoras: list, sha_comisiones: str, sha_listado: str, h2: Hoja2) -> str:
    lineas = cabecera(sha_comisiones, sha_listado, h2.verificada, h2.comprobaciones)
    for com in comercializadoras:
        lineas += ["", "", "-- " + "=" * 70, f"-- {com.titulo}"]
        lineas += [f"-- {r}" for r in com.resumen]
        lineas.append("-- " + "=" * 70)
        bloques = sql_productos(com)
        for cj in com.conjuntos:
            bloques += sql_conjunto(com, cj)
            bloques += sql_desactivar(cj)
            for r in cj.reglas:
                bloques += sql_regla(cj, r)
            for r in cj.reglas:
                if r.celdas:
                    bloques += sql_matriz(cj, r)
        for pol in com.politicas:
            bloques += sql_politica(com, pol)
        for b in bloques:
            lineas += ["", b]
    lineas += ["", "", "-- " + "=" * 70, "-- COMPROBACIÓN FINAL", "-- " + "=" * 70]
    for b in sql_comprobacion(comercializadoras):
        lineas += ["", b]
    return "\n".join(lineas) + "\n"


def cabecera_nombres(conjuntos: int, reglas: int) -> list:
    return [
        "-- ============================================================",
        "-- MCAS - Nombres legibles de las tablas de comisiones y de sus reglas",
        f"-- {NOMBRE_SQL_NOMBRES}",
        "--",
        "-- CÓMO EJECUTARLO",
        f"--   Después de {NOMBRE_SQL} y de V20260930_1_MCAS_Usabilidad.sql (que añade",
        "--   las columnas nombre y descripcion), contra la BBDD de Aura (MariaDB), ANTES de desplegar el",
        "--   backend de esta versión. Solo DML: no crea ni altera tablas. Revise al final la consulta de",
        "--   comprobación (debe dar 0 tablas y 0 reglas sin nombre).",
        "--",
        "-- PRINCIPIOS",
        "--   * Idempotente: se puede relanzar. Solo rellena lo que está vacío: el nombre si es NULL y la",
        "--     descripción si también lo es (COALESCE), así que nunca pisa un nombre ni una descripción que",
        "--     haya escrito el administrador.",
        "--   * Los nombres no llevan importes ni porcentajes (una «Nueva versión» copia el nombre y el",
        "--     importe puede cambiar): los importes van en la descripción, con su origen (Hoja2, Excel).",
        "--   * La tabla se localiza por su código y la regla por su código dentro de esa tabla (subconsulta,",
        "--     sin UPDATE con JOIN). Las copias hechas con «Nueva versión» tienen otro código: no se tocan.",
        "--   * Solo cambia textos para el administrador: el estado (borrador, activa...), las condiciones y los",
        "--     importes no se modifican, así que también se aplica a tablas ya activas.",
        f"--   * {conjuntos} tablas y {reglas} reglas (también las de V20260913_2 que no crea el generador).",
        "--",
        "-- NO EDITAR A MANO: se genera con backend/tools/mcas/generar_seed_hoja2.py (mismo comando que",
        f"--   {NOMBRE_SQL}).",
        "-- ============================================================",
    ]


def sql_nombre_conjunto(cj: Conjunto) -> str:
    return ("UPDATE mcas_conjuntos_reglas\n"
            f"SET nombre = {sql_texto(cj.nombre)},\n"
            f"    descripcion = COALESCE(descripcion, {sql_texto(cj.descripcion)})\n"
            f"WHERE codigo = {sql_texto(cj.codigo)} AND nombre IS NULL;")


def sql_nombre_regla(cj: Conjunto, codigo: str, nombre: str, descripcion: str) -> str:
    return ("UPDATE mcas_reglas_comision\n"
            f"SET nombre = {sql_texto(nombre)},\n"
            f"    descripcion = COALESCE(descripcion, {sql_texto(descripcion)})\n"
            f"WHERE codigo = {sql_texto(codigo)} AND nombre IS NULL\n"
            "  AND id_conjunto_reglas IN (SELECT cr.id FROM mcas_conjuntos_reglas cr\n"
            f"                             WHERE cr.codigo = {sql_texto(cj.codigo)});")


def generar_sql_nombres(comercializadoras: list) -> str:
    conjuntos = [cj for com in comercializadoras for cj in com.conjuntos]
    reglas = sum(len(cj.reglas) + len(cj.anteriores) for cj in conjuntos)
    lineas = cabecera_nombres(len(conjuntos), reglas)
    for com in comercializadoras:
        lineas += ["", "", "-- " + "=" * 70, f"-- {com.titulo}", "-- " + "=" * 70]
        for cj in com.conjuntos:
            lineas += ["", f"-- Tabla {cj.codigo}", sql_nombre_conjunto(cj)]
            for a in cj.anteriores:
                lineas += ["", f"-- {a.codigo} (creada por V20260913_2)",
                           sql_nombre_regla(cj, a.codigo, a.nombre, a.descripcion)]
            for r in cj.reglas:
                lineas += ["", f"-- {r.codigo}", sql_nombre_regla(cj, r.codigo, r.nombre, r.descripcion)]
    codigos = ", ".join(sql_texto(cj.codigo) for cj in conjuntos)
    lineas += ["", "", "-- " + "=" * 70, "-- COMPROBACIÓN FINAL: tablas y reglas sembradas que siguen sin nombre",
               "-- (0 y 0 si se han encontrado todas las comercializadoras)", "-- " + "=" * 70, "",
               "SELECT 'tablas_sin_nombre' AS comprobacion, COUNT(*) AS total\n"
               "FROM mcas_conjuntos_reglas\n"
               f"WHERE codigo IN ({codigos}) AND nombre IS NULL\n"
               "UNION ALL\n"
               "SELECT 'reglas_sin_nombre', COUNT(*)\n"
               "FROM mcas_reglas_comision r\n"
               "JOIN mcas_conjuntos_reglas cr ON cr.id = r.id_conjunto_reglas\n"
               f"WHERE cr.codigo IN ({codigos}) AND r.nombre IS NULL;"]
    return "\n".join(lineas) + "\n"


def resumen(comercializadoras: list) -> str:
    filas = [f"{'Comercializadora':<16}{'Prod.':>6}{'Conj.':>6}{'Reglas':>7}{'Desact.':>8}{'Valores':>8}"
             f"{'Polít.':>7}{'Tramos':>7}"]
    for com in comercializadoras:
        reglas = sum(len(cj.reglas) for cj in com.conjuntos)
        desact = sum(len(cj.desactivar) for cj in com.conjuntos)
        valores = sum(len(c.dims) for cj in com.conjuntos for r in cj.reglas for c in r.celdas)
        nuevos = sum(1 for cj in com.conjuntos if not cj.existente)
        tramos = sum(len(p.tramos) for p in com.politicas)
        filas.append(f"{com.titulo:<16}{len(com.productos):>6}{nuevos:>6}{reglas:>7}{desact:>8}{valores:>8}"
                     f"{len(com.politicas):>7}{tramos:>7}")
    return "\n".join(filas)


def sha256(ruta: Path) -> str:
    return hashlib.sha256(ruta.read_bytes()).hexdigest().upper()


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="Genera el SQL de precarga MCAS (Hoja2 + Excel de comisiones).")
    ap.add_argument("comisiones", type=Path, help=f"ruta de «{ARCHIVO_COMISIONES}»")
    ap.add_argument("--listado", type=Path, default=None, help=f"ruta de «{ARCHIVO_LISTADO}» (verifica la Hoja2)")
    ap.add_argument("--salida", type=Path, default=SALIDA_POR_DEFECTO, help="fichero SQL de salida")
    ap.add_argument("--salida-nombres", type=Path, default=SALIDA_NOMBRES_POR_DEFECTO,
                    help="fichero SQL de nombres legibles")
    args = ap.parse_args(argv)
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")

    sha_comisiones = sha256(args.comisiones)
    print(f"SHA-256 {args.comisiones.name}: {sha_comisiones}")
    if sha_comisiones != HASH_COMISIONES_REVISADO:
        print(f"AVISO: el Excel de comisiones no es el revisado ({HASH_COMISIONES_REVISADO}): "
              "revise el SQL generado y el test McasSeedHoja2Test.")
    if args.listado is not None:
        sha_listado = sha256(args.listado)
        print(f"SHA-256 {args.listado.name}: {sha_listado}")
    else:
        sha_listado = HASH_LISTADO_REGISTRADO
        print("AVISO: sin --listado no se verifican los textos de la Hoja2 (se usa el SHA-256 registrado).")

    try:
        h2 = Hoja2(args.listado)
        wb = openpyxl.load_workbook(args.comisiones, data_only=True)
        comercializadoras = construir(wb, h2, sha_comisiones, sha_listado)
        comprobar(comercializadoras)
        sql = generar_sql(comercializadoras, sha_comisiones, sha_listado, h2)
        sql_nombres = generar_sql_nombres(comercializadoras)
    except ErrorGenerador as e:
        print(f"ERROR: {e}", file=sys.stderr)
        return 1

    for salida, contenido in ((args.salida, sql), (args.salida_nombres, sql_nombres)):
        salida.parent.mkdir(parents=True, exist_ok=True)
        salida.write_text(contenido, encoding="utf-8", newline="\n")
    print(f"Hoja2: {'verificada, ' + str(h2.comprobaciones) + ' textos' if h2.verificada else 'NO verificada'}")
    print(resumen(comercializadoras))
    print(f"Escrito {args.salida}")
    print(f"Escrito {args.salida_nombres}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
