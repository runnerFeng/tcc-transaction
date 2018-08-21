package org.mengyun.tcctransaction.repository;

import org.mengyun.tcctransaction.core.Transaction;
import org.mengyun.tcctransaction.api.TransactionXid;

import java.util.Date;
import java.util.List;

/**
 * Created by changmingxie on 11/12/15.
 */
public interface TransactionRepository {
    /**
     * 新增事务
     *
     * @param transaction
     * @return
     */
    int create(Transaction transaction);

    /**
     * 更新事务
     * @param transaction
     * @return
     */
    int update(Transaction transaction);

    /**
     * 删除事务
     * @param transaction
     * @return
     */
    int delete(Transaction transaction);

    /**
     * 获取事务
     * @param xid
     * @return
     */
    Transaction findByXid(TransactionXid xid);

    /**
     * 获取超过指定时间的事务集合
     * @param date
     * @return
     */
    List<Transaction> findAllUnmodifiedSince(Date date);
}
