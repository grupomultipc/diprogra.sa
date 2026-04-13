package com.diprogra.controller;

import com.diprogra.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
public class DashboardController {

    @Autowired private JdbcTemplate db;
    @Autowired private AuthService  auth;

    // ── Dashboard ────────────────────────────────────────────────
    @GetMapping("/api/dashboard")
    public ResponseEntity<?> dashboard(HttpSession session) {
        if (!auth.isLoggedIn(session))
            return ResponseEntity.status(401).body(Map.of("error","No autenticado"));

        Map<String, Object> stats = db.queryForMap("""
            SELECT COUNT(*) total_art,
                   COALESCE(SUM(stocks),0) total_stock,
                   SUM(CASE WHEN stocks=0 THEN 1 ELSE 0 END) sin_stock,
                   SUM(CASE WHEN stocks>0 AND stocks<10 THEN 1 ELSE 0 END) bajo_stock,
                   COALESCE(SUM(entradas),0) total_entradas,
                   COALESCE(SUM(salidas),0)  total_salidas
            FROM articulos
            """);

        List<Map<String, Object>> topStock = db.queryForList("""
            SELECT codigo,articulo,stocks,imagen FROM articulos
            WHERE stocks>0 ORDER BY stocks DESC LIMIT 10""");

        List<Map<String, Object>> ultSalidas = db.queryForList("""
            SELECT codigo,articulo,fecha,cantidad FROM movimientos
            WHERE tipo='SALIDA' ORDER BY fecha DESC,id DESC LIMIT 12""");

        List<Map<String, Object>> ultEntradas = db.queryForList("""
            SELECT codigo,articulo,fecha,cantidad FROM movimientos
            WHERE tipo='ENTRADA' ORDER BY fecha DESC,id DESC LIMIT 12""");

        List<Map<String, Object>> porMes = db.queryForList("""
            SELECT TO_CHAR(fecha,'YYYY-MM') mes,
                   SUM(CASE WHEN tipo='ENTRADA' THEN cantidad ELSE 0 END) entradas,
                   SUM(CASE WHEN tipo='SALIDA'  THEN cantidad ELSE 0 END) salidas
            FROM movimientos WHERE fecha >= NOW() - INTERVAL '6 months'
            GROUP BY mes ORDER BY mes""");

        List<Map<String, Object>> alertas = db.queryForList("""
            SELECT codigo,articulo,stocks FROM articulos
            WHERE stocks>=0 AND stocks<10 ORDER BY stocks LIMIT 8""");

        return ResponseEntity.ok(Map.of(
            "stats",           stats,
            "top_stock",       topStock,
            "ultimas_salidas", ultSalidas,
            "ultimas_entradas",ultEntradas,
            "por_mes",         porMes,
            "alertas",         alertas
        ));
    }

    // ── Cotizaciones ─────────────────────────────────────────────
    @GetMapping("/api/cotizaciones")
    public ResponseEntity<?> listarCotizaciones(
            @RequestParam(defaultValue="1") int page,
            HttpSession session) {
        if (!auth.isLoggedIn(session)) return ResponseEntity.status(401).body(Map.of("error","No autenticado"));
        int limit = 20, offset = (Math.max(1,page)-1)*limit;
        int total = db.queryForObject("SELECT COUNT(*) FROM cotizaciones", Integer.class);
        List<Map<String, Object>> rows = db.queryForList(
            "SELECT * FROM cotizaciones ORDER BY id DESC LIMIT ? OFFSET ?", limit, offset);
        return ResponseEntity.ok(Map.of("total",total,"rows",rows));
    }

    @PostMapping("/api/cotizaciones")
    public ResponseEntity<?> crearCotizacion(@RequestBody Map<String, Object> body,
                                              HttpSession session, HttpServletRequest request) {
        if (!auth.canEdit(session)) return ResponseEntity.status(403).body(Map.of("error","Sin permiso"));

        String num = "COT-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                    + "-" + System.currentTimeMillis() % 10000;

        List<?> items = (List<?>) body.getOrDefault("items", List.of());
        double total = items.stream().mapToDouble(i -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> mp = (Map<String, Object>) i;
                return Double.parseDouble(String.valueOf(mp.getOrDefault("cantidad", "0")))
                     * Double.parseDouble(String.valueOf(mp.getOrDefault("precio", "0")));
            } catch (Exception e) { return 0; }
        }).sum();

        String fechaCot = body.getOrDefault("fecha_cotizacion", LocalDate.now().toString()).toString();
        if (fechaCot.isBlank()) fechaCot = LocalDate.now().toString();

        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        String itemsJson;
        try { itemsJson = om.writeValueAsString(items); }
        catch (Exception e) { itemsJson = "[]"; }

        db.update("""
            INSERT INTO cotizaciones (numero,cliente,telefono,fecha_cotizacion,items,notas,observaciones,total,usuario)
            VALUES (?,?,?,?::date,?,?,?,?,?)
            """, num, s(body,"cliente"), s(body,"telefono"), fechaCot,
            itemsJson, s(body,"notas"), s(body,"observaciones"),
            total, auth.sessionUser(session));

        auth.logBit("CREAR","cotizacion","Cotización "+num+" — Cliente: "+s(body,"cliente")+" Total: Q"+total,
                    num, session, request);
        return ResponseEntity.ok(Map.of("ok",true,"numero",num));
    }

    @GetMapping("/api/cotizaciones/{id}")
    public ResponseEntity<?> getCotizacion(@PathVariable int id, HttpSession session) {
        if (!auth.isLoggedIn(session)) return ResponseEntity.status(401).body(Map.of("error","No autenticado"));
        List<Map<String, Object>> rows = db.queryForList("SELECT * FROM cotizaciones WHERE id=?", id);
        if (rows.isEmpty()) return ResponseEntity.status(404).body(Map.of("error","No encontrada"));
        Map<String, Object> r = new HashMap<>(rows.get(0));
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            r.put("items", om.readValue(r.get("items").toString(), List.class));
        } catch (Exception e) { r.put("items", List.of()); }
        return ResponseEntity.ok(r);
    }

    // ── Usuarios ─────────────────────────────────────────────────
    @GetMapping("/api/usuarios")
    public ResponseEntity<?> listarUsuarios(HttpSession session) {
        if (!auth.isAdmin(session)) return ResponseEntity.status(403).body(Map.of("error","Sin permiso"));
        List<Map<String, Object>> rows = db.queryForList(
            "SELECT id,username,nombre,rol,activo,created_at FROM usuarios ORDER BY id");
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/api/usuarios")
    public ResponseEntity<?> crearUsuario(@RequestBody Map<String, Object> body,
                                           HttpSession session, HttpServletRequest request) {
        if (!auth.isAdmin(session)) return ResponseEntity.status(403).body(Map.of("error","Sin permiso"));
        String username = s(body,"username").toLowerCase();
        String nombre   = s(body,"nombre");
        String password = s(body,"password");
        String rol      = s(body,"rol").isBlank() ? "visualizacion" : s(body,"rol");
        if (username.isBlank() || nombre.isBlank() || password.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error","Todos los campos son requeridos"));
        try {
            db.update("INSERT INTO usuarios (username,nombre,password,rol) VALUES (?,?,?,?)",
                username, nombre, auth.hashPassword(password), rol);
            auth.logBit("CREAR","usuario","Usuario creado: "+username+" ("+rol+")", username, session, request);
            return ResponseEntity.ok(Map.of("ok",true));
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return ResponseEntity.status(409).body(Map.of("error","Usuario '"+username+"' ya existe"));
        }
    }

    @PutMapping("/api/usuarios/{id}")
    public ResponseEntity<?> editarUsuario(@PathVariable int id,
                                            @RequestBody Map<String, Object> body,
                                            HttpSession session, HttpServletRequest request) {
        if (!auth.isAdmin(session)) return ResponseEntity.status(403).body(Map.of("error","Sin permiso"));
        List<Map<String, Object>> rows = db.queryForList("SELECT * FROM usuarios WHERE id=?", id);
        if (rows.isEmpty()) return ResponseEntity.status(404).body(Map.of("error","No encontrado"));
        Map<String, Object> u = rows.get(0);
        String nombre = body.containsKey("nombre") ? s(body,"nombre") : (String) u.get("nombre");
        String rol    = body.containsKey("rol")    ? s(body,"rol")    : (String) u.get("rol");
        boolean activo = body.containsKey("activo") ?
            Boolean.parseBoolean(body.get("activo").toString()) :
            (Boolean) u.get("activo");

        if (body.containsKey("password") && !s(body,"password").isBlank()) {
            db.update("UPDATE usuarios SET nombre=?,rol=?,activo=?,password=? WHERE id=?",
                nombre, rol, activo, auth.hashPassword(s(body,"password")), id);
        } else {
            db.update("UPDATE usuarios SET nombre=?,rol=?,activo=? WHERE id=?", nombre, rol, activo, id);
        }
        auth.logBit("EDITAR","usuario","Usuario editado: "+u.get("username")+" → rol="+rol, u.get("username").toString(), session, request);
        return ResponseEntity.ok(Map.of("ok",true));
    }

    @DeleteMapping("/api/usuarios/{id}")
    public ResponseEntity<?> eliminarUsuario(@PathVariable int id,
                                              HttpSession session, HttpServletRequest request) {
        if (!auth.isAdmin(session)) return ResponseEntity.status(403).body(Map.of("error","Sin permiso"));
        String uidSession = session.getAttribute("uid") != null ? session.getAttribute("uid").toString() : "";
        if (String.valueOf(id).equals(uidSession))
            return ResponseEntity.badRequest().body(Map.of("error","No puedes eliminar tu propio usuario"));
        List<Map<String, Object>> rows = db.queryForList("SELECT username,nombre FROM usuarios WHERE id=?", id);
        db.update("DELETE FROM usuarios WHERE id=?", id);
        if (!rows.isEmpty()) auth.logBit("ELIMINAR","usuario","Usuario eliminado: "+rows.get(0).get("username"), rows.get(0).get("username").toString(), session, request);
        return ResponseEntity.ok(Map.of("ok",true));
    }

    // ── Config empresa ───────────────────────────────────────────
    @GetMapping("/api/config")
    public ResponseEntity<?> getConfig(HttpSession session) {
        if (!auth.isLoggedIn(session)) return ResponseEntity.status(401).body(Map.of("error","No autenticado"));
        List<Map<String, Object>> rows = db.queryForList("SELECT clave, valor FROM config");
        Map<String, Object> result = new HashMap<>();
        for (Map<String, Object> r : rows) result.put(r.get("clave").toString(), r.get("valor"));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/config")
    public ResponseEntity<?> setConfig(@RequestBody Map<String, Object> body,
                                        HttpSession session, HttpServletRequest request) {
        if (!auth.isAdmin(session)) return ResponseEntity.status(403).body(Map.of("error","Sin permiso"));
        body.forEach((k, v) -> db.update(
            "INSERT INTO config (clave,valor) VALUES (?,?) ON CONFLICT (clave) DO UPDATE SET valor=EXCLUDED.valor",
            k, v != null ? v.toString() : ""));
        auth.logBit("EDITAR","config","Configuración de empresa actualizada","", session, request);
        return ResponseEntity.ok(Map.of("ok",true));
    }

    // ── Bitácora ─────────────────────────────────────────────────
    @GetMapping("/api/bitacora")
    public ResponseEntity<?> bitacora(
            @RequestParam(defaultValue="")  String q,
            @RequestParam(defaultValue="")  String modulo,
            @RequestParam(defaultValue="")  String usuario,
            @RequestParam(defaultValue="")  String fecha_de,
            @RequestParam(defaultValue="")  String fecha_a,
            @RequestParam(defaultValue="1") int page,
            HttpSession session) {

        if (!auth.isAdmin(session)) return ResponseEntity.status(403).body(Map.of("error","Sin permiso"));
        int limit = 60, offset = (Math.max(1,page)-1)*limit;
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("1=1");
        if (!q.isBlank()) {
            where.append(" AND (descripcion ILIKE ? OR accion ILIKE ? OR dato_id ILIKE ?)");
            String like = "%" + q + "%";
            params.add(like); params.add(like); params.add(like);
        }
        if (!modulo.isBlank())  { where.append(" AND modulo=?");    params.add(modulo); }
        if (!usuario.isBlank()) { where.append(" AND usuario=?");   params.add(usuario); }
        if (!fecha_de.isBlank()){ where.append(" AND fecha::date>=?::date"); params.add(fecha_de); }
        if (!fecha_a.isBlank()) { where.append(" AND fecha::date<=?::date"); params.add(fecha_a); }

        String sql = "WHERE " + where;
        int total = db.queryForObject("SELECT COUNT(*) FROM bitacora "+sql, Integer.class, params.toArray());
        params.add(limit); params.add(offset);
        List<Map<String,Object>> rows = db.queryForList("SELECT * FROM bitacora "+sql+" ORDER BY id DESC LIMIT ? OFFSET ?", params.toArray());
        List<String> modulos  = db.queryForList("SELECT DISTINCT modulo FROM bitacora ORDER BY modulo", String.class);
        List<String> usuarios = db.queryForList("SELECT DISTINCT usuario FROM bitacora ORDER BY usuario", String.class);
        return ResponseEntity.ok(Map.of("total",total,"page",page,"rows",rows,"modulos",modulos,"usuarios",usuarios));
    }

    @GetMapping("/api/bitacora/stats")
    public ResponseEntity<?> bitacoraStats(HttpSession session) {
        if (!auth.isAdmin(session)) return ResponseEntity.status(403).body(Map.of("error","Sin permiso"));
        int total = db.queryForObject("SELECT COUNT(*) FROM bitacora", Integer.class);
        int hoy   = db.queryForObject("SELECT COUNT(*) FROM bitacora WHERE fecha::date = CURRENT_DATE", Integer.class);
        List<Map<String,Object>> xUsr = db.queryForList("SELECT usuario, COUNT(*) cnt FROM bitacora GROUP BY usuario ORDER BY cnt DESC LIMIT 6");
        List<Map<String,Object>> xMod = db.queryForList("SELECT modulo, COUNT(*) cnt FROM bitacora GROUP BY modulo ORDER BY cnt DESC");
        List<Map<String,Object>> xDia = db.queryForList("SELECT fecha::date dia, COUNT(*) cnt FROM bitacora WHERE fecha >= NOW()-INTERVAL '7 days' GROUP BY dia ORDER BY dia");
        return ResponseEntity.ok(Map.of("total",total,"hoy",hoy,"por_usuario",xUsr,"por_modulo",xMod,"por_dia",xDia));
    }

    // ── Informes ──────────────────────────────────────────────────
    @GetMapping("/api/informes/inventario")
    public ResponseEntity<?> infInventario(
            @RequestParam(defaultValue="month") String period,
            @RequestParam(defaultValue="")      String de,
            @RequestParam(defaultValue="")      String hasta,
            HttpSession session) {
        if (!auth.isLoggedIn(session)) return ResponseEntity.status(401).body(Map.of("error","No autenticado"));
        MovPeriodResult res = buildMovPeriodSql(period, de, hasta, "");
        List<Map<String,Object>> mov     = db.queryForList(res.sql, res.params);
        List<Map<String,Object>> topArt  = db.queryForList("SELECT articulo,stocks FROM articulos WHERE stocks>0 ORDER BY stocks DESC LIMIT 10");
        int sinStock  = db.queryForObject("SELECT COUNT(*) FROM articulos WHERE stocks=0", Integer.class);
        int bajoStock = db.queryForObject("SELECT COUNT(*) FROM articulos WHERE stocks>0 AND stocks<10", Integer.class);
        int totalArt  = db.queryForObject("SELECT COUNT(*) FROM articulos", Integer.class);
        double valor  = db.queryForObject("SELECT COALESCE(SUM(stocks*precio),0) FROM articulos", Double.class);
        return ResponseEntity.ok(Map.of("movimientos",mov,"top_articulos",topArt,"sin_stock",sinStock,
            "bajo_stock",bajoStock,"total_art",totalArt,"valor_total",valor));
    }

    @GetMapping("/api/informes/movimientos")
    public ResponseEntity<?> infMovimientos(
            @RequestParam(defaultValue="month") String period,
            @RequestParam(defaultValue="")      String de,
            @RequestParam(defaultValue="")      String hasta,
            @RequestParam(defaultValue="")      String tipo,
            HttpSession session) {
        if (!auth.isLoggedIn(session)) return ResponseEntity.status(401).body(Map.of("error","No autenticado"));
        // FIX: Validar tipo para evitar SQL injection
        String tipoFilter = ("ENTRADA".equals(tipo.toUpperCase()) || "SALIDA".equals(tipo.toUpperCase()))
                            ? tipo.toUpperCase() : "";
        MovPeriodResult res = buildMovPeriodSql(period, de, hasta, tipoFilter);
        List<Map<String,Object>> rows = db.queryForList(res.sql, res.params);

        // FIX: Usar parámetros en vez de concatenación de strings
        List<Map<String,Object>> topMov;
        if (tipoFilter.isBlank()) {
            topMov = db.queryForList(
                "SELECT articulo,COUNT(*) cnt,SUM(cantidad) qty FROM movimientos GROUP BY codigo,articulo ORDER BY cnt DESC LIMIT 10");
        } else {
            topMov = db.queryForList(
                "SELECT articulo,COUNT(*) cnt,SUM(cantidad) qty FROM movimientos WHERE tipo=? GROUP BY codigo,articulo ORDER BY cnt DESC LIMIT 10",
                tipoFilter);
        }
        return ResponseEntity.ok(Map.of("series",rows,"top_articulos",topMov));
    }

    @GetMapping("/api/informes/proveedores")
    public ResponseEntity<?> infProveedores(HttpSession session) {
        if (!auth.isLoggedIn(session)) return ResponseEntity.status(401).body(Map.of("error","No autenticado"));
        int total     = db.queryForObject("SELECT COUNT(*) FROM proveedores WHERE activo=true", Integer.class);
        int inactivos = db.queryForObject("SELECT COUNT(*) FROM proveedores WHERE activo=false", Integer.class);
        List<Map<String,Object>> lista = db.queryForList("SELECT * FROM proveedores ORDER BY nombre");
        return ResponseEntity.ok(Map.of("total",total,"inactivos",inactivos,"lista",lista));
    }

    @GetMapping("/api/informes/clientes")
    public ResponseEntity<?> infClientes(
            @RequestParam(defaultValue="month") String period,
            @RequestParam(defaultValue="")      String de,
            @RequestParam(defaultValue="")      String hasta,
            HttpSession session) {
        if (!auth.isLoggedIn(session)) return ResponseEntity.status(401).body(Map.of("error","No autenticado"));
        int total = db.queryForObject("SELECT COUNT(*) FROM clientes WHERE activo=true", Integer.class);
        List<Map<String,Object>> porTipo = db.queryForList("SELECT tipo, COUNT(*) cnt FROM clientes WHERE activo=true GROUP BY tipo");
        List<Map<String,Object>> lista   = db.queryForList("SELECT * FROM clientes ORDER BY nombre");
        return ResponseEntity.ok(Map.of("total",total,"por_tipo",porTipo,"lista",lista));
    }

    @GetMapping("/api/informes/empleados")
    public ResponseEntity<?> infEmpleados(HttpSession session) {
        if (!auth.isLoggedIn(session)) return ResponseEntity.status(401).body(Map.of("error","No autenticado"));
        int total = db.queryForObject("SELECT COUNT(*) FROM empleados WHERE activo=true", Integer.class);
        List<Map<String,Object>> porDep    = db.queryForList("SELECT departamento, COUNT(*) cnt, SUM(salario) masa FROM empleados WHERE activo=true GROUP BY departamento ORDER BY cnt DESC");
        List<Map<String,Object>> porPuesto = db.queryForList("SELECT puesto, COUNT(*) cnt FROM empleados WHERE activo=true GROUP BY puesto ORDER BY cnt DESC LIMIT 10");
        double masa = db.queryForObject("SELECT COALESCE(SUM(salario),0) FROM empleados WHERE activo=true", Double.class);
        List<Map<String,Object>> lista = db.queryForList("SELECT * FROM empleados ORDER BY nombre");
        return ResponseEntity.ok(Map.of("total",total,"por_departamento",porDep,"por_puesto",porPuesto,"masa_salarial",masa,"lista",lista));
    }

    // ── Clase helper para SQL parametrizado ───────────────────────
    private static class MovPeriodResult {
        String sql;
        Object[] params;
        MovPeriodResult(String sql, Object... params) {
            this.sql = sql;
            this.params = params;
        }
    }

    // FIX: buildMovPeriodSql ahora usa parámetros ? en vez de concatenar strings
    //      Esto elimina el riesgo de SQL injection con el campo tipoFilter
    private MovPeriodResult buildMovPeriodSql(String period, String de, String hasta, String tipoFilter) {
        boolean hasTipo = !tipoFilter.isBlank();
        String tipoWhere = hasTipo ? " AND tipo=?" : "";

        return switch (period) {
            case "week" -> {
                List<Object> p = new ArrayList<>();
                if (hasTipo) p.add(tipoFilter);
                yield new MovPeriodResult(
                    "SELECT TO_CHAR(fecha,'IYYY-IW') periodo," +
                    " SUM(CASE WHEN tipo='ENTRADA' THEN cantidad ELSE 0 END) entradas," +
                    " SUM(CASE WHEN tipo='SALIDA' THEN cantidad ELSE 0 END) salidas" +
                    " FROM movimientos WHERE fecha >= NOW()-INTERVAL '8 weeks'" + tipoWhere +
                    " GROUP BY periodo ORDER BY periodo",
                    p.toArray());
            }
            case "year" -> {
                List<Object> p = new ArrayList<>();
                if (hasTipo) p.add(tipoFilter);
                yield new MovPeriodResult(
                    "SELECT TO_CHAR(fecha,'YYYY') periodo," +
                    " SUM(CASE WHEN tipo='ENTRADA' THEN cantidad ELSE 0 END) entradas," +
                    " SUM(CASE WHEN tipo='SALIDA' THEN cantidad ELSE 0 END) salidas" +
                    " FROM movimientos" + (hasTipo ? " WHERE tipo=?" : "") +
                    " GROUP BY periodo ORDER BY periodo",
                    p.toArray());
            }
            case "range" -> {
                if (!de.isBlank() && !hasta.isBlank()) {
                    List<Object> p = new ArrayList<>(List.of(de, hasta));
                    if (hasTipo) p.add(tipoFilter);
                    yield new MovPeriodResult(
                        "SELECT TO_CHAR(fecha,'YYYY-MM-DD') periodo," +
                        " SUM(CASE WHEN tipo='ENTRADA' THEN cantidad ELSE 0 END) entradas," +
                        " SUM(CASE WHEN tipo='SALIDA' THEN cantidad ELSE 0 END) salidas" +
                        " FROM movimientos WHERE fecha BETWEEN ?::date AND ?::date" + tipoWhere +
                        " GROUP BY periodo ORDER BY periodo",
                        p.toArray());
                } else {
                    yield buildMovPeriodSql("month", de, hasta, tipoFilter);
                }
            }
            default -> {
                List<Object> p = new ArrayList<>();
                if (hasTipo) p.add(tipoFilter);
                yield new MovPeriodResult(
                    "SELECT TO_CHAR(fecha,'YYYY-MM') periodo," +
                    " SUM(CASE WHEN tipo='ENTRADA' THEN cantidad ELSE 0 END) entradas," +
                    " SUM(CASE WHEN tipo='SALIDA' THEN cantidad ELSE 0 END) salidas" +
                    " FROM movimientos WHERE fecha >= NOW()-INTERVAL '12 months'" + tipoWhere +
                    " GROUP BY periodo ORDER BY periodo",
                    p.toArray());
            }
        };
    }

    private String s(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString().trim() : "";
    }
}
