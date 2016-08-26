package info.batey.examples.cassandra.lwts;

public interface VoucherManager {
    boolean createVoucher(String name);

    boolean sellVoucher(String name, String who);
}
