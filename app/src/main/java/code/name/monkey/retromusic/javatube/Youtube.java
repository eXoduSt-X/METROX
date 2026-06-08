package code.name.monkey.retromusic.javatube;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
    private String js = null;
    private String playerJs = null;
    private String videoId = null;

    // Constructor con un solo argumento para mantener compatibilidad absoluta con tu HomeFragment.kt
    public Youtube(String url) throws Exception {
        this.urlVideo = url;
        this.watchUrl = "https://www.youtube.com/watch?v=" + videoId();
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

    /**
     * BYPASS MWEB: Atacamos la API de InnerTube camuflándonos como un navegador móvil.
     * Al usar el cliente MWEB, Google anula la exigencia del PoToken de la app nativa.
     */
    private String fetchPlayerResponseFromApi() throws Exception {
        URL innerTubeUrl = new URL("https://youtubei.googleapis.com/v1/player?key=");
        HttpURLConnection conn = (HttpURLConnection) innerTubeUrl.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        // User-Agent simulando el navegador del dispositivo móvil
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Redmi Note 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36");
        conn.setDoOutput(true);

        // Mutación a Payload Mobile Web
        JSONObject clientObj = new JSONObject();
        clientObj.put("clientName", "MWEB");
        clientObj.put("clientVersion", "2.20240308.00.00");
        clientObj.put("hl", "es");
        clientObj.put("gl", "US");

        JSONObject payload = new JSONObject();
        payload.put("videoId", videoId());
        
        JSONObject contextObj = new JSONObject();
        contextObj.put("client", clientObj);
        payload.put("context", contextObj);

        // Transmisión del JSON por el canal OutputStream
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
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

    private String getHtmlFallback() throws Exception {
        URL url = new URL(watchUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Redmi Note 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36");
        
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        String inputLine;
        StringBuilder response = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine).append("\n");
        }
        in.close();
        return response.toString();
    }

    private JSONArray applyDescrambler(JSONObject streamData) throws Exception {
        JSONArray formats = new JSONArray();
        if (streamData.has("formats")) {
            for (int i = 0; i < streamData.getJSONArray("formats").length(); i++) {
                formats.put(streamData.getJSONArray("formats").get(i));
            }
        }
        if (streamData.has("adaptiveFormats")) {
            for (int i = 0; i < streamData.getJSONArray("adaptiveFormats").length(); i++) {
                formats.put(streamData.getJSONArray("adaptiveFormats").get(i));
            }
        }
        
        for (int i = 0; i < formats.length(); i++) {
            JSONObject formatObj = formats.getJSONObject(i);
            if (formatObj.has("signatureCipher")) {
                String rawSig = formatObj.getString("signatureCipher");
                String[] parts = rawSig.split("&");
                for (String part : parts) {
                    if (part.startsWith("url=")) {
                        formatObj.put("url", decodeURL(part.substring(4)));
                    } else if (part.startsWith("s=")) {
                        formatObj.put("s", decodeURL(part.substring(2)));
                    }
                }
            } else if (formatObj.has("cipher")) {
                String rawSig = formatObj.getString("cipher");
                String[] parts = rawSig.split("&");
                for (String part : parts) {
                    if (part.startsWith("url=")) {
                        formatObj.put("url", decodeURL(part.substring(4)));
                    } else if (part.startsWith("s=")) {
                        formatObj.put("s", decodeURL(part.substring(2)));
                    }
                }
            }
        }
        return formats;
    }

    private JSONObject setVidInfo() throws Exception {
        try {
            // Intentamos la extracción directa por la API de la Web Móvil
            return new JSONObject(fetchPlayerResponseFromApi());
        } catch (Exception e) {
            // Fallback en caliente mediante Scraper móvil si la API sufre de Throttling temporal
            String htmlData = getHtmlFallback();
            String pattern = "ytInitialPlayerResponse\\s=\\s(\\{\"responseContext\":.*?\\});(?:var|</script>)";
            Pattern regex = Pattern.compile(pattern);
            Matcher matcher = regex.matcher(htmlData);
            if (matcher.find()) {
                return new JSONObject(matcher.group(1));
            }
            throw new Exception("Error crítico: No se pudo obtener respuesta válida de los servidores de YouTube.");
        }
    }

    private JSONObject getVidInfo() throws Exception {
        if (vidInfo == null) {
            vidInfo = setVidInfo();
        }
        return vidInfo;
    }

    private void checkAvailability() throws Exception {
        JSONObject status = getVidInfo().getJSONObject("playabilityStatus");
        String statusStr = status.optString("status", "");
        if (status.has("liveStreamability") || getVidInfo().getJSONObject("videoDetails").optBoolean("isLive", false)) {
            throw new Exception("Video is a live stream.");
        } else if (statusStr.equals("LOGIN_REQUIRED")) {
            throw new Exception("This video requires authentication or is age-restricted.");
        } else if (!statusStr.equals("OK") && !statusStr.equals("")) {
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
        
        Cipher cipher = new Cipher(getJs(), getYtPlayerJs());
        Pattern nSigPattern = Pattern.compile("&n=(.*?)&");
        
        for (int i = 0; streamManifest.length() > i; i++) {
            JSONObject streamObj = streamManifest.getJSONObject(i);
            
            if (streamObj.has("signatureCipher") || streamObj.has("s")) {
                String oldUrl = streamObj.getString("url");
                String sig = streamObj.getString("s");
                streamObj.put("url", oldUrl + "&sig=" + cipher.getSignature(sig));
            }

            String oldUrl = streamObj.optString("url", "");
            if (!oldUrl.isEmpty()) {
                Matcher matcher = nSigPattern.matcher(oldUrl);
                if (matcher.find()) {
                    String nSig = matcher.group(1);
                    String newUrl = oldUrl.replaceFirst("&n=(.*?)&", "&n=" + cipher.getNSig(nSig) + "&");
                    streamObj.put("url", newUrl);
                }
                
                video = new Stream(streamObj, videoTitle);
                fmtStream.add(video);
            }
        }
        return fmtStream;
    }

    private String setYtPlayerJs() throws Exception {
        Pattern pattern = Pattern.compile("(/s/player/[\\w\\d]+/[\\w\\d_/.\\-]+/base\\.js)");
        Matcher matcher = pattern.matcher(getHtmlFallback());
        if (matcher.find()) {
            return "https://youtube.com" + matcher.group(1);
        } else {
            // URL inyectada de respaldo por si el scraper sufriera bloqueos de DOM
            return "https://youtube.com/s/player/218080ff/player_ias.vflset/es_US/base.js";
        }
    }
    
    public String getYtPlayerJs() throws Exception {
        if (playerJs == null) {
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
        if (js == null) {
            js = setJs();
        }
        return js;
    }

    public String getUrl() {
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
