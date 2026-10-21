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
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.persistence.UserMfa;
import com.alibaba.nacos.plugin.auth.impl.persistence.UserPersistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ConsoleMfaManager 单元测试.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class ConsoleMfaManagerTest {
    
    @Mock
    private UserPersistService userPersistService;
    
    @Mock
    private AuthConfigs authConfigs;
    
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    @InjectMocks
    private ConsoleMfaManager consoleMfaManager;
    
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(consoleMfaManager, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(consoleMfaManager, "tokenSecretKey", "testNacosSecretTokenKey123456789");
    }
    
    @Test
    void testIsMfaEnabled() {
        when(userPersistService.findUserMfa("admin")).thenReturn(new UserMfa("admin", true, "secret", new Date()));
        when(userPersistService.findUserMfa("guest")).thenReturn(null);
        
        assertTrue(consoleMfaManager.isMfaEnabled("admin"));
        assertFalse(consoleMfaManager.isMfaEnabled("guest"));
    }
    
    @Test
    void testSetupWorkflowSuccess() throws AccessException {
        String username = "nacosUser";
        ConsoleMfaManager.MfaSetupResult setupResult = consoleMfaManager.createSetupChallenge(username);
        assertNotNull(setupResult.getChallengeId());
        assertNotNull(setupResult.getSecret());
        assertNotNull(setupResult.getQrCodeBase64());
        
        // 计算正确的一组 OTP
        String validCode = TotpUtils.generateCode(setupResult.getSecret(), System.currentTimeMillis());
        ConsoleMfaManager.MfaVerifyResult verifyResult = consoleMfaManager.verifyAndComplete(
                setupResult.getChallengeId(), validCode, false);
        
        assertEquals(username, verifyResult.getUsername());
        assertNotNull(verifyResult.getRecoveryCodes());
        assertEquals(10, verifyResult.getRecoveryCodes().size());
        
        // 校验持久化层被正确调用
        verify(userPersistService).enableUserMfa(eq(username), anyString(), anyList());
    }
    
    @Test
    void testVerifyWorkflowSuccess() throws AccessException {
        String username = "existingUser";
        String plainSecret = "JBSWY3DPEHPK3PXP";
        String encryptedSecret = SecretCryptoUtils.encrypt(plainSecret, "testNacosSecretTokenKey123456789");
        when(userPersistService.findUserMfa(username)).thenReturn(
                new UserMfa(username, true, encryptedSecret, new Date()));
        
        String challengeId = consoleMfaManager.createVerifyChallenge(username);
        String validCode = TotpUtils.generateCode(plainSecret, System.currentTimeMillis());
        
        ConsoleMfaManager.MfaVerifyResult result = consoleMfaManager.verifyAndComplete(challengeId, validCode, false);
        assertEquals(username, result.getUsername());
        assertNull(result.getRecoveryCodes());
    }
    
    @Test
    void testReplayAttackProtection() throws AccessException {
        String username = "replayUser";
        String plainSecret = "JBSWY3DPEHPK3PXP";
        String encryptedSecret = SecretCryptoUtils.encrypt(plainSecret, "testNacosSecretTokenKey123456789");
        when(userPersistService.findUserMfa(username)).thenReturn(
                new UserMfa(username, true, encryptedSecret, new Date()));
        
        String challengeId1 = consoleMfaManager.createVerifyChallenge(username);
        String validCode = TotpUtils.generateCode(plainSecret, System.currentTimeMillis());
        
        // 第一次成功
        consoleMfaManager.verifyAndComplete(challengeId1, validCode, false);
        
        // 第二次使用同一验证码即便换了 challenge 也会触发防重放拒绝
        String challengeId2 = consoleMfaManager.createVerifyChallenge(username);
        assertThrows(AccessException.class,
                () -> consoleMfaManager.verifyAndComplete(challengeId2, validCode, false));
    }
    
    @Test
    void testMaxFailedAttemptsLock() {
        String username = "lockUser";
        String plainSecret = "JBSWY3DPEHPK3PXP";
        String encryptedSecret = SecretCryptoUtils.encrypt(plainSecret, "testNacosSecretTokenKey123456789");
        when(userPersistService.findUserMfa(username)).thenReturn(
                new UserMfa(username, true, encryptedSecret, new Date()));
        
        String challengeId = consoleMfaManager.createVerifyChallenge(username);
        
        // 连续输入 4 次错误验证码
        for (int i = 0; i < 4; i++) {
            assertThrows(AccessException.class,
                    () -> consoleMfaManager.verifyAndComplete(challengeId, "000000", false));
        }
        
        // 第 5 次触发锁定并清除挑战
        AccessException lockException = assertThrows(AccessException.class,
                () -> consoleMfaManager.verifyAndComplete(challengeId, "000000", false));
        assertTrue(lockException.getErrMsg().contains("Too many failed attempts"));
        
        // 再次尝试应提示 challenge 已失效
        AccessException expiredException = assertThrows(AccessException.class,
                () -> consoleMfaManager.verifyAndComplete(challengeId, "000000", false));
        assertTrue(expiredException.getErrMsg().contains("expired or invalid"));
    }
    
    @Test
    void testRecoveryCodeWorkflow() throws AccessException {
        String username = "recoveryUser";
        when(userPersistService.verifyAndConsumeRecoveryCode(eq(username), eq("abcd-1234"), any(PasswordEncoder.class)))
                .thenReturn(true);
        
        String challengeId = consoleMfaManager.createVerifyChallenge(username);
        ConsoleMfaManager.MfaVerifyResult result = consoleMfaManager.verifyAndComplete(challengeId, "abcd-1234", true);
        assertEquals(username, result.getUsername());
    }
}
