package com.github.tvbox.osc.cache;

import android.os.StrictMode;

import com.github.tvbox.osc.data.AppDataManager;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.google.android.exoplayer2.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.orhanobut.hawk.Hawk;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 类描述:
 *
 * @author pj567
 * @since 2020/5/15
 */
public class CacheManager {
    //反序列,把二进制数据转换成java object对象
    private static Object toObject(byte[] data) {
        ByteArrayInputStream bais = null;
        ObjectInputStream ois = null;
        try {
            bais = new ByteArrayInputStream(data);
            ois = new ObjectInputStream(bais);
            return ois.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (bais != null) {
                    bais.close();
                }
                if (ois != null) {
                    ois.close();
                }
            } catch (Exception ignore) {
                ignore.printStackTrace();
            }
        }
        return null;
    }

    //序列化存储数据需要转换成二进制
    private static <T> byte[] toByteArray(T body) {
        ByteArrayOutputStream baos = null;
        ObjectOutputStream oos = null;
        try {
            baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(body);
            oos.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (baos != null) {
                    baos.close();
                }
                if (oos != null) {
                    oos.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return new byte[0];
    }

    public static <T> void delete(String key, T body) {
        Cache cache = new Cache();
        cache.key = key;
        cache.data = toByteArray(body);
        AppDataManager.get().getCacheDao().delete(cache);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String user = Hawk.get(HawkConfig.PLAY_RECORD_USER, null);
                    String url = Hawk.get(HawkConfig.PLAY_RECORD_URL, null);
                    if (body!=null && url!=null && user!=null){
                        Map<String,Object> param = new HashMap<>();
                        param.put("key",key);
                        param.put("userName",user);
                        //调用远端保存进度
                        post(url+"/tvBox/delCache",param);
                    }
                }catch (Exception e){

                }
            }
        }).start();
    }

    public static <T> void save(String key, T body) {
        Cache cache = new Cache();
        cache.key = key;
        cache.data = toByteArray(body);
        AppDataManager.get().getCacheDao().save(cache);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String user = Hawk.get(HawkConfig.PLAY_RECORD_USER, null);
                    String url = Hawk.get(HawkConfig.PLAY_RECORD_URL, null);
                    if (body!=null && url!=null && user!=null){
                        Map<String,Object> param = new HashMap<>();
                        param.put("key",key);
                        param.put("userName",user);
                        param.put("data",body.toString());
                        //调用远端保存进度
                        post(url+"/tvBox/addCache",param);
                    }
                }catch (Exception e){

                }
            }
        }).start();
    }

    public static Object getCache(String key) {
        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }
        Cache cache = AppDataManager.get().getCacheDao().getCache(key);
        try {
            String user = Hawk.get(HawkConfig.PLAY_RECORD_USER, null);
            String url = Hawk.get(HawkConfig.PLAY_RECORD_URL, null);
            if (url!=null && user!=null){
                //调用远端获取进度
                Map param = new HashMap();
                param.put("userName",user);
                param.put("key",key);
                String res = post(url+"/tvBox/getCache",param);
                if (res!=null&&!"".equals(res)){
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
                    Map<String,String> map = gson.fromJson(res, Map.class);
                    if (map.get("data")!=null){
                        return Long.parseLong(map.get("data").toString());
                    }
                }
            }
        }catch (Exception e){

        }
        if (cache != null && cache.data != null) {
            return toObject(cache.data);
        }
        return null;
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
