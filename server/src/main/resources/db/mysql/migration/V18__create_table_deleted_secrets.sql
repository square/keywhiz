CREATE TABLE `deleted_secrets` (
    `id` bigint NOT NULL,
    `name` varchar(255) NOT NULL,
    `updatedat` bigint NOT NULL,
    `createdat` bigint NOT NULL,
    `description` varchar(255),
    `createdby` varchar(255),
    `updatedby` varchar(255),
    `type` varchar(20),
    `options` varchar(255) NOT NULL DEFAULT '{}',
    `current` bigint,
    `row_hmac` varchar(64) NOT NULL DEFAULT '',
    `owner` bigint,
    `expiry` bigint,
    PRIMARY KEY (`id`),
    KEY `ds_name_idx` (`name`),
    KEY `ds_current_idx` (`current`),
    KEY `ds_owner_idx` (`owner`),
    KEY `ds_expiry_idx` (`expiry`),
    KEY `ds_expiry_current` (`expiry`,`current`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE `deleted_accessgrants` (
    `id` bigint(20) NOT NULL,
    `groupid` bigint(20) NOT NULL,
    `secretid` bigint(20) NOT NULL,
    `updatedat` bigint(20) NOT NULL,
    `createdat` bigint(20) NOT NULL,
    `row_hmac` varchar(64) NOT NULL DEFAULT '',
    PRIMARY KEY (`id`),
    KEY `dag_groupid_secretid_idx` (`groupid`,`secretid`),
    KEY `dag_secretid_idx` (`secretid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;