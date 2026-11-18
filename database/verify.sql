-- Read-only checks after seed; each anomaly query must return 0 rows.
SELECT m.id AS membership_without_payment
FROM memberships m LEFT JOIN payments p ON p.membership_id=m.id
WHERE p.id IS NULL;

SELECT p.id AS amount_mismatch
FROM payments p JOIN memberships m ON m.id=p.membership_id
WHERE p.amount_vnd<>m.price_vnd_snapshot;

SELECT a.id AS first_id,b.id AS second_id
FROM memberships a JOIN memberships b
 ON a.member_id=b.member_id AND a.id<b.id
 AND a.kind_snapshot='GYM' AND b.kind_snapshot='GYM'
 AND a.start_date<b.end_date AND b.start_date<a.end_date;

SELECT member_id,COUNT(*) AS open_count FROM visits
WHERE check_out_at IS NULL GROUP BY member_id HAVING COUNT(*)>1;

-- Further anomalies: each query should return zero rows.
SELECT b.id FROM pt_bookings b JOIN memberships m ON m.id=b.membership_id
WHERE m.kind_snapshot<>'PT' OR m.member_id<>b.member_id
 OR DATE(b.start_at)<m.start_date OR DATE(b.start_at)>=m.end_date;
SELECT v.id FROM visits v JOIN memberships m ON m.id=v.membership_id
WHERE m.kind_snapshot<>'GYM' OR m.member_id<>v.member_id;
SELECT a.id,b.id FROM pt_bookings a JOIN pt_bookings b ON a.id<b.id
 AND a.status='BOOKED' AND b.status='BOOKED'
 AND (a.trainer_id=b.trainer_id OR a.member_id=b.member_id)
 AND a.start_at<b.end_at AND b.start_at<a.end_at;
SELECT m.id FROM memberships m LEFT JOIN pt_bookings b ON b.membership_id=m.id
WHERE m.kind_snapshot='PT' GROUP BY m.id,m.sessions_total_snapshot
HAVING SUM(CASE WHEN b.status IN ('BOOKED','COMPLETED') THEN 1 ELSE 0 END)>m.sessions_total_snapshot;

-- Immediately after all bootstrap/seed: 4 users, 2 trainers, 6 members,
-- 5 plans, 8 memberships, 8 payments, 3 visits (1 open),
-- 5 availability rows, 4 bookings (2 BOOKED/1 COMPLETED/1 NO_SHOW), 2 equipment.
-- Total paid 4700000. PT membership 7: total4 used1 reserved2 available1.
SELECT SUM(amount_vnd) AS total_paid_vnd FROM payments;
SELECT status,COUNT(*) AS count_by_status FROM pt_bookings GROUP BY status;
SELECT m.id,m.sessions_total_snapshot,
 SUM(CASE WHEN b.status='COMPLETED' THEN 1 ELSE 0 END) AS used,
 SUM(CASE WHEN b.status='BOOKED' THEN 1 ELSE 0 END) AS reserved,
 m.sessions_total_snapshot-SUM(CASE WHEN b.status IN ('BOOKED','COMPLETED') THEN 1 ELSE 0 END) AS available
FROM memberships m LEFT JOIN pt_bookings b ON b.membership_id=m.id
WHERE m.kind_snapshot='PT' GROUP BY m.id,m.sessions_total_snapshot;
