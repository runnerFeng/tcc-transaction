package org.mengyun.tcctransaction.sample.order.infrastructure.dao;

import org.mengyun.tcctransaction.sample.order.domain.entity.Order;

/**
 * Created by changming.xie on 4/1/16.
 */
public interface OrderDao {

    /**
     * 新增订单
     *
     * @param order
     * @return
     */
    int insert(Order order);

    /**
     * 更新订单
     *
     * @param order
     * @return
     */
    int update(Order order);

    Order findByMerchantOrderNo(String merchantOrderNo);
}
