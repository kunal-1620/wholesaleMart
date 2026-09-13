package org.example.service;

import org.example.domain.StoredFile;
import org.example.repo.StoredFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileStorageService {
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
            String original = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
            String extension = "";
            int dot = original.lastIndexOf('.');
            if (dot >= 0) {
                extension = original.substring(dot).toLowerCase(Locale.ROOT);
            }
            StoredFile storedFile = new StoredFile();
            storedFile.setId(UUID.randomUUID().toString());
            storedFile.setBusinessId(businessId);
            storedFile.setFolder(folder);
            storedFile.setFilename(folder + "-" + UUID.randomUUID() + extension);
            storedFile.setContentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
            storedFile.setData(file.getBytes());
            storedFiles.save(storedFile);
            return "/files/" + storedFile.getId();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not store uploaded file", exception);
        }
    }
}
