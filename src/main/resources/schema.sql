CREATE TABLE IF NOT EXISTS chunk_fingerprints (
    fingerprint VARCHAR(64) PRIMARY KEY,
    original_size INT NOT NULL
);
