package org.mengyun.tcctransaction.support;

import org.mengyun.tcctransaction.core.TransactionManager;
import org.mengyun.tcctransaction.repository.TransactionRepository;
import org.mengyun.tcctransaction.recover.RecoverConfig;

/**
 * Created by changming.xie on 2/24/17.
 */
public interface TransactionConfigurator {

    TransactionManager getTransactionManager();

    TransactionRepository getTransactionRepository();

    RecoverConfig getRecoverConfig();
}
