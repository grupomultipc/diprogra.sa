package com.diprogra.controller;

import com.diprogra.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

@Controller
public class AuthController {

    @Autowired private AuthService authService;

    // ── Páginas HTML ────────────────────────────────────────────
    @GetMapping("/login")
    public String loginPage(HttpSession session) {
        if (authService.isLoggedIn(session)) return "redirect:/";
        return "login";
    }

    @GetMapping("/")
    public String index(HttpSession session) {
        if (!authService.isLoggedIn(session)) return "redirect:/login";
        return "index";
    }

    // ── API: Login ──────────────────────────────────────────────
    @PostMapping("/api/login")
    @ResponseBody
    public ResponseEntity<?> doLogin(@RequestBody Map<String, String> body,
                                     HttpSession session,
                                     HttpServletRequest request) {
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");
        Map<String, Object> result = authService.login(username, password, session, request.getRemoteAddr());
        if (result == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuario o contraseña incorrectos"));
        }
        return ResponseEntity.ok(result);
    }

    // ── API: Logout ─────────────────────────────────────────────
    @PostMapping("/api/logout")
    @ResponseBody
    public ResponseEntity<?> doLogout(HttpSession session, HttpServletRequest request) {
        authService.logout(session, request.getRemoteAddr());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── API: Info sesión ─────────────────────────────────────────
    @GetMapping("/api/me")
    @ResponseBody
    public ResponseEntity<?> me(HttpSession session) {
        if (!authService.isLoggedIn(session))
            return ResponseEntity.status(401).body(Map.of("error", "No autenticado"));
        return ResponseEntity.ok(Map.of(
            "user",   session.getAttribute("user"),
            "nombre", session.getAttribute("nombre"),
            "rol",    session.getAttribute("rol")
        ));
    }
}
