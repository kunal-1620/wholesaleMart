package org.example.service;

import org.example.domain.StoredFile;
import org.example.repo.StoredFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileStorageService {
    private static final int MAX_IMAGE_DIMENSION = 1200;
    private static final float JPEG_QUALITY = 0.78f;

    private final StoredFileRepository storedFiles;

    public FileStorageService(StoredFileRepository storedFiles) {
        this.storedFiles = storedFiles;
    }

    public String store(MultipartFile file, String folder, Long businessId) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (businessId == null) {
            throw new IllegalArgumentException("Business ID is required before storing files.");
        }
        try {
            StoredUpload upload = prepareUpload(file);
            StoredFile storedFile = new StoredFile();
            storedFile.setId(UUID.randomUUID().toString());
            storedFile.setBusinessId(businessId);
            storedFile.setFolder(folder);
            storedFile.setFilename(folder + "-" + UUID.randomUUID() + upload.extension());
            storedFile.setContentType(upload.contentType());
            storedFile.setData(upload.data());
            storedFiles.save(storedFile);
            return "/files/" + storedFile.getId();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not store uploaded file", exception);
        }
    }

    public String replace(MultipartFile file, String existingPath, String folder, Long businessId) {
        String newPath = store(file, folder, businessId);
        if (newPath != null) {
            deleteByPath(existingPath);
            return newPath;
        }
        return existingPath;
    }

    public void deleteByPath(String path) {
        String id = storedFileId(path);
        if (id != null) {
            storedFiles.deleteById(id);
        }
    }

    private StoredUpload prepareUpload(MultipartFile file) throws IOException {
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        if (contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            StoredUpload optimizedImage = optimizeImage(file);
            if (optimizedImage != null) {
                return optimizedImage;
            }
        }
        String original = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        String extension = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) {
            extension = original.substring(dot).toLowerCase(Locale.ROOT);
        }
        return new StoredUpload(file.getBytes(), contentType, extension);
    }

    private StoredUpload optimizeImage(MultipartFile file) throws IOException {
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(file.getBytes()));
        if (original == null) {
            return null;
        }
        int width = original.getWidth();
        int height = original.getHeight();
        double scale = Math.min(1.0, (double) MAX_IMAGE_DIMENSION / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage output = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            return null;
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ImageOutputStream imageOutput = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(imageOutput);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(output, null, null), params);
            return new StoredUpload(bytes.toByteArray(), "image/jpeg", ".jpg");
        } finally {
            writer.dispose();
        }
    }

    private String storedFileId(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String prefix = "/files/";
        if (!path.startsWith(prefix)) {
            return null;
        }
        String id = path.substring(prefix.length()).trim();
        return id.isBlank() ? null : id;
    }

    private record StoredUpload(byte[] data, String contentType, String extension) {
    }
}
