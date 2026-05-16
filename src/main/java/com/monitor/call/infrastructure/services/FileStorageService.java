package com.monitor.call.infrastructure.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);

    @Value("${app.uploads.dir:/var/uploads}")
    private String uploadsDir;

    /**
     * Almacena el archivo en {uploadsDir}/payments/{submissionId}/{uuid}.{ext}
     * @return la ruta relativa desde uploadsDir para guardar en BD
     */
    public String storePaymentFile(MultipartFile file, Long submissionId) {
        try {
            String originalFilename = file.getOriginalFilename() != null
                    ? file.getOriginalFilename() : "file";
            String extension = "";
            int dotIdx = originalFilename.lastIndexOf('.');
            if (dotIdx >= 0) extension = originalFilename.substring(dotIdx);

            String storedFilename = UUID.randomUUID() + extension;
            Path dir = Paths.get(uploadsDir, "payments", String.valueOf(submissionId));
            Files.createDirectories(dir);

            Path target = dir.resolve(storedFilename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            String relativePath = "payments/" + submissionId + "/" + storedFilename;
            logger.info("Archivo almacenado: {}", relativePath);
            return relativePath;

        } catch (IOException e) {
            throw new RuntimeException("Error al almacenar el comprobante: " + e.getMessage(), e);
        }
    }

    /**
     * Carga un archivo a partir de la ruta relativa guardada en BD.
     */
    public Resource loadFile(String relativePath) {
        try {
            Path filePath = Paths.get(uploadsDir).resolve(relativePath).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new RuntimeException("Archivo no encontrado o no legible: " + relativePath);
        } catch (Exception e) {
            throw new RuntimeException("Error al cargar el archivo: " + e.getMessage(), e);
        }
    }
}
