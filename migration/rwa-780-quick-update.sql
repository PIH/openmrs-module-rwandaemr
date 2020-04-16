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

