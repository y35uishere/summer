package com.code.server.redis.service;

import com.code.server.redis.config.IConstant;
import com.code.server.redis.dao.IRoom_Server;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Created by sunxianping on 2017/5/26.
 */
@Service
public class RoomRedisService implements IRoom_Server ,IConstant{


    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public String getServerId(String roomId) {

        HashOperations<String,String,String> room_server = redisTemplate.opsForHash();
        return room_server.get(ROOMID_SERVERID, roomId);
    }

    @Override
    public void setServerId(String roomId, String serverid) {
        HashOperations<String,String,String> room_server = redisTemplate.opsForHash();
        room_server.put(ROOMID_SERVERID, roomId,serverid);

    }

    @Override
    public void removeServer(String roomId) {
        HashOperations<String,String,Integer> room_server = redisTemplate.opsForHash();
        room_server.delete(ROOMID_SERVERID, roomId);
    }

    @Override
    public boolean isExist(String roomId) {
        HashOperations<String,String,Integer> room_server = redisTemplate.opsForHash();
        return room_server.hasKey(ROOMID_SERVERID, roomId);
    }
}
