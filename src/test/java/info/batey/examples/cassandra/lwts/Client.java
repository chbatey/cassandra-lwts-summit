package info.batey.examples.cassandra.lwts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

class Client extends Thread {

    private static final Logger LOG = LoggerFactory.getLogger(Client.class);

    private VoucherManager vm;
    private final int vouchersToBuy;
    private String name;
    private String who;
    private CountDownLatch cdl;
    private CountDownLatch finish;
    private int vouchersBought;
    private int vouchersFailed;
    private int unknowns;
    private int commitFailed;

    Client(VoucherManager vm, int vouchersToBuy, String name, String who, CountDownLatch cdl, CountDownLatch finish) {
        this.vm = vm;
        this.vouchersToBuy = vouchersToBuy;
        this.name = name;
        this.who = who;
        this.cdl = cdl;
        this.finish = finish;
        this.vouchersFailed = 0;
    }

    @Override
    public void run() {
        try {
            cdl.countDown();
            cdl.await();
            LOG.info("Starting");
            for (int i = 0; i < vouchersToBuy; i++) {
                try {
                    if (vm.sellVoucher(name, who)) {
                        vouchersBought++;
                    } else {
                        vouchersFailed++;
                    }
                } catch (VoucherManager.UnknownException u) {
                    unknowns++;
                } catch (VoucherManager.CommitFailed e) {
                    commitFailed++;
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            LOG.info("Finished");
            finish.countDown();
        }
    }

    int getVouchersBought() {
        LOG.info("Client {} has bought {} vouchers", who, vouchersBought);
        return vouchersBought;
    }

    int getVouchersFailed() {
        LOG.info("Client {} has had {} failures", who, vouchersFailed);
        return vouchersFailed;
    }

    int getUnknowns() {
        LOG.info("Client {} has {} unknowns", who, unknowns);
        return unknowns;
    }

    public int getCommitFailed() {
        LOG.info("Client {} has {} commit failed", who, commitFailed);
        return commitFailed;
    }
}
