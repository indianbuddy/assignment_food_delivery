CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    phone         VARCHAR(50)  NOT NULL,
    role          VARCHAR(30)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL
);

CREATE TABLE city (
    id     BIGSERIAL PRIMARY KEY,
    name   VARCHAR(120) NOT NULL UNIQUE,
    active BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE restaurant (
    id       BIGSERIAL PRIMARY KEY,
    name     VARCHAR(255) NOT NULL,
    address  VARCHAR(500) NOT NULL,
    city_id  BIGINT       NOT NULL REFERENCES city (id),
    owner_id BIGINT       NOT NULL REFERENCES app_user (id),
    active   BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_restaurant_city ON restaurant (city_id);
CREATE INDEX idx_restaurant_owner ON restaurant (owner_id);

CREATE TABLE menu_item (
    id             BIGSERIAL PRIMARY KEY,
    restaurant_id  BIGINT         NOT NULL REFERENCES restaurant (id),
    name           VARCHAR(255)   NOT NULL,
    description    VARCHAR(2000)  NOT NULL DEFAULT '',
    price          NUMERIC(10, 2) NOT NULL CHECK (price > 0),
    stock_quantity INTEGER        NOT NULL CHECK (stock_quantity >= 0),
    available      BOOLEAN        NOT NULL DEFAULT TRUE,
    version        BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_menu_item_restaurant ON menu_item (restaurant_id);

CREATE TABLE delivery_partner (
    id      BIGSERIAL PRIMARY KEY,
    user_id BIGINT      NOT NULL UNIQUE REFERENCES app_user (id),
    city_id BIGINT      NOT NULL REFERENCES city (id),
    status  VARCHAR(20) NOT NULL
);

CREATE INDEX idx_delivery_partner_city_status ON delivery_partner (city_id, status);

CREATE TABLE orders (
    id                  BIGSERIAL PRIMARY KEY,
    customer_id         BIGINT         NOT NULL REFERENCES app_user (id),
    restaurant_id       BIGINT         NOT NULL REFERENCES restaurant (id),
    delivery_partner_id BIGINT         REFERENCES delivery_partner (id),
    status              VARCHAR(30)    NOT NULL,
    total_amount        NUMERIC(10, 2) NOT NULL,
    created_at          TIMESTAMP      NOT NULL,
    updated_at          TIMESTAMP      NOT NULL,
    version             BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_orders_customer ON orders (customer_id);
CREATE INDEX idx_orders_restaurant ON orders (restaurant_id);
CREATE INDEX idx_orders_partner ON orders (delivery_partner_id);
CREATE INDEX idx_orders_status ON orders (status);

CREATE TABLE order_item (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT         NOT NULL REFERENCES orders (id),
    menu_item_id    BIGINT         NOT NULL REFERENCES menu_item (id),
    menu_item_name  VARCHAR(255)   NOT NULL,
    quantity        INTEGER        NOT NULL CHECK (quantity > 0),
    price_at_order  NUMERIC(10, 2) NOT NULL
);

CREATE INDEX idx_order_item_order ON order_item (order_id);

CREATE TABLE payment (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT         NOT NULL UNIQUE REFERENCES orders (id),
    amount     NUMERIC(10, 2) NOT NULL,
    status     VARCHAR(20)    NOT NULL,
    method     VARCHAR(50)    NOT NULL,
    created_at TIMESTAMP      NOT NULL
);

CREATE TABLE rating (
    id                 BIGSERIAL PRIMARY KEY,
    order_id           BIGINT      NOT NULL UNIQUE REFERENCES orders (id),
    customer_id        BIGINT      NOT NULL REFERENCES app_user (id),
    restaurant_rating  INTEGER     NOT NULL CHECK (restaurant_rating BETWEEN 1 AND 5),
    delivery_rating    INTEGER     NOT NULL CHECK (delivery_rating BETWEEN 1 AND 5),
    comment            VARCHAR(2000),
    created_at         TIMESTAMP   NOT NULL
);

CREATE TABLE notification_log (
    id                 BIGSERIAL PRIMARY KEY,
    order_id           BIGINT       NOT NULL,
    recipient_user_id  BIGINT       NOT NULL,
    audience           VARCHAR(30)  NOT NULL,
    message            VARCHAR(1000) NOT NULL,
    sent_at            TIMESTAMP    NOT NULL
);

CREATE INDEX idx_notification_log_order ON notification_log (order_id);
