package org.mengyun.tcctransaction.api;

import java.lang.reflect.Method;

/**
 * Created by changming.xie on 1/18/17.
 */
public interface TransactionContextEditor {

    /**
     * 从参数中获得事务上下文
     *
     * @param target
     * @param method
     * @param args
     * @return
     */
    TransactionContext get(Object target, Method method, Object[] args);

    /**
     * 设置事物上下文到参数中
     *
     * @param transactionContext
     * @param target
     * @param method
     * @param args
     */
    void set(TransactionContext transactionContext, Object target, Method method, Object[] args);

}
