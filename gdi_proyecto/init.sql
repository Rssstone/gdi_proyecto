SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS gdi_db;
USE gdi_db;

CREATE TABLE usuarios (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nombre_completo VARCHAR(150) NOT NULL,
    rol ENUM('Administrador', 'Odontologo', 'Recepcionista') NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE pacientes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nombre_completo VARCHAR(150) NOT NULL,
    fecha_nacimiento DATE,
    edad INT,
    direccion VARCHAR(255),
    telefono VARCHAR(20),
    rfc VARCHAR(13),
    regimen_fiscal VARCHAR(100),
    cp_sat VARCHAR(10),
    email_facturacion VARCHAR(100),
    fecha_registro TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE expedientes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    paciente_id INT NOT NULL,
    antecedentes_personales TEXT,
    antecedentes_familiares TEXT,
    alergias TEXT,
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (paciente_id) REFERENCES pacientes(id) ON DELETE CASCADE
);

CREATE TABLE citas (
    id INT AUTO_INCREMENT PRIMARY KEY,
    paciente_id INT NOT NULL,
    odontologo_id INT, 
    fecha DATE NOT NULL,
    hora TIME NOT NULL,
    duracion_minutos INT NOT NULL DEFAULT 30,
    motivo VARCHAR(255),
    estado ENUM('Programada', 'Confirmada', 'Cancelada', 'Completada', 'Inasistencia') DEFAULT 'Programada',
    notas_recepcion TEXT,
    urgente BOOLEAN NOT NULL DEFAULT FALSE,  -- urgencia atendida como sobrecupo
    FOREIGN KEY (paciente_id) REFERENCES pacientes(id) ON DELETE CASCADE,
    FOREIGN KEY (odontologo_id) REFERENCES usuarios(id) ON DELETE SET NULL
);

CREATE TABLE tratamientos (
    id INT AUTO_INCREMENT PRIMARY KEY,
    expediente_id INT NOT NULL,
    fecha_consulta DATE NOT NULL,
    condicion_llegada TEXT,
    procedimiento_realizado TEXT,
    observaciones_clinicas TEXT,
    presupuesto_total DECIMAL(10,2),
    FOREIGN KEY (expediente_id) REFERENCES expedientes(id) ON DELETE CASCADE
);

CREATE TABLE odontogramas (
    id INT AUTO_INCREMENT PRIMARY KEY,
    expediente_id INT NOT NULL,
    numero_diente INT NOT NULL,             -- numeración FDI (11 a 48)
    estado_diente VARCHAR(100),
    superficies VARCHAR(20),                -- hallazgos por cara: "O:C,M:R" (C caries, R restaurado, F fractura)
    descripcion TEXT,
    UNIQUE KEY uk_odontograma_diente (expediente_id, numero_diente),
    FOREIGN KEY (expediente_id) REFERENCES expedientes(id) ON DELETE CASCADE
);

-- Historial del odontograma: cada cambio de una pieza con su estado anterior y el nuevo.
CREATE TABLE odontograma_historial (
    id INT AUTO_INCREMENT PRIMARY KEY,
    expediente_id INT NOT NULL,
    numero_diente INT NOT NULL,
    estado_anterior VARCHAR(30),
    caras_anteriores VARCHAR(20),
    estado_nuevo VARCHAR(30),
    caras_nuevas VARCHAR(20),
    descripcion VARCHAR(500),
    usuario_id INT,
    fecha DATETIME NOT NULL,
    FOREIGN KEY (expediente_id) REFERENCES expedientes(id) ON DELETE CASCADE,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE SET NULL
);

-- Módulo técnico: especificaciones de coronas, puentes y prótesis que toma el odontólogo.
CREATE TABLE fichas_tecnicas (
    id INT AUTO_INCREMENT PRIMARY KEY,
    paciente_id INT NOT NULL,
    odontologo_id INT,
    tipo_trabajo VARCHAR(30) NOT NULL,
    piezas VARCHAR(60) NOT NULL,            -- numeración FDI, ej. "14, 15, 16"
    material VARCHAR(60) NOT NULL,
    color_vita VARCHAR(10),                 -- guía de color VITA (A1 a D4)
    ancho_mm DECIMAL(5,2),
    alto_mm DECIMAL(5,2),
    grosor_mm DECIMAL(5,2),
    terminacion VARCHAR(40),
    laboratorio VARCHAR(120),
    fecha_toma DATE NOT NULL,
    fecha_entrega DATE,
    notas TEXT,
    FOREIGN KEY (paciente_id) REFERENCES pacientes(id) ON DELETE CASCADE,
    FOREIGN KEY (odontologo_id) REFERENCES usuarios(id) ON DELETE SET NULL
);

-- Radiografías, tomografías y fotografías del expediente.
CREATE TABLE estudios (
    id INT AUTO_INCREMENT PRIMARY KEY,
    paciente_id INT NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    descripcion VARCHAR(255),
    nombre_archivo VARCHAR(255) NOT NULL,
    tipo_mime VARCHAR(100) NOT NULL,
    tamano BIGINT NOT NULL,
    archivo LONGBLOB NOT NULL,
    fecha DATETIME NOT NULL,
    FOREIGN KEY (paciente_id) REFERENCES pacientes(id) ON DELETE CASCADE
);

CREATE TABLE pagos (
    id INT AUTO_INCREMENT PRIMARY KEY,
    paciente_id INT NOT NULL,
    tratamiento_id INT,
    monto_pagado DECIMAL(10,2) NOT NULL,
    saldo_pendiente DECIMAL(10,2) NOT NULL,
    concepto VARCHAR(255),
    fecha_pago TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (paciente_id) REFERENCES pacientes(id) ON DELETE CASCADE,
    FOREIGN KEY (tratamiento_id) REFERENCES tratamientos(id) ON DELETE SET NULL
);

CREATE TABLE inventario (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nombre_producto VARCHAR(150) NOT NULL,
    cantidad_actual INT NOT NULL DEFAULT 0,
    nivel_minimo INT NOT NULL DEFAULT 0,
    fecha_ultima_actualizacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Entradas y salidas de material: quién las registró y en qué tratamiento se usó (RF-10).
CREATE TABLE movimientos_inventario (
    id INT AUTO_INCREMENT PRIMARY KEY,
    inventario_id INT NOT NULL,
    usuario_id INT,
    tratamiento_id INT,
    tipo ENUM('Entrada', 'Salida') NOT NULL,
    cantidad INT NOT NULL,
    fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (inventario_id) REFERENCES inventario(id) ON DELETE CASCADE,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE SET NULL,
    FOREIGN KEY (tratamiento_id) REFERENCES tratamientos(id) ON DELETE SET NULL
);

-- Contraseñas iniciales de demostración: el backend las cifra con BCrypt en el primer inicio de sesión.
INSERT INTO usuarios (nombre_completo, rol, email, password) VALUES
('Dra. Ana López', 'Odontologo', 'ana@clinica.com', '12345'),
('Recepción', 'Recepcionista', 'recepcion@clinica.com', '12345'),
('Administrador', 'Administrador', 'admin@clinica.com', '12345');

INSERT INTO pacientes (nombre_completo, telefono) VALUES
('Juan Pérez', '5551234567'),
('María García', '5556781234'),
('Luis Hernández', '5552223344'),
('Sofía Ramírez', '5559876543');

-- Citas de ejemplo en los próximos días hábiles (se omite el domingo).
SET @d1 = DATE_ADD(CURDATE(), INTERVAL IF(DAYOFWEEK(DATE_ADD(CURDATE(), INTERVAL 1 DAY)) = 1, 2, 1) DAY);
SET @d2 = DATE_ADD(@d1, INTERVAL IF(DAYOFWEEK(DATE_ADD(@d1, INTERVAL 1 DAY)) = 1, 2, 1) DAY);

INSERT INTO citas (paciente_id, odontologo_id, fecha, hora, duracion_minutos, motivo, estado) VALUES
(1, 1, @d1, '09:00:00', 60, 'Limpieza dental', 'Confirmada'),
(2, 1, @d1, '10:30:00', 30, 'Revisión de ortodoncia', 'Programada'),
(3, 1, @d1, '12:00:00', 90, 'Extracción de muela', 'Programada'),
(4, 1, @d2, '11:00:00', 30, 'Valoración inicial', 'Programada');

-- Expediente y odontograma de ejemplo.
INSERT INTO expedientes (paciente_id, antecedentes_personales, antecedentes_familiares, alergias) VALUES
(1, 'Hipertensión controlada', 'Diabetes (padre)', 'Penicilina');

INSERT INTO odontogramas (expediente_id, numero_diente, estado_diente, superficies, descripcion) VALUES
(1, 16, 'Sano', 'O:C,M:C', 'Caries oclusal y mesial'),
(1, 26, 'Sano', 'O:R', 'Resina'),
(1, 36, 'Endodoncia', NULL, 'Tratamiento de conductos terminado'),
(1, 46, 'Ausente', NULL, 'Extraído hace 2 años'),
(1, 11, 'Corona', NULL, 'Corona de zirconia');

INSERT INTO fichas_tecnicas (paciente_id, odontologo_id, tipo_trabajo, piezas, material, color_vita, ancho_mm, alto_mm,
                             grosor_mm, terminacion, laboratorio, fecha_toma, fecha_entrega, notas) VALUES
(1, 1, 'Corona', '11', 'Zirconia', 'A2', 8.60, 10.50, 1.50, 'Chamfer', 'Laboratorio Dental Ortiz',
 CURDATE(), DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'Ajustar contacto con la pieza 21');
