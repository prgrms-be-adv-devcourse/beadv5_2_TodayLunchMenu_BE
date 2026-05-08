import http from 'k6/http';
import { BASE_URL } from '../config/thresholds.js';

export function fetchCurrentBidPrice(auctionId) {
  const res = http.get(`${BASE_URL}/api/auctions/${auctionId}`, {
    headers: { 'Content-Type': 'application/json' },
  });
  if (res.status !== 200) return null;
  const data = res.json('data');
  if (!data) return null;
  const current = data.currentHighestPrice ?? 0;
  return Math.max(data.startPrice, current + data.bidUnit);
}

/**
 * 경매 상세 조회 후 { minBidPrice, bidUnit, startPrice } 반환.
 * 호출자가 bidUnit 배수를 더해 입찰가를 다양화할 수 있도록 bidUnit을 함께 노출한다.
 * 조회 실패 시 null 반환.
 */
export function fetchAuctionState(auctionId) {
  const res = http.get(`${BASE_URL}/api/auctions/${auctionId}`, {
    headers: { 'Content-Type': 'application/json' },
  });
  if (res.status !== 200) return null;
  const data = res.json('data');
  if (!data) return null;
  const current = data.currentHighestPrice ?? 0;
  return {
    startPrice: data.startPrice,
    bidUnit: data.bidUnit,
    minBidPrice: Math.max(data.startPrice, current + data.bidUnit),
  };
}
