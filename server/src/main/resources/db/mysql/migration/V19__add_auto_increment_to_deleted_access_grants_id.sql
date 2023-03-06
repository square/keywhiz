DROP TABLE deleted_accessgrants;

CREATE TABLE `deleted_accessgrants` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT,
    `groupid` bigint(20) NOT NULL,
    `secretid` bigint(20) NOT NULL,
    `updatedat` bigint(20) NOT NULL,
    `createdat` bigint(20) NOT NULL,
    `row_hmac` varchar(64) NOT NULL DEFAULT '',
    PRIMARY KEY (`id`),
    KEY `dag_groupid_secretid_idx` (`groupid`,`secretid`),
    KEY `dag_secretid_idx` (`secretid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;