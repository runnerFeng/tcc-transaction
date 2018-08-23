package org.mengyun.tcctransaction.sample.http.order.web.controller.vo;

import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by changming.xie on 4/2/16.
 */
@Data
public class PlaceOrderRequest {

    private long payerUserId;

    private long shopId;

    private BigDecimal redPacketPayAmount;

    private List<Pair<Long, Integer>> productQuantities = new ArrayList<>();

}
