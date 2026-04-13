package com.diprogra.controller;

import com.diprogra.config.AppConfig;
import com.diprogra.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpSession;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/backup")
public class BackupController {

    @Autowired private JdbcTemplate db;
    @Autowired private AuthService  auth;
    @Autowired private AppConfig    cfg;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> TABLAS = List.of(
        "usuarios", "articulos", "movimientos",
        "proveedores", "clientes", "empleados",
        "cotizaciones", "config", "bitacora"
    );

    // ── GET /api/backup/info ─────────────────────────────────────
    @GetMapping("/info")
    public ResponseEntity<?> info(HttpSession session) {
        if (!auth.isAdmin(session))
            return ResponseEntity.status(403).body(Map.of("error","Sin permiso"));

        Path dir = cfg.getBackupsPath();
        String lastBackup = null;
        List<Map<String,Object>> historial = new ArrayList<>();

        try {
            if (Files.exists(dir)) {
                List<Path> files = Files.list(dir)
                    .filter(p -> p.toString().endsWith(".db"))
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());

                for (Path f : files) {
                    Map<String,Object> entry = new LinkedHashMap<>();
                    entry.put("nombre", f.getFileName().toString());
                    entry.put("fecha",  Files.getLastModifiedTime(f).toString());
                    entry.put("tamano", Files.size(f));
                    historial.add(entry);
                }
                if (!files.isEmpty()) {
                    lastBackup = files.get(0).getFileName().toString();
                }
            }
        } catch (IOException ignored) {}

        return ResponseEntity.ok(Map.of(
            "backup_dir",   dir.toString(),
            "db_path",      dir.toString(),
            "last_backup",  lastBackup != null ? lastBackup : "",
            "historial",    historial
        ));
    }

    // ── GET /api/backup/download ─────────────────────────────────
    @GetMapping("/download")
    public ResponseEntity<?> download(
            @RequestParam(defaultValue="1") int num,
            HttpSession session) throws IOException {

        if (!auth.isAdmin(session))
            return ResponseEntity.status(403).body(Map.of("error","Sin permiso"));

        // Generar respaldo en memoria
        byte[] data = generarBackup();
        String ts   = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fname = "respaldo" + num + "_" + ts + ".db";

        // Guardar en carpeta de respaldos
        Path dest = cfg.getBackupsPath().resolve(fname);
        Files.write(dest, data);

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fname + "\"")
            .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
            .body(data);
    }

    // ── POST /api/backup/restore ─────────────────────────────────
    @PostMapping("/restore")
    public ResponseEntity<?> restore(
            @RequestParam("archivo") MultipartFile archivo,
            HttpSession session) throws IOException {

        if (!auth.isAdmin(session))
            return ResponseEntity.status(403).body(Map.of("error","Sin permiso"));

        if (archivo.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error","Archivo vacío"));

        // 1. Crear respaldo de seguridad antes de restaurar
        try {
            byte[] seguridad = generarBackup();
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Files.write(cfg.getBackupsPath().resolve("seguridad_prerestaurar_" + ts + ".db"), seguridad);
        } catch (Exception e) {
            // No bloquear la restauración si el backup de seguridad falla
        }

        // 2. Leer y parsear el archivo .db (JSON)
        byte[] bytes = archivo.getBytes();
        Map<String, List<Map<String,Object>>> backup;
        try {
            backup = JSON.readValue(bytes, JSON.getTypeFactory()
                .constructMapType(LinkedHashMap.class,
                    JSON.getTypeFactory().constructType(String.class),
                    JSON.getTypeFactory().constructCollectionType(List.class, Map.class)));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error","Archivo inválido. Asegúrate de usar un respaldo generado por este sistema."));
        }

        // 3. Restaurar cada tabla respetando dependencias
        List<String> ordenRestaurar = List.of(
            "config", "usuarios", "articulos",
            "movimientos", "proveedores", "clientes",
            "empleados", "cotizaciones", "bitacora"
        );

        try {
            // Borrar en orden inverso para respetar FK
            db.execute("SET session_replication_role = replica"); // deshabilita FK checks en PG
            for (int i = ordenRestaurar.size() - 1; i >= 0; i--) {
                String tabla = ordenRestaurar.get(i);
                if (backup.containsKey(tabla)) {
                    try { db.execute("DELETE FROM " + tabla); } catch (Exception ignored) {}
                }
            }

            // Insertar datos
            for (String tabla : ordenRestaurar) {
                List<Map<String,Object>> filas = backup.get(tabla);
                if (filas == null || filas.isEmpty()) continue;
                for (Map<String,Object> fila : filas) {
                    try {
                        List<String> cols = new ArrayList<>(fila.keySet());
                        String sql = "INSERT INTO " + tabla +
                            " (" + String.join(",", cols) + ") VALUES (" +
                            cols.stream().map(c -> "?").collect(Collectors.joining(",")) + ")";
                        db.update(sql, fila.values().toArray());
                    } catch (Exception ignored) {}
                }
            }
            db.execute("SET session_replication_role = DEFAULT");
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body(Map.of("error","Error al restaurar: " + e.getMessage()));
        }

        return ResponseEntity.ok(Map.of(
            "ok", true,
            "mensaje", "Base de datos restaurada correctamente desde " + archivo.getOriginalFilename()
        ));
    }

    // ── Utilidad: generar backup JSON ────────────────────────────
    private byte[] generarBackup() throws IOException {
        Map<String, Object> backup = new LinkedHashMap<>();
        backup.put("version",   "2.1");
        backup.put("generado",  LocalDateTime.now().toString());
        backup.put("sistema",   "DIPROGRA");

        for (String tabla : TABLAS) {
            try {
                List<Map<String, Object>> filas = db.queryForList("SELECT * FROM " + tabla);
                backup.put(tabla, filas);
            } catch (Exception e) {
                backup.put(tabla, List.of());
            }
        }
        return JSON.writeValueAsBytes(backup);
    }
}
