package org.mengyun.tcctransaction.core;

import lombok.Setter;
import org.apache.log4j.Logger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.mengyun.tcctransaction.api.TransactionContext;
import org.mengyun.tcctransaction.api.TransactionStatus;
import org.mengyun.tcctransaction.enums.TransactionType;
import org.mengyun.tcctransaction.exception.CancellingException;
import org.mengyun.tcctransaction.exception.ConfirmingException;
import org.mengyun.tcctransaction.exception.NoExistedTransactionException;
import org.mengyun.tcctransaction.exception.SystemException;
import org.mengyun.tcctransaction.repository.TransactionRepository;

import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;

/**
 * @author changmingxie
 * @date 10/26/15
 * Desc:事物管理器
 */
public class TransactionManager {

    static final Logger logger = Logger.getLogger(TransactionManager.class.getSimpleName());
    /**
     * 当前线程事务队列
     */
    private static final ThreadLocal<Deque<Transaction>> CURRENT = new ThreadLocal<>();
    @Setter
    private TransactionRepository transactionRepository;
    @Setter
    private ExecutorService executorService;

    public TransactionManager() {
    }

    /**
     * 发起根事务
     * 1.该方法在根(ROOT)事务的try阶段被调用
     * 2.该方法调用方法类型为MethodType.ROOT
     *
     * @return
     */
    public Transaction begin() {
        // 创建根事务
        Transaction transaction = new Transaction(TransactionType.ROOT);
        // 存储事务
        transactionRepository.create(transaction);
        // 注册事务到队列中
        registerTransaction(transaction);
        return transaction;
    }

    /**
     * 从事务上下文中传播发起分支事务
     * 1.该方法在分支(BRANCH)事务的try阶段被调用
     * 2.该方法调用方法类型为MethodType.PROVIDER
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
     * 从事务上下文中传播获取分支事务
     * 1.该方法在分支(BRANCH)事务的confirm/cancel阶段被调用
     * 2.该方法调用方法类型为MethodType.PROVIDER
     *
     * @param transactionContext
     * @return
     * @throws NoExistedTransactionException
     */
    public Transaction propagationExistBegin(TransactionContext transactionContext) throws NoExistedTransactionException {
        // 查询事务
        Transaction transaction = transactionRepository.findByXid(transactionContext.getXid());

        if (transaction != null) {
            // 设置状态为CONFIRMING or CANCELING
            transaction.changeStatus(TransactionStatus.valueOf(transactionContext.getStatus()));
            // 注册事务
            registerTransaction(transaction);
            return transaction;
        } else {
            throw new NoExistedTransactionException();
        }
    }

    /**
     * 提交事物：在事务try阶段没有异常的情况下由框架自动调用
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

                executorService.submit(() -> commitTransaction(transaction));
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
     * 回滚事务：在事务try阶段没有异常的情况下由框架自动调用
     * 1.事务的rollback会遍历所有参与者，并分别调用参与者的rollback，通常，根事务端的参与者包含根事务参与者和分支事务参与者，而分支事务参与者通
     * 常只有一个本地的事务参与者，除非它也发起了TCC分布式事务
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
                executorService.submit(() -> rollbackTransaction(transaction));
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
            // 获取头部元素，注册时也是在头部插入一个元素（栈的功能），即后加入的事务先执行
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

    /**
     * 每次从事务管理器中获取事物后只是删除了repository中的记录，并未删除队列中的记录，该方法就是用来删除队列中的记录并且该方法在事务拦截器中的
     * finally块中调用:
     * {@link org.mengyun.tcctransaction.interceptor.CompensableTransactionInterceptor#rootMethodProceed(ProceedingJoinPoint, boolean, boolean)}
     * {@link org.mengyun.tcctransaction.interceptor.CompensableTransactionInterceptor#providerMethodProceed(ProceedingJoinPoint, TransactionContext, boolean, boolean)}
     *
     * @param transaction
     */
    public void cleanAfterCompletion(Transaction transaction) {
        if (isTransactionActive() && transaction != null) {
            // 清理之前要比对要清理的事务是不是当前事务
            Transaction currentTransaction = getCurrentTransaction();
            if (currentTransaction == transaction) {
                //pop()弹出栈，清理该事务
                CURRENT.get().pop();
            } else {
                throw new SystemException("Illegal transaction when clean after completion");
            }
        }
    }

    /**
     * 添加参与者到事务
     * 1.该方法在事务处于try阶段被调用:
     * {@link org.mengyun.tcctransaction.interceptor.ResourceCoordinatorInterceptor#enlistParticipant(ProceedingJoinPoint)}
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
