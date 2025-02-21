package com.group.javafastfile.services;

import com.group.javafastfile.repositories.FileRepository;
import org.rabinfingerprint.fingerprint.RabinFingerprintLong;
import org.rabinfingerprint.polynomial.Polynomial;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class FileService {

    private static final Logger logger = LoggerFactory.getLogger(FileService.class);

    private static final int MIN_CHUNK_SIZE = 2048;
    private static final int MAX_CHUNK_SIZE = 8192;
    private final Polynomial polynomial = Polynomial.createIrreducible(53);

    @Autowired
    private FileRepository fileRepository;

    public String storeFile(MultipartFile file) {
        try {
            long startTime = System.currentTimeMillis();

            byte[] data = file.getBytes();
            int originalSize = data.length;

            List<byte[]> chunks = chunkFile(data);
            int totalChunkSize = 0;
            List<String> chunkHashes = new ArrayList<>();

            for (byte[] chunk : chunks) {
                String hash = computeHash(chunk);
                if (!fileRepository.exists(hash)) {
                    fileRepository.saveChunk(hash, chunk);
                    totalChunkSize += chunk.length;
                }
                chunkHashes.add(hash);
            }

            // Log chunking time
            long endTime = System.currentTimeMillis();
            logger.info("Chunking time for {}: {} ms", file.getOriginalFilename(), (endTime - startTime));

            // Log storage savings
            double storageSaved = 100.0 * (1 - ((double) totalChunkSize / originalSize));
            logger.info("Storage saved by chunking {}: {}% (Original: {} bytes, Chunks: {} bytes)",
                    file.getOriginalFilename(), String.format("%.2f", storageSaved), originalSize, totalChunkSize);


            // Save file index
            Map<String, List<String>> fileIndex = fileRepository.loadFileIndex();
            fileIndex.put(file.getOriginalFilename(), chunkHashes);
            fileRepository.saveFileIndex(fileIndex);

            return "File successfully chunked and stored: " + file.getOriginalFilename();
        } catch (IOException e) {
            logger.error("Failed to process file: {}", e.getMessage());
            return "Failed to process file: " + e.getMessage();
        }
    }

    public Resource loadFile(String filename) {
        try {
            long startTime = System.currentTimeMillis();

            Map<String, List<String>> fileIndex = fileRepository.loadFileIndex();
            if (!fileIndex.containsKey(filename)) {
                throw new RuntimeException("File not found: " + filename);
            }

            List<String> chunkHashes = fileIndex.get(filename);
            Path tempFile = Files.createTempFile("reconstructed_", filename);

            for (String hash : chunkHashes) {
                Path chunkPath = Paths.get(FileRepository.CHUNK_DIR, hash);
                byte[] compressedData = Files.readAllBytes(chunkPath);

                int originalSize = fileRepository.determineOriginalSize(hash);

                byte[] decompressedData = fileRepository.decompressChunk(compressedData, originalSize);
                Files.write(tempFile, decompressedData, java.nio.file.StandardOpenOption.APPEND);
            }

            long endTime = System.currentTimeMillis();
            logger.info("Reconstruction time for {}: {} ms", filename, (endTime - startTime));

            return new UrlResource(tempFile.toUri());
        } catch (IOException e) {
            logger.error("Failed to reconstruct file: {}", filename, e);
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

    public String storeFileRaw(MultipartFile file) {
        try {
            long startTime = System.currentTimeMillis();

            byte[] fileData = file.getBytes();
            int originalSize = fileData.length;
            byte[] compressedData = fileRepository.compressData(fileData); // Compress with LZ4

            int compressedSize = compressedData.length;
            double compressionRatio = 100.0 * (1 - ((double) compressedSize / originalSize));

            Path uploadPath = Paths.get(FileRepository.UPLOAD_DIR);

            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            Path filePath = uploadPath.resolve(Objects.requireNonNull(file.getOriginalFilename()));
            Files.write(filePath, compressedData); // Store compressed file

            fileRepository.save(file.getOriginalFilename(), compressedSize, originalSize);

            long endTime = System.currentTimeMillis();
            logger.info("Compression time for {}: {} ms", file.getOriginalFilename(), (endTime - startTime));
            logger.info("Compression savings for {}: {:.2f}% (Original: {} bytes, Compressed: {} bytes)",
                    file.getOriginalFilename(), compressionRatio, originalSize, compressedSize);

            return "File uploaded successfully (compressed) without chunking: " + file.getOriginalFilename();
        } catch (IOException e) {
            logger.error("Failed to upload raw file: {}", e.getMessage());
            return "Failed to upload raw file: " + e.getMessage();
        }
    }

    public List<String> listFilesRaw() {
        return fileRepository.listRawFiles();
    }

    public Resource loadRawFile(String filename) {
        try {
            long startTime = System.currentTimeMillis();

            Path filePath = Paths.get(FileRepository.UPLOAD_DIR).resolve(filename).normalize();

            if (!Files.exists(filePath)) {
                throw new RuntimeException("File not found: " + filename);
            }

            // Read compressed file
            byte[] compressedData = Files.readAllBytes(filePath);

            // Get the original size
            int originalSize = fileRepository.determineOriginalSizeRaw(filename);

            // Decompress the file using LZ4
            byte[] decompressedData = fileRepository.decompressData(compressedData, originalSize);

            // Store decompressed content in a temporary file
            Path tempFile = Files.createTempFile("decompressed_", filename);
            Files.write(tempFile, decompressedData);

            long endTime = System.currentTimeMillis();
            logger.info("Decompression time for {}: {} ms", filename, (endTime - startTime));

            return new UrlResource(tempFile.toUri());
        } catch (IOException e) {
            logger.error("Failed to load raw file: {}", filename, e);
            throw new RuntimeException("Failed to load raw file: " + filename, e);
        }
    }

}
