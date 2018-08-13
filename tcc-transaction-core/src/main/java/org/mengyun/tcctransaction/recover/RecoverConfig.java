package org.mengyun.tcctransaction.recover;

import java.util.Set;

/**
 * Created by changming.xie on 6/1/16.
 */
public interface RecoverConfig {
    /**
     * 最大重试次数
     *
     * @return
     */
    int getMaxRetryCount();

    /**
     * 恢复间隔时间
     * @return
     */
    int getRecoverDuration();

    /**
     * cron表达式
     * @return
     */
    String getCronExpression();

    /**
     * 延迟取消异常集合
     * @return
     */
    Set<Class<? extends Exception>> getDelayCancelExceptions();

    /**
     * 设置延迟取消异常集合
     * @param delayRecoverExceptions
     */
    void setDelayCancelExceptions(Set<Class<? extends Exception>> delayRecoverExceptions);

    int getAsyncTerminateThreadPoolSize();
}
