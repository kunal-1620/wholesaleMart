package org.example.service;

import org.example.domain.StoredFile;
import org.example.repo.StoredFileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

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
import java.net.URI;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class FileStorageService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileStorageService.class);
    private static final String DATABASE_PROVIDER = "database";
    private static final String R2_PROVIDER = "r2";
    private static final String PAYMENTS_FOLDER = "payments";
    private static final int MAX_IMAGE_DIMENSION = 1200;
    private static final float JPEG_QUALITY = 0.78f;
    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final StoredFileRepository storedFiles;
    private final String storageBackend;
    private final String r2PublicBucket;
    private final String r2PrivateBucket;
    private final String r2PublicBaseUrl;
    private final boolean uploadsEnabled;
    private final long storageLimitBytes;
    private final S3Client r2Client;

    public FileStorageService(
            StoredFileRepository storedFiles,
            @Value("${app.storage.backend:database}") String storageBackend,
            @Value("${app.storage.r2.endpoint:}") String r2Endpoint,
            @Value("${app.storage.r2.region:auto}") String r2Region,
            @Value("${app.storage.r2.access-key-id:}") String r2AccessKeyId,
            @Value("${app.storage.r2.secret-access-key:}") String r2SecretAccessKey,
            @Value("${app.storage.r2.public-bucket:}") String r2PublicBucket,
            @Value("${app.storage.r2.private-bucket:}") String r2PrivateBucket,
            @Value("${app.storage.r2.public-base-url:}") String r2PublicBaseUrl,
            @Value("${app.uploads.enabled:true}") boolean uploadsEnabled,
            @Value("${app.storage.limit-mb:8500}") long storageLimitMb
    ) {
        this.storedFiles = storedFiles;
        this.storageBackend = normalized(storageBackend);
        this.r2PublicBucket = blankToNull(r2PublicBucket);
        this.r2PrivateBucket = blankToNull(r2PrivateBucket);
        this.r2PublicBaseUrl = trimTrailingSlash(blankToNull(r2PublicBaseUrl));
        this.uploadsEnabled = uploadsEnabled;
        this.storageLimitBytes = Math.max(0, storageLimitMb) * BYTES_PER_MB;
        this.r2Client = r2Enabled()
                ? buildR2Client(r2Endpoint, r2Region, r2AccessKeyId, r2SecretAccessKey)
                : null;
        if (r2Enabled()) {
            require(this.r2PublicBucket, "R2_PUBLIC_BUCKET is required when APP_STORAGE_BACKEND=r2.");
            require(this.r2PrivateBucket, "R2_PRIVATE_BUCKET is required when APP_STORAGE_BACKEND=r2.");
        }
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
            assertUploadAllowed(upload.data().length, 0);
            return storePrepared(upload, folder, businessId);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not store uploaded file", exception);
        }
    }

    public String replace(MultipartFile file, String existingPath, String folder, Long businessId) {
        if (file == null || file.isEmpty()) {
            return existingPath;
        }
        if (businessId == null) {
            throw new IllegalArgumentException("Business ID is required before storing files.");
        }
        try {
            StoredUpload upload = prepareUpload(file);
            assertUploadAllowed(upload.data().length, existingSizeBytes(existingPath));
            String newPath = storePrepared(upload, folder, businessId);
            deleteByPath(existingPath);
            return newPath;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not store uploaded file", exception);
        }
    }

    public void deleteByPath(String path) {
        String id = storedFileId(path);
        if (id != null) {
            storedFiles.findById(id).ifPresent(file -> {
                deleteExternalObject(file);
                storedFiles.delete(file);
            });
        }
    }

    public Optional<StoredFile> find(String id) {
        return storedFiles.findById(id);
    }

    public byte[] read(StoredFile file) {
        if (isR2File(file)) {
            if (r2Client == null) {
                throw new IllegalStateException("R2 storage is not configured, but this file is stored in R2.");
            }
            String bucket = bucketFor(file);
            ResponseBytes<GetObjectResponse> object = r2Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(file.getObjectKey())
                    .build());
            return object.asByteArray();
        }
        return file.getData() == null ? new byte[0] : file.getData();
    }

    public boolean isR2File(StoredFile file) {
        return file != null && R2_PROVIDER.equalsIgnoreCase(file.getStorageProvider());
    }

    public boolean hasPublicUrl(StoredFile file) {
        return file != null && file.getPublicUrl() != null && !file.getPublicUrl().isBlank();
    }

    public boolean isPrivate(StoredFile file) {
        return file != null && PAYMENTS_FOLDER.equals(file.getFolder());
    }

    public long totalStoredBytes() {
        return storedFiles.totalSizeBytes();
    }

    private String storePrepared(StoredUpload upload, String folder, Long businessId) {
        if (r2Enabled()) {
            return storeInR2(upload, folder, businessId);
        }
        StoredFile storedFile = new StoredFile();
        storedFile.setId(UUID.randomUUID().toString());
        storedFile.setBusinessId(businessId);
        storedFile.setFolder(folder);
        storedFile.setFilename(folder + "-" + UUID.randomUUID() + upload.extension());
        storedFile.setContentType(upload.contentType());
        storedFile.setStorageProvider(DATABASE_PROVIDER);
        storedFile.setSizeBytes(upload.data().length);
        storedFile.setData(upload.data());
        storedFiles.save(storedFile);
        return "/files/" + storedFile.getId();
    }

    private String storeInR2(StoredUpload upload, String folder, Long businessId) {
        boolean privateFile = PAYMENTS_FOLDER.equals(folder);
        String bucket = privateFile ? r2PrivateBucket : r2PublicBucket;
        String id = UUID.randomUUID().toString();
        String filename = folder + "-" + UUID.randomUUID() + upload.extension();
        String key = objectKey(businessId, folder, id, upload.extension());
        r2Client.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(upload.contentType())
                        .contentLength((long) upload.data().length)
                        .cacheControl(privateFile ? "private, no-store" : "public, max-age=2592000")
                        .build(),
                RequestBody.fromBytes(upload.data()));

        StoredFile storedFile = new StoredFile();
        storedFile.setId(id);
        storedFile.setBusinessId(businessId);
        storedFile.setFolder(folder);
        storedFile.setFilename(filename);
        storedFile.setContentType(upload.contentType());
        storedFile.setStorageProvider(R2_PROVIDER);
        storedFile.setSizeBytes(upload.data().length);
        storedFile.setObjectKey(key);
        if (!privateFile && r2PublicBaseUrl != null) {
            storedFile.setPublicUrl(r2PublicBaseUrl + "/" + key);
        }
        storedFiles.save(storedFile);
        return "/files/" + storedFile.getId();
    }

    private void deleteExternalObject(StoredFile file) {
        if (!isR2File(file) || file.getObjectKey() == null || file.getObjectKey().isBlank()) {
            return;
        }
        if (r2Client == null) {
            LOGGER.warn("Could not delete R2 object {} for stored file {} because R2 storage is not configured.", file.getObjectKey(), file.getId());
            return;
        }
        try {
            r2Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketFor(file))
                    .key(file.getObjectKey())
                    .build());
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not delete R2 object {} for stored file {}", file.getObjectKey(), file.getId(), exception);
        }
    }

    private S3Client buildR2Client(String endpoint, String region, String accessKeyId, String secretAccessKey) {
        require(endpoint, "R2_ENDPOINT is required when APP_STORAGE_BACKEND=r2.");
        require(accessKeyId, "R2_ACCESS_KEY_ID is required when APP_STORAGE_BACKEND=r2.");
        require(secretAccessKey, "R2_SECRET_ACCESS_KEY is required when APP_STORAGE_BACKEND=r2.");
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint.trim()))
                .region(Region.of(blankToDefault(region, "auto")))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId.trim(), secretAccessKey.trim())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    private String bucketFor(StoredFile file) {
        return isPrivate(file) ? r2PrivateBucket : r2PublicBucket;
    }

    private String objectKey(Long businessId, String folder, String id, String extension) {
        return "businesses/" + businessId + "/" + safeKeySegment(folder) + "/" + id + extension;
    }

    private void assertUploadAllowed(long uploadBytes, long replaceableBytes) {
        if (!uploadsEnabled) {
            throw new IllegalStateException("Uploads are currently disabled. Set APP_UPLOADS_ENABLED=true to allow new uploads.");
        }
        if (storageLimitBytes <= 0) {
            throw new IllegalStateException("Uploads are blocked because APP_STORAGE_LIMIT_MB is 0.");
        }
        long currentBytes = totalStoredBytes();
        long effectiveBytes = Math.max(0, currentBytes - Math.max(0, replaceableBytes)) + uploadBytes;
        if (effectiveBytes > storageLimitBytes) {
            throw new IllegalStateException("Storage limit reached. Current usage is "
                    + humanBytes(currentBytes)
                    + " of "
                    + humanBytes(storageLimitBytes)
                    + "; this upload needs "
                    + humanBytes(uploadBytes)
                    + ". Delete old images or increase APP_STORAGE_LIMIT_MB.");
        }
    }

    private long existingSizeBytes(String path) {
        String id = storedFileId(path);
        if (id == null) {
            return 0;
        }
        return storedFiles.findById(id)
                .map(StoredFile::getSizeBytes)
                .orElse(0L);
    }

    private String humanBytes(long bytes) {
        double mb = (double) bytes / BYTES_PER_MB;
        DecimalFormat format = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US));
        return format.format(mb) + " MB";
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

    private boolean r2Enabled() {
        return R2_PROVIDER.equalsIgnoreCase(storageBackend);
    }

    private String safeKeySegment(String value) {
        String segment = value == null ? "files" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-");
        return segment.isBlank() ? "files" : segment;
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private void require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
    }

    private record StoredUpload(byte[] data, String contentType, String extension) {
    }
}
