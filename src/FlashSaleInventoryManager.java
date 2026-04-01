import java.util.*;

public class FlashSaleInventoryManager {

    // productId -> stock
    private HashMap<String, Integer> stockMap;

    // productId -> waiting list
    private HashMap<String, Queue<Integer>> waitingList;

    public FlashSaleInventoryManager() {
        stockMap = new HashMap<>();
        waitingList = new HashMap<>();
    }

    // Add product with stock
    public void addProduct(String productId, int stock) {
        stockMap.put(productId, stock);
        waitingList.put(productId, new LinkedList<>());
    }

    // Check stock
    public int checkStock(String productId) {
        return stockMap.getOrDefault(productId, 0);
    }

    // Purchase item (thread-safe)
    public synchronized String purchaseItem(String productId, int userId) {

        int stock = stockMap.getOrDefault(productId, 0);

        if (stock > 0) {
            stockMap.put(productId, stock - 1);
            return "Success, " + (stock - 1) + " units remaining";
        } else {
            Queue<Integer> queue = waitingList.get(productId);
            queue.add(userId);
            return "Added to waiting list, position #" + queue.size();
        }
    }

    // Process waiting list when stock returns
    public synchronized void restock(String productId, int amount) {

        int currentStock = stockMap.getOrDefault(productId, 0);
        stockMap.put(productId, currentStock + amount);

        Queue<Integer> queue = waitingList.get(productId);

        while (!queue.isEmpty() && stockMap.get(productId) > 0) {
            int user = queue.poll();
            stockMap.put(productId, stockMap.get(productId) - 1);
            System.out.println("User " + user + " from waiting list purchased item.");
        }
    }

    public static void main(String[] args) {

        FlashSaleInventoryManager manager = new FlashSaleInventoryManager();

        manager.addProduct("IPHONE15_256GB", 3);

        System.out.println(manager.checkStock("IPHONE15_256GB"));

        System.out.println(manager.purchaseItem("IPHONE15_256GB", 12345));
        System.out.println(manager.purchaseItem("IPHONE15_256GB", 67890));
        System.out.println(manager.purchaseItem("IPHONE15_256GB", 11111));
        System.out.println(manager.purchaseItem("IPHONE15_256GB", 22222));

        manager.restock("IPHONE15_256GB", 2);
    }
}