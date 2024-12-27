import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public class StoreDataFetcher {

    private static final String BASE_URL = "https://www.lowes.com/wpd/checkotherstores/5012898687/";
    private static final String URL_SUFFIX = "?itemNumber=4358811&modelId=GL22BLKS1&vendorNumber=108893&maxResults=100&inventorySource=bifrost";
    private static final String ZIP_CODES_FILE = "/D:/lowes-sales-performance-main/zipcodes.txt";
    private static final String PRODUCT_NAME = "GL22BLKS1";
    private static final int MIN_QUANTITY_THRESHOLD = 0; // Minimum quantity filter

    // Database connection details
    private static final String DB_URL = "jdbc:postgresql://invetory-inquiry-6364.j77.aws-us-east-1.cockroachlabs.cloud:26257/INVENTORY_INQUIRY";
    private static final String DB_USER = "boost";
    private static final String DB_PASSWORD = "CnL-z0Ilq5T9o48OBL6SxQ";

    public static void main(String[] args) {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            System.out.println("Connected to the database!");

            int importOrder = getNextImportOrder(connection);
            System.out.println("Starting with import_order: " + importOrder);

            // Dynamic file name
            LocalDateTime today = LocalDateTime.now();
            String date = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String outputFileName = PRODUCT_NAME + "_Lowes_" + date + ".csv";

            // Buffer to store all rows
            List<String[]> dataBuffer = new ArrayList<>();
            dataBuffer.add(new String[]{"product_id", "store_name", "state", "zipcode", "qty", "sales", "import_order", "created_time"}); // Add headers

            Set<String> uniqueStores = new HashSet<>(); // Set to track processed stores by a unique key (e.g., store ID or name)

            try (BufferedReader zipReader = new BufferedReader(new FileReader(ZIP_CODES_FILE))) {
                String line;

                while ((line = zipReader.readLine()) != null) {
                    String[] zipCodes = line.split(",");
                    for (String zipCode : zipCodes) {
                        zipCode = zipCode.trim();
                        if (zipCode.isEmpty()) continue;

                        String requestUrl = BASE_URL + zipCode + URL_SUFFIX;
                        try {
                            String jsonResponse = fetchJSONData(requestUrl);

                            if (jsonResponse != null) {
                                HashMap<String, String[]> storeData = parseStoreData(jsonResponse, today, importOrder, uniqueStores);

                                // Buffer rows into dataBuffer
                                dataBuffer.addAll(storeData.values());

                                // Insert data into the database
                                insertDataToDatabase(storeData, connection);
                            }
                        } catch (Exception e) {
                            System.out.println("Error processing ZIP code " + zipCode + ": " + e.getMessage());
                        }
                    }
                }
            }

            // Write all buffered data to the CSV file at once
            try (FileWriter writer = new FileWriter(outputFileName)) {
                for (String[] row : dataBuffer) {
                    writer.append(String.join(",", row)).append("\n");
                }
                System.out.println("Data successfully written to " + outputFileName);
            }

            // Insert summary data into VendorData table
            insertVendorData(connection, "Lowes", PRODUCT_NAME, importOrder, LocalDate.now());
            System.out.println("Vendor data summary added.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Function to fetch the maximum import_order value from the database
    private static int getNextImportOrder(Connection connection) {
        int nextImportOrder = 1; // Default to 1 if no records exist
        try {
            String query = "SELECT MAX(import_order) FROM LowesData";
            PreparedStatement statement = connection.prepareStatement(query);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                int lastImportOrder = resultSet.getInt(1);
                if (!resultSet.wasNull()) {
                    nextImportOrder = lastImportOrder + 1;
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching next import_order: " + e.getMessage());
        }
        return nextImportOrder;
    }

    // Function to fetch JSON data from the URL
    private static String fetchJSONData(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        if (conn.getResponseCode() != 200) {
            throw new RuntimeException("HTTP error code: " + conn.getResponseCode());
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String output;
        while ((output = br.readLine()) != null) {
            response.append(output);
        }
        conn.disconnect();
        return response.toString();
    }

    // Function to parse JSON response and store data in a HashMap, filtering by quantity
    private static HashMap<String, String[]> parseStoreData(String jsonResponse, LocalDateTime importOrderTime, int importOrder, Set<String> uniqueStores) {
        HashMap<String, String[]> storeData = new HashMap<>();
        String createdTime = importOrderTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        JSONObject jsonObject = new JSONObject(jsonResponse);
        JSONArray storesArray = jsonObject.getJSONArray("stores");

        for (int i = 0; i < storesArray.length(); i++) {
            JSONObject storeObj = storesArray.getJSONObject(i).getJSONObject("store");
            JSONObject inventory = storesArray.getJSONObject(i).getJSONObject("inventory");

            String storeName = storeObj.optString("bisName", "N/A"); // Use store name as the unique key
            String zipCode = storeObj.optString("zip", ""); // Nullable
            int qty = inventory.optInt("onhandQty", 0);
            int sales = inventory.optInt("sales", 0); // Sales is nullable; default 0

            // Create a unique store key (store name + zip code)
            String uniqueKey = storeName + "_" + zipCode;

            // Skip if the store is already processed
            if (uniqueStores.contains(uniqueKey)) {
                continue;
            }
            uniqueStores.add(uniqueKey); // Mark store as processed

            // Only include stores with onhandQty >= MIN_QUANTITY_THRESHOLD
            if (qty >= MIN_QUANTITY_THRESHOLD) {
                String[] row = {
                        PRODUCT_NAME,
                        storeName,    // store_name
                        storeObj.optString("state", "N/A"), // state
                        zipCode,      // zipcode
                        String.valueOf(qty),    // qty
                        String.valueOf(sales),  // sales
                        String.valueOf(importOrder), // import_order
                        createdTime             // created_time
                };
                storeData.put(storeName, row);
            }
        }

        return storeData;
    }

    public static void insertVendorData(Connection connection, String vendor, String productId, int importOrder, LocalDate date) {
        String query = "INSERT INTO VendorData (vendor, product_id, \"order\", \"date\", product_import_order) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, vendor);
            statement.setString(2, productId);
            statement.setInt(3, importOrder);
            statement.setDate(4, java.sql.Date.valueOf(date));
            statement.setInt(5, importOrder);
            statement.executeUpdate();
            System.out.println("VendorData table updated with summary for import_order: " + importOrder);
        } catch (SQLException e) {
            System.out.println("Error inserting data into VendorData table: " + e.getMessage());
        }
    }

    // Function to insert data into the database
    private static void insertDataToDatabase(HashMap<String, String[]> storeData, Connection connection) {
        String insertQuery = "INSERT INTO LowesData (product_id, store_name, state, zipcode, qty, sales, import_order, created_time) " +
                "ON CONFLICT (product_id, store_name, zipcode) " +
                "DO UPDATE SET qty = EXCLUDED.qty, sales = EXCLUDED.sales, created_time = EXCLUDED.created_time";
        try (PreparedStatement statement = connection.prepareStatement(insertQuery)) {
            for (String[] values : storeData.values()) {
                statement.setString(1, values[0]); // product_id
                statement.setString(2, values[1]); // store_name
                statement.setString(3, values[2]); // state
                statement.setString(4, values[3]); // zipcode
                statement.setInt(5, Integer.parseInt(values[4])); // qty
                statement.setInt(6, Integer.parseInt(values[5])); // sales
                statement.setInt(7, Integer.parseInt(values[6])); // import_order
                statement.setString(8, values[7]); // created_time
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            System.out.println("Error inserting data into LowesData table: " + e.getMessage());
        }
    }
}

