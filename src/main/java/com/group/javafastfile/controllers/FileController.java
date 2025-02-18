package com.group.javafastfile.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class FileController {

    @GetMapping("/upload")
    public String uploadFile() {
        return "File uploaded!";
    }
}
