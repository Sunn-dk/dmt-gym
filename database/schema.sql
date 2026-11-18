-- MySQL 8.4 / InnoDB / utf8mb4. Run once in an EMPTY demo database.
-- No DROP, no automatic reset, no business data deletion.
SET NAMES utf8mb4;

CREATE TABLE trainers (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    full_name VARCHAR(100) NOT NULL,
    specialty VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_trainer_name CHECK (CHAR_LENGTH(TRIM(full_name)) BETWEEN 2 AND 100),
    CONSTRAINT ck_trainer_active CHECK (active IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    trainer_id BIGINT NULL UNIQUE,
    credential_version INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_user_trainer FOREIGN KEY (trainer_id) REFERENCES trainers(id),
    CONSTRAINT ck_user_role CHECK (role IN ('MANAGER','RECEPTIONIST','TRAINER')),
    CONSTRAINT ck_user_trainer CHECK (
        (role='TRAINER' AND trainer_id IS NOT NULL) OR
        (role IN ('MANAGER','RECEPTIONIST') AND trainer_id IS NULL)
    ),
    CONSTRAINT ck_user_active CHECK (active IN (0,1)),
    CONSTRAINT ck_credential_version CHECK (credential_version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE members (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    full_name VARCHAR(100) NOT NULL,
    phone VARCHAR(10) NOT NULL UNIQUE,
    birth_date DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(0) NOT NULL,
    created_by BIGINT NOT NULL,
    CONSTRAINT fk_member_creator FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT ck_member_name CHECK (CHAR_LENGTH(TRIM(full_name)) BETWEEN 2 AND 100),
    CONSTRAINT ck_member_phone CHECK (CHAR_LENGTH(phone)=10),
    CONSTRAINT ck_member_active CHECK (active IN (0,1)),
    INDEX ix_member_name (full_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE plans (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    kind VARCHAR(3) NOT NULL,
    duration_days INT NOT NULL,
    sessions_total INT NULL,
    price_vnd BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_plan_name CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 2 AND 100),
    CONSTRAINT ck_plan_days CHECK (duration_days BETWEEN 1 AND 3650),
    CONSTRAINT ck_plan_kind CHECK (kind IN ('GYM','PT')),
    CONSTRAINT ck_plan_sessions CHECK (
        (kind='GYM' AND sessions_total IS NULL) OR
        (kind='PT' AND sessions_total IS NOT NULL AND sessions_total BETWEEN 1 AND 1000)
    ),
    CONSTRAINT ck_plan_price CHECK (price_vnd BETWEEN 1 AND 100000000),
    CONSTRAINT ck_plan_active CHECK (active IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE memberships (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    sale_type VARCHAR(10) NOT NULL,
    request_key CHAR(36) NOT NULL UNIQUE,
    plan_name_snapshot VARCHAR(100) NOT NULL,
    kind_snapshot VARCHAR(3) NOT NULL,
    duration_days_snapshot INT NOT NULL,
    sessions_total_snapshot INT NULL,
    price_vnd_snapshot BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    sold_at DATETIME(0) NOT NULL,
    sold_by BIGINT NOT NULL,
    CONSTRAINT fk_membership_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT fk_membership_plan FOREIGN KEY (plan_id) REFERENCES plans(id),
    CONSTRAINT fk_membership_seller FOREIGN KEY (sold_by) REFERENCES users(id),
    CONSTRAINT ck_sale_type CHECK (sale_type IN ('PURCHASE','RENEWAL')),
    CONSTRAINT ck_snapshot_days CHECK (duration_days_snapshot BETWEEN 1 AND 3650),
    CONSTRAINT ck_snapshot_kind CHECK (kind_snapshot IN ('GYM','PT')),
    CONSTRAINT ck_snapshot_sessions CHECK (
        (kind_snapshot='GYM' AND sessions_total_snapshot IS NULL) OR
        (kind_snapshot='PT' AND sessions_total_snapshot IS NOT NULL AND sessions_total_snapshot BETWEEN 1 AND 1000)
    ),
    CONSTRAINT ck_renewal_kind CHECK (sale_type='PURCHASE' OR kind_snapshot='GYM'),
    CONSTRAINT ck_snapshot_price CHECK (price_vnd_snapshot BETWEEN 1 AND 100000000),
    CONSTRAINT ck_membership_dates CHECK (end_date=DATE_ADD(start_date, INTERVAL duration_days_snapshot DAY)),
    UNIQUE KEY uq_membership_owner (member_id,id),
    INDEX ix_membership_coverage (member_id,kind_snapshot,end_date,start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE trainer_availability (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trainer_id BIGINT NOT NULL,
    start_at DATETIME(0) NOT NULL,
    end_at DATETIME(0) NOT NULL,
    CONSTRAINT fk_availability_trainer FOREIGN KEY (trainer_id) REFERENCES trainers(id),
    CONSTRAINT ck_availability_range CHECK (
        start_at<end_at AND DATE(start_at)=DATE(end_at)
        AND TIME(start_at)>='05:00:00' AND TIME(end_at)<='22:00:00'
    ),
    INDEX ix_availability_trainer_date (trainer_id,start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pt_bookings (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    membership_id BIGINT NOT NULL,
    trainer_id BIGINT NOT NULL,
    start_at DATETIME(0) NOT NULL,
    end_at DATETIME(0) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'BOOKED',
    created_at DATETIME(0) NOT NULL,
    created_by BIGINT NOT NULL,
    resolved_at DATETIME(0) NULL,
    resolved_by BIGINT NULL,
    CONSTRAINT fk_booking_membership_owner FOREIGN KEY (member_id,membership_id)
        REFERENCES memberships(member_id,id),
    CONSTRAINT fk_booking_trainer FOREIGN KEY (trainer_id) REFERENCES trainers(id),
    CONSTRAINT fk_booking_creator FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_booking_resolver FOREIGN KEY (resolved_by) REFERENCES users(id),
    CONSTRAINT ck_booking_status CHECK (status IN ('BOOKED','CANCELLED','COMPLETED','NO_SHOW')),
    CONSTRAINT ck_booking_duration CHECK (
        end_at=DATE_ADD(start_at,INTERVAL 60 MINUTE) AND DATE(start_at)=DATE(end_at)
        AND TIME(start_at)>='05:00:00' AND TIME(end_at)<='22:00:00'
    ),
    CONSTRAINT ck_booking_resolution CHECK (
        (status='BOOKED' AND resolved_at IS NULL AND resolved_by IS NULL) OR
        (status<>'BOOKED' AND resolved_at IS NOT NULL AND resolved_by IS NOT NULL)
    ),
    INDEX ix_booking_trainer_date (trainer_id,start_at),
    INDEX ix_booking_member_date (member_id,start_at),
    INDEX ix_booking_quota (membership_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Should: implement after all Must requirements pass. No coupling to PT booking.
CREATE TABLE equipment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    location VARCHAR(100) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'AVAILABLE',
    note VARCHAR(255) NOT NULL DEFAULT '',
    updated_at DATETIME(0) NOT NULL,
    updated_by BIGINT NOT NULL,
    CONSTRAINT fk_equipment_updater FOREIGN KEY (updated_by) REFERENCES users(id),
    CONSTRAINT ck_equipment_status CHECK (status IN ('AVAILABLE','BROKEN','MAINTENANCE')),
    CONSTRAINT ck_equipment_name CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 2 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    membership_id BIGINT NOT NULL UNIQUE,
    amount_vnd BIGINT NOT NULL,
    method VARCHAR(4) NOT NULL DEFAULT 'CASH',
    paid_at DATETIME(0) NOT NULL,
    collected_by BIGINT NOT NULL,
    CONSTRAINT fk_payment_membership FOREIGN KEY (membership_id) REFERENCES memberships(id),
    CONSTRAINT fk_payment_collector FOREIGN KEY (collected_by) REFERENCES users(id),
    CONSTRAINT ck_payment_amount CHECK (amount_vnd BETWEEN 1 AND 100000000),
    CONSTRAINT ck_payment_method CHECK (method='CASH'),
    INDEX ix_payment_date (paid_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE visits (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    membership_id BIGINT NOT NULL,
    check_in_at DATETIME(0) NOT NULL,
    check_out_at DATETIME(0) NULL,
    check_in_by BIGINT NOT NULL,
    check_out_by BIGINT NULL,
    open_member_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN check_out_at IS NULL THEN member_id ELSE NULL END) STORED,
    CONSTRAINT fk_visit_membership_owner FOREIGN KEY (member_id,membership_id)
        REFERENCES memberships(member_id,id),
    CONSTRAINT fk_visit_in_actor FOREIGN KEY (check_in_by) REFERENCES users(id),
    CONSTRAINT fk_visit_out_actor FOREIGN KEY (check_out_by) REFERENCES users(id),
    CONSTRAINT ck_visit_checkout CHECK (
        (check_out_at IS NULL AND check_out_by IS NULL) OR
        (check_out_at IS NOT NULL AND check_out_by IS NOT NULL AND check_out_at>=check_in_at)
    ),
    UNIQUE KEY uq_open_visit (open_member_id),
    INDEX ix_visit_member_date (member_id,check_in_at),
    INDEX ix_visit_date (check_in_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
