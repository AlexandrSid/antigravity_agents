INSERT INTO addresses (id, country, city, street, building, apartment, postal_code)
SELECT x,
       CONCAT('Country ', x),
       CONCAT('City ', x),
       CONCAT('Street ', x),
       CAST(x AS VARCHAR),
       NULL,
       CONCAT('ZIP-', x)
FROM SYSTEM_RANGE(1, 20);

CREATE LOCAL TEMPORARY TABLE seed_address_distribution (
    address_id BIGINT PRIMARY KEY,
    first_user_id BIGINT NOT NULL,
    last_user_id BIGINT NOT NULL
);

INSERT INTO seed_address_distribution VALUES
 (1,1,110),(2,111,210),(3,211,300),(4,301,380),(5,381,450),
 (6,451,510),(7,511,565),(8,566,615),(9,616,660),(10,661,700),
 (11,701,735),(12,736,765),(13,766,790),(14,791,810),(15,811,828),
 (16,829,844),(17,845,858),(18,859,870),(19,871,880),(20,881,1000);

INSERT INTO users
    (id, first_name, last_name, birth_date, email, phone_number,
     address_id, father_id, mother_id, is_deleted)
SELECT x,
       CONCAT('First', x),
       CONCAT('Last', x),
       DATEADD('DAY', MOD(x, 3650), DATE '1940-01-01'),
       CONCAT('user', x, '@example.com'),
       CONCAT('+1555', RIGHT(CONCAT('0000000', x), 7)),
       (SELECT address_id FROM seed_address_distribution
         WHERE x BETWEEN first_user_id AND last_user_id),
       NULL, NULL, FALSE
FROM SYSTEM_RANGE(1, 100);

INSERT INTO users
    (id, first_name, last_name, birth_date, email, phone_number,
     address_id, father_id, mother_id, is_deleted)
SELECT x,
       CONCAT('First', x),
       CONCAT('Last', x),
       DATEADD('DAY', MOD(x - 101, 3650), DATE '1970-01-01'),
       CONCAT('user', x, '@example.com'),
       CONCAT('+1555', RIGHT(CONCAT('0000000', x), 7)),
       (SELECT address_id FROM seed_address_distribution
         WHERE x BETWEEN first_user_id AND last_user_id),
       1 + MOD(x - 101, 50),
       51 + MOD(x - 101, 50),
       FALSE
FROM SYSTEM_RANGE(101, 400);

INSERT INTO users
    (id, first_name, last_name, birth_date, email, phone_number,
     address_id, father_id, mother_id, is_deleted)
SELECT x,
       CONCAT('First', x),
       CONCAT('Last', x),
       DATEADD('DAY', MOD(x - 401, 7300), DATE '2000-01-01'),
       CONCAT('user', x, '@example.com'),
       CONCAT('+1555', RIGHT(CONCAT('0000000', x), 7)),
       (SELECT address_id FROM seed_address_distribution
         WHERE x BETWEEN first_user_id AND last_user_id),
       101 + MOD(x - 401, 150),
       251 + MOD(x - 401, 150),
       FALSE
FROM SYSTEM_RANGE(401, 1000);

DROP TABLE seed_address_distribution;
ALTER TABLE addresses ALTER COLUMN id RESTART WITH 21;
ALTER TABLE users ALTER COLUMN id RESTART WITH 1001;
