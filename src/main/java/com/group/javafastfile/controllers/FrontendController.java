package com.group.javafastfile.controllers;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FrontendController {
    @GetMapping("/upload")
    public ResponseEntity<Resource> getUploadPage() {
        Resource resource = new ClassPathResource("static/upload.html");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE)
                .body(resource);
    }

    @GetMapping("/uploadRaw")
    public ResponseEntity<Resource> getUploadRawPage() {
        Resource resource = new ClassPathResource("static/uploadRaw.html");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE)
                .body(resource);
    }

    @GetMapping("/raw")
    public ResponseEntity<Resource> getRawPage() {
        Resource resource = new ClassPathResource("static/indexRaw.html");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE)
                .body(resource);
    }
}
