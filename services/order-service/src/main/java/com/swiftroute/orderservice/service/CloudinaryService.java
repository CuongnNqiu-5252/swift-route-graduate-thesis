package com.swiftroute.orderservice.service;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.swiftroute.orderservice.dto.response.FileUploadResponse;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CloudinaryService {

    private final Cloudinary cloudinary;

    @Autowired
    public CloudinaryService(@Autowired(required = false) Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    /**
     * Upload an image to Cloudinary in folder 'swiftroute/evidence'.
     * If Cloudinary keys are not yet provided by the user, returns a safe demo URL
     * so workflows (POD, Incident evidence) continue working without interruption.
     */
    public FileUploadResponse uploadImage(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        String targetFolder = (folder != null && !folder.isBlank())
                ? "swiftroute/" + folder.trim()
                : "swiftroute/evidence";

        if (cloudinary == null) {
            String fakePublicId = "mock_" + UUID.randomUUID().toString().substring(0, 10);
            String placeholderUrl = "https://images.unsplash.com/photo-1586528116311-ad8dd3c8310d?auto=format&fit=crop&w=800&q=80";
            log.info("Cloudinary is not configured yet (CLOUDINARY_CLOUD_NAME/API_KEY/API_SECRET missing). " +
                    "Returning fallback demo URL for file '{}': {}", file.getOriginalFilename(), placeholderUrl);
            return new FileUploadResponse(
                    placeholderUrl,
                    fakePublicId,
                    "jpg",
                    file.getSize(),
                    file.getOriginalFilename(),
                    Instant.now()
            );
        }

        try {
            Map<?, ?> uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", targetFolder,
                            "resource_type", "image"
                    )
            );

            String secureUrl = (String) uploadResult.get("secure_url");
            String publicId = (String) uploadResult.get("public_id");
            String format = (String) uploadResult.get("format");
            Number bytes = (Number) uploadResult.get("bytes");

            log.info("Uploaded image to Cloudinary: publicId={}, url={}", publicId, secureUrl);

            return new FileUploadResponse(
                    secureUrl != null ? secureUrl : (String) uploadResult.get("url"),
                    publicId,
                    format != null ? format : "jpg",
                    bytes != null ? bytes.longValue() : file.getSize(),
                    file.getOriginalFilename(),
                    Instant.now()
            );
        } catch (IOException e) {
            log.error("Failed to upload image to Cloudinary", e);
            throw new RuntimeException("Image upload failed: " + e.getMessage(), e);
        }
    }
}

