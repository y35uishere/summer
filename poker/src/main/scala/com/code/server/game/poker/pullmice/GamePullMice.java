package com.code.server.game.poker.pullmice;
import com.code.server.constant.response.*;
import com.code.server.game.room.Game;
import com.code.server.game.room.Room;
import com.code.server.game.room.kafka.MsgSender;
import com.code.server.game.room.service.RoomManager;
import com.code.server.util.IdWorker;
import groovy.util.logging.Slf4j;
import org.springframework.beans.BeanUtils;

import java.util.*;

@Slf4j
public class GamePullMice extends Game{

    protected static final String serviceName = "gamePullMiceService";

    protected RoomPullMice room;

    protected Integer state = PullMiceConstant.STATE_START;

    protected Map<Long, PlayerPullMice> playerCardInfos = new HashMap<Long, PlayerPullMice>();

    protected List<PlayerPullMice> pxList;

    protected List<Long> pxUsers = new ArrayList<>();

    protected Long playerCurrentId;

    protected boolean allFeng;

    protected long diZhu;

    @Override
    public IfaceGameVo toVo(long watchUser) {
        GamePullMiceVo vo = new GamePullMiceVo();
        vo.playerCurrentId = this.playerCurrentId;
        vo.pxList = this.pxList;
        vo.state = this.state;
        vo.playerCardInfos = new HashMap<>();
        vo.allFeng = this.allFeng;
        vo.diZhu = diZhu;
        for (Map.Entry<Long, PlayerPullMice> entry : playerCardInfos.entrySet()){
            PlayerPullMice playerPullMice = entry.getValue();

            PlayerPullMice player = new PlayerPullMice();
            BeanUtils.copyProperties(playerPullMice, player);

            List<Integer> list = CardUtils.transformLocalCards2ClientCards(playerPullMice.getCards());
            player.setCards(list);
            vo.playerCardInfos.put(entry.getKey(), player);
        }
        return vo;
    }

    public void startGame(List<Long> users, Room room){
        this.room = (RoomPullMice) room;
        this.users = users;

        Map res = new HashMap();
        this.diZhu = 1;
        res.put("diZhu", diZhu);
        MsgSender.sendMsg2Player(serviceName, "gamePullMiceBegin_", res, users);
        isNoticeClientShuffle();
        initPlayer();
        for (PlayerPullMice p : playerCardInfos.values()){
            p.setScore(-diZhu);
            this.room.potBottom += diZhu;
        }

        //先推送一下分数
        this.pushScoreChange();
        firstDeal();
    }

    public void initPlayer(){
        playerCardInfos.clear();
        for (Long uid : users){
            PlayerPullMice playerPullMice = new PlayerPullMice();
            playerPullMice.setUserId(uid);
            playerCardInfos.put(uid, playerPullMice);
        }
    }

    public void initCards(){
        room.cards.clear();
        for (int i = 0; i < 54; i++){
            room.cards.add(i);
        }
        for (int i = 0; i < 54; i++){
            room.cards.add(i);
        }
        Collections.shuffle(room.cards);
    }

    //第一次下注
    public void betStart(){
        state = PullMiceConstant.STATE_BET;
        PlayerPullMice playerCurrent = null;

        List<PlayerPullMiceVo> aList = new ArrayList<>();
        for (PlayerPullMice playerPullMice : this.pxList){
            if (playerPullMice.getPxId() == 1){
                playerCurrent = playerPullMice;
            }

            PlayerPullMice p = new PlayerPullMice();
            BeanUtils.copyProperties(playerPullMice, p);
            List<Integer> list = CardUtils.transformLocalCards2ClientCards(playerPullMice.getCards());
            p.setCards(list);
            aList.add((PlayerPullMiceVo) p.toVo());
        }

        betStartSender(playerCurrent.getUserId(), aList, null);
    }

    public void betStartSender(Long userId, List<PlayerPullMiceVo> aList, Object userInfo){

        this.playerCurrentId = userId;

        Map<Object, Object> result = new HashMap<>();

        if (userInfo != null){
            result.put("userInfo", userInfo);
        }
        result.put("userId", userId);
        result.put("result", aList);
        result.put("state", this.state);
        //此时pxId为1的先下注
        MsgSender.sendMsg2Player(serviceName, "betStart", result, userId);
    }

    public int fiveStepClose(Long userId, Integer zhu){
        state = PullMiceConstant.BET_WU_BU_FENG;

        updatePxList();
        Bet bet = new Bet();
        bet.setZhu(zhu);

        PlayerPullMice playerPullMice = null;
        long ret = 0;
        if (zhu == Bet.WU_BU_FENG){
            playerPullMice = this.pxList.get(0);
            playerPullMice.setScore(playerPullMice.getScore() - 5);
            this.room.potBottom += 5;
            ret = 5;

            playerPullMice.getBetList().add(bet);
        }else if(zhu == Bet.FENG){
            playerPullMice = this.playerCardInfos.get(userId);
            playerPullMice.setScore(playerPullMice.getScore() - 10);
            this.room.potBottom += 10;
            ret = 10;
            playerPullMice.getBetList().add(bet);

            //判断谁是第一个封的人

            boolean findIt = false;
            for (int i = 0; i < pxList.size(); i++){
                PlayerPullMice p = pxList.get(i);
                if (p.isAlreadyFeng()){
                    findIt = true;
                    break;
                }
            }

            if (findIt == false){
                playerPullMice.setAlreadyFeng(true);
            }

        }else if(zhu == Bet.FOLLOW){
            playerPullMice = this.playerCardInfos.get(userId);
            playerPullMice.setScore(playerPullMice.getScore() - 5);
            this.room.potBottom += 5;
            ret = 5;

            playerPullMice.getBetList().add(bet);
        }else if(zhu == Bet.ESCAPE){
            playerPullMice = this.playerCardInfos.get(userId);
            playerPullMice.setEscape(true);
            ret = 0;
        }

        Map<String, Long> res = new HashMap<>();
        res.put("userId", userId);
        res.put("ret", ret);
        res.put("currentScore", playerPullMice.getScore());
        res.put("potBottom", this.room.potBottom);

        MsgSender.sendMsg2Player(serviceName, "fiveStepCloseResult", res, this.pxUsers);
        MsgSender.sendMsg2Player(serviceName, "fiveStepClose", "0", userId);

        //解决封的问题

        //圈数
        int count = 0;
        PlayerPullMice pp = pxList.get(0);
        count = pp.getBetList().size() - 3;

        boolean isOver = false;

        int escapeCount = 0;
        for (int i = 0; i < this.pxList.size(); i++){
            if (pxList.get(i).isEscape()){
                escapeCount++;
            }
        }

        if (escapeCount == users.size() - 1){
            isOver = true;
        }else{

            //判断如果第五轮所有人下注结束
            if (count == 5){

                isOver = true;
                for (int i = 1; i < this.pxList.size(); i++){
                    PlayerPullMice p = this.pxList.get(i);

                    if (p.isEscape() == true) continue;

                    if ( p.getBetList().size() != count - 3){
                        isOver = false;
                        break;
                    }
                }
            }

            //就判断封
            if (isOver == false){

                boolean isFinal = true;
                for (int i = 0; i < this.pxList.size(); i++){
                    PlayerPullMice p = this.pxList.get(i);

                    if (p.isEscape() == true){
                        continue;
                    }

                    Bet b = p.getBetList().get(p.getBetList().size() - 1);
                    if (b.getZhu() != Bet.FENG ){
                        isFinal = false;
                    }
                }

                if (isFinal){

                    //如果所有人都封了，
                    PlayerPullMice p = null;

                    for (int k = 0; k < this.pxList.size(); k++){
                        PlayerPullMice pullMice = this.pxList.get(k);
                        if (pullMice.getBetList().get(3).getZhu() == Bet.WU_BU_FENG){
                            p = pullMice;
                            break;
                        }
                    }

                    if (!p.isEscape()){
                        Bet b = p.getBetList().get(3);
                        if (b.getZhu() == Bet.WU_BU_FENG){
                            p.setScore(p.getScore() - 5);
                            this.room.potBottom += 5;
                        }
                    }

                    isOver = true;
                }


            }
        }

        if (isOver){
            //推送一下分数
            this.pushScoreChange();
            state = PullMiceConstant.STATE_OPEN;
            this.gameOver();
        }else {

            PlayerPullMice playerCurrent = playerCardInfos.get(userId);
            PlayerPullMice playerNext = null;

            for (PlayerPullMice p : pxList){
                if (p.getPxId() == (playerCurrent.getPxId() + 1) && p.isEscape() == false){
                    playerNext = p;
                    break;
                }
            }

            if (playerNext == null){
                playerNext = pxList.get(0);
            }

            List<PlayerPullMiceVo> aList = new ArrayList<>();
            for (PlayerPullMice playerPullMice_ : this.pxList){
                PlayerPullMice p = new PlayerPullMice();
                BeanUtils.copyProperties(playerPullMice_, p);
                List<Integer> list_ = CardUtils.transformLocalCards2ClientCards(playerPullMice_.getCards());
                p.setCards(list_);
                aList.add((PlayerPullMiceVo) p.toVo());

            }

            Map<Object,Object> userInfo = new HashMap<>();
            userInfo.put("count", count);
            boolean allFeng = true;
            if (count == 4 ){

                for (PlayerPullMice p: this.pxList){

                    if (p.isEscape()){
                        continue;
                    }

                    if (p.getBetList().size() - 3 != 4){
                        allFeng = false;
                        break;
                    }
                }
            }else if(count < 4){
                allFeng = false;
            }

            this.allFeng = allFeng;
            userInfo.put("allFeng", allFeng);

            betStartSender(playerNext.getUserId(),aList, userInfo);

        }

        System.out.println("---------Count =" + count);
        return 0;
    }

    /*
    *  这个方法不处理 五不封
    * */
    public int bet(Long userId, Integer zhu, int times){

        PlayerPullMice playerPullMice = playerCardInfos.get(userId);
        if (playerPullMice == null) return ErrorCode.NO_USER;

        if (playerPullMice.getBetList().size() != state - 2){
            return ErrorCode.ALREADY_BET;
        }

        //第一个发牌的人
        PlayerPullMice playerOne = null;

        int escapeCount = 0;
        for (int i = 0; i < this.pxList.size(); i++){
            PlayerPullMice p = this.pxList.get(i);
            if (p.isEscape() == false){
                if (playerOne == null){
                    playerOne = p;
                }
            }else {
                escapeCount++;
            }
        }

        Bet bet = new Bet();
        bet.setZhu(zhu);
        //取出player最后一次下的注
        if (zhu == Bet.FOLLOW){
            Bet  bet_ = playerOne.getBetList().get(playerOne.getBetList().size() - 1);
            bet.setZhu(bet_.getZhu());
            zhu = bet_.getZhu();
        }

        playerPullMice.getBetList().add(bet);

        long ret = 0;
        if (zhu == Bet.YI){
            ret = 1;
        }else if(zhu == Bet.ER){
            ret = 2;
        }else if(zhu == Bet.SAN){
            ret = 3;
        }else if(zhu == Bet.SI){
            ret = 4;
        }else if(zhu == Bet.WU){
            ret = 5;
        }else if(zhu == Bet.ESCAPE){
            playerPullMice.setEscape(true);
            escapeCount++;
        }

        playerPullMice.setScore(playerPullMice.getScore() - ret);
        this.room.potBottom += ret;

        Map<String, Long> res = new HashMap<>();
        res.put("userId", userId);
        res.put("ret", ret);
        res.put("currentScore", playerPullMice.getScore());
        res.put("potBottom", this.room.potBottom);

        if (times == 1){
            MsgSender.sendMsg2Player(serviceName, "betResult1", res, this.pxUsers);
            MsgSender.sendMsg2Player(serviceName, "bet1", "0", userId);
        }else if(times == 2){
            MsgSender.sendMsg2Player(serviceName, "betResult2", res, this.pxUsers);
            MsgSender.sendMsg2Player(serviceName, "bet2", "0", userId);
        }else if(times == 3){
            MsgSender.sendMsg2Player(serviceName, "betResult3", res, this.pxUsers);
            MsgSender.sendMsg2Player(serviceName, "bet3", "0", userId);
        }else if(times == 4){
            MsgSender.sendMsg2Player(serviceName, "betResult4", res, this.pxUsers);
            MsgSender.sendMsg2Player(serviceName, "bet4", "0", userId);
        }

        boolean isDeal = true;

        if (escapeCount == this.users.size() - 1){

            state = PullMiceConstant.STATE_OPEN;

            this.gameOver();

            return 0;

        }else {

            for (PlayerPullMice player : playerCardInfos.values()){

                if (player.isEscape()){
                    continue;
                }

                //第一次下注
                if (state == PullMiceConstant.STATE_BET){
                    if (player.getBetList().size() != 1){
                        isDeal = false;
                        break;
                    }

                } else if (state == PullMiceConstant.BET_FIRST){
                    if (player.getBetList().size() != 2){
                        isDeal = false;
                        break;
                    }
                }else if(state == PullMiceConstant.BET_SECOND){
                    if (player.getBetList().size() != 3){
                        isDeal = false;
                        break;
                    }
                }else if(state == PullMiceConstant.BET_THIRD){
                    //发第五张牌之后，第一个人如果没有选封
                    if (player.getBetList().size() != 4){
                        isDeal = false;
                        break;
                    }
                }
            }
        }

        if (isDeal){

            state++;

            if (state == PullMiceConstant.BET_FOURTH){

                state = PullMiceConstant.STATE_OPEN;

                this.gameOver();
                //算开牌

            }else {

                if (state == PullMiceConstant.BET_FIRST){
                    System.out.println("++++++++:" + "第1次下注结束" + "[" + state + "]");
                }else if(state == PullMiceConstant.BET_SECOND){
                    System.out.println("++++++++:" + "第2次下注结束" + "[" + state + "]");
                }else if(state == PullMiceConstant.BET_THIRD){
                    System.out.println("++++++++:" + "第3次下注结束" + "[" + state + "]");
                }

                updatePxList();
                //再发一张牌
                deal(this.pxList);

                updatePxList();

                PlayerPullMice playerCurrent = null;

                List<PlayerPullMiceVo> aList = new ArrayList<>();
                for (PlayerPullMice playerPullMice_ : this.pxList){
                    if (playerPullMice_.getPxId() == 1){
                        playerCurrent = playerPullMice_;
                    }

                    PlayerPullMice p = new PlayerPullMice();
                    BeanUtils.copyProperties(playerPullMice_, p);

                    List<Integer> list_ = CardUtils.transformLocalCards2ClientCards(playerPullMice_.getCards());
                    p.setCards(list_);

                    aList.add((PlayerPullMiceVo) p.toVo());
                }

                betStartSender(playerCurrent.getUserId(), aList, null);
            }

        }else {

            PlayerPullMice playerCurrent = playerCardInfos.get(userId);

            PlayerPullMice playerNext = null;

            for (PlayerPullMice p : pxList){
                if (p.getPxId() == playerCurrent.getPxId() + 1){
                    playerNext = p;
                    break;
                }
            }

            List<PlayerPullMiceVo> aList = new ArrayList<>();
            for (PlayerPullMice playerPullMice_ : this.pxList){

                PlayerPullMice p = new PlayerPullMice();
                BeanUtils.copyProperties(playerPullMice_, p);
                List<Integer> list_ = CardUtils.transformLocalCards2ClientCards(playerPullMice_.getCards());
                p.setCards(list_);
                aList.add((PlayerPullMiceVo) p.toVo());

            }

            this.updatePxList();

            betStartSender(playerNext.getUserId(), aList, null);

        }

        return 0;
    }

    //算分
    public void compute(){

        List<PlayerPullMice> list = new ArrayList<>();
        for (PlayerPullMice p : playerCardInfos.values()){
            list.add(p);
        }
        PlayerPullMice winner = CardUtils.findWinner(list);
        winner.setWinner(true);
        winner.setScore(winner.getScore() + this.room.potBottom);
        this.room.potBottom = 0;

        for (PlayerPullMice p : list){
            //一开始算过地注
            room.addUserSocre(p.getUserId(), p.getScore());
        }
    }

    /**
     * 牌局结果
     */
    public void sendResult(){

        List<PlayerPullMiceVo> list = new ArrayList<>();
        for (PlayerPullMice p : playerCardInfos.values()){
            list.add((PlayerPullMiceVo) p.toVo());
        }
        MsgSender.sendMsg2Player(serviceName, "gameResult", list, this.pxUsers);

        //推送一下分数
        this.pushScoreChange();
    }

    //生成战绩
    public void genRecord(){
        long id = IdWorker.getDefaultInstance().nextId();
        Map<Long, Double> map = new HashMap<>();
        for (Map.Entry<Long, PlayerPullMice> entry : playerCardInfos.entrySet()){
           PlayerPullMice p = entry.getValue();
            map.put(p.getUserId(), p.getScore() + 0.0);
        }
        genRecord(map, this.room, id);
    }

    public void gameOver(){
        compute();
        sendResult();
        genRecord();
        this.room.clearReadyStatus(true);
        if (this.room.curGameNumber == this.room.maxGameCount){
            sendFinalResult();
        }
    }

    public void sendFinalResult(){

        List<UserOfResult>  userOfResult =  this.room.getUserOfResult();
        GameOfResult gameOfResult = new GameOfResult();
        gameOfResult.setUserList(userOfResult);
        MsgSender.sendMsg2Player("gameService", "gamePullMiceFinalResult", gameOfResult, this.pxUsers);
        RoomManager.removeRoom(room.getRoomId());

        this.room.genRoomRecord();
    }

    //通知客户端洗牌
    public void isNoticeClientShuffle(){
        if (room.cards.size() == 0){
            initCards();
            MsgSender.sendMsg2Player(new ResponseVo(serviceName, "shuffle", this.room.cards.size()), this.pxUsers);
        }
    }

    public void updatePxList(){

        List<PlayerPullMice> list = new ArrayList<>();
        list.addAll(playerCardInfos.values());
        //确定发牌顺序
        CardUtils.calListPxId(list, users);
        //获取下注顺序的数组
        this.pxList = list;
        updatePxUsers();
    }

    public void firstDeal(){

        updatePxList();
        //发第一张牌
        deal(this.pxList);
        //发第二张明牌
        deal(this.pxList);

        updatePxList();
        //推送下注
        betStart();
    }

    public void deal(List<PlayerPullMice> list){

        for (int i = 0; i < list.size(); i++){
            PlayerPullMice player = playerCardInfos.get(users.get(i));
            if (player.isEscape() == true){
                continue;
            }
            //一张一张的发牌
            isNoticeClientShuffle();
            player.getCards().add(room.cards.remove(0));
            long point = CardUtils.calculateTotalPoint(player.getCards());
            player.setPoint(point);
            Map<Object, Object> res = new HashMap<>();
            res.put("userId", player.getUserId());
            res.put("cards", CardUtils.transformLocalCards2ClientCards(player.getCards()));
            res.put("cardsTotal", this.room.cards.size());
            MsgSender.sendMsg2Player(serviceName, "deal", res, this.pxUsers);
        }
    }

    //更新排序后对应的id数组
    public void updatePxUsers(){
        pxUsers.clear();
        for (PlayerPullMice p : this.pxList){
            pxUsers.add(p.getUserId());
        }
    }

    /*
    * 推送玩家分数
    * */
    public void pushScoreChange() {
        Map<Long, Double> userScores = new HashMap<>();
        userScores.putAll(this.room.userScores);
        MsgSender.sendMsg2Player(new ResponseVo("gameService", "scoreChangePullMice", userScores), this.getUsers());
    }
}
