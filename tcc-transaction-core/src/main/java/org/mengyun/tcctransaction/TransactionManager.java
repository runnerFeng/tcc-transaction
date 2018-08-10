package org.mengyun.tcctransaction;

import org.apache.log4j.Logger;
import org.mengyun.tcctransaction.api.TransactionContext;
import org.mengyun.tcctransaction.api.TransactionStatus;
import org.mengyun.tcctransaction.common.TransactionType;

import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;

/**
 * Created by changmingxie on 10/26/15.
 */
public class TransactionManager {

    static final Logger logger = Logger.getLogger(TransactionManager.class.getSimpleName());
    // 当前线程事务队列
    private static final ThreadLocal<Deque<Transaction>> CURRENT = new ThreadLocal<>();
    private TransactionRepository transactionRepository;
    private ExecutorService executorService;

    public TransactionManager() {
    }

    public void setTransactionRepository(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    /**
     * 发起根事务
     *
     * @return
     */
    public Transaction begin() {
        // 创建根事务
        Transaction transaction = new Transaction(TransactionType.ROOT);
        // 存储事务
        transactionRepository.create(transaction);
        // 注册事务
        registerTransaction(transaction);
        return transaction;
    }

    /**
     * 传播发起分支事务
     *
     * @param transactionContext
     * @return
     */
    public Transaction propagationNewBegin(TransactionContext transactionContext) {
        // 创建分支事务
        Transaction transaction = new Transaction(transactionContext);
        // 存储事务
        transactionRepository.create(transaction);
        // 注册事务
        registerTransaction(transaction);
        return transaction;
    }

    /**
     * 传播获取分支事务
     *
     * @param transactionContext
     * @return
     * @throws NoExistedTransactionException
     */
    public Transaction propagationExistBegin(TransactionContext transactionContext) throws NoExistedTransactionException {
        // 查询事务
        Transaction transaction = transactionRepository.findByXid(transactionContext.getXid());

        if (transaction != null) {
            // 设置状态
            transaction.changeStatus(TransactionStatus.valueOf(transactionContext.getStatus()));
            // 注册事务
            registerTransaction(transaction);
            return transaction;
        } else {
            throw new NoExistedTransactionException();
        }
    }

    /**
     * 提交事务
     *
     * @param asyncCommit
     */
    public void commit(boolean asyncCommit) {
        // 获取事务
        final Transaction transaction = getCurrentTransaction();
        // 设置事务状态为CONFIRMING
        transaction.changeStatus(TransactionStatus.CONFIRMING);
        // 更新事务
        transactionRepository.update(transaction);
        // 提交事务
        if (asyncCommit) {
            try {
                Long statTime = System.currentTimeMillis();

                executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        commitTransaction(transaction);
                    }
                });
                logger.debug("async submit cost time:" + (System.currentTimeMillis() - statTime));
            } catch (Throwable commitException) {
                logger.warn("compensable transaction async submit confirm failed, recovery job will try to confirm later.", commitException);
                throw new ConfirmingException(commitException);
            }
        } else {
            commitTransaction(transaction);
        }
    }

    /**
     * 回滚事务
     *
     * @param asyncRollback
     */
    public void rollback(boolean asyncRollback) {
        // 获取事务
        final Transaction transaction = getCurrentTransaction();
        // 设置事务状态为CANCELLING
        transaction.changeStatus(TransactionStatus.CANCELLING);
        // 更新事务
        transactionRepository.update(transaction);

        if (asyncRollback) {

            try {
                executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        rollbackTransaction(transaction);
                    }
                });
            } catch (Throwable rollbackException) {
                logger.warn("compensable transaction async rollback failed, recovery job will try to rollback later.", rollbackException);
                throw new CancellingException(rollbackException);
            }
        } else {

            rollbackTransaction(transaction);
        }
    }


    private void commitTransaction(Transaction transaction) {
        try {
            transaction.commit();
            transactionRepository.delete(transaction);
        } catch (Throwable commitException) {
            logger.warn("compensable transaction confirm failed, recovery job will try to confirm later.", commitException);
            throw new ConfirmingException(commitException);
        }
    }

    private void rollbackTransaction(Transaction transaction) {
        try {
            transaction.rollback();
            transactionRepository.delete(transaction);
        } catch (Throwable rollbackException) {
            logger.warn("compensable transaction rollback failed, recovery job will try to rollback later.", rollbackException);
            throw new CancellingException(rollbackException);
        }
    }

    public Transaction getCurrentTransaction() {
        if (isTransactionActive()) {
            // 获取头部元素
            return CURRENT.get().peek();
        }
        return null;
    }

    public boolean isTransactionActive() {
        Deque<Transaction> transactions = CURRENT.get();
        return transactions != null && !transactions.isEmpty();
    }

    /**
     * 注册事务到当前事务线程队列
     *
     * @param transaction
     */
    private void registerTransaction(Transaction transaction) {

        if (CURRENT.get() == null) {
            CURRENT.set(new LinkedList<>());
        }

        // 向头部插入一个元素
        CURRENT.get().push(transaction);
    }

    public void cleanAfterCompletion(Transaction transaction) {
        if (isTransactionActive() && transaction != null) {
            Transaction currentTransaction = getCurrentTransaction();
            if (currentTransaction == transaction) {
                CURRENT.get().pop();
            } else {
                throw new SystemException("Illegal transaction when clean after completion");
            }
        }
    }

    /**
     * 添加参与者到事务
     *
     * @param participant
     */
    public void enlistParticipant(Participant participant) {
        // 获取事务
        Transaction transaction = this.getCurrentTransaction();
        // 添加参与者
        transaction.enlistParticipant(participant);
        // 更新事务
        transactionRepository.update(transaction);
    }
}
