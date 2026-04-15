package com.example.payment.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.payment.domain.enumtype.EscrowStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Escrow 엔티티 테스트")
class EscrowTest {

    private UUID escrowId;
    private UUID orderId;
    private UUID buyerMemberId;
    private UUID sellerMemberId;
    private LocalDateTime createdAt;
    private LocalDateTime releaseAt;

    @BeforeEach
    void setUp() {
        escrowId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        buyerMemberId = UUID.randomUUID();
        sellerMemberId = UUID.randomUUID();
        createdAt = LocalDateTime.of(2024, 1, 1, 10, 0, 0);
        releaseAt = createdAt.plusDays(7);
    }

    @Nested
    @DisplayName("Escrow.createHeld() 생성 테스트")
    class CreateHeld {

        @Test
        @DisplayName("createHeld() 생성 시 HELD 상태와 기본 필드가 저장된다")
        void createHeld_storesFieldsCorrectly() {
            Escrow escrow = Escrow.createHeld(escrowId, orderId, buyerMemberId, sellerMemberId, 12_000L, releaseAt, createdAt);

            assertThat(escrow.getEscrowId()).isEqualTo(escrowId);
            assertThat(escrow.getOrderId()).isEqualTo(orderId);
            assertThat(escrow.getBuyerMemberId()).isEqualTo(buyerMemberId);
            assertThat(escrow.getSellerMemberId()).isEqualTo(sellerMemberId);
            assertThat(escrow.getAmount()).isEqualTo(12_000L);
            assertThat(escrow.getEscrowStatus()).isEqualTo(EscrowStatus.HELD);
            assertThat(escrow.getReleaseAt()).isEqualTo(releaseAt);
            assertThat(escrow.getCreatedAt()).isEqualTo(createdAt);
            assertThat(escrow.getUpdatedAt()).isEqualTo(createdAt);
            assertThat(escrow.isHeld()).isTrue();
        }

        @Test
        @DisplayName("createHeld() 생성 시 releaseAt은 null일 수 있다")
        void createHeld_releaseAtCanBeNull() {
            Escrow escrow = Escrow.createHeld(escrowId, orderId, buyerMemberId, sellerMemberId, 12_000L, null, createdAt);

            assertThat(escrow.getReleaseAt()).isNull();
            assertThat(escrow.getEscrowStatus()).isEqualTo(EscrowStatus.HELD);
        }

        @Test
        @DisplayName("0원으로 createHeld() 생성 시 예외가 발생한다")
        void createHeld_zeroAmount_throwsException() {
            assertThatThrownBy(() -> Escrow.createHeld(escrowId, orderId, buyerMemberId, sellerMemberId, 0L, releaseAt, createdAt))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Escrow amount must be positive.");
        }
    }

    @Nested
    @DisplayName("Escrow.release() 테스트")
    class Release {

        @Test
        @DisplayName("HELD 상태에서 RELEASED로 전이된다")
        void release_heldEscrow_changesStatus() {
            Escrow escrow = Escrow.createHeld(escrowId, orderId, buyerMemberId, sellerMemberId, 12_000L, releaseAt, createdAt);
            LocalDateTime releasedAt = createdAt.plusDays(3);

            escrow.release(releasedAt, releasedAt);

            assertThat(escrow.getEscrowStatus()).isEqualTo(EscrowStatus.RELEASED);
            assertThat(escrow.getReleasedAt()).isEqualTo(releasedAt);
            assertThat(escrow.getUpdatedAt()).isEqualTo(releasedAt);
            assertThat(escrow.isReleased()).isTrue();
        }

        @Test
        @DisplayName("이미 RELEASED 상태면 다시 release() 할 수 없다")
        void release_releasedEscrow_throwsException() {
            Escrow escrow = Escrow.createHeld(escrowId, orderId, buyerMemberId, sellerMemberId, 12_000L, releaseAt, createdAt);
            escrow.release(createdAt.plusDays(3), createdAt.plusDays(3));

            assertThatThrownBy(() -> escrow.release(createdAt.plusDays(4), createdAt.plusDays(4)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only held escrow can be changed.");
        }
    }

    @Nested
    @DisplayName("Escrow.refund() 테스트")
    class Refund {

        @Test
        @DisplayName("HELD 상태에서 REFUNDED로 전이된다")
        void refund_nonZeroBalanceHeldEscrow_throwsException() {
            Escrow escrow = Escrow.createHeld(escrowId, orderId, buyerMemberId, sellerMemberId, 12_000L, releaseAt, createdAt);
            LocalDateTime refundedAt = createdAt.plusDays(1);

            assertThatThrownBy(() -> escrow.refund(refundedAt, refundedAt))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("zero-balance");
        }

        @Test
        @DisplayName("이미 REFUNDED 상태면 다시 refund() 할 수 없다")
        void refund_refundedEscrow_throwsException() {
            Escrow escrow = Escrow.createHeld(escrowId, orderId, buyerMemberId, sellerMemberId, 12_000L, releaseAt, createdAt);
            escrow.applyRefundAmount(12_000L, createdAt.plusDays(1), createdAt.plusDays(1));

            assertThatThrownBy(() -> escrow.refund(createdAt.plusDays(2), createdAt.plusDays(2)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only held escrow can be changed.");
        }
    }

    @Nested
    @DisplayName("Escrow.scheduleReleaseAt() 테스트")
    class ScheduleReleaseAt {

        @Test
        @DisplayName("HELD 상태에서 releaseAt을 설정할 수 있다")
        void scheduleReleaseAt_heldEscrow_updatesReleaseAt() {
            Escrow escrow = Escrow.createHeld(escrowId, orderId, buyerMemberId, sellerMemberId, 12_000L, null, createdAt);
            LocalDateTime scheduledAt = createdAt.plusDays(7);

            escrow.scheduleReleaseAt(scheduledAt, createdAt.plusDays(1));

            assertThat(escrow.getReleaseAt()).isEqualTo(scheduledAt);
            assertThat(escrow.getUpdatedAt()).isEqualTo(createdAt.plusDays(1));
            assertThat(escrow.isReleaseScheduled()).isTrue();
        }

        @Test
        @DisplayName("이미 releaseAt이 있으면 다시 설정할 수 없다")
        void scheduleReleaseAt_alreadyScheduled_throwsException() {
            Escrow escrow = Escrow.createHeld(escrowId, orderId, buyerMemberId, sellerMemberId, 12_000L, null, createdAt);
            escrow.scheduleReleaseAt(createdAt.plusDays(7), createdAt.plusDays(1));

            assertThatThrownBy(() -> escrow.scheduleReleaseAt(createdAt.plusDays(8), createdAt.plusDays(2)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already scheduled");
        }
    }
}
