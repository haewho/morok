import java.util.HashMap;
import java.util.Map;
import org.morok.proxy.ProxyRouteTransaction;

public final class ProxyRouteTransactionTest {
    static final class Store implements ProxyRouteTransaction.Store {
        final Map<String, Object> memory = new HashMap<>();
        final Map<String, Object> disk = new HashMap<>();
        boolean failNext, failAll, throwNext;
        public Map<String, ?> values() { return new HashMap<>(memory); }
        public boolean commit(Map<String, Object> values) {
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                if (entry.getValue() == null) memory.remove(entry.getKey());
                else memory.put(entry.getKey(), entry.getValue());
            }
            if (throwNext) { throwNext = false; throw new IllegalStateException("Synthetic failure"); }
            if (failNext) { failNext = false; return false; }
            if (failAll) return false;
            disk.clear(); disk.putAll(memory); return true;
        }
    }
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        Store store = new Store();
        Map<String, Object> initial = new HashMap<>();
        initial.put("mode", "MANUAL"); initial.put("proxy", "old.example"); initial.put("unrelated", 42);
        store.commit(initial);
        Map<String, Object> next = new HashMap<>();
        next.put("mode", "AUTO"); next.put("proxy", "new.example"); next.put("new-key", true);
        store.failNext = true;
        check(!ProxyRouteTransaction.commit(store, next), "failed disk write must report failure");
        check(store.memory.equals(initial) && store.disk.equals(initial), "failed write restores route in memory and on disk");
        store.failAll = true;
        check(!ProxyRouteTransaction.commit(store, next), "rollback disk failure must still report failure");
        check(store.memory.equals(initial), "rollback updates memory even when disk remains unwritable");
        store.failAll = false; store.throwNext = true;
        check(!ProxyRouteTransaction.commit(store, next) && store.memory.equals(initial), "exception after memory mutation restores prior route");
        check(ProxyRouteTransaction.commit(store, next), "successful transaction");
        check("AUTO".equals(store.disk.get("mode")) && "new.example".equals(store.disk.get("proxy")), "mode and endpoint commit together");
        check(Integer.valueOf(42).equals(store.disk.get("unrelated")), "unrelated preferences preserved");
        System.out.println("PASS: route transaction success, disk failure, rollback failure, exception, missing-key restoration and unrelated preference preservation");
    }
}
