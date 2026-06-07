package code.name.monkey.retromusic.javatube;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import org.json.*;

public class Youtube {

    private final String urlVideo;
    private final String watchUrl;
    private JSONObject vidInfo = null;
    private String html = null;
    private String js = null;
    private String playerJs = null;

    public Youtube(String url) throws Exception {
        urlVideo = url;
        watchUrl = "https://www.youtube.com/watch?v=" + videoId();
    }

    private String videoId() throws Exception {
        Pattern pattern = Pattern.compile("(?:v=|/)([0-9A-Za-z_-]{11}).*");
        Matcher matcher = pattern.matcher(urlVideo);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            throw new Exception("RegexMatcherError. Unable to find video information: " + pattern);
        }
    }

    // Método de red local nativo para eliminar la dependencia de la clase Request externa
    private String httpRequest(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        String inputLine;
        StringBuilder response = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine).append("\n");
        }
        in.close();
        return response.toString();
    }

    private String setHtml() throws Exception {
        return httpRequest(watchUrl);
    }

    private String getHtml() throws Exception {
        if(html == null){
            html = setHtml();
        }
        return html;
    }

    private static JSONArray applyDescrambler(JSONObject streamData) throws JSONException {
        JSONArray formats = new JSONArray();
        if (streamData.has("formats")) {
            for(int i = 0; streamData.getJSONArray("formats").length() > i; i++){
                formats.put(streamData.getJSONArray("formats").get(i));
            }
        }
        if (streamData.has("adaptiveFormats")) {
            for(int i = 0; streamData.getJSONArray("adaptiveFormats").length() > i; i++){
                formats.put(streamData.getJSONArray("adaptiveFormats").get(i));
            }
        }
        for(int i = 0; i < formats.length(); i++){
            if(formats.getJSONObject(i).has("signatureCipher")){
                String rawSig = formats.getJSONObject(i).getString("signatureCipher").replace("sp=sig", "");
                String[] parts = rawSig.split("&");
                for(int j = 0; j < parts.length; j++){
                    if(parts[j].startsWith("url=")){
                        formats.getJSONObject(i).put("url", parts[j].replace("url=", ""));
                    } else if(parts[j].startsWith("s=")){
                        formats.getJSONObject(i).put("s", parts[j].replace("s=", ""));
                    }
                }
            }
        }
        return formats;
    }

    private JSONObject setVidInfo() throws Exception {
        String pattern = "ytInitialPlayerResponse\\s=\\s(\\{\\\"responseContext\\\":.*?\\});</script>";
        Pattern regex = Pattern.compile(pattern);
        Matcher matcher = regex.matcher(getHtml());
        if(matcher.find()){
           return new JSONObject(matcher.group(1));
        } else {
            throw new Exception("RegexMatcherError: " + pattern);
        }
    }

    private JSONObject getVidInfo() throws Exception {
        if(vidInfo == null){
            vidInfo = setVidInfo();
        }
        return vidInfo;
    }

    private void checkAvailability() throws Exception {
        JSONObject status = getVidInfo().getJSONObject("playabilityStatus");
        if(status.has("liveStreamability")) {
            throw new Exception("Video is a live stream.");
        } else if(Objects.equals(status.getString("status"), "LOGIN_REQUIRED")){
            throw new Exception("This is a private video.");
        } else if(!Objects.equals(status.getString("status"), "OK")){
            throw new Exception(status.getString("reason"));
        }
    }

    private JSONObject streamData() throws Exception {
        checkAvailability();
        return getVidInfo().getJSONObject("streamingData");
    }

    private String decodeURL(String s) throws UnsupportedEncodingException {
        return URLDecoder.decode(s, StandardCharsets.UTF_8.name());
    }

    private ArrayList<Stream> fmtStreams() throws Exception {
        JSONArray streamManifest = applyDescrambler(streamData());
        ArrayList<Stream> fmtStream = new ArrayList<>();
        String title = getTitle();
        Stream video;
        Cipher cipher = new Cipher(getJs(), getYtPlayerJs());
        
        for (int i = 0; streamManifest.length() > i; i++) {
            JSONObject streamObj = streamManifest.getJSONObject(i);
            if(streamObj.has("signatureCipher")){
                String oldUrl = decodeURL(streamObj.getString("url"));
                streamObj.remove("url");
                streamObj.put("url", oldUrl + "&sig=" + cipher.getSignature(decodeURL(streamObj.getString("s")).split("(?!^)")));
            }

            String oldUrl = streamObj.getString("url");
            Matcher matcher = Pattern.compile("&n=(.*?)&").matcher(oldUrl);
            if (matcher.find()) {
                String newUrl = oldUrl.replaceFirst("&n=(.*?)&", "&n=" + cipher.calculateN(matcher.group(1)) + "&");
                streamObj.put("url", newUrl);
            }

            video = new Stream(streamObj, title);
            fmtStream.add(video);
        }
        return fmtStream;
    }

    private String setYtPlayerJs() throws Exception {
        Pattern pattern = Pattern.compile("(/s/player/[\\w\\d]+/[\\w\\d_/.\\-]+/base\\.js)");
        Matcher matcher = pattern.matcher(getHtml());
        if (matcher.find()) {
            return "https://youtube.com" + matcher.group(1);
        } else {
            throw new Exception("RegexMatcherError. Could not find playerJs: " + pattern);
        }
    }
    
    public String getYtPlayerJs() throws Exception {
        if(playerJs == null){
            playerJs = setYtPlayerJs();
        }
        return playerJs;
    }

    private String setJs() throws Exception {
        return httpRequest(getYtPlayerJs()).replace("\n", "");
    }
    
    private String getJs() throws Exception {
        if(js == null){
            js = setJs();
        }
        return js;
    }

    public String getUrl(){
        return watchUrl;
    }

    public String getTitle() throws Exception {
        return getVidInfo().getJSONObject("videoDetails").getString("title");
    }

    public String getDescription() throws Exception {
        return getVidInfo().getJSONObject("videoDetails").getString("shortDescription");
    }

    public String getPublishDate() throws Exception {
        Pattern pattern = Pattern.compile("(?<=itemprop=\"datePublished\" content=\")\\d{4}-\\d{2}-\\d{2}");
        Matcher matcher = pattern.matcher(getHtml());
        if (matcher.find()) {
            return matcher.group(0);
        } else {
            throw new Exception("RegexMatcherError. Unable to find publication date: " + pattern);
        }
    }

    public Integer length() throws Exception {
        return getVidInfo().getJSONObject("videoDetails").getInt("lengthSeconds");
    }

    public String getThumbnailUrl() throws Exception {
        JSONArray thumbnails = getVidInfo().getJSONObject("videoDetails")
                .getJSONObject("thumbnail")
                .getJSONArray("thumbnails");
        return thumbnails.getJSONObject(thumbnails.length() - 1).getString("url");
    }

    public Integer getViews() throws Exception {
        return Integer.parseInt(getVidInfo().getJSONObject("videoDetails").getString("viewCount"));
    }

    public String getAuthor() throws Exception {
        return getVidInfo().getJSONObject("videoDetails").getString("author");
    }

    public JSONArray getKeywords() throws Exception {
        try {
            return getVidInfo().getJSONObject("videoDetails").getJSONArray("keywords");
        } catch (JSONException e){
            return null;
        }
    }

    public ArrayList<Stream> getStreamsList() throws Exception {
        return fmtStreams();
    }
}

