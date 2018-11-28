package com.zdd.risk.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.zdd.risk.bean.Certification;
import com.zdd.risk.dao.ICertificationDAO;
import com.zdd.risk.utils.HttpUtils;
import com.zdd.risk.utils.MD5Utils;
import com.zdd.risk.utils.SecurityUtil;
import com.zdd.risk.utils.rsa.RsaCodingUtil;
import org.rosuda.JRI.REXP;
import org.rosuda.JRI.Rengine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author 租无忧科技有限公司
 * @createtime 2016年9月26日下午8:47:14
 */
@Service
public class RModelServiceImpl implements  IRModelService{
    private static final Logger log = LoggerFactory.getLogger(RModelServiceImpl.class);
    private static Rengine re = null;
    private static final String LEVEL_A="A";
    private static final String LEVEL_D="D";
    private static final String LEVEL_E="E";

    @Autowired
    private ICertificationDAO iCertificationDAO;

    static {
        re = new Rengine(new String[] { "--vanilla" }, false, null);

        if (!(re.waitForR())) {
            System.out.println("Cannot load R");
        } else {

            re.eval("Sys.setlocale('LC_CTYPE','de_DE.utf8')");
            re.eval("load('/usr/src/score_dz.rdata')");
            re.eval("library(dplyr)");
            re.eval("library(dbplyr)");
            re.eval("library(magrittr)");
            re.eval("library(data.table)");
            re.eval("library(DBI)");
            re.eval("library(RJSONIO)");
            re.eval("library(jsonlite)");
            re.eval("library(RSQLite)");
            re.eval("library(log4r)");
        }
    }

    @Override
    public JSONObject callRJava(JSONObject paramJson){
        JSONObject result = new JSONObject();
        String infos =new StringBuffer("[").append(paramJson.toJSONString()).append("]").toString();
        log.info("java.library.path====="+System.getProperty("java.library.path"));

        String version =re.eval("R.version.string").asString();
        log.info("version="+version);

        re.assign("infos",infos);
        log.info("Rpara infos = "+re.eval("infos").asString());

        REXP x=re.eval("score_dz(infos)");
        log.info("R result="+ JSON.toJSONString(x));
        if(x!=null && x.getContent()!=null){

            Certification record = new Certification();
            record.setIdCard(paramJson.getString("id_card"));
            record.setMobile(paramJson.getString("tel"));
            record.setCertificationType(String.valueOf(1));
            record.setCertificationItem(infos);
            record.setCertificationResult(JSON.toJSONString(x.getContent()));
            record.setCertificationLimit(new Date());
            record.setFlag(0);
            record.setCreatTime(new Date());

            iCertificationDAO.insert(record);
            log.info("result"+JSONObject.toJSON(x.getContent()).toString());
            result =JSON.parseArray(JSONObject.toJSON(x.getContent()).toString()).getJSONObject(0);

        }
        re.end();


        log.info("end");
        return result;
    }
    @Override
    public JSONObject calculateModleData(JSONObject paramJson){
        log.info("计算模型入参 param= "+paramJson.toJSONString());
        JSONObject result = callRJava(paramJson);
        Map para = new HashMap();
        Map reMap = new HashMap();
        reMap.put("code","100000");
        reMap.put("codeMsg","");
        if(result!=null && result.get("score")!= null) {
            if(result.getDouble("score")>=600) {
                para = new HashMap();
                para.put("level", LEVEL_A);
                para.put("recommend", "低风险客户");
                reMap.put("data", para);
            }else{
                para = new HashMap();
                para.put("level", LEVEL_E);
                para.put("recommend", "高风险客户建议拒绝");
                reMap.put("data",para);
            }
        }else {
            para = new HashMap();
            para.put("level", LEVEL_D);
            para.put("recommend", "模型入参数据维度不足,建议人工");
            reMap.put("data",para);
        }
        return new JSONObject(reMap);
    }

}
