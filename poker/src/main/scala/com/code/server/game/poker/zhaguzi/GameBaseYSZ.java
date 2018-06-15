package com.code.server.game.poker.zhaguzi;

import com.code.server.constant.data.DataManager;
import com.code.server.constant.response.*;
import com.code.server.game.room.Game;
import com.code.server.game.room.IfaceRoom;
import com.code.server.game.room.Room;
import com.code.server.game.room.kafka.MsgSender;
import com.code.server.game.room.service.RoomManager;
import com.code.server.util.IdWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class GameBaseYSZ extends Game {
    protected static final Logger logger = LoggerFactory.getLogger(GameBaseYSZ.class);

    private static  Double INIT_BOTTOM_CHIP = 1.0;//底注
    private static final int INIT_CARD_NUM = 3;//玩家牌数3张

    protected List<Integer> cards = new ArrayList<>();//牌
    public Map<Long, PlayerYSZ> playerCardInfos = new HashMap<>();
    protected Random rand = new Random();

    protected int curRoundNumber = 1;//当前轮数
    protected Double chip = INIT_BOTTOM_CHIP;

    protected List<Integer> leaveCards = new ArrayList<>();//剩余的牌，暂时无用
    protected List<Long> aliveUser = new ArrayList<>();//存活的人
    protected List<Long> seeUser = new ArrayList<>();//看牌的人
    protected List<Long> loseUser = new ArrayList<>();//输牌的人
    protected Long curUserId;
    protected RoomYSZ room;
    protected List<Integer> genZhuList = new ArrayList<>();

    public List<Integer> getGenZhuList() {
        return genZhuList;
    }

    public void setGenZhuList(List<Integer> genZhuList) {
        this.genZhuList = genZhuList;
    }

    //private Double MAX_BET_NUM = DataManager.data.getRoomDataMap().get(room.getGameType()).getMaxBet();//最大下注数
    private Double MAX_BET_NUM = 0.0;

    public static String getStr(String str) {

        switch (str) {
            case "see":
                return "seeCard";
            case "call":
                return "jiao";
            case "kill":
                return "kill";
            case "fold":
                return "giveup";
            case "raise":
                return "raise";
        }
        return null;
    }

    public void initDiZhu(){

        //房卡场 暂定
        if (room.getGoldRoomPermission() == IfaceRoom.GOLD_ROOM_PERMISSION_NONE){
            MAX_BET_NUM = DataManager.data.getRoomDataMap().get(room.getGameType()).getMaxBet();
            INIT_BOTTOM_CHIP = 1d;
            genZhuList.add(100);
            genZhuList.add(150);
            genZhuList.add(200);
            genZhuList.add(250);
            return;
        }

        double dizhu = 0d;
        double max = 0;

        //随机
        if (room.getGoldRoomPermission() == IfaceRoom.GOLD_ROOM_PERMISSION_DEFAULT){
            dizhu = room.getGoldRoomType();
            if (room.getGoldRoomType() == 50){
                max = 500;
                genZhuList.add(100);
                genZhuList.add(150);
                genZhuList.add(200);
                genZhuList.add(250);
            }else if (room.getGoldRoomType() == 100){
                max = 1000;
                genZhuList.add(200);
                genZhuList.add(300);
                genZhuList.add(400);
                genZhuList.add(500);
            }else if (room.getGoldRoomType() == 500){
                max = 5000;
                genZhuList.add(1000);
                genZhuList.add(1500);
                genZhuList.add(2000);
                genZhuList.add(2500);
            }else if (room.getGoldRoomType() == 1000){
                max = 10000;
                genZhuList.add(2000);
                genZhuList.add(3000);
                genZhuList.add(4000);
                genZhuList.add(5000);
            }
            INIT_BOTTOM_CHIP = dizhu;
            MAX_BET_NUM = max;

        }else if (room.getGoldRoomPermission() == IfaceRoom.GOLD_ROOM_PERMISSION_PUBLIC || room.getGoldRoomPermission() == IfaceRoom.GOLD_ROOM_PERMISSION_PRIVATE){
            dizhu = room.getGoldRoomType();
            if (room.getGoldRoomType() == 50){
                max = 500;
                genZhuList.add(100);
                genZhuList.add(150);
                genZhuList.add(200);
                genZhuList.add(250);

            }else if (room.getGoldRoomType() == 100){
                max = 1000;
                genZhuList.add(200);
                genZhuList.add(300);
                genZhuList.add(400);
                genZhuList.add(500);

            }else if (room.getGoldRoomType() == 1000){
                max = 10000;
                genZhuList.add(2000);
                genZhuList.add(3000);
                genZhuList.add(4000);
                genZhuList.add(5000);

            }else if (room.getGoldRoomType() == 2000){
                max = 20000;
                genZhuList.add(4000);
                genZhuList.add(6000);
                genZhuList.add(8000);
                genZhuList.add(10000);
            }
        }

    }

    public void init(List<Long> users) {
        //初始化玩家
        for (Long uid : users) {
            PlayerYSZ playerCardInfo = getGameTypePlayerCardInfo();
            playerCardInfo.userId = uid;
            playerCardInfos.put(uid, playerCardInfo);
        }
        this.users.addAll(users);
        this.aliveUser.addAll(users);

        shuffle();//洗牌
        deal();//发牌
        initDiZhu();
        mustBet();
        curUserId = room.getBankerId();

        noticeAction(curUserId);
        updateLastOperateTime();
    }

    public boolean isYsz() {
        return true;
    }

    public void startGame(List<Long> users, Room room) {

        PokerItem.isYSZ = true;

        this.room = (RoomYSZ) room;
        init(users);
        updateLastOperateTime();
        //通知其他人游戏已经开始
        MsgSender.sendMsg2Player(new ResponseVo("gameService", "gameBegin", "ok"), this.getUsers());
    }

    /**
     * 必须下底注
     */
    private void mustBet() {
        for (Long l : playerCardInfos.keySet()) {
            playerCardInfos.get(l).setAllScore(INIT_BOTTOM_CHIP);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("mustBet", chip);
        result.put("zhuList", this.getGenZhuList());
        result.put("maxBet", this.MAX_BET_NUM);
        ResponseVo vo = new ResponseVo("gameService", "mustBet", result);
        MsgSender.sendMsg2Player(vo, users);
    }

    /**
     * 加注
     *
     * @return
     */
    public int raise(long userId, double addChip) {
        logger.info(userId + "  下注: " + addChip);

        if (userId != curUserId) {//判断是否到顺序
            return ErrorCode.NOT_YOU_TURN;
        }
//        MAX_BET_NUM = DataManager.data.getRoomDataMap().get(room.getGameType()).getMaxBet();
        if (seeUser.contains(userId)) {
//            if(addChip!=chip*2+2 && addChip!=chip*2*2 && addChip!=chip*2*4 && addChip!=MAX_BET_NUM){
//                return ErrorCode.BET_WRONG;
//            }
            chip = addChip / 2;
        } else {
//            if(addChip!=chip+2 && addChip!=chip*2 && addChip!=chip*4 && addChip!=MAX_BET_NUM/2){
//                return ErrorCode.BET_WRONG;
//            }
            chip = addChip;
        }

        playerCardInfos.get(userId).setAllScore(playerCardInfos.get(userId).getAllScore() + addChip);
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("addChip", addChip);
        ResponseVo vo = new ResponseVo("gameService", "raiseResponse", result);
        MsgSender.sendMsg2Player(vo, users);

        noticeAction(curUserId);

        MsgSender.sendMsg2Player("gameService", "raise", 0, userId);
        updateLastOperateTime();

        return 0;
    }

    public int call(long userId) {

        logger.info(userId + "  跟注: " + chip);

        if (userId != curUserId) {//判断是否到顺序
            return ErrorCode.NOT_YOU_TURN;
        }

        if (seeUser.contains(userId)) {
            playerCardInfos.get(userId).setAllScore(playerCardInfos.get(userId).getAllScore() + chip * 2);
        } else {
            playerCardInfos.get(userId).setAllScore(playerCardInfos.get(userId).getAllScore() + chip);
        }

        logger.info("{}", userId);

        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        if (seeUser.contains(userId)) {
            result.put("addChip", chip * 2);
        } else {
            result.put("addChip", chip);
        }

        ResponseVo vo = new ResponseVo("gameService", "callResponse", result);
        MsgSender.sendMsg2Player(vo, users);

        noticeAction(curUserId);

        MsgSender.sendMsg2Player("gameService", "call", 0, userId);
        updateLastOperateTime();

        return 0;
    }

    public int fold(long userId) {

        logger.info(userId + "  foldddd!!!");


        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("result", "fold success!!");
        ResponseVo vo = new ResponseVo("gameService", "foldResponse", result);
        MsgSender.sendMsg2Player(vo, users);

//        logger.info(userId + "  f!");
        if (aliveUser.size() == 2) {
            logger.info("over");

            curUserId = 0l;

            aliveUser.remove(userId);
            //处理结果
            compute(aliveUser);
            sendResult();
            genRecord();

            room.setBankerId(aliveUser.get(0));
            room.clearReadyStatus(true);
            sendFinalResult();
        } else {

            logger.info("conti");
            noticeActionByFold(userId);
            aliveUser.remove(userId);
        }

        MsgSender.sendMsg2Player("gameService", "fold", 0, userId);
        updateLastOperateTime();

        return 0;
    }

    public int see(long userId) {

        logger.info(userId + "  ssss" + playerCardInfos.get(userId).getHandcards());

        if (playerCardInfos.get(userId).getCurRoundNumber() <= room.getMenPai()) {
            return ErrorCode.NOT_GET_MEMPAI;
        }
        seeUser.add(userId);
        playerCardInfos.get(userId).setSee("0");

        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        ResponseVo vo = new ResponseVo("gameService", "seeResponse", result);
        MsgSender.sendMsg2Player(vo, users);

        Map<String, Object> seeResult = new HashMap<>();
        result.put("userId", userId);
        result.put("cards", playerCardInfos.get(userId).getHandcards());
        ResponseVo seeVo = new ResponseVo("gameService", "seeResponse", result);
        MsgSender.sendMsg2Player(vo, userId);

        noticeActionSelf(userId);

        MsgSender.sendMsg2Player("gameService", "see", 0, userId);
        updateLastOperateTime();

        return 0;
    }

    /**
     * 比牌
     *
     * @return
     */
    public int kill(long askerId, long accepterId) {

        logger.info(askerId + "  比牌: " + chip);

        if (!aliveUser.contains(askerId) || !aliveUser.contains(accepterId)) {
            return ErrorCode.NOT_KILL;
        }

        Player asker = new Player(askerId, ArrUtils.cardCode.get(playerCardInfos.get(askerId).getHandcards().get(0)), ArrUtils.cardCode.get(playerCardInfos.get(askerId).getHandcards().get(1)), ArrUtils.cardCode.get(playerCardInfos.get(askerId).getHandcards().get(2)));
        Player accepter = new Player(accepterId, ArrUtils.cardCode.get(playerCardInfos.get(accepterId).getHandcards().get(0)), ArrUtils.cardCode.get(playerCardInfos.get(accepterId).getHandcards().get(1)), ArrUtils.cardCode.get(playerCardInfos.get(accepterId).getHandcards().get(2)));

        ArrayList<Player> winnerList = new ArrayList<>();
        if ("30".equals(this.room.getGameType())) {
            winnerList = Player.findWinners(Player.Rules.XiaoYao, asker, accepter);
        } else {
            winnerList = Player.findWinners(Player.Rules.XiaoYao, asker, accepter);
        }

        Long winnerId = winnerList.size() == 1 ? winnerList.get(0).getUid() : winnerList.get(1).getUid();
        loseUser.add(winnerId == askerId ? accepterId : askerId);

        Map<String, Object> result = new HashMap<>();
        result.put("askerId", askerId);
        result.put("winnerId", winnerId);
        result.put("loserId", winnerId == askerId ? accepterId : askerId);
        if (seeUser.contains(askerId)) {
            playerCardInfos.get(askerId).setAllScore(playerCardInfos.get(askerId).getAllScore() + chip * 2);
            result.put("addChip", chip * 2);
            logger.info("");
        } else {
            playerCardInfos.get(askerId).setAllScore(playerCardInfos.get(askerId).getAllScore() + chip);
            result.put("addChip", chip);
        }
        ResponseVo vo = new ResponseVo("gameService", "killResponse", result);
        MsgSender.sendMsg2Player(vo, users);

        if (aliveUser.size() > 2) {
            logger.info("");
            if (winnerId == askerId) {
                aliveUser.remove(winnerId == askerId ? accepterId : askerId);
            }

            if (aliveUser.size() > 1) {
                noticeAction(curUserId);
            } else {
                logger.info("");
                //处理结果
                List<Long> list = new ArrayList<>();
                list.add(winnerId);
                compute(list);
                sendResult();
                genRecord();

                room.setBankerId(winnerId);
                room.clearReadyStatus(true);
                sendFinalResult();
            }

            if (winnerId != askerId) {
                aliveUser.remove(winnerId == askerId ? accepterId : askerId);
            }
        } else {
            aliveUser.remove(winnerId == askerId ? accepterId : askerId);
            List<Long> list = new ArrayList<>();
            list.add(winnerId);
            compute(list);
            sendResult();
            genRecord();
            logger.info("");
            room.setBankerId(winnerId);
            room.clearReadyStatus(true);
            sendFinalResult();
        }

        MsgSender.sendMsg2Player("gameService", "kill", 0, askerId);

        updateLastOperateTime();

        return 0;
    }

//    /**
//     * 透视
//     *
//     * @return
//     */
//    public int perspective(long userId) {
//        Map<Long, Object> result = new HashMap<>();
//        for (Long l : playerCardInfos.keySet()) {
//            result.put(l, playerCardInfos.get(l).handcards);
//        }
//        ResponseVo vo = new ResponseVo("gameService", "perspective", result);
//        MsgSender.sendMsg2Player(vo, userId);
//        return 0;
//    }

    /**
     * 换牌
     * type:baoZi,tongHuaShun,tongHua,shunZi,duiZi,erSanWu,sanPai
     *
     * @return
     */
//    public int changeCard(long userId, String cardType) {
//        Map<Long, Object> result = new HashMap<>();
//        List<Integer> changeCards = new ArrayList<>();
//        if ("baoZi".equals(cardType)) {
//            changeCards = ArrUtils.getBaoZi(leaveCards);
//        } else if ("tongHuaShun".equals(cardType)) {
//            changeCards = ArrUtils.getTongHuaShun(leaveCards);
//        } else if ("tongHua".equals(cardType)) {
//            changeCards = ArrUtils.getTongHua(leaveCards);
//        } else if ("shunZi".equals(cardType)) {
//            changeCards = ArrUtils.getShunZi(leaveCards);
//        } else if ("duiZi".equals(cardType)) {
//            changeCards = ArrUtils.getDuiZi(leaveCards);
//        } else if ("erSanWu".equals(cardType)) {
//            changeCards = ArrUtils.getErSanWu(leaveCards);
//        } else if ("sanPai".equals(cardType)) {
//            changeCards = ArrUtils.getSanPai(leaveCards);
//        }
//        changeCard(userId, playerCardInfos.get(userId).getHandcards(), changeCards);
//        result.put(userId, changeCards);
//        ResponseVo vo = new ResponseVo("gameService", "changeCard", result);
//        MsgSender.sendMsg2Player(vo, userId);
//        return 0;
//    }
    //=====================================
    //==============结束操作================
    //=====================================

    /**
     * 算分
     *
     * @param winList
     */
    protected void compute(List<Long> winList) {
        RoomYSZ roomYSZ = null;
        if (room instanceof RoomYSZ) {
            roomYSZ = (RoomYSZ) room;
        }
        //设置每个人的牌类型
        for (PlayerYSZ playerCardInfo : playerCardInfos.values()) {
            Player p = new Player(playerCardInfo.getUserId(), ArrUtils.cardCode.get(playerCardInfo.getHandcards().get(0)), ArrUtils.cardCode.get(playerCardInfo.getHandcards().get(1)), ArrUtils.cardCode.get(playerCardInfo.getHandcards().get(2)));
            playerCardInfo.setCardType(p.getCategory().toString());
            if (PokerItem.is235(p.getPokers())) {
                playerCardInfo.setCardType("BaoZiShaShou");
            }

            //添加次数
            if ("BaoZi".equals(playerCardInfo.getCardType())) {
                logger.info("");
                roomYSZ.addBaoziNum(playerCardInfo.getUserId());
            } else if ("ShunJin".equals(playerCardInfo.getCardType())) {
                logger.info("");
                roomYSZ.addTonghuashunNum(playerCardInfo.getUserId());
            } else if ("JinHua".equals(playerCardInfo.getCardType())) {
                logger.info("");
                roomYSZ.addTonghuaNum(playerCardInfo.getUserId());
            } else if ("ShunZi".equals(playerCardInfo.getCardType())) {
                logger.info("");
                roomYSZ.addShunziNum(playerCardInfo.getUserId());
            } else if ("DuiZi".equals(playerCardInfo.getCardType())) {
                roomYSZ.addDuiziNum(playerCardInfo.getUserId());
            } else if ("DanZi".equals(playerCardInfo.getCardType())) {
                roomYSZ.addSanpaiNum(playerCardInfo.getUserId());
            }
        }
        //添加彩分
        for (PlayerYSZ playerCardInfo : playerCardInfos.values()) {
            if ("BaoZi".equals(playerCardInfo.getCardType())) {
                double tempCaifen = 0.0;
                for (PlayerYSZ p : playerCardInfos.values()) {
                    if (playerCardInfo.getUserId() != p.getUserId()) {
                        tempCaifen += room.getCaiFen();
                        p.setCaifen(p.getCaifen() - room.getCaiFen());
                    }
                }
                playerCardInfo.setCaifen(playerCardInfo.getCaifen() + tempCaifen);
            }
        }

        //算分
        double totalChip = 0.0;
        for (PlayerYSZ playerCardInfo : playerCardInfos.values()) {
            totalChip += playerCardInfo.getAllScore();
        }
        for (PlayerYSZ playerCardInfo : playerCardInfos.values()) {
            if (winList.contains(playerCardInfo.getUserId())) {
                playerCardInfo.setScore(room.getMultiple() * totalChip / winList.size());
            } else {
                playerCardInfo.setScore(-room.getMultiple() * playerCardInfo.getAllScore());
            }
        }
        for (PlayerYSZ playerCardInfo : playerCardInfos.values()) {
            if (winList.contains(playerCardInfo.getUserId())) {
                logger.info("");
                room.addUserSocre(playerCardInfo.getUserId(), playerCardInfo.getScore() - room.getMultiple() * playerCardInfo.getAllScore());
                room.addUserSocre(playerCardInfo.getUserId(), playerCardInfo.getCaifen());
                playerCardInfo.setFinalScore(playerCardInfo.getScore() - room.getMultiple() * playerCardInfo.getAllScore() + playerCardInfo.getCaifen());
            } else {
                room.addUserSocre(playerCardInfo.getUserId(), -room.getMultiple() * playerCardInfo.getAllScore());
                room.addUserSocre(playerCardInfo.getUserId(), playerCardInfo.getCaifen());
                playerCardInfo.setFinalScore(-room.getMultiple() * playerCardInfo.getAllScore() + playerCardInfo.getCaifen());
            }
        }
    }

    /**
     * 发送战绩
     */
    protected void sendResult() {
        GameResultHitGoldFlower gameResultHitGoldFlower = new GameResultHitGoldFlower();

        //将room保存到playerCardInfos
        double personNumberTemp = 0.0;
        double nagetiveTotal = 0.0;
        for (Long l : this.playerCardInfos.keySet()) {
            if (this.playerCardInfos.get(l).getScore() < 0) {
                personNumberTemp += 1;
                nagetiveTotal += this.playerCardInfos.get(l).getScore();
            }
        }
        for (Long l : this.playerCardInfos.keySet()) {
            if (this.playerCardInfos.get(l).getScore() > 0) {
                this.playerCardInfos.get(l).setScore(-nagetiveTotal / (playerCardInfos.size() - personNumberTemp));
            }
        }

        for (PlayerYSZ playerCardInfo : playerCardInfos.values()) {
            gameResultHitGoldFlower.getPlayerCardInfos().add(playerCardInfo.toVoHaveHandcards());
        }

        gameResultHitGoldFlower.getUserScores().putAll(this.room.getUserScores());
        List<Long> winnerList = new ArrayList<>();
        for (PlayerYSZ playerCardInfo : playerCardInfos.values()) {
            if (playerCardInfo.getFinalScore() > 0) {
                winnerList.add(playerCardInfo.getUserId());
            }
        }
        gameResultHitGoldFlower.setWinnerList(winnerList);
        gameResultHitGoldFlower.setBankerId(winnerList.get(0));
        MsgSender.sendMsg2Player("gameService", "gameResult", gameResultHitGoldFlower, users);
    }

    /**
     * 战绩
     */
    protected void genRecord() {
        long id = IdWorker.getDefaultInstance().nextId();
        genRecord(playerCardInfos.values().stream().collect
                (Collectors.toMap(PlayerYSZ::getUserId, PlayerYSZ::getScore)), room, id);
    }

    /**
     * 最后结算
     */
    protected void sendFinalResult() {
        //所有牌局都结束
        if (room.getCurGameNumber() > room.getGameNumber()) {
            logger.info("");
            List<UserOfResult> userOfResultList = this.room.getUserOfResult();
            // 存储返回
            GameOfResult gameOfResult = new GameOfResult();
            gameOfResult.setUserList(userOfResultList);
            MsgSender.sendMsg2Player("gameService", "gameFinalResult", gameOfResult, users);

            RoomManager.removeRoom(room.getRoomId());

            //战绩
            this.room.genRoomRecord();

        }
    }

    //===========================================
    //==============以下为通知代码================
    //===========================================

    /**
     * 通知操作按钮(本轮的人)
     *
     * @param userId
     */
    protected void noticeActionSelf(long userId) {

//        MAX_BET_NUM = DataManager.data.getRoomDataMap().get(room.getGameType()).getMaxBet();

        /**
         protected String call = "1";//跟注
         protected String raise = "0";//加注  ===
         protected String fold = "1";//弃牌
         protected String kill = "1";//比牌
         protected String see = "0";//看牌    ===
         */
        PlayerYSZ playerCardInfo = playerCardInfos.get(userId);
        playerCardInfo.setRaise("1");
        playerCardInfo.setFold("1");
        playerCardInfo.setCall("1");
        playerCardInfo.setKill("1");
        playerCardInfo.setSee("1");

        if (seeUser.contains(userId) || getMaxRoundNumber() <= room.getMenPai()) {
            playerCardInfo.setSee("0");
        }
        if (seeUser.contains(userId)) {
            if (chip >= MAX_BET_NUM) {
                playerCardInfo.setRaise("0");
            }
        } else {
            if (chip >= MAX_BET_NUM / 2) {
                logger.info("");
                playerCardInfo.setRaise("0");
            }
        }

        if (getMaxRoundNumber() <= room.getMenPai()) {
            playerCardInfo.setKill("0");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("playerCardInfo", playerCardInfo);
        result.put("chip", chip);
        ResponseVo vo = new ResponseVo("gameService", "noticeActionSelf", result);
        MsgSender.sendMsg2Player(vo, users);
    }

    /**
     * 通知操作按钮(下一个)
     *
     * @param userId
     */
    protected void noticeAction(long userId) {
//        MAX_BET_NUM = DataManager.data.getRoomDataMap().get(room.getGameType()).getMaxBet();
        curUserId = nextActioner(userId);
        /**
         protected String call = "1";//跟注
         protected String raise = "0";//加注  ===
         protected String fold = "1";//弃牌
         protected String kill = "1";//比牌
         protected String see = "0";//看牌    ===
         */
        PlayerYSZ playerCardInfo = playerCardInfos.get(curUserId);
        playerCardInfo.setRaise("1");
        playerCardInfo.setFold("1");
        playerCardInfo.setCall("1");
        playerCardInfo.setKill("1");
        playerCardInfo.setSee("1");
        playerCardInfo.setCurRoundNumber(playerCardInfo.getCurRoundNumber() + 1);
        if (seeUser.contains(curUserId) || getMaxRoundNumber() <= room.getMenPai()) {
            playerCardInfo.setSee("0");
        }
        if (getMaxRoundNumber() <= room.getMenPai()) {
            playerCardInfo.setKill("0");
        }
        if (seeUser.contains(curUserId)) {
            logger.info("");
            if (chip >= MAX_BET_NUM) {
                playerCardInfo.setRaise("0");
            }
        } else {
            logger.info("");
            if (chip >= MAX_BET_NUM / 2) {
                playerCardInfo.setRaise("0");
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("playerCardInfo", playerCardInfo);
        result.put("chip", chip);
        ResponseVo vo = new ResponseVo("gameService", "noticeAction", result);
        MsgSender.sendMsg2Player(vo, users);
    }


    /**
     * 通知操作按钮(下一个)
     *
     * @param userId
     */
    protected void noticeActionByFold(long userId) {
        logger.info("");
//        MAX_BET_NUM = DataManager.data.getRoomDataMap().get(room.getGameType()).getMaxBet();
        int index = aliveUser.indexOf(userId);

        int nextId = index + 1;
        if (nextId >= aliveUser.size()) {
            nextId = 0;
        }
        if (getMaxRoundNumberB() || aliveUser.size() == 1) {
            campareAllCards();
        }
        curUserId = aliveUser.get(nextId);

        /**
         protected String call = "1";//跟注
         protected String raise = "0";//加注  ===
         protected String fold = "1";//弃牌
         protected String kill = "1";//比牌
         protected String see = "0";//看牌    ===
         */
        PlayerYSZ playerCardInfo = playerCardInfos.get(curUserId);
        playerCardInfo.setRaise("1");
        playerCardInfo.setFold("1");
        playerCardInfo.setCall("1");
        playerCardInfo.setKill("1");
        playerCardInfo.setSee("1");
        playerCardInfo.setCurRoundNumber(playerCardInfo.getCurRoundNumber() + 1);
        if (seeUser.contains(curUserId) || getMaxRoundNumber() <= room.getMenPai()) {
            playerCardInfo.setSee("0");
        }
        if (getMaxRoundNumber() <= room.getMenPai()) {
            playerCardInfo.setKill("0");
        }

        if (seeUser.contains(curUserId)) {
            logger.info("");
            if (chip >= MAX_BET_NUM) {
                playerCardInfo.setRaise("0");
            }
        } else {
            if (chip >= MAX_BET_NUM / 2) {
                playerCardInfo.setRaise("0");
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("playerCardInfo", playerCardInfo);
        result.put("chip", chip);
        ResponseVo vo = new ResponseVo("gameService", "noticeAction", result);
        MsgSender.sendMsg2Player(vo, users);
    }


    //===========================================
    //==============以下为准备代码================
    //===========================================

    public PlayerYSZ getGameTypePlayerCardInfo() {
        switch (room.getGameType()) {
            case "1":
                return new PlayerYSZ();
            default:
                return new PlayerYSZ();
        }
    }


    /**
     * 洗牌
     */
    protected void shuffle() {
        for (int i = 1; i < 53; i++) {
            cards.add(i);
        }
        Collections.shuffle(cards);
    }

    /**
     * 发牌
     */
    protected void deal() {
        for (PlayerYSZ playerCardInfo : playerCardInfos.values()) {
            for (int i = 0; i < INIT_CARD_NUM; i++) {
                playerCardInfo.handcards.add(cards.remove(0));
            }
            //通知发牌
            MsgSender.sendMsg2Player(new ResponseVo("gameService", "deal", playerCardInfo.handcards), playerCardInfo.userId);
        }

        //底牌
        leaveCards.addAll(cards);
    }

    /**
     * 下一个操作人
     *
     * @param curId
     * @return
     */
    protected Long nextActioner(long curId) {
        logger.info("");
        int index = aliveUser.indexOf(curId);

        int nextId = index + 1;
        if (nextId >= aliveUser.size()) {
            nextId = 0;
        }
        if (getMaxRoundNumberB() || aliveUser.size() == 1) {
            campareAllCards();
        }
        return aliveUser.get(nextId);
    }


    /**
     * 比较所有人牌型
     *
     * @param
     * @return
     */
    protected void campareAllCards() {

        logger.info("比较所有人牌型");
        ArrayList<Player> list = new ArrayList<>();
        ArrayList<Long> winList = new ArrayList<>();
        ArrayList<Player> winnerList = null;
        for (Long l : aliveUser) {
            Player p = new Player(l, ArrUtils.cardCode.get(playerCardInfos.get(l).getHandcards().get(0)), ArrUtils.cardCode.get(playerCardInfos.get(l).getHandcards().get(1)), ArrUtils.cardCode.get(playerCardInfos.get(l).getHandcards().get(2)));
            list.add(p);
        }

        if (list.size() == 5) {
            winnerList = Player.findWinners(list.get(0), list.get(1), list.get(2), list.get(3), list.get(4));
        } else if (list.size() == 4) {
            winnerList = Player.findWinners(list.get(0), list.get(1), list.get(2), list.get(3));
        } else if (list.size() == 3) {
            winnerList = Player.findWinners(list.get(0), list.get(1), list.get(2));
        } else if (list.size() == 2) {
            winnerList = Player.findWinners(list.get(0), list.get(1));
        } else if (list.size() == 1){
            winList.add(list.get(0).getUid());
        }

        for (Player p : winnerList) {
            winList.add(p.getUid());
        }
        Map<String, Object> result = new HashMap<>();
        result.put("winList", winList);
        ResponseVo vo = new ResponseVo("gameService", "campareAllCards", result);
        MsgSender.sendMsg2Player(vo, users);

        //处理结果
        compute(winList);
        sendResult();
        genRecord();

        room.setBankerId(winList.get(0));
        room.clearReadyStatus(true);
        sendFinalResult();
    }


    //===========================================
    //==============get，set================
    //===========================================


    public Random getRand() {
        return rand;
    }

    public void setRand(Random rand) {
        this.rand = rand;
    }

    public int getCurRoundNumber() {
        return curRoundNumber;
    }

    public void setCurRoundNumber(int curRoundNumber) {
        this.curRoundNumber = curRoundNumber;
    }

    public Double getChip() {
        return chip;
    }

    public void setChip(Double chip) {
        this.chip = chip;
    }

    public List<Integer> getLeaveCards() {
        return leaveCards;
    }

    public void setLeaveCards(List<Integer> leaveCards) {
        this.leaveCards = leaveCards;
    }

    public List<Long> getAliveUser() {
        return aliveUser;
    }

    public void setAliveUser(List<Long> aliveUser) {
        this.aliveUser = aliveUser;
    }

    public List<Long> getSeeUser() {
        return seeUser;
    }

    public void setSeeUser(List<Long> seeUser) {
        this.seeUser = seeUser;
    }


    public RoomYSZ getRoom() {
        return room;
    }

    public GameBaseYSZ setRoom(RoomYSZ room) {
        this.room = room;
        return this;
    }

    public List<Integer> getCards() {
        return cards;
    }

    public void setCards(List<Integer> cards) {
        this.cards = cards;
    }

    public Map<Long, PlayerYSZ> getPlayerCardInfos() {
        return playerCardInfos;
    }

    public void setPlayerCardInfos(Map<Long, PlayerYSZ> playerCardInfos) {
        this.playerCardInfos = playerCardInfos;
    }

    public Long getCurUserId() {
        return curUserId;
    }

    public void setCurUserId(Long curUserId) {
        this.curUserId = curUserId;
    }


    public List<Long> getLoseUser() {
        return loseUser;
    }

    public void setLoseUser(List<Long> loseUser) {
        this.loseUser = loseUser;
    }

    @Override
    public IfaceGameVo toVo(long userId) {
        GameYSZVo vo = new GameYSZVo();
        //vo.cards = this.getCards();
        vo.chip = this.getChip();
        //vo.leaveCards = this.getLeaveCards();
        vo.aliveUser = this.getAliveUser();
        vo.seeUser = this.getSeeUser();
        vo.curUserId = this.getCurUserId();
        vo.curRoundNumber = getMaxRoundNumber();
        vo.loseUser = this.getLoseUser();
        vo.setZhuList(this.getGenZhuList());

        Double temp = 0.0;
        //玩家牌信息
        for (PlayerYSZ playerCardInfo : this.getPlayerCardInfos().values()) {
            if (seeUser.contains(playerCardInfo.getUserId())) {
                vo.playerCardInfos.put(playerCardInfo.userId, playerCardInfo.toVo(userId));
            } else {
                vo.playerCardInfos.put(playerCardInfo.userId, playerCardInfo.toVo());
            }
            temp += playerCardInfo.getAllScore();
        }
        vo.allTableChip = temp;
        return vo;
    }

    public int getMaxRoundNumber() {
        int max = 1;
        for (Long l : playerCardInfos.keySet()) {
            if (playerCardInfos.get(l).getCurRoundNumber() > max) {
                max = playerCardInfos.get(l).getCurRoundNumber();
            } else {
                max = max;
            }
        }
        return max;
    }

    /**
     * 判断是否大于最大轮数
     *
     * @return
     */
    public boolean getMaxRoundNumberB() {
        boolean maxRound = false;
        int tempCount = 0;
        for (Long l : playerCardInfos.keySet()) {
            if (aliveUser.contains(l) && playerCardInfos.get(l).getCurRoundNumber() == room.getCricleNumber()) {
                tempCount += 1;
            }
        }
        logger.info("");
        if (tempCount == aliveUser.size()) {
            maxRound = true;
        }
        return maxRound;
    }


    //======================================
    //==========  作弊相关方法  =============
    //======================================

    /**
     * 换牌
     *
     * @param before
     * @param after
     */
    public void changeCard(Long userId, List<Integer> before, List<Integer> after) {
        leaveCards.removeAll(after);
        leaveCards.addAll(before);
        playerCardInfos.get(userId).handcards.removeAll(before);
        playerCardInfos.get(userId).handcards.addAll(after);
    }


}