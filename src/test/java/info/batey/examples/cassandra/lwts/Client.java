package info.batey.examples.cassandra.lwts;

import org.HdrHistogram.Histogram;
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

    private Histogram histogram = new Histogram(3600000000000L, 3);

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
                long start = System.nanoTime();
                try {
                    boolean sold = vm.sellVoucher(name, who);
                    if (sold) {
                        vouchersBought++;
                    } else {
                        vouchersFailed++;
                    }
                } catch (VoucherManager.UnknownException u) {
                    unknowns++;
                } catch (VoucherManager.CommitFailed e) {
                    commitFailed++;
                } finally {
                    histogram.recordValue(System.nanoTime() - start);
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
        LOG.debug("Client {} has bought {} vouchers", who, vouchersBought);
        return vouchersBought;
    }

    int getVouchersFailed() {
        LOG.debug("Client {} has had {} failures", who, vouchersFailed);
        return vouchersFailed;
    }

    int getUnknowns() {
        LOG.debug("Client {} has {} unknowns", who, unknowns);
        return unknowns;
    }

    int getCommitFailed() {
        LOG.debug("Client {} has {} commit failed", who, commitFailed);
        return commitFailed;
    }

    public Histogram getHistogram() {
        return histogram;
    }
}
