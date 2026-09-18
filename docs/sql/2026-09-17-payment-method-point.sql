-- PostgreSQL: Hibernate ddl-auto=update does not extend existing enum CHECK constraints.
-- Preserve existing payment methods and allow verified point payments.
BEGIN;
SET LOCAL lock_timeout = '5s';
ALTER TABLE reservation_payment DROP CONSTRAINT IF EXISTS reservation_payment_method_check;
ALTER TABLE reservation_payment ADD CONSTRAINT reservation_payment_method_check
    CHECK (method IN ('CARD', 'KAKAO_PAY', 'NAVER_PAY', 'POINT'));
COMMIT;
