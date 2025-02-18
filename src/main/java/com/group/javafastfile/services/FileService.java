package com.group.javafastfile.services;

import com.group.javafastfile.repositories.FileRepository;
import org.rabinfingerprint.fingerprint.RabinFingerprintLong;
import org.rabinfingerprint.polynomial.Polynomial;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class FileService {

    private static final int MIN_CHUNK_SIZE = 2048;
    private static final int MAX_CHUNK_SIZE = 8192;
    private static final String CHUNK_DIR = "chunks/";
    private static final String INDEX_FILE = "chunks/index.json";
    private final Polynomial polynomial = Polynomial.createIrreducible(53);
    @Autowired
    private FileRepository fileRepository;

    public String chunkAndStoreFile(MultipartFile file) {
        try {
            byte[] data = file.getBytes();
            List<byte[]> chunks = chunkFile(data);

            Map<String, String> index = fileRepository.loadIndex();

            for (byte[] chunk : chunks) {
                String hash = computeHash(chunk);
                if (!index.containsKey(hash)) {
                    fileRepository.saveChunk(hash, chunk);
                    index.put(hash, CHUNK_DIR + hash);
                }
            }
            fileRepository.saveIndex(index);

            return "File successfully chunked and stored: " + file.getOriginalFilename();
        } catch (IOException e) {
            return "Failed to process file: " + e.getMessage();
        }
    }

    private List<byte[]> chunkFile(byte[] data) {
        List<byte[]> chunks = new ArrayList<>();
        RabinFingerprintLong rabin = new RabinFingerprintLong(polynomial);

        int start = 0;
        for (int i = 0; i < data.length; i++) {
            rabin.pushByte(data[i]);

            // Determine chunk boundaries dynamically
            if (rabin.getFingerprintLong() % MIN_CHUNK_SIZE == 0 || i - start >= MAX_CHUNK_SIZE) {
                chunks.add(Arrays.copyOfRange(data, start, i + 1));
                start = i + 1;
                rabin.reset();
            }
        }
        if (start < data.length) {
            chunks.add(Arrays.copyOfRange(data, start, data.length));
        }
        return chunks;
    }

    private String computeHash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hashing algorithm not found", e);
        }
    }

    public Resource loadFile(String filename) {
        try {
            Path filePath = Paths.get(CHUNK_DIR).resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("File not found: " + filename);
            }
        } catch (Exception e) {
            throw new RuntimeException("File not found: " + filename, e);
        }
    }

    public List<String> listFiles() {
        return fileRepository.listChunks();
    }
}
