package info.batey.examples.cassandra.lwts;

import com.datastax.driver.core.*;
import com.datastax.driver.core.exceptions.WriteTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class VouchersBatches implements VoucherManager {

    private static final Logger LOG = LoggerFactory.getLogger(VouchersBatches.class);

    private Session session;
    private static final String GET_SOLD_VOUCHERS = "SELECT sold FROM vouchers WHERE name = ?";
    private static final String DELETE_VOUCHER = "DELETE  FROM vouchers WHERE name = ?";
    private static final String INSERT_SOLD_VOUCHERS = "BEGIN BATCH \n" +
            "    UPDATE vouchers SET sold = ? WHERE name = ? IF sold = ?\n" +
            "    INSERT INTO vouchers (name, when, who) VALUES ( ?, now(), ?) \n" +
            "APPLY BATCH";
    private final PreparedStatement getSoldVouchers;
    private final PreparedStatement updateSoldVouchers;
    private final PreparedStatement deleteVoucher;

    VouchersBatches(Session session) {
        this.session = session;

        getSoldVouchers = session.prepare(GET_SOLD_VOUCHERS);
        getSoldVouchers.setConsistencyLevel(ConsistencyLevel.LOCAL_SERIAL);

        updateSoldVouchers = session.prepare(INSERT_SOLD_VOUCHERS);
        updateSoldVouchers.setConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM);
        updateSoldVouchers.setSerialConsistencyLevel(ConsistencyLevel.LOCAL_SERIAL);

        deleteVoucher = session.prepare(DELETE_VOUCHER);
        deleteVoucher.setConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM);
        deleteVoucher.setSerialConsistencyLevel(ConsistencyLevel.LOCAL_SERIAL);
    }

    @Override
    public boolean createVoucher(String name) {
        ResultSet result = session.execute("INSERT INTO vouchers (name, sold) VALUES (?, ?) IF NOT EXISTS", name, 0);
        return result.wasApplied();
    }

    @Override
    public boolean sellVoucher(String name, String who) throws UnknownException, CommitFailed {
        Row one = session.execute(getSoldVouchers.bind(name)).one();
        int sold = one.getInt("sold");
        try {
            ResultSet execute = session.execute(updateSoldVouchers.bind(sold + 1, name, sold, name, who));
            if (execute.wasApplied()) {
                return true;
            } else {
                LOG.info("Not applied {}", execute.one());
                return false;
            }
        } catch (WriteTimeoutException e) {
            LOG.warn("Write failed ", e);
            if (e.getWriteType().equals(WriteType.CAS)) {
                throw new UnknownException();
            } else {
                if (e.getWriteType().equals(WriteType.SIMPLE)) {
                    throw new CommitFailed();
                } else {
                    throw new RuntimeException("Unexpected write type: " + e.getWriteType());
                }
            }
        }
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
