package com.parking.security;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class SM4EncryptionService {

    private static final String ALGORITHM_NAME = "SM4";
    private static final String ALGORITHM_NAME_CBC_PADDING = "SM4/CBC/PKCS7Padding";
    private static final int KEY_SIZE = 128;
    private static final int IV_SIZE = 16;
    
    private SecretKey masterKey;
    private final Map<String, SecretKey> dataKeys = new HashMap<>();

    @Value("${encryption.sm4.master-key:}")
    private String masterKeyBase64;

    @Value("${encryption.sm4.enabled:true}")
    private boolean encryptionEnabled;

    @PostConstruct
    public void init() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
            log.info("BouncyCastle provider registered for SM4 encryption");
        }
        
        try {
            if (masterKeyBase64 != null && !masterKeyBase64.isEmpty()) {
                byte[] keyBytes = Base64.decodeBase64(masterKeyBase64);
                masterKey = new SecretKeySpec(keyBytes, ALGORITHM_NAME);
                log.info("SM4 master key loaded from configuration");
            } else {
                masterKey = generateKey();
                log.warn("No SM4 master key configured, generated random key. " +
                        "Please configure 'encryption.sm4.master-key' in application.properties");
                log.warn("Generated master key (Base64): {}", Base64.encodeBase64String(masterKey.getEncoded()));
            }
        } catch (Exception e) {
            log.error("Failed to initialize SM4 encryption service", e);
            throw new RuntimeException("SM4 encryption service initialization failed", e);
        }
    }

    public SecretKey generateKey() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance(ALGORITHM_NAME, BouncyCastleProvider.PROVIDER_NAME);
        kg.init(KEY_SIZE, new SecureRandom());
        return kg.generateKey();
    }

    public String generateKeyBase64() throws Exception {
        return Base64.encodeBase64String(generateKey().getEncoded());
    }

    public byte[] generateIV() {
        byte[] iv = new byte[IV_SIZE];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    public String encrypt(String plaintext) {
        if (!encryptionEnabled) {
            return plaintext;
        }
        
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        
        try {
            byte[] iv = generateIV();
            byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM_NAME_CBC_PADDING, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new IvParameterSpec(iv));
            
            byte[] ciphertext = cipher.doFinal(plaintextBytes);
            
            byte[] combined = new byte[IV_SIZE + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, IV_SIZE);
            System.arraycopy(ciphertext, 0, combined, IV_SIZE, ciphertext.length);
            
            String encrypted = Base64.encodeBase64String(combined);
            
            log.debug("Encrypted data, length: {} -> {}", plaintext.length(), encrypted.length());
            
            return "SM4:" + encrypted;
            
        } catch (Exception e) {
            log.error("SM4 encryption failed", e);
            throw new RuntimeException("SM4 encryption failed", e);
        }
    }

    public String decrypt(String ciphertext) {
        if (!encryptionEnabled) {
            return ciphertext;
        }
        
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }
        
        if (!ciphertext.startsWith("SM4:")) {
            return ciphertext;
        }
        
        try {
            String base64Data = ciphertext.substring(4);
            byte[] combined = Base64.decodeBase64(base64Data);
            
            byte[] iv = new byte[IV_SIZE];
            System.arraycopy(combined, 0, iv, 0, IV_SIZE);
            
            byte[] ciphertextBytes = new byte[combined.length - IV_SIZE];
            System.arraycopy(combined, IV_SIZE, ciphertextBytes, 0, ciphertextBytes.length);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM_NAME_CBC_PADDING, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new IvParameterSpec(iv));
            
            byte[] plaintextBytes = cipher.doFinal(ciphertextBytes);
            String plaintext = new String(plaintextBytes, StandardCharsets.UTF_8);
            
            log.debug("Decrypted data, length: {} -> {}", ciphertext.length(), plaintext.length());
            
            return plaintext;
            
        } catch (Exception e) {
            log.error("SM4 decryption failed", e);
            throw new RuntimeException("SM4 decryption failed", e);
        }
    }

    public EncryptedData encryptWithDetail(String plaintext) {
        if (!encryptionEnabled) {
            return EncryptedData.builder()
                    .ciphertext(plaintext)
                    .isEncrypted(false)
                    .build();
        }
        
        try {
            byte[] iv = generateIV();
            byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM_NAME_CBC_PADDING, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new IvParameterSpec(iv));
            
            byte[] ciphertextBytes = cipher.doFinal(plaintextBytes);
            
            return EncryptedData.builder()
                    .ciphertext(Base64.encodeBase64String(ciphertextBytes))
                    .iv(Base64.encodeBase64String(iv))
                    .algorithm(ALGORITHM_NAME_CBC_PADDING)
                    .isEncrypted(true)
                    .build();
                    
        } catch (Exception e) {
            log.error("SM4 encryption with detail failed", e);
            throw new RuntimeException("SM4 encryption failed", e);
        }
    }

    public String decryptWithDetail(String ciphertextBase64, String ivBase64) {
        if (!encryptionEnabled) {
            return ciphertextBase64;
        }
        
        try {
            byte[] iv = Base64.decodeBase64(ivBase64);
            byte[] ciphertextBytes = Base64.decodeBase64(ciphertextBase64);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM_NAME_CBC_PADDING, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new IvParameterSpec(iv));
            
            byte[] plaintextBytes = cipher.doFinal(ciphertextBytes);
            return new String(plaintextBytes, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("SM4 decryption with detail failed", e);
            throw new RuntimeException("SM4 decryption failed", e);
        }
    }

    public String createDataKey(String keyId) throws Exception {
        if (dataKeys.containsKey(keyId)) {
            throw new IllegalArgumentException("Data key already exists: " + keyId);
        }
        
        SecretKey dataKey = generateKey();
        dataKeys.put(keyId, dataKey);
        
        return Base64.encodeBase64String(dataKey.getEncoded());
    }

    public String encryptWithDataKey(String keyId, String plaintext) {
        SecretKey dataKey = dataKeys.get(keyId);
        if (dataKey == null) {
            throw new IllegalArgumentException("Data key not found: " + keyId);
        }
        
        try {
            byte[] iv = generateIV();
            Cipher cipher = Cipher.getInstance(ALGORITHM_NAME_CBC_PADDING, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE, dataKey, new IvParameterSpec(iv));
            
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            byte[] combined = new byte[IV_SIZE + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, IV_SIZE);
            System.arraycopy(ciphertext, 0, combined, IV_SIZE, ciphertext.length);
            
            return "SM4-DK:" + keyId + ":" + Base64.encodeBase64String(combined);
            
        } catch (Exception e) {
            log.error("SM4 data key encryption failed", e);
            throw new RuntimeException("SM4 data key encryption failed", e);
        }
    }

    public String decryptWithDataKey(String ciphertext) {
        if (!ciphertext.startsWith("SM4-DK:")) {
            return ciphertext;
        }
        
        String[] parts = ciphertext.substring(7).split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid SM4-DK format");
        }
        
        String keyId = parts[0];
        String base64Data = parts[1];
        
        SecretKey dataKey = dataKeys.get(keyId);
        if (dataKey == null) {
            throw new IllegalArgumentException("Data key not found: " + keyId);
        }
        
        try {
            byte[] combined = Base64.decodeBase64(base64Data);
            
            byte[] iv = new byte[IV_SIZE];
            System.arraycopy(combined, 0, iv, 0, IV_SIZE);
            
            byte[] ciphertextBytes = new byte[combined.length - IV_SIZE];
            System.arraycopy(combined, IV_SIZE, ciphertextBytes, 0, ciphertextBytes.length);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM_NAME_CBC_PADDING, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.DECRYPT_MODE, dataKey, new IvParameterSpec(iv));
            
            byte[] plaintextBytes = cipher.doFinal(ciphertextBytes);
            return new String(plaintextBytes, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("SM4 data key decryption failed", e);
            throw new RuntimeException("SM4 data key decryption failed", e);
        }
    }

    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }

    public String getAlgorithmInfo() {
        return ALGORITHM_NAME_CBC_PADDING;
    }

    @lombok.Data
    @lombok.Builder
    public static class EncryptedData {
        private String ciphertext;
        private String iv;
        private String algorithm;
        private boolean isEncrypted;
    }
}
