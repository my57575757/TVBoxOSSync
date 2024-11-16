package com.github.tvbox.osc.cache;

import android.os.StrictMode;
import android.text.TextUtils;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.data.AppDataManager;
import com.github.tvbox.osc.util.LOG;
import com.google.android.exoplayer2.util.Log;
import com.google.gson.ExclusionStrategy;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.orhanobut.hawk.Hawk;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author pj567
 * @date :2021/1/7
 * @description:
 */
public class RoomDataManger {
    static ExclusionStrategy vodInfoStrategy = new ExclusionStrategy() {
        @Override
        public boolean shouldSkipField(FieldAttributes field) {
            if (field.getDeclaringClass() == VodInfo.class && field.getName().equals("seriesFlags")) {
                return true;
            }
            if (field.getDeclaringClass() == VodInfo.class && field.getName().equals("seriesMap")) {
                return true;
            }
            return false;
        }

        @Override
        public boolean shouldSkipClass(Class<?> clazz) {
            return false;
        }
    };

    private static Gson getVodInfoGson() {
        return new GsonBuilder().addSerializationExclusionStrategy(vodInfoStrategy).create();
    }

    public static void insertVodRecord(String sourceKey, VodInfo vodInfo) {
        VodRecord record = AppDataManager.get().getVodRecordDao().getVodRecord(sourceKey, vodInfo.id);
        if (record == null) {
            record = new VodRecord();
        }
        record.sourceKey = sourceKey;
        record.vodId = vodInfo.id;
        record.updateTime = System.currentTimeMillis();
        record.dataJson = getVodInfoGson().toJson(vodInfo);
        AppDataManager.get().getVodRecordDao().insert(record);
        VodRecord finalRecord = record;
        new Thread(new Runnable() {
            @Override
            public void run() {
                //远端插入vod记录
                String user = Hawk.get(HawkConfig.PLAY_RECORD_USER, null);
                String url = Hawk.get(HawkConfig.PLAY_RECORD_URL, null);
                Gson gson = new GsonBuilder().registerTypeAdapter(Long.class, new TypeAdapter<Long>() {
                    @Override
                    public void write(JsonWriter out, Long value) throws IOException {
                        out.value(value.toString());
                    }

                    @Override
                    public Long read(JsonReader in) throws IOException {
                        return in.nextLong();
                    }
                }).create();
                Map<String,Object> param = gson.fromJson(gson.toJson(finalRecord), Map.class);
                param.put("userName",user);
                post(url+"/tvBox/addRecord",param);
            }
        }).start();
    }

    public static VodInfo getVodInfo(String sourceKey, String vodId) {
        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }
        //远端获取vod记录
        String user = Hawk.get(HawkConfig.PLAY_RECORD_USER, null);
        String url = Hawk.get(HawkConfig.PLAY_RECORD_URL, null);
        Gson gson = new GsonBuilder().registerTypeAdapter(Long.class, new TypeAdapter<Long>() {
            @Override
            public void write(JsonWriter out, Long value) throws IOException {
                out.value(value.toString());
            }

            @Override
            public Long read(JsonReader in) throws IOException {
                return in.nextLong();
            }
        }).create();
        Map param = new HashMap<>();
        param.put("userName",user);
        param.put("sourceKey",sourceKey);
        param.put("vodId",vodId);
        String res = post(url + "/tvBox/getRecord", param);

        VodRecord record = AppDataManager.get().getVodRecordDao().getVodRecord(sourceKey, vodId);
        if (res!=null&&!"".equals(res)){
            record = gson.fromJson(res, VodRecord.class);
        }
        try {
            if (record != null && record.dataJson != null && !TextUtils.isEmpty(record.dataJson)) {
                VodInfo vodInfo = getVodInfoGson().fromJson(record.dataJson, new TypeToken<VodInfo>() {
                }.getType());
                if (vodInfo.name == null)
                    return null;
                return vodInfo;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void deleteVodRecord(String sourceKey, VodInfo vodInfo) {
        VodRecord record = AppDataManager.get().getVodRecordDao().getVodRecord(sourceKey, vodInfo.id);
        if (record != null) {
            AppDataManager.get().getVodRecordDao().delete(record);
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                //远端删除vod记录
                String user = Hawk.get(HawkConfig.PLAY_RECORD_USER, null);
                String url = Hawk.get(HawkConfig.PLAY_RECORD_URL, null);
                Gson gson = new GsonBuilder().registerTypeAdapter(Long.class, new TypeAdapter<Long>() {
                    @Override
                    public void write(JsonWriter out, Long value) throws IOException {
                        out.value(value.toString());
                    }

                    @Override
                    public Long read(JsonReader in) throws IOException {
                        return in.nextLong();
                    }
                }).create();
                Map param = new HashMap();
                param.put("userName",user);
                param.put("sourceKey",sourceKey);
                param.put("vodId",vodInfo.id);
                post(url+"/tvBox/delRecord",param);
            }
        }).start();
    }

    public static List<VodInfo> getAllVodRecord(int limit) {
        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }
        //远端获取vod记录
        String user = Hawk.get(HawkConfig.PLAY_RECORD_USER, null);
        String url = Hawk.get(HawkConfig.PLAY_RECORD_URL, null);
        Gson gson = new GsonBuilder().registerTypeAdapter(Long.class, new TypeAdapter<Long>() {
            @Override
            public void write(JsonWriter out, Long value) throws IOException {
                out.value(value.toString());
            }

            @Override
            public Long read(JsonReader in) throws IOException {
                return in.nextLong();
            }
        }).create();

        Map param = new HashMap<>();
        param.put("userName",user);
        param.put("limit",String.valueOf(limit));
        String res = post(url + "/tvBox/getRecordAll", param);

        List<VodRecord> recordList;
        if (res!=null&&!"".equals(res)){
            VodRecord[] array = gson.fromJson(res,VodRecord[].class);
            recordList = Arrays.asList(array);
        }else {
            recordList = AppDataManager.get().getVodRecordDao().getAll(limit);
        }
        List<VodInfo> vodInfoList = new ArrayList<>();
        if (recordList != null) {
            for (VodRecord record : recordList) {
                VodInfo info = null;
                try {
                    if (record.dataJson != null && !TextUtils.isEmpty(record.dataJson)) {
                        info = getVodInfoGson().fromJson(record.dataJson, new TypeToken<VodInfo>() {
                        }.getType());
                        info.sourceKey = record.sourceKey;
                        SourceBean sourceBean = ApiConfig.get().getSource(info.sourceKey);
                        if (sourceBean == null || info.name == null)
                            info = null;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (info != null)
                    vodInfoList.add(info);
            }
        }
        return vodInfoList;
    }

    public static void insertVodCollect(String sourceKey, VodInfo vodInfo) {
        VodCollect record = AppDataManager.get().getVodCollectDao().getVodCollect(sourceKey, vodInfo.id);
        if (record != null) {
            return;
        }
        record = new VodCollect();
        record.sourceKey = sourceKey;
        record.vodId = vodInfo.id;
        record.updateTime = System.currentTimeMillis();
        record.name = vodInfo.name;
        record.pic = vodInfo.pic;
        AppDataManager.get().getVodCollectDao().insert(record);
        VodCollect finalRecord = record;
        new Thread(new Runnable() {
            @Override
            public void run() {
                //远端增加Collect
                String user = Hawk.get(HawkConfig.PLAY_RECORD_USER, null);
                String url = Hawk.get(HawkConfig.PLAY_RECORD_URL, null);
                Gson gson = new GsonBuilder().registerTypeAdapter(Long.class, new TypeAdapter<Long>() {
                    @Override
                    public void write(JsonWriter out, Long value) throws IOException {
                        out.value(value.toString());
                    }

                    @Override
                    public Long read(JsonReader in) throws IOException {
                        return in.nextLong();
                    }
                }).create();
                Map<String,Object> param = gson.fromJson(gson.toJson(finalRecord), Map.class);
                param.put("userName",user);
                post(url+"/tvBox/addCollect",param);
            }
        }).start();
    }

    public static void deleteVodCollect(int id,VodCollect vodInfo) {
        AppDataManager.get().getVodCollectDao().delete(id);
        new Thread(new Runnable() {
            @Override
            public void run() {
                //远端删除Collect
                String user = Hawk.get(HawkConfig.PLAY_RECORD_USER, null);
                String url = Hawk.get(HawkConfig.PLAY_RECORD_URL, null);
                new GsonBuilder().registerTypeAdapter(Long.class, new TypeAdapter<Long>() {
                    @Override
                    public void write(JsonWriter out, Long value) throws IOException {
                        out.value(value.toString());
                    }

                    @Override
                    public Long read(JsonReader in) throws IOException {
                        return in.nextLong();
                    }
                }).create();
                Map param = new HashMap();
                param.put("userName",user);
                param.put("sourceKey",vodInfo.sourceKey);
                param.put("vodId",vodInfo.vodId);
                post(url+"/tvBox/delCollect",param);
            }
        }).start();
    }

    public static void deleteVodCollect(String sourceKey, VodInfo vodInfo) {
        VodCollect record = AppDataManager.get().getVodCollectDao().getVodCollect(sourceKey, vodInfo.id);
        if (record != null) {
            AppDataManager.get().getVodCollectDao().delete(record);
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                //远端删除Collect
                String user = Hawk.get(HawkConfig.PLAY_RECORD_USER, null);
                String url = Hawk.get(HawkConfig.PLAY_RECORD_URL, null);
                new GsonBuilder().registerTypeAdapter(Long.class, new TypeAdapter<Long>() {
                    @Override
                    public void write(JsonWriter out, Long value) throws IOException {
                        out.value(value.toString());
                    }

                    @Override
                    public Long read(JsonReader in) throws IOException {
                        return in.nextLong();
                    }
                }).create();
                Map param = new HashMap();
                param.put("userName",user);
                param.put("sourceKey",vodInfo.sourceKey);
                param.put("vodId",vodInfo.id);
                post(url+"/tvBox/delCollect",param);
            }
        }).start();
    }
    
    public static void deleteVodCollectAll() {
        AppDataManager.get().getVodCollectDao().deleteAll();
        new Thread(new Runnable() {
            @Override
            public void run() {
                //远端删除Collect
                String user = Hawk.get(HawkConfig.PLAY_RECORD_USER, null);
                String url = Hawk.get(HawkConfig.PLAY_RECORD_URL, null);
                new GsonBuilder().registerTypeAdapter(Long.class, new TypeAdapter<Long>() {
                    @Override
                    public void write(JsonWriter out, Long value) throws IOException {
                        out.value(value.toString());
                    }

                    @Override
                    public Long read(JsonReader in) throws IOException {
                        return in.nextLong();
                    }
                }).create();
                Map param = new HashMap();
                param.put("userName",user);
                post(url+"/tvBox/delCollect",param);
            }
        }).start();
    }

    public static void deleteVodRecordAll() {
        AppDataManager.get().getVodRecordDao().deleteAll();
        new Thread(new Runnable() {
            @Override
            public void run() {
                //远端删除vod记录
                String user = Hawk.get(HawkConfig.PLAY_RECORD_USER, null);
                String url = Hawk.get(HawkConfig.PLAY_RECORD_URL, null);
                new GsonBuilder().registerTypeAdapter(Long.class, new TypeAdapter<Long>() {
                    @Override
                    public void write(JsonWriter out, Long value) throws IOException {
                        out.value(value.toString());
                    }

                    @Override
                    public Long read(JsonReader in) throws IOException {
                        return in.nextLong();
                    }
                }).create();
                Map param = new HashMap();
                param.put("userName",user);
                post(url+"/tvBox/delRecord",param);
            }
        }).start();
    }

    public static boolean isVodCollect(String sourceKey, String vodId) {
        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }
        VodCollect record;
        //远端获取Collect
        String user = Hawk.get(HawkConfig.PLAY_RECORD_USER, null);
        String url = Hawk.get(HawkConfig.PLAY_RECORD_URL, null);
        Gson gson = new GsonBuilder().registerTypeAdapter(Long.class, new TypeAdapter<Long>() {
            @Override
            public void write(JsonWriter out, Long value) throws IOException {
                out.value(value.toString());
            }

            @Override
            public Long read(JsonReader in) throws IOException {
                return in.nextLong();
            }
        }).create();

        Map param = new HashMap<>();
        param.put("userName",user);
        param.put("sourceKey",sourceKey);
        param.put("vodId",vodId);
        String res = post(url + "/tvBox/getCollect", param);
        if (res!=null&&!"".equals(res)){
            record = gson.fromJson(res,VodCollect.class);
        }else {
            record = AppDataManager.get().getVodCollectDao().getVodCollect(sourceKey, vodId);
        }
        return record != null;
    }

    public static List<VodCollect> getAllVodCollect() {
        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }
        //远端获取Collect
        String user = Hawk.get(HawkConfig.PLAY_RECORD_USER, null);
        String url = Hawk.get(HawkConfig.PLAY_RECORD_URL, null);
        Gson gson = new GsonBuilder().registerTypeAdapter(Long.class, new TypeAdapter<Long>() {
            @Override
            public void write(JsonWriter out, Long value) throws IOException {
                out.value(value.toString());
            }

            @Override
            public Long read(JsonReader in) throws IOException {
                return in.nextLong();
            }
        }).create();

        Map param = new HashMap<>();
        param.put("userName",user);
        String res = post(url + "/tvBox/getCollectAll", param);

        List<VodCollect> recordList = new ArrayList<>();
        if (res!=null&&!"".equals(res)){
            VodCollect[] array = gson.fromJson(res,VodCollect[].class);
            recordList = Arrays.asList(array);
        }else {
            recordList = AppDataManager.get().getVodCollectDao().getAll();
        }
        return recordList;
    }

    private static String post(String url, Map<String,Object> param) {
        String res = null;
        HttpURLConnection conn = null;
        Gson gson = new GsonBuilder().registerTypeAdapter(Long.class, new TypeAdapter<Long>() {
            @Override
            public void write(JsonWriter out, Long value) throws IOException {
                out.value(value.toString());
            }

            @Override
            public Long read(JsonReader in) throws IOException {
                return in.nextLong();
            }
        }).create();
        try {
            LOG.e("请求开始:"+url);
            URL urlU = new URL(url);
            conn = (HttpURLConnection) urlU.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);

            // 设置请求头，如Content-Type
            conn.setRequestProperty("Content-Type", "application/json");
            String postParams = gson.toJson(param);
            LOG.e("请求参数:"+postParams);
            byte[] outputInBytes = postParams.getBytes("UTF-8");
            OutputStream os = conn.getOutputStream();
            os.write(outputInBytes);
            os.close();
            // 获取响应码
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) { // 成功响应
                // 处理响应内容
                java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                res = response.toString();
            }
        }catch (Exception e){
            StringWriter stringWriter = new StringWriter();
            e.printStackTrace(new PrintWriter(stringWriter,true));
            LOG.e(e.toString());
            Log.e("TAG", "Exception occurred", e);
        }finally {
            if (conn!=null){
                conn.disconnect();
            }
        }
        LOG.e("请求返回值:"+res);
        return res;
    }
    private static String toJSONString(Map<String, Object> param) {
        StringBuffer str = new StringBuffer();
        str.append("{");
        if (param!=null && !param.isEmpty()){
            Set<Map.Entry<String, Object>> entries = param.entrySet();
            for (Map.Entry<String, Object> entry : entries) {
                str.append("\""+entry.getKey()+"\":"+"\""+entry.getValue()+"\",");
            }
            str.replace(str.length()-1,str.length(),"");
        }
        str.append("}");
        return str.toString();
    }
}