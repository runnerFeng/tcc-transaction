package org.mengyun.tcctransaction.serializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoFactory;
import com.esotericsoftware.kryo.pool.KryoPool;
import org.objenesis.strategy.StdInstantiatorStrategy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Created by changming.xie on 9/18/17.
 */
public class KryoPoolSerializer<T> implements ObjectSerializer<T> {

    static KryoFactory factory = () -> {
        Kryo kryo = new Kryo();
        kryo.setReferences(true);
        kryo.setRegistrationRequired(false);
        //Fix the NPE bug when deserializing Collections.
        ((Kryo.DefaultInstantiatorStrategy) kryo.getInstantiatorStrategy())
                .setFallbackInstantiatorStrategy(new StdInstantiatorStrategy());

        return kryo;
    };


    KryoPool pool = new KryoPool.Builder(factory).softReferences().build();

    private int initPoolSize = 300;

    public KryoPoolSerializer() {
        init();
    }

    public KryoPoolSerializer(int initPoolSize) {
        this.initPoolSize = initPoolSize;
        init();
    }

    private void init() {

        for (int i = 0; i < initPoolSize; i++) {
            Kryo kryo = pool.borrow();
            pool.release(kryo);
        }
    }

    @Override
    public byte[] serialize(final T object) {

        return pool.run(kryo -> {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            Output output = new Output(byteArrayOutputStream);

            kryo.writeClassAndObject(output, object);
            output.flush();

            return byteArrayOutputStream.toByteArray();
        });
    }

    @Override
    public T deserialize(final byte[] bytes) {

        return pool.run(kryo -> {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
            Input input = new Input(byteArrayInputStream);

            return (T) kryo.readClassAndObject(input);
        });
    }

    @Override
    public T clone(final T object) {
        return pool.run(kryo -> kryo.copy(object));
    }
}
