package com.userservice.web;

import com.userservice.dto.UserCreateRequest;
import com.userservice.dto.UserResponse;
import com.userservice.dto.UserUpdateRequest;
import com.userservice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable Long id) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @GetMapping("/by-email")
    public UserResponse getByEmail(@RequestParam("email") String email) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @GetMapping("/{id}/family")
    public List<UserResponse> getDirectFamily(@PathVariable Long id) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
