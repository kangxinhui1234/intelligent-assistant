package com.kxh.aiagent.chatmemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import lombok.Data;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@Data
public class RedisBasedChatMemory implements ChatMemory {

    @Autowired
    private RedisTemplate redisTemplate;;
    /**
     * 创建时手动设置
     */
    private int lastN = 10;

    @Override
    public void add(String conversationId, Message message) {
        redisTemplate.opsForList().leftPush(conversationId,message);
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        redisTemplate.opsForList().leftPushAll(conversationId,messages);

    }

    @Override
    public List<Message> get(String conversationId) {
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < lastN; i++) {
            Message msg = (Message)redisTemplate.opsForList().rightPop(conversationId);
            if (msg == null) break;
            messages.add(msg);
        }
        return messages;
    }


    @Override
    public void clear(String conversationId) {
         redisTemplate.delete(conversationId);
    }

}
