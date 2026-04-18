import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.Key;
import java.util.Base64;

public class Inventory {
    
    private static final String DATA_FILE = "inventory.json";
    private static final String TEMP_FILE = "inventory.tmp.json";
    private static final String ALGORITHM = "AES";
    private static final String DEFAULT_KEY = "MySuperSecretKey"; // 16 bytes static key for AES
    private static final int LOW_STOCK_THRESHOLD = 5;

    private List<Item> items;
    private Scanner scanner;

    public Inventory(int capacity) {
        // Capacity is ignored for ArrayList, but kept for constructor signature compatibility
        items = new ArrayList<>();
        scanner = new Scanner(System.in);
        loadFromJson();
    }

    // Item inner class encapsulating inventory properties
    private static class Item {
        String name;
        int quantity;
        double price;

        Item(String name, int quantity, double price) {
            this.name = name;
            this.quantity = quantity;
            this.price = price;
        }
    }

    public void run() {
        int choice = -1;
        do {
            System.out.println("\n--- Inventory Management System (Secured & JSON-Backed) ---");
            System.out.println("1. Add new item");
            System.out.println("2. Update stock");
            System.out.println("3. Display items by price");
            System.out.println("4. Display items by quantity");
            System.out.println("5. Show low stock alert");
            System.out.println("0. Exit");
            System.out.print("Enter choice: ");
            
            if (scanner.hasNextInt()) {
                choice = scanner.nextInt();
                scanner.nextLine(); // clear newline
            } else {
                choice = -1;
                scanner.nextLine(); // clear invalid input
            }

            switch (choice) {
                case 1 -> addItem();
                case 2 -> updateStock();
                case 3 -> displaySortedByPrice();
                case 4 -> displaySortedByQuantity();
                case 5 -> lowStockAlert();
                case 0 -> System.out.println("Exiting and securing data...");
                default -> System.out.println("Invalid choice. Please enter a valid number.");
            }
        } while (choice != 0);
    }

    public void addItem() {
        System.out.print("Enter product name: ");
        String name = scanner.nextLine().trim();
        // Validation to prevent JSON injection or format breaking
        if (name.isEmpty() || name.contains("\"") || name.contains("{") || name.contains("}")) {
             System.out.println("Invalid name formatting. Avoid using quotes or brackets.");
             return;
        }

        System.out.print("Enter quantity: ");
        while (!scanner.hasNextInt()) {
            System.out.print("Invalid input. Enter a whole number: ");
            scanner.next();
        }
        int qty = scanner.nextInt();

        System.out.print("Enter price: ");
        while (!scanner.hasNextDouble()) {
            System.out.print("Invalid input. Enter a valid price: ");
            scanner.next();
        }
        double price = scanner.nextDouble();
        scanner.nextLine(); // clear newline

        items.add(new Item(name, qty, price));
        System.out.println("Item added successfully.");
        
        checkAutomatedLowStock(name, qty);
        saveToJson();
    }

    public void updateStock() {
        System.out.print("Enter product name to update: ");
        String name = scanner.nextLine().trim();
        Item item = findProduct(name);

        if (item == null) {
            System.out.println("Product not found.");
            return;
        }

        System.out.print("Enter new quantity: ");
        while (!scanner.hasNextInt()) {
            System.out.print("Invalid input. Enter a whole number: ");
            scanner.next();
        }
        int qty = scanner.nextInt();
        scanner.nextLine(); // clear newline

        item.quantity = qty;
        System.out.println("Stock updated.");
        
        checkAutomatedLowStock(name, qty);
        saveToJson();
    }

    public void displaySortedByPrice() {
        System.out.println("\nItems sorted by price:");
        List<Item> sortedItems = new ArrayList<>(items);
        sortedItems.sort(Comparator.comparingDouble(item -> item.price));
        displayItemsList(sortedItems);
    }

    public void displaySortedByQuantity() {
        System.out.println("\nItems sorted by quantity:");
        List<Item> sortedItems = new ArrayList<>(items);
        sortedItems.sort(Comparator.comparingInt(item -> item.quantity));
        displayItemsList(sortedItems);
    }

    public void lowStockAlert() {
        System.out.println("\nLow Stock Items (quantity < " + LOW_STOCK_THRESHOLD + "):");
        boolean found = false;
        for (Item item : items) {
            if (item.quantity < LOW_STOCK_THRESHOLD) {
                System.out.println(item.name + " - Qty: " + item.quantity);
                found = true;
            }
        }
        if (!found) {
            System.out.println("All items sufficiently stocked.");
        }
    }

    private void checkAutomatedLowStock(String name, int quantity) {
        if (quantity < LOW_STOCK_THRESHOLD) {
            System.out.println("\n[SYSTEM ALERT]: Low stock detected for " + name + " (Qty: " + quantity + "). Consider reordering soon!");
        }
    }

    private void displayItemsList(List<Item> listToDisplay) {
        System.out.printf("%-20s %-10s %-10s%n", "Product", "Quantity", "Price");
        for (Item item : listToDisplay) {
            System.out.printf("%-20s %-10d $%-10.2f%n", item.name, item.quantity, item.price);
        }
    }

    private Item findProduct(String name) {
        for (Item item : items) {
            if (item.name.equalsIgnoreCase(name)) {
                return item;
            }
        }
        return null;
    }
    
    // --- Secure Data Persistence & JSON Management ---
    
    private void saveToJson() {
        try {
            // 1. Manually Build JSON Array string
            StringBuilder json = new StringBuilder("[\n");
            for (int i = 0; i < items.size(); i++) {
                Item item = items.get(i);
                json.append("  {\n");
                json.append("    \"name\": \"").append(item.name).append("\",\n");
                json.append("    \"quantity\": ").append(item.quantity).append(",\n");
                json.append("    \"price\": ").append(item.price).append("\n");
                json.append("  }");
                if (i < items.size() - 1) json.append(",");
                json.append("\n");
            }
            json.append("]");

            // 2. Encrypt
            String encryptedData = encrypt(json.toString(), DEFAULT_KEY);
            
            // 3. Write securely (Atomic Write)
            Path tempFile = Paths.get(TEMP_FILE);
            Path dataFile = Paths.get(DATA_FILE);
            Files.write(tempFile, encryptedData.getBytes());
            Files.move(tempFile, dataFile, StandardCopyOption.REPLACE_EXISTING);
            
        } catch (Exception e) {
            System.err.println("Error saving inventory data securely: " + e.getMessage());
        }
    }

    private void loadFromJson() {
        Path dataFile = Paths.get(DATA_FILE);
        if (!Files.exists(dataFile)) {
            return; // No existing data
        }

        try {
            // 1. Read & Decrypt
            String encryptedData = new String(Files.readAllBytes(dataFile));
            String jsonData = decrypt(encryptedData, DEFAULT_KEY);
            
            // 2. Parse lightweight JSON manually using block splitting
            String[] blocks = jsonData.split("\\{");
            for (int i = 1; i < blocks.length; i++) {
                String block = blocks[i];
                Matcher nm = Pattern.compile("\"name\":\\s*\"(.*?)\"").matcher(block);
                Matcher qm = Pattern.compile("\"quantity\":\\s*(\\d+)").matcher(block);
                Matcher pm = Pattern.compile("\"price\":\\s*([\\d.]+)").matcher(block);
                
                if (nm.find() && qm.find() && pm.find()) {
                    String name = nm.group(1);
                    int qty = Integer.parseInt(qm.group(1));
                    double price = Double.parseDouble(pm.group(1));
                    items.add(new Item(name, qty, price));
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading inventory data (corrupted, missing, or decryption failed). Starting fresh.");
        }
    }

    private String encrypt(String data, String keyString) throws Exception {
        Key key = new SecretKeySpec(keyString.getBytes(), ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encryptedVal = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encryptedVal);
    }

    private String decrypt(String encryptedData, String keyString) throws Exception {
        Key key = new SecretKeySpec(keyString.getBytes(), ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decodedValue = Base64.getDecoder().decode(encryptedData);
        byte[] decryptedVal = cipher.doFinal(decodedValue);
        return new String(decryptedVal);
    }
}
