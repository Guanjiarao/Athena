-- 用户浏览记录表（如果已建表，先 DROP 再重建）
DROP TABLE IF EXISTS user_view_record;

CREATE TABLE user_view_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    note_id BIGINT NOT NULL,
    first_view_time DATETIME NOT NULL,
    last_view_time DATETIME NOT NULL,
    view_count INT DEFAULT 1,
    duration INT DEFAULT 0 COMMENT '最近一次浏览时长（秒）',
    UNIQUE KEY uk_user_note (user_id, note_id),
    INDEX idx_user_time (user_id, last_view_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户浏览记录';
