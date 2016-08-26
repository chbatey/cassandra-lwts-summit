package info.batey.examples.cassandra.lwts;

interface VoucherManager {
    boolean createVoucher(String name);

    boolean sellVoucher(String name, String who) throws UnknownException, CommitFailed;

    int vouchersSold(String name);

    void deleteVoucher(String name);


    class UnknownException extends Exception {}
    class CommitFailed extends Exception {}
}
