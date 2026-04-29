package com.parking.controller;

import com.parking.security.SM4EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/encryption")
@RequiredArgsConstructor
@Slf4j
public class EncryptionController {

    private final SM4EncryptionService encryptionService;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("enabled", encryptionService.isEncryptionEnabled());
        result.put("algorithm", encryptionService.getAlgorithmInfo());
        result.put("provider", "BouncyCastle");
        result.put("description", "SM4国密算法端到端加密");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/encrypt")
    public ResponseEntity<Map<String, Object>> encrypt(@RequestBody Map<String, String> request) {
        String plaintext = request.get("plaintext");
        
        if (plaintext == null || plaintext.isEmpty()) {
            return ResponseEntity.badRequest().body(
                Map.of("success", false, "message", "Plaintext is required")
            );
        }
        
        try {
            String ciphertext = encryptionService.encrypt(plaintext);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("plaintext", plaintext);
            result.put("ciphertext", ciphertext);
            result.put("encrypted", encryptionService.isEncryptionEnabled());
            
            log.info("Encrypted data successfully, length: {} -> {}", 
                     plaintext.length(), ciphertext.length());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Encryption failed", e);
            return ResponseEntity.internalServerError().body(
                Map.of("success", false, "message", "Encryption failed: " + e.getMessage())
            );
        }
    }

    @PostMapping("/decrypt")
    public ResponseEntity<Map<String, Object>> decrypt(@RequestBody Map<String, String> request) {
        String ciphertext = request.get("ciphertext");
        
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ResponseEntity.badRequest().body(
                Map.of("success", false, "message", "Ciphertext is required")
            );
        }
        
        try {
            String plaintext = encryptionService.decrypt(ciphertext);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("ciphertext", ciphertext);
            result.put("plaintext", plaintext);
            result.put("decrypted", encryptionService.isEncryptionEnabled());
            
            log.info("Decrypted data successfully");
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Decryption failed", e);
            return ResponseEntity.internalServerError().body(
                Map.of("success", false, "message", "Decryption failed: " + e.getMessage())
            );
        }
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testEncryption() {
        try {
            String testPlate = "京A12345";
            
            String encrypted = encryptionService.encrypt(testPlate);
            String decrypted = encryptionService.decrypt(encrypted);
            
            boolean success = testPlate.equals(decrypted);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("original", testPlate);
            result.put("encrypted", encrypted);
            result.put("decrypted", decrypted);
            result.put("match", success);
            result.put("encryptionEnabled", encryptionService.isEncryptionEnabled());
            
            if (success) {
                log.info("SM4 encryption test passed: {} -> {} -> {}", 
                         testPlate, encrypted, decrypted);
            } else {
                log.error("SM4 encryption test failed: {} -> {} -> {}", 
                         testPlate, encrypted, decrypted);
            }
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("SM4 encryption test failed", e);
            return ResponseEntity.internalServerError().body(
                Map.of("success", false, "message", "Test failed: " + e.getMessage())
            );
        }
    }

    @GetMapping("/generate-key")
    public ResponseEntity<Map<String, Object>> generateKey() {
        try {
            String keyBase64 = encryptionService.generateKeyBase64();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("key", keyBase64);
            result.put("algorithm", "SM4");
            result.put("keySize", "128 bits");
            result.put("description", "Generated SM4 key (Base64 encoded)");
            result.put("usage", "Set this key in 'encryption.sm4.master-key' in application.properties");
            
            log.info("Generated new SM4 key");
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to generate key", e);
            return ResponseEntity.internalServerError().body(
                Map.of("success", false, "message", "Key generation failed: " + e.getMessage())
            );
        }
    }

    @PostMapping("/encrypt/batch")
    public ResponseEntity<Map<String, Object>> encryptBatch(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> plaintexts = (List<String>) request.get("plaintexts");
        
        if (plaintexts == null || plaintexts.isEmpty()) {
            return ResponseEntity.badRequest().body(
                Map.of("success", false, "message", "Plaintexts list is required")
            );
        }
        
        try {
            Map<String, String> results = new HashMap<>();
            int successCount = 0;
            
            for (String plaintext : plaintexts) {
                if (plaintext != null && !plaintext.isEmpty()) {
                    results.put(plaintext, encryptionService.encrypt(plaintext));
                    successCount++;
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("total", plaintexts.size());
            response.put("encryptedCount", successCount);
            response.put("results", results);
            
            log.info("Batch encrypted {} items", successCount);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Batch encryption failed", e);
            return ResponseEntity.internalServerError().body(
                Map.of("success", false, "message", "Batch encryption failed: " + e.getMessage())
            );
        }
    }

    @PostMapping("/decrypt/batch")
    public ResponseEntity<Map<String, Object>> decryptBatch(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> ciphertexts = (List<String>) request.get("ciphertexts");
        
        if (ciphertexts == null || ciphertexts.isEmpty()) {
            return ResponseEntity.badRequest().body(
                Map.of("success", false, "message", "Ciphertexts list is required")
            );
        }
        
        try {
            Map<String, String> results = new HashMap<>();
            int successCount = 0;
            
            for (String ciphertext : ciphertexts) {
                if (ciphertext != null && !ciphertext.isEmpty()) {
                    results.put(ciphertext, encryptionService.decrypt(ciphertext));
                    successCount++;
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("total", ciphertexts.size());
            response.put("decryptedCount", successCount);
            response.put("results", results);
            
            log.info("Batch decrypted {} items", successCount);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Batch decryption failed", e);
            return ResponseEntity.internalServerError().body(
                Map.of("success", false, "message", "Batch decryption failed: " + e.getMessage())
            );
        }
    }
}
