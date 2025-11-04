package org.everything;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class IndexStore {
    public static class Node {
        public final long frn;
        public long parentFrn;
        public String name;
        public boolean isDir;
        public String volume;
        public String ext;
        public long mtime;

        public Node(long frn, long parentFrn, String name, boolean isDir, String volume) {
            this.frn = frn;
            this.parentFrn = parentFrn;
            this.name = name;
            this.isDir = isDir;
            this.volume = volume;
            int dot = name.lastIndexOf('.');
            this.ext = (dot > 0 && dot < name.length() - 1) ? name.substring(dot + 1).toLowerCase() : "";
            this.mtime = System.currentTimeMillis();
        }
    }

    private final ConcurrentHashMap<Long, Node> nodes = new ConcurrentHashMap<>();
    private final Map<String, List<Long>> nameIndex = new HashMap<>();
    private final Map<Long, String> pathCache = new ConcurrentHashMap<>();
    private final AtomicInteger indexedCount = new AtomicInteger();
    private final Map<String, Integer> volumeProgress = new ConcurrentHashMap<>();
    private final Map<String, Boolean> volumeDone = new ConcurrentHashMap<>();

    public synchronized void upsert(long frn, long parentFrn, String name, boolean isDir, String volume) {
        Node n = nodes.get(frn);
        if (n == null) {
            n = new Node(frn, parentFrn, name, isDir, volume);
            nodes.put(frn, n);
            indexedCount.incrementAndGet();
        } else {
            n.parentFrn = parentFrn;
            n.name = name;
            n.isDir = isDir;
            n.volume = volume;
            int dot = name.lastIndexOf('.');
            n.ext = (dot > 0 && dot < name.length() - 1) ? name.substring(dot + 1).toLowerCase() : "";
            n.mtime = System.currentTimeMillis();
        }
        indexName(name, frn);
    }

    public synchronized void remove(long frn) {
        Node n = nodes.remove(frn);
        if (n != null) {
            pathCache.remove(frn);
        }
    }

    private void indexName(String name, long frn) {
        String key = name.toLowerCase(Locale.ROOT);
        List<Long> list = nameIndex.get(key);
        if (list == null) {
            list = new ArrayList<>();
            nameIndex.put(key, list);
        }
        if (!list.contains(frn)) list.add(frn);
    }

    public String resolvePath(Node n) {
        String cached = pathCache.get(n.frn);
        if (cached != null) return cached;
        Deque<String> parts = new ArrayDeque<>();
        Node cur = n;
        while (cur != null) {
            parts.addFirst(cur.name);
            cur = nodes.get(cur.parentFrn);
        }
        String path = n.volume + "\\" + String.join("\\", parts);
        pathCache.put(n.frn, path);
        return path;
    }

    public List<Node> searchWithFilters(String keyword, String extFilter, String typeFilter, String pathFilter, int limit) {
        keyword = keyword == null ? "" : keyword.toLowerCase();
        if (extFilter != null) extFilter = extFilter.toLowerCase();
        if (pathFilter != null) pathFilter = pathFilter.toLowerCase();

        List<Node> out = new ArrayList<>();
        for (Node n : nodes.values()) {
            if (!keyword.isEmpty() && !n.name.toLowerCase().contains(keyword)) continue;
            if (extFilter != null && !extFilter.equals(n.ext)) continue;
            if (typeFilter != null) {
                if (typeFilter.equals("folder") && !n.isDir) continue;
                if (typeFilter.equals("file") && n.isDir) continue;
            }
            if (pathFilter != null) {
                String fullPath = resolvePath(n).toLowerCase();
                if (!fullPath.contains(pathFilter)) continue;
            }
            out.add(n);
            if (out.size() >= limit) break;
        }
        return out;
    }

    public int getTotalCount() { return nodes.size(); }
    public int getIndexedCount() { return indexedCount.get(); }

    public void reportProgress(String volume, int count) { volumeProgress.put(volume, count); }
    public Map<String, Integer> getProgressSnapshot() { return new HashMap<>(volumeProgress); }

    public void markComplete(String volume) { volumeDone.put(volume, true); }
    public boolean isComplete(String volume) { return volumeDone.getOrDefault(volume, false); }
}
