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
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * TOTP (RFC 6238) 工具类与二维码生成.
 *
 * @author nacos
 */
public class TotpUtils {
    
    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";
    
    private static final int SECRET_BYTE_LENGTH = 20;
    
    private static final int TIME_STEP_SECONDS = 30;
    
    private static final int DIGITS = 6;
    
    private static final int MODULUS = 1_000_000;
    
    private static final int BUFFER_SIZE = 8;
    
    private static final Base32 BASE32 = new Base32();
    
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    /**
     * 生成 Base32 编码的安全随机 TOTP 密钥.
     *
     * @return 密钥字符串（如 JBSWY3DPEHPK3PXP）
     */
    public static String generateSecretKey() {
        byte[] bytes = new byte[SECRET_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        return BASE32.encodeAsString(bytes).replace("=", "");
    }
    
    /**
     * 生成指定时间戳的 6 位 TOTP 动态码.
     *
     * @param secretKey Base32 密钥
     * @param timeMillis 时间戳（毫秒）
     * @return 6 位数字验证码
     */
    public static String generateCode(String secretKey, long timeMillis) {
        long timeStep = timeMillis / 1000L / TIME_STEP_SECONDS;
        byte[] keyBytes = BASE32.decode(secretKey);
        byte[] data = new byte[BUFFER_SIZE];
        for (int i = BUFFER_SIZE - 1; i >= 0; i--) {
            data[i] = (byte) (timeStep & 0xFF);
            timeStep >>= 8;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
            mac.init(new SecretKeySpec(keyBytes, HMAC_SHA1_ALGORITHM));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int truncatedHash = (hash[offset] & 0x7F) << 24
                    | (hash[offset + 1] & 0xFF) << 16
                    | (hash[offset + 2] & 0xFF) << 8
                    | (hash[offset + 3] & 0xFF);
            int pinValue = truncatedHash % MODULUS;
            return String.format("%0" + DIGITS + "d", pinValue);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate TOTP code", e);
        }
    }
    
    /**
     * 校验 TOTP 动态码（支持 ±1 窗口时钟容差，即 -30s 到 +30s）.
     *
     * @param secretKey Base32 密钥
     * @param code 用户输入的 6 位验证码
     * @param timeMillis 当前时间戳（毫秒）
     * @return 是否验证通过
     */
    public static boolean verifyCode(String secretKey, String code, long timeMillis) {
        if (StringUtils.isBlank(secretKey) || StringUtils.isBlank(code) || code.trim().length() != DIGITS) {
            return false;
        }
        String cleanCode = code.trim();
        long currentStep = timeMillis / 1000L / TIME_STEP_SECONDS;
        for (int i = -1; i <= 1; i++) {
            long step = currentStep + i;
            String expected = generateCode(secretKey, step * TIME_STEP_SECONDS * 1000L);
            if (expected.equals(cleanCode)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 校验 TOTP 动态码（使用系统当前时间）.
     *
     * @param secretKey Base32 密钥
     * @param code 用户输入的 6 位验证码
     * @return 是否验证通过
     */
    public static boolean verifyCode(String secretKey, String code) {
        return verifyCode(secretKey, code, System.currentTimeMillis());
    }
    
    /**
     * 生成 otpauth 协议链接.
     *
     * @param username 用户名
     * @param secretKey 密钥
     * @param issuer 签发者名称（如 Nacos）
     * @return otpauth URI
     */
    public static String getOtpAuthUri(String username, String secretKey, String issuer) {
        try {
            String encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8.name()).replace("+", "%20");
            String encodedAccount = URLEncoder.encode(username, StandardCharsets.UTF_8.name()).replace("+", "%20");
            return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d",
                    encodedIssuer, encodedAccount, secretKey, encodedIssuer, DIGITS, TIME_STEP_SECONDS);
        } catch (UnsupportedEncodingException e) {
            return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s", issuer, username, secretKey, issuer);
        }
    }
    
    /**
     * 将 otpauth URI 转换为 Base64 格式的图片 Data URL.
     *
     * @param otpAuthUri otpauth 协议链接
     * @param width 宽
     * @param height 高
     * @return data:image/png;base64,... 图片字符串
     */
    public static String generateQrCodeBase64(String otpAuthUri, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(otpAuthUri, BarcodeFormat.QR_CODE, width, height);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return "data:image/png;base64," + Base64.encodeBase64String(outputStream.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate QR code for OTP", e);
        }
    }
}
