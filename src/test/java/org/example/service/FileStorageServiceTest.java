package org.example.service;

import org.example.domain.StoredFile;
import org.example.repo.StoredFileRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileStorageServiceTest {
    private static final long ONE_MB = 1024L * 1024L;

    @Test
    void blocksUploadWhenStorageLimitWouldBeExceeded() {
        StoredFileRepository storedFiles = mock(StoredFileRepository.class);
        when(storedFiles.totalSizeBytes()).thenReturn(ONE_MB - 100);

        FileStorageService service = databaseStorage(storedFiles, true, 1);
        MockMultipartFile upload = new MockMultipartFile("file", "proof.txt", "text/plain", new byte[101]);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.store(upload, "payments", 1L));

        assertTrue(exception.getMessage().contains("Storage limit reached"));
    }

    @Test
    void allowsReplacementWhenExistingFileBytesAreFreed() {
        StoredFile existing = new StoredFile();
        existing.setId("existing");
        existing.setBusinessId(1L);
        existing.setFolder("products");
        existing.setFilename("old.txt");
        existing.setContentType("text/plain");
        existing.setStorageProvider("database");
        existing.setSizeBytes(512 * 1024);

        StoredFileRepository storedFiles = mock(StoredFileRepository.class);
        when(storedFiles.totalSizeBytes()).thenReturn(ONE_MB);
        when(storedFiles.findById("existing")).thenReturn(Optional.of(existing));
        when(storedFiles.save(any(StoredFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FileStorageService service = databaseStorage(storedFiles, true, 1);
        MockMultipartFile replacement = new MockMultipartFile("file", "new.txt", "text/plain", new byte[512 * 1024]);

        String path = service.replace(replacement, "/files/existing", "products", 1L);

        assertTrue(path.startsWith("/files/"));
        ArgumentCaptor<StoredFile> saved = ArgumentCaptor.forClass(StoredFile.class);
        verify(storedFiles).save(saved.capture());
        assertEquals(512 * 1024, saved.getValue().getSizeBytes());
    }

    @Test
    void blocksUploadsWhenSwitchIsDisabled() {
        StoredFileRepository storedFiles = mock(StoredFileRepository.class);
        FileStorageService service = databaseStorage(storedFiles, false, 8500);
        MockMultipartFile upload = new MockMultipartFile("file", "proof.txt", "text/plain", new byte[1]);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.store(upload, "payments", 1L));

        assertTrue(exception.getMessage().contains("Uploads are currently disabled"));
    }

    private FileStorageService databaseStorage(StoredFileRepository storedFiles, boolean uploadsEnabled, long limitMb) {
        return new FileStorageService(
                storedFiles,
                "database",
                "",
                "auto",
                "",
                "",
                "",
                "",
                "",
                uploadsEnabled,
                limitMb
        );
    }
}
