CREATE TABLE IF NOT EXISTS user_event_recommendation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    caregiver_id BIGINT NOT NULL,
    aqi INT,
    weather VARCHAR(50),
    temperature DECIMAL(5,1),
    event_name VARCHAR(100) NOT NULL,
    medication_period VARCHAR(30) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    event_date DATE NOT NULL,
    remark TEXT,
    user_feedback VARCHAR(20) NOT NULL,
    score INT NOT NULL
);
