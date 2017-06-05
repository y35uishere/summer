package com.code.server.game.mahjong.service;

import com.code.server.constant.kafka.KafkaMsgKey;
import com.code.server.game.mahjong.response.ResponseVo;
import com.code.server.game.room.MsgSender;
import com.code.server.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * Created by sunxianping on 2017/5/23.
 */
public class MsgDispatch {

    public static void dispatch(ConsumerRecord<String, String> record) {

        String key = record.key();
        String value = record.value();

        KafkaMsgKey msgKey = JsonUtil.readValue(key, KafkaMsgKey.class);
        JsonNode jsonNode = JsonUtil.readTree(value);


        long userId = msgKey.getUserId();
        String roomId = msgKey.getRoomId();


        String service = jsonNode.get("service").asText();
        String method = jsonNode.get("method").asText();
        JsonNode params = jsonNode.get("params");


        int code = dispatchAllMsg(userId, roomId, service, method, params);
        //客户端要的方法返回
        if (code != 0) {
            ResponseVo vo = new ResponseVo(service, method, code);
            MsgSender.sendMsg2Player(vo, userId);

        }

    }

    private static int dispatchAllMsg(long userId, String roomId, String service, String method, JsonNode params) {
        switch (service) {

            case "GameLogicService": {
                return GameLogicService.dispatch(userId, method, roomId, params);
            }

            case "mahjongRoomService": {
                return MahjongRoomService.dispatch(userId, method, params);
            }

            case "reconnService": {
                return ReconnService.dispatch(userId, method, roomId);
            }

            default:
                return -1;
        }
    }


}
