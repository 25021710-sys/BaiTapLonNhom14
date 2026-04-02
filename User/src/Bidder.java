import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

public class Bidder extends User implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private BigDecimal walletBalance;
    private int totalBidsPlaced;
    private int auctionsWon;

    public Bidder(){
        super(username, passwordHash, email, fullName, UserRole.BIDDER);
        this.walletBalance = BigDecimal.ZERO;
        this.totalBidsPlaced = 0;
        this.auctionsWon = 0;
    }

    public Bidder(String username, String passwordHash, String email, String fullName){
        super(username, passwordHash, email, fullName, UserRole.BIDDER);
        this.walletBalance = BigDecimal.ZERO;
        this.totalBidsPlaced = 0;
        this.auctionsWon = 0;
    }

    @Override
    public boolean canBid(){
        return true;
    }

    @Override
    public boolean canSell(){
        return false;
    }

    @Override
    public boolean canManageSystem(){
        return false;
    }

    @Override
    public String getDisplayInfo(){
        return String.format("[BIDDER] %s | Ví: %s VNĐ | Đã thắng: %d",
                getFullName(), walletBalance.toPlainString(), auctionsWon);
    }

    public BigDecimal getWalletBalance(){ return walletBalance;}
    public void setWalletBalance(BigDecimal walletBalance){this.walletBalance = walletBalance;}

    public int getTotalBidsPlaced(){ return totalBidsPlaced;}
    public void setTotalBidsPlaced(int totalBidsPlaced){ this.totalBidsPlaced = totalBidsPlaced;}

    public int getAuctionsWon(){ return auctionsWon;}
    public void setAuctionsWon(int auctionsWon){ this.auctionsWon = auctionsWon;}

    public void incrementBidCount(){ this.totalBidsPlaced++;}
    public void incrementWonCount(){ this.auctionsWon++;}
}