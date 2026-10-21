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

package com.alibaba.nacos.plugin.auth.impl.persistence;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户 2FA (MFA) 状态与密钥实体.
 *
 * @author nacos
 */
public class UserMfa implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 用户名.
     */
    private String username;
    
    /**
     * 是否启用 2FA.
     */
    private boolean mfaEnabled;
    
    /**
     * 加密存储的 TOTP Secret.
     */
    private String mfaSecret;
    
    /**
     * 2FA 绑定/启用时间.
     */
    private Date mfaEnabledTime;
    
    public UserMfa() {
    }
    
    public UserMfa(String username, boolean mfaEnabled, String mfaSecret, Date mfaEnabledTime) {
        this.username = username;
        this.mfaEnabled = mfaEnabled;
        this.mfaSecret = mfaSecret;
        this.mfaEnabledTime = mfaEnabledTime;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public boolean isMfaEnabled() {
        return mfaEnabled;
    }
    
    public void setMfaEnabled(boolean mfaEnabled) {
        this.mfaEnabled = mfaEnabled;
    }
    
    public String getMfaSecret() {
        return mfaSecret;
    }
    
    public void setMfaSecret(String mfaSecret) {
        this.mfaSecret = mfaSecret;
    }
    
    public Date getMfaEnabledTime() {
        return mfaEnabledTime;
    }
    
    public void setMfaEnabledTime(Date mfaEnabledTime) {
        this.mfaEnabledTime = mfaEnabledTime;
    }
    
    @Override
    public String toString() {
        return "UserMfa{" + "username='" + username + '\'' + ", mfaEnabled=" + mfaEnabled + ", mfaEnabledTime="
                + mfaEnabledTime + '}';
    }
}
