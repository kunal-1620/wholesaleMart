package org.example.controller;

import jakarta.servlet.http.HttpSession;
import org.example.domain.CustomerOrder;
import org.example.domain.Role;
import org.example.domain.StoredFile;
import org.example.repo.CustomerOrderRepository;
import org.example.repo.StoredFileRepository;
import org.example.session.CurrentUser;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class FileController {
    private final StoredFileRepository storedFiles;
    private final CustomerOrderRepository orders;

    public FileController(StoredFileRepository storedFiles, CustomerOrderRepository orders) {
        this.storedFiles = storedFiles;
        this.orders = orders;
    }

    @GetMapping("/files/{id}")
    public ResponseEntity<byte[]> file(@PathVariable String id, HttpSession session) {
        CurrentUser currentUser = (CurrentUser) session.getAttribute("currentUser");
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }
        StoredFile file = storedFiles.findById(id).orElse(null);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canView(currentUser, file)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok()
                .contentType(mediaType(file.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getFilename().replace("\"", "") + "\"")
                .cacheControl(CacheControl.noStore())
                .body(file.getData());
    }

    private boolean canView(CurrentUser currentUser, StoredFile file) {
        if (currentUser.role() == Role.PLATFORM_ADMIN) {
            return true;
        }
        if (!file.getBusinessId().equals(currentUser.businessId())) {
            return false;
        }
        if (!"payments".equals(file.getFolder())) {
            return true;
        }
        if (currentUser.role() == Role.BUSINESS_ADMIN || currentUser.role() == Role.STAFF) {
            return true;
        }
        return orders.findByPaymentProofPath("/files/" + file.getId())
                .map(CustomerOrder::getCustomer)
                .map(customer -> customer.getId().equals(currentUser.userId()))
                .orElse(false);
    }

    private MediaType mediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
