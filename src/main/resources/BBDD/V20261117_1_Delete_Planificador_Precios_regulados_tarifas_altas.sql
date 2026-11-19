-- Las tarifas 6.2TD, 6.3TD y 6.4TD no se usan en los estudios del Planificador: se quitan sus peajes y cargos del BOE.
-- MariaDB 10.11. Lo aplica solo el backend al arrancar (una vez por entorno, queda anotado en pl_scripts).

DELETE FROM pl_precios_regulados WHERE tarifa IN ('6.2TD', '6.3TD', '6.4TD');
