package com.userservice.service;

import com.userservice.dto.UserCreateRequest;
import com.userservice.dto.UserResponse;
import com.userservice.dto.UserUpdateRequest;

import java.util.List;

public interface UserService {

    UserResponse create(UserCreateRequest request);

    UserResponse getById(Long id);

    UserResponse update(Long id, UserUpdateRequest request);

    void softDelete(Long id);

    UserResponse getByEmail(String email);

    List<UserResponse> getDirectFamily(Long id);
}
