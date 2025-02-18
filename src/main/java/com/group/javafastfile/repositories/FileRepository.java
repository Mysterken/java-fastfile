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
import java.util.stream.Collectors;

@Repository
public class FileRepository {

    private static final String CHUNK_DIR = "chunks/";
    private static final String INDEX_FILE = "chunks/index.json";

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

    public Map<String, String> loadIndex() {
        Path indexPath = Paths.get(INDEX_FILE);
        if (!Files.exists(indexPath)) {
            return new HashMap<>();
        }

        try (BufferedReader reader = Files.newBufferedReader(indexPath)) {
            return new HashMap<>(new ObjectMapper().readValue(reader, Map.class));
        } catch (IOException e) {
            return new HashMap<>();
        }
    }

    public void saveIndex(Map<String, String> index) throws IOException {
        Path indexPath = Paths.get(INDEX_FILE);
        Files.write(indexPath, new ObjectMapper().writeValueAsBytes(index));
    }

    public List<String> listChunks() {
        try {
            return Files.list(Paths.get(CHUNK_DIR))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("Could not list chunks", e);
        }
    }
}
