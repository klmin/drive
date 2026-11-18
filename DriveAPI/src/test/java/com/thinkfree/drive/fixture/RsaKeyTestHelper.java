package com.thinkfree.drive.fixture;

import lombok.NoArgsConstructor;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

@NoArgsConstructor
public class RsaKeyTestHelper {
    
    public static RsaKeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return new RsaKeyPair(
                    (RSAPublicKey) keyPair.getPublic(),
                    (RSAPrivateKey) keyPair.getPrivate()
            );
        } catch (Exception e) {
            throw new RuntimeException("RSA 키 쌍 생성 실패", e);
        }
    }

    public record RsaKeyPair(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        
        public String publicKeyBase64() {
            return Base64.getEncoder().encodeToString(publicKey.getEncoded());
        }
        
        public String publicKeyPem() {
            String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(publicKey.getEncoded());
            return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
        }
        
        public String privateKeyBase64() {
            return Base64.getEncoder().encodeToString(privateKey.getEncoded());
        }
    }
}
