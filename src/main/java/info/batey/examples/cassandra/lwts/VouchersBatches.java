package info.batey.examples.cassandra.lwts;

import com.datastax.driver.core.*;

public class VouchersBatches implements VoucherManager {

    private static final int MAX_VOUCHERS = 3;

    private Session session;
    private static final String GET_SOLD_VOUCHERS = "SELECT sold FROM vouchers WHERE name = ?";
    private static final String INSERT_SOLD_VOUCHERS = "BEGIN BATCH \n" +
            "    UPDATE vouchers SET sold = ? WHERE name = ? IF sold = ?\n" +
            "    INSERT INTO vouchers (name, when, who) VALUES ( ?, now(), ?) \n" +
            "APPLY BATCH";
    private final PreparedStatement getSoldVouchers;
    private final PreparedStatement updateSoldVouchers;

    public VouchersBatches(Session session) {
        this.session = session;

        getSoldVouchers = session.prepare(GET_SOLD_VOUCHERS);
        getSoldVouchers.setConsistencyLevel(ConsistencyLevel.LOCAL_SERIAL);

        updateSoldVouchers = session.prepare(INSERT_SOLD_VOUCHERS);
        updateSoldVouchers.setConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM);
        updateSoldVouchers.setSerialConsistencyLevel(ConsistencyLevel.LOCAL_SERIAL);
    }

    @Override
    public boolean createVoucher(String name) {
        ResultSet result = session.execute("INSERT INTO vouchers (name, sold) VALUES (?, ?) IF NOT EXISTS", name, 0);
        return result.wasApplied();
    }

    @Override
    public boolean sellVoucher(String name, String who) {
        Row one = session.execute(getSoldVouchers.bind(name)).one();
        int sold = one.getInt("sold");
        if (sold < MAX_VOUCHERS) {
           session.execute(updateSoldVouchers.bind(sold + 1, name, sold, name, who));
            return true;
        } else {
            return false;
        }
    }
}
