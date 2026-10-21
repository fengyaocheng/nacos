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

import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.persistence.UserMfa;
import com.alibaba.nacos.plugin.auth.impl.persistence.UserPersistService;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 控制台 2FA 单机核心管理服务.
 *
 * @author nacos
 */
@Service
public class ConsoleMfaManager {
    
    public static final int MAX_FAILED_ATTEMPTS = 5;
    
    public static final int QR_CODE_WIDTH = 200;
    
    public static final int QR_CODE_HEIGHT = 200;
    
    private static final int OTP_LENGTH = 6;
    
    private static final String ISSUER = "Nacos";
    
    @Autowired
    private UserPersistService userPersistService;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private AuthConfigs authConfigs;
    
    @Value("${nacos.core.auth.plugin.nacos.token.secret.key:}")
    private String tokenSecretKey;
    
    /**
     * 单机 Challenge 缓存，5 分钟有效，自动淘汰.
     */
    private final Cache<String, MfaChallengeContext> challengeCache = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(2000)
            .build();
    
    /**
     * 已消费的 OTP 缓存（90 秒），防止当前 30 秒窗口内的抓包重放.
     */
    private final Cache<String, Boolean> consumedOtpCache = CacheBuilder.newBuilder()
            .expireAfterWrite(90, TimeUnit.SECONDS)
            .maximumSize(5000)
            .build();
    
    /**
     * 判断用户是否已激活 2FA.
     *
     * @param username 用户名
     * @return 是否已启用
     */
    public boolean isMfaEnabled(String username) {
        UserMfa userMfa = userPersistService.findUserMfa(username);
        return userMfa != null && userMfa.isMfaEnabled();
    }
    
    /**
     * 创建首次绑定 Challenge（生成临时 Secret 与二维码，暂存于内存，未激活前不落库）.
     *
     * @param username 用户名
     * @return 包含 challengeId、secret 与 qrCodeBase64 的上下文
     */
    public MfaSetupResult createSetupChallenge(String username) {
        String challengeId = UUID.randomUUID().toString().replace("-", "");
        String tempSecret = TotpUtils.generateSecretKey();
        String otpAuthUri = TotpUtils.getOtpAuthUri(username, tempSecret, ISSUER);
        String qrCodeBase64 = TotpUtils.generateQrCodeBase64(otpAuthUri, QR_CODE_WIDTH, QR_CODE_HEIGHT);
        
        MfaChallengeContext context = new MfaChallengeContext(challengeId, username, tempSecret, true);
        challengeCache.put(challengeId, context);
        
        return new MfaSetupResult(challengeId, tempSecret, qrCodeBase64);
    }
    
    /**
     * 创建日常登录验证 Challenge.
     *
     * @param username 用户名
     * @return challengeId
     */
    public String createVerifyChallenge(String username) {
        String challengeId = UUID.randomUUID().toString().replace("-", "");
        MfaChallengeContext context = new MfaChallengeContext(challengeId, username, null, false);
        challengeCache.put(challengeId, context);
        return challengeId;
    }
    
    /**
     * 校验 Challenge 并完成 2FA 认证或首次绑定.
     *
     * @param challengeId 挑战 ID
     * @param rawCode 用户输入的 6 位动态码或恢复码
     * @param isRecoveryCode 是否为恢复码
     * @return 认证结果（若为首次绑定成功则包含一次性恢复码列表）
     * @throws AccessException 校验失败时抛出异常
     */
    public MfaVerifyResult verifyAndComplete(String challengeId, String rawCode, boolean isRecoveryCode)
            throws AccessException {
        if (StringUtils.isBlank(challengeId) || StringUtils.isBlank(rawCode)) {
            throw new AccessException("Challenge ID or verification code cannot be empty");
        }
        
        MfaChallengeContext context = challengeCache.getIfPresent(challengeId);
        if (context == null) {
            throw new AccessException("Challenge expired or invalid, please login again");
        }
        
        if (context.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            challengeCache.invalidate(challengeId);
            throw new AccessException("Too many failed attempts. Challenge locked, please login again");
        }
        
        String username = context.getUsername();
        String code = rawCode.trim();
        
        // 1. 首次绑定流程
        if (context.isSetupMode()) {
            boolean valid = TotpUtils.verifyCode(context.getTempSecret(), code);
            if (!valid) {
                int attempts = context.incrementFailedAttempts();
                int remaining = MAX_FAILED_ATTEMPTS - attempts;
                if (remaining <= 0) {
                    challengeCache.invalidate(challengeId);
                    throw new AccessException("Too many failed attempts. Challenge locked, please login again");
                }
                throw new AccessException("Invalid verification code. Remaining attempts: " + remaining);
            }
            
            // 校验通过：持久化 Secret 并生成恢复码
            List<String> plainRecoveryCodes = RecoveryCodeUtils.generateRecoveryCodes();
            List<String> recoveryCodeHashes = RecoveryCodeUtils.hashCodes(plainRecoveryCodes, passwordEncoder);
            String encryptedSecret = SecretCryptoUtils.encrypt(context.getTempSecret(), tokenSecretKey);
            
            userPersistService.enableUserMfa(username, encryptedSecret, recoveryCodeHashes);
            challengeCache.invalidate(challengeId);
            consumedOtpCache.put(username + ":" + code, true);
            
            return new MfaVerifyResult(username, plainRecoveryCodes);
        }
        
        // 2. 日常登录流程
        if (isRecoveryCode) {
            // 使用恢复码单次登录
            boolean consumed = userPersistService.verifyAndConsumeRecoveryCode(username, code.toLowerCase(),
                    passwordEncoder);
            if (!consumed) {
                int attempts = context.incrementFailedAttempts();
                int remaining = MAX_FAILED_ATTEMPTS - attempts;
                if (remaining <= 0) {
                    challengeCache.invalidate(challengeId);
                    throw new AccessException("Too many failed attempts. Challenge locked, please login again");
                }
                throw new AccessException("Invalid recovery code. Remaining attempts: " + remaining);
            }
            challengeCache.invalidate(challengeId);
            return new MfaVerifyResult(username, null);
        } else {
            // 使用 TOTP 动态码登录
            String replayKey = username + ":" + code;
            if (consumedOtpCache.getIfPresent(replayKey) != null) {
                throw new AccessException("Verification code has already been used, please wait for the next code");
            }
            
            UserMfa userMfa = userPersistService.findUserMfa(username);
            if (userMfa == null || !userMfa.isMfaEnabled()) {
                throw new AccessException("MFA is not enabled for this user");
            }
            
            String plainSecret = SecretCryptoUtils.decrypt(userMfa.getMfaSecret(), tokenSecretKey);
            boolean valid = TotpUtils.verifyCode(plainSecret, code);
            if (!valid) {
                int attempts = context.incrementFailedAttempts();
                int remaining = MAX_FAILED_ATTEMPTS - attempts;
                if (remaining <= 0) {
                    challengeCache.invalidate(challengeId);
                    throw new AccessException("Too many failed attempts. Challenge locked, please login again");
                }
                throw new AccessException("Invalid verification code. Remaining attempts: " + remaining);
            }
            
            consumedOtpCache.put(replayKey, true);
            challengeCache.invalidate(challengeId);
            return new MfaVerifyResult(username, null);
        }
    }
    
    /**
     * 重置用户的 2FA 绑定及恢复码.
     *
     * @param username 用户名
     */
    public void resetMfa(String username) {
        userPersistService.resetUserMfa(username);
    }
    
    /**
     * 直接校验用户的 TOTP 验证码（用于 API 携带 mfaCode 防绕过）.
     *
     * @param username 用户名
     * @param rawCode 6 位验证码
     * @return 是否校验通过
     */
    public boolean verifyUserOtpDirectly(String username, String rawCode) {
        if (StringUtils.isBlank(rawCode) || rawCode.trim().length() != OTP_LENGTH) {
            return false;
        }
        String cleanCode = rawCode.trim();
        String replayKey = username + ":" + cleanCode;
        if (consumedOtpCache.getIfPresent(replayKey) != null) {
            return false;
        }
        UserMfa userMfa = userPersistService.findUserMfa(username);
        if (userMfa == null || !userMfa.isMfaEnabled()) {
            return false;
        }
        String plainSecret = SecretCryptoUtils.decrypt(userMfa.getMfaSecret(), tokenSecretKey);
        boolean valid = TotpUtils.verifyCode(plainSecret, cleanCode);
        if (valid) {
            consumedOtpCache.put(replayKey, true);
        }
        return valid;
    }
    
    public static class MfaSetupResult {
        
        private final String challengeId;
        
        private final String secret;
        
        private final String qrCodeBase64;
        
        public MfaSetupResult(String challengeId, String secret, String qrCodeBase64) {
            this.challengeId = challengeId;
            this.secret = secret;
            this.qrCodeBase64 = qrCodeBase64;
        }
        
        public String getChallengeId() {
            return challengeId;
        }
        
        public String getSecret() {
            return secret;
        }
        
        public String getQrCodeBase64() {
            return qrCodeBase64;
        }
    }
    
    public static class MfaVerifyResult {
        
        private final String username;
        
        private final List<String> recoveryCodes;
        
        public MfaVerifyResult(String username, List<String> recoveryCodes) {
            this.username = username;
            this.recoveryCodes = recoveryCodes;
        }
        
        public String getUsername() {
            return username;
        }
        
        public List<String> getRecoveryCodes() {
            return recoveryCodes;
        }
    }
}
