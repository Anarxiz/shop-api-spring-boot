-- Начальная схема БД. Соответствует JPA-сущностям из com.deliver.shop.domain.entities.
-- Flyway выполняет скрипт в схеме shop (spring.flyway.schemas), поэтому имена таблиц без префикса.

CREATE TABLE categories (
    id   UUID        NOT NULL,
    name VARCHAR(40) NOT NULL,
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT uq_categories_name UNIQUE (name)
);

CREATE TABLE users (
    id            UUID         NOT NULL,
    first_name    VARCHAR(20)  NOT NULL,
    last_name     VARCHAR(20)  NOT NULL,
    email         VARCHAR(100) NOT NULL,
    phone         VARCHAR(20)  NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    password_salt VARCHAR(255) NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_phone UNIQUE (phone)
);

CREATE TABLE addresses (
    id        UUID         NOT NULL,
    city      VARCHAR(50)  NOT NULL,
    street    VARCHAR(100) NOT NULL,
    house     VARCHAR(50)  NOT NULL,
    apartment VARCHAR(5)   NOT NULL,
    floor     VARCHAR(3),
    user_id   UUID         NOT NULL,
    CONSTRAINT pk_addresses PRIMARY KEY (id),
    CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_addresses_user_id ON addresses (user_id);

CREATE TABLE dishes (
    id           UUID           NOT NULL,
    name         VARCHAR(40)    NOT NULL,
    description  VARCHAR(200)   NOT NULL,
    structure    VARCHAR(200)   NOT NULL,
    price        NUMERIC(18, 2) NOT NULL,
    image_url    VARCHAR(255),
    is_available BOOLEAN        NOT NULL,
    category_id  UUID           NOT NULL,
    CONSTRAINT pk_dishes PRIMARY KEY (id),
    CONSTRAINT fk_dishes_category FOREIGN KEY (category_id) REFERENCES categories (id)
);
CREATE INDEX idx_dishes_category_id ON dishes (category_id);

CREATE TABLE orders (
    id          UUID                     NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    status      VARCHAR(50)              NOT NULL,
    total_price NUMERIC(18, 2)           NOT NULL,
    user_id     UUID                     NOT NULL,
    address_id  UUID                     NOT NULL,
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_orders_address FOREIGN KEY (address_id) REFERENCES addresses (id)
);
CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_orders_address_id ON orders (address_id);

CREATE TABLE order_items (
    id             UUID           NOT NULL,
    quantity       INTEGER        NOT NULL,
    price_at_order NUMERIC(18, 2) NOT NULL,
    order_id       UUID           NOT NULL,
    dish_id        UUID           NOT NULL,
    CONSTRAINT pk_order_items PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_dish FOREIGN KEY (dish_id) REFERENCES dishes (id)
);
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_order_items_dish_id ON order_items (dish_id);
