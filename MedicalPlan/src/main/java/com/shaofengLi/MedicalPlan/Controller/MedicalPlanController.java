package com.shaofengLi.MedicalPlan.Controller;

import com.shaofengLi.MedicalPlan.Service.ETagService;
import com.shaofengLi.MedicalPlan.Service.MedicalPlanService;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.everit.json.schema.Schema;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
public class MedicalPlanController {
    private MedicalPlanService medicalPlanService;
    private ETagService ETagService;
    public MedicalPlanController(MedicalPlanService medicalPlanService,ETagService ETagService) {
        this.medicalPlanService = medicalPlanService;
        this.ETagService = ETagService;
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
            List<String> ifNoneMatch = headers.getIfNoneMatch();
            if(ifNoneMatch == null && ifNoneMatch.contains(etag)){
                return new ResponseEntity<>(HttpStatus.PRECONDITION_FAILED);
            }
        } catch (Exception e) {
            return new ResponseEntity<>("etag format error",HttpStatus.BAD_REQUEST);
        }

        medicalPlanService.deleteMedicalPlan(key);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
