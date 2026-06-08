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
    private String videoId = null;

    // Constructor con un solo argumento para mantener compatibilidad con tu HomeFragment.kt actual
    public Youtube(String url) throws Exception {
        urlVideo = url;
        watchUrl = "https://www.youtube.com/watch?v=" + videoId();
    }

    private String videoId() throws Exception {
        if (videoId == null) {
            Pattern pattern = Pattern.compile("(?:v=|/)([0-9A-Za-z_-]{11}).*");
            Matcher matcher = pattern.matcher(urlVideo);
            if (matcher.find()) {
                videoId = matcher.group(1);
            } else {
                throw new Exception("RegexMatcherError. Unable to find video ID: " + pattern);
            }
        }
        return videoId;
    }

    private String httpRequest(String urlString, Map<String, String> extraHeaders) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        
        // Simulación de cabeceras de cliente WEB moderno para saltar restricciones iniciales
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
        conn.setRequestProperty("Accept", "*/*");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        
        if (extraHeaders != null) {
            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                conn.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        
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
        return httpRequest(watchUrl, null);
    }

    private String getHtml() throws Exception {
        if(html == null){
            html = setHtml();
        }
        return html;
    }

    private JSONArray applyDescrambler(JSONObject streamData) throws Exception {
        JSONArray formats = new JSONArray();
        if (streamData.has("formats")) {
            for(int i = 0; i < streamData.getJSONArray("formats").length(); i++){
                formats.put(streamData.getJSONArray("formats").get(i));
            }
        }
        if (streamData.has("adaptiveFormats")) {
            for(int i = 0; i < streamData.getJSONArray("adaptiveFormats").length(); i++){
                formats.put(streamData.getJSONArray("adaptiveFormats").get(i));
            }
        }
        
        // Procesamos los bloques de cifrado adaptados de la lógica moderna de JavaTube
        for(int i = 0; i < formats.length(); i++){
            JSONObject formatObj = formats.getJSONObject(i);
            if(formatObj.has("signatureCipher")){
                String rawSig = formatObj.getString("signatureCipher");
                String[] parts = rawSig.split("&");
                for(String part : parts) {
                    if(part.startsWith("url=")) {
                        formatObj.put("url", decodeURL(part.substring(4)));
                    } else if(part.startsWith("s=")) {
                        formatObj.put("s", decodeURL(part.substring(2)));
                    }
                }
            } else if (!formatObj.has("url") && streamData.has("serverAbrStreamingUrl")) {
                formatObj.put("url", streamData.getString("serverAbrStreamingUrl"));
                formatObj.put("is_sabr", true);
            }
        }
        return formats;
    }

    private JSONObject setVidInfo() throws Exception {
        // Expresión regular actualizada para capturar el JSON dinámico actual de YouTube
        String pattern = "ytInitialPlayerResponse\\s=\\s(\\{\"responseContext\":.*?\\});(?:var|</script>)";
        Pattern regex = Pattern.compile(pattern);
        Matcher matcher = regex.matcher(getHtml());
        if(matcher.find()){
           return new JSONObject(matcher.group(1));
        } else {
            // Fallback al formato alternativo si el principal cambia de orden en la carga síncrona
            String fallbackPattern = "var\\sytInitialPlayerResponse\\s=\\s(\\{.*?\\});";
            Matcher fallbackMatcher = Pattern.compile(fallbackPattern).matcher(getHtml());
            if (fallbackMatcher.find()) {
                return new JSONObject(fallbackMatcher.group(1));
            }
            throw new Exception("RegexMatcherError: Impossible to extract player response layout.");
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
        String statusStr = status.optString("status", "");
        if(status.has("liveStreamability") || getVidInfo().getJSONObject("videoDetails").optBoolean("isLive", false)) {
            throw new Exception("Video is a live stream.");
        } else if(statusStr.equals("LOGIN_REQUIRED")){
            throw new Exception("This video requires authentication or is age-restricted.");
        } else if(!statusStr.equals("OK") && !statusStr.equals("")){
            throw new Exception(status.optString("reason", "Unknown extraction playback error."));
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
        String videoTitle = getTitle();
        Stream video;
        
        // Inicialización segura del Cipher pasándole los scripts descifrados mediante Rhino
        Cipher cipher = new Cipher(getJs());
        Pattern nSigPattern = Pattern.compile("&n=(.*?)&");
        
        for (int i = 0; streamManifest.length() > i; i++) {
            JSONObject streamObj = streamManifest.getJSONObject(i);
            
            if(streamObj.has("signatureCipher") || streamObj.has("s")){
                String oldUrl = streamObj.getString("url");
                String sig = streamObj.getString("s");
                streamObj.put("url", oldUrl + "&sig=" + cipher.getSignature(sig));
            }

            String oldUrl = streamObj.getString("url");
            Matcher matcher = nSigPattern.matcher(oldUrl);
            if (matcher.find()) {
                String nSig = matcher.group(1);
                String newUrl = oldUrl.replaceFirst("&n=(.*?)&", "&n=" + cipher.getNSig(nSig) + "&");
                streamObj.put("url", newUrl);
            }

            // Mapeo adaptado al constructor local de tu clase Stream(JSONObject, String)
            video = new Stream(streamObj, videoTitle);
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
            throw new Exception("RegexMatcherError. Could not find playerJs source locator.");
        }
    }
    
    public String getYtPlayerJs() throws Exception {
        if(playerJs == null){
            playerJs = setYtPlayerJs();
        }
        return playerJs;
    }

    private String setJs() throws Exception {
        URL url = new URL(getYtPlayerJs());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        String inputLine;
        StringBuilder response = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        return response.toString();
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

    public Integer length() throws Exception {
        return getVidInfo().getJSONObject("videoDetails").getInt("lengthSeconds");
    }

    public ArrayList<Stream> getStreamsList() throws Exception {
        return fmtStreams();
    }
}
