/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.plugin.auth.impl.mfa;

import com.alibaba.nacos.common.utils.StringUtils;
import org.apache.commons.codec.binary.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * TOTP Secret 加解密工具类（保护数据库中的静态密钥安全）.
 *
 * @author nacos
 */
public class SecretCryptoUtils {
    
    private static final String DEFAULT_SEED = "Nacos2FaSecretCryptoKeySalt";
    
    private static final String AES_ALGORITHM = "AES";
    
    private static final String CIPHER_TRANSFORMATION = "AES/ECB/PKCS5Padding";
    
    private static final int AES_KEY_LENGTH = 16;
    
    /**
     * 加密 TOTP Secret.
     *
     * @param plainSecret 明文密钥
     * @param seedKey 派生秘钥种子（可取自 nacos token secret key）
     * @return Base64 编码的密文
     */
    public static String encrypt(String plainSecret, String seedKey) {
        if (StringUtils.isBlank(plainSecret)) {
            return plainSecret;
        }
        try {
            SecretKeySpec secretKey = deriveKey(seedKey);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(plainSecret.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeBase64String(encrypted);
        } catch (Exception e) {
            // 加密异常时降级返回原始值，避免服务不可用
            return plainSecret;
        }
    }
    
    /**
     * 解密 TOTP Secret.
     *
     * @param encryptedSecret 密文
     * @param seedKey 派生秘钥种子
     * @return 明文密钥
     */
    public static String decrypt(String encryptedSecret, String seedKey) {
        if (StringUtils.isBlank(encryptedSecret)) {
            return encryptedSecret;
        }
        try {
            SecretKeySpec secretKey = deriveKey(seedKey);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decrypted = cipher.doFinal(Base64.decodeBase64(encryptedSecret));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 若历史数据本身为明文或解密失败，返回原值
            return encryptedSecret;
        }
    }
    
    private static SecretKeySpec deriveKey(String seed) {
        String effectiveSeed = StringUtils.isNotBlank(seed) ? seed : DEFAULT_SEED;
        try {
            byte[] keyBytes = effectiveSeed.getBytes(StandardCharsets.UTF_8);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            keyBytes = sha.digest(keyBytes);
            // 派生 128 位 AES 密钥
            keyBytes = Arrays.copyOf(keyBytes, AES_KEY_LENGTH);
            return new SecretKeySpec(keyBytes, AES_ALGORITHM);
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive crypto key", e);
        }
    }
}
