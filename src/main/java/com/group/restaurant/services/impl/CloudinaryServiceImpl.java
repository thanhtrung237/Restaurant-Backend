package com.group.restaurant.services.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.group.restaurant.exceptions.InvalidParamException;
import com.group.restaurant.services.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudinaryServiceImpl implements CloudinaryService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final String[] ALLOWED_TYPES = {"image/jpeg", "image/png", "image/webp", "image/gif"};

    private final Cloudinary cloudinary;

    @Override
    public String uploadImage(MultipartFile file, String folder) {
        validateFile(file);
        try {
            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", "booking-service/" + folder,
                    "resource_type", "image",
                    "transformation", "q_auto,f_auto,w_1200,c_limit"
            ));
            return (String) result.get("secure_url");
        } catch (IOException e) {
            throw new InvalidParamException("Không thể upload ảnh: " + e.getMessage());
        }
    }

    @Override
    public void deleteImage(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (IOException e) {
            throw new InvalidParamException("Không thể xóa ảnh: " + e.getMessage());
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidParamException("File không được để trống");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidParamException("File không được vượt quá 5MB");
        }
        String contentType = file.getContentType();
        boolean valid = false;
        for (String type : ALLOWED_TYPES) {
            if (type.equals(contentType)) {
                valid = true;
                break;
            }
        }
        if (!valid) {
            throw new InvalidParamException("Chỉ chấp nhận file ảnh (JPEG, PNG, WebP, GIF)");
        }
    }
}
