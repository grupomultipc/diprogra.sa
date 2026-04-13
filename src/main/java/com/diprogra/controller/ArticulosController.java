package com.diprogra.controller;

import com.diprogra.config.AppConfig;
import com.diprogra.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@RestController
public class ArticulosController {

    @Autowired private JdbcTemplate db;
    @Autowired private AuthService  auth;
    @Autowired private AppConfig    cfg;

    private static final Set<String> ALLOWED_EXT = Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp");

    // ── GET /api/articulos ───────────────────────────────────────
    @GetMapping("/api/articulos")
    public ResponseEntity<?> listar(
            @RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="") String filtro,
            @RequestParam(defaultValue="1")  int page,
            @RequestParam(defaultValue="50") int limit,
            HttpSession session) {

        if (!auth.isLoggedIn(session))
            return ResponseEntity.status(401).body(Map.of("error","No autenticado"));

        limit = Math.min(limit, 100);
        int offset = (Math.max(1, page) - 1) * limit;

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("1=1");

        if (!q.isBlank()) {
            where.append(" AND (codigo ILIKE ? OR articulo ILIKE ? OR descripcion ILIKE ?)");
            String like = "%" + q + "%";
            params.add(like); params.add(like); params.add(like);
        }
        if ("stock".equals(filtro))        { where.append(" AND stocks > 0"); }
        else if ("nostock".equals(filtro)) { where.append(" AND stocks = 0"); }
        else if ("low".equals(filtro))     { where.append(" AND stocks > 0 AND stocks < 10"); }

        String sql = "WHERE " + where;
        int total = db.queryForObject("SELECT COUNT(*) FROM articulos " + sql, Integer.class, params.toArray());

        params.add(limit); params.add(offset);
        List<Map<String, Object>> rows = db.queryForList(
            "SELECT * FROM articulos " + sql + " ORDER BY codigo LIMIT ? OFFSET ?",
            params.toArray()
        );

        return ResponseEntity.ok(Map.of("total", total, "page", page, "rows", rows));
    }

    // ── GET /api/articulos/{codigo} ──────────────────────────────
    @GetMapping("/api/articulos/{codigo}")
    public ResponseEntity<?> getOne(@PathVariable String codigo, HttpSession session) {
        if (!auth.isLoggedIn(session))
            return ResponseEntity.status(401).body(Map.of("error","No autenticado"));
        List<Map<String, Object>> rows = db.queryForList(
            "SELECT * FROM articulos WHERE codigo = ?", codigo.toUpperCase());
        if (rows.isEmpty()) return ResponseEntity.status(404).body(Map.of("error","No encontrado"));
        return ResponseEntity.ok(rows.get(0));
    }

    // ── POST /api/articulos ─────────────────────────────────────
    @PostMapping("/api/articulos")
    public ResponseEntity<?> crear(
            @RequestParam("codigo")                 String codigo,
            @RequestParam("articulo")               String articulo,
            @RequestParam(value="descripcion", defaultValue="") String descripcion,
            @RequestParam(value="precio",      defaultValue="0") double precio,
            @RequestParam(value="entradas",    defaultValue="0") double entradas,
            @RequestParam(value="salidas",     defaultValue="0") double salidas,
            @RequestParam(value="stocks",      defaultValue="0") double stocks,
            @RequestParam(value="imagen",      required=false)   MultipartFile imagenFile,
            HttpSession session, HttpServletRequest request) {

        if (!auth.canEdit(session))
            return ResponseEntity.status(403).body(Map.of("error","Sin permiso"));

        codigo  = codigo.trim().toUpperCase();
        articulo = articulo.trim().toUpperCase();
        if (codigo.isBlank() || articulo.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error","Código y artículo requeridos"));

        String imagen = saveImage(imagenFile, codigo);

        try {
            db.update("""
                INSERT INTO articulos (codigo,articulo,descripcion,precio,entradas,salidas,stocks,imagen)
                VALUES (?,?,?,?,?,?,?,?)
                """, codigo, articulo, descripcion, precio, entradas, salidas, stocks, imagen);
            auth.logBit("CREAR","articulo","Nuevo artículo: "+articulo, codigo, session, request);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return ResponseEntity.status(409).body(Map.of("error","Código "+codigo+" ya existe"));
        }
    }

    // ── PUT /api/articulos/{codigo} ──────────────────────────────
    @PutMapping("/api/articulos/{codigo}")
    public ResponseEntity<?> editar(
            @PathVariable String codigo,
            @RequestParam("articulo")               String articulo,
            @RequestParam(value="descripcion", defaultValue="") String descripcion,
            @RequestParam(value="precio",      defaultValue="0") double precio,
            @RequestParam(value="entradas",    defaultValue="0") double entradas,
            @RequestParam(value="salidas",     defaultValue="0") double salidas,
            @RequestParam(value="stocks",      defaultValue="0") double stocks,
            @RequestParam(value="imagen_actual",defaultValue="") String imagenActual,
            @RequestParam(value="imagen",      required=false)   MultipartFile imagenFile,
            HttpSession session, HttpServletRequest request) {

        if (!auth.canEdit(session))
            return ResponseEntity.status(403).body(Map.of("error","Sin permiso"));

        articulo = articulo.trim().toUpperCase();
        if (articulo.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error","Artículo requerido"));

        String imagen = imagenActual;
        String newImg = saveImage(imagenFile, codigo.toUpperCase());
        if (!newImg.isBlank()) imagen = newImg;

        db.update("""
            UPDATE articulos SET articulo=?,descripcion=?,precio=?,entradas=?,salidas=?,stocks=?,
            imagen=?,modificado_por=?,updated_at=NOW() WHERE codigo=?
            """, articulo, descripcion, precio, entradas, salidas, stocks,
            imagen, auth.sessionUser(session), codigo.toUpperCase());

        auth.logBit("EDITAR","articulo","Artículo editado: "+articulo, codigo.toUpperCase(), session, request);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── DELETE /api/articulos/{codigo} ───────────────────────────
    @DeleteMapping("/api/articulos/{codigo}")
    public ResponseEntity<?> eliminar(@PathVariable String codigo,
                                       HttpSession session, HttpServletRequest request) {
        if (!auth.isAdmin(session))
            return ResponseEntity.status(403).body(Map.of("error","Sin permiso"));

        List<Map<String, Object>> rows = db.queryForList(
            "SELECT articulo, imagen FROM articulos WHERE codigo=?", codigo.toUpperCase());
        if (!rows.isEmpty()) {
            String img = (String) rows.get(0).get("imagen");
            if (img != null && !img.isBlank()) {
                try { Files.deleteIfExists(cfg.getUploadsPath().resolve(img)); } catch (IOException ignored) {}
            }
        }

        db.update("DELETE FROM movimientos WHERE codigo=?",  codigo.toUpperCase());
        db.update("DELETE FROM articulos   WHERE codigo=?",  codigo.toUpperCase());

        String nombre = rows.isEmpty() ? codigo : (String) rows.get(0).get("articulo");
        auth.logBit("ELIMINAR","articulo","Artículo eliminado: "+nombre, codigo.toUpperCase(), session, request);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── GET /api/imagenes/{filename} ─────────────────────────────
    // FIX: Ruta movida de /api/articulos/imagen/{filename} a /api/imagenes/{filename}
    //      para evitar conflicto con /api/articulos/{codigo} donde codigo="imagen"
    @GetMapping("/api/imagenes/{filename}")
    public ResponseEntity<byte[]> getImagen(@PathVariable String filename, HttpSession session) throws IOException {
        if (!auth.isLoggedIn(session)) return ResponseEntity.status(401).build();

        // FIX: Validar que el filename no contenga path traversal (ej: ../../etc/passwd)
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\"))
            return ResponseEntity.badRequest().build();

        Path path = cfg.getUploadsPath().resolve(filename);
        if (!Files.exists(path)) return ResponseEntity.notFound().build();
        byte[] data = Files.readAllBytes(path);
        String ct = filename.endsWith(".png")  ? "image/png"  :
                    filename.endsWith(".gif")  ? "image/gif"  :
                    filename.endsWith(".webp") ? "image/webp" : "image/jpeg";
        return ResponseEntity.ok().header("Content-Type", ct).body(data);
    }

    // ── GET /api/buscar ──────────────────────────────────────────
    @GetMapping("/api/buscar")
    public ResponseEntity<?> buscar(
            @RequestParam(defaultValue="")  String q,
            @RequestParam(defaultValue="20") int limit,
            HttpSession session) {

        if (!auth.isLoggedIn(session))
            return ResponseEntity.status(401).body(Map.of("error","No autenticado"));
        if (q.isBlank()) return ResponseEntity.ok(List.of());

        // 1. Búsqueda full-text PostgreSQL
        List<Map<String, Object>> rows = db.queryForList("""
            SELECT codigo, articulo, stocks, precio, imagen
            FROM articulos
            WHERE to_tsvector('spanish', articulo || ' ' || descripcion || ' ' || codigo)
                  @@ plainto_tsquery('spanish', ?)
            ORDER BY stocks DESC LIMIT ?
            """, q, limit);

        // 2. Fallback ILIKE (substring)
        if (rows.isEmpty()) {
            String like = "%" + q + "%";
            rows = db.queryForList("""
                SELECT codigo, articulo, stocks, precio, imagen FROM articulos
                WHERE codigo ILIKE ? OR articulo ILIKE ? OR descripcion ILIKE ?
                ORDER BY codigo LIMIT ?
                """, like, like, like, limit);
        }

        // 3. Búsqueda por iniciales: "mt" → artículos cuyas palabras empiezan con M y T
        //    Solo se activa si el query es puro alfabético sin espacios (ej: "mt", "bc", "fs")
        if (rows.isEmpty() && q.matches("[a-zA-Z]{2,6}")) {
            // Construir regex de iniciales: "mt" → "^M.*\sT" no es simple en SQL,
            // usamos una función que convierte el artículo a sus iniciales y compara
            String inicialesPattern = q.toUpperCase().chars()
                .mapToObj(c -> String.valueOf((char)c))
                .collect(java.util.stream.Collectors.joining("%"));
            // Buscar artículos donde el inicio de cada palabra corresponda a las iniciales
            // Técnica: regex PostgreSQL ~ para hacer matching de iniciales
            String regexPattern = java.util.Arrays.stream(q.toUpperCase().split(""))
                .map(c -> c + "[A-Z0-9]*")
                .collect(java.util.stream.Collectors.joining("\\s+"));
            rows = db.queryForList("""
                SELECT codigo, articulo, stocks, precio, imagen FROM articulos
                WHERE articulo ~* ?
                ORDER BY articulo LIMIT ?
                """, "\\m" + regexPattern, limit);
        }

        return ResponseEntity.ok(rows);
    }

    // ── Utilidad: guardar imagen ─────────────────────────────────
    private String saveImage(MultipartFile file, String codigo) {
        if (file == null || file.isEmpty()) return "";
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.contains(".")) return "";
        String ext = originalName.substring(originalName.lastIndexOf('.')).toLowerCase();
        if (!ALLOWED_EXT.contains(ext)) return "";
        try {
            String fname = codigo + ext;
            Files.copy(file.getInputStream(), cfg.getUploadsPath().resolve(fname),
                       StandardCopyOption.REPLACE_EXISTING);
            return fname;
        } catch (IOException e) {
            return "";
        }
    }
}
