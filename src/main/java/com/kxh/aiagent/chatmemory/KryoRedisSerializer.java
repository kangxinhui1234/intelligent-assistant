package com.kxh.aiagent.chatmemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class KryoRedisSerializer<T> implements RedisSerializer<T> {

    private static final ThreadLocal<Kryo> kryoThreadLocal = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        // 允许注册子类，否则多态时可能报错
        kryo.setRegistrationRequired(false);
        kryo.setReferences(true);
        // 关键：启用 Objenesis
        kryo.setInstantiatorStrategy(
                new DefaultInstantiatorStrategy(new StdInstantiatorStrategy())
        );
        return kryo;
    });

    @Override
    public byte[] serialize(T t) throws SerializationException {
        if (t == null) return new byte[0];
        Kryo kryo = kryoThreadLocal.get();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             Output output = new Output(baos)) {
            kryo.writeClassAndObject(output, t); // 支持多态
            output.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new SerializationException("Kryo serialize error", e);
        }
    }
    @Override
    @SuppressWarnings("unchecked")
    public T deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) return null;
        Kryo kryo = kryoThreadLocal.get();
        try (Input input = new Input(new ByteArrayInputStream(bytes))) {
            return (T) kryo.readClassAndObject(input); // 支持多态
        } catch (Exception e) {
            throw new SerializationException("Kryo deserialize error", e);
        }
    }
    @Override
    public boolean canSerialize(Class<?> type) {
        return RedisSerializer.super.canSerialize(type);
    }

    @Override
    public Class<?> getTargetType() {
        return RedisSerializer.super.getTargetType();
    }
}
