package com.swiftroute.orderservice.service;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

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
     * Evidence uploads fail closed when durable storage is unavailable. Returning a
     * demo URL would create a false proof-of-delivery record.
     */
    public FileUploadResponse uploadImage(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_EVIDENCE_FILE);
        }

        String contentType = file.getContentType();
        if (file.getSize() > 10L * 1024 * 1024
                || contentType == null
                || !(contentType.equalsIgnoreCase("image/jpeg")
                    || contentType.equalsIgnoreCase("image/png")
                    || contentType.equalsIgnoreCase("image/webp"))) {
            throw new AppException(ErrorCode.INVALID_EVIDENCE_FILE);
        }

        String targetFolder = (folder != null && !folder.isBlank())
                ? "swiftroute/" + folder.trim()
                : "swiftroute/evidence";

        if (cloudinary == null) {
            log.error("Evidence upload rejected because Cloudinary is not configured");
            throw new AppException(ErrorCode.EVIDENCE_STORAGE_UNAVAILABLE);
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

            if ((secureUrl == null || secureUrl.isBlank()) || publicId == null || publicId.isBlank()) {
                log.error("Cloudinary returned an incomplete evidence upload result");
                throw new AppException(ErrorCode.EVIDENCE_STORAGE_UNAVAILABLE);
            }

            log.info("Uploaded image to Cloudinary: publicId={}, url={}", publicId, secureUrl);

            return new FileUploadResponse(
                    secureUrl,
                    publicId,
                    format != null ? format : "jpg",
                    bytes != null ? bytes.longValue() : file.getSize(),
                    file.getOriginalFilename(),
                    Instant.now()
            );
        } catch (IOException e) {
            log.error("Failed to upload image to Cloudinary", e);
            throw new AppException(ErrorCode.EVIDENCE_STORAGE_UNAVAILABLE);
        }
    }
}
