package info.batey.examples.cassandra.lwts;

import com.datastax.driver.core.*;

public class VouchersMutableLWT implements VoucherManager {

    private static final int MAX_VOUCHERS = 3;

    private Session session;
    private static final String GET_SOLD_VOUCHERS = "SELECT sold FROM vouchers_mutable WHERE name = ?";
    private static final String UPDATE_SOLD_VOUCHERS = "UPDATE vouchers_mutable SET sold = ? WHERE name = ? IF sold = ?";
    private final PreparedStatement getSoldVouchers;
    private final PreparedStatement updateSoldVouchers;

    public VouchersMutableLWT(Session session) {
        this.session = session;
        getSoldVouchers = session.prepare(GET_SOLD_VOUCHERS);
        getSoldVouchers.setConsistencyLevel(ConsistencyLevel.LOCAL_SERIAL);

        updateSoldVouchers = session.prepare(UPDATE_SOLD_VOUCHERS);
        updateSoldVouchers.setConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM);
        updateSoldVouchers.setSerialConsistencyLevel(ConsistencyLevel.LOCAL_SERIAL);
    }

    public boolean createVoucher(String name) {
        ResultSet result = session.execute("INSERT INTO vouchers_mutable (name, sold) VALUES (?, ?) IF NOT EXISTS", name, 0);
        return result.wasApplied();
    }

    public boolean sellVoucher(String name, String who) {
        Row one = session.execute(getSoldVouchers.bind(name)).one();
        int sold = one.getInt("sold");
        if (sold < MAX_VOUCHERS) {
           session.execute(updateSoldVouchers.bind(sold + 1, name, sold));
            return true;
        } else {
            return false;
        }
    }
}
