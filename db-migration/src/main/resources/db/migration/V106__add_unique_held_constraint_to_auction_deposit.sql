-- 한 경매에 HELD 상태의 보증금은 동시에 1건만 존재 가능하도록 강제.
-- 동시 입찰 시 두 번째 HELD INSERT를 DB 레벨에서 차단 → NonUniqueResultException 방지.

-- 기존 데이터에 중복 HELD가 있으면 부분 유니크 인덱스 생성이 실패하므로 사전 정리.
-- 우선순위: 보증금이 큰 입찰(= 입찰가 큰 입찰) → 동률이면 먼저 들어온 입찰.
-- 근거: Auction.isHigherThanCurrentBid 는 strictly greater 이므로 동일 가격은 ACTIVE 미획득 → 먼저 처리된 쪽이 ACTIVE.
WITH ranked_helds AS (
    SELECT auction_deposit_id,
           ROW_NUMBER() OVER (
               PARTITION BY auction_id
               ORDER BY deposit_amount DESC, created_at ASC
           ) AS rn
    FROM payment.auction_deposit
    WHERE status = 'HELD'
)
UPDATE payment.auction_deposit
SET status = 'REFUNDED',
    updated_at = NOW()
WHERE auction_deposit_id IN (
    SELECT auction_deposit_id FROM ranked_helds WHERE rn > 1
);

-- 부분 유니크 인덱스: status='HELD'인 행에 대해서만 auction_id 유니크 보장.
CREATE UNIQUE INDEX IF NOT EXISTS uk_auction_deposit_auction_id_held
    ON payment.auction_deposit (auction_id)
    WHERE status = 'HELD';
