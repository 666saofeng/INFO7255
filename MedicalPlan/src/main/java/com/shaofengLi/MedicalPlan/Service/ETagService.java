package com.shaofengLi.MedicalPlan.Service;

import org.json.JSONObject;
import org.springframework.data.redis.connection.Message;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;

@Service
public class ETagService {
    public String createETag(JSONObject json){
        String encoded = null;
        try{
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(json.toString().getBytes(StandardCharsets.UTF_8));
            encoded = Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e){
            e.printStackTrace();
        }
        return "\""+encoded+"\"";
    }
    public boolean checkETag(JSONObject json, List<String> etags){
        if(etags.isEmpty()){
            return false;
        }
        String encoded = null;
        return etags.contains(encoded);
    }
}
