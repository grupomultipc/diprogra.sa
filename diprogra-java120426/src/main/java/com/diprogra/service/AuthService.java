package com.diprogra.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class AuthService {

    @Autowired private JdbcTemplate db;

    // ── Hash SHA-256 ────────────────────────────────────────────
    public String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 no disponible", e);
        }
    }

    // ── Login ───────────────────────────────────────────────────
    public Map<String, Object> login(String username, String password, HttpSession session, String ip) {
        List<Map<String, Object>> rows = db.queryForList(
            "SELECT * FROM usuarios WHERE username = ? AND activo = true",
            username
        );
        if (rows.isEmpty()) return null;
        Map<String, Object> u = rows.get(0);
        if (!u.get("password").toString().equals(hashPassword(password))) return null;

        session.setAttribute("user",   u.get("username").toString());
        session.setAttribute("nombre", u.get("nombre").toString());
        session.setAttribute("rol",    u.get("rol").toString());
        session.setAttribute("uid",    u.get("id").toString());

        logBit("LOGIN", "auth", "Ingreso: " + u.get("nombre") + " (" + u.get("rol") + ")",
               "", username, ip);

        Map<String, Object> result = new HashMap<>();
        result.put("ok",     true);
        result.put("rol",    u.get("rol"));
        result.put("nombre", u.get("nombre"));
        return result;
    }

    // ── Logout ──────────────────────────────────────────────────
    public void logout(HttpSession session, String ip) {
        String user = sessionUser(session);
        logBit("LOGOUT", "auth", "Cierre de sesión", "", user, ip);
        session.invalidate();
    }

    // ── Helpers de sesión ───────────────────────────────────────
    public String sessionUser(HttpSession session) {
        Object u = session.getAttribute("user");
        return u != null ? u.toString() : "sistema";
    }

    public String sessionRol(HttpSession session) {
        Object r = session.getAttribute("rol");
        return r != null ? r.toString() : "";
    }

    public boolean isLoggedIn(HttpSession session) {
        return session.getAttribute("user") != null;
    }

    public boolean canEdit(HttpSession session) {
        String rol = sessionRol(session);
        return "administrador".equals(rol) || "editor".equals(rol);
    }

    public boolean isAdmin(HttpSession session) {
        return "administrador".equals(sessionRol(session));
    }

    // ── Bitácora ────────────────────────────────────────────────
    public void logBit(String accion, String modulo, String descripcion,
                       String datoId, String usuario, String ip) {
        try {
            db.update(
                "INSERT INTO bitacora (usuario, accion, modulo, descripcion, dato_id, ip) VALUES (?,?,?,?,?,?)",
                usuario, accion, modulo, descripcion, datoId, ip
            );
        } catch (Exception e) {
            // Nunca dejar que el log rompa la operación principal
        }
    }

    public void logBit(String accion, String modulo, String descripcion,
                       String datoId, HttpSession session, HttpServletRequest request) {
        logBit(accion, modulo, descripcion, datoId,
               sessionUser(session), request.getRemoteAddr());
    }
}
