CREATE TABLE IF NOT EXISTS player_reviews (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    player_id BIGINT UNSIGNED NOT NULL,
    author_player_id BIGINT UNSIGNED NOT NULL,
    content TEXT NOT NULL,
    positions VARCHAR(100) NOT NULL DEFAULT '',
    anonymous TINYINT NOT NULL DEFAULT 0,
    rating_count INT NOT NULL DEFAULT 0,
    rating_sum INT NOT NULL DEFAULT 0,
    confidence_score DECIMAL(8, 3) NOT NULL DEFAULT 0.000,
    tip_total INT NOT NULL DEFAULT 0,
    popularity_score DECIMAL(10, 3) NOT NULL DEFAULT 0.000,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_player_reviews_player (player_id, deleted),
    KEY idx_player_reviews_author (author_player_id, deleted),
    KEY idx_player_reviews_popularity (popularity_score, deleted),
    CONSTRAINT fk_player_reviews_player FOREIGN KEY (player_id) REFERENCES players(id),
    CONSTRAINT fk_player_reviews_author FOREIGN KEY (author_player_id) REFERENCES players(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS player_review_ratings (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    review_id BIGINT UNSIGNED NOT NULL,
    rater_player_id BIGINT UNSIGNED NOT NULL,
    score INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_rater (review_id, rater_player_id),
    KEY idx_player_review_ratings_rater (rater_player_id, deleted),
    CONSTRAINT fk_player_review_ratings_review FOREIGN KEY (review_id) REFERENCES player_reviews(id),
    CONSTRAINT fk_player_review_ratings_rater FOREIGN KEY (rater_player_id) REFERENCES players(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS player_review_tips (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    review_id BIGINT UNSIGNED NOT NULL,
    tipper_player_id BIGINT UNSIGNED NOT NULL,
    amount INT NOT NULL,
    deposit_ledger_id BIGINT UNSIGNED NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_player_review_tips_review (review_id, deleted),
    KEY idx_player_review_tips_tipper (tipper_player_id, deleted),
    CONSTRAINT fk_player_review_tips_review FOREIGN KEY (review_id) REFERENCES player_reviews(id),
    CONSTRAINT fk_player_review_tips_tipper FOREIGN KEY (tipper_player_id) REFERENCES players(id),
    CONSTRAINT fk_player_review_tips_ledger FOREIGN KEY (deposit_ledger_id) REFERENCES player_deposit_ledger(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
