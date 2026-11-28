package com.swiftroute.orderservice.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.dto.response.FileUploadResponse;
import com.swiftroute.orderservice.service.CloudinaryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
@Tag(name = "Uploads", description = "Endpoints for uploading images to Cloudinary (POD, Incident evidence)")
public class FileUploadController {

    private final CloudinaryService cloudinaryService;

    @Operation(summary = "Upload image to Cloudinary (POD, Evidence, Signatures)")
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<FileUploadResponse>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", required = false, defaultValue = "evidence") String folder) {
        FileUploadResponse response = cloudinaryService.uploadImage(file, folder);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

