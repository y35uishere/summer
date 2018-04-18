package com.code.server.game.room;

import com.code.server.game.room.service.RoomManager;
import com.code.server.redis.service.RedisManager;

/**
 * Created by sunxianping on 2018/4/11.
 */
public class RoomExtendGold extends Room {


    @Override
    public void addUserSocre(long userId, double score) {
        super.addUserSocre(userId, score);
        //todo 金币改变
        if (isGoldRoom()) {
            RedisManager.getUserRedisService().addUserGold(userId, score);
        }

    }

    @Override
    public boolean isGoldRoom() {
        return goldRoomPermission != GOLD_ROOM_PERMISSION_NONE;
    }

    @Override
    public int joinRoom(long userId, boolean isJoin) {
        //随机匹配的金币房
        if (isGoldRoom() && goldRoomPermission == GOLD_ROOM_PERMISSION_DEFAULT) {
            int rtn = super.joinRoom(userId, isJoin);
            if (rtn != 0) {
                return rtn;
            }
            //如果房间已满 加入已满房间
            if (this.isRoomFull()) {
                RoomManager.getInstance().removeFormNotFullRoom(this);
            }
            return 0;
        }else{
            return super.joinRoom(userId, isJoin);
        }

    }

    @Override
    protected boolean isCanJoinCheckMoney(long userId) {
        return super.isCanJoinCheckMoney(userId);
    }

    @Override
    public int quitRoom(long userId) {
        if (isGoldRoom() && goldRoomPermission == GOLD_ROOM_PERMISSION_DEFAULT) {
            int rtn = super.quitRoom(userId);
            if (rtn != 0) {
                return rtn;
            }
            RoomManager.getInstance().removeFromFullRoom(this);
            return 0;
        }else return super.quitRoom(userId);
    }

    protected boolean isRoomFull() {
        return this.users.size() >= personNumber;
    }
}