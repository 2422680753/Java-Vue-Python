package com.parking.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.persistence.AttributeConverter;

@Component
@Slf4j
public class LicensePlateConverter implements AttributeConverter<String, String> {

    private static SM4EncryptionService encryptionService;

    @Autowired
    private SM4EncryptionService injectedService;

    @PostConstruct
    public void init() {
        encryptionService = injectedService;
        log.info("LicensePlateConverter initialized with SM4 encryption");
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }
        
        try {
            if (encryptionService != null && encryptionService.isEncryptionEnabled()) {
                return encryptionService.encrypt(attribute);
            }
            return attribute;
        } catch (Exception e) {
            log.error("Failed to encrypt license plate: {}", attribute, e);
            return attribute;
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }
        
        try {
            if (encryptionService != null && encryptionService.isEncryptionEnabled()) {
                return encryptionService.decrypt(dbData);
            }
            return dbData;
        } catch (Exception e) {
            log.error("Failed to decrypt license plate: {}", dbData, e);
            return dbData;
        }
    }
}
