package org.mengyun.tcctransaction.core;

import org.mengyun.tcctransaction.api.TransactionContext;
import org.mengyun.tcctransaction.api.TransactionContextEditor;
import org.mengyun.tcctransaction.exception.SystemException;
import org.mengyun.tcctransaction.support.FactoryBuilder;
import org.mengyun.tcctransaction.utils.StringUtils;

import java.io.Serializable;
import java.lang.reflect.Method;

/**
 * Created by changmingxie on 10/30/15.
 */
public class Terminator implements Serializable {

    private static final long serialVersionUID = -164958655471605778L;

    public Terminator() {
    }

    public Object invoke(TransactionContext transactionContext, InvocationContext invocationContext, Class<? extends TransactionContextEditor> transactionContextEditorClass) {

        if (StringUtils.isNotEmpty(invocationContext.getMethodName())) {

            try {
                // 获得参与者对象单例
                Object target = FactoryBuilder.factoryOf(invocationContext.getTargetClass()).getInstance();
                // 反射获得方法
                Method method = target.getClass().getMethod(invocationContext.getMethodName(), invocationContext.getParameterTypes());
                // 设置事务上下文到方法参数
                FactoryBuilder.factoryOf(transactionContextEditorClass).getInstance().set(transactionContext, target, method, invocationContext.getArgs());
                // 反射调用真正的方法(本地或者远程)
                return method.invoke(target, invocationContext.getArgs());

            } catch (Exception e) {
                throw new SystemException(e);
            }
        }
        return null;
    }
}
