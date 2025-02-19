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
import java.nio.file.Files;
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
    private static final String FILE_INDEX = "chunks/file_index.json";
    private final Polynomial polynomial = Polynomial.createIrreducible(53);

    @Autowired
    private FileRepository fileRepository;

    public String storeFile(MultipartFile file) {
        try {
            byte[] data = file.getBytes();
            List<byte[]> chunks = chunkFile(data);
            Map<String, List<String>> fileIndex = fileRepository.loadFileIndex();
            List<String> chunkHashes = new ArrayList<>();

            for (byte[] chunk : chunks) {
                String hash = computeHash(chunk);
                if (!fileRepository.exists(hash)) {
                    fileRepository.saveChunk(hash, chunk);
                }
                chunkHashes.add(hash);
            }

            fileIndex.put(file.getOriginalFilename(), chunkHashes);
            fileRepository.saveFileIndex(fileIndex);

            return "File successfully chunked and stored: " + file.getOriginalFilename();
        } catch (IOException e) {
            return "Failed to process file: " + e.getMessage();
        }
    }

    public Resource loadFile(String filename) {
        try {
            Map<String, List<String>> fileIndex = fileRepository.loadFileIndex();
            if (!fileIndex.containsKey(filename)) {
                throw new RuntimeException("File not found: " + filename);
            }

            List<String> chunkHashes = fileIndex.get(filename);
            Path tempFile = Files.createTempFile("reconstructed_", filename);

            for (String hash : chunkHashes) {
                Path chunkPath = Paths.get(CHUNK_DIR, hash);
                byte[] chunkData = Files.readAllBytes(chunkPath);
                Files.write(tempFile, chunkData, java.nio.file.StandardOpenOption.APPEND);
            }

            return new UrlResource(tempFile.toUri());
        } catch (IOException e) {
            throw new RuntimeException("Failed to reconstruct file: " + filename, e);
        }
    }

    private List<byte[]> chunkFile(byte[] data) {
        List<byte[]> chunks = new ArrayList<>();
        RabinFingerprintLong rabin = new RabinFingerprintLong(polynomial);

        int start = 0;
        for (int i = 0; i < data.length; i++) {
            rabin.pushByte(data[i]);
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

    public List<String> listFiles() {
        return new ArrayList<>(fileRepository.loadFileIndex().keySet());
    }
}
