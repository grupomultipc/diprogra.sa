package com.diprogra.controller;

import com.diprogra.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.*;

@RestController
public class MovimientosController {

    @Autowired private JdbcTemplate db;
    @Autowired private AuthService  auth;

    // ── GET /api/movimientos ────────────────────────────────────
    @GetMapping("/api/movimientos")
    public ResponseEntity<?> listar(
            @RequestParam(defaultValue="")  String tipo,
            @RequestParam(defaultValue="")  String q,
            @RequestParam(defaultValue="")  String fecha_de,
            @RequestParam(defaultValue="")  String fecha_a,
            @RequestParam(defaultValue="1")  int page,
            @RequestParam(defaultValue="50") int limit,
            HttpSession session) {

        if (!auth.isLoggedIn(session))
            return ResponseEntity.status(401).body(Map.of("error","No autenticado"));

        limit = Math.min(limit, 100);
        int offset = (Math.max(1, page) - 1) * limit;

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("1=1");

        tipo = tipo.toUpperCase();
        if ("ENTRADA".equals(tipo) || "SALIDA".equals(tipo)) {
            where.append(" AND tipo=?"); params.add(tipo);
        }
        if (!q.isBlank()) {
            where.append(" AND (codigo ILIKE ? OR articulo ILIKE ? OR notas ILIKE ?)");
            String like = "%" + q + "%";
            params.add(like); params.add(like); params.add(like);
        }
        if (!fecha_de.isBlank()) { where.append(" AND fecha >= ?::date"); params.add(fecha_de); }
        if (!fecha_a.isBlank())  { where.append(" AND fecha <= ?::date"); params.add(fecha_a);  }

        String sql = "WHERE " + where;
        int total = db.queryForObject("SELECT COUNT(*) FROM movimientos " + sql, Integer.class, params.toArray());

        params.add(limit); params.add(offset);
        List<Map<String, Object>> rows = db.queryForList(
            "SELECT * FROM movimientos " + sql + " ORDER BY fecha DESC, id DESC LIMIT ? OFFSET ?",
            params.toArray()
        );

        return ResponseEntity.ok(Map.of("total", total, "page", page, "rows", rows));
    }

    // ── POST /api/movimientos ────────────────────────────────────
    @PostMapping("/api/movimientos")
    public ResponseEntity<?> crear(@RequestBody Map<String, Object> body,
                                    HttpSession session, HttpServletRequest request) {
        if (!auth.canEdit(session))
            return ResponseEntity.status(403).body(Map.of("error","Sin permiso"));

        String tipo     = body.getOrDefault("tipo","").toString().toUpperCase();
        String codigo   = body.getOrDefault("codigo","").toString().trim().toUpperCase();
        String fecha    = body.getOrDefault("fecha", LocalDate.now().toString()).toString();
        double cantidad = Double.parseDouble(body.getOrDefault("cantidad","0").toString());
        double precio   = Double.parseDouble(body.getOrDefault("precio","0").toString());
        String notas    = body.getOrDefault("notas","").toString().trim();

        if (!"ENTRADA".equals(tipo) && !"SALIDA".equals(tipo))
            return ResponseEntity.badRequest().body(Map.of("error","Tipo inválido"));
        if (codigo.isBlank() || cantidad <= 0)
            return ResponseEntity.badRequest().body(Map.of("error","Código y cantidad requeridos"));

        List<Map<String, Object>> artRows = db.queryForList(
            "SELECT * FROM articulos WHERE codigo=?", codigo);
        if (artRows.isEmpty())
            return ResponseEntity.status(404).body(Map.of("error","Artículo "+codigo+" no encontrado"));

        Map<String, Object> art = artRows.get(0);
        double stockActual = ((Number) art.get("stocks")).doubleValue();

        if ("SALIDA".equals(tipo) && stockActual < cantidad)
            return ResponseEntity.badRequest().body(
                Map.of("error","Stock insuficiente. Disponible: "+stockActual));

        String nombreArt = (String) art.get("articulo");
        String usuario   = auth.sessionUser(session);

        db.update("""
            INSERT INTO movimientos (tipo,codigo,articulo,fecha,cantidad,precio,notas,usuario)
            VALUES (?,?,?,?::date,?,?,?,?)
            """, tipo, codigo, nombreArt, fecha, cantidad, precio, notas, usuario);

        if ("ENTRADA".equals(tipo)) {
            db.update("""
                UPDATE articulos SET entradas=entradas+?, stocks=stocks+?,
                modificado_por=?, updated_at=NOW() WHERE codigo=?
                """, cantidad, cantidad, usuario, codigo);
        } else {
            db.update("""
                UPDATE articulos SET salidas=salidas+?, stocks=stocks-?,
                modificado_por=?, updated_at=NOW() WHERE codigo=?
                """, cantidad, cantidad, usuario, codigo);
        }

        auth.logBit(tipo,"movimiento",
            tipo+" de "+cantidad+" — "+nombreArt, codigo, session, request);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── PUT /api/movimientos/{id} ────────────────────────────────
    @PutMapping("/api/movimientos/{id}")
    public ResponseEntity<?> editar(@PathVariable int id,
                                     @RequestBody Map<String, Object> body,
                                     HttpSession session, HttpServletRequest request) {
        if (!auth.canEdit(session))
            return ResponseEntity.status(403).body(Map.of("error","Sin permiso"));

        List<Map<String, Object>> rows = db.queryForList(
            "SELECT * FROM movimientos WHERE id=?", id);
        if (rows.isEmpty())
            return ResponseEntity.status(404).body(Map.of("error","No encontrado"));

        Map<String, Object> m = rows.get(0);
        double cantOld    = ((Number) m.get("cantidad")).doubleValue();
        double cantNueva  = Double.parseDouble(body.getOrDefault("cantidad", cantOld).toString());
        double precNuevo  = Double.parseDouble(body.getOrDefault("precio",   m.get("precio")).toString());
        String notasNueva = body.getOrDefault("notas", m.get("notas")).toString();
        String fechaNueva = body.getOrDefault("fecha", m.get("fecha").toString()).toString();
        String codigoArt  = (String) m.get("codigo");
        String tipoMov    = (String) m.get("tipo");

        if ("ENTRADA".equals(tipoMov)) {
            db.update("UPDATE articulos SET entradas=GREATEST(0,entradas-?), stocks=GREATEST(0,stocks-?) WHERE codigo=?",
                      cantOld, cantOld, codigoArt);
            db.update("UPDATE articulos SET entradas=entradas+?, stocks=stocks+? WHERE codigo=?",
                      cantNueva, cantNueva, codigoArt);
        } else {
            // FIX: queryForObject con Double puede devolver null — usar null-safe
            Double stockObj = db.queryForObject(
                "SELECT stocks FROM articulos WHERE codigo=?", Double.class, codigoArt);
            double stockActual = (stockObj != null) ? stockObj : 0.0;

            double nuevoStock = stockActual + cantOld - cantNueva;
            if (nuevoStock < 0)
                return ResponseEntity.badRequest().body(Map.of("error","Stock insuficiente para nueva cantidad"));

            db.update("UPDATE articulos SET salidas=GREATEST(0,salidas-?), stocks=stocks+? WHERE codigo=?",
                      cantOld, cantOld, codigoArt);
            db.update("UPDATE articulos SET salidas=salidas+?, stocks=stocks-? WHERE codigo=?",
                      cantNueva, cantNueva, codigoArt);
        }

        db.update("""
            UPDATE movimientos SET cantidad=?, precio=?, notas=?, fecha=?::date,
            modificado_por=? WHERE id=?
            """, cantNueva, precNuevo, notasNueva, fechaNueva,
            auth.sessionUser(session), id);

        auth.logBit("EDITAR","movimiento","Movimiento #"+id+" editado: cantidad="+cantNueva,
                    String.valueOf(id), session, request);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── DELETE /api/movimientos/{id} ─────────────────────────────
    @DeleteMapping("/api/movimientos/{id}")
    public ResponseEntity<?> eliminar(@PathVariable int id,
                                       HttpSession session, HttpServletRequest request) {
        if (!auth.isAdmin(session))
            return ResponseEntity.status(403).body(Map.of("error","Sin permiso"));

        List<Map<String, Object>> rows = db.queryForList(
            "SELECT * FROM movimientos WHERE id=?", id);
        if (rows.isEmpty())
            return ResponseEntity.status(404).body(Map.of("error","No encontrado"));

        Map<String, Object> mv = rows.get(0);
        String codigo   = (String) mv.get("codigo");
        double cantidad = ((Number) mv.get("cantidad")).doubleValue();
        String tipo     = (String) mv.get("tipo");
        String usuario  = auth.sessionUser(session);

        if ("ENTRADA".equals(tipo)) {
            db.update("UPDATE articulos SET entradas=GREATEST(0,entradas-?), stocks=GREATEST(0,stocks-?), modificado_por=? WHERE codigo=?",
                      cantidad, cantidad, usuario, codigo);
        } else {
            db.update("UPDATE articulos SET salidas=GREATEST(0,salidas-?), stocks=stocks+?, modificado_por=? WHERE codigo=?",
                      cantidad, cantidad, usuario, codigo);
        }

        db.update("DELETE FROM movimientos WHERE id=?", id);
        auth.logBit("ELIMINAR","movimiento",
            "Mov eliminado: "+tipo+" "+cantidad+" — "+mv.get("articulo"),
            String.valueOf(id), session, request);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
