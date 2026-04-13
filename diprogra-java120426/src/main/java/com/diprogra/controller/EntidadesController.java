package com.diprogra.controller;

import com.diprogra.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
public class EntidadesController {

    @Autowired private JdbcTemplate db;
    @Autowired private AuthService  auth;

    // ═══════════════════════════════════════════════════════════════
    // PROVEEDORES
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/api/proveedores")
    public ResponseEntity<?> listarProveedores(
            @RequestParam(defaultValue="")  String q,
            @RequestParam(defaultValue="1")  int page,
            @RequestParam(defaultValue="50") int limit,
            HttpSession session) {

        if (!auth.isLoggedIn(session)) return ResponseEntity.status(401).body(Map.of("error","No autenticado"));

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("1=1");
        if (!q.isBlank()) {
            where.append(" AND (codigo ILIKE ? OR nombre ILIKE ? OR contacto ILIKE ? OR telefono ILIKE ?)");
            String like = "%" + q + "%";
            params.add(like); params.add(like); params.add(like); params.add(like);
        }
        int offset = (Math.max(1, page) - 1) * limit;
        String sql = "WHERE " + where;
        int total = db.queryForObject("SELECT COUNT(*) FROM proveedores " + sql, Integer.class, params.toArray());
        params.add(limit); params.add(offset);
        List<Map<String, Object>> rows = db.queryForList(
            "SELECT * FROM proveedores " + sql + " ORDER BY nombre LIMIT ? OFFSET ?", params.toArray());
        return ResponseEntity.ok(Map.of("total", total, "page", page, "rows", rows));
    }

    @PostMapping("/api/proveedores")
    public ResponseEntity<?> crearProveedor(@RequestBody Map<String, Object> body,
                                             HttpSession session, HttpServletRequest request) {
        if (!auth.canEdit(session)) return ResponseEntity.status(403).body(Map.of("error","Sin permiso"));
        String codigo = s(body,"codigo").toUpperCase();
        String nombre = s(body,"nombre");
        if (codigo.isBlank() || nombre.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error","Código y nombre requeridos"));
        try {
            db.update("INSERT INTO proveedores (codigo,nombre,contacto,telefono,email,direccion,nit,notas) VALUES (?,?,?,?,?,?,?,?)",
                codigo, nombre, s(body,"contacto"), s(body,"telefono"), s(body,"email"),
                s(body,"direccion"), s(body,"nit"), s(body,"notas"));
            auth.logBit("CREAR","proveedor","Proveedor: "+nombre, codigo, session, request);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return ResponseEntity.status(409).body(Map.of("error","Código "+codigo+" ya existe"));
        }
    }

    @PutMapping("/api/proveedores/{id}")
    public ResponseEntity<?> editarProveedor(@PathVariable int id,
                                              @RequestBody Map<String, Object> body,
                                              HttpSession session, HttpServletRequest request) {
        if (!auth.canEdit(session)) return ResponseEntity.status(403).body(Map.of("error","Sin permiso"));
        List<Map<String, Object>> rows = db.queryForList("SELECT * FROM proveedores WHERE id=?", id);
        if (rows.isEmpty()) return ResponseEntity.status(404).body(Map.of("error","No encontrado"));
        Map<String, Object> p = rows.get(0);
        db.update("""
            UPDATE proveedores SET nombre=?,contacto=?,telefono=?,email=?,direccion=?,nit=?,
            notas=?,activo=?,updated_at=NOW() WHERE id=?
            """, get(body,"nombre",p), get(body,"contacto",p), get(body,"telefono",p),
            get(body,"email",p), get(body,"direccion",p), get(body,"nit",p),
            get(body,"notas",p), boolVal(body,"activo",p), id);
        auth.logBit("EDITAR","proveedor","Proveedor editado: "+s(body,"nombre"), String.valueOf(id), session, request);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @DeleteMapping("/api/proveedores/{id}")
    public ResponseEntity<?> eliminarProveedor(@PathVariable int id,
                                                HttpSession session, HttpServletRequest request) {
        if (!auth.isAdmin(session)) return ResponseEntity.status(403).body(Map.of("error","Sin permiso"));
        List<Map<String, Object>> rows = db.queryForList("SELECT nombre FROM proveedores WHERE id=?", id);
        db.update("DELETE FROM proveedores WHERE id=?", id);
        if (!rows.isEmpty()) auth.logBit("ELIMINAR","proveedor","Proveedor eliminado: "+rows.get(0).get("nombre"), String.valueOf(id), session, request);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ═══════════════════════════════════════════════════════════════
    // CLIENTES
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/api/clientes")
    public ResponseEntity<?> listarClientes(
            @RequestParam(defaultValue="")  String q,
            @RequestParam(defaultValue="1")  int page,
            @RequestParam(defaultValue="50") int limit,
            HttpSession session) {

        if (!auth.isLoggedIn(session)) return ResponseEntity.status(401).body(Map.of("error","No autenticado"));
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("1=1");
        if (!q.isBlank()) {
            where.append(" AND (codigo ILIKE ? OR nombre ILIKE ? OR telefono ILIKE ? OR nit ILIKE ?)");
            String like = "%" + q + "%";
            params.add(like); params.add(like); params.add(like); params.add(like);
        }
        int offset = (Math.max(1, page) - 1) * limit;
        String sql = "WHERE " + where;
        int total = db.queryForObject("SELECT COUNT(*) FROM clientes " + sql, Integer.class, params.toArray());
        params.add(limit); params.add(offset);
        List<Map<String, Object>> rows = db.queryForList(
            "SELECT * FROM clientes " + sql + " ORDER BY nombre LIMIT ? OFFSET ?", params.toArray());
        return ResponseEntity.ok(Map.of("total", total, "page", page, "rows", rows));
    }

    @PostMapping("/api/clientes")
    public ResponseEntity<?> crearCliente(@RequestBody Map<String, Object> body,
                                           HttpSession session, HttpServletRequest request) {
        if (!auth.canEdit(session)) return ResponseEntity.status(403).body(Map.of("error","Sin permiso"));
        String codigo = s(body,"codigo").toUpperCase();
        String nombre = s(body,"nombre");
        if (codigo.isBlank() || nombre.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error","Código y nombre requeridos"));
        try {
            db.update("INSERT INTO clientes (codigo,nombre,contacto,telefono,email,direccion,nit,tipo,notas) VALUES (?,?,?,?,?,?,?,?,?)",
                codigo, nombre, s(body,"contacto"), s(body,"telefono"), s(body,"email"),
                s(body,"direccion"), s(body,"nit"),
                s(body,"tipo").isBlank() ? "individual" : s(body,"tipo"),
                s(body,"notas"));
            auth.logBit("CREAR","cliente","Cliente: "+nombre, codigo, session, request);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return ResponseEntity.status(409).body(Map.of("error","Código "+codigo+" ya existe"));
        }
    }

    @PutMapping("/api/clientes/{id}")
    public ResponseEntity<?> editarCliente(@PathVariable int id,
                                            @RequestBody Map<String, Object> body,
                                            HttpSession session, HttpServletRequest request) {
        if (!auth.canEdit(session)) return ResponseEntity.status(403).body(Map.of("error","Sin permiso"));
        List<Map<String, Object>> rows = db.queryForList("SELECT * FROM clientes WHERE id=?", id);
        if (rows.isEmpty()) return ResponseEntity.status(404).body(Map.of("error","No encontrado"));
        Map<String, Object> cl = rows.get(0);
        db.update("""
            UPDATE clientes SET nombre=?,contacto=?,telefono=?,email=?,direccion=?,nit=?,tipo=?,
            notas=?,activo=?,updated_at=NOW() WHERE id=?
            """, get(body,"nombre",cl), get(body,"contacto",cl), get(body,"telefono",cl),
            get(body,"email",cl), get(body,"direccion",cl), get(body,"nit",cl),
            get(body,"tipo",cl), get(body,"notas",cl), boolVal(body,"activo",cl), id);
        auth.logBit("EDITAR","cliente","Cliente editado: "+s(body,"nombre"), String.valueOf(id), session, request);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @DeleteMapping("/api/clientes/{id}")
    public ResponseEntity<?> eliminarCliente(@PathVariable int id,
                                              HttpSession session, HttpServletRequest request) {
        if (!auth.isAdmin(session)) return ResponseEntity.status(403).body(Map.of("error","Sin permiso"));
        List<Map<String, Object>> rows = db.queryForList("SELECT nombre FROM clientes WHERE id=?", id);
        db.update("DELETE FROM clientes WHERE id=?", id);
        if (!rows.isEmpty()) auth.logBit("ELIMINAR","cliente","Cliente eliminado: "+rows.get(0).get("nombre"), String.valueOf(id), session, request);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ═══════════════════════════════════════════════════════════════
    // EMPLEADOS
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/api/empleados")
    public ResponseEntity<?> listarEmpleados(
            @RequestParam(defaultValue="")  String q,
            @RequestParam(defaultValue="1")  int page,
            @RequestParam(defaultValue="50") int limit,
            HttpSession session) {

        if (!auth.isLoggedIn(session)) return ResponseEntity.status(401).body(Map.of("error","No autenticado"));
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("1=1");
        if (!q.isBlank()) {
            where.append(" AND (codigo ILIKE ? OR nombre ILIKE ? OR apellido ILIKE ? OR puesto ILIKE ?)");
            String like = "%" + q + "%";
            params.add(like); params.add(like); params.add(like); params.add(like);
        }
        int offset = (Math.max(1, page) - 1) * limit;
        String sql = "WHERE " + where;
        int total = db.queryForObject("SELECT COUNT(*) FROM empleados " + sql, Integer.class, params.toArray());
        params.add(limit); params.add(offset);
        List<Map<String, Object>> rows = db.queryForList(
            "SELECT * FROM empleados " + sql + " ORDER BY nombre LIMIT ? OFFSET ?", params.toArray());
        return ResponseEntity.ok(Map.of("total", total, "page", page, "rows", rows));
    }

    @PostMapping("/api/empleados")
    public ResponseEntity<?> crearEmpleado(@RequestBody Map<String, Object> body,
                                            HttpSession session, HttpServletRequest request) {
        if (!auth.canEdit(session)) return ResponseEntity.status(403).body(Map.of("error","Sin permiso"));
        String codigo = s(body,"codigo").toUpperCase();
        String nombre = s(body,"nombre");
        if (codigo.isBlank() || nombre.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error","Código y nombre requeridos"));

        String fechaIngreso = s(body,"fecha_ingreso");
        Object fi = fechaIngreso.isBlank() ? null : fechaIngreso;
        double salario = 0;
        try { salario = Double.parseDouble(s(body,"salario")); } catch (Exception ignored) {}

        try {
            db.update("INSERT INTO empleados (codigo,nombre,apellido,puesto,departamento,telefono,email,dpi,fecha_ingreso,salario,notas) VALUES (?,?,?,?,?,?,?,?,?::date,?,?)",
                codigo, nombre, s(body,"apellido"), s(body,"puesto"), s(body,"departamento"),
                s(body,"telefono"), s(body,"email"), s(body,"dpi"), fi, salario, s(body,"notas"));
            auth.logBit("CREAR","empleado","Empleado: "+nombre, codigo, session, request);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return ResponseEntity.status(409).body(Map.of("error","Código "+codigo+" ya existe"));
        }
    }

    @PutMapping("/api/empleados/{id}")
    public ResponseEntity<?> editarEmpleado(@PathVariable int id,
                                             @RequestBody Map<String, Object> body,
                                             HttpSession session, HttpServletRequest request) {
        if (!auth.canEdit(session)) return ResponseEntity.status(403).body(Map.of("error","Sin permiso"));
        List<Map<String, Object>> rows = db.queryForList("SELECT * FROM empleados WHERE id=?", id);
        if (rows.isEmpty()) return ResponseEntity.status(404).body(Map.of("error","No encontrado"));
        Map<String, Object> em = rows.get(0);

        String fechaIngreso = body.containsKey("fecha_ingreso") ? s(body,"fecha_ingreso") : "";
        Object fi = fechaIngreso.isBlank() ? em.get("fecha_ingreso") : fechaIngreso;
        double salario;
        try { salario = Double.parseDouble(s(body,"salario")); }
        catch (Exception e) { salario = ((Number)em.get("salario")).doubleValue(); }

        db.update("""
            UPDATE empleados SET nombre=?,apellido=?,puesto=?,departamento=?,telefono=?,email=?,
            dpi=?,fecha_ingreso=?::date,salario=?,notas=?,activo=?,updated_at=NOW() WHERE id=?
            """, get(body,"nombre",em), get(body,"apellido",em), get(body,"puesto",em),
            get(body,"departamento",em), get(body,"telefono",em), get(body,"email",em),
            get(body,"dpi",em), fi, salario, get(body,"notas",em), boolVal(body,"activo",em), id);

        auth.logBit("EDITAR","empleado","Empleado editado: "+s(body,"nombre"), String.valueOf(id), session, request);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @DeleteMapping("/api/empleados/{id}")
    public ResponseEntity<?> eliminarEmpleado(@PathVariable int id,
                                               HttpSession session, HttpServletRequest request) {
        if (!auth.isAdmin(session)) return ResponseEntity.status(403).body(Map.of("error","Sin permiso"));
        List<Map<String, Object>> rows = db.queryForList("SELECT nombre FROM empleados WHERE id=?", id);
        db.update("DELETE FROM empleados WHERE id=?", id);
        if (!rows.isEmpty()) auth.logBit("ELIMINAR","empleado","Empleado eliminado: "+rows.get(0).get("nombre"), String.valueOf(id), session, request);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── Helpers ─────────────────────────────────────────────────
    private String s(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString().trim() : "";
    }

    private Object get(Map<String, Object> body, String key, Map<String, Object> fallback) {
        return body.containsKey(key) ? body.get(key) : fallback.get(key);
    }

    private boolean boolVal(Map<String, Object> body, String key, Map<String, Object> fallback) {
        Object v = body.containsKey(key) ? body.get(key) : fallback.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        return true;
    }
}
