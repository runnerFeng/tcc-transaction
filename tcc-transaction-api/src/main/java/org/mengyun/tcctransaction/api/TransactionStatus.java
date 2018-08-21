package org.mengyun.tcctransaction.api;

/**
 * Created by changmingxie on 10/28/15.
 */
public enum TransactionStatus {
    /**
     * 尝试中状态
     */
    TRYING(1),
    /**
     * 确认中状态
     */
    CONFIRMING(2),
    /**
     * 取消中状态
     */
    CANCELLING(3);

    private int id;

    TransactionStatus(int id) {
        this.id = id;
    }

    public static TransactionStatus valueOf(int id) {
        for (TransactionStatus transactionStatus : TransactionStatus.values()) {
            if (transactionStatus.getId() == id) {
                return transactionStatus;
            }
        }
        return null;
    }

    public int getId() {
        return id;
    }

}
