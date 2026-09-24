package com.userservice.service;

import com.userservice.dto.UserCreateRequest;
import com.userservice.dto.UserResponse;
import com.userservice.dto.UserUpdateRequest;
import com.userservice.repository.AddressRepository;
import com.userservice.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;

    public UserServiceImpl(UserRepository userRepository, AddressRepository addressRepository) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
    }

    @Override
    public UserResponse create(UserCreateRequest request) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public UserResponse getById(Long id) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public UserResponse update(Long id, UserUpdateRequest request) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void softDelete(Long id) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public UserResponse getByEmail(String email) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<UserResponse> getDirectFamily(Long id) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
