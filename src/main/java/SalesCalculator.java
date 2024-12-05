//public class SalesCalculator {
//
//    private static final String DB_URL = "jdbc:mysql://localhost:3306/bs";
//    private static final String DB_USER = "root";
//    private static final String DB_PASSWORD = "Noahmqy4212*";
//
//    public static void main(String[] args) {
//        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
//            System.out.println("Connected to the database!");
//
//            calculateAndUpdateSales(connection);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    public static void calculateAndUpdateSales(Connection connection) {
//        try {
//            // Select records where sales is NULL
//            String query = "SELECT id, qty FROM LowesData WHERE sales IS NULL";
//            PreparedStatement statement = connection.prepareStatement(query);
//            ResultSet resultSet = statement.executeQuery();
//
//            // Prepare the update query
//            String updateQuery = "UPDATE LowesData SET sales = ? WHERE id = ?";
//            PreparedStatement updateStatement = connection.prepareStatement(updateQuery);
//
//            while (resultSet.next()) {
//                int id = resultSet.getInt("id");
//                int qty = resultSet.getInt("qty");
//
//                // Calculate sales (replace with your logic)
//                double pricePerUnit = 10.0; // Example price
//                double sales = qty * pricePerUnit;
//
//                // Update the sales value in the database
//                updateStatement.setDouble(1, sales);
//                updateStatement.setInt(2, id);
//                updateStatement.addBatch();
//            }
//
//            // Execute the batch update
//            updateStatement.executeBatch();
//            System.out.println("Sales updated successfully!");
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//}
