package info.batey.examples.cassandra.lwts;

import com.datastax.driver.core.*;

class VouchersMutable implements VoucherManager {


    private Session session;
    private static final String GET_SOLD_VOUCHERS = "SELECT sold FROM vouchers_mutable WHERE name = ?";
    private static final String UPDATE_SOLD_VOUCHERS = "UPDATE vouchers_mutable SET sold = ? WHERE name = ?";
    private static final String DELETE_VOUCHERS = "DELETE FROM vouchers_mutable WHERE name = ?";

    private final PreparedStatement getSoldVouchers;
    private final PreparedStatement updateSoldVouchers;
    private final PreparedStatement deleteVoucher;

    VouchersMutable(Session session) {
        this.session = session;
        getSoldVouchers = session.prepare(GET_SOLD_VOUCHERS);
        getSoldVouchers.setConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM);

        updateSoldVouchers = session.prepare(UPDATE_SOLD_VOUCHERS);
        updateSoldVouchers.setConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM);

        deleteVoucher = session.prepare(DELETE_VOUCHERS);
        deleteVoucher.setConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM);
    }

    @Override
    public boolean createVoucher(String name) {
        ResultSet result = session.execute("INSERT INTO vouchers_mutable (name, sold) VALUES (?, ?)", name, 0);
        return result.wasApplied();
    }

    @Override
    public boolean sellVoucher(String name, String who) {
        Row one = session.execute(getSoldVouchers.bind(name)).one();
        int sold = one.getInt("sold");
        session.execute(updateSoldVouchers.bind(sold + 1, name));
        return true;
    }

    @Override
    public int vouchersSold(String name) {
        return session.execute(getSoldVouchers.bind(name)).one().getInt("sold");
    }

    @Override
    public void deleteVoucher(String name) {
        session.execute(deleteVoucher.bind(name));
    }
}
