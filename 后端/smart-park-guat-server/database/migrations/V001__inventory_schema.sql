CREATE TABLE sp_park (
 id BIGINT NOT NULL AUTO_INCREMENT,
 code VARCHAR(64) NOT NULL, name VARCHAR(100) NOT NULL,
 record_origin VARCHAR(8) NOT NULL,
 source VARCHAR(24) NOT NULL,
 source_updated_at DATETIME(3) NULL,
 created_at DATETIME(3) NOT NULL,
 updated_at DATETIME(3) NOT NULL,
 PRIMARY KEY(id),
 UNIQUE KEY uk_sp_park_code(code),
 CONSTRAINT ck_sp_park_origin CHECK(record_origin IN ('REAL','DEMO')),
 CONSTRAINT ck_sp_park_source CHECK(source IN ('MOCK_IMPORT','SIMULATOR','COLLECTOR','MANUAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sp_room (
 id BIGINT NOT NULL AUTO_INCREMENT,
 park_id BIGINT NOT NULL, code VARCHAR(64) NOT NULL, name VARCHAR(100) NOT NULL, location VARCHAR(255) NOT NULL, manager_display_name VARCHAR(100) NULL, scene_model_key VARCHAR(100) NOT NULL,
 record_origin VARCHAR(8) NOT NULL,
 source VARCHAR(24) NOT NULL,
 source_updated_at DATETIME(3) NULL,
 created_at DATETIME(3) NOT NULL,
 updated_at DATETIME(3) NOT NULL,
 PRIMARY KEY(id),
 UNIQUE KEY uk_sp_room_park_code(park_id,code), UNIQUE KEY uk_sp_room_scene_key(scene_model_key), CONSTRAINT fk_sp_room_park FOREIGN KEY(park_id) REFERENCES sp_park(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT ck_sp_room_origin CHECK(record_origin IN ('REAL','DEMO')),
 CONSTRAINT ck_sp_room_source CHECK(source IN ('MOCK_IMPORT','SIMULATOR','COLLECTOR','MANUAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sp_zone (
 id BIGINT NOT NULL AUTO_INCREMENT,
 room_id BIGINT NOT NULL, code VARCHAR(32) NOT NULL, name VARCHAR(100) NOT NULL, display_order INT NOT NULL,
 record_origin VARCHAR(8) NOT NULL,
 source VARCHAR(24) NOT NULL,
 source_updated_at DATETIME(3) NULL,
 created_at DATETIME(3) NOT NULL,
 updated_at DATETIME(3) NOT NULL,
 PRIMARY KEY(id),
 UNIQUE KEY uk_sp_zone_room_code(room_id,code), KEY ix_sp_zone_room_order(room_id,display_order,id), CONSTRAINT fk_sp_zone_room FOREIGN KEY(room_id) REFERENCES sp_room(id) ON DELETE RESTRICT ON UPDATE RESTRICT, CONSTRAINT ck_sp_zone_order CHECK(display_order>=0),
 CONSTRAINT ck_sp_zone_origin CHECK(record_origin IN ('REAL','DEMO')),
 CONSTRAINT ck_sp_zone_source CHECK(source IN ('MOCK_IMPORT','SIMULATOR','COLLECTOR','MANUAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sp_cabinet (
 id BIGINT NOT NULL AUTO_INCREMENT,
 zone_id BIGINT NOT NULL, code VARCHAR(64) NOT NULL, name VARCHAR(100) NOT NULL, serial_number VARCHAR(100) NOT NULL, capacity_u INT NOT NULL, scene_model_key VARCHAR(100) NOT NULL, grid_row INT NOT NULL, grid_column INT NOT NULL, position_x DECIMAL(10,3) NOT NULL, position_y DECIMAL(10,3) NOT NULL, position_z DECIMAL(10,3) NOT NULL, registration_status VARCHAR(16) NOT NULL,
 record_origin VARCHAR(8) NOT NULL,
 source VARCHAR(24) NOT NULL,
 source_updated_at DATETIME(3) NULL,
 created_at DATETIME(3) NOT NULL,
 updated_at DATETIME(3) NOT NULL,
 PRIMARY KEY(id),
 UNIQUE KEY uk_sp_cabinet_zone_code(zone_id,code), UNIQUE KEY uk_sp_cabinet_serial(serial_number), UNIQUE KEY uk_sp_cabinet_scene_key(scene_model_key), KEY ix_sp_cabinet_zone_status(zone_id,registration_status), CONSTRAINT fk_sp_cabinet_zone FOREIGN KEY(zone_id) REFERENCES sp_zone(id) ON DELETE RESTRICT ON UPDATE RESTRICT, CONSTRAINT ck_sp_cabinet_capacity CHECK(capacity_u>0), CONSTRAINT ck_sp_cabinet_grid CHECK(grid_row>=0 AND grid_column>=0), CONSTRAINT ck_sp_cabinet_registration CHECK(registration_status IN ('ACTIVE','INACTIVE')),
 CONSTRAINT ck_sp_cabinet_origin CHECK(record_origin IN ('REAL','DEMO')),
 CONSTRAINT ck_sp_cabinet_source CHECK(source IN ('MOCK_IMPORT','SIMULATOR','COLLECTOR','MANUAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sp_device (
 id BIGINT NOT NULL AUTO_INCREMENT,
 cabinet_id BIGINT NOT NULL, code VARCHAR(64) NOT NULL, name VARCHAR(100) NOT NULL, device_type VARCHAR(16) NOT NULL, scene_model_key VARCHAR(100) NOT NULL, legacy_key VARCHAR(100) NOT NULL, registration_status VARCHAR(16) NOT NULL, u_start INT NULL, u_height INT NULL, network_layer VARCHAR(16) NULL,
 record_origin VARCHAR(8) NOT NULL,
 source VARCHAR(24) NOT NULL,
 source_updated_at DATETIME(3) NULL,
 created_at DATETIME(3) NOT NULL,
 updated_at DATETIME(3) NOT NULL,
 PRIMARY KEY(id),
 UNIQUE KEY uk_sp_device_code(code), UNIQUE KEY uk_sp_device_scene_key(scene_model_key), KEY ix_sp_device_cabinet_type(cabinet_id,device_type,id), CONSTRAINT fk_sp_device_cabinet FOREIGN KEY(cabinet_id) REFERENCES sp_cabinet(id) ON DELETE RESTRICT ON UPDATE RESTRICT, CONSTRAINT ck_sp_device_type CHECK(device_type IN ('SWITCH','SERVER','STORAGE','OTHER')), CONSTRAINT ck_sp_device_registration CHECK(registration_status IN ('ACTIVE','INACTIVE')), CONSTRAINT ck_sp_device_u CHECK((u_start IS NULL AND u_height IS NULL) OR (u_start IS NOT NULL AND u_height IS NOT NULL AND u_start>=1 AND u_height>=1)), CONSTRAINT ck_sp_device_layer CHECK(network_layer IS NULL OR network_layer IN ('CORE','AGGREGATE','ACCESS','TERMINAL')),
 CONSTRAINT ck_sp_device_origin CHECK(record_origin IN ('REAL','DEMO')),
 CONSTRAINT ck_sp_device_source CHECK(source IN ('MOCK_IMPORT','SIMULATOR','COLLECTOR','MANUAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

