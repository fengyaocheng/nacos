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

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MFA 登录挑战上下文（单机短期会话）.
 *
 * @author nacos
 */
public class MfaChallengeContext implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private final String challengeId;
    
    private final String username;
    
    private final String tempSecret;
    
    private final boolean setupMode;
    
    private final long createdAt;
    
    private final AtomicInteger failedAttempts = new AtomicInteger(0);
    
    public MfaChallengeContext(String challengeId, String username, String tempSecret, boolean setupMode) {
        this.challengeId = challengeId;
        this.username = username;
        this.tempSecret = tempSecret;
        this.setupMode = setupMode;
        this.createdAt = System.currentTimeMillis();
    }
    
    public String getChallengeId() {
        return challengeId;
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getTempSecret() {
        return tempSecret;
    }
    
    public boolean isSetupMode() {
        return setupMode;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public int getFailedAttempts() {
        return failedAttempts.get();
    }
    
    public int incrementFailedAttempts() {
        return failedAttempts.incrementAndGet();
    }
}
