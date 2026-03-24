package com.example.shopapp.controller;

import com.example.shopapp.dto.admin.AdminFileResponse;
import com.example.shopapp.dto.admin.AdminFilesPageResponse;
import com.example.shopapp.service.AdminFileManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/files")
@RequiredArgsConstructor
public class AdminFileManagementController {

    private final AdminFileManagementService fileManagementService;

    /**
     * Get all files with pagination, search, and filter
     * GET /api/admin/files?page=1&size=10&fileName=report&fileType=application/pdf
     */
    @GetMapping
    public ResponseEntity<AdminFilesPageResponse> getAllFiles(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) String fileType) {
        return ResponseEntity.ok(fileManagementService.getAllFiles(page, size, fileName, fileType));
    }

    /**
     * Get file by ID
     * GET /api/admin/files/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<AdminFileResponse> getFileById(@PathVariable Long id) {
        return ResponseEntity.ok(fileManagementService.getFileById(id));
    }

    /**
     * Delete a single file
     * DELETE /api/admin/files/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable Long id) {
        fileManagementService.deleteFile(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Delete multiple files
     * DELETE /api/admin/files?ids=1,2,3
     */
    @DeleteMapping
    public ResponseEntity<Void> deleteFiles(@RequestParam List<Long> ids) {
        fileManagementService.deleteFiles(ids);
        return ResponseEntity.noContent().build();
    }
}
