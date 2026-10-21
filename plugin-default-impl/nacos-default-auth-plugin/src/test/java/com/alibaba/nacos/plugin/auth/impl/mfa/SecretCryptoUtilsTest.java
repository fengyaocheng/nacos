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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * SecretCryptoUtils 单元测试.
 *
 * @author nacos
 */
class SecretCryptoUtilsTest {
    
    @Test
    void testEncryptAndDecrypt() {
        String plainSecret = "JBSWY3DPEHPK3PXP";
        String seedKey = "customSecretKeyForNacosUnitTest";
        
        String encrypted = SecretCryptoUtils.encrypt(plainSecret, seedKey);
        assertNotEquals(plainSecret, encrypted);
        
        String decrypted = SecretCryptoUtils.decrypt(encrypted, seedKey);
        assertEquals(plainSecret, decrypted);
    }
    
    @Test
    void testEmptyAndFallback() {
        assertEquals("", SecretCryptoUtils.encrypt("", "key"));
        assertEquals("", SecretCryptoUtils.decrypt("", "key"));
        // 传入非密文字符串解密时容错回退
        String plain = "NotEncryptedSecret";
        assertEquals(plain, SecretCryptoUtils.decrypt(plain, "key"));
    }
}
