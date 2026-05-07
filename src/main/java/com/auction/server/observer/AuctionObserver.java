package com.auction.server.observer;

import com.auction.common.dto.AuctionUpdateDTO;

public interface AuctionObserver {
    void onAuctionUpdate(AuctionUpdateDTO update);
}
