package com.shaofengLi.MedicalPlan.Controller;

import com.shaofengLi.MedicalPlan.Service.ETagService;
import com.shaofengLi.MedicalPlan.Service.MedicalPlanService;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class MedicalPlanController {
    private MedicalPlanService medicalPlanService;
    private final RabbitTemplate rabbitTemplate;
    private ETagService ETagService;
    public MedicalPlanController(MedicalPlanService medicalPlanService,ETagService ETagService,RabbitTemplate rabbitTemplate) {
        this.medicalPlanService = medicalPlanService;
        this.ETagService = ETagService;
        this.rabbitTemplate = rabbitTemplate;
    }
    @GetMapping(value = "/healthz")
    public ResponseEntity<?> healthz(){
        return new ResponseEntity<>("healthz",HttpStatus.OK);
    }
    @GetMapping(value = "/medicalPlan/{objectType}/{objectId}")
    public ResponseEntity<?> getPlan(@PathVariable String objectType,@PathVariable String objectId, @RequestHeader HttpHeaders headers) throws Exception{
        String key = objectType+ ":" + objectId;
        String etag = medicalPlanService.retriveETag(key);
        if (!medicalPlanService.contains(key)){
            return new ResponseEntity<>("plan not found",HttpStatus.NOT_FOUND);
        }
        try {
            List<String> ifNoneMatch = headers.getIfNoneMatch();
            if (ifNoneMatch != null && ifNoneMatch.contains(etag)){
                return new ResponseEntity<>(HttpStatus.NOT_MODIFIED);
            }
        } catch (Exception e) {
            return new ResponseEntity<>("etag format error",HttpStatus.BAD_REQUEST);
        }
        Map<String,Object> objectReturned = medicalPlanService.getMedicalPlan(key);
        if (objectType.equals("plan")){
            headers.setETag(etag);
            return new ResponseEntity<>(objectReturned,headers,HttpStatus.OK);

        }
        else {
            return new ResponseEntity<>("objectReturned",HttpStatus.OK);
        }
//        return new ResponseEntity<>("test",HttpStatus.OK);
    }
    @PostMapping(value = "/medicalPlan")
    public ResponseEntity<?> createPlan(@RequestBody String plan) throws Exception {
        if (plan == null || plan.isEmpty()) {
            throw  new Exception("plan is empty");
        }
//        try {
            JSONObject planJsonObject = new JSONObject(plan);
            JSONObject schemaJson = new JSONObject(new JSONTokener(new ClassPathResource("./schema.json").getInputStream()));
            Schema schema = SchemaLoader.load(schemaJson);
            try {
                schema.validate(planJsonObject);
            }
            catch (ValidationException e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
//            throw  new Exception("schema is not valid");
            }

            String planId ="plan:"+planJsonObject.getString("objectId");
            if(medicalPlanService.contains(planId)){
                return new ResponseEntity<>("plan already exists",HttpStatus.CONFLICT);
            }
            String etag = ETagService.createETag(planJsonObject);
            Map<String, Map<String,Object>> parsingPlan = medicalPlanService.CreateMedicalPlan(planJsonObject);
            HttpHeaders headersToSend = new HttpHeaders();
            headersToSend.setETag(etag);
            Map<String, String> message = new HashMap<>();
            message.put("operation", "create");
            message.put("body", plan);
            rabbitTemplate.convertAndSend("indexing-queue", message);
            medicalPlanService.setETag(etag,planId);
            return new ResponseEntity<> ("{\"objectId\": \"" + planJsonObject.getString("objectId") + "\"}", headersToSend, HttpStatus.CREATED);
//        }catch (Exception e){
//            throw  ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Exception());
//        }
    }
    @DeleteMapping(value = "/medicalPlan/{objectType}/{objectId}")
    public ResponseEntity<?> deletePlan(@PathVariable String objectId, @RequestHeader HttpHeaders headers,@PathVariable String objectType) {
        String key = objectType + ":" + objectId;
        String etag = medicalPlanService.retriveETag(key);
        if (!medicalPlanService.contains(key)){
            return new ResponseEntity<>("plan not found",HttpStatus.NOT_FOUND);
        }
        try {
            List<String> ifMatch = headers.getIfMatch();
            if(ifMatch.isEmpty() || !ifMatch.contains(etag)){
                return new ResponseEntity<>(HttpStatus.PRECONDITION_FAILED);
            }
        } catch (Exception e) {
            return new ResponseEntity<>("etag format error",HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> plan = medicalPlanService.getMedicalPlan(key);
        Map<String, String> message = new HashMap<>();
        message.put("operation", "delete");
        message.put("body",  new JSONObject(plan).toString());

        System.out.println("Sending message: " + message);
        rabbitTemplate.convertAndSend("indexing-queue", message);
        medicalPlanService.deleteMedicalPlan(key);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
    @PatchMapping(value = "/medicalPlan/{objectType}/{objectId}")
    public ResponseEntity<?> patchPlan(@PathVariable String objectId, @RequestHeader HttpHeaders headers,@PathVariable String objectType,@RequestBody String plan)throws Exception{
        String key = objectType+ ":" + objectId;
        JSONObject planJsonObject = new JSONObject(plan);

        if (plan == null || plan.isEmpty()) {
            throw  new Exception("plan is empty");
        }

        Map <String,String> allEtages = medicalPlanService.getAllETag();
        for (Map.Entry<String,String> entry : allEtages.entrySet()){
            String headkey = entry.getKey();
            String headEtag = entry.getValue();
            try {
                List<String> ifMatch = headers.getIfMatch();
                if(ifMatch==null || !ifMatch.contains(headEtag)){
                    HttpHeaders headersToSend = new HttpHeaders();
                    headersToSend.setETag(headEtag);
                    return new ResponseEntity<>(headersToSend,HttpStatus.PRECONDITION_FAILED);
                }
                if(medicalPlanService.findObject(headkey,key)){
                    Map<String, String> message = new HashMap<>();
                    Map<String, Object> oldPlan = medicalPlanService.getMedicalPlan(headkey);
                    message.put("operation", "delete");
                    message.put("body", new JSONObject(oldPlan).toString());
                    rabbitTemplate.convertAndSend("indexing-queue", message);

                    medicalPlanService.deleteMedicalPlan(key);
                    medicalPlanService.CreateMedicalPlan(planJsonObject);
                    Map<String,Object> headObject = medicalPlanService.getMedicalPlan(headkey);
                    message.put("operation", "create");
                    message.put("body", new JSONObject(headObject).toString());
                    rabbitTemplate.convertAndSend("indexing-queue", message);
                    String newEtag = ETagService.createETag(new JSONObject(headObject));
                    HttpHeaders headersToSend = new HttpHeaders();
                    headersToSend.setETag(newEtag);
                    medicalPlanService.setETag(newEtag,headkey);
                    return new ResponseEntity<>(headersToSend,HttpStatus.OK);
                }
            } catch (Exception e) {
                return new ResponseEntity<>("patch error",HttpStatus.BAD_REQUEST);
            }
        }
        return new ResponseEntity<>("test",HttpStatus.OK);

    }
    @PutMapping(value = "/medicalPlan/{objectType}/{objectId}")
    public ResponseEntity<?> updatePlan(@PathVariable String objectId, @RequestHeader HttpHeaders headers,@PathVariable String objectType,@RequestBody String plan) throws Exception{
        String key = objectType+ ":" + objectId;
        if (plan == null || plan.isEmpty()) {
            throw  new Exception("plan is empty");
        }
        JSONObject planJsonObject = new JSONObject(plan);
        JSONObject schemaJson = new JSONObject(new JSONTokener(new ClassPathResource("./schema.json").getInputStream()));
        Schema schema = SchemaLoader.load(schemaJson);
        try {
            schema.validate(planJsonObject);
        }
        catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
//            throw  new Exception("schema is not valid");
        }
        String planId ="plan:"+planJsonObject.getString("objectId");
        try {
            List<String> ifMatch = headers.getIfMatch();
            String curEtag = medicalPlanService.retriveETag(key);
            System.out.println(curEtag);
            System.out.println(ifMatch);
            if(ifMatch.isEmpty() || !ifMatch.contains(curEtag)){
                HttpHeaders headersToSend = new HttpHeaders();
                headersToSend.setETag(curEtag);
                return new ResponseEntity<>(headersToSend,HttpStatus.PRECONDITION_FAILED);
            }
        } catch (Exception e) {
            return new ResponseEntity<>("etag format error",HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> oldPlan = medicalPlanService.getMedicalPlan(key);
        Map<String, String> message = new HashMap<>();
        message.put("operation", "delete");
        message.put("body", new JSONObject(oldPlan).toString());
        rabbitTemplate.convertAndSend("indexing-queue", message);
        String etag = ETagService.createETag(planJsonObject);
        medicalPlanService.setETag(etag,planId);
        medicalPlanService.updateMedicalPlan(key,planJsonObject);
        HttpHeaders headersToSend = new HttpHeaders();
        headersToSend.setETag(etag);
        medicalPlanService.setETag(etag,planId);
        message.put("operation", "create");
        message.put("body", plan);
        rabbitTemplate.convertAndSend("indexing-queue", message);
        return new ResponseEntity<>(headersToSend,HttpStatus.OK);
    }

}
