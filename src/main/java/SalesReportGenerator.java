import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.util.HashMap;

public class SalesReportGenerator {

    private static final String DB_URL = "jdbc:postgresql://invetory-inquiry-6364.j77.aws-us-east-1.cockroachlabs.cloud:26257/INVENTORY_INQUIRY";
    private static final String DB_USER = "boost";
    private static final String DB_PASSWORD = "CnL-z0Ilq5T9o48OBL6SxQ";

    public static void main(String[] args) {
        String outputFileName = "SalesPerformanceSummary.csv"; // Report file name

        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             FileWriter writer = new FileWriter(outputFileName)) {

            System.out.println("Connected to the database!");

            // Write CSV headers
            writer.append("Store Name, State, Total Sales\n");

            // Query total sales per store
            String query = """
                SELECT store_name, state, SUM(sales) AS total_sales
                FROM LowesData
                GROUP BY store_name, state
                ORDER BY total_sales DESC
            """;

            try (PreparedStatement statement = connection.prepareStatement(query);
                 ResultSet resultSet = statement.executeQuery()) {

                while (resultSet.next()) {
                    String storeName = resultSet.getString("store_name");
                    String state = resultSet.getString("state");
                    int totalSales = resultSet.getInt("total_sales");

                    // Write row to CSV
                    writer.append(String.format("%s,%s,%d\n", storeName, state, totalSales));
                }
            }

            System.out.println("Sales performance report generated: " + outputFileName);

        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("File error: " + e.getMessage());
        }
    }
}
