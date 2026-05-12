package com.dragon.service;

import com.dragon.utils.memory.MemoryEntry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Encrypted, persistent vector memory store.
 *
 * <p>All memories are kept in-memory as {@link MemoryEntry} objects and flushed
 * to a binary {@code .vector} file on every write.  The file is encrypted with
 * AES-256-GCM using a key derived from a configurable passphrase via PBKDF2.
 *
 * <h2>File layout (binary)</h2>
 * <pre>
 *  [4 bytes]  magic  = 0x56454354  ("VECT")
 *  [4 bytes]  version = 1
 *  [16 bytes] PBKDF2 salt
 *  [12 bytes] AES-GCM IV
 *  [remaining] AES-GCM ciphertext of serialized List&lt;MemoryEntry&gt;
 * </pre>
 */
@Service
@Slf4j
public class VectorMemoryStore {

    // ── AES-GCM constants ──────────────────────────────────────────────────
    private static final String  AES_ALGO       = "AES/GCM/NoPadding";
    private static final String  KDF_ALGO       = "PBKDF2WithHmacSHA256";
    private static final int     GCM_TAG_BITS   = 128;
    private static final int     GCM_IV_BYTES   = 12;
    private static final int     SALT_BYTES     = 16;
    private static final int     KDF_ITERATIONS = 310_000;   // OWASP 2023 recommendation
    private static final int     KEY_BITS       = 256;

    // ── File format ────────────────────────────────────────────────────────
    private static final int MAGIC   = 0x56454354;  // "VECT"
    private static final int VERSION = 1;

    // ── Spring config ──────────────────────────────────────────────────────
    /** Path to the .vector file, e.g. ${user.home}/dragon/memory.vector */
    @Value("${memory.store.path:memory.vector}")
    private String storePath;

    /**
     * Passphrase used to derive the AES key via PBKDF2.
     * Store this in an env-variable or Vault — never hard-code in production.
     */
    @Value("${memory.store.passphrase:changeme-use-env-var}")
    private String passphrase;

    private final List<MemoryEntry> memories = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() {
        load();
        log.info("[MemoryStore] Loaded {} memories from '{}'", memories.size(), storePath);
    }

    /**
     * Adds a new memory and persists the store to disk.
     *
     * @param userMessage       The user's input.
     * @param assistantResponse The AI's reply.
     * @param tag               Optional category label (may be {@code null}).
     * @param embedding         Semantic float vector for similarity search.
     */
    public void remember(String userMessage, String assistantResponse,
                         String tag, float[] embedding) {
        MemoryEntry entry = new MemoryEntry(userMessage, assistantResponse, tag, embedding);
        memories.add(entry);
        save();
        log.debug("[MemoryStore] Stored memory id={}", entry.getId());
    }

    /**
     * Returns the {@code topK} memories whose embeddings are most similar
     * to {@code queryEmbedding} by cosine similarity.
     *
     * @param queryEmbedding The embedding of the current query.
     * @param topK           How many results to return.
     * @return Ordered list of best-matching memories (most similar first).
     */
    public List<MemoryEntry> recall(float[] queryEmbedding, int topK) {
        return memories.stream()
                .filter(e -> e.getEmbedding() != null
                        && e.getEmbedding().length == queryEmbedding.length)
                .sorted(Comparator.comparingDouble(
                                (MemoryEntry e) -> cosineSimilarity(e.getEmbedding(), queryEmbedding))
                        .reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * Returns all memories that carry the given tag.
     */
    public List<MemoryEntry> recallByTag(String tag) {
        return memories.stream()
                .filter(e -> tag.equalsIgnoreCase(e.getTag()))
                .collect(Collectors.toList());
    }

    /**
     * Deletes the memory with the given ID and persists.
     *
     * @return {@code true} if found and removed.
     */
    public boolean forget(String id) {
        boolean removed = memories.removeIf(e -> e.getId().equals(id));
        if (removed) save();
        return removed;
    }

    /** Returns an unmodifiable snapshot of all memories. */
    public List<MemoryEntry> allMemories() {
        return Collections.unmodifiableList(memories);
    }

    /** How many memories are currently stored. */
    public int size() {
        return memories.size();
    }

    /**
     * Serializes {@link #memories} and writes the encrypted binary to disk.
     *
     * <pre>
     * File layout:
     *   4B  magic   (0x56454354)
     *   4B  version (1)
     *  16B  salt
     *  12B  GCM IV
     *   nB  AES-GCM ciphertext (serialized List&lt;MemoryEntry&gt;)
     * </pre>
     */
    private synchronized void save() {
        try {
            byte[] plaintext = serialize(new ArrayList<>(memories));

            SecureRandom rng  = new SecureRandom();
            byte[] salt = new byte[SALT_BYTES];
            byte[] iv   = new byte[GCM_IV_BYTES];
            rng.nextBytes(salt);
            rng.nextBytes(iv);

            SecretKey key        = deriveKey(passphrase.toCharArray(), salt);
            byte[]    ciphertext = encrypt(plaintext, key, iv);

            Path path = Path.of(storePath);
            Files.createDirectories(path.getParent() == null ? Path.of(".") : path.getParent());

            try (DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(path)))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.write(salt);
                out.write(iv);
                out.write(ciphertext);
            }

            log.debug("[MemoryStore] Saved {} bytes to '{}'", Files.size(path), storePath);

        } catch (Exception e) {
            log.error("[MemoryStore] Failed to save memory store", e);
        }
    }

    @SuppressWarnings("unchecked")
    private synchronized void load() {
        Path path = Path.of(storePath);
        if (!Files.exists(path)) {
            log.info("[MemoryStore] No existing store at '{}' — starting fresh.", storePath);
            return;
        }

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {

            int magic   = in.readInt();
            int version = in.readInt();

            if (magic != MAGIC) {
                log.error("[MemoryStore] Invalid magic bytes — file may be corrupt or wrong format.");
                return;
            }
            if (version != VERSION) {
                log.warn("[MemoryStore] Unexpected version {} (expected {})", version, VERSION);
            }

            byte[] salt = in.readNBytes(SALT_BYTES);
            byte[] iv   = in.readNBytes(GCM_IV_BYTES);

            byte[] ciphertext = in.readAllBytes();

            SecretKey key       = deriveKey(passphrase.toCharArray(), salt);
            byte[]    plaintext = decrypt(ciphertext, key, iv);

            List<MemoryEntry> loaded = (List<MemoryEntry>) deserialize(plaintext);
            memories.clear();
            memories.addAll(loaded);

        } catch (Exception e) {
            log.error("[MemoryStore] Failed to load memory store — store may be corrupt or key mismatch.", e);
        }
    }

    private static SecretKey deriveKey(char[] passphrase, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KDF_ALGO);
        KeySpec spec = new PBEKeySpec(passphrase, salt, KDF_ITERATIONS, KEY_BITS);
        byte[] raw = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(raw, "AES");
    }

    private static byte[] encrypt(byte[] plaintext, SecretKey key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_ALGO);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher.doFinal(plaintext);
    }

    private static byte[] decrypt(byte[] ciphertext, SecretKey key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_ALGO);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher.doFinal(ciphertext);
    }

    private static byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
        }
        return bos.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return ois.readObject();
        }
    }

    /**
     * Cosine similarity between two float vectors.
     * Returns a value in [-1, 1]; higher means more similar.
     */
    static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot   += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0 : dot / denom;
    }
}