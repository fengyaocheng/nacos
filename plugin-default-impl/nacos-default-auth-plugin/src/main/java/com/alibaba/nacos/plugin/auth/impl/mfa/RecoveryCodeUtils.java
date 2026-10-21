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

import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * 2FA 紧急恢复码工具类.
 *
 * @author nacos
 */
public class RecoveryCodeUtils {
    
    /**
     * 恢复码候选字符集，排除易混淆字符（如 0, O, 1, I, l）.
     */
    private static final char[] CODE_CHARS = "23456789abcdefghjkmnpqrstuvwxyz".toCharArray();
    
    private static final int DEFAULT_CODE_COUNT = 10;
    
    private static final int CODE_SEGMENT_LENGTH = 4;
    
    private static final SecureRandom RANDOM = new SecureRandom();
    
    /**
     * 生成指定数量的恢复码列表（默认格式 xxxx-xxxx）.
     *
     * @param count 数量
     * @return 明文恢复码列表
     */
    public static List<String> generateRecoveryCodes(int count) {
        int codeCount = count > 0 ? count : DEFAULT_CODE_COUNT;
        List<String> codes = new ArrayList<>(codeCount);
        for (int i = 0; i < codeCount; i++) {
            codes.add(generateSingleCode());
        }
        return codes;
    }
    
    /**
     * 生成默认数量（10个）的恢复码.
     *
     * @return 10 个明文恢复码
     */
    public static List<String> generateRecoveryCodes() {
        return generateRecoveryCodes(DEFAULT_CODE_COUNT);
    }
    
    private static String generateSingleCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CODE_SEGMENT_LENGTH; i++) {
            sb.append(CODE_CHARS[RANDOM.nextInt(CODE_CHARS.length)]);
        }
        sb.append("-");
        for (int i = 0; i < CODE_SEGMENT_LENGTH; i++) {
            sb.append(CODE_CHARS[RANDOM.nextInt(CODE_CHARS.length)]);
        }
        return sb.toString();
    }
    
    /**
     * 对明文恢复码列表进行 BCrypt 哈希加密.
     *
     * @param plainCodes 明文恢复码列表
     * @param passwordEncoder 密码编码器
     * @return 哈希列表
     */
    public static List<String> hashCodes(List<String> plainCodes, PasswordEncoder passwordEncoder) {
        List<String> hashes = new ArrayList<>(plainCodes.size());
        for (String code : plainCodes) {
            hashes.add(passwordEncoder.encode(code.trim().toLowerCase()));
        }
        return hashes;
    }
}
