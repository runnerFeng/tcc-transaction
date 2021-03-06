package org.mengyun.tcctransaction.repository;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.mengyun.tcctransaction.core.Transaction;
import org.mengyun.tcctransaction.repository.helper.ExpandTransactionSerializer;
import org.mengyun.tcctransaction.repository.helper.RedisHelper;
import org.mengyun.tcctransaction.serializer.KryoPoolSerializer;
import org.mengyun.tcctransaction.serializer.ObjectSerializer;
import org.mengyun.tcctransaction.utils.RedisUtils;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

import javax.transaction.xa.Xid;
import java.util.*;

/**
 * Created by changming.xie on 2/24/16.
 * <p/>
 * As the storage of transaction need safely durable,make sure the redis server is set as AOF mode and always fsync.
 * set below directives in your redis.conf
 * appendonly yes
 * appendfsync always
 */
@Slf4j
public class RedisTransactionRepository extends CachableTransactionRepository {

    @Getter
    private JedisPool jedisPool;
    @Setter
    private String keyPrefix = "TCC:";
    @Setter
    @Getter
    private int fetchKeySize = 1000;
    private boolean supportScan;
    @Setter
    private ObjectSerializer serializer = new KryoPoolSerializer();

    public void setJedisPool(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
        supportScan = RedisUtils.isSupportScanCommand(jedisPool.getResource());
    }

    @Override
    protected int doCreate(final Transaction transaction) {

        try {
            Long statusCode = RedisHelper.execute(jedisPool, jedis -> {

                List<byte[]> params = new ArrayList<>();

                for (Map.Entry<byte[], byte[]> entry : ExpandTransactionSerializer.serialize(serializer, transaction).entrySet()) {
                    params.add(entry.getKey());
                    params.add(entry.getValue());
                }

                Object result = jedis.eval("if redis.call('exists', KEYS[1]) == 0 then redis.call('hmset', KEYS[1], unpack(ARGV)); return 1; end; return 0;".getBytes(),
                        Arrays.asList(RedisHelper.getRedisKey(keyPrefix, transaction.getXid())), params);

                return (Long) result;
            });
            return statusCode.intValue();
        } catch (Exception e) {
            throw new TransactionIOException(e);
        }
    }

    @Override
    protected int doUpdate(final Transaction transaction) {

        try {

            Long statusCode = RedisHelper.execute(jedisPool, jedis -> {

                transaction.updateTime();
                transaction.updateVersion();

                List<byte[]> params = new ArrayList<>();

                for (Map.Entry<byte[], byte[]> entry : ExpandTransactionSerializer.serialize(serializer, transaction).entrySet()) {
                    params.add(entry.getKey());
                    params.add(entry.getValue());
                }

                Object result = jedis.eval(String.format("if redis.call('hget',KEYS[1],'VERSION') == '%s' then redis.call('hmset', KEYS[1], unpack(ARGV)); return 1; end; return 0;",
                        transaction.getVersion() - 1).getBytes(),
                        Arrays.asList(RedisHelper.getRedisKey(keyPrefix, transaction.getXid())), params);

                return (Long) result;
            });

            return statusCode.intValue();
        } catch (Exception e) {
            throw new TransactionIOException(e);
        }
    }

    @Override
    protected int doDelete(final Transaction transaction) {
        try {

            Long result = RedisHelper.execute(jedisPool, jedis -> jedis.del(RedisHelper.getRedisKey(keyPrefix, transaction.getXid())));

            return result.intValue();
        } catch (Exception e) {
            throw new TransactionIOException(e);
        }
    }

    @Override
    protected Transaction doFindOne(final Xid xid) {

        try {
            Long startTime = System.currentTimeMillis();
            Map<byte[], byte[]> content = RedisHelper.execute(jedisPool, jedis -> jedis.hgetAll(RedisHelper.getRedisKey(keyPrefix, xid)));
            log.info("redis find cost time :" + (System.currentTimeMillis() - startTime));

            if (content != null && content.size() > 0) {
                return ExpandTransactionSerializer.deserialize(serializer, content);
            }
            return null;
        } catch (Exception e) {
            throw new TransactionIOException(e);
        }
    }

    @Override
    protected List<Transaction> doFindAllUnmodifiedSince(Date date) {

        List<Transaction> allTransactions = doFindAll();

        List<Transaction> allUnmodifiedSince = new ArrayList<>();

        for (Transaction transaction : allTransactions) {
            if (transaction.getLastUpdateTime().compareTo(date) < 0) {
                allUnmodifiedSince.add(transaction);
            }
        }

        return allUnmodifiedSince;
    }

    protected List<Transaction> doFindAll() {

        try {
            final Set<byte[]> keys = RedisHelper.execute(jedisPool, jedis -> {

                if (supportScan) {
                    List<String> allKeys = new ArrayList<>();
                    String cursor = "0";
                    do {
                        ScanResult<String> scanResult = jedis.scan(cursor, new ScanParams().match(keyPrefix + "*").count(fetchKeySize));
                        allKeys.addAll(scanResult.getResult());
                        cursor = scanResult.getStringCursor();
                    } while (!cursor.equals("0"));

                    Set<byte[]> allKeySet = new HashSet<>();

                    for (String key : allKeys) {
                        allKeySet.add(key.getBytes());
                    }
                    log.info(String.format("find all key by scan command with pattern:%s allKeySet.size()=%d", keyPrefix + "*", allKeySet.size()));
                    return allKeySet;

                } else {
                    return jedis.keys((keyPrefix + "*").getBytes());
                }

            });


            return RedisHelper.execute(jedisPool, jedis -> {

                Pipeline pipeline = jedis.pipelined();

                for (final byte[] key : keys) {
                    pipeline.hgetAll(key);
                }
                List<Object> result = pipeline.syncAndReturnAll();

                List<Transaction> list = new ArrayList<Transaction>();
                for (Object data : result) {

                    if (data != null && ((Map<byte[], byte[]>) data).size() > 0) {

                        list.add(ExpandTransactionSerializer.deserialize(serializer, (Map<byte[], byte[]>) data));
                    }

                }

                return list;
            });

        } catch (Exception e) {
            throw new TransactionIOException(e);
        }
    }


}
