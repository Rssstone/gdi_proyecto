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
    motivo VARCHAR(255),
    estado ENUM('Programada', 'Confirmada', 'Cancelada', 'Completada', 'Inasistencia') DEFAULT 'Programada',
    notas_recepcion TEXT,
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
    numero_diente INT NOT NULL,
    estado_diente VARCHAR(100),
    descripcion TEXT,
    FOREIGN KEY (expediente_id) REFERENCES expedientes(id) ON DELETE CASCADE
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

INSERT INTO citas (paciente_id, odontologo_id, fecha, hora, motivo, estado) VALUES
(1, 1, @d1, '09:00:00', 'Limpieza dental', 'Confirmada'),
(2, 1, @d1, '10:30:00', 'Revisión de ortodoncia', 'Programada'),
(3, 1, @d1, '12:00:00', 'Dolor en muela', 'Programada'),
(4, 1, @d2, '11:00:00', 'Valoración inicial', 'Programada');
