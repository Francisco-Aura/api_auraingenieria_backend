-- Script de prueba: crea una tabla y mete una fila (con un punto y coma dentro del texto)
CREATE TABLE IF NOT EXISTS prueba_scripts (n INT, texto VARCHAR(100));
INSERT INTO prueba_scripts VALUES (1, 'uno; con punto y coma');
