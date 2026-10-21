/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.persistence.model.Page;

import java.util.List;

/**
 * User CRUD service.
 *
 * @author nkorange
 * @since 1.2.0
 */
@SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
public interface UserPersistService {

    /**
     * create user.
     *
     * @param username username
     * @param password password
     */
    void createUser(String username, String password);

    /**
     * delete user by username.
     *
     * @param username username
     */
    void deleteUser(String username);

    /**
     * update password of the user.
     *
     * @param username username
     * @param password password
     */
    void updateUserPassword(String username, String password);

    /**
     * query user by username.
     *
     * @param username username
     * @return user
     */
    User findUserByUsername(String username);

    /**
     * get users by page.
     *
     * @param pageNo pageNo
     * @param pageSize pageSize
     * @return user page info
     */
    Page<User> getUsers(int pageNo, int pageSize, String username);

    /**
     * fuzzy query user by username.
     *
     * @param username username
     * @return usernames
     */
    List<String> findUserLikeUsername(String username);

    Page<User> findUsersLike4Page(String username, int pageNo, int pageSize);

    String generateLikeArgument(String s);
    
    /**
     * create Pagination utils.
     *
     * @param <E> Generic object
     * @return {@link AuthPaginationHelper}
     */
    <E> AuthPaginationHelper<E> createPaginationHelper();
    
    /**
     * 查询用户 2FA 信息.
     *
     * @param username 用户名
     * @return UserMfa 实体
     */
    UserMfa findUserMfa(String username);
    
    /**
     * 启用用户 2FA 绑定并存储加密 Secret 及恢复码哈希.
     *
     * @param username 用户名
     * @param encryptedSecret 加密后的 Secret
     * @param recoveryCodeHashes 恢复码哈希列表
     */
    void enableUserMfa(String username, String encryptedSecret, List<String> recoveryCodeHashes);
    
    /**
     * 重置或禁用用户 2FA（删除 Secret 并清理恢复码）.
     *
     * @param username 用户名
     */
    void resetUserMfa(String username);
    
    /**
     * 校验并原子消费单个恢复码.
     *
     * @param username 用户名
     * @param rawCode 原始恢复码
     * @param passwordEncoder 密码校验器
     * @return 是否成功消费
     */
    boolean verifyAndConsumeRecoveryCode(String username, String rawCode,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder);
}
