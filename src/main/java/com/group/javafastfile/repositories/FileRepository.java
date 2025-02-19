package com.group.javafastfile.repositories;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class FileRepository {
    private static final String CHUNK_DIR = "chunks/";
    private static final String FILE_INDEX = "chunks/file_index.json";

    public void saveChunk(String hash, byte[] chunk) throws IOException {
        Path chunkPath = Paths.get(CHUNK_DIR, hash);
        if (!Files.exists(chunkPath.getParent())) {
            Files.createDirectories(chunkPath.getParent());
        }
        Files.write(chunkPath, chunk);
    }

    public boolean exists(String hash) {
        return Files.exists(Paths.get(CHUNK_DIR, hash));
    }

    public Map<String, List<String>> loadFileIndex() {
        Path fileIndexPath = Paths.get(FILE_INDEX);
        if (!Files.exists(fileIndexPath)) {
            return new HashMap<>();
        }

        try (BufferedReader reader = Files.newBufferedReader(fileIndexPath)) {
            return new ObjectMapper().readValue(reader, Map.class);
        } catch (IOException e) {
            return new HashMap<>();
        }
    }

    public void saveFileIndex(Map<String, List<String>> fileIndex) throws IOException {
        Path fileIndexPath = Paths.get(FILE_INDEX);
        Files.write(fileIndexPath, new ObjectMapper().writeValueAsBytes(fileIndex));
    }
}
