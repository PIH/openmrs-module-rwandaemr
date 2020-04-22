update drug_order set units=null;
update drug_order set frequency=null;
update orders set orderer = 8 where orderer is null; -- admin account
delete from drug_order where order_id in 
(select order_id FROM orders where encounter_id is null);
delete from obs where order_id in 
(select order_id FROM orders where encounter_id is null);
delete from orderextension_order where order_id in 
(select order_id FROM orders where encounter_id is null);
delete from orders where encounter_id is null;
UPDATE `drug` SET `units`='mg' WHERE `drug_id`='747';
ALTER TABLE `order_type`
ADD COLUMN `java_class_name` VARCHAR(255) DEFAULT NULL,
ADD COLUMN `parent` INT(11) DEFAULT NULL;
update orders set orderer=8; -- admin account
UPDATE `orders` SET `discontinued_date`='2017-07-26 00:00:00' WHERE `order_id`='247333';
UPDATE `orders` SET `discontinued_date`='2020-02-19 00:00:00' WHERE `order_id`='484810';
update orders set discontinued_by=8 where discontinued = true and discontinued_by is null;
UPDATE `order_type` SET `java_class_name`='org.openmrs.TestOrder' WHERE `order_type_id`='3';
UPDATE `order_type` SET `java_class_name`='org.openmrs.TestOrder' WHERE `order_type_id`='4';
UPDATE `order_type` SET `java_class_name`='org.openmrs.DrugOrder' WHERE `order_type_id`='2';
UPDATE `order_type` SET `java_class_name`='org.openmrs.DrugOrder' WHERE `order_type_id`='5';
UPDATE `order_type` SET `java_class_name`='org.openmrs.DrugOrder' WHERE `order_type_id`='1';

-- from ensure-unknown-provider-configured
INSERT INTO `person` (`creator`, `date_created`, `uuid`) VALUES(1, NOW(), '8ea1809e-9b78-11e6-9f33-a24fc0d9649c');
INSERT INTO provider(provider_id, person_id, name, identifier, creator, date_created, retired, uuid)
  SELECT person_id AS provider_id, person_id, 'Unknown Provider', 'unknown', 1, '2016-03-28T22:25:46', 0, '6a7d7d04-f523-11e5-9ce9-5e5517507c66'
  FROM person WHERE uuid = '8ea1809e-9b78-11e6-9f33-a24fc0d9649c';
insert into global_property(property, property_value, uuid)
values('provider.unknownProviderUuid', '6a7d7d04-f523-11e5-9ce9-5e5517507c66', '0b665382-f52c-11e5-9ce9-5e5517507c66');
INSERT INTO users(user_id, person_id, system_id, creator, date_created, retired, uuid)
  SELECT person_id AS user_id, person_id, 'unknown', 1, '2016-03-28T22:25:46', 0, '1d575c76-113d-11e6-a148-3e1d05defe78'
  FROM person WHERE uuid = '8ea1809e-9b78-11e6-9f33-a24fc0d9649c';
INSERT INTO provider (provider_id, person_id, name, identifier, creator, date_created, retired, uuid)
  SELECT DISTINCT u.user_id AS provider_id, u.person_id AS person_id,
                  IFNULL(u.username, u.system_id) AS name, IFNULL(u.username, u.system_id) AS identifier,
    u.creator, (NOW()) AS date_created, '0' AS retired, (SELECT UUID()) AS uuid
  FROM users u INNER JOIN orders o ON u.user_id = o.orderer WHERE o.orderer IS NOT NULL
                                                                  and o.orderer != 8 AND u.user_id != 6 AND u.user_id != (SELECT person_id FROM person WHERE uuid = '8ea1809e-9b78-11e6-9f33-a24fc0d9649c');
UPDATE encounter_provider SET provider_id = (SELECT person_id FROM person WHERE uuid = '8ea1809e-9b78-11e6-9f33-a24fc0d9649c')
WHERE provider_id NOT IN (SELECT DISTINCT provider_id FROM provider);

-- delete all modules from the modules folder except logic
update global_property set property_value='false' where property='sync.mandatory';
delete from global_property where property like 'sync%';
delete from scheduler_task_config_property where task_config_id in (select task_config_id from scheduler_task_config where name like '%Sync%');
delete from scheduler_task_config where name like '%Sync%';
delete from scheduler_task_config where name ='Register Reports';
delete from scheduler_task_config where name ='Process Usage Statistics Data';
delete from scheduler_task_config where name ='Send Usage Statistics Reports';
