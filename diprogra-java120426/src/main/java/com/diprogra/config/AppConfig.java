package com.diprogra.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import java.io.IOException;
import java.nio.file.*;

@Configuration
public class AppConfig {

    // FIX: Rutas relativas en vez de C:/ — funcionan en Windows, Linux y Mac
    @Value("${diprogra.uploads-dir:./datos-diprogra/imagenes}")
    private String uploadsDir;

    @Value("${diprogra.backups-dir:./datos-diprogra/respaldos}")
    private String backupsDir;

    @PostConstruct
    public void init() throws IOException {
        // FIX: Usar toAbsolutePath() para evitar ambigüedad de rutas relativas
        Path uploads = Paths.get(uploadsDir).toAbsolutePath().normalize();
        Path backups = Paths.get(backupsDir).toAbsolutePath().normalize();

        Files.createDirectories(uploads);
        Files.createDirectories(backups);

        System.out.println("[DIPROGRA] Carpeta imágenes : " + uploads);
        System.out.println("[DIPROGRA] Carpeta respaldos: " + backups);
    }

    public Path getUploadsPath() {
        return Paths.get(uploadsDir).toAbsolutePath().normalize();
    }

    public Path getBackupsPath() {
        return Paths.get(backupsDir).toAbsolutePath().normalize();
    }
}
