-- ═══════════════════════════════════════════════════════════
--  DIPROGRA v2.1 — Schema PostgreSQL
-- ═══════════════════════════════════════════════════════════

-- Usuarios del sistema
CREATE TABLE IF NOT EXISTS usuarios (
    id              SERIAL PRIMARY KEY,
    username        VARCHAR(50) UNIQUE NOT NULL,
    nombre          VARCHAR(100) NOT NULL,
    password        VARCHAR(64) NOT NULL,
    rol             VARCHAR(20) NOT NULL DEFAULT 'visualizacion'
                    CHECK(rol IN ('administrador','editor','visualizacion')),
    activo          BOOLEAN DEFAULT TRUE,
    face_descriptor TEXT DEFAULT '',
    face_registrado BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT NOW()
);

-- Artículos / Inventario
CREATE TABLE IF NOT EXISTS articulos (
    codigo          VARCHAR(50) PRIMARY KEY,
    articulo        VARCHAR(200) NOT NULL,
    descripcion     TEXT DEFAULT '',
    precio          DECIMAL(12,2) DEFAULT 0,
    entradas        DECIMAL(12,2) DEFAULT 0,
    salidas         DECIMAL(12,2) DEFAULT 0,
    stocks          DECIMAL(12,2) DEFAULT 0,
    imagen          VARCHAR(200) DEFAULT '',
    modificado_por  VARCHAR(50) DEFAULT '',
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

-- Índice de búsqueda de texto completo en artículos
CREATE INDEX IF NOT EXISTS idx_art_stock  ON articulos(stocks);
CREATE INDEX IF NOT EXISTS idx_art_codigo ON articulos(codigo);
CREATE INDEX IF NOT EXISTS idx_art_fts ON articulos USING gin(to_tsvector('spanish', articulo || ' ' || descripcion || ' ' || codigo));

-- Movimientos de inventario (entradas y salidas)
CREATE TABLE IF NOT EXISTS movimientos (
    id              SERIAL PRIMARY KEY,
    tipo            VARCHAR(7) NOT NULL CHECK(tipo IN ('ENTRADA','SALIDA')),
    codigo          VARCHAR(50) NOT NULL,
    articulo        VARCHAR(200) NOT NULL,
    fecha           DATE NOT NULL,
    cantidad        DECIMAL(12,2) NOT NULL CHECK(cantidad > 0),
    precio          DECIMAL(12,2) DEFAULT 0,
    notas           TEXT DEFAULT '',
    usuario         VARCHAR(50) DEFAULT '',
    modificado_por  VARCHAR(50) DEFAULT '',
    created_at      TIMESTAMP DEFAULT NOW(),
    FOREIGN KEY(codigo) REFERENCES articulos(codigo) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_mov_codigo ON movimientos(codigo);
CREATE INDEX IF NOT EXISTS idx_mov_tipo   ON movimientos(tipo);
CREATE INDEX IF NOT EXISTS idx_mov_fecha  ON movimientos(fecha DESC);

-- Proveedores
CREATE TABLE IF NOT EXISTS proveedores (
    id          SERIAL PRIMARY KEY,
    codigo      VARCHAR(50) UNIQUE NOT NULL,
    nombre      VARCHAR(200) NOT NULL,
    contacto    VARCHAR(100) DEFAULT '',
    telefono    VARCHAR(50) DEFAULT '',
    email       VARCHAR(100) DEFAULT '',
    direccion   TEXT DEFAULT '',
    nit         VARCHAR(20) DEFAULT '',
    notas       TEXT DEFAULT '',
    activo      BOOLEAN DEFAULT TRUE,
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);

-- Clientes
CREATE TABLE IF NOT EXISTS clientes (
    id          SERIAL PRIMARY KEY,
    codigo      VARCHAR(50) UNIQUE NOT NULL,
    nombre      VARCHAR(200) NOT NULL,
    contacto    VARCHAR(100) DEFAULT '',
    telefono    VARCHAR(50) DEFAULT '',
    email       VARCHAR(100) DEFAULT '',
    direccion   TEXT DEFAULT '',
    nit         VARCHAR(20) DEFAULT '',
    tipo        VARCHAR(20) DEFAULT 'individual',
    notas       TEXT DEFAULT '',
    activo      BOOLEAN DEFAULT TRUE,
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);

-- Empleados
CREATE TABLE IF NOT EXISTS empleados (
    id              SERIAL PRIMARY KEY,
    codigo          VARCHAR(50) UNIQUE NOT NULL,
    nombre          VARCHAR(100) NOT NULL,
    apellido        VARCHAR(100) DEFAULT '',
    puesto          VARCHAR(100) DEFAULT '',
    departamento    VARCHAR(100) DEFAULT '',
    telefono        VARCHAR(50) DEFAULT '',
    email           VARCHAR(100) DEFAULT '',
    dpi             VARCHAR(20) DEFAULT '',
    fecha_ingreso   DATE,
    salario         DECIMAL(12,2) DEFAULT 0,
    notas           TEXT DEFAULT '',
    activo          BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

-- Cotizaciones
CREATE TABLE IF NOT EXISTS cotizaciones (
    id                  SERIAL PRIMARY KEY,
    numero              VARCHAR(30) UNIQUE NOT NULL,
    cliente             VARCHAR(200) DEFAULT '',
    telefono            VARCHAR(50) DEFAULT '',
    fecha_cotizacion    DATE,
    items               TEXT NOT NULL DEFAULT '[]',
    notas               TEXT DEFAULT '',
    observaciones       TEXT DEFAULT '',
    total               DECIMAL(12,2) DEFAULT 0,
    usuario             VARCHAR(50) DEFAULT '',
    created_at          TIMESTAMP DEFAULT NOW()
);

-- Configuración de la empresa
CREATE TABLE IF NOT EXISTS config (
    clave   VARCHAR(100) PRIMARY KEY,
    valor   TEXT
);

-- Bitácora de actividad
CREATE TABLE IF NOT EXISTS bitacora (
    id          SERIAL PRIMARY KEY,
    usuario     VARCHAR(50) NOT NULL,
    accion      VARCHAR(50) NOT NULL,
    modulo      VARCHAR(50) NOT NULL,
    descripcion TEXT DEFAULT '',
    dato_id     VARCHAR(100) DEFAULT '',
    ip          VARCHAR(45) DEFAULT '',
    fecha       TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_bit_usuario ON bitacora(usuario);
CREATE INDEX IF NOT EXISTS idx_bit_fecha   ON bitacora(fecha DESC);
CREATE INDEX IF NOT EXISTS idx_bit_modulo  ON bitacora(modulo);

-- ── Datos por defecto ────────────────────────────────────────

-- FIX: Contraseñas de ejemplo con hashes SHA-256 correctos y verificados:
--   admin    → contraseña: admin123
--   editor1  → contraseña: editor123
--   viewer1  → contraseña: viewer123

-- SHA-256("admin123")   = 240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9
INSERT INTO usuarios (username, nombre, password, rol)
VALUES ('admin', 'Administrador', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'administrador')
ON CONFLICT (username) DO NOTHING;

-- SHA-256("editor123")  = ef5e5a1fb95055e0e56cccf98a41e784a132c14e7f6e1ba244302f0e72b29baf
INSERT INTO usuarios (username, nombre, password, rol)
VALUES ('editor1', 'Editor Uno', 'ef5e5a1fb95055e0e56cccf98a41e784a132c14e7f6e1ba244302f0e72b29baf', 'editor')
ON CONFLICT (username) DO NOTHING;

-- SHA-256("viewer123")  = 65375049b9e4d7cad6c9ba286fdeb9394b28135a3e84136404cfccfdcc438894
INSERT INTO usuarios (username, nombre, password, rol)
VALUES ('viewer1', 'Visualizador Uno', '65375049b9e4d7cad6c9ba286fdeb9394b28135a3e84136404cfccfdcc438894', 'visualizacion')
ON CONFLICT (username) DO NOTHING;

-- Configuración por defecto de la empresa
INSERT INTO config (clave, valor) VALUES ('empresa_nombre',    'DIPROGRA S.A.')            ON CONFLICT (clave) DO NOTHING;
INSERT INTO config (clave, valor) VALUES ('empresa_direccion', 'Guatemala')                 ON CONFLICT (clave) DO NOTHING;
INSERT INTO config (clave, valor) VALUES ('empresa_telefono',  '')                          ON CONFLICT (clave) DO NOTHING;
INSERT INTO config (clave, valor) VALUES ('empresa_email',     '')                          ON CONFLICT (clave) DO NOTHING;
INSERT INTO config (clave, valor) VALUES ('empresa_nit',       '')                          ON CONFLICT (clave) DO NOTHING;
INSERT INTO config (clave, valor) VALUES ('empresa_slogan',    'Soluciones para el campo')  ON CONFLICT (clave) DO NOTHING;
