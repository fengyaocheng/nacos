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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TotpUtils 单元测试.
 *
 * @author nacos
 */
class TotpUtilsTest {
    
    @Test
    void testGenerateSecretKey() {
        String secretKey = TotpUtils.generateSecretKey();
        assertNotNull(secretKey);
        assertTrue(secretKey.length() >= 16);
    }
    
    @Test
    void testGenerateAndVerifyCode() {
        String secretKey = TotpUtils.generateSecretKey();
        long now = 1700000000000L;
        String code = TotpUtils.generateCode(secretKey, now);
        assertEquals(6, code.length());
        
        // 校验当前时间
        assertTrue(TotpUtils.verifyCode(secretKey, code, now));
        
        // 校验容差：前 30 秒（-1 step）和后 30 秒（+1 step）均可通过
        assertTrue(TotpUtils.verifyCode(secretKey, code, now + 25000L));
        assertTrue(TotpUtils.verifyCode(secretKey, code, now - 25000L));
        
        // 校验超出容差：差异 90 秒应被拒绝
        assertFalse(TotpUtils.verifyCode(secretKey, code, now + 90000L));
        assertFalse(TotpUtils.verifyCode(secretKey, code, now - 90000L));
        
        // 错误 code 校验
        assertFalse(TotpUtils.verifyCode(secretKey, "000000", now));
        assertFalse(TotpUtils.verifyCode(secretKey, "invalid", now));
        assertFalse(TotpUtils.verifyCode(secretKey, "", now));
    }
    
    @Test
    void testOtpAuthUriAndQrCode() {
        String secretKey = "JBSWY3DPEHPK3PXP";
        String uri = TotpUtils.getOtpAuthUri("nacos", secretKey, "Nacos");
        assertTrue(uri.startsWith("otpauth://totp/Nacos:nacos?secret=JBSWY3DPEHPK3PXP"));
        
        String qrCodeBase64 = TotpUtils.generateQrCodeBase64(uri, 150, 150);
        assertNotNull(qrCodeBase64);
        assertTrue(qrCodeBase64.startsWith("data:image/png;base64,"));
    }
}
