package at.emielregis.dathostdemomanager.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class WorkshopAnalyserController {

    private record PublishedMap(String name, String id, int subscriptions, int lifetimeSubscriptions,
                                int lifetimeFavorites, String previewUrl) {
    }

    @GetMapping("/api/collection/download")
    public Map<String, Object> downloadCollectionImages(@RequestParam String collectionId) {
        String[] ids = getPublishedMapIdsForCollection(collectionId);
        List<PublishedMap> mapList = new ArrayList<>();
        List<String> unparsedIds = new ArrayList<>();

        for (String id : ids) {
            PublishedMap map = getPublishedMapForId(id);
            if (map != null) {
                try {
                    downloadImage(map.previewUrl(), map.name());
                } catch (IOException e) {
                    System.err.println("Failed to download image for " + map.name() + ": " + e.getMessage());
                }
                mapList.add(map);
            } else {
                unparsedIds.add(id); // Accumulate unparsed IDs
            }
        }

        // Prepare the response map
        Map<String, Object> response = new HashMap<>();
        response.put("maps", mapList);
        response.put("unparsedIds", unparsedIds);

        return response; // Return both maps and unparsed IDs
    }


    private String[] getPublishedMapIdsForCollection(String id) {
        OkHttpClient client = new OkHttpClient();
        String url = "https://api.steampowered.com/ISteamRemoteStorage/GetCollectionDetails/v1/?format=json";
        RequestBody formBody = new FormBody.Builder()
                .add("collectioncount", "1")
                .add("publishedfileids[0]", id)
                .build();
        Request request = new Request.Builder().url(url).post(formBody).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            String responseData = response.body().string();
            return parseIds(responseData);
        } catch (Exception e) {
            e.printStackTrace();
        }
        throw new IllegalStateException();
    }

    private String[] parseIds(String responseData) {
        JsonObject jsonObject = JsonParser.parseString(responseData).getAsJsonObject();
        JsonArray childrenArray = jsonObject.getAsJsonObject("response")
                .getAsJsonArray("collectiondetails")
                .get(0).getAsJsonObject()
                .getAsJsonArray("children");
        String[] publishedFileIds = new String[childrenArray.size()];
        for (int i = 0; i < childrenArray.size(); i++) {
            JsonElement child = childrenArray.get(i);
            publishedFileIds[i] = child.getAsJsonObject().get("publishedfileid").getAsString();
        }
        return publishedFileIds;
    }

    private PublishedMap getPublishedMapForId(String id) {
        OkHttpClient client = new OkHttpClient();
        String url = "https://api.steampowered.com/ISteamRemoteStorage/GetPublishedFileDetails/v1/?format=json";
        RequestBody formBody = new FormBody.Builder()
                .add("itemcount", "1")
                .add("publishedfileids[0]", id)
                .build();
        Request request = new Request.Builder().url(url).post(formBody).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            String responseData = response.body().string();
            return parsePublishedMap(responseData, id);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private PublishedMap parsePublishedMap(String responseData, String id) {
        JsonObject jsonObject = JsonParser.parseString(responseData).getAsJsonObject();
        JsonObject details = jsonObject.getAsJsonObject("response")
                .getAsJsonArray("publishedfiledetails")
                .get(0).getAsJsonObject();
        if (!details.has("title")) {
            System.out.println("COULD NOT PARSE: " + id);
            return null;
        }
        String name = details.get("title").getAsString();
        int subscriptions = details.get("subscriptions").getAsInt();
        int lifetimeSubscriptions = details.get("lifetime_subscriptions").getAsInt();
        int lifetimeFavorites = details.get("lifetime_favorited").getAsInt();
        String previewUrl = details.get("preview_url").getAsString();
        return new PublishedMap(name, id, subscriptions, lifetimeSubscriptions, lifetimeFavorites, previewUrl);
    }

    private void downloadImage(String imageUrl, String fileName) throws IOException {
        URL url = new URL(imageUrl);
        InputStream in = url.openStream();
        try (ReadableByteChannel rbc = Channels.newChannel(in)) {
            String userHome = System.getProperty("user.home");
            String downloadPath = userHome + "\\Downloads\\Images"; // Windows Downloads directory path
            File directory = new File(downloadPath);
            if (!directory.exists()) {
                directory.mkdirs(); // Create the directory if it does not exist
            }
            String sanitizedFileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
            try (FileOutputStream fos = new FileOutputStream(new File(directory, sanitizedFileName + ".png"))) {
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            }
        }
    }
}
