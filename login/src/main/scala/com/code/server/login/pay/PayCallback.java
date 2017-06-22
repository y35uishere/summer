package com.code.server.login.pay;


import com.code.server.constant.response.ResponseVo;
import com.code.server.db.Service.ChargeService;
import com.code.server.db.Service.UserService;
import com.code.server.db.model.Charge;
import com.code.server.db.model.User;
import com.code.server.login.kafka.MsgSender;
import com.code.server.login.util.ErrorCode;
import com.code.server.login.util.PayUtil;
import com.code.server.login.config.ServerConfig;
import org.dom4j.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;


@RestController
public class PayCallback {

    @Autowired
    private ChargeService chargeService ;
    @Autowired
    private UserService userService ;

    @Autowired
    private ServerConfig serverConfig;

    /**
     * 接受微信回调
     *
     * @param
     */
    @RequestMapping(value = "/callback")
    public String callback(HttpServletRequest request, HttpServletResponse response) {
        System.out.println("接受回调参数");
        //获取微信返回结果
        String parameterstr = PayUtil.ObtainParameterString(request);

        System.out.println("parameterstr:" + parameterstr);

        Element element = PayUtil.ParsingXML(parameterstr);//解析xmlString

        SortedMap<String, String> secondParams = new TreeMap<>();

        secondParams.put("appid", element.elementText("appid"));
        secondParams.put("bank_type", element.elementText("bank_type"));
        secondParams.put("cash_fee", element.elementText("cash_fee"));
        secondParams.put("fee_type", element.elementText("fee_type"));
        secondParams.put("is_subscribe", element.elementText("is_subscribe"));
        secondParams.put("mch_id", element.elementText("mch_id"));
        secondParams.put("nonce_str", element.elementText("nonce_str"));
        secondParams.put("openid", element.elementText("openid"));
        secondParams.put("out_trade_no", element.elementText("out_trade_no"));
        secondParams.put("result_code", element.elementText("result_code"));
        secondParams.put("return_code", element.elementText("return_code"));
        secondParams.put("time_end", element.elementText("time_end"));
        secondParams.put("total_fee", element.elementText("total_fee"));
        secondParams.put("trade_type", element.elementText("trade_type"));
        secondParams.put("transaction_id", element.elementText("transaction_id"));


        String paySign = PayUtil.createSign("UTF-8",serverConfig.getKey(), secondParams);

        System.out.println(paySign);

        //解析返回结果
        String returnXML = null;

        //通知客户端
        MsgSender msgSender = new MsgSender();

        //回调成功
        if ("SUCCESS".equals(element.elementText("return_code"))) {
            System.out.println("回调成功");
            //业务返回成功

            Charge charge = chargeService.getChargeByOrderid(element.elementText("out_trade_no"));
            if ("SUCCESS".equals(element.elementText("result_code"))) {
                System.out.println("业务成功");


                if (paySign.equals(element.elementText("sign")) && String.valueOf(charge.getMoney()).equals(String.valueOf(Integer.valueOf(element.elementText("cash_fee")) / 100))) {

                    if ("0".equals(charge.getStatus())) {
                        System.out.println("修改订单状态");
                        //修改支付订单状态 已支付

                        Charge charge1 = new Charge();
                        charge1.setStatus(1);
                        charge1.setOrderId(element.elementText("out_trade_no"));
                        chargeService.save(charge1);

                        //查询玩家
                        User user = userService.getUserByUserId(charge.getUserid());


                        System.out.println("修改玩家豆豆");
                        //修改玩家豆豆
                        User userupdate = new User();
                        userupdate.setMoney(user.getMoney() + Integer.valueOf(element.elementText("total_fee")) / 10);
                        userupdate.setId(user.getId());
                        userService.save(userupdate);
                        System.out.println("通知客户端刷新充值");
                        Map<String, String> rs = new HashMap<>();
                        msgSender.sendMsg2Player(new ResponseVo("userService", "refresh", rs), charge.getUserid());
                    }

		    			
		    			/*returnXML = "<xml>";
                        returnXML += "<return_code>![CDATA[SUCCESS]]</return_code>";
		    		    returnXML += "<return_msg>![CDATA[OK]]</return_msg>";
		    		    returnXML += "</xml>";*/

                    returnXML = "SUCCESS";
                }
            } else {

                //余额不足
                if ("NOTENOUGH".equals(element.elementText("err_code"))) {
                    Map<String, String> rs = new HashMap<>();
                    rs.put("err_code", element.elementText("err_code"));
                    rs.put("err_code_des", element.elementText("err_code_des"));
                    ResponseVo vo = new ResponseVo("userService", "refresh", rs);
                    vo.setCode(ErrorCode.BALANCE_INSUFFICIENT);
                    msgSender.sendMsg2Player(vo, charge.getUserid());
                    //订单已支付
                } else if ("ORDERPAID".equals(element.elementText("err_code"))) {
                    Map<String, String> rs = new HashMap<>();
                    rs.put("err_code", element.elementText("err_code"));
                    rs.put("err_code_des", element.elementText("err_code_des"));
                    ResponseVo vo = new ResponseVo("userService", "refresh", rs);
                    vo.setCode(ErrorCode.ORDER_WAS_PAID);
                    msgSender.sendMsg2Player(vo, charge.getUserid());
                    //订单已关闭
                } else if ("ORDERCLOSED".equals(element.elementText("err_code"))) {
                    Map<String, String> rs = new HashMap<>();
                    rs.put("err_code", element.elementText("err_code"));
                    rs.put("err_code_des", element.elementText("err_code_des"));
                    ResponseVo vo = new ResponseVo("userService", "refresh", rs);
                    vo.setCode(ErrorCode.ORDER_WAS_CLOSED);
                    msgSender.sendMsg2Player(vo, charge.getUserid());
                }


            }
        }
        System.out.println("回调结束，返回微信成功信息");

        System.out.println(returnXML);

        try {
            response.getWriter().write(returnXML);
            response.getWriter().flush();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }


        return returnXML;
    }

}