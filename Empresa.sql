CREATE DATABASE EmpresaLog;
USE EmpresaLog;

-- ROL DE ADMINISTRADOR (opcional, pero mejor limitarlo a EmpresaLog)
CREATE USER 'admin'@'localhost' IDENTIFIED BY '12345ñ';
GRANT ALL PRIVILEGES ON EmpresaLog.* TO 'admin'@'localhost' WITH GRANT OPTION;
FLUSH PRIVILEGES;



CREATE TABLE Clientes (
  ID_Cliente   INT PRIMARY KEY AUTO_INCREMENT,
  Nom_Cliente  VARCHAR(200) NOT NULL UNIQUE
) ENGINE=InnoDB;

CREATE TABLE Remitentes (
  ID_Remitente   INT PRIMARY KEY AUTO_INCREMENT,
  Nom_Remitente  VARCHAR(200) NOT NULL UNIQUE
) ENGINE=InnoDB;

CREATE TABLE Consignatarios (
  ID_Consignatario   INT PRIMARY KEY AUTO_INCREMENT,
  Nom_Consignatario  VARCHAR(200) NOT NULL UNIQUE
) ENGINE=InnoDB;

CREATE TABLE Operadores (
  ID_Operador   INT PRIMARY KEY AUTO_INCREMENT,
  Nom_Operador  VARCHAR(200) NOT NULL UNIQUE
) ENGINE=InnoDB;

CREATE TABLE Vehiculos (
  ID_Vehiculo  INT PRIMARY KEY AUTO_INCREMENT,
  Placa        VARCHAR(50) NOT NULL UNIQUE,
  tipo_placa   ENUM('CABEZAL','FURGON') NOT NULL
) ENGINE=InnoDB;

CREATE TABLE Custodios (
  ID_Custodio   INT PRIMARY KEY AUTO_INCREMENT,
  Nom_Custodio  VARCHAR(100) NOT NULL UNIQUE
) ENGINE=InnoDB;



CREATE TABLE Carta_Porte (
  Carta_Porte_id INT AUTO_INCREMENT PRIMARY KEY NOT NULL, 
  
  -- Claves foráneas
  ID_Cliente          INT NOT NULL,
  ID_Remitente        INT NOT NULL,
  ID_Consignatario    INT NOT NULL,
  ID_Operador         INT NOT NULL,
  ID_Placa_Cabezal    INT NOT NULL,
  ID_Placa_Del_Furgon INT NOT NULL,
  ID_Custodio         INT NOT NULL,
  
  -- DEMÁS CAMPOS
  FACTURA          VARCHAR(100),
  FECHA_FACTURA    VARCHAR(100),
  VALOR            DECIMAL(12,2),
  FECHA_DE_PAGO    VARCHAR(100),
  DESTINO          VARCHAR(100),
  REFERENCIA       VARCHAR(100),
  FACTURA2         VARCHAR(100),
  VALOR_FLETE      DECIMAL(12,2),
  ANTICIPO         DECIMAL(12,2),
  A_CANCELACION    DECIMAL(12,2),
  FECHA_DE_PAGADO  VARCHAR(100),
  F_DE_CARGA       VARCHAR(100),
  F_DE_CRUCE       VARCHAR(100),
  F_SAL_T_U        VARCHAR(100),
  F_F_DESTINO      VARCHAR(100),
  F_EN_DESTINO     VARCHAR(100),
  F_DESCARGA       VARCHAR(100),
  F_E_DE_DOCTOS    VARCHAR(100),
  PAGADO           VARCHAR(50),
  OBSERVACIONES    VARCHAR(250),

  -- RELACIONES
  CONSTRAINT FK_Cliente
    FOREIGN KEY (ID_Cliente)       REFERENCES Clientes(ID_Cliente),
  CONSTRAINT FK_Remitente
    FOREIGN KEY (ID_Remitente)     REFERENCES Remitentes(ID_Remitente),
  CONSTRAINT FK_Consignatario
    FOREIGN KEY (ID_Consignatario) REFERENCES Consignatarios(ID_Consignatario),
  CONSTRAINT FK_Operador
    FOREIGN KEY (ID_Operador)      REFERENCES Operadores(ID_Operador),
  CONSTRAINT FK_PLACA_CABEZAL
    FOREIGN KEY (ID_Placa_Cabezal)    REFERENCES Vehiculos(ID_Vehiculo),
  CONSTRAINT FK_PLACA_DEL_FURGON
    FOREIGN KEY (ID_Placa_Del_Furgon) REFERENCES Vehiculos(ID_Vehiculo),
  CONSTRAINT FK_Custodios
    FOREIGN KEY (ID_Custodio)      REFERENCES Custodios(ID_Custodio)
) ENGINE=InnoDB;


-- =====================================================
-- TABLA DE AUDITORÍA
-- =====================================================

CREATE TABLE IF NOT EXISTS AUDITORIA (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  Carta_Porte_id INT NOT NULL,
  modificado_en  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  usuario        VARCHAR(100) NULL,
  accion         ENUM('INSERT','UPDATE','DELETE') NOT NULL,
  descripcion    VARCHAR(255) NULL,
  detalle        JSON NULL,
  INDEX (Carta_Porte_id),
  INDEX (modificado_en),
  INDEX (accion)
);

-- =====================================================
-- TRIGGERS DE AUDITORÍA
-- =====================================================
DROP TRIGGER IF EXISTS TRIGGER_1;
DELIMITER //

CREATE TRIGGER TRIGGER_1
AFTER INSERT ON Carta_Porte
FOR EACH ROW
BEGIN
  INSERT INTO AUDITORIA (Carta_Porte_id, usuario, accion, descripcion, detalle)
  VALUES (
    NEW.Carta_Porte_id,
    COALESCE(@app_user, SUBSTRING_INDEX(CURRENT_USER(), '@', 1)),
    'INSERT',
    CONCAT('Inserción #Carta Porte: ', NEW.Carta_Porte_id),
    JSON_OBJECT(
      'nuevo', JSON_OBJECT(
        'ID_Cliente',         NEW.ID_Cliente,
        'ID_Remitente',       NEW.ID_Remitente,
        'ID_Consignatario',   NEW.ID_Consignatario,
        'ID_Operador',        NEW.ID_Operador,
        'ID_Placa_Cabezal',   NEW.ID_Placa_Cabezal,
        'ID_Placa_Del_Furgon',NEW.ID_Placa_Del_Furgon,
        'ID_Custodio',        NEW.ID_Custodio,
        'FACTURA',            NEW.FACTURA,
        'FECHA_FACTURA',      NEW.FECHA_FACTURA,
        'VALOR',              NEW.VALOR,
        'FECHA_DE_PAGO',      NEW.FECHA_DE_PAGO,
        'DESTINO',            NEW.DESTINO,
        'REFERENCIA',         NEW.REFERENCIA,
        'FACTURA2',           NEW.FACTURA2,
        'VALOR_FLETE',        NEW.VALOR_FLETE,
        'ANTICIPO',           NEW.ANTICIPO,
        'A_CANCELACION',      NEW.A_CANCELACION,
        'FECHA_DE_PAGADO',    NEW.FECHA_DE_PAGADO,
        'F_DE_CARGA',         NEW.F_DE_CARGA,
        'F_DE_CRUCE',         NEW.F_DE_CRUCE,
        'F_SAL_T_U',          NEW.F_SAL_T_U,
        'F_F_DESTINO',        NEW.F_F_DESTINO,
        'F_EN_DESTINO',       NEW.F_EN_DESTINO,
        'F_DESCARGA',         NEW.F_DESCARGA,
        'F_E_DE_DOCTOS',      NEW.F_E_DE_DOCTOS,
        'PAGADO',             NEW.PAGADO,
        'OBSERVACIONES',      NEW.OBSERVACIONES
      )
    )
  );
END //
DELIMITER ;
DROP TRIGGER IF EXISTS TRIGGER_2;
DELIMITER //

CREATE TRIGGER TRIGGER_2
AFTER UPDATE ON Carta_Porte
FOR EACH ROW
BEGIN
  DECLARE cambio JSON;
  SET cambio = JSON_OBJECT();

  -- ===== CLAVES FORÁNEAS (IDs) =====
  IF NOT (OLD.ID_Cliente       <=> NEW.ID_Cliente) THEN
    SET cambio = JSON_SET(cambio, '$.ID_Cliente',
      JSON_OBJECT('ANTES', OLD.ID_Cliente, 'DESPUES', NEW.ID_Cliente));
  END IF;

  IF NOT (OLD.ID_Remitente     <=> NEW.ID_Remitente) THEN
    SET cambio = JSON_SET(cambio, '$.ID_Remitente',
      JSON_OBJECT('ANTES', OLD.ID_Remitente, 'DESPUES', NEW.ID_Remitente));
  END IF;

  IF NOT (OLD.ID_Consignatario <=> NEW.ID_Consignatario) THEN
    SET cambio = JSON_SET(cambio, '$.ID_Consignatario',
      JSON_OBJECT('ANTES', OLD.ID_Consignatario, 'DESPUES', NEW.ID_Consignatario));
  END IF;

  IF NOT (OLD.ID_Operador      <=> NEW.ID_Operador) THEN
    SET cambio = JSON_SET(cambio, '$.ID_Operador',
      JSON_OBJECT('ANTES', OLD.ID_Operador, 'DESPUES', NEW.ID_Operador));
  END IF;

  IF NOT (OLD.ID_Placa_Cabezal <=> NEW.ID_Placa_Cabezal) THEN
    SET cambio = JSON_SET(cambio, '$.ID_Placa_Cabezal',
      JSON_OBJECT('ANTES', OLD.ID_Placa_Cabezal, 'DESPUES', NEW.ID_Placa_Cabezal));
  END IF;

  IF NOT (OLD.ID_Placa_Del_Furgon <=> NEW.ID_Placa_Del_Furgon) THEN
    SET cambio = JSON_SET(cambio, '$.ID_Placa_Del_Furgon',
      JSON_OBJECT('ANTES', OLD.ID_Placa_Del_Furgon, 'DESPUES', NEW.ID_Placa_Del_Furgon));
  END IF;

  IF NOT (OLD.ID_Custodio      <=> NEW.ID_Custodio) THEN
    SET cambio = JSON_SET(cambio, '$.ID_Custodio',
      JSON_OBJECT('ANTES', OLD.ID_Custodio, 'DESPUES', NEW.ID_Custodio));
  END IF;

  -- ===== CAMPOS NORMALES =====
  IF NOT (OLD.FACTURA        <=> NEW.FACTURA) THEN
    SET cambio = JSON_SET(cambio, '$.FACTURA',
      JSON_OBJECT('ANTES', OLD.FACTURA, 'DESPUES', NEW.FACTURA));
  END IF;

  IF NOT (OLD.FECHA_FACTURA  <=> NEW.FECHA_FACTURA) THEN
    SET cambio = JSON_SET(cambio, '$.FECHA_FACTURA',
      JSON_OBJECT('ANTES', OLD.FECHA_FACTURA, 'DESPUES', NEW.FECHA_FACTURA));
  END IF;

  IF NOT (OLD.VALOR          <=> NEW.VALOR) THEN
    SET cambio = JSON_SET(cambio, '$.VALOR',
      JSON_OBJECT('ANTES', OLD.VALOR, 'DESPUES', NEW.VALOR));
  END IF;

  IF NOT (OLD.FECHA_DE_PAGO  <=> NEW.FECHA_DE_PAGO) THEN
    SET cambio = JSON_SET(cambio, '$.FECHA_DE_PAGO',
      JSON_OBJECT('ANTES', OLD.FECHA_DE_PAGO, 'DESPUES', NEW.FECHA_DE_PAGO));
  END IF;

  IF NOT (OLD.DESTINO        <=> NEW.DESTINO) THEN
    SET cambio = JSON_SET(cambio, '$.DESTINO',
      JSON_OBJECT('ANTES', OLD.DESTINO, 'DESPUES', NEW.DESTINO));
  END IF;

  IF NOT (OLD.REFERENCIA     <=> NEW.REFERENCIA) THEN
    SET cambio = JSON_SET(cambio, '$.REFERENCIA',
      JSON_OBJECT('ANTES', OLD.REFERENCIA, 'DESPUES', NEW.REFERENCIA));
  END IF;

  IF NOT (OLD.FACTURA2       <=> NEW.FACTURA2) THEN
    SET cambio = JSON_SET(cambio, '$.FACTURA2',
      JSON_OBJECT('ANTES', OLD.FACTURA2, 'DESPUES', NEW.FACTURA2));
  END IF;

  IF NOT (OLD.VALOR_FLETE    <=> NEW.VALOR_FLETE) THEN
    SET cambio = JSON_SET(cambio, '$.VALOR_FLETE',
      JSON_OBJECT('ANTES', OLD.VALOR_FLETE, 'DESPUES', NEW.VALOR_FLETE));
  END IF;

  IF NOT (OLD.ANTICIPO       <=> NEW.ANTICIPO) THEN
    SET cambio = JSON_SET(cambio, '$.ANTICIPO',
      JSON_OBJECT('ANTES', OLD.ANTICIPO, 'DESPUES', NEW.ANTICIPO));
  END IF;

  IF NOT (OLD.A_CANCELACION  <=> NEW.A_CANCELACION) THEN
    SET cambio = JSON_SET(cambio, '$.A_CANCELACION',
      JSON_OBJECT('ANTES', OLD.A_CANCELACION, 'DESPUES', NEW.A_CANCELACION));
  END IF;

  IF NOT (OLD.FECHA_DE_PAGADO <=> NEW.FECHA_DE_PAGADO) THEN
    SET cambio = JSON_SET(cambio, '$.FECHA_DE_PAGADO',
      JSON_OBJECT('ANTES', OLD.FECHA_DE_PAGADO, 'DESPUES', NEW.FECHA_DE_PAGADO));
  END IF;

  IF NOT (OLD.F_DE_CARGA     <=> NEW.F_DE_CARGA) THEN
    SET cambio = JSON_SET(cambio, '$.F_DE_CARGA',
      JSON_OBJECT('ANTES', OLD.F_DE_CARGA, 'DESPUES', NEW.F_DE_CARGA));
  END IF;

  IF NOT (OLD.F_DE_CRUCE     <=> NEW.F_DE_CRUCE) THEN
    SET cambio = JSON_SET(cambio, '$.F_DE_CRUCE',
      JSON_OBJECT('ANTES', OLD.F_DE_CRUCE, 'DESPUES', NEW.F_DE_CRUCE));
  END IF;

  IF NOT (OLD.F_SAL_T_U      <=> NEW.F_SAL_T_U) THEN
    SET cambio = JSON_SET(cambio, '$.F_SAL_T_U',
      JSON_OBJECT('ANTES', OLD.F_SAL_T_U, 'DESPUES', NEW.F_SAL_T_U));
  END IF;

  IF NOT (OLD.F_F_DESTINO    <=> NEW.F_F_DESTINO) THEN
    SET cambio = JSON_SET(cambio, '$.F_F_DESTINO',
      JSON_OBJECT('ANTES', OLD.F_F_DESTINO, 'DESPUES', NEW.F_F_DESTINO));
  END IF;

  IF NOT (OLD.F_EN_DESTINO   <=> NEW.F_EN_DESTINO) THEN
    SET cambio = JSON_SET(cambio, '$.F_EN_DESTINO',
      JSON_OBJECT('ANTES', OLD.F_EN_DESTINO, 'DESPUES', NEW.F_EN_DESTINO));
  END IF;

  IF NOT (OLD.F_DESCARGA     <=> NEW.F_DESCARGA) THEN
    SET cambio = JSON_SET(cambio, '$.F_DESCARGA',
      JSON_OBJECT('ANTES', OLD.F_DESCARGA, 'DESPUES', NEW.F_DESCARGA));
  END IF;

  IF NOT (OLD.F_E_DE_DOCTOS  <=> NEW.F_E_DE_DOCTOS) THEN
    SET cambio = JSON_SET(cambio, '$.F_E_DE_DOCTOS',
      JSON_OBJECT('ANTES', OLD.F_E_DE_DOCTOS, 'DESPUES', NEW.F_E_DE_DOCTOS));
  END IF;

  IF NOT (OLD.PAGADO         <=> NEW.PAGADO) THEN
    SET cambio = JSON_SET(cambio, '$.PAGADO',
      JSON_OBJECT('ANTES', OLD.PAGADO, 'DESPUES', NEW.PAGADO));
  END IF;

  IF NOT (OLD.OBSERVACIONES  <=> NEW.OBSERVACIONES) THEN
    SET cambio = JSON_SET(cambio, '$.OBSERVACIONES',
      JSON_OBJECT('ANTES', OLD.OBSERVACIONES, 'DESPUES', NEW.OBSERVACIONES));
  END IF;

  INSERT INTO AUDITORIA (Carta_Porte_id, usuario, accion, descripcion, detalle)
  VALUES (
    NEW.Carta_Porte_id,
    COALESCE(@app_user, SUBSTRING_INDEX(CURRENT_USER(), '@', 1)),
    'UPDATE',
    CONCAT('Actualizacion #Carta Porte: ', NEW.Carta_Porte_id),
    NULLIF(cambio, JSON_OBJECT())
  );
END //



-- ---------- DELETE ----------
DROP TRIGGER IF EXISTS TRIGGER_3//
CREATE TRIGGER TRIGGER_3
AFTER DELETE ON Carta_Porte
FOR EACH ROW
BEGIN
  INSERT INTO AUDITORIA (Carta_Porte_id, usuario, accion, descripcion, detalle)
  VALUES (
    OLD.Carta_Porte_id,
    COALESCE(@app_user, SUBSTRING_INDEX(CURRENT_USER(), '@', 1)),
    'DELETE',
    CONCAT('Eliminación de registro ', OLD.Carta_Porte_id),
    JSON_OBJECT(
      'eliminado', JSON_OBJECT(
        'Cliente',        OLD.Cliente,
        'Operador',       OLD.Operador,
        'DESTINO',        OLD.DESTINO,
        'CUSTODIO',       OLD.CUSTODIO,
        'FACTURA',        OLD.FACTURA,
        'FECHA_FACTURA',  OLD.FECHA_FACTURA,
        'VALOR',          OLD.VALOR,
        'FECHA_DE_PAGO',  OLD.FECHA_DE_PAGO,
        'REMITENTE',      OLD.REMITENTE,
        'CONSIGNATORIO',  OLD.CONSIGNATORIO,
        'FACTURA2',       OLD.FACTURA2,
        'VALOR_FLETE',    OLD.VALOR_FLETE,
        'ANTICIPO',       OLD.ANTICIPO,
        'A_CANCELACION',  OLD.A_CANCELACION,
        'FECHA_DE_PAGADO',OLD.FECHA_DE_PAGADO,
        'F_DE_CARGA',     OLD.F_DE_CARGA,
        'F_DE_CRUCE',     OLD.F_DE_CRUCE,
        'F_SAL_T_U',      OLD.F_SAL_T_U,
        'F_F_DESTINO',    OLD.F_F_DESTINO,
        'F_EN_DESTINO',   OLD.F_EN_DESTINO,
        'F_DESCARGA',     OLD.F_DESCARGA,
        'F_E_DE_DOCTOS',  OLD.F_E_DE_DOCTOS,
        'PAGADO',         OLD.PAGADO,
        'OBSERVACIONES',  OLD.OBSERVACIONES
      )
    )
  );
END//

DROP TRIGGER IF EXISTS TRIGGER_3;
DELIMITER //

CREATE TRIGGER TRIGGER_3
AFTER DELETE ON Carta_Porte
FOR EACH ROW
BEGIN
  INSERT INTO AUDITORIA (Carta_Porte_id, usuario, accion, descripcion, detalle)
  VALUES (
    OLD.Carta_Porte_id,
    COALESCE(@app_user, SUBSTRING_INDEX(CURRENT_USER(), '@', 1)),
    'DELETE',
    CONCAT('Eliminación de registro ', OLD.Carta_Porte_id),
    JSON_OBJECT(
      'eliminado', JSON_OBJECT(
        'ID_Cliente',          OLD.ID_Cliente,
        'ID_Remitente',        OLD.ID_Remitente,
        'ID_Consignatario',    OLD.ID_Consignatario,
        'ID_Operador',         OLD.ID_Operador,
        'ID_Placa_Cabezal',    OLD.ID_Placa_Cabezal,
        'ID_Placa_Del_Furgon', OLD.ID_Placa_Del_Furgon,
        'ID_Custodio',         OLD.ID_Custodio,
        'FACTURA',             OLD.FACTURA,
        'FECHA_FACTURA',       OLD.FECHA_FACTURA,
        'VALOR',               OLD.VALOR,
        'FECHA_DE_PAGO',       OLD.FECHA_DE_PAGO,
        'DESTINO',             OLD.DESTINO,
        'REFERENCIA',          OLD.REFERENCIA,
        'FACTURA2',            OLD.FACTURA2,
        'VALOR_FLETE',         OLD.VALOR_FLETE,
        'ANTICIPO',            OLD.ANTICIPO,
        'A_CANCELACION',       OLD.A_CANCELACION,
        'FECHA_DE_PAGADO',     OLD.FECHA_DE_PAGADO,
        'F_DE_CARGA',          OLD.F_DE_CARGA,
        'F_DE_CRUCE',          OLD.F_DE_CRUCE,
        'F_SAL_T_U',           OLD.F_SAL_T_U,
        'F_F_DESTINO',         OLD.F_F_DESTINO,
        'F_EN_DESTINO',        OLD.F_EN_DESTINO,
        'F_DESCARGA',          OLD.F_DESCARGA,
        'F_E_DE_DOCTOS',       OLD.F_E_DE_DOCTOS,
        'PAGADO',              OLD.PAGADO,
        'OBSERVACIONES',       OLD.OBSERVACIONES
      )
    )
  );
END //

DELIMITER ;




-- TABLA PARA GUARDAR LOS FILTROS

SELECT * FROM Filtro_Columnas;
CREATE TABLE Filtro_Columnas (
  nombre       VARCHAR(60) PRIMARY KEY,
  columnas     VARCHAR(512) NOT NULL,   -- índices de columnas, separados por coma (ej: "0,2,9,6")
  usuario      VARCHAR(100) NULL,       -- opcional, por si después quieres por-usuario
  actualizado  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);









