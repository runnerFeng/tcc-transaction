package org.mengyun.tcctransaction.spring;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.mengyun.tcctransaction.core.TransactionManager;
import org.mengyun.tcctransaction.interceptor.CompensableTransactionAspect;
import org.mengyun.tcctransaction.interceptor.CompensableTransactionInterceptor;
import org.mengyun.tcctransaction.support.TransactionConfigurator;
import org.springframework.core.Ordered;

/**
 * Created by changmingxie on 10/30/15.
 */
@Aspect
public class ConfigurableTransactionAspect extends CompensableTransactionAspect implements Ordered {

    private TransactionConfigurator transactionConfigurator;

    public void init() {
        TransactionManager transactionManager = transactionConfigurator.getTransactionManager();

        CompensableTransactionInterceptor compensableTransactionInterceptor = new CompensableTransactionInterceptor();
        compensableTransactionInterceptor.setTransactionManager(transactionManager);
        compensableTransactionInterceptor.setDelayCancelExceptions(transactionConfigurator.getRecoverConfig().getDelayCancelExceptions());

        this.setCompensableTransactionInterceptor(compensableTransactionInterceptor);
    }

    /**
     * 这里需要注意的是：
     * 1.这两个切面是有优先级排序的，排序通过实现spring框架中的Ordered接口就可以了，其中CompensableTransactionAspect优先级低于ResourceCoordinatorAspect
     * ，ResourceCoordinateAspect的作用是添加参与者到事务，该方法在事务处于try阶段被调用。
     * {@link org.mengyun.tcctransaction.interceptor.ResourceCoordinatorInterceptor#enlistParticipant(ProceedingJoinPoint)}
     *
     * @return
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    public void setTransactionConfigurator(TransactionConfigurator transactionConfigurator) {
        this.transactionConfigurator = transactionConfigurator;
    }

}
