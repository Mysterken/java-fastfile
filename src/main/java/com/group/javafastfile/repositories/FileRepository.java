package com.group.javafastfile.repositories;

import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class FileRepository {
    private final List<Object> storedFiles = new ArrayList<>();

    public void save(String fileName) {
        storedFiles.add(fileName);
    }
}
