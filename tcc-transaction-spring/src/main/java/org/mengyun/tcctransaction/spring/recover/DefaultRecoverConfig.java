package org.mengyun.tcctransaction.spring.recover;

import org.mengyun.tcctransaction.exception.OptimisticLockException;
import org.mengyun.tcctransaction.recover.RecoverConfig;

import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by changming.xie on 6/1/16.
 */
public class DefaultRecoverConfig implements RecoverConfig {

    public static final RecoverConfig INSTANCE = new DefaultRecoverConfig();

    private int maxRetryCount = 30;

    /**
     * 120 seconds
     */
    private int recoverDuration = 120;

    private String cronExpression = "0 */1 * * * ?";

    private int asyncTerminateThreadPoolSize = 1024;

    private Set<Class<? extends Exception>> delayCancelExceptions = new HashSet<>();

    public DefaultRecoverConfig() {
        delayCancelExceptions.add(OptimisticLockException.class);
        delayCancelExceptions.add(SocketTimeoutException.class);
    }

    @Override
    public int getMaxRetryCount() {
        return maxRetryCount;
    }

    public void setMaxRetryCount(int maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    @Override
    public int getRecoverDuration() {
        return recoverDuration;
    }

    public void setRecoverDuration(int recoverDuration) {
        this.recoverDuration = recoverDuration;
    }

    @Override
    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    @Override
    public Set<Class<? extends Exception>> getDelayCancelExceptions() {
        return this.delayCancelExceptions;
    }

    @Override
    public void setDelayCancelExceptions(Set<Class<? extends Exception>> delayCancelExceptions) {
        this.delayCancelExceptions.addAll(delayCancelExceptions);
    }

    @Override
    public int getAsyncTerminateThreadPoolSize() {
        return asyncTerminateThreadPoolSize;
    }

    public void setAsyncTerminateThreadPoolSize(int asyncTerminateThreadPoolSize) {
        this.asyncTerminateThreadPoolSize = asyncTerminateThreadPoolSize;
    }
}
