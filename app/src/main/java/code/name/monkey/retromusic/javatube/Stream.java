package code.name.monkey.retromusic.javatube;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static java.lang.Math.min;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;

public class Stream {

    private final String title;
    private final String url;
    private final Integer itag;
    private final String mimeType;
    private final String codecs;
    private final String type;
    private final String subType;
    private final String videoCodec;
    private final String audioCodec;
    private final Integer bitrate;
    private final Boolean isOtf;
    private final long fileSize;
    private final Map<String, String> itagProfile;
    private final String abr;
    private Integer fps = null;
    private final String resolution;

    public Stream(JSONObject stream, String videoTitle) throws Exception {
        title = videoTitle;
        url = stream.getString("url");
        itag = stream.getInt("itag");
        mimeType = mimeTypeCodec(stream.getString("mimeType")).group(1);
        codecs = mimeTypeCodec(stream.getString("mimeType")).group(2);
        type = Arrays.asList(mimeType.split("/")).get(0);
        subType = Arrays.asList(mimeType.split("/")).get(1);
        videoCodec = parseCodecs().get(0);
        audioCodec = parseCodecs().get(1);
        bitrate = stream.getInt("bitrate");
        isOtf = setIsOtf(stream);
        fileSize = setFileSize(stream.has("contentLength") ? stream.getString("contentLength") : null);
        itagProfile = getFormatProfile();
        abr = itagProfile.get("abr");
        if(stream.has("fps")){
            fps = stream.getInt("fps");
        }
        resolution = itagProfile.get("resolution");
    }

    private long setFileSize(String size) throws IOException {
        if (size == null) {
            if(!isOtf){
                try {
                    URL destinationUrl = new URL(this.url);
                    HttpURLConnection http = (HttpURLConnection) destinationUrl.openConnection();
                    http.setRequestMethod("HEAD");
                    http.setRequestProperty("User-Agent", "Mozilla/5.0");
                    if (http.getHeaderFields().get("Content-Length") != null) {
                        size = http.getHeaderFields().get("Content-Length").get(0);
                    } else {
                        size = "0";
                    }
                    http.disconnect();
                } catch (Exception e) {
                    size = "0";
                }
            } else {
                size = "0";
            }
            return Long.parseLong(size);
        }
        return Long.parseLong(size);
    }

    private boolean setIsOtf(JSONObject stream) throws JSONException {
        if(stream.has("type")){
            return Objects.equals(stream.getString("type"), "FORMAT_STREAM_TYPE_OTF");
        } else {
            return false;
        }
    }

    public Boolean isAdaptive(){
        return (Arrays.asList(codecs.split(",")).size() % 2) == 1;
    }

    public Boolean isProgressive(){
        return !isAdaptive();
    }

    public Boolean includeAudioTrack(){
        return isProgressive() || Objects.equals(type, "audio");
    }

    public Boolean includeVideoTrack() { return isProgressive() || Objects.equals(type, "video"); }

    private ArrayList<String> parseCodecs(){
        ArrayList<String> array = new ArrayList<>();
        String video = null, audio = null;
        if(!isAdaptive()){
            video = Arrays.asList(codecs.split(",")).get(0);
            audio = Arrays.asList(codecs.split(",")).get(1);
        } else if(includeVideoTrack()){
            video = Arrays.asList(codecs.split(",")).get(0);
        } else if (includeAudioTrack()) {
            audio = Arrays.asList(codecs.split(",")).get(0);
        }
        array.add(video);
        array.add(audio);
        return array;
    }

    private Matcher mimeTypeCodec(String mimeTypeCodec) throws Exception {
        Pattern pattern = Pattern.compile("(\\w+/\\w+);\\scodecs=\"([a-zA-Z-0-9.,\\s]*)\"");
        Matcher matcher = pattern.matcher(mimeTypeCodec);
        if (matcher.find()) {
            return matcher;
        } else {
            throw new Exception("RegexMatcherError: " + pattern);
        }
    }

    public String safeFileName(String s){
        s = s.replaceAll("[\"'#$%*,.:;<>?\\\\^|~/]", " ");
        return s.replace(" ", "_");
    }

    private void checkFile(Context context, String filePath) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=?";
        String[] selectionArgs = new String[]{filePath};
        Cursor cursor = resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, null, selection, selectionArgs, null);
        if (cursor != null) {
            int count = cursor.getCount();
            cursor.close();
            if (count > 0) {
                throw new IOException("Failed to delete existing output file: " + filePath);
            }
        }
    }

    static long progress;
    public static void onProgress(long value){
        progress = value;
    }

    public static long getProgress() {
        return progress;
    }

    public void download(Context context, String path) throws Exception {
        startDownload(context, path, title, Stream::onProgress);
    }

    public void download(Context context, String path, Consumer<Long> progress) throws Exception {
        startDownload(context, path, title, progress);
    }

    public void download(Context context, String path, String fileName) throws Exception {
        startDownload(context, path, fileName, Stream::onProgress);
    }

    public void download(Context context, String path, String fileName, Consumer<Long> progress) throws Exception {
        startDownload(context, path, fileName, progress);
    }

    // Adaptación nativa del método de descarga por bloques (chunks) HTTP GET
    private void startDownload(Context context, String path, String fileName, Consumer<Long> progress) throws Exception {
        String savePath = path + safeFileName(fileName) + fileSize + "." + subType;
        if(!isOtf()){
            long startSize = 0;
            long stopPos;
            int defaultRange = 1048576; // 1MB chunks
            long progressPercentage;
            long lastPrintedProgress = 0;

            checkFile(context, savePath);
            File outputFile = new File(savePath);

            do {
                stopPos = min(startSize + defaultRange, fileSize);
                if (stopPos >= fileSize) {
                    stopPos = fileSize;
                }
                String chunkUrlStr = url + "&range=" + startSize + "-" + stopPos;
                
                // Ejecución nativa de la descarga del fragmento
                URL chunkUrl = new URL(chunkUrlStr);
                HttpURLConnection conn = (HttpURLConnection) chunkUrl.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                
                try (InputStream is = conn.getInputStream();
                     FileOutputStream fos = new FileOutputStream(outputFile, true)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long chunkBytesRead = 0;
                    while ((bytesRead = is.read(buffer)) != -null && bytesRead != -1) {
                        fos.write(buffer, 0, bytesRead);
                        chunkBytesRead += bytesRead;
                    }
                    startSize = startSize + chunkBytesRead;
                } finally {
                    conn.disconnect();
                }

                progressPercentage = (stopPos * 100L) / (fileSize);
                if (progressPercentage != lastPrintedProgress) {
                    lastPrintedProgress = progressPercentage;
                    progress.accept(progressPercentage);
                }
            } while (stopPos != fileSize);
        } else {
            downloadOtf(context, savePath, progress);
        }
    }

    // Adaptación nativa del método de descarga para Streams tipo OTF via HTTP POST
    private void downloadOtf(Context context, String savePath, Consumer<Long> progress) throws Exception {
        int countChunk = 0;
        int lastChunk = 0;

        File outputFile = new File(savePath);
        if (outputFile.exists()) {
            throw new IOException("Failed to create output file: File already exists");
        }

        try (FileOutputStream outputStream = new FileOutputStream(outputFile, true)) {
            do {
                String chunkUrlStr = url + "&sq=" + countChunk;
                URL chunkUrl = new URL(chunkUrlStr);
                HttpURLConnection conn = (HttpURLConnection) chunkUrl.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setDoOutput(true);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (InputStream is = conn.getInputStream()) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, bytesRead);
                    }
                } finally {
                    conn.disconnect();
                }
                byte[] chunkReceived = baos.toByteArray();

                if (countChunk == 0) {
                    Pattern pattern = Pattern.compile("Segment-Count: (\\d*)");
                    Matcher matcher = pattern.matcher(new String(chunkReceived));
                    if (matcher.find()) {
                        lastChunk = Integer.parseInt(matcher.group(1));
                    } else {
                        throw new Exception("RegexMatcherError: " + pattern);
                    }
                }
                progress.accept((countChunk * 100L) / (lastChunk));
                countChunk = countChunk + 1;
                outputStream.write(chunkReceived);
            } while (countChunk <= lastChunk);
        } catch (IOException e) {
            throw new Exception("Failed to write to output file", e);
        }
    }

    private Map<String, String> getFormatProfile(){
        Map<Integer, ArrayList<String>> itags = new HashMap<>();
        // progressive video
        itags.put(5, new ArrayList<>(Arrays.asList("240p", "64kbps")));
        itags.put(6, new ArrayList<>(Arrays.asList("270p", "64kbps")));
        itags.put(13, new ArrayList<>(Arrays.asList("144p", null)));
        itags.put(17, new ArrayList<>(Arrays.asList("144p", "24kbps")));
        itags.put(18, new ArrayList<>(Arrays.asList("360p", "96kbps")));
        itags.put(22, new ArrayList<>(Arrays.asList("720p", "192kbps")));
        itags.put(34, new ArrayList<>(Arrays.asList("360p", "128kbps")));
        itags.put(35, new ArrayList<>(Arrays.asList("480p", "128kbps")));
        itags.put(36, new ArrayList<>(Arrays.asList("240p", null)));
        itags.put(37, new ArrayList<>(Arrays.asList("1080p", "192kbps")));
        itags.put(38, new ArrayList<>(Arrays.asList("3072p", "192kbps")));
        itags.put(43, new ArrayList<>(Arrays.asList("360p", "128kbps")));
        itags.put(44, new ArrayList<>(Arrays.asList("480p", "128kbps")));
        itags.put(45, new ArrayList<>(Arrays.asList("720p", "192kbps")));
        itags.put(46, new ArrayList<>(Arrays.asList("1080p", "192kbps")));
        itags.put(139, new ArrayList<>(Arrays.asList(null, "48kbps")));
        itags.put(140, new ArrayList<>(Arrays.asList(null, "128kbps")));
        itags.put(141, new ArrayList<>(Arrays.asList(null, "256kbps")));
        itags.put(251, new ArrayList<>(Arrays.asList(null, "160kbps")));

        String res, bitrate;
        if(itags.containsKey(itag)){
            res = itags.get(itag).get(0);
            bitrate = itags.get(itag).get(1);
        } else {
            res = null;
            bitrate = null;
        }

        Map<String, String> returnItags = new HashMap<>();
        returnItags.put("resolution", res);
        returnItags.put("abr", bitrate);
        return returnItags;
    }

    public String getTitle(){ return title; }
    public String getUrl(){ return url; }
    public Integer getItag(){ return itag; }
    public String getMimeType(){ return mimeType; }
    public String getCodecs(){ return codecs; }
    public String getType(){ return type; }
    public String getSubType(){ return subType; }
    public String getVideoCodec(){ return videoCodec; }
    public String getAudioCodec(){ return audioCodec; }
    public Integer getBitrate(){ return bitrate; }
    public Boolean getIsOtf(){ return isOtf; }
    public long getFileSize(){ return fileSize; }
    public Map<String, String> getItagProfile(){ return itagProfile; }
    public String getAbr(){ return abr; }
    public Integer getFps(){ return fps; }
    public String getResolution(){ return resolution; }
}
