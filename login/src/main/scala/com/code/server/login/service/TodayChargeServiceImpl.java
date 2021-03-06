package com.code.server.login.service;
import com.code.server.constant.game.AgentBean;
import com.code.server.db.Service.ChargeService;
import com.code.server.db.dao.IChargeDao;
import com.code.server.db.dao.IUserDao;
import com.code.server.db.model.Charge;
import com.code.server.db.model.User;
import com.code.server.login.action.DelegateRelataionAction;
import com.code.server.login.util.AgentUtil;
import com.code.server.login.vo.*;
import com.code.server.redis.service.RedisManager;
import com.code.server.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import scala.Char;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by dajuejinxian on 2018/5/14.
 */

@Service
public class TodayChargeServiceImpl implements TodayChargeService {

    @Autowired
    private IChargeDao chargeDao;

    @Autowired
    private IUserDao userDao;

    //提现
    public static final String CHARGE_TYPE_CASH = "11";
    //金额
    public static final int MONEY_TYPE = 0;

    public static final int GOLD_TYPE = 1;

    private static final Logger logger = LoggerFactory.getLogger(TodayChargeServiceImpl.class);


    class AgentItem{

        private boolean isAgent;
        private long parentId;
        private long selfId;

        public boolean isAgent() {
            return isAgent;
        }

        public void setAgent(boolean agent) {
            isAgent = agent;
        }

        public long getParentId() {
            return parentId;
        }

        public void setParentId(long parentId) {
            this.parentId = parentId;
        }

        public long getSelfId() {
            return selfId;
        }

        public void setSelfId(long selfId) {
            this.selfId = selfId;
        }

        @Override
        public String toString() {
            return "AgentItem{" +
                    "isAgent=" + isAgent +
                    ", parentId=" + parentId +
                    ", selfId=" + selfId +
                    '}';
        }
    }

    @Override
    public HomeChargeVo showCharge(Date start, Date end, long agentId) {

        String startStr = DateUtil.convert2DayString(start);
        String endStr = DateUtil.convert2DayString(end);
        OneLevelVo oneLevelVo = oneLevelCharges(start, end, agentId);
        TwoLevelVo twoLevelVo = twoLevelCharges(start, end, agentId);

        ThreeLevelVo threeLevelVo = threeLevelCharges(start, end, agentId);

        logger.info("=========================+++++++++++++++++++++++++++++");
        logger.info("threeLevelVo:{}", threeLevelVo);

        HomeChargeVo homeChargeVo = new HomeChargeVo();
        homeChargeVo.setOnelevel("" + oneLevelVo.getMoney());
        homeChargeVo.setTwoLevel("" + twoLevelVo.getMoney());
        homeChargeVo.setThreeLevel("" + threeLevelVo.getMoney());

        homeChargeVo.setOneLevelGold("" + oneLevelVo.getGold());
        homeChargeVo.setTwoLevelGold("" + twoLevelVo.getGold());
        homeChargeVo.setThreeLevelGold("" + threeLevelVo.getGold());

        homeChargeVo.setOneLevelVoList(oneLevelVo.getList());
        homeChargeVo.setTwoLevelInfoVoList(twoLevelVo.getList());
        homeChargeVo.setThreeLevelInfoVoList(threeLevelVo.getList());

        double total = oneLevelVo.getMoney() + twoLevelVo.getMoney() + threeLevelVo.getMoney();
        homeChargeVo.setTotal("" + total);

        double totalGold = oneLevelVo.getGold() + twoLevelVo.getGold() + threeLevelVo.getGold();
        homeChargeVo.setTotalGold("" + totalGold);

        double income = oneLevelVo.getMoney() * 0.6 + twoLevelVo.getMoney() * 0.1 + threeLevelVo.getMoney() * 0.1;
        income *= 0.94;
        logger.info("l1:{}",  oneLevelVo.getMoney());
        logger.info("l2:{}",  twoLevelVo.getMoney());
        logger.info("l3:{}",  threeLevelVo.getMoney());
        logger.info("income:{}", income);
        homeChargeVo.setIncome(income);
        homeChargeVo.setStart(startStr);
        homeChargeVo.setEnd(endStr);
        return homeChargeVo;
    }

    @Override
    public List<WaterRecordVo> waterRecords(long agentId) {

        Date start = DateUtil.getThisYearStart();
        Date end = new Date();
        List<Charge> list = getChargesByUseridInAndCreatetimeBetweenAndStatusIsAndRecharge_sourceIs(Arrays.asList(agentId),start, end, 1, CHARGE_TYPE_CASH);

        List<WaterRecordVo> voList = new ArrayList<>();
        for (Charge charge : list){

            WaterRecordVo vo = new WaterRecordVo();
            vo.setUid(charge.getUserid());
            vo.setMoney("¥" + charge.getMoney());
            vo.setTimeStamp(DateUtil.convert2String(charge.getCreatetime()));
            voList.add(vo);
        }
        return voList;
    }

    //直接玩家充值
    @Override
    public OneLevelVo oneLevelCharges(Date start, Date end, long agentId) {

        return oneLevelChargesNew(start, end, agentId);

//        AgentBean agentBean = RedisManager.getAgentRedisService().getAgentBean(agentId);
//        OneLevelVo oneLevelVo = new OneLevelVo();
//
//        //金额
//        double total = 0d;
//        //金币
//        double goldTotal = 0d;
//        List<OneLevelInfoVo> oneLevelInfoVoList = new ArrayList<>();
//        //获取自己和自己直接玩家的所有list
//        List<Long> aList = new ArrayList<>();
//        aList.add(agentBean.getId());
//        aList.addAll(agentBean.getChildList());
//
//        //查一下手下玩家
//        for (Long uid : aList){
//
//            //不要代理 只要玩家
////            if (uid != agentId && RedisManager.getAgentRedisService().isExit(uid)) continue;
//            if ( RedisManager.getAgentRedisService().isExit(uid)){
//                if (uid != agentId){
//                    continue;
//                }
//            }
//
//            User user = userDao.getUserById(uid);
//            if (user == null){
//                continue;
//            }
//
//            List<Charge> list = getChargesByUseridInAndCreatetimeBetweenAndStatusIsAndChargeTypeIn(Arrays.asList(uid), start, end, 1, Arrays.asList(MONEY_TYPE, GOLD_TYPE));
//
//            double totalMoney = 0d;
//            double totalGold = 0d;
//            for (Charge charge : list){
//                if (charge.getChargeType() == MONEY_TYPE){
//                    totalMoney += charge.getMoney();
//                }else {
//                    totalGold += charge.getMoney();
//                }
//            }
//
//            total += totalMoney;
//            goldTotal += totalGold;
//
//            OneLevelInfoVo oneLevelInfoVo = new OneLevelInfoVo();
//            oneLevelInfoVo.setUid(user.getId());
//            oneLevelInfoVo.setGold(goldTotal +"");
//            oneLevelInfoVo.setImage(user.getImage() + "/96");
//            oneLevelInfoVo.setUsername(user.getUsername());
//            oneLevelInfoVo.setMoney("" + totalMoney);
//            oneLevelInfoVoList.add(oneLevelInfoVo);
//        }
//
//        oneLevelVo.setMoney(total);
//        oneLevelVo.setGold(goldTotal);
//        oneLevelVo.setList(oneLevelInfoVoList);
//
//        return oneLevelVo;
    }

    @Override
    public TwoLevelVo twoLevelCharges(Date start, Date end, long agentId) {

//        AgentBean agentBean = RedisManager.getAgentRedisService().getAgentBean(agentId);
//        TwoLevelVo twoLevelVo = new TwoLevelVo();
//
//        //获取所有二级代理的id
//        List<Long> aList = new ArrayList<>();
//        for (Long id : agentBean.getChildList()){
//            if (RedisManager.getAgentRedisService().isExit(id)){
//                aList.add(id);
//            }
//        }
//
//        logger.info("==========agentId{}===aList:{}", agentId, aList);
//
//        double total = 0d;
//        double goldTotal = 0d;
//
//        //手下所有二级代理
//        for (Long delegateId : aList){
//
//            User user = userDao.getUserById(delegateId);
//            if (user == null) continue;
//
//            List<Charge> list = getChargesByUseridInAndCreatetimeBetweenAndStatusIsAndChargeTypeIn(Arrays.asList(delegateId), start, end, 1, Arrays.asList(MONEY_TYPE, GOLD_TYPE));
//            TwoLevelInfoVo twoLevelInfoVo = new TwoLevelInfoVo();
//
//            //计算金额
//            double totalMoney = 0d;
//            //计算金币
//            double totalGold = 0d;
//            for (Charge charge : list){
//                if (charge.getChargeType() == MONEY_TYPE){
//                    totalMoney += charge.getMoney();
//                }else {
//                    totalGold += charge.getMoney();
//                }
//            }
//
//            twoLevelInfoVo.setMoney("" + totalMoney);
//            twoLevelInfoVo.setGold("" + totalGold);
//            twoLevelInfoVo.setUid(user.getId());
//            twoLevelInfoVo.setImage(user.getImage() + "/96");
//            twoLevelInfoVo.setUsername(user.getUsername());
//            twoLevelVo.getList().add(twoLevelInfoVo);
//
//            total += totalMoney;
//            goldTotal += totalGold;
//
//            //二级代理手下直接用户
//            AgentBean twoLevelAgentBean = RedisManager.getAgentRedisService().getAgentBean(delegateId);
//            if (twoLevelAgentBean == null){
//                logger.info("异常数据:{}", delegateId);
//                continue;
//            }
//            for (Long uid : twoLevelAgentBean.getChildList()){
//
//                if (RedisManager.getAgentRedisService().isExit(uid)) continue;
//
////                User twoLevelUser = userDao.getUserById(uid);
//
//                User twoLevelUser = userDao.getUserById(twoLevelAgentBean.getId());
//
//                if (twoLevelUser == null) continue;
//
//                TwoLevelInfoVo infoVo = new TwoLevelInfoVo();
//                infoVo.setUsername(twoLevelUser.getUsername());
//                infoVo.setImage(twoLevelUser.getImage() + "/96");
//
//                List<Charge> twoLevelChargeList = getChargesByUseridInAndCreatetimeBetweenAndStatusIsAndChargeTypeIn(Arrays.asList(uid), start, end, 1, Arrays.asList(MONEY_TYPE, GOLD_TYPE));
//                double twoLevelUserTotal = 0;
//                double twoLevelUserGoldTotal = 0;
//                for (Charge charge : twoLevelChargeList){
//                    if (charge.getChargeType() == MONEY_TYPE){
//                        twoLevelUserTotal += charge.getMoney();
//                    }else {
//                        twoLevelUserGoldTotal += charge.getMoney();
//                    }
//                }
//                infoVo.setMoney("" + twoLevelUserTotal);
//                twoLevelVo.getList().add(infoVo);
//
//                total += twoLevelUserTotal;
//                goldTotal += twoLevelUserGoldTotal;
//
//
//            }
//        }
//
//        twoLevelVo.setMoney(total);
//        twoLevelVo.setGold(goldTotal);
//        return twoLevelVo;

        return twoLevelChargesNew(start, end, agentId);
    }

    @Override
    public ThreeLevelVo threeLevelCharges(Date start, Date end, long agentId) {

//        AgentBean agentBean = RedisManager.getAgentRedisService().getAgentBean(agentId);
//        ThreeLevelVo threeLevelVo = new ThreeLevelVo();
//
//        //所有的二级代理
//        List<Long> aList = new ArrayList<>();
//        for (Long uid : agentBean.getChildList()){
//            if (RedisManager.getAgentRedisService().isExit(uid)){
//                aList.add(uid);
//            }
//        }
//
//        logger.info("{}的所有二级代理{}", agentId, aList);
//
//        //所有的三级代理和三级代理手上的玩家
////        List<Long> bList = new ArrayList<>();
////        for (Long uid : aList){
////            if (RedisManager.getAgentRedisService().isExit(uid)){
////                // 二级代理的bean
////                AgentBean agent3Bean = RedisManager.getAgentRedisService().getAgentBean(uid);
////                List<Long> li = new ArrayList<>();
////                for (Long tid : agent3Bean.getChildList()){
////                   if ( RedisManager.getAgentRedisService().isExit(tid)){
////                       AgentBean ab = RedisManager.getAgentRedisService().getAgentBean(tid);
////                       li.addAll(ab.getChildList());
////                   }
////                }
////                bList.addAll(li);
////            }
////        }
//
//        List<AgentItem> bList = new ArrayList<>();
//        for (Long uid : aList){
//            if (RedisManager.getAgentRedisService().isExit(uid)){
//                AgentBean agent3Bean = RedisManager.getAgentRedisService().getAgentBean(uid);
//                List<AgentItem> li = new ArrayList<>();
//                for (Long tid : agent3Bean.getChildList()){
//                    if (RedisManager.getAgentRedisService().isExit(tid)){
//                        AgentBean ab = RedisManager.getAgentRedisService().getAgentBean(tid);
//                        for (Long iid : ab.getChildList()){
//                            AgentItem agentItem = new AgentItem();
//                            if(RedisManager.getAgentRedisService().isExit(iid)){
//                                agentItem.isAgent = true;
//                            }else {
//                                agentItem.isAgent = false;
//                            }
//                            agentItem.parentId = ab.getId();
//                            agentItem.selfId = iid;
//                            li.add(agentItem);
//                        }
//                    }
//                }
//                bList.addAll(li);
//            }
//        }
//
//        logger.info("{}的所有三级代理{}", agentId, bList);
//
//        double total = 0d;
//        double goldTotal = 0d;
//        for (AgentItem item : bList){
//
//            User user = userDao.getUserById(item.selfId);
//            if (user == null) continue;
//            List<Charge> chargeList = getChargesByUseridInAndCreatetimeBetweenAndStatusIsAndChargeTypeIn(Arrays.asList(item.selfId), start, end, 1, Arrays.asList(MONEY_TYPE, GOLD_TYPE));
//
//            logger.info(" 三级代理{}的订单{}", item.selfId, chargeList);
//
//            User agentUser = userDao.getUserById(item.parentId);
//            ThreeLevelInfoVo threeLevelInfoVo = new ThreeLevelInfoVo();
//            threeLevelInfoVo.setUsername(agentUser.getUsername());
//            threeLevelInfoVo.setImage(agentUser.getImage() + "/96");
//            threeLevelInfoVo.setUid(agentUser.getId());
//
////            ThreeLevelInfoVo threeLevelInfoVo = new ThreeLevelInfoVo();
////            threeLevelInfoVo.setUsername(user.getUsername());
////            threeLevelInfoVo.setImage(user.getImage() + "/96");
////            threeLevelInfoVo.setUid(user.getId());
//
//            double totalMoney = 0;
//            double totalGold = 0;
//            for (Charge charge : chargeList){
//                if (charge.getChargeType() == MONEY_TYPE){
//                    totalMoney += charge.getMoney();
//                }else {
//                    totalGold += charge.getMoney();
//                }
//            }
//            threeLevelInfoVo.setMoney("" + totalMoney);
//            threeLevelInfoVo.setGold("" + totalGold);
//            threeLevelVo.getList().add(threeLevelInfoVo);
//
//            goldTotal += totalGold;
//            total += totalMoney;
//        }
//
//        threeLevelVo.setMoney(total);
//        threeLevelVo.setGold(goldTotal);
//        return threeLevelVo;

        return threeLevelChargesNew(start, end, agentId);
    }

    @Override
    public HomeChargeVo showCharge(long agentId) {
        //今日
        Date start = DateUtil.getDayBegin();
        Date end = new Date();
        return showCharge(start, end, agentId);
    }

    @Override
    public OneLevelVo oneLevelCharges(long agentId) {
        //今日
        Date start = DateUtil.getDayBegin();
        Date end = new Date();
        return oneLevelCharges(start, end, agentId);
    }

    @Override
    public TwoLevelVo twoLevelCharges(long agentId) {
        Date start = DateUtil.getDayBegin();
        Date end = new Date();
        return twoLevelCharges(start, end, agentId);
    }

    @Override
    public ThreeLevelVo threeLevelCharges(long agentId) {
        Date start = DateUtil.getDayBegin();
        Date end = new Date();
        return threeLevelCharges(start, end, agentId);
    }

    @Override
    public double canBlance(long agentId) {
        AgentBean agentBean = RedisManager.getAgentRedisService().getAgentBean(agentId);
        return agentBean.getRebate();
    }

    public List<Charge> getChargesByUseridInAndCreatetimeBetweenAndStatusIsAndChargeTypeIn(List<Long> users, Date start, Date end, int status, List<Integer> list){

        Specification<Charge> specification = new Specification<Charge>() {
            @Override
            public Predicate toPredicate(Root<Charge> root, CriteriaQuery<?> query, CriteriaBuilder cb) {

                List<Predicate> predicateList = new ArrayList<>();
                predicateList.add(cb.between(root.get("createtime").as(Date.class), start, end));
                predicateList.add(cb.equal(root.get("status").as(Integer.class), status));
                predicateList.add(root.get("chargeType").as(Integer.class).in(list));
                predicateList.add(root.get("userid").as(Integer.class).in(users));
                Predicate[] p = new Predicate[predicateList.size()];
                query.where(cb.and(predicateList.toArray(p)));
                return query.getRestriction();
            }
        };
        List<Charge> chargeList = chargeDao.findAll(specification);
        return chargeList;
    }

    public List<Charge> getChargesByUseridInAndCreatetimeBetweenAndStatusIsAndRecharge_sourceIs(List<Long> users, Date start, Date end, int status, String sourceType){

        Specification<Charge> specification = new Specification<Charge>() {
            @Override
            public Predicate toPredicate(Root<Charge> root, CriteriaQuery<?> query, CriteriaBuilder cb) {

                List<Predicate> predicateList = new ArrayList<>();
                predicateList.add(cb.between(root.get("createtime").as(Date.class), start, end));
                predicateList.add(cb.equal(root.get("status").as(Integer.class), status));
                predicateList.add(cb.equal(root.get("recharge_source").as(String.class), sourceType));
                predicateList.add(root.get("userid").as(Integer.class).in(users));
                Predicate[] p = new Predicate[predicateList.size()];
                query.where(cb.and(predicateList.toArray(p)));
                return query.getRestriction();
            }
        };
        List<Charge> chargeList = chargeDao.findAll(specification);
        return chargeList;
    }

    public ThreeLevelVo threeLevelChargesNew(Date start, Date end, long agentId){
        List<Charge> list = findA3(agentId,start, end);
        //根据用户id对订单进行分组
        Map<Long, List<Charge>> groupBy = list.stream().collect(Collectors.groupingBy(Charge::getUserid));
        List<ThreeLevelInfoVo> oneLevelInfoVoList = new ArrayList<>();

        double total = 0d;

        for (Long id : groupBy.keySet()){

            List<Charge> chargeList = groupBy.get(id);

            double totalMoney = 0d;
            for (Charge charge : chargeList){
                totalMoney += charge.getMoney();
            }

            total += totalMoney;

            User user = userDao.findOne(id);
            ThreeLevelInfoVo oneLevelInfoVo = new ThreeLevelInfoVo();
            oneLevelInfoVo.setUsername(user.getUsername());
            oneLevelInfoVo.setMoney(totalMoney + "");
            oneLevelInfoVo.setUid(id);
            oneLevelInfoVoList.add(oneLevelInfoVo);

        }

        Collections.sort(oneLevelInfoVoList, new Comparator<ThreeLevelInfoVo>() {
            @Override
            public int compare(ThreeLevelInfoVo o1, ThreeLevelInfoVo o2) {
                if (o1.getUid() < o2.getUid()) {
                    return 1;
                }
                if (o1.getUid() == o2.getUid()) {
                    return 0;
                }
                return -1;
            }
        });

        ThreeLevelVo oneLevelVo = new ThreeLevelVo();
        oneLevelVo.setMoney(total);
        oneLevelVo.setList(oneLevelInfoVoList);
        return oneLevelVo;
    }

    public TwoLevelVo twoLevelChargesNew(Date start, Date end, long agentId){
        List<Charge> list = findA2(agentId, start, end);
        //根据用户id对订单进行分组
        Map<Long, List<Charge>> groupBy = list.stream().collect(Collectors.groupingBy(Charge::getUserid));

        List<TwoLevelInfoVo> oneLevelInfoVoList = new ArrayList<>();

        double total = 0d;

        for (Long id : groupBy.keySet()){

            List<Charge> chargeList = groupBy.get(id);

            double totalMoney = 0d;
            for (Charge charge : chargeList){
                totalMoney += charge.getMoney();
            }

            total += totalMoney;

            User user = userDao.findOne(id);
            TwoLevelInfoVo oneLevelInfoVo = new TwoLevelInfoVo();
            oneLevelInfoVo.setUsername(user.getUsername());
            oneLevelInfoVo.setMoney(totalMoney + "");
            oneLevelInfoVo.setUid(id);
            oneLevelInfoVoList.add(oneLevelInfoVo);

        }

        Collections.sort(oneLevelInfoVoList, new Comparator<TwoLevelInfoVo>() {
            @Override
            public int compare(TwoLevelInfoVo o1, TwoLevelInfoVo o2) {
                if (o1.getUid() < o2.getUid()) {
                    return 1;
                }
                if (o1.getUid() == o2.getUid()) {
                    return 0;
                }
                return -1;
            }
        });

       TwoLevelVo oneLevelVo = new TwoLevelVo();
       oneLevelVo.setMoney(total);
       oneLevelVo.setList(oneLevelInfoVoList);
       return oneLevelVo;
    }

    public OneLevelVo oneLevelChargesNew(Date start, Date end, long agentId){
        List<Charge> list = findA1(agentId, start, end);
        //根据用户id对订单进行分组
        Map<Long, List<Charge>> groupBy = list.stream().collect(Collectors.groupingBy(Charge::getUserid));

        List<OneLevelInfoVo> oneLevelInfoVoList = new ArrayList<>();

        double total = 0d;

        for (Long id : groupBy.keySet()){

            List<Charge> chargeList = groupBy.get(id);

            double totalMoney = 0d;
            for (Charge charge : chargeList){
                totalMoney += charge.getMoney();
            }

            total += totalMoney;

            User user = userDao.findOne(id);
            OneLevelInfoVo oneLevelInfoVo = new OneLevelInfoVo();
            oneLevelInfoVo.setUsername(user.getUsername());
            oneLevelInfoVo.setMoney(totalMoney + "");
            oneLevelInfoVo.setUid(id);
            oneLevelInfoVo.setImage(user.getImage());
            oneLevelInfoVoList.add(oneLevelInfoVo);

        }

        Collections.sort(oneLevelInfoVoList, new Comparator<OneLevelInfoVo>() {
            @Override
            public int compare(OneLevelInfoVo o1, OneLevelInfoVo o2) {
                if (o1.getUid() < o2.getUid()) {
                    return 1;
                }
                if (o1.getUid() == o2.getUid()) {
                    return 0;
                }
                return -1;
            }
        });

        OneLevelVo oneLevelVo = new OneLevelVo();
        oneLevelVo.setMoney(total);
        oneLevelVo.setList(oneLevelInfoVoList);

        return oneLevelVo;
    }

    public List<Charge> findA1(long agentId, Date start, Date end){
        return findAN(agentId, "a1", start, end);
    }

    public List<Charge> findA2(long agentId, Date start, Date end){
        return findAN(agentId, "a2", start, end);
    }

    public List<Charge> findA3(long agentId, Date start, Date end){
        return findAN(agentId, "a3", start, end);
    }

    //按顺序查询
    public List<Charge> findAN(long agentId, String pathValue, Date start, Date end){

        Specification<Charge> specification = new Specification<Charge>() {
            @Override
            public Predicate toPredicate(Root<Charge> root, CriteriaQuery<?> query, CriteriaBuilder cb) {

                List<Predicate> predicates = new ArrayList<>();
                predicates.add(cb.equal(root.get(pathValue).as(Long.class), agentId));
                predicates.add(cb.between(root.get("createtime").as(Date.class), start, end));
                predicates.add(cb.equal(root.get("status").as(Integer.class), 1));
                predicates.add(cb.equal(root.get("chargeType").as(Integer.class), MONEY_TYPE));;
                Predicate[] pre = new Predicate[predicates.size()];
                return query.where(predicates.toArray(pre)).getRestriction();
            }
        };

        Sort.Order order1 = new Sort.Order(Sort.Direction.ASC, "userid");
        Sort.Order order2 = new Sort.Order(Sort.Direction.ASC, "orderId");

        Sort sort = new Sort(order1, order2);
        List<Charge> list = chargeDao.findAll(specification, sort);
        return list;
    }
}
