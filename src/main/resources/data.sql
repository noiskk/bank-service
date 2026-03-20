-- 파일 위치: BANK 프로젝트의 src/main/resources/data.sql

-- 1. 정상 계좌 (잔액 100만원, 계좌 상태 ACTIVE) - 정상 체크카드 결제 테스트용
INSERT INTO account_table (account_num, amount, customer_id, card_num, account_status, minimum_balance, created_at)
VALUES ('1002-111-111111', 1000000, 100, 1234123412341234, 'ACTIVE', 0, NOW());

-- 2. 잔액 부족 테스트용 계좌 (잔액 5,000원) - 결제 실패 시나리오 테스트용
INSERT INTO account_table (account_num, amount, customer_id, card_num, account_status, minimum_balance, created_at)
VALUES ('1002-222-222222', 5000, 101, 4321432143214321, 'ACTIVE', 0, NOW());

-- 3. 정지된 계좌 (잔액 50만원) - 계좌 문제로 인한 결제 실패 테스트용
INSERT INTO account_table (account_num, amount, customer_id, card_num, account_status, minimum_balance, created_at)
VALUES ('1002-333-333333', 500000, 102, 5555555555555555, 'SUSPENDED', 0, NOW());