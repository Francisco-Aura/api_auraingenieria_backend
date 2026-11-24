-- Producción solar horaria de 1 kWp (kWh por hora, año tipo de 8760 h en hora local) que da PVGIS (Comisión Europea) para una ubicación,
-- una inclinación y un azimut. Sirve de caché: cada combinación se pide a PVGIS una sola vez. Ubicación en centésimas de grado (≈ 1 km).
-- MariaDB 10.11. Lo aplica solo el backend al arrancar (una vez por entorno, queda anotado en pl_scripts).

CREATE TABLE IF NOT EXISTS pl_pvgis_series (
    id       INT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    lat_c    INT        NOT NULL,
    lon_c    INT        NOT NULL,
    angulo   SMALLINT   NOT NULL,
    azimut   SMALLINT   NOT NULL,
    datos    MEDIUMTEXT NOT NULL,
    UNIQUE KEY uk_pl_pvgis_series (lat_c, lon_c, angulo, azimut)
);
