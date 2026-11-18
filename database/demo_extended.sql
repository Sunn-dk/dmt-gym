-- Optional extended demo fixture layered ON TOP of database/seed.sql (F11 / QA-R09).
-- Run ONLY on a fresh demo DB after base seed.sql has been loaded cleanly.
-- Keeps base seed totals verifiable before running (4,700,000 VND -> +2,600,000 VND = 7,300,000 VND).
SET time_zone = '+07:00';
SET @d = CURRENT_DATE;
START TRANSACTION;

INSERT INTO members (id,full_name,phone,birth_date,active,created_at,created_by) VALUES
(7,'Phạm Gần Hết Hạn','0900000007','1997-04-12',TRUE,NOW(),1),
(8,'Lê Gia Hạn Xa','0900000008','1996-09-20',TRUE,NOW(),1),
(9,'Trần Hết Quyền PT','0900000009','1995-11-05',TRUE,NOW(),1),
(10,'Đỗ Hết Hạn Hôm Nay','0900000010','1999-02-14',TRUE,NOW(),1);

INSERT INTO memberships
(id,member_id,plan_id,sale_type,request_key,plan_name_snapshot,kind_snapshot,duration_days_snapshot,sessions_total_snapshot,
 price_vnd_snapshot,start_date,end_date,sold_at,sold_by) VALUES
-- HV-7: GYM 5 ngay [D-2, D+3) -> lastUsable = D+2 (con 2 ngay -> hien tren bang nhac 7 ngay)
(9,7,1,'PURCHASE','00000000-0000-4000-8000-000000000009','Gym 5 ngày mẫu','GYM',5,NULL,100000,
 DATE_SUB(@d,INTERVAL 2 DAY),DATE_ADD(@d,INTERVAL 3 DAY),DATE_SUB(@d,INTERVAL 2 DAY),2),
-- HV-8: GYM 5 ngay [D-2, D+3) + Gia han GYM 30 ngay [D+3, D+33) -> MAX(end_date) = D+33 -> KHONG nhac 7 ngay
(10,8,1,'PURCHASE','00000000-0000-4000-8000-000000000010','Gym 5 ngày mẫu','GYM',5,NULL,100000,
 DATE_SUB(@d,INTERVAL 2 DAY),DATE_ADD(@d,INTERVAL 3 DAY),DATE_SUB(@d,INTERVAL 2 DAY),2),
(11,8,1,'RENEWAL','00000000-0000-4000-8000-000000000011','Gym 30 ngày','GYM',30,NULL,300000,
 DATE_ADD(@d,INTERVAL 3 DAY),DATE_ADD(@d,INTERVAL 33 DAY),DATE_SUB(@d,INTERVAL 1 DAY),2),
-- HV-9: GYM [D-5, D+25) + GYM tuong lai SCHEDULED [D+25, D+55) + PT 4 buoi [D-5, D+25) se het quyen dat
(12,9,1,'PURCHASE','00000000-0000-4000-8000-000000000012','Gym 30 ngày','GYM',30,NULL,300000,
 DATE_SUB(@d,INTERVAL 5 DAY),DATE_ADD(@d,INTERVAL 25 DAY),DATE_SUB(@d,INTERVAL 5 DAY),2),
(13,9,1,'RENEWAL','00000000-0000-4000-8000-000000000013','Gym 30 ngày','GYM',30,NULL,300000,
 DATE_ADD(@d,INTERVAL 25 DAY),DATE_ADD(@d,INTERVAL 55 DAY),NOW(),2),
(14,9,4,'PURCHASE','00000000-0000-4000-8000-000000000014','PT 4 buổi','PT',30,4,1200000,
 DATE_SUB(@d,INTERVAL 5 DAY),DATE_ADD(@d,INTERVAL 25 DAY),DATE_SUB(@d,INTERVAL 5 DAY),2),
-- HV-10: GYM [D-29, D+1) -> lastUsable = D (bien het han hom nay -> 0 ngay con lai tren bang nhac)
(15,10,1,'PURCHASE','00000000-0000-4000-8000-000000000015','Gym 30 ngày','GYM',30,NULL,300000,
 DATE_SUB(@d,INTERVAL 29 DAY),DATE_ADD(@d,INTERVAL 1 DAY),DATE_SUB(@d,INTERVAL 29 DAY),2);

INSERT INTO payments (id,membership_id,amount_vnd,method,paid_at,collected_by)
SELECT id,id,price_vnd_snapshot,'CASH',sold_at,sold_by FROM memberships WHERE id BETWEEN 9 AND 15;

-- 1 visit cho HV-9 tai D-2 09:50-11:10 khop voi buoi COMPLETED BK-5 ben duoi
INSERT INTO visits (id,member_id,membership_id,check_in_at,check_out_at,check_in_by,check_out_by) VALUES
(4,9,12,DATE_ADD(DATE_SUB(@d,INTERVAL 2 DAY),INTERVAL 590 MINUTE),
 DATE_ADD(DATE_SUB(@d,INTERVAL 2 DAY),INTERVAL 670 MINUTE),2,2);

-- HV-9 PT #14: total=4, used=1 (BK-5), reserved=3 (BK-6,7,8), available=0 + 1 lich CANCELLED (BK-9)
-- BK-6 qua gio nhung KHONG co visit giao gio -> dung de demo chan NO_ATTENDANCE
INSERT INTO pt_bookings
(id,member_id,membership_id,trainer_id,start_at,end_at,status,created_at,created_by,resolved_at,resolved_by) VALUES
(5,9,14,1,DATE_ADD(DATE_SUB(@d,INTERVAL 2 DAY),INTERVAL 10 HOUR),DATE_ADD(DATE_SUB(@d,INTERVAL 2 DAY),INTERVAL 11 HOUR),
 'COMPLETED',DATE_SUB(@d,INTERVAL 3 DAY),2,DATE_ADD(DATE_SUB(@d,INTERVAL 2 DAY),INTERVAL 11 HOUR),3),
(6,9,14,2,DATE_ADD(DATE_SUB(@d,INTERVAL 1 DAY),INTERVAL 10 HOUR),DATE_ADD(DATE_SUB(@d,INTERVAL 1 DAY),INTERVAL 11 HOUR),
 'BOOKED',DATE_SUB(@d,INTERVAL 2 DAY),2,NULL,NULL),
(7,9,14,1,DATE_ADD(DATE_ADD(@d,INTERVAL 1 DAY),INTERVAL 10 HOUR),DATE_ADD(DATE_ADD(@d,INTERVAL 1 DAY),INTERVAL 11 HOUR),
 'BOOKED',NOW(),2,NULL,NULL),
(8,9,14,2,DATE_ADD(DATE_ADD(@d,INTERVAL 1 DAY),INTERVAL 11 HOUR),DATE_ADD(DATE_ADD(@d,INTERVAL 1 DAY),INTERVAL 12 HOUR),
 'BOOKED',NOW(),2,NULL,NULL),
(9,9,14,2,DATE_ADD(DATE_ADD(@d,INTERVAL 1 DAY),INTERVAL 14 HOUR),DATE_ADD(DATE_ADD(@d,INTERVAL 1 DAY),INTERVAL 15 HOUR),
 'CANCELLED',NOW(),2,NOW(),2);

COMMIT;
