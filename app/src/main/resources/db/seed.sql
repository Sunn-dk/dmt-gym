-- Demo only; run once after schema.sql and bootstrap users.
-- Required users: 1 MANAGER, 2 RECEPTIONIST, 3 TRAINER linked to trainer 1,
-- 4 TRAINER linked to trainer 2. Run seed_trainers.sql before user bootstrap.
-- This file deliberately contains no plain-text password or fake password hash.
-- D follows the gym timezone; tests use an injected fixed Clock instead.
SET time_zone = '+07:00';
SET @d = CURRENT_DATE;
START TRANSACTION;

INSERT INTO plans (id,name,kind,duration_days,sessions_total,price_vnd,active) VALUES
(1,'Gym 30 ngày','GYM',30,NULL,300000,TRUE),
(2,'Gym 90 ngày','GYM',90,NULL,800000,TRUE),
(3,'Gói cũ ngừng bán','GYM',30,NULL,250000,FALSE),
(4,'PT 4 buổi','PT',30,4,1200000,TRUE),
(5,'PT 12 buổi','PT',90,12,3300000,TRUE);

INSERT INTO members (id,full_name,phone,birth_date,active,created_at,created_by) VALUES
(1,'Hội viên mẫu An','0900000001','2000-01-15',TRUE,NOW(),1),
(2,'Hội viên mẫu Bình','0900000002','1998-06-20',TRUE,NOW(),1),
(3,'Hội viên mẫu Chi','0900000003','2001-03-10',TRUE,NOW(),1),
(4,'Hội viên mẫu Dũng','0900000004','1995-08-09',FALSE,NOW(),1),
(5,'Hội viên mẫu Hà','0900000005','2000-12-01',TRUE,NOW(),1),
(6,'Hội viên mẫu Lan','0900000006','1999-05-12',TRUE,NOW(),1);

INSERT INTO memberships
(id,member_id,plan_id,sale_type,request_key,plan_name_snapshot,kind_snapshot,duration_days_snapshot,sessions_total_snapshot,
 price_vnd_snapshot,start_date,end_date,sold_at,sold_by) VALUES
(1,2,1,'PURCHASE','00000000-0000-4000-8000-000000000001','Gym 30 ngày','GYM',30,NULL,300000,
 DATE_SUB(@d,INTERVAL 20 DAY),DATE_ADD(@d,INTERVAL 10 DAY),DATE_SUB(@d,INTERVAL 20 DAY),2),
(2,3,1,'PURCHASE','00000000-0000-4000-8000-000000000002','Gym 30 ngày','GYM',30,NULL,300000,
 DATE_SUB(@d,INTERVAL 31 DAY),DATE_SUB(@d,INTERVAL 1 DAY),DATE_SUB(@d,INTERVAL 31 DAY),2),
(3,4,1,'PURCHASE','00000000-0000-4000-8000-000000000003','Gym 30 ngày','GYM',30,NULL,300000,
 DATE_SUB(@d,INTERVAL 5 DAY),DATE_ADD(@d,INTERVAL 25 DAY),DATE_SUB(@d,INTERVAL 5 DAY),2),
(4,5,1,'PURCHASE','00000000-0000-4000-8000-000000000004','Gym 30 ngày','GYM',30,NULL,300000,
 DATE_SUB(@d,INTERVAL 5 DAY),DATE_ADD(@d,INTERVAL 25 DAY),DATE_SUB(@d,INTERVAL 5 DAY),2),
(5,6,1,'PURCHASE','00000000-0000-4000-8000-000000000005','Gym 30 ngày','GYM',30,NULL,300000,
 DATE_SUB(@d,INTERVAL 30 DAY),@d,DATE_SUB(@d,INTERVAL 30 DAY),2),
(6,6,2,'RENEWAL','00000000-0000-4000-8000-000000000006','Gym 90 ngày','GYM',90,NULL,800000,
 @d,DATE_ADD(@d,INTERVAL 90 DAY),DATE_SUB(@d,INTERVAL 1 DAY),2);

INSERT INTO memberships
(id,member_id,plan_id,sale_type,request_key,plan_name_snapshot,kind_snapshot,
 duration_days_snapshot,sessions_total_snapshot,price_vnd_snapshot,start_date,end_date,sold_at,sold_by) VALUES
(7,2,4,'PURCHASE','00000000-0000-4000-8000-000000000007','PT 4 buổi','PT',30,4,1200000,
 DATE_SUB(@d,INTERVAL 10 DAY),DATE_ADD(@d,INTERVAL 20 DAY),DATE_SUB(@d,INTERVAL 10 DAY),2),
(8,1,4,'PURCHASE','00000000-0000-4000-8000-000000000008','PT 4 buổi','PT',30,4,1200000,
 @d,DATE_ADD(@d,INTERVAL 30 DAY),NOW(),2);

INSERT INTO payments (id,membership_id,amount_vnd,method,paid_at,collected_by)
SELECT id,id,price_vnd_snapshot,'CASH',sold_at,sold_by FROM memberships;

-- A deliberately unresolved visit from yesterday to demonstrate manual close.
INSERT INTO visits (id,member_id,membership_id,check_in_at,check_out_at,check_in_by,check_out_by) VALUES
(1,5,4,DATE_ADD(DATE_SUB(@d,INTERVAL 1 DAY),INTERVAL 18 HOUR),NULL,2,NULL),
(2,2,1,DATE_ADD(DATE_SUB(@d,INTERVAL 1 DAY),INTERVAL 17 HOUR),
 DATE_ADD(DATE_SUB(@d,INTERVAL 1 DAY),INTERVAL 18 HOUR),2,2);
INSERT INTO visits (id,member_id,membership_id,check_in_at,check_out_at,check_in_by,check_out_by) VALUES
(3,2,1,DATE_ADD(DATE_SUB(@d,INTERVAL 2 DAY),INTERVAL 17 HOUR),
 DATE_ADD(DATE_SUB(@d,INTERVAL 2 DAY),INTERVAL 18 HOUR),2,2);

INSERT INTO trainer_availability (id,trainer_id,start_at,end_at) VALUES
(1,1,DATE_ADD(DATE_ADD(@d,INTERVAL 1 DAY),INTERVAL 8 HOUR),DATE_ADD(DATE_ADD(@d,INTERVAL 1 DAY),INTERVAL 21 HOUR)),
(2,2,DATE_ADD(DATE_ADD(@d,INTERVAL 1 DAY),INTERVAL 8 HOUR),DATE_ADD(DATE_ADD(@d,INTERVAL 1 DAY),INTERVAL 21 HOUR)),
(3,1,DATE_ADD(DATE_SUB(@d,INTERVAL 2 DAY),INTERVAL 8 HOUR),DATE_ADD(DATE_SUB(@d,INTERVAL 2 DAY),INTERVAL 21 HOUR)),
(4,2,DATE_ADD(DATE_SUB(@d,INTERVAL 1 DAY),INTERVAL 8 HOUR),DATE_ADD(DATE_SUB(@d,INTERVAL 1 DAY),INTERVAL 21 HOUR)),
(5,1,DATE_ADD(DATE_SUB(@d,INTERVAL 3 DAY),INTERVAL 8 HOUR),DATE_ADD(DATE_SUB(@d,INTERVAL 3 DAY),INTERVAL 21 HOUR));

INSERT INTO pt_bookings
(id,member_id,membership_id,trainer_id,start_at,end_at,status,created_at,created_by,resolved_at,resolved_by) VALUES
(1,2,7,1,DATE_ADD(DATE_SUB(@d,INTERVAL 2 DAY),INTERVAL 17 HOUR),DATE_ADD(DATE_SUB(@d,INTERVAL 2 DAY),INTERVAL 18 HOUR),
 'COMPLETED',DATE_SUB(@d,INTERVAL 3 DAY),2,DATE_ADD(DATE_SUB(@d,INTERVAL 2 DAY),INTERVAL 18 HOUR),3),
(2,2,7,1,DATE_ADD(DATE_ADD(@d,INTERVAL 1 DAY),INTERVAL 17 HOUR),DATE_ADD(DATE_ADD(@d,INTERVAL 1 DAY),INTERVAL 18 HOUR),
 'BOOKED',NOW(),2,NULL,NULL),
(3,2,7,2,DATE_ADD(DATE_SUB(@d,INTERVAL 1 DAY),INTERVAL 17 HOUR),DATE_ADD(DATE_SUB(@d,INTERVAL 1 DAY),INTERVAL 18 HOUR),
 'BOOKED',DATE_SUB(@d,INTERVAL 2 DAY),2,NULL,NULL),
(4,2,7,1,DATE_ADD(DATE_SUB(@d,INTERVAL 3 DAY),INTERVAL 17 HOUR),DATE_ADD(DATE_SUB(@d,INTERVAL 3 DAY),INTERVAL 18 HOUR),
 'NO_SHOW',DATE_SUB(@d,INTERVAL 4 DAY),2,DATE_ADD(DATE_SUB(@d,INTERVAL 3 DAY),INTERVAL 18 HOUR),3);

INSERT INTO equipment (id,code,name,location,status,note,updated_at,updated_by) VALUES
(1,'TM01','Máy chạy bộ 01','Khu cardio','AVAILABLE','Kiểm tra mẫu',NOW(),1),
(2,'BK01','Xe đạp 01','Khu cardio','BROKEN','Dây đai bị trượt',NOW(),2);
COMMIT;
