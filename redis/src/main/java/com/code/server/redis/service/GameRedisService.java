package com.code.server.redis.service;

import com.code.server.constant.exception.RegisterFailedException;
import com.code.server.redis.config.ServerInfo;
import com.code.server.redis.config.IConstant;
import com.code.server.redis.dao.IGameRedis;
import com.code.server.util.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by sunxianping on 2017/6/14.
 */
@Service
public class GameRedisService implements IGameRedis,IConstant{

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public void register(String serverType, int serverId) throws RegisterFailedException {
        BoundHashOperations<String,String,String> gateServer = redisTemplate.boundHashOps(IConstant.GAME_SERVER_LIST + serverType);
        String json = gateServer.get(""+serverId);
        ServerInfo serverInfo;
        //不存在此game 加入
        if (json == null) {
            serverInfo = new ServerInfo();
            serverInfo.setServerId(serverId);
            serverInfo.setServerType(serverType);
            serverInfo.setStartTime(LocalDateTime.now().toString());
        } else {
            serverInfo = JsonUtil.readValue(json, ServerInfo.class);
            long now = System.currentTimeMillis();
            //认为有相同的gateid 停止启动
            if (now - getLastHeart(serverId) < 6000) {
                throw new RegisterFailedException();
            }
            cleanGame(serverId);
        }

        gateServer.put(String.valueOf(serverId), JsonUtil.toJson(serverInfo));
    }

    @Override
    public void heart(int serverId) {
        BoundHashOperations<String,String,String> heart_gate = redisTemplate.boundHashOps(IConstant.HEART_GAME);
        heart_gate.put(""+serverId,""+System.currentTimeMillis());
    }

    @Override
    public void cleanGame(int serverId) {
        System.out.println("==================清除game");
        //删掉 user-gate
        BoundHashOperations<String,String,String> room_server = redisTemplate.boundHashOps(ROOM_GAMESERVER);
        String serverStr = String.valueOf(serverId);
        //获得在该server下的room
        if (room_server != null) {
            List<String> removeUserList = new ArrayList<>();
            for(Map.Entry<String,String> entry : room_server.entries().entrySet()){
                if (entry.getValue().equals(serverStr)) {
                    removeUserList.add(entry.getKey());
                }
            }
            if (removeUserList.size() > 0) {
                RedisManager.removeRoomAllInfo(removeUserList.toArray());
            }

        }
    }

    @Override
    public long getLastHeart(int serverId) {
        BoundHashOperations<String,String,String> heart_gate = redisTemplate.boundHashOps(HEART_GAME);
        String time = heart_gate.get("" + serverId);
        if (time == null) {
            return 0;
        }
        return Long.parseLong(time);
    }
}
