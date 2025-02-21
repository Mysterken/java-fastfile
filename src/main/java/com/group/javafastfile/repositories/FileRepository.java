package com.group.javafastfile.repositories;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4FastDecompressor;

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
    public static final String CHUNK_DIR = "chunks/";
    private static final String FILE_INDEX = "chunks/file_index.json";

    public static final String UPLOAD_DIR = "uploads/";
    private static final String FILE_LIST = "uploads/file_list.json";

    private final LZ4Factory lz4Factory = LZ4Factory.fastestInstance();
    private final LZ4Compressor compressor = lz4Factory.fastCompressor();
    private final LZ4FastDecompressor decompressor = lz4Factory.fastDecompressor();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void saveChunk(String hash, byte[] chunk) throws IOException {
        Path chunkPath = Paths.get(CHUNK_DIR, hash);
        if (!Files.exists(chunkPath.getParent())) {
            Files.createDirectories(chunkPath.getParent());
        }

        byte[] compressedChunk = compressor.compress(chunk);
        Files.write(chunkPath, compressedChunk);

        if (!fingerprintExists(hash)) {
            jdbcTemplate.update("INSERT INTO chunk_fingerprints (fingerprint, original_size) VALUES (?, ?)", hash, chunk.length);
        }
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

    public boolean fingerprintExists(String fingerprint) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chunk_fingerprints WHERE fingerprint = ?", Integer.class, fingerprint);
        return count != null && count > 0;
    }

    public byte[] decompressChunk(byte[] compressedData, int originalSize) {
        return decompressor.decompress(compressedData, originalSize);
    }

    public int determineOriginalSize(String hash) {
        return jdbcTemplate.queryForObject(
                "SELECT original_size FROM chunk_fingerprints WHERE fingerprint = ?",
                Integer.class, hash
        );
    }

    public int determineOriginalSizeRaw(String filename) {
        try {
            Path fileIndexPath = Paths.get(FILE_LIST);
            if (!Files.exists(fileIndexPath)) {
                throw new RuntimeException("File metadata not found");
            }

            Map<String, Map<String, Integer>> fileList;
            try (BufferedReader reader = Files.newBufferedReader(fileIndexPath)) {
                fileList = new ObjectMapper().readValue(reader, Map.class);
            }

            if (!fileList.containsKey(filename)) {
                throw new RuntimeException("Metadata for file not found: " + filename);
            }

            return fileList.get(filename).get("original_size");
        } catch (IOException e) {
            throw new RuntimeException("Failed to retrieve original size for: " + filename, e);
        }
    }


    /**
     * Compress data using LZ4 before storing.
     */
    public byte[] compressData(byte[] data) {
        return compressor.compress(data);
    }

    /**
     * Decompress LZ4-compressed data.
     */
    public byte[] decompressData(byte[] compressedData, int originalSize) {
        return decompressor.decompress(compressedData, originalSize);
    }


    /**
     * Save metadata about raw files (compressed size & original size).
     */
    public void save(String fileName, int compressedSize, int originalSize) {
        try {
            Path fileIndexPath = Paths.get(FILE_LIST);
            Map<String, Map<String, Integer>> fileList = new HashMap<>();

            if (Files.exists(fileIndexPath)) {
                try (BufferedReader reader = Files.newBufferedReader(fileIndexPath)) {
                    fileList = new ObjectMapper().readValue(reader, Map.class);
                }
            }

            Map<String, Integer> sizeMap = new HashMap<>();
            sizeMap.put("compressed_size", compressedSize);
            sizeMap.put("original_size", originalSize);
            fileList.put(fileName, sizeMap);

            Files.write(fileIndexPath, new ObjectMapper().writeValueAsBytes(fileList));
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file metadata: " + fileName, e);
        }
    }

    public List<String> listRawFiles() {
        try {
            return Files.list(Paths.get(UPLOAD_DIR))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(filename -> !filename.equals("file_list.json")) // Exclude metadata file
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("Could not list raw files", e);
        }
    }



}
