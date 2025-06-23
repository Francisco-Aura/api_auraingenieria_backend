-- Datos iniciales para la tabla app_monitorizacion
INSERT INTO app_monitorizacion (nombre, descripcion) VALUES
('Fronius', 'Sistema de monitorización Fronius Solar.web'),
('SMA', 'Sistema de monitorización SMA Sunny Portal'),
('SMA Exeos', 'Sistema de monitorización SMA Exeos'),
('Huawei', 'Sistema de monitorización Huawei FusionSolar'),
('ITR Lacecal', 'Sistema de monitorización ITR Lacecal'),
('Ingeteam', 'Sistema de monitorización Ingeteam INGECON'),
('Victron', 'Sistema de monitorización Victron VRM Portal'),
('Otro', 'Otro sistema de monitorización no especificado');

-- Datos iniciales para la tabla integracion_sistemas_fv  
INSERT INTO integracion_sistemas_fv (nombre, descripcion) VALUES
('Fronius', 'Integración con sistemas fotovoltaicos Fronius'),
('SMA', 'Integración con sistemas fotovoltaicos SMA'),
('SMA Exeos', 'Integración con sistemas fotovoltaicos SMA Exeos'),
('Huawei', 'Integración con sistemas fotovoltaicos Huawei'),
('ITR Lacecal', 'Integración con sistemas fotovoltaicos ITR Lacecal'),
('Ingeteam', 'Integración con sistemas fotovoltaicos Ingeteam'),
('Victron', 'Integración con sistemas fotovoltaicos Victron'),
('Otro', 'Otro sistema de integración fotovoltaica no especificado');
