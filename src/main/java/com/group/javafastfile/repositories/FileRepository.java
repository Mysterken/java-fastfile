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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Repository
public class FileRepository {
    public static final String CHUNK_DIR = "chunks/";
    private static final String FILE_INDEX = "chunks/file_index.json";

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

    // New method to save a ZIP file
    public void saveZipFile(String zipFilePath) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(Paths.get(zipFilePath)))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                byte[] fileContent = zipInputStream.readAllBytes();
                String hash = generateHash(fileContent); // Implement this method to generate a unique hash for the file content
                saveChunk(hash, fileContent);
                zipInputStream.closeEntry();
            }
        }
    }

    // New method to create a ZIP file from chunks
    public void createZipFile(String zipFilePath, List<String> chunkHashes) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(Paths.get(zipFilePath)))) {
            for (String hash : chunkHashes) {
                Path chunkPath = Paths.get(CHUNK_DIR, hash);
                byte[] compressedChunk = Files.readAllBytes(chunkPath);
                int originalSize = determineOriginalSize(hash);
                byte[] decompressedChunk = decompressChunk(compressedChunk, originalSize);

                ZipEntry entry = new ZipEntry(hash);
                zipOutputStream.putNextEntry(entry);
                zipOutputStream.write(decompressedChunk);
                zipOutputStream.closeEntry();
            }
        }
    }

    // Implement this method to generate a unique hash for the file content
    private String generateHash(byte[] content) {
        // Your hash generation logic here
        return "";
    }
}