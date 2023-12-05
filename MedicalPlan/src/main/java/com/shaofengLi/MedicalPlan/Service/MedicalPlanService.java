package com.shaofengLi.MedicalPlan.Service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;

import java.util.*;

@Service
public class MedicalPlanService {
    private Jedis jedis;

    private ETagService eTagService;

//        private EtagService etag;
        public MedicalPlanService(Jedis jedis,ETagService eTagSerivce) {
            this.jedis = jedis;
            this.eTagService = eTagSerivce;
        }

        public Map<String, Map<String,Object>> CreateMedicalPlan(JSONObject plan) {
            Map<String,Map<String,Object>> map = new HashMap<>();
            Map<String,Object> contentMap = new HashMap<>();
            for(String key :plan.keySet()){
                String redisKey = plan.get("objectType") + ":" + plan.get("objectId");
                Object value = plan.get(key);
                if(value instanceof JSONObject){
                    value = CreateMedicalPlan((JSONObject) value);
                    jedis.sadd(redisKey + ":" + key,((Map<String,Map<String,Object>>) value).entrySet().iterator().next().getKey());
                }
                else if(value instanceof JSONArray){
                    value = jsonToList((JSONArray) value);
                    ((List<Map<String,Map<String,Object>>>) value)
                            .forEach((entry) -> {
                                entry.keySet()
                                        .forEach((listKey) -> {
                                            jedis.sadd(redisKey + ":" + key, listKey);
                                        });
                            });
                }
                else{
                    jedis.hset(redisKey,key,value.toString());
                    contentMap.put(key,value);
                    map.put(redisKey,contentMap);
                }
            }
            return map;
        }
        public String setETag(String ETag,String planId){
            jedis.hset(planId,"ETag",ETag);
            return ETag;
        }
        public String retriveETag(String key){
            return jedis.hget(key,"ETag");
        }
        public Map<String, Map<String,Object>> changeToJSON(JSONObject jsonObject){
            Map<String, Map<String, Object>> map = new HashMap<>();
            Map<String, Object> contentMap = new HashMap<>();

            for (String key : jsonObject.keySet()) {
                String redisKey = jsonObject.get("objectType") + ":" + jsonObject.get("objectId");
                Object value = jsonObject.get(key);

                if (value instanceof JSONObject) {
                    value = changeToJSON((JSONObject) value);
                    jedis.sadd(redisKey + ":" + key, ((Map<String, Map<String, Object>>) value).entrySet().iterator().next().getKey());
                } else if (value instanceof JSONArray) {
                    value = jsonToList((JSONArray) value);
                    ((List<Map<String, Map<String, Object>>>) value)
                            .forEach((entry) -> {
                                entry.keySet()
                                        .forEach((listKey) -> {
                                            jedis.sadd(redisKey + ":" + key, listKey);
                                        });
                            });
                } else {
                    jedis.hset(redisKey, key, value.toString());
                    contentMap.put(key, value);
                    map.put(redisKey, contentMap);
                }
            }
            return map;
        }
    public List<Object> jsonToList(JSONArray jsonArray) {
        List<Object> result = new ArrayList<>();
        for (Object value : jsonArray) {
            if (value instanceof JSONArray) value = jsonToList((JSONArray) value);
            else if (value instanceof JSONObject) value = changeToJSON((JSONObject) value);
            result.add(value);
        }
        return result;
    }
    public Map<String,Object> getMedicalPlan(String key){
        Set<String> keys = jedis.keys(key + ":*");
        keys.add(key);
        Map<String,Object> res = new HashMap<>();
        for (String temp : keys){
            if(temp.equals(key)){
                Map<String,String> object = jedis.hgetAll(temp);
                for (String objectKey:object.keySet()){
                    if(!objectKey.equalsIgnoreCase("ETag")){
                        res.put(objectKey, isInteger(object.get(objectKey)) ? Integer.parseInt(object.get(objectKey)) : object.get(objectKey));
                    }
                }
            }
            else{
                String newKey = temp.substring((key + ":").length());
                Set<String> members = jedis.smembers(temp);
                if (members.size() > 1 || newKey.equals("linkedPlanServices")) {
                    List<Object> listObj = new ArrayList<>();
                    for (String member : members) {
                        listObj.add(getMedicalPlan(member));
                    }
                    res.put(newKey, listObj);
                }
                else {
                    Map<String, String> object = jedis.hgetAll(members.iterator().next());
                    Map<String, Object> nestedMap = new HashMap<>();
                    for (String objKey : object.keySet()) {
                        nestedMap.put(objKey, isInteger(object.get(objKey)) ? Integer.parseInt(object.get(objKey)) : object.get(objKey));
                    }
                    res.put(newKey, nestedMap);
                }
            }
        }
        return res;
    }

    public void deleteMedicalPlan(String key){
        Set<String> keys = jedis.keys(key + ":*");
        keys.add(key);

        for (String temp : keys) {
            if (temp.equals(key)) {
                jedis.del(new String[]{temp});
            } else {
                Set<String> members = jedis.smembers(temp);
                if (members.size() > 1) {
                    for (String member : members) {
                        deleteMedicalPlan(member);
                    }
                } else {
                    jedis.del(new String[]{members.iterator().next(), temp});
                }
            }
           jedis.del(temp);
        }
    }
    public boolean contains(String key){
//        Map<String, String> value = jedis.hgetAll(key);
        return   jedis.exists(key);

    }
//    public boolean containsETag(String ETag){
//        return jedis.exists(ETag);
//    }
    public void updateMedicalPlan(String key,JSONObject plan){
        deleteMedicalPlan(key);
        CreateMedicalPlan(plan);
    }
    public boolean findObject(String headkey,String key){
        Set<String> keys = jedis.keys(headkey + ":*");
        Deque<String> q = new ArrayDeque<>(keys);
        while(!q.isEmpty()){
            String temp = q.pollFirst();
            if(jedis.smembers(temp).contains(key)){
                //update
                return true;
            }
            else{
                q.add(jedis.smembers(temp).iterator().next());
            }
        }
        return false;
    }
    private boolean isInteger(String str) {
        try {
            Integer.parseInt(str);
        } catch (Exception e) {
            return false;
        }
        return true;
    }
    public Map<String,String> getAllETag(){
        Set<String> keys = jedis.keys("*");
        Map<String,String> res = new HashMap<>();
        for (String key:keys){
            if("hash".equals(jedis.type(key))){
                if(jedis.hexists(key,"ETag")){
                    res.put(key,jedis.hget(key,"ETag"));
                }
            }
        }
        return res;
    }
}
