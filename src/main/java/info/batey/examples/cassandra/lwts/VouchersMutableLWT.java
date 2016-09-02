package info.batey.examples.cassandra.lwts;

import com.datastax.driver.core.*;
import com.datastax.driver.core.exceptions.WriteTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class VouchersMutableLWT implements VoucherManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(VouchersMutableLWT.class);
    private Session session;
    private static final String GET_SOLD_VOUCHERS = "SELECT sold FROM vouchers_mutable WHERE name = ?";
    private static final String UPDATE_SOLD_VOUCHERS = "UPDATE vouchers_mutable SET sold = ? WHERE name = ? IF sold = ?";
    private static final String DELETE_VOUCHERS = "DELETE FROM vouchers_mutable WHERE name = ? IF EXISTS";

    private final PreparedStatement getSoldVouchers;
    private final PreparedStatement updateSoldVouchers;
    private final PreparedStatement deleteVoucher;

    VouchersMutableLWT(Session session) {
        this.session = session;
        getSoldVouchers = session.prepare(GET_SOLD_VOUCHERS);
        getSoldVouchers.setConsistencyLevel(ConsistencyLevel.LOCAL_SERIAL);

        updateSoldVouchers = session.prepare(UPDATE_SOLD_VOUCHERS);
        updateSoldVouchers.setConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM);
        updateSoldVouchers.setSerialConsistencyLevel(ConsistencyLevel.LOCAL_SERIAL);

        deleteVoucher = session.prepare(DELETE_VOUCHERS);
        deleteVoucher.setConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM);
        deleteVoucher.setSerialConsistencyLevel(ConsistencyLevel.LOCAL_SERIAL);
    }

    public boolean createVoucher(String name) {
        ResultSet result = session.execute("INSERT INTO vouchers_mutable (name, sold) VALUES (?, ?) IF NOT EXISTS", name, 0);
        return result.wasApplied();
    }

    @Override
    public boolean sellVoucher(String name, String who) throws UnknownException, CommitFailed {
        Row one = session.execute(getSoldVouchers.bind(name)).one();
        int sold = one.getInt("sold");
        try {
            if (session.execute(updateSoldVouchers.bind(sold + 1, name, sold)).wasApplied()) {
                return true;
            } else {
               return false;
            }
        } catch (WriteTimeoutException e) {
            LOGGER.warn("Failed to write", e);
            if (e.getWriteType().equals(WriteType.CAS)) {
                throw new UnknownException();
            } else if (e.getWriteType().equals(WriteType.SIMPLE)) {
                throw new CommitFailed();
            } else {
                throw new RuntimeException("Unexpected write type: " + e.getWriteType());
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
