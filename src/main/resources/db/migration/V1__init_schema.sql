-- V1: Initial Schema for user-service

CREATE TABLE addresses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    country VARCHAR(100) NOT NULL,
    city VARCHAR(100) NOT NULL,
    street VARCHAR(150) NOT NULL,
    building VARCHAR(50) NOT NULL,
    apartment VARCHAR(50),
    postal_code VARCHAR(20)
);

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    birth_date DATE NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone_number VARCHAR(50),
    address_id BIGINT,
    father_id BIGINT,
    mother_id BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_users_address FOREIGN KEY (address_id) REFERENCES addresses(id),
    CONSTRAINT fk_users_father FOREIGN KEY (father_id) REFERENCES users(id),
    CONSTRAINT fk_users_mother FOREIGN KEY (mother_id) REFERENCES users(id)
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_phone ON users(phone_number);
CREATE INDEX idx_users_is_deleted ON users(is_deleted);
CREATE INDEX idx_users_parents ON users(father_id, mother_id);
