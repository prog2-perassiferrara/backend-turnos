CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    login VARCHAR(50) COLLATE utf8mb4_0900_bin NOT NULL,
    email VARCHAR(254) COLLATE utf8mb4_0900_bin NOT NULL,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    image_url VARCHAR(256),
    lang_key VARCHAR(10) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    activated BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    last_modified_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_app_user_login UNIQUE (login),
    CONSTRAINT uk_app_user_email UNIQUE (email)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

-- La PK global evita también cruces login/email entre altas concurrentes.
-- Un conjunto deduplica ambos valores cuando coinciden dentro de la misma cuenta.
CREATE TABLE user_identifier (
    identifier_value VARCHAR(254) COLLATE utf8mb4_0900_bin NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (identifier_value),
    CONSTRAINT fk_user_identifier_account FOREIGN KEY (user_id) REFERENCES app_user (id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE user_authority (
    user_id BIGINT NOT NULL,
    authority VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, authority),
    CONSTRAINT fk_user_authority_account FOREIGN KEY (user_id) REFERENCES app_user (id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
