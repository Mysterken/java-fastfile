package com.group.javafastfile.controllers;

import com.group.javafastfile.services.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
public class FileController {

    @Autowired
    private FileService fileService;

    @PostMapping("/sendFile")
    public String handleFileUpload(@RequestParam("file") MultipartFile file) {
        return fileService.storeFile(file);
    }

    @PostMapping("/sendFileRaw")
    public String handleRawFileUpload(@RequestParam("file") MultipartFile file) {
        return fileService.storeFileRaw(file);
    }


    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        Resource file = fileService.loadFile(filename);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(file);
    }

    @GetMapping("/listFiles")
    public List<String> listFiles() {
        return fileService.listFiles();
    }

    @GetMapping("/listFilesRaw")
    public List<String> listFilesRaw() {
        return fileService.listFilesRaw();
    }

    @GetMapping("/downloadRaw/{filename}")
    public ResponseEntity<Resource> downloadRawFile(@PathVariable String filename) {
        Resource file = fileService.loadRawFile(filename);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(file);
    }

}
