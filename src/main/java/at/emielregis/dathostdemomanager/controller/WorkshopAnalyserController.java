package at.emielregis.dathostdemomanager.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${settings.workshop-image-path}")
    private String workshopImagePath;

    private static final Logger logger = LoggerFactory.getLogger(WorkshopAnalyserController.class);

    private record PublishedMap(String name, String id, int subscriptions, int lifetimeSubscriptions,
                                int lifetimeFavorites, boolean isHostage, String previewUrl) {
    }

    @GetMapping("/api/collection/download")
    public Map<String, Object> downloadCollectionImages(@RequestParam String collectionId) {
        logger.info("Starting to download collection images for collectionId: {}", collectionId);
        String[] ids = getPublishedMapIdsForCollection(collectionId);
        List<PublishedMap> mapList = new ArrayList<>();
        List<String> unparsedIds = new ArrayList<>();

        for (String id : ids) {
            PublishedMap map = getPublishedMapForId(id);
            if (map != null) {
                try {
                    downloadImage(map.previewUrl(), map.name(), map.isHostage());
                    logger.info("Downloaded image for map: {}", map.name());
                } catch (IOException e) {
                    logger.error("Failed to download image for map: {}", map.name(), e);
                }
                mapList.add(map);
            } else {
                unparsedIds.add(id);
                logger.warn("Failed to parse map with id: {}", id);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("maps", mapList);
        response.put("unparsedIds", unparsedIds);

        logger.info("Download process completed. Parsed {} maps, {} maps could not be parsed.", mapList.size(), unparsedIds.size());
        return response;
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
            logger.info("Successfully retrieved collection details for collectionId: {}", id);
            return parseIds(responseData);
        } catch (Exception e) {
            logger.error("Failed to retrieve collection details for collectionId: {}", id, e);
            throw new IllegalStateException("Unable to retrieve collection details", e);
        }
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
        logger.info("Parsed {} published file IDs from collection details.", publishedFileIds.length);
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
            logger.info("Successfully retrieved details for map id: {}", id);
            return parsePublishedMap(responseData, id);
        } catch (Exception e) {
            logger.error("Failed to retrieve details for map id: {}", id, e);
            return null;
        }
    }

    private PublishedMap parsePublishedMap(String responseData, String id) {
        JsonObject jsonObject = JsonParser.parseString(responseData).getAsJsonObject();
        JsonObject details = jsonObject.getAsJsonObject("response")
                .getAsJsonArray("publishedfiledetails")
                .get(0).getAsJsonObject();
        if (!details.has("title")) {
            logger.warn("Could not parse map with id: {}", id);
            return null;
        }
        String name = details.get("title").getAsString();
        int subscriptions = details.get("subscriptions").getAsInt();
        int lifetimeSubscriptions = details.get("lifetime_subscriptions").getAsInt();
        int lifetimeFavorites = details.get("lifetime_favorited").getAsInt();
        String previewUrl = details.get("preview_url").getAsString();
        boolean isHostage = details.get("description").getAsString().toLowerCase().contains("hostage");
        logger.info("Parsed map: {} (id: {})", name, id);
        return new PublishedMap(name, id, subscriptions, lifetimeSubscriptions, lifetimeFavorites, isHostage, previewUrl);
    }

    private void downloadImage(String imageUrl, String fileName, boolean hostage) throws IOException {
        URL url = new URL(imageUrl);
        InputStream in = url.openStream();
        try (ReadableByteChannel rbc = Channels.newChannel(in)) {
            File directory = new File(workshopImagePath);
            if (!directory.exists()) {
                directory.mkdirs();
                logger.info("Created directory: {}", workshopImagePath);
            }
            String sanitizedFileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
            if (hostage) {
                sanitizedFileName = sanitizedFileName + " (HOSTAGE)";
            }
            try (FileOutputStream fos = new FileOutputStream(new File(directory, sanitizedFileName + ".png"))) {
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                logger.info("Image saved as: {}", sanitizedFileName + ".png");
            }
        }
    }
}
