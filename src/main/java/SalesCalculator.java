import java.sql.*;

public class SalesCalculator {

    private static final String DB_URL = "jdbc:postgresql://invetory-inquiry-6364.j77.aws-us-east-1.cockroachlabs.cloud:26257/INVENTORY_INQUIRY";
    private static final String DB_USER = "boost";
    private static final String DB_PASSWORD = "CnL-z0Ilq5T9o48OBL6SxQ";

    // Fixed restocking increment (6 units per case)
    private static final int RESTOCK_INCREMENT = 6;

    public static void main(String[] args) {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            System.out.println("Connected to the database!");

            calculateAndUpdateSales(connection);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void calculateAndUpdateSales(Connection connection) {
        try {
            // Fetch current and previous stock data for each store
            String query = """
    WITH latest_imports AS (
        SELECT DISTINCT import_order
        FROM LowesData
        ORDER BY import_order DESC
        LIMIT 2
    )
    SELECT current.id, current.store_name, current.qty AS current_qty, previous.qty AS previous_qty
    FROM LowesData AS current
    JOIN LowesData AS previous
    ON current.store_name = previous.store_name
    AND current.import_order = (SELECT MAX(import_order) FROM latest_imports)
    AND previous.import_order = (SELECT MIN(import_order) FROM latest_imports)
    WHERE current.sales IS NULL;
""";


            PreparedStatement statement = connection.prepareStatement(query);
            ResultSet resultSet = statement.executeQuery();

            // Prepare the update query
            String updateQuery = "UPDATE LowesData SET sales = ? WHERE id = ?";
            PreparedStatement updateStatement = connection.prepareStatement(updateQuery);

            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String storeName = resultSet.getString("store_name");
                int currentQty = resultSet.getInt("current_qty");
                int previousQty = resultSet.getInt("previous_qty");

                if (resultSet.wasNull()) {
                    System.out.printf("Skipping record %d (store: %s): No previous stock data.\n", id, storeName);
                    continue; // Skip if no previous data
                }

                // Estimate restocking quantity (multiples of 6)
                int restockedQty = RESTOCK_INCREMENT; // Default: assume one case restocked
                while (previousQty + restockedQty < currentQty) {
                    restockedQty += RESTOCK_INCREMENT; // Add another case if needed
                }

                // Calculate sales
                int sales = (previousQty + restockedQty) - currentQty;

                // Handle edge cases: prevent negative sales
                if (sales < 0) {
                    System.out.printf("Warning: Negative sales detected for record %d (store: %s). Adjusting to 0.\n", id, storeName);
                    sales = 0;
                }

                // Log calculation for debugging
                System.out.printf(
                        "Store: %s, Previous: %d, Restocked: %d, Current: %d, Sales: %d\n",
                        storeName, previousQty, restockedQty, currentQty, sales
                );

                // Update the sales value in the database
                updateStatement.setInt(1, sales);
                updateStatement.setInt(2, id);
                updateStatement.addBatch();
            }

            // Execute the batch update
            updateStatement.executeBatch();
            System.out.println("Sales updated successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
