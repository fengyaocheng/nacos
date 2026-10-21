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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RecoveryCodeUtils 单元测试.
 *
 * @author nacos
 */
class RecoveryCodeUtilsTest {
    
    @Test
    void testGenerateRecoveryCodes() {
        List<String> codes = RecoveryCodeUtils.generateRecoveryCodes(10);
        assertEquals(10, codes.size());
        for (String code : codes) {
            assertEquals(9, code.length()); // 4 + '-' + 4 = 9
            assertTrue(code.contains("-"));
        }
    }
    
    @Test
    void testHashCodes() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        List<String> codes = RecoveryCodeUtils.generateRecoveryCodes(5);
        List<String> hashes = RecoveryCodeUtils.hashCodes(codes, encoder);
        assertEquals(5, hashes.size());
        for (int i = 0; i < codes.size(); i++) {
            assertTrue(encoder.matches(codes.get(i).toLowerCase(), hashes.get(i)));
        }
    }
}
