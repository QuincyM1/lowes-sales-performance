import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

public class SimpleApiFetcher {
    public static void main(String[] args) {
        String urlString = "https://api.example.com/data";  // Replace with your actual API URL
        String apiKey = "YOUR_API_KEY";  // Replace with your API key if needed

        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);  // Set API key if needed

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // Write the response to a CSV file
                try (BufferedWriter writer = Files.newBufferedWriter(Paths.get("output.csv"))) {
                    writer.write("data");  // Adjust headers based on actual data format
                    writer.newLine();
                    writer.write(response.toString());
                }

                System.out.println("Data written to output.csv");
            } else {
                System.out.println("GET request failed. Response Code: " + responseCode);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
