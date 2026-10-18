package com.example.userservice.api.exception

class UserNotFoundException(message: String) : RuntimeException(message)

class ConflictException(message: String) : RuntimeException(message)

class BadParentageException(message: String) : RuntimeException(message)
